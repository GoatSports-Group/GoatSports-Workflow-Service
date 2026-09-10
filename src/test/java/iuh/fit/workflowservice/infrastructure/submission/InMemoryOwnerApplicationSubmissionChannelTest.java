package iuh.fit.workflowservice.infrastructure.submission;

import iuh.fit.workflowservice.application.dto.response.storage.PresignedUrlResponse;
import iuh.fit.workflowservice.application.dto.submission.OwnerUploadPreparation;
import iuh.fit.workflowservice.application.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryOwnerApplicationSubmissionChannelTest {
    private final InMemoryOwnerApplicationSubmissionChannel channel =
            new InMemoryOwnerApplicationSubmissionChannel(Duration.ofSeconds(1));

    @Test
    void preparationSignalCanArriveBeforeHttpRequestStartsWaiting() throws ServiceException {
        OwnerUploadPreparation preparation = new OwnerUploadPreparation(
                10L,
                20L,
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                List.of(new PresignedUrlResponse("https://upload.example", "temp/document.png"))
        );

        channel.uploadPreparationReady(preparation);

        assertEquals(preparation, channel.awaitUploadPreparation(10L));
    }

    @Test
    void preparationFailureIsReturnedToHttpRequest() {
        channel.uploadPreparationFailed(10L, "Không thể lấy URL tải lên.");

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> channel.awaitUploadPreparation(10L)
        );
        assertEquals("Không thể lấy URL tải lên.", exception.getMessage());
    }

    @Test
    void documentCreationSignalCanArriveBeforeHttpRequestStartsWaiting() {
        channel.documentsCreated(10L);

        assertDoesNotThrow(() -> channel.awaitDocumentsCreated(10L));
    }

    @Test
    void documentCreationFailureIsReturnedToHttpRequest() {
        channel.documentCreationFailed(10L, "Không thể tạo documents.");

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> channel.awaitDocumentsCreated(10L)
        );
        assertEquals("Không thể tạo documents.", exception.getMessage());
    }
}
