package iuh.fit.workflowservice.application.port.progress;

import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProcess;
import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProgress;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerApplicationProgressStore {
    List<OwnerApplicationProgress> findOwnedByIds(
            Collection<UUID> ownerApplicationIds,
            UUID ownerId
    );

    Optional<OwnerApplicationProcess> findByOwnerApplicationId(UUID ownerApplicationId);

    boolean markViewedIfAbsent(
            long processInstanceKey,
            UUID ownerApplicationId,
            String viewedAt,
            UUID viewerId
    );

    boolean markReceivedIfAbsent(long processInstanceKey, String receivedAt);
}
