package iuh.fit.workflowservice.application.dto.request.venue;

import java.util.List;

public record CreateDocumentsRequest(
        String ownerApplicationId,
        List<String> documentKeys
) {}