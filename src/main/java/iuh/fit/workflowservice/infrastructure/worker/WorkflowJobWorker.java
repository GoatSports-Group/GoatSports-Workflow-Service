package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkflowJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageJobWorker.class);

    private final CamundaClient camundaClient;

    @JobWorker(type = "publishFileMoveEvent")
    public void handlePublishFileMove(ActivatedJob job) {
        LOGGER.info("Executing publishFileMoveEvent for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        camundaClient.newPublishMessageCommand()
                .messageName("OwnerDocumentsCreated")
                .correlationKey(UUID.randomUUID().toString())
                .variables(variables)
                .send()
                .join();
    }

    @JobWorker(type = "publishOwnerApplicationCreated")
    public void handlePublishCreatedEvent(ActivatedJob job) {
        LOGGER.info("Executing publishOwnerApplicationCreated for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        camundaClient.newPublishMessageCommand()
                .messageName("OwnerApplicationCreated")
                .correlationKey(UUID.randomUUID().toString())
                .variables(variables)
                .send()
                .join();
    }
}
