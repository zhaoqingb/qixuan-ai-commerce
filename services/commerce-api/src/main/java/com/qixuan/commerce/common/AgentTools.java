package com.qixuan.commerce.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.entity.AiActionApproval;
import com.qixuan.commerce.entity.AiToolAudit;
import com.qixuan.commerce.entity.RefundRequest;
import com.qixuan.commerce.entity.Shipment;
import com.qixuan.commerce.entity.SkuInventory;
import com.qixuan.commerce.entity.Store;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.AiToolAuditMapper;
import com.qixuan.commerce.mapper.RefundRequestMapper;
import com.qixuan.commerce.mapper.ShipmentMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.qixuan.commerce.mapper.TradeOrderMapper;
import com.qixuan.commerce.service.AiApprovalService;
import com.qixuan.commerce.service.SkuInventoryService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agent 可用的工具：定义（给模型看）+ 执行（真正干活）。
 *
 * 所有工具都以当前登录用户为边界 —— 模型不知道用户是谁，userId 由调用方传进来，
 * 绝不能由模型自己"猜"或"指定"。
 *
 * 工具分两类：
 * - 只读（get_*）：直接查库返回
 * - 写操作（restock_sku）：只生成待确认记录，真人点确认后才执行
 */
@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private final TradeOrderMapper tradeOrderMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final ShipmentMapper shipmentMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final StoreMapper storeMapper;
    private final SkuInventoryService skuInventoryService;
    private final AiApprovalService approvalService;
    private final AiToolAuditMapper auditMapper;
    private final JsonMapper jsonMapper;

    public AgentTools(TradeOrderMapper tradeOrderMapper, StoreOrderMapper storeOrderMapper,
            ShipmentMapper shipmentMapper, SkuInventoryMapper skuInventoryMapper,
            RefundRequestMapper refundRequestMapper, StoreMapper storeMapper,
            SkuInventoryService skuInventoryService, AiApprovalService approvalService,
            AiToolAuditMapper auditMapper, JsonMapper jsonMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.storeOrderMapper = storeOrderMapper;
        this.shipmentMapper = shipmentMapper;
        this.skuInventoryMapper = skuInventoryMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.storeMapper = storeMapper;
        this.skuInventoryService = skuInventoryService;
        this.approvalService = approvalService;
        this.auditMapper = auditMapper;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 给模型看的工具清单（JSON Schema 格式）。
     *
     * 按角色下发：所有登录用户拿到查询类工具；只有店铺 owner 才额外拿到
     * 补货、发货这类写工具。少给一个工具，模型就少一条做错事的路。
     */
    public List<Map<String, Object>> definitions(Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();

        // ===== 通用（任何登录用户）=====
        Map<String, Object> orderArgs = new LinkedHashMap<>();
        orderArgs.put("orderNo", Map.of(
                "type", "string",
                "description", "订单号，形如 Q202611171710535226"));
        list.add(tool("get_order_status",
                "查询订单状态、金额，以及每个店铺子单的发货状态和物流单号。只能查当前用户自己的订单。",
                orderArgs));

        Map<String, Object> myOrdersArgs = new LinkedHashMap<>();
        myOrdersArgs.put("limit", Map.of(
                "type", "integer",
                "description", "返回条数，默认 5，最多 10"));
        list.add(tool("list_my_orders",
                "列出当前用户最近的几笔订单（订单号、状态、金额、下单时间）。"
                        + "用户说「我的订单」但不给单号时，先用这个查出来再回答。",
                myOrdersArgs));

        Map<String, Object> stockArgs = new LinkedHashMap<>();
        stockArgs.put("skuId", Map.of(
                "type", "integer",
                "description", "SKU 主键 id，例如 5"));
        list.add(tool("get_sku_stock",
                "查询某个 SKU 当前的可售库存数量。",
                stockArgs));

        Map<String, Object> refundArgs = new LinkedHashMap<>();
        refundArgs.put("limit", Map.of(
                "type", "integer",
                "description", "返回条数，默认 5，最多 10"));
        list.add(tool("list_my_refunds",
                "列出当前用户最近的退款申请（退款单号、件数、金额、状态）。",
                refundArgs));

        // ===== 商家专属：按 store.owner_user_id 判断 =====
        Long storeId = findOwnedStoreId(userId);
        if (storeId == null) {
            return list;
        }

        Map<String, Object> pendingArgs = new LinkedHashMap<>();
        pendingArgs.put("limit", Map.of(
                "type", "integer",
                "description", "返回条数，默认 5，最多 10"));
        list.add(tool("list_pending_shipments",
                "列出自己店铺里待发货的子单（状态 PAID）。发货前用它拿 storeOrderId，不要猜。",
                pendingArgs));

        Map<String, Object> restockArgs = new LinkedHashMap<>();
        restockArgs.put("skuId", Map.of(
                "type", "integer",
                "description", "要补货的 SKU 主键 id"));
        restockArgs.put("qty", Map.of(
                "type", "integer",
                "description", "补货件数，必须大于 0，单次不超过 100000"));
        list.add(tool("restock_sku",
                "给某个 SKU 补货（商家操作）。这是写操作，不会立即生效："
                        + "调用后会生成一条【待确认】记录，必须由用户本人点击确认后才真正入库。"
                        + "调用前建议先用 get_sku_stock 确认该 SKU 当前库存，并在回答里说明补货前后的数量。",
                restockArgs));

        Map<String, Object> shipArgs = new LinkedHashMap<>();
        shipArgs.put("subOrderNo", Map.of(
                "type", "string",
                "description", "子单号，形如 Q202611171715556474-1，必须原样照抄 list_pending_shipments 返回的值，不许改动"));
        shipArgs.put("carrierCode", Map.of(
                "type", "string",
                "description", "快递公司代码，如 SF（顺丰）、YTO（圆通）、JD（京东）"));
        shipArgs.put("trackingNo", Map.of(
                "type", "string",
                "description", "快递单号。用户没提供时必须先问，绝对不能编造"));
        list.add(tool("ship_order",
                "给某个子单发货（商家操作）。这是写操作：调用后只生成待确认记录，"
                        + "必须由用户确认后才真正发货。",
                shipArgs));

        return list;
    }

    /** 判断用户是不是某个店的 owner；不是商家就返回 null */
    private Long findOwnedStoreId(Long userId) {
        List<Store> stores = storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .eq(Store::getOwnerUserId, userId)
                .last("LIMIT 1"));
        return stores.isEmpty() ? null : stores.get(0).getId();
    }

    private Map<String, Object> tool(String name, String description,
            Map<String, Object> properties) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", new ArrayList<>(properties.keySet()));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", params);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function");
        item.put("function", function);
        return item;
    }

    /**
     * 执行工具，并留下审计记录。
     *
     * 审计是横切关注点：谁调的（userId）、在哪个会话（conversationId）、
     * 调了什么（toolName + 参数）、结果是什么 —— 出问题时唯一能还原现场的东西。
     *
     * @param conversationId 会话 id，写操作生成审批单时要记下"是哪次对话里提的"
     * @return 给模型看的字符串（建议是 JSON；出错时返回带 error 字段的 JSON，而不是抛异常，
     *         让模型有机会自己解释给用户）
     */
    public String execute(Long userId, Long conversationId, String name, String argumentsJson) {
        String result = dispatch(userId, conversationId, name, argumentsJson);
        writeAudit(conversationId, userId, name, argumentsJson, result);
        return result;
    }

    private String dispatch(Long userId, Long conversationId, String name, String argumentsJson) {
        return switch (name) {
            case "get_order_status" -> getOrderStatus(userId, argumentsJson);
            case "list_my_orders" -> listMyOrders(userId, argumentsJson);
            case "get_sku_stock" -> getSkuStock(argumentsJson);
            case "list_my_refunds" -> listMyRefunds(userId, argumentsJson);
            case "list_pending_shipments" -> listPendingShipments(userId, argumentsJson);
            case "restock_sku" -> requestRestock(userId, conversationId, argumentsJson);
            case "ship_order" -> requestShip(userId, conversationId, argumentsJson);
            default -> "{\"error\":\"未知工具：" + name + "\"}";
        };
    }

    /**
     * 写审计。失败只记日志，绝不因为"记账失败"而让用户的请求失败 ——
     * 审计是旁路，不是主链路。
     */
    private void writeAudit(Long conversationId, Long userId, String toolName,
            String request, String result) {
        try {
            AiToolAudit audit = new AiToolAudit();
            audit.setConversationId(conversationId);
            audit.setActorUserId(userId);
            audit.setToolName(toolName);
            audit.setRequestSummary(truncate(request));
            audit.setResultSummary(truncate(result));
            audit.setSuccess(result != null && result.startsWith("{\"error\"") ? 0 : 1);
            auditMapper.insert(audit);
        } catch (Exception ex) {
            log.warn("写工具审计失败：tool={}, conversation={}", toolName, conversationId, ex);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    /**
     * 补货：不执行，只生成待确认记录。
     *
     * 返回给模型的是「已提交审批」，不是「已完成」—— 这一点必须在返回文案里写清楚，
     * 否则模型会向用户谎报"已经补好了"。
     */
    private String requestRestock(Long userId, Long conversationId, String argumentsJson) {
        try {
            JsonNode args = jsonMapper.readTree(argumentsJson);
            Long skuId = args.path("skuId").asLong();
            int qty = args.path("qty").asInt();

            if (qty <= 0) {
                return "{\"error\":\"补货数量必须大于0\"}";
            }

            Store store;
            try {
                store = skuInventoryService.requireOwnedStoreOfSku(userId, skuId);
            } catch (ResponseStatusException ex) {
                return "{\"error\":\"该SKU不存在，或不属于你的店铺\"}";
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("skuId", skuId);
            payload.put("qty", qty);
            String payloadJson = jsonMapper.writeValueAsString(payload);

            AiActionApproval approval = approvalService.create(userId, conversationId, store.getId(), "RESTOCK",
                    payloadJson);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "PENDING_APPROVAL");
            result.put("approvalNo", approval.getApprovalNo());
            result.put("description", "给 SKU " + skuId + " 补货 " + qty + " 件");
            result.put("hint", "该操作尚未执行，必须由用户确认后才生效。请明确告诉用户「已提交，等待确认」，不要说已经补好了。");
            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"补货申请创建失败\"}";
        }

    }

    /** 列当前用户最近的订单：用户往往不知道订单号，这是最常用的一步 */
    private String listMyOrders(Long userId, String argumentsJson) {
        try {
            int limit = parseLimit(argumentsJson);

            List<TradeOrder> orders = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getUserId, userId)
                    .orderByDesc(TradeOrder::getId)
                    .last("LIMIT " + limit));

            List<Map<String, Object>> views = new ArrayList<>();
            for (TradeOrder order : orders) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("orderNo", order.getOrderNo());
                view.put("status", order.getStatus());
                view.put("payableAmount", order.getPayableAmount());
                view.put("createdAt", order.getCreatedAt() == null ? null : order.getCreatedAt().toString());
                views.add(view);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", views.size());
            result.put("orders", views);
            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"查询订单列表失败\"}";
        }
    }

    /** 列当前用户的退款申请进度 */
    private String listMyRefunds(Long userId, String argumentsJson) {
        try {
            int limit = parseLimit(argumentsJson);

            List<RefundRequest> refunds = refundRequestMapper.selectList(
                    new LambdaQueryWrapper<RefundRequest>()
                            .eq(RefundRequest::getUserId, userId)
                            .orderByDesc(RefundRequest::getId)
                            .last("LIMIT " + limit));

            List<Map<String, Object>> views = new ArrayList<>();
            for (RefundRequest refund : refunds) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("refundNo", refund.getRefundNo());
                view.put("quantity", refund.getQuantity());
                view.put("refundAmount", refund.getRefundAmount());
                view.put("status", refund.getStatus());
                view.put("createdAt", refund.getCreatedAt() == null ? null : refund.getCreatedAt().toString());
                views.add(view);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", views.size());
            result.put("refunds", views);
            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"查询退款记录失败\"}";
        }
    }

    /** 商家：列自己店铺待发货的子单。发货前必须先拿到 storeOrderId，不能让模型猜 */
    private String listPendingShipments(Long userId, String argumentsJson) {
        try {
            Long storeId = findOwnedStoreId(userId);
            if (storeId == null) {
                return "{\"error\":\"你不是任何店铺的商家，无法查看发货列表\"}";
            }

            int limit = parseLimit(argumentsJson);

            List<StoreOrder> subs = storeOrderMapper.selectList(new LambdaQueryWrapper<StoreOrder>()
                    .eq(StoreOrder::getStoreId, storeId)
                    .eq(StoreOrder::getStatus, "PAID")
                    .orderByDesc(StoreOrder::getId)
                    .last("LIMIT " + limit));

            List<Map<String, Object>> views = new ArrayList<>();
            for (StoreOrder sub : subs) {
                Map<String, Object> view = new LinkedHashMap<>();
                // 子单号放第一个：模型会照着它去调 ship_order，而不是去猜内部 id
                view.put("subOrderNo", sub.getSubOrderNo());
                view.put("payableAmount", sub.getPayableAmount());
                view.put("status", sub.getStatus());
                views.add(view);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("storeId", storeId);
            result.put("count", views.size());
            result.put("pendingShipments", views);
            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"查询待发货列表失败\"}";
        }
    }

    /**
     * 发货：不执行，只生成待确认记录。
     *
     * 参数用「子单号」而不是数据库主键 —— 模型很容易把 id 编错（实测编过一次，
     * 差点把另一笔订单发出去），而子单号是用户在页面上能核对的东西。
     *
     * 物流单号必须由用户提供 —— 模型编一个单号，商家真的贴到包裹上，
     * 买家查不到物流，这是最典型的"AI 幻觉造成的现实损失"。
     */
    private String requestShip(Long userId, Long conversationId, String argumentsJson) {
        try {
            JsonNode args = jsonMapper.readTree(argumentsJson);
            String subOrderNo = args.path("subOrderNo").asText();
            String carrierCode = args.path("carrierCode").asText();
            String trackingNo = args.path("trackingNo").asText();

            if (subOrderNo.isBlank()) {
                return "{\"error\":\"必须提供子单号，请先调用 list_pending_shipments 获取\"}";
            }
            if (carrierCode.isBlank() || trackingNo.isBlank()) {
                return "{\"error\":\"快递公司和快递单号都必须提供。用户没给单号时请先询问，不要编造\"}";
            }

            // 用业务单号查，不信任任何数字 id
            StoreOrder sub = storeOrderMapper.selectOne(new LambdaQueryWrapper<StoreOrder>()
                    .eq(StoreOrder::getSubOrderNo, subOrderNo));
            if (sub == null) {
                return "{\"error\":\"子单不存在，请确认单号是否正确\"}";
            }

            Long storeId = findOwnedStoreId(userId);
            if (storeId == null || !storeId.equals(sub.getStoreId())) {
                return "{\"error\":\"该子单不存在，或不属于你的店铺\"}";
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("storeOrderId", sub.getId());
            payload.put("subOrderNo", sub.getSubOrderNo());
            payload.put("carrierCode", carrierCode);
            payload.put("trackingNo", trackingNo);
            String payloadJson = jsonMapper.writeValueAsString(payload);

            AiActionApproval approval = approvalService.create(userId, conversationId, storeId, "SHIP",
                    payloadJson);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "PENDING_APPROVAL");
            result.put("approvalNo", approval.getApprovalNo());
            result.put("description", "给子单 " + sub.getSubOrderNo() + " 发货（" + carrierCode + " " + trackingNo + "）");
            result.put("hint", "发货尚未执行，必须由用户确认后才生效。请明确告诉用户「已提交，等待确认」，并复述子单号让用户核对。");
            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"发货申请创建失败\"}";
        }
    }

    /** 列表工具的 limit：默认 5，最多 10，防止模型一次拉太多把上下文撑爆 */
    private int parseLimit(String argumentsJson) {
        try {
            JsonNode args = jsonMapper.readTree(argumentsJson == null || argumentsJson.isBlank()
                    ? "{}"
                    : argumentsJson);
            int limit = args.path("limit").asInt(5);
            if (limit <= 0) {
                return 5;
            }
            return Math.min(limit, 10);
        } catch (Exception ex) {
            return 5;
        }
    }

    private String getOrderStatus(Long userId, String argumentsJson) {
        try {
            JsonNode args = jsonMapper.readTree(argumentsJson);
            String orderNo = args.path("orderNo").asText();

            // 关键：必须带 userId，模型不能查别人的订单
            TradeOrder order = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getOrderNo, orderNo)
                    .eq(TradeOrder::getUserId, userId));

            if (order == null) {
                return "{\"error\":\"未找到该订单，可能订单号有误或不属于当前用户\"}";
            }

            List<StoreOrder> subs = storeOrderMapper.selectList(
                    new LambdaQueryWrapper<StoreOrder>()
                            .eq(StoreOrder::getTradeOrderId, order.getId()));

            List<Map<String, Object>> subViews = new ArrayList<>();
            for (StoreOrder sub : subs) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("storeId", sub.getStoreId());
                view.put("status", sub.getStatus());
                view.put("payableAmount", sub.getPayableAmount());

                Shipment shipment = shipmentMapper.selectOne(
                        new LambdaQueryWrapper<Shipment>()
                                .eq(Shipment::getStoreOrderId, sub.getId()));
                if (shipment != null) {
                    view.put("carrierCode", shipment.getCarrierCode());
                    view.put("trackingNo", shipment.getTrackingNo());
                }
                subViews.add(view);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderNo", order.getOrderNo());
            result.put("status", order.getStatus());
            result.put("payableAmount", order.getPayableAmount());
            result.put("paidAt", order.getPaidAt() == null ? null : order.getPaidAt().toString());
            result.put("storeOrders", subViews);

            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"查询订单失败\"}";
        }
    }

    private String getSkuStock(String argumentsJson) {
        try {
            JsonNode args = jsonMapper.readTree(argumentsJson);
            long skuId = args.path("skuId").asLong();

            SkuInventory inventory = skuInventoryMapper.selectById(skuId);
            if (inventory == null) {
                return "{\"error\":\"该 SKU 没有库存记录\"}";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("skuId", skuId);
            result.put("availableQty", inventory.getAvailableQty());
            result.put("soldQty", inventory.getSoldQty());

            return jsonMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"查询库存失败\"}";
        }
    }
}
