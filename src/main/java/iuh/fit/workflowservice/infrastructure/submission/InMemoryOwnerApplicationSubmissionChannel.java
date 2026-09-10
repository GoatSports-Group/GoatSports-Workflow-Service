package iuh.fit.workflowservice.infrastructure.submission;

import iuh.fit.workflowservice.application.dto.submission.OwnerUploadPreparation;
import iuh.fit.workflowservice.application.exception.ServiceException;
import iuh.fit.workflowservice.application.port.submission.OwnerApplicationSubmissionChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class InMemoryOwnerApplicationSubmissionChannel implements OwnerApplicationSubmissionChannel {
    private final ConcurrentHashMap<Long, CompletableFuture<OwnerUploadPreparation>> preparations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> documentCreations =
            new ConcurrentHashMap<>();
    private final Duration awaitTimeout;

    public InMemoryOwnerApplicationSubmissionChannel(
            @Value("${workflow.owner-application.await-timeout:2m}") Duration awaitTimeout
    ) {
        this.awaitTimeout = awaitTimeout;
    }

    @Override
    public OwnerUploadPreparation awaitUploadPreparation(long processInstanceKey) throws ServiceException {
        CompletableFuture<OwnerUploadPreparation> future = preparations.computeIfAbsent(
                processInstanceKey,
                ignored -> new CompletableFuture<>()
        );
        try {
            return future.get(awaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Quá trình chuẩn bị hồ sơ đã bị gián đoạn.");
        } catch (TimeoutException exception) {
            throw new ServiceException("Hệ thống chưa chuẩn bị xong hồ sơ tải lên. Vui lòng kiểm tra lại quy trình.");
        } catch (ExecutionException exception) {
            throw failure(exception, "Không thể chuẩn bị hồ sơ đăng ký chủ sân.");
        } finally {
            preparations.remove(processInstanceKey, future);
        }
    }

    @Override
    public void uploadPreparationReady(OwnerUploadPreparation preparation) {
        preparations.computeIfAbsent(
                preparation.processInstanceKey(),
                ignored -> new CompletableFuture<>()
        ).complete(preparation);
    }

    @Override
    public void uploadPreparationFailed(long processInstanceKey, String message) {
        preparations.computeIfAbsent(
                processInstanceKey,
                ignored -> new CompletableFuture<>()
        ).completeExceptionally(new ServiceException(message));
    }

    @Override
    public void awaitDocumentsCreated(long processInstanceKey) throws ServiceException {
        CompletableFuture<Void> future = documentCreations.computeIfAbsent(
                processInstanceKey,
                ignored -> new CompletableFuture<>()
        );
        try {
            future.get(awaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Quá trình tạo tài liệu đã bị gián đoạn.");
        } catch (TimeoutException exception) {
            throw new ServiceException("Hệ thống chưa xác nhận tạo tài liệu thành công. Vui lòng kiểm tra lại quy trình.");
        } catch (ExecutionException exception) {
            throw failure(exception, "Không thể tạo tài liệu cho hồ sơ đăng ký chủ sân.");
        } finally {
            documentCreations.remove(processInstanceKey, future);
        }
    }

    @Override
    public void documentsCreated(long processInstanceKey) {
        documentCreations.computeIfAbsent(
                processInstanceKey,
                ignored -> new CompletableFuture<>()
        ).complete(null);
    }

    @Override
    public void documentCreationFailed(long processInstanceKey, String message) {
        documentCreations.computeIfAbsent(
                processInstanceKey,
                ignored -> new CompletableFuture<>()
        ).completeExceptionally(new ServiceException(message));
    }

    private ServiceException failure(ExecutionException exception, String fallback) {
        Throwable cause = exception.getCause();
        return cause instanceof ServiceException serviceException
                ? serviceException
                : new ServiceException(cause == null || cause.getMessage() == null ? fallback : cause.getMessage());
    }
}
