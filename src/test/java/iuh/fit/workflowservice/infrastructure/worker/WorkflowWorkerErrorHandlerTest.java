package iuh.fit.workflowservice.infrastructure.worker;

import feign.FeignException;
import feign.Request;
import feign.Response;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.JobError;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.infrastructure.util.ObjectMapperAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowWorkerErrorHandlerTest {
    private ActivatedJob job;
    private WorkflowWorkerErrorHandler handler;

    @BeforeEach
    void setUp() {
        job = mock(ActivatedJob.class);
        when(job.getRetries()).thenReturn(3);
        handler = new WorkflowWorkerErrorHandler(
                new ObjectMapperAdapter(new com.fasterxml.jackson.databind.ObjectMapper())
        );
    }

    @Test
    void convertsDownstreamBadRequestToBpmnBusinessError() {
        BaseResponse<Void> response = new BaseResponse<>(
                400, null, "Người dùng có đơn đăng ký đang chờ duyệt.", "Có lỗi xảy ra"
        );

        BpmnError error = assertInstanceOf(BpmnError.class,
                handler.fromResponse(job, "VENUE_SERVICE", "CREATE_APP_FAILED", response));

        assertEquals("CREATE_APP_FAILED", error.getErrorCode());
        assertEquals("Người dùng có đơn đăng ký đang chờ duyệt.", error.getErrorMessage());
        assertEquals("FAILED", variables(error).get("workflowStatus"));
    }

    @Test
    void extractsBusinessMessageFromFeignErrorBody() {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://localhost/internal/owner-applications",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .request(request)
                .status(400)
                .reason("Bad Request")
                .headers(Map.of())
                .body("{\"message\":\"Người dùng có đơn đăng ký đang chờ duyệt.\"}", StandardCharsets.UTF_8)
                .build();
        FeignException exception = FeignException.errorStatus("submitApplication", response);

        BpmnError error = assertInstanceOf(BpmnError.class,
                handler.fromException(job, "VENUE_SERVICE", "CREATE_APP_FAILED", exception));

        assertEquals("Người dùng có đơn đăng ký đang chờ duyệt.", error.getErrorMessage());
    }

    @Test
    void retriesTransientDownstreamErrorWithBackoff() {
        BaseResponse<Void> response = new BaseResponse<>(503, null, "Unavailable", "Service unavailable");

        JobError error = assertInstanceOf(JobError.class,
                handler.fromResponse(job, "STORAGE_SERVICE", "GET_PRESIGNED_FAILED", response));

        assertEquals(2, error.getRetries());
        assertEquals("RETRYING", variables(error).get("workflowStatus"));
    }

    @Test
    void marksTransientErrorAsFailedWhenRetriesAreExhausted() {
        when(job.getRetries()).thenReturn(1);
        BaseResponse<Void> response = new BaseResponse<>(503, null, "Unavailable", "Service unavailable");

        BpmnError error = assertInstanceOf(BpmnError.class,
                handler.fromResponse(job, "STORAGE_SERVICE", "GET_PRESIGNED_FAILED", response));

        assertEquals("GET_PRESIGNED_FAILED", error.getErrorCode());
        assertEquals("FAILED", variables(error).get("workflowStatus"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> variables(io.camunda.client.exception.CamundaError error) {
        if (error instanceof BpmnError bpmnError) {
            return (Map<String, Object>) bpmnError.getVariables();
        }
        return (Map<String, Object>) ((JobError) error).getVariables();
    }
}
