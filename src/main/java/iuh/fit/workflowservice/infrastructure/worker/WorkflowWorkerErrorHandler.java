package iuh.fit.workflowservice.infrastructure.worker;

import feign.FeignException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import iuh.fit.workflowservice.application.dto.response.base.BaseResponse;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowWorkerErrorHandler {
    private static final int UNKNOWN_STATUS = 500;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

    private final ObjectMapperPort objectMapper;

    public <T> BaseResponse<T> execute(
            ActivatedJob job,
            String source,
            String bpmnErrorCode,
            Supplier<BaseResponse<T>> operation
    ) {
        try {
            BaseResponse<T> response = operation.get();
            if (response == null) {
                throw new IllegalStateException(source + " returned an empty response");
            }
            if (response.getError() != null
                    || response.getStatusCode() == null
                    || response.getStatusCode() >= 400) {
                throw fromResponse(job, source, bpmnErrorCode, response);
            }
            return response;
        } catch (Exception exception) {
            throw fromException(job, source, bpmnErrorCode, exception);
        }
    }

    public RuntimeException fromResponse(
            ActivatedJob job,
            String source,
            String bpmnErrorCode,
            BaseResponse<?> response
    ) {
        int status = response.getStatusCode() != null ? response.getStatusCode() : UNKNOWN_STATUS;
        String message = readableMessage(response.getMessage(), defaultMessage(source, status));
        return create(job, source, bpmnErrorCode, status, message, null);
    }

    public RuntimeException fromException(
            ActivatedJob job,
            String source,
            String bpmnErrorCode,
            Exception exception
    ) {
        if (exception instanceof io.camunda.client.exception.CamundaError camundaError) {
            return camundaError;
        }
        int status = exception instanceof FeignException feignException
                ? feignException.status()
                : UNKNOWN_STATUS;
        String message = exception instanceof FeignException feignException
                ? feignMessage(feignException, source, status)
                : defaultMessage(source, status);
        return create(job, source, bpmnErrorCode, status, message, exception);
    }

    private RuntimeException create(
            ActivatedJob job,
            String source,
            String bpmnErrorCode,
            int status,
            String message,
            Throwable cause
    ) {
        boolean businessError = status >= 400 && status < 500 && status != 408 && status != 429;
        int remainingRetries = Math.max(0, job.getRetries() - 1);
        String workflowStatus = businessError || remainingRetries == 0 ? "FAILED" : "RETRYING";
        Map<String, Object> variables = errorVariables(workflowStatus, source, bpmnErrorCode, status, message);

        if (businessError) {
            log.warn("Worker {} stopped by business error {}: {}", source, status, message);
            return CamundaError.bpmnError(bpmnErrorCode, message, variables, cause);
        }

        log.error("Worker {} failed with status {}, remaining retries: {}", source, status, remainingRetries, cause);
        return CamundaError.jobError(message, variables, remainingRetries, RETRY_BACKOFF, cause);
    }

    private Map<String, Object> errorVariables(
            String workflowStatus,
            String source,
            String code,
            int status,
            String message
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("workflowStatus", workflowStatus);
        variables.put("workflowErrorSource", source);
        variables.put("workflowErrorCode", code);
        variables.put("workflowErrorStatus", status);
        variables.put("workflowErrorMessage", message);
        return variables;
    }

    private String feignMessage(FeignException exception, String source, int status) {
        if (status >= 500 || status <= 0) {
            return defaultMessage(source, status);
        }
        Object body = objectMapper.read(exception.contentUTF8());
        if (body instanceof Map<?, ?> values) {
            return readableMessage(values.get("message"), defaultMessage(source, status));
        }
        return defaultMessage(source, status);
    }

    private String readableMessage(Object value, String fallback) {
        if (value instanceof String message && !message.isBlank()) {
            return message.trim();
        }
        return fallback;
    }

    private String defaultMessage(String source, int status) {
        if (status == 429) {
            return "Dịch vụ đang quá tải. Hệ thống sẽ tự động thử lại.";
        }
        return switch (source) {
            case "VENUE_SERVICE" -> "Không thể tạo đơn đăng ký chủ sân lúc này.";
            case "STORAGE_SERVICE" -> "Không thể chuẩn bị nơi tải tài liệu lúc này.";
            case "NOTIFICATION_SERVICE" -> "Không thể gửi thông báo lúc này.";
            default -> "Không thể xử lý bước workflow lúc này.";
        };
    }
}
