package iuh.fit.workflowservice.application.dto.event;

import iuh.fit.workflowservice.application.dto.event.type.OwnerApplicationEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OwnerApplicationEvent {
    private UUID eventId;
    private OwnerApplicationEventType eventType;
    private LocalDateTime timestamp;
    private OwnerApplicationData data;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OwnerApplicationData {
        private UUID ownerApplicationId;
        private UUID userId;
        private String email;
        private String status;
        private String rejectReason;
    }
}
