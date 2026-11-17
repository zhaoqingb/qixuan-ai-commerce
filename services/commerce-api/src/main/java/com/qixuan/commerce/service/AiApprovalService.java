package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.dto.ApprovalResult;
import com.qixuan.commerce.dto.ShipRequest;
import com.qixuan.commerce.dto.ApprovalView;
import com.qixuan.commerce.entity.AiActionApproval;
import com.qixuan.commerce.entity.AiToolAudit;
import com.qixuan.commerce.mapper.AiActionApprovalMapper;
import com.qixuan.commerce.mapper.AiToolAuditMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 写操作的审批（人在环）。
 *
 * 核心约定：模型只负责"提议"，参数写死在 request_payload 里；
 * 真人确认后，系统按单子里存的参数执行 —— 不再接受任何新参数。
 */
@Service
public class AiApprovalService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String ACTION_RESTOCK = "RESTOCK";
    private static final String ACTION_SHIP = "SHIP";

    /** 审批单有效期：10 分钟。过期不执行，避免"三天前的确认"被翻出来用 */
    private static final int TTL_MINUTES = 10;

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AiActionApprovalMapper approvalMapper;
    private final AiToolAuditMapper auditMapper;
    private final AiApprovalExpiryService expiryService;
    private final SkuInventoryService skuInventoryService;
    private final ShipmentService shipmentService;
    private final JsonMapper jsonMapper;

    public AiApprovalService(AiActionApprovalMapper approvalMapper, AiToolAuditMapper auditMapper,
            AiApprovalExpiryService expiryService, SkuInventoryService skuInventoryService,
            ShipmentService shipmentService, JsonMapper jsonMapper) {
        this.approvalMapper = approvalMapper;
        this.auditMapper = auditMapper;
        this.expiryService = expiryService;
        this.skuInventoryService = skuInventoryService;
        this.shipmentService = shipmentService;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 创建审批单（由 Agent 工具调用触发，不直接改业务数据）。
     *
     * @param payloadJson 固化的执行参数，审批通过后原样使用
     */
    @Transactional
    public AiActionApproval create(Long userId, Long conversationId, Long storeId,
            String actionCode, String payloadJson) {

        AiActionApproval approval = new AiActionApproval();
        approval.setApprovalNo(generateApprovalNo());
        approval.setConversationId(conversationId);
        approval.setActorUserId(userId);
        approval.setStoreId(storeId);
        approval.setActionCode(actionCode);
        approval.setRequestPayload(payloadJson);
        approval.setStatus(STATUS_PENDING);
        approval.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        approvalMapper.insert(approval);
        return approval;
    }

    /**
     * 确认并执行。
     *
     * 顺序很重要：查单 → 验归属 → 验过期 → CAS 抢状态 → 执行 → 记 executed_at。
     * 任何一步失败都要让整笔回滚，绝不能出现"状态改了但没执行"。
     */
    @Transactional
    public ApprovalResult approve(Long userId, Long approvalId) {
        AiActionApproval approval = requireOwned(userId, approvalId);

        if (STATUS_PENDING.equals(approval.getStatus()) && isExpired(approval)) {
            expiryService.markExpired(approvalId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该操作已过期，请重新发起");
        }

        LocalDateTime now = LocalDateTime.now();
        AiActionApproval patch = new AiActionApproval();
        patch.setStatus(STATUS_APPROVED);
        patch.setDecidedAt(now);
        int rows = approvalMapper.update(patch,
                new LambdaUpdateWrapper<AiActionApproval>()
                        .eq(AiActionApproval::getId, approvalId)
                        .eq(AiActionApproval::getStatus, STATUS_PENDING));

        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该操作已处理");
        }
        String message = executeAction(approval);

        AiActionApproval executed = new AiActionApproval();
        executed.setExecutedAt(now);
        approvalMapper.update(executed, new LambdaUpdateWrapper<AiActionApproval>()
                .eq(AiActionApproval::getId, approvalId)
                .eq(AiActionApproval::getStatus, STATUS_APPROVED));

        return new ApprovalResult(approval.getApprovalNo(), STATUS_APPROVED, message, now);
    }

    /** 拒绝：只改状态，不执行任何业务动作 */
    @Transactional
    public void reject(Long userId, Long approvalId) {
        AiActionApproval approval = requireOwned(userId, approvalId);

        if (STATUS_PENDING.equals(approval.getStatus()) && isExpired(approval)) {
            expiryService.markExpired(approvalId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该操作已过期，请重新发起");
        }

        AiActionApproval patch = new AiActionApproval();
        patch.setStatus(STATUS_REJECTED);
        patch.setDecidedAt(LocalDateTime.now());

        int rows = approvalMapper.update(patch, new LambdaUpdateWrapper<AiActionApproval>()
                .eq(AiActionApproval::getId, approvalId)
                .eq(AiActionApproval::getStatus, STATUS_PENDING));

        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该操作已处理");
        }
    }

    /** 当前用户所有待确认的操作（前端用来展示"待你确认"卡片） */
    public List<ApprovalView> listPending(Long userId) {
        List<AiActionApproval> rows = approvalMapper.selectList(
                new LambdaQueryWrapper<AiActionApproval>()
                        .eq(AiActionApproval::getActorUserId, userId)
                        .eq(AiActionApproval::getStatus, STATUS_PENDING)
                        .gt(AiActionApproval::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(AiActionApproval::getId));

        List<ApprovalView> views = new ArrayList<>();
        for (AiActionApproval row : rows) {
            views.add(toView(row));
        }
        return views;
    }

    /** 按 action_code 分发到真正的业务方法 */
    public String executeAction(AiActionApproval approval) {
        JsonNode payload = jsonMapper.readTree(approval.getRequestPayload());

        return switch (approval.getActionCode()) {
            case ACTION_RESTOCK -> {
                long skuId = payload.path("skuId").asLong();
                int qty = payload.path("qty").asInt();
                int available = skuInventoryService.restock(
                        approval.getActorUserId(), skuId, qty, approval.getApprovalNo());
                yield "补货成功，SKU " + skuId + " 当前可售 " + available + " 件";
            }
            case ACTION_SHIP -> {
                long storeOrderId = payload.path("storeOrderId").asLong();
                String carrierCode = payload.path("carrierCode").asText();
                String trackingNo = payload.path("trackingNo").asText();
                shipmentService.ship(approval.getActorUserId(), storeOrderId,
                        new ShipRequest(carrierCode, trackingNo));
                yield "发货成功，子单 " + storeOrderId + " 已标记为已发货（" + carrierCode + " " + trackingNo + "）";
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "不支持的动作类型：" + approval.getActionCode());
        };
    }

    /** 查单 + 归属校验。不区分"不存在"和"不是你的"，统一 404 */
    public AiActionApproval requireOwned(Long userId, Long approvalId) {
        AiActionApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null || !userId.equals(approval.getActorUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待确认操作不存在");
        }
        return approval;
    }

    public boolean isExpired(AiActionApproval approval) {
        return approval.getExpiresAt() != null && approval.getExpiresAt().isBefore(LocalDateTime.now());
    }

    public ApprovalView toView(AiActionApproval approval) {
        return new ApprovalView(
                approval.getId(),
                approval.getApprovalNo(),
                approval.getActionCode(),
                describe(approval),
                approval.getStatus(),
                approval.getExpiresAt());
    }

    /** 把固化的参数翻译成一句人话，前端和模型都能直接用 */
    private String describe(AiActionApproval approval) {
        try {
            JsonNode payload = jsonMapper.readTree(approval.getRequestPayload());
            if (ACTION_RESTOCK.equals(approval.getActionCode())) {
                return "给 SKU " + payload.path("skuId").asLong()
                        + " 补货 " + payload.path("qty").asInt() + " 件";
            }
            if (ACTION_SHIP.equals(approval.getActionCode())) {
                return "给子单 " + payload.path("subOrderNo").asText() + " 发货（"
                        + payload.path("carrierCode").asText() + " "
                        + payload.path("trackingNo").asText() + "）";
            }
        } catch (Exception ignored) {
            // 描述失败不影响主流程
        }
        return approval.getActionCode();
    }

    private String generateApprovalNo() {
        return "A" + LocalDateTime.now().format(NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
