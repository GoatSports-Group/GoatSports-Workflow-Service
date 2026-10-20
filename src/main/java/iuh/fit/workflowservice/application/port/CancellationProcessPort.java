package iuh.fit.workflowservice.application.port;

import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.dto.cancellation.StartCancellationCommand;

import java.util.List;
import java.util.UUID;

/** Process "Hủy đặt sân và hoàn tiền" trên Camunda. */
public interface CancellationProcessPort {
    /** @return process instance key */
    long start(StartCancellationCommand command);

    /** @return false khi process không còn chờ chủ sân (đã quyết định, đã tự chấp thuận khi tới giờ chơi...) */
    boolean decide(UUID cancellationId, boolean approved, UUID processedBy, String decisionNote);

    /** Chuyển thay đổi trạng thái khoản hoàn của payment-service vào process đang chờ khoản hoàn đó. */
    void publishRefundStatus(UUID refundId, String refundStatus, String failureReason, String messageId);

    /** Trạng thái hiện tại của các process còn chạy, để đối soát lại bản chiếu ở venue-service. */
    List<CancellationState> activeStates();
}
