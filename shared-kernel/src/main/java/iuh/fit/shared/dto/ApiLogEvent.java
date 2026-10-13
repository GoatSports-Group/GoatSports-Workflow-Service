package iuh.fit.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiLogEvent {
    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    private int statusCode;
    private LocalDateTime timestamp;
    private String errorMessage;
    /** Ngữ cảnh để lần vết: service ghi (vd auth-service), mã tương quan của request và request gốc. */
    private String sourceService;
    private String correlationId;
    private String httpMethod;
    private String requestUri;
    private String userAgent;
}
