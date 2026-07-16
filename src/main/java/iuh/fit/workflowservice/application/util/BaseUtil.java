package iuh.fit.workflowservice.application.util;

import iuh.fit.workflowservice.application.exception.ServiceException;

import java.util.UUID;

public class BaseUtil {

    public static UUID uuid(String id) throws ServiceException {
        UUID uuid;

        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ServiceException("Id không hợp lệ: không phải UUID");
        }

        return uuid;
    }
}
