package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageJobWorker.class);
    private final ObjectMapperPort objectMapper;
    private final CamundaClient camundaClient;

    @JobWorker(type = "publishFileMoveEvent")
    public void handlePublishFileMove(ActivatedJob job) {
        LOGGER.info("Executing publishFileMoveEvent for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        camundaClient.newPublishMessageCommand()
                .messageName("OwnerDocumentsCreated")
                .correlationKey(getOwnerApplicationId(variables))
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
                .correlationKey(getOwnerApplicationId(variables))
                .variables(variables)
                .send()
                .join();
    }

    @JobWorker(type = "publishRejectedEvent")
    public void handlePublishRejectedEvent(ActivatedJob job) {
        LOGGER.info("Executing publishRejectedEvent for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        camundaClient.newPublishMessageCommand()
                .messageName("OwnerApplicationRejected")
                .correlationKey(getOwnerApplicationId(variables))
                .variables(variables)
                .send()
                .join();
    }

    @JobWorker(type = "publishApprovedEvent")
    public void handlePublishApprovedEvent(ActivatedJob job) {
        LOGGER.info("Executing publishApprovedEvent for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        camundaClient.newPublishMessageCommand()
                .messageName("OwnerApplicationApproved")
                .correlationKey(getOwnerApplicationId(variables))
                .variables(variables)
                .send()
                .join();
    }

    private String getOwnerApplicationId(Map<String, Object> variables) {
        OwnerApplicationResponse ownerApplication = objectMapper.convert(variables.get("ownerApplication"), OwnerApplicationResponse.class);
        return ownerApplication.ownerApplicationId().toString();
    }
}
