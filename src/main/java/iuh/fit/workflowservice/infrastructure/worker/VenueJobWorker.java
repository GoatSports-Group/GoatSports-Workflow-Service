package iuh.fit.workflowservice.infrastructure.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import iuh.fit.workflowservice.application.dto.request.venue.CreateDocumentsRequest;
import iuh.fit.workflowservice.application.dto.request.venue.ReviewOwnerApplicationRequest;
import iuh.fit.workflowservice.application.dto.request.venue.SubmitOwnerApplicationRequest;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.dto.response.venue.OwnerApplicationResponse;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
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
    private final ObjectMapperPort objectMapper;
    private final VenueClient venueClient;
    private final WorkflowWorkerErrorHandler errorHandler;

    @JobWorker(type = "createOwnerApplication")
    public Map<String, Object> handleCreateOwnerApplication(ActivatedJob job) {
        LOGGER.info("Executing createOwnerApplication for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();
        SubmitOwnerApplicationRequest request = new SubmitOwnerApplicationRequest(
            (String) variables.get("ownerApplicationRequestId"),
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
            (String) variables.get("city"),
            numberValue(variables.get("latitude")),
            numberValue(variables.get("longitude"))
        );

        BaseResponse<OwnerApplicationResponse> response = errorHandler.execute(
                job, "VENUE_SERVICE", "CREATE_APP_FAILED",
                () -> venueClient.submitApplication(request)
        );

        if (response.getData() == null || response.getData().ownerApplicationId() == null) {
            throw errorHandler.fromException(
                    job, "VENUE_SERVICE", "CREATE_APP_FAILED",
                    new IllegalStateException("Venue Service response is missing ownerApplicationId")
            );
        }

        return Map.of(
                "ownerApplication", response.getData(),
                "ownerApplicationId", response.getData().ownerApplicationId().toString(),
                "mainProcessInstanceKey", job.getProcessInstanceKey()
        );
    }

    @JobWorker(type = "createDocuments")
    public Map<String, Object> handleCreateDocuments(ActivatedJob job) {
        LOGGER.info("Executing createDocuments for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        OwnerApplicationResponse ownerApplication = objectMapper.convert(variables.get("ownerApplication"), OwnerApplicationResponse.class);

        CreateDocumentsRequest request = new CreateDocumentsRequest(
                ownerApplication.ownerApplicationId().toString(),
                (List<String>) variables.get("documentKeys")
        );

        errorHandler.execute(job, "VENUE_SERVICE", "CREATE_DOCUMENTS_FAILED",
                () -> venueClient.createDocuments(request));

        return Map.of(
                "eventType", "SUBMITTED",
                "workflowStatus", "SUBMITTED"
        );
    }

    @JobWorker(type = "rejectApplication")
    public Map<String, Object> handleRejectApplication(ActivatedJob job) {
        LOGGER.info("Executing rejectApplication for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        String id = getOwnerApplicationField(variables, "ownerApplicationId");
        String rejectReason = getOwnerApplicationField(variables, "rejectReason");
        String reviewerId = (String) variables.get("reviewerId");
        if (reviewerId == null) {
            reviewerId = getOwnerApplicationField(variables, "reviewerId");
        }

        String finalReviewerId = reviewerId;
        BaseResponse<OwnerApplicationResponse> response = errorHandler.execute(
                job, "VENUE_SERVICE", "REJECT_APP_FAILED",
                () -> venueClient.rejectApplication(
                        id, new ReviewOwnerApplicationRequest(finalReviewerId, rejectReason)
                )
        );

        return Map.of(
                "eventType", "REJECTED",
                "ownerApplication", response.getData()
        );
    }

    @JobWorker(type = "approveApplication")
    public Map<String, Object> handleApproveApplication(ActivatedJob job) {
        LOGGER.info("Executing approveApplication for job: {}", job.getKey());

        Map<String, Object> variables = job.getVariablesAsMap();

        String id = getOwnerApplicationField(variables, "ownerApplicationId");
        String reviewerId = (String) variables.get("reviewerId");
        if (reviewerId == null) {
            reviewerId = getOwnerApplicationField(variables, "reviewerId");
        }

        String finalReviewerId = reviewerId;
        BaseResponse<OwnerApplicationResponse> response = errorHandler.execute(
                job, "VENUE_SERVICE", "APPROVE_APP_FAILED",
                () -> venueClient.approveApplication(
                        id, new ReviewOwnerApplicationRequest(finalReviewerId, null)
                )
        );

        return Map.of(
                "eventType", "APPROVED",
                "ownerApplication", response.getData()
        );
    }

    private String getOwnerApplicationField(Map<String, Object> variables, String fieldName) {
        Object ownerApp = variables.get("ownerApplication");
        if (ownerApp == null) {
            return null;
        }

        if (ownerApp instanceof Map) {
            Object val = ((Map<?, ?>) ownerApp).get(fieldName);
            return val != null ? val.toString() : null;
        } else if (ownerApp instanceof OwnerApplicationResponse) {
            OwnerApplicationResponse response = (OwnerApplicationResponse) ownerApp;
            if ("ownerApplicationId".equals(fieldName)) {
                return response.ownerApplicationId() != null ? response.ownerApplicationId().toString() : null;
            } else if ("userId".equals(fieldName)) {
                return response.userId() != null ? response.userId().toString() : null;
            } else if ("rejectReason".equals(fieldName)) {
                return response.rejectReason();
            }
        }

        return null;
    }

    @JobWorker(type = "rollbackOwnerApplication")
    public Map<String, Object> handleRollbackOwnerApplication(ActivatedJob job) {
        LOGGER.info("Executing rollbackOwnerApplication for job: {}", job.getKey());
        Map<String, Object> variables = job.getVariablesAsMap();
        String ownerApplicationId = (String) variables.get("ownerApplicationRequestId");

        errorHandler.execute(
                job,
                "VENUE_SERVICE",
                "ROLLBACK_APP_FAILED",
                () -> venueClient.rollbackApplication(ownerApplicationId)
        );
        return Map.of("ownerApplicationRolledBack", true);
    }

    private Double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
