package iuh.fit.workflowservice.application.dto.request.venue;

public record ReviewOwnerApplicationRequest(
        String reviewerId,
        String rejectReason
) {
}
