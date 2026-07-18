package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.request.venue.CreateDocumentsRequest;
import iuh.fit.workflowservice.application.dto.request.venue.SubmitOwnerApplicationRequest;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.infrastructure.external.client.VenueClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VenueJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(VenueJobWorker.class);

    private final VenueClient venueClient;

    @JobWorker(type = "createOwnerApplication")
    public Map<String, Object> handleCreateOwnerApplication(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing createOwnerApplication for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        SubmitOwnerApplicationRequest request = new SubmitOwnerApplicationRequest(
            (String) variables.get("userId"),
            (String) variables.get("fullName"),
            (String) variables.get("phone"),
            (String) variables.get("email"),
            (String) variables.get("identityNumber"),
            (String) variables.get("businessName"),
            (String) variables.get("businessType"),
            (String) variables.get("taxCode"),
            (String) variables.get("address"),
            (String) variables.get("province"),
            (String) variables.get("district"),
            (String) variables.get("ward"),
            (String) variables.get("city")
        );

        BaseResponse<OwnerApplicationResponse> response = venueClient.submitApplication(request);

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of("ownerApplication", response.getData());
    }

    @JobWorker(type = "createDocuments")
    public Map<String, Object> handleCreateDocuments(ActivatedJob job) throws ServiceException {
        LOGGER.info("Executing createDocuments for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        CreateDocumentsRequest request = new CreateDocumentsRequest(
                ((OwnerApplicationResponse) variables.get("ownerApplication")).ownerApplicationId().toString(),
                (List<String>) variables.get("documentKeys")
        );

        BaseResponse<Void> response = venueClient.createDocuments(request);

        if(response.getError() != null || response.getStatusCode() >= 400) {
            throw new ServiceException(response.getMessage().toString());
        }

        return Map.of("eventType", "SUBMITTED");
    }
}
