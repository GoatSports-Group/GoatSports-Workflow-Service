package iuh.fit.workflowservice.application.port.notification;

import iuh.fit.workflowservice.application.dto.event.OwnerApplicationProgressChangedEvent;
import iuh.fit.workflowservice.application.exception.ServiceException;

public interface OwnerApplicationProgressPublisher {
    void publish(OwnerApplicationProgressChangedEvent event) throws ServiceException;
}
