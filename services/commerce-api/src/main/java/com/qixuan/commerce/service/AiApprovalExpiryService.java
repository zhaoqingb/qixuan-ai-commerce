package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.entity.AiActionApproval;
import com.qixuan.commerce.mapper.AiActionApprovalMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把审批单标记为「已过期」。
 *
 * 为什么单独一个类？
 * 因为调用它的场景是「标记完过期后立刻抛 409 拒绝用户」——
 * 如果和抛异常放在同一个事务里，这次 UPDATE 会被一起回滚，
 * 结果是"拒绝了用户，但单据还是 PENDING"。
 *
 * REQUIRES_NEW 让这次更新独立提交，不受外层事务回滚影响；
 * 而 Spring 的事务代理只在「跨 Bean 调用」时生效，
 * 所以这个能力必须放在另一个 Bean 里，不能是同类内部方法。
 */
@Service
public class AiApprovalExpiryService {

    private final AiActionApprovalMapper approvalMapper;

    public AiApprovalExpiryService(AiActionApprovalMapper approvalMapper) {
        this.approvalMapper = approvalMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExpired(Long approvalId) {
        AiActionApproval patch = new AiActionApproval();
        patch.setStatus("EXPIRED");
        approvalMapper.update(patch, new LambdaUpdateWrapper<AiActionApproval>()
                .eq(AiActionApproval::getId, approvalId)
                .eq(AiActionApproval::getStatus, "PENDING"));
    }
}
