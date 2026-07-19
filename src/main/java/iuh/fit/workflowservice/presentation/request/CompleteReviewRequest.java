package iuh.fit.workflowservice.presentation.request;

import jakarta.validation.constraints.NotNull;

public record CompleteReviewRequest(
        @NotNull(message = "Bạn phải cung cấp trạng thái đơn")
        Boolean approved,
        String rejectReason
) {
}