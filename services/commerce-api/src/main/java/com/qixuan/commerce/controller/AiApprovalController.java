package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ApprovalResult;
import com.qixuan.commerce.dto.ApprovalView;
import com.qixuan.commerce.service.AiApprovalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 写操作的人工确认入口。
 *
 * 模型不能直接改业务数据，只能生成 PENDING 记录；这里是"真人点头"的那道门。
 */
@RestController
@RequestMapping("/api/ai/approvals")
public class AiApprovalController {

    private final AiApprovalService approvalService;

    public AiApprovalController(AiApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<ApprovalView> list(@RequestAttribute("userId") Long userId) {
        return approvalService.listPending(userId);
    }

    @PostMapping("/{id}/approve")
    public ApprovalResult approve(@RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        return approvalService.approve(userId, id);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        approvalService.reject(userId, id);
    }
}
