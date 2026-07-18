package iuh.fit.workflowservice.infrastructure.external.client;

import iuh.fit.workflowservice.application.dto.event.OwnerApplicationEvent;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.notification.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "GOATSPORTS-NOTIFICATION-SERVICE",
        url = "${notification.service.url}",
        path = "/internal/notifications"
)
public interface NotificationClient {
    @PostMapping("/owner-applications")
    BaseResponse<NotificationResponse> createOwnerApplicationNotification(@RequestBody OwnerApplicationEvent event);

    @PostMapping("/notify")
    BaseResponse<Void> notifyUser(@RequestBody NotificationResponse notification);
}
