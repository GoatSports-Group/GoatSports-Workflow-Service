package iuh.fit.workflowservice.application.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.workflowservice.application.exception.ServiceException;

import java.util.UUID;

public class BaseUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static UUID uuid(String id) throws ServiceException {
        UUID uuid;

        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ServiceException("Id không hợp lệ: không phải UUID");
        }

        return uuid;
    }

    public static Object convert(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception e) {
            return value;
        }
    }
}
