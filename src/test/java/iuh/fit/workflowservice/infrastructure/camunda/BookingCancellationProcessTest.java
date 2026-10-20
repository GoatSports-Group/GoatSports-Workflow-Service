package iuh.fit.workflowservice.infrastructure.camunda;

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import iuh.fit.workflowservice.application.dto.cancellation.CancellationState;
import iuh.fit.workflowservice.application.dto.cancellation.CreatedRefund;
import iuh.fit.workflowservice.application.dto.cancellation.StartCancellationCommand;
import iuh.fit.workflowservice.application.exception.CancellationMissingException;
import iuh.fit.workflowservice.application.port.CancellationProcessPort;
import iuh.fit.workflowservice.application.port.VenueCancellationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chạy process thật trên Camunda (Testcontainers) với worker thật; chỉ venue-service được giả lập.
 * Kiểm tra trạng thái trong Camunda (biến "status") và lệnh ghi sang venue-service luôn khớp nhau.
 */
@SpringBootTest(properties = {
        "camunda.client.deployment.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "workflow.cancellation.reconcile-initial-delay-ms=3600000"
})
@ActiveProfiles("test")
@CamundaSpringProcessTest
@EnabledIf(value = "dockerAvailable", disabledReason = "Cần Docker để chạy Camunda (Testcontainers)")
class BookingCancellationProcessTest {
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired CancellationProcessPort process;
    @Autowired CamundaProcessTestContext camunda;
    @MockitoBean VenueCancellationPort venue;

    private final UUID cancellationId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();

    @Test
    void automaticCancellationWithoutDepositEndsApproved() {
        long key = process.start(start("AUTOMATIC", 0, "Tự động hủy booking chưa thanh toán.", inHours(24)));

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("Task_StateApproved", "EndEvent_Approved")
                .hasVariable("status", "APPROVED");
        verify(venue).applyState(argThat(state("APPROVED")));
        verify(venue, never()).createRefund(any(), any(), any());
    }

    @Test
    void ownerRejectionIsWrittenWithTheOwnersNote() {
        long key = process.start(start("OWNER_REVIEW", 150_000, null, inHours(24)));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Task_WaitOwnerDecision");

        assertThat(process.decide(cancellationId, false, ownerId, "Sân đã xếp lịch cho đội khác")).isTrue();

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("EndEvent_Rejected")
                .hasVariable("status", "REJECTED");
        verify(venue).applyState(argThat(state -> "REJECTED".equals(state.status())
                && ownerId.equals(state.processedBy())
                && "Sân đã xếp lịch cho đội khác".equals(state.decisionNote())));
        // Đã quyết định rồi thì không còn gì để quyết định nữa.
        assertThat(process.decide(cancellationId, true, ownerId, null)).isFalse();
    }

    @Test
    void approvedRefundFollowsPaymentEventsUntilRefunded() {
        when(venue.createRefund(bookingId, cancellationId, ownerId))
                .thenReturn(new CreatedRefund(refundId, "AWAITING_CLAIM", null));
        long key = process.start(start("OWNER_REVIEW", 150_000, null, inHours(24)));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Task_WaitOwnerDecision");

        process.decide(cancellationId, true, ownerId, "Đồng ý");
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Event_RefundStatusChanged")
                .hasVariable("status", "REFUND_AWAITING_CLAIM");

        process.publishRefundStatus(refundId, "FAILED", "payOS từ chối", "event-1");
        assertThat(ProcessInstanceSelectors.byKey(key)).hasVariable("status", "REFUND_FAILED");
        verify(venue, timeout(WAIT.toMillis())).applyState(argThat(state ->
                "REFUND_FAILED".equals(state.status()) && "payOS từ chối".equals(state.refundFailureReason())));

        // Kafka giao lại cùng event: không được chạy lại vòng lặp lần nữa.
        process.publishRefundStatus(refundId, "FAILED", "payOS từ chối", "event-1");
        process.publishRefundStatus(refundId, "SUCCEEDED", null, "event-2");

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("EndEvent_Refunded")
                .hasVariable("status", "REFUNDED");
        verify(venue, times(1)).applyState(argThat(state("REFUND_FAILED")));
        verify(venue).applyState(argThat(state("REFUNDED")));
    }

    @Test
    void ownerSilenceIsRemindedThenAutoApprovedWhenPlayStarts() {
        long key = process.start(start("OWNER_REVIEW", 0, null, inHours(8)));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Task_WaitOwnerDecision");

        camunda.increaseTime(Duration.ofHours(7));
        verify(venue, timeout(WAIT.toMillis())).remindOwner(bookingId, cancellationId);
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Task_WaitOwnerDecision");

        camunda.increaseTime(Duration.ofHours(2));
        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("Event_AutoApprove", "EndEvent_Approved")
                .hasVariable("status", "APPROVED");
        verify(venue).applyState(argThat(state -> "APPROVED".equals(state.status())
                && state.processedBy() == null
                && state.decisionNote().startsWith("Tự động chấp thuận")));
    }

    @Test
    void adoptedRefundWaitsForPaymentFromWhereItWas() {
        long key = process.start(new StartCancellationCommand(cancellationId, bookingId, "OWNER_REVIEW", 150_000,
                inHours(24), null, "REFUND_MANUAL_REVIEW", refundId));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Event_RefundStatusChanged");

        process.publishRefundStatus(refundId, "SUCCEEDED", null, "event-manual");

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted().hasVariable("status", "REFUNDED");
        verify(venue, never()).createRefund(any(), any(), any());
    }

    @Test
    void cancellationMissingInVenueEndsTheProcessInsteadOfRetrying() {
        doThrow(new CancellationMissingException("gone")).when(venue).applyState(any());

        long key = process.start(start("AUTOMATIC", 0, null, inHours(24)));

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("EndEvent_CancellationMissing");
    }

    @Test
    void activeStatesExposeCamundaStatusForReconciliation() {
        when(venue.createRefund(eq(bookingId), eq(cancellationId), any()))
                .thenReturn(new CreatedRefund(refundId, "AWAITING_BANK_ACCOUNT", "Liên kết ngân hàng"));
        long key = process.start(start("AUTOMATIC", 50_000, "Tự động chấp thuận.", inHours(24)));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Event_RefundStatusChanged");

        List<CancellationState> states = awaitActiveState();

        CancellationState state = states.stream().filter(item -> item.cancellationId().equals(cancellationId))
                .findFirst().orElseThrow();
        assertThat(state.status()).isEqualTo("REFUND_AWAITING_BANK_ACCOUNT");
        assertThat(state.version()).isPositive();
        assertThat(state.refundFailureReason()).isEqualTo("Liên kết ngân hàng");
    }

    private List<CancellationState> awaitActiveState() {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (true) {
            List<CancellationState> states = process.activeStates();
            if (states.stream().anyMatch(item -> item.cancellationId().equals(cancellationId))
                    || System.nanoTime() > deadline) return states;
            try {
                Thread.sleep(300);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return states;
            }
        }
    }

    private StartCancellationCommand start(String reviewMode, double refundAmount, String note, String playStartAt) {
        return new StartCancellationCommand(cancellationId, bookingId, reviewMode, refundAmount, playStartAt, note,
                null, null);
    }

    private String inHours(long hours) {
        return camunda.getCurrentTime().plus(Duration.ofHours(hours)).atOffset(ZoneOffset.ofHours(7)).toString();
    }

    private ArgumentMatcher<CancellationState> state(String status) {
        return state -> state != null && status.equals(state.status()) && cancellationId.equals(state.cancellationId());
    }
}
