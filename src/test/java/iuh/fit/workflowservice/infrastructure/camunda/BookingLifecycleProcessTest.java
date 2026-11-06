package iuh.fit.workflowservice.infrastructure.camunda;

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import iuh.fit.workflowservice.application.dto.lifecycle.StartBookingLifecycleCommand;
import iuh.fit.workflowservice.application.exception.BookingMissingException;
import iuh.fit.workflowservice.application.port.BookingLifecycleProcessPort;
import iuh.fit.workflowservice.application.port.VenueBookingLifecyclePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đang chờ ở event-based gateway thì gateway là phần tử active; catch event phía sau chỉ được kích hoạt khi tới.
 * Chạy process "Vòng đời đặt sân" thật trên Camunda (Testcontainers) với worker thật; chỉ venue-service được giả lập
 * (trả về trạng thái đơn sau khi ghi).
 */
@SpringBootTest(properties = {
        "camunda.client.deployment.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "workflow.cancellation.reconcile-initial-delay-ms=3600000"
})
@ActiveProfiles("test")
@CamundaSpringProcessTest
@EnabledIf(value = "dockerAvailable", disabledReason = "Cần Docker để chạy Camunda (Testcontainers)")
class BookingLifecycleProcessTest {
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired BookingLifecycleProcessPort process;
    @Autowired CamundaProcessTestContext camunda;
    @MockitoBean VenueBookingLifecyclePort venue;

    private final UUID bookingId = UUID.randomUUID();

    @Test
    void paidBookingIsCheckedInAfterRemainingPaymentThenCompletedWhenPlayEnds() {
        when(venue.applyState(bookingId, "COMPLETED")).thenReturn("COMPLETED");
        long key = process.start(pending(2));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Gateway_WaitDeposit");

        process.publish(bookingId, "BookingDepositPaid", "deposit-1");
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Gateway_WaitArrival");
        // Quầy thu tiền rồi nhận sân liền nhau: message sau tới trước khi process sang bước chờ nó vẫn được giữ.
        process.publish(bookingId, "BookingRemainingPaid", "remaining-1");
        process.publish(bookingId, "BookingCheckedIn", "checked-in-1");
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Event_PlayEnded");

        camunda.increaseTime(Duration.ofHours(3));
        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("Event_RemainingPaid", "Event_CheckedIn", "Task_Complete", "End_Completed")
                .hasVariable("bookingStatus", "COMPLETED");
        verify(venue, never()).applyState(any(), eq("EXPIRED"));
    }

    @Test
    void unpaidHoldExpiresAndReleasesTheSlot() {
        when(venue.applyState(bookingId, "EXPIRED")).thenReturn("EXPIRED");
        long key = process.start(pending(5));

        camunda.increaseTime(Duration.ofMinutes(20));

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted().hasCompletedElements("End_Expired");
        verify(venue).applyState(bookingId, "EXPIRED");
    }

    @Test
    void missedDepositMessageIsRecoveredWhenVenueRefusesToExpire() {
        // venue-service đã xác nhận cọc nhưng message bị lỡ: không cho hết hạn, process quay về chờ khách đến.
        when(venue.applyState(bookingId, "EXPIRED")).thenReturn("CONFIRMED");
        long key = process.start(pending(5));

        camunda.increaseTime(Duration.ofMinutes(20));

        assertThat(ProcessInstanceSelectors.byKey(key)).isActive().hasActiveElements("Gateway_WaitArrival");
    }

    @Test
    void confirmedBookingNobodyCollectsBecomesNoShow() {
        when(venue.applyState(bookingId, "NO_SHOW")).thenReturn("NO_SHOW");
        long key = process.start(new StartBookingLifecycleCommand(bookingId, "CONFIRMED", false, null, inHours(2)));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Gateway_WaitArrival");

        camunda.increaseTime(Duration.ofHours(3));

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted().hasCompletedElements("End_NoShow");
    }

    @Test
    void missedCheckInMessageCompletesInsteadOfNoShow() {
        when(venue.applyState(bookingId, "NO_SHOW")).thenReturn("CHECKED_IN");
        when(venue.applyState(bookingId, "COMPLETED")).thenReturn("COMPLETED");
        long key = process.start(new StartBookingLifecycleCommand(bookingId, "CONFIRMED", true, null, inHours(2)));
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Gateway_WaitCheckIn");

        camunda.increaseTime(Duration.ofHours(3));

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted()
                .hasCompletedElements("Task_NoShow", "Task_Complete", "End_Completed");
    }

    @Test
    void approvedCancellationStopsTheLifecycleWithoutTouchingTheBooking() {
        long key = process.start(pending(15));
        process.publish(bookingId, "BookingDepositPaid", "deposit-1");
        assertThat(ProcessInstanceSelectors.byKey(key)).hasActiveElements("Gateway_WaitArrival");

        process.publish(bookingId, "BookingCancelled", "cancelled-1");

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted().hasCompletedElements("End_Cancelled");
        camunda.increaseTime(Duration.ofDays(1));
        verify(venue, never()).applyState(any(), anyString());
    }

    @Test
    void paymentClosedEndsWithoutWritingState() {
        long key = process.start(pending(15));

        process.publish(bookingId, "BookingPaymentClosed", "closed-1");

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted().hasCompletedElements("End_PaymentClosed");
        verify(venue, never()).applyState(any(), anyString());
    }

    @Test
    void bookingMissingInVenueEndsTheProcessInsteadOfRetrying() {
        when(venue.applyState(bookingId, "EXPIRED")).thenThrow(new BookingMissingException("gone"));
        long key = process.start(pending(5));

        camunda.increaseTime(Duration.ofMinutes(20));

        assertThat(ProcessInstanceSelectors.byKey(key)).isCompleted().hasCompletedElements("End_BookingMissing");
    }

    private StartBookingLifecycleCommand pending(long holdMinutes) {
        return new StartBookingLifecycleCommand(bookingId, "PENDING_PAYMENT", false,
                at(Duration.ofMinutes(holdMinutes)), inHours(2));
    }

    private String inHours(long hours) {
        return at(Duration.ofHours(hours));
    }

    private String at(Duration fromNow) {
        return camunda.getCurrentTime().plus(fromNow).atOffset(ZoneOffset.ofHours(7)).toString();
    }
}
