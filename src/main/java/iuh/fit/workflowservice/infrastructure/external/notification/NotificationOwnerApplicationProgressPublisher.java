package iuh.fit.workflowservice.infrastructure.external.notification;

import iuh.fit.workflowservice.application.dto.event.OwnerApplicationProgressChangedEvent;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.notification.OwnerApplicationProgressPublisher;
import iuh.fit.workflowservice.infrastructure.external.client.NotificationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationOwnerApplicationProgressPublisher implements OwnerApplicationProgressPublisher {
    private final NotificationClient notificationClient;

    @Override
    public void publish(OwnerApplicationProgressChangedEvent event) throws ServiceException {
        BaseResponse<Void> response = notificationClient.publishOwnerApplicationProgress(event);
        if (response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }
    }
}
