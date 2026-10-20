package iuh.fit.workflowservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.workflowservice.application.port.CancellationProcessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * payment-service là nơi biết khoản hoàn đi tới đâu (payOS, người chơi tự nhận, chủ sân xác nhận tay...). Mọi thay
 * đổi đi qua đây vào process; venue-service không tự đọc event hoàn tiền nữa nên trạng thái chỉ đổi theo Camunda.
 * Khoản hoàn không thuộc yêu cầu hủy nào (hoàn phí giải đấu...) không có process chờ, message tự hết hạn.
 */
@Component
@RequiredArgsConstructor
public class PaymentRefundEventConsumer {
    private final ObjectMapper objectMapper;
    private final CancellationProcessPort cancellationProcess;

    @KafkaListener(topics = "${payment.events.topic}", groupId = "${payment.events.workflow-group-id}")
    public void consume(String message) throws Exception {
        JsonNode envelope = objectMapper.readTree(message);
        if (!envelope.path("eventType").asText().startsWith("refund.")) return;
        JsonNode payload = envelope.path("payload");
        JsonNode failureReason = payload.path("failureReason");
        cancellationProcess.publishRefundStatus(
                UUID.fromString(payload.path("refundId").asText()),
                payload.path("status").asText(),
                failureReason.isMissingNode() || failureReason.isNull() ? null : failureReason.asText(),
                envelope.path("eventId").asText()
        );
    }
}
