package iuh.fit.workflowservice.application.dto.progress;

import java.util.UUID;

public record OwnerApplicationProcess(
        long processInstanceKey,
        UUID ownerApplicationId,
        UUID userId
) { }
