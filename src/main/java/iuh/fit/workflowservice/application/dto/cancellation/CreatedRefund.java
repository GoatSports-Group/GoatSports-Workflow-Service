package iuh.fit.workflowservice.application.dto.cancellation;

import java.util.UUID;

/** Khoản hoàn payment-service vừa tạo cho yêu cầu hủy; {@code status} là RefundStatus của payment-service. */
public record CreatedRefund(UUID refundId, String status, String failureReason) {
}
