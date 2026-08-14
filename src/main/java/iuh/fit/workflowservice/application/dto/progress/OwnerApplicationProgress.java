package iuh.fit.workflowservice.application.dto.progress;

import java.util.UUID;

public record OwnerApplicationProgress(
        UUID ownerApplicationId,
        String receivedAt,
        String viewedAt
) { }
