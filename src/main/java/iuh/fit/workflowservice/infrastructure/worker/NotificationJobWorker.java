package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.event.OwnerApplicationEvent;
import iuh.fit.workflowservice.application.dto.event.type.OwnerApplicationEventType;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.notification.NotificationResponse;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.infrastructure.external.client.NotificationClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationJobWorker.class);

    private final NotificationClient notificationClient;

    @JobWorker(type = "createNotification")
    public Map<String, Object> handleCreateNotification(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing createNotification for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        OwnerApplicationResponse ownerApplication = ((OwnerApplicationResponse) variables.get("ownerApplication"));
        String eventType = (String) variables.get("eventType");

        OwnerApplicationEvent event = OwnerApplicationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(OwnerApplicationEventType.valueOf(eventType))
                .timestamp(LocalDateTime.now())
                .data(OwnerApplicationEvent.OwnerApplicationData.builder()
                        .ownerApplicationId(ownerApplication.ownerApplicationId())
                        .userId(ownerApplication.userId())
                        .email(ownerApplication.email())
                        .status(ownerApplication.status())
                        .rejectReason(ownerApplication.rejectReason())
                        .build()
                )
                .build();

        BaseResponse<NotificationResponse> response = notificationClient.createOwnerApplicationNotification(event);

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of("notification", response.getData());
    }

    @JobWorker(type = "notifyUser")
    public Map<String, Object> handleNotifyUser(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing notifyUser for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        NotificationResponse notification = ((NotificationResponse) variables.get("notification"));

        BaseResponse<Void> response = notificationClient.notifyUser(notification);

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of();
    }
}
