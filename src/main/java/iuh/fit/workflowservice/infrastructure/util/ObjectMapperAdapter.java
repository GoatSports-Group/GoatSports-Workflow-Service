package iuh.fit.workflowservice.infrastructure.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.workflowservice.application.port.util.ObjectMapperPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectMapperAdapter implements ObjectMapperPort {
    private final ObjectMapper objectMapper;

    @Override
    public Object read(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public <T> T convert(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }

        return objectMapper.convertValue(source, targetType);
    }
}
