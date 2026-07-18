package iuh.fit.workflowservice.application.dto.response.venue;

import java.util.UUID;

public record DocumentResponse(
    UUID ownerApplicationDocumentId,
    String documentType,
    String fileUrl
) {
}