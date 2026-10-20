package iuh.fit.workflowservice.infrastructure.scheduling;

import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.port.CancellationProcessPort;
import iuh.fit.workflowservice.application.port.VenueCancellationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Camunda là nguồn sự thật của trạng thái yêu cầu hủy. Định kỳ đẩy lại trạng thái của mọi process đang chạy sang
 * venue-service: bản ghi DB lệch (sửa tay, lần ghi trước lỗi) được kéo về đúng trạng thái trong Camunda. Venue-service
 * chỉ nhận bản có version không nhỏ hơn bản đã ghi, nên dữ liệu search hơi trễ không làm lùi trạng thái.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancellationStateReconciler {
    private final CancellationProcessPort cancellationProcess;
    private final VenueCancellationPort venue;

    @Scheduled(fixedDelayString = "${workflow.cancellation.reconcile-delay-ms:600000}",
            initialDelayString = "${workflow.cancellation.reconcile-initial-delay-ms:120000}")
    public void reconcile() {
        int pushed = 0;
        for (CancellationState state : cancellationProcess.activeStates()) {
            try {
                venue.applyState(state);
                pushed++;
            } catch (RuntimeException exception) {
                log.warn("Đối soát yêu cầu hủy {} chưa được: {}", state.cancellationId(), exception.getMessage());
            }
        }
        if (pushed > 0) log.info("Đã đối soát {} yêu cầu hủy theo trạng thái trong Camunda", pushed);
    }
}
