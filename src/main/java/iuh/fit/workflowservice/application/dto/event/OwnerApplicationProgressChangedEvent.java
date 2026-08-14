package iuh.fit.workflowservice.application.dto.event;

import java.util.UUID;

public record OwnerApplicationProgressChangedEvent(
        String type,
        UUID userId,
        UUID ownerApplicationId
) {
    private static final String PROGRESS_CHANGED = "OWNER_APPLICATION_PROGRESS_CHANGED";

    public static OwnerApplicationProgressChangedEvent changed(UUID userId, UUID ownerApplicationId) {
        return new OwnerApplicationProgressChangedEvent(PROGRESS_CHANGED, userId, ownerApplicationId);
    }
}
