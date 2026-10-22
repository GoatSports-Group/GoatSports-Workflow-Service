package iuh.fit.workflowservice.infrastructure.external;

import feign.FeignException;
import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.dto.cancellation.CreatedRefund;
import iuh.fit.workflowservice.application.exception.CancellationMissingException;
import iuh.fit.workflowservice.application.port.VenueCancellationPort;
import iuh.fit.workflowservice.infrastructure.external.client.VenueCancellationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class VenueCancellationAdapter implements VenueCancellationPort {
    private final VenueCancellationClient client;

    @Override
    public void applyState(CancellationState state) {
        call(state.cancellationId(), () -> client.applyState(state.cancellationId(), new VenueCancellationClient.StateRequest(
                state.bookingId(), state.status(), state.version(), state.processedBy(), state.decisionNote(),
                state.refundFailureReason())));
    }

    @Override
    public CreatedRefund createRefund(UUID bookingId, UUID cancellationId, UUID processedBy) {
        VenueCancellationClient.RefundResponse refund = call(cancellationId, () -> client.createRefund(
                cancellationId, new VenueCancellationClient.RefundRequest(bookingId, processedBy))).getData();
        if (refund == null || refund.refundId() == null) {
            throw new IllegalStateException("venue-service không trả về khoản hoàn của yêu cầu hủy " + cancellationId);
        }
        return new CreatedRefund(refund.refundId(), refund.status(), refund.failureReason());
    }

    @Override
    public void remindOwner(UUID bookingId, UUID cancellationId) {
        call(cancellationId, () -> client.remindOwner(cancellationId, new VenueCancellationClient.ReminderRequest(bookingId)));
    }

    private static <T> T call(UUID cancellationId, Supplier<T> request) {
        try {
            return request.get();
        } catch (FeignException.NotFound exception) {
            throw new CancellationMissingException("venue-service không còn yêu cầu hủy " + cancellationId);
        }
    }
}
