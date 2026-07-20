package iuh.fit.workflowservice.application.port.util;

public interface ObjectMapperPort {
    Object read(String value);

    <T> T convert(Object source, Class<T> targetType);
}
