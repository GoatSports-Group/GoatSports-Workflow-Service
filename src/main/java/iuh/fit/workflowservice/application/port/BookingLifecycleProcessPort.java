package iuh.fit.workflowservice.application.port;

import iuh.fit.workflowservice.application.dto.lifecycle.StartBookingLifecycleCommand;

import java.util.Set;
import java.util.UUID;

/** Process "Vòng đời đặt sân" trên Camunda. */
public interface BookingLifecycleProcessPort {
    /** Các message venue-service được phép gửi (tên message trong BPMN). */
    Set<String> MESSAGES = Set.of(
            "BookingDepositPaid", "BookingPaymentClosed", "BookingRemainingPaid", "BookingCheckedIn", "BookingCancelled"
    );

    /** @return process instance key */
    long start(StartBookingLifecycleCommand command);

    /** Báo process của đơn đi tiếp; gửi lặp cùng messageId thì bỏ qua. */
    void publish(UUID bookingId, String messageName, String messageId);
}
