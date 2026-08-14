package iuh.fit.workflowservice.application.service;

import iuh.fit.workflowservice.application.dto.event.OwnerApplicationProgressChangedEvent;
import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProcess;
import iuh.fit.workflowservice.application.dto.progress.OwnerApplicationProgress;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.notification.OwnerApplicationProgressPublisher;
import iuh.fit.workflowservice.application.port.progress.OwnerApplicationProgressStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerApplicationProgressService {
    private final OwnerApplicationProgressStore progressStore;
    private final OwnerApplicationProgressPublisher progressPublisher;

    public List<OwnerApplicationProgress> findMyProgress(
            Collection<UUID> ownerApplicationIds,
            UUID ownerId
    ) {
        return progressStore.findOwnedByIds(ownerApplicationIds, ownerId);
    }

    public void markViewed(UUID ownerApplicationId, UUID viewerId) throws ServiceException {
        OwnerApplicationProcess process = progressStore.findByOwnerApplicationId(ownerApplicationId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy quy trình của đơn đăng ký."));

        boolean changed = progressStore.markViewedIfAbsent(
                process.processInstanceKey(),
                process.ownerApplicationId(),
                LocalDateTime.now().toString(),
                viewerId
        );

        if (changed) {
            progressPublisher.publish(OwnerApplicationProgressChangedEvent.changed(
                    process.userId(),
                    process.ownerApplicationId()
            ));
        }
    }
}
