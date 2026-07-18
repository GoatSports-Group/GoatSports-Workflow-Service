package iuh.fit.workflowservice.application.dto.request.storage;

public record PresignedUrlRequest(
        String fileName,
        String contentType,
        String folder
) {}
