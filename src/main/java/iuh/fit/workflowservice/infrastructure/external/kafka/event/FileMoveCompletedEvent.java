package iuh.fit.workflowservice.infrastructure.external.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMoveCompletedEvent {
    private Long processInstanceKey;
}
