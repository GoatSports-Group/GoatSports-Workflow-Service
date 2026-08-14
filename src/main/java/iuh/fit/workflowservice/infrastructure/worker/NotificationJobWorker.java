package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.event.OwnerApplicationEvent;
import iuh.fit.workflowservice.application.dto.event.OwnerApplicationProgressChangedEvent;
import iuh.fit.workflowservice.application.dto.event.type.OwnerApplicationEventType;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.notification.NotificationResponse;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.notification.OwnerApplicationProgressPublisher;
import iuh.fit.workflowservice.application.port.progress.OwnerApplicationProgressStore;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
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
    private final ObjectMapperPort objectMapper;
    private final NotificationClient notificationClient;
    private final OwnerApplicationProgressStore progressStore;
    private final OwnerApplicationProgressPublisher progressPublisher;

    @JobWorker(type = "createNotification")
    public Map<String, Object> handleCreateNotification(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing createNotification for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        OwnerApplicationResponse ownerApplication = objectMapper.convert(variables.get("ownerApplication"), OwnerApplicationResponse.class);
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

        NotificationResponse notification = objectMapper.convert(variables.get("notification"), NotificationResponse.class);

        BaseResponse<Void> response = notificationClient.notifyUser(notification);

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        String eventType = (String) variables.get("eventType");
        if (OwnerApplicationEventType.SUBMITTED.name().equals(eventType)) {
            OwnerApplicationResponse ownerApplication = objectMapper.convert(
                    variables.get("ownerApplication"),
                    OwnerApplicationResponse.class
            );
            long processInstanceKey = resolveMainProcessInstanceKey(variables, ownerApplication);
            String receivedAt = notification.createdAt() == null
                    ? LocalDateTime.now().toString()
                    : notification.createdAt().toString();

            boolean changed = progressStore.markReceivedIfAbsent(processInstanceKey, receivedAt);
            if (changed) {
                progressPublisher.publish(OwnerApplicationProgressChangedEvent.changed(
                        ownerApplication.userId(),
                        ownerApplication.ownerApplicationId()
                ));
            }
        }

        return Map.of();
    }

    private long resolveMainProcessInstanceKey(
            Map<String, Object> variables,
            OwnerApplicationResponse ownerApplication
    ) throws ServiceException {
        Object rawProcessInstanceKey = variables.get("mainProcessInstanceKey");
        if (rawProcessInstanceKey instanceof Number number) {
            return number.longValue();
        }
        if (rawProcessInstanceKey != null) {
            try {
                return Long.parseLong(rawProcessInstanceKey.toString());
            } catch (NumberFormatException exception) {
                LOGGER.warn("Invalid mainProcessInstanceKey: {}", rawProcessInstanceKey);
            }
        }

        return progressStore.findByOwnerApplicationId(ownerApplication.ownerApplicationId())
                .map(process -> process.processInstanceKey())
                .orElseThrow(() -> new ServiceException(
                        "Không tìm thấy quy trình của đơn đăng ký để ghi nhận thời gian tiếp nhận."
                ));
    }
}
