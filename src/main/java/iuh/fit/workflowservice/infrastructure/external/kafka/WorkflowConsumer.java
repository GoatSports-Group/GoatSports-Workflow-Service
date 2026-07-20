package iuh.fit.workflowservice.infrastructure.external.kafka;

import io.camunda.client.CamundaClient;
import iuh.fit.workflowservice.infrastructure.external.kafka.event.FileMoveCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowConsumer {
    private final CamundaClient camundaClient;

    @RetryableTopic(
            backOff = @BackOff(delay = 2000, multiplier = 2.0)
    )
    @KafkaListener(
            topics = "${spring.kafka.topic.file-move-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeFileMoveCompletedEvent(FileMoveCompletedEvent event) {
        log.info("Workflow received completion notification for process: {}", event.getProcessInstanceKey());

        camundaClient.newPublishMessageCommand()
                .messageName("WaitingFromConsumer")
                .correlationKey(event.getProcessInstanceKey().toString())
                .send()
                .join();

        log.info("Successfully correlated message for key: {}", event.getProcessInstanceKey());
    }

    @DltHandler
    public void handleDlt(FileMoveCompletedEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event failed all retries and reached DLT topic {}. Payload: {}", topic, event.getProcessInstanceKey());
    }
}
