package iuh.fit.workflowservice.presentation.controller.internal;

import iuh.fit.workflowservice.application.dto.cancellation.StartCancellationCommand;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.port.CancellationProcessPort;
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
@RequestMapping("/internal/booking-cancellations")
@RequiredArgsConstructor
public class BookingCancellationWorkflowController {
    private final CancellationProcessPort cancellationProcess;

    @PostMapping
    public ResponseEntity<Map<String, Long>> start(@Valid @RequestBody StartRequest request) {
        long processInstanceKey = cancellationProcess.start(new StartCancellationCommand(
                request.cancellationId(), request.bookingId(), request.reviewMode(), request.refundAmount(),
                request.playStartAt(), request.decisionNote(), request.resumeStatus(), request.refundId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("processInstanceKey", processInstanceKey));
    }

    @PostMapping("/{cancellationId}/decision")
    public ResponseEntity<Object> decide(@PathVariable UUID cancellationId, @Valid @RequestBody DecisionRequest request) {
        if (cancellationProcess.decide(cancellationId, request.approved(), request.processedBy(), request.decisionNote())) {
            return ResponseEntity.noContent().build();
        }
        BaseResponse<Object> conflict = new BaseResponse<>(HttpStatus.CONFLICT.value(), null,
                "Yêu cầu hủy không còn chờ chủ sân duyệt.", "CANCELLATION_NOT_AWAITING_DECISION");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
    }

    public record StartRequest(
            @NotNull UUID cancellationId,
            @NotNull UUID bookingId,
            @NotBlank String reviewMode,
            @NotNull Double refundAmount,
            @NotBlank String playStartAt,
            String decisionNote,
            String resumeStatus,
            UUID refundId
    ) {
    }

    public record DecisionRequest(@NotNull Boolean approved, @NotNull UUID processedBy, String decisionNote) {
    }
}
