package iuh.fit.workflowservice.application.dto.response.notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse (
    UUID notificationId,
    UUID receiverId,
    String title,
    String content,
    String type,
    String status,
    UUID referenceId,
    LocalDateTime createdAt,
    LocalDateTime readAt
) {}