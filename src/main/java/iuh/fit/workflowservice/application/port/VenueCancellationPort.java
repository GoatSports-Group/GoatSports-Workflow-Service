package iuh.fit.workflowservice.application.port;

import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.dto.cancellation.CreatedRefund;

import java.util.UUID;

/**
 * Các thao tác venue-service làm theo lệnh của process. Mọi hàm ném
 * {@link iuh.fit.workflowservice.application.exception.CancellationMissingException} khi venue-service không còn
 * yêu cầu hủy đó.
 */
public interface VenueCancellationPort {
    void applyState(CancellationState state);

    CreatedRefund createRefund(UUID bookingId, UUID cancellationId, UUID processedBy);

    void remindOwner(UUID bookingId, UUID cancellationId);
}
