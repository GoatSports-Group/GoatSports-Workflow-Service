package iuh.fit.workflowservice.application.dto.response.storage;

public record PresignedUrlResponse(String uploadUrl, String objectKey) {
}
