package iuh.fit.workflowservice.presentation.controller.internal;

import iuh.fit.workflowservice.application.dto.lifecycle.StartBookingLifecycleCommand;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.port.BookingLifecycleProcessPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/booking-lifecycles")
@RequiredArgsConstructor
public class BookingLifecycleWorkflowController {
    private final BookingLifecycleProcessPort lifecycleProcess;

    @PostMapping
    public ResponseEntity<Map<String, Long>> start(@Valid @RequestBody StartRequest request) {
        long processInstanceKey = lifecycleProcess.start(new StartBookingLifecycleCommand(
                request.bookingId(), request.status(), Boolean.TRUE.equals(request.remainingPaid()),
                request.holdExpiresAt(), request.playEndAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("processInstanceKey", processInstanceKey));
    }

    @PostMapping("/{bookingId}/messages")
    public ResponseEntity<Object> publish(@PathVariable UUID bookingId, @Valid @RequestBody MessageRequest request) {
        if (!BookingLifecycleProcessPort.MESSAGES.contains(request.name())) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(HttpStatus.BAD_REQUEST.value(), null,
                    "Message không thuộc process vòng đời đặt sân.", "UNKNOWN_LIFECYCLE_MESSAGE"));
        }
        lifecycleProcess.publish(bookingId, request.name(), request.messageId());
        return ResponseEntity.noContent().build();
    }

    public record StartRequest(
            @NotNull UUID bookingId,
            @NotBlank String status,
            Boolean remainingPaid,
            String holdExpiresAt,
            @NotBlank String playEndAt
    ) {
    }

    public record MessageRequest(@NotBlank String name, @NotBlank String messageId) {
    }
}
