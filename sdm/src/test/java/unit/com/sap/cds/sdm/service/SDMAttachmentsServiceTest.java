package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.service.AttachmentModificationResult;
import com.sap.cds.feature.attachments.service.model.service.CreateAttachmentInput;
import com.sap.cds.feature.attachments.service.model.service.MarkAsDeletedInput;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.service.SDMAttachmentsService;
import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
import com.sap.cds.services.request.UserInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SDMAttachmentsServiceTest {

  @Mock private UserInfo mockUserInfo;
  @Mock private CdsEntity mockAttachmentEntity;

  private SDMAttachmentsService service;

  @BeforeEach
  void setUp() {
    service = spy(new SDMAttachmentsService());
    // Mock the emit method to avoid OpenTelemetry initialization issues - use lenient
    lenient().doNothing().when(service).emit(any());
  }

  @Test
  void testConstructor() {
    // When
    SDMAttachmentsService newService = new SDMAttachmentsService();

    // Then
    assertNotNull(newService);
  }

  @Test
  void testCopyAttachments_WithSystemUser() {
    // Given
    String upId = "test-up-id";
    String facet = "test-facet";
    List<String> objectIds = Arrays.asList("obj1", "obj2", "obj3");
    boolean isSystemUser = true;

    try (MockedStatic<AttachmentCopyEventContext> mockedStatic =
        mockStatic(AttachmentCopyEventContext.class)) {
      AttachmentCopyEventContext mockContext = mock(AttachmentCopyEventContext.class);
      mockedStatic.when(AttachmentCopyEventContext::create).thenReturn(mockContext);

      CopyAttachmentInput input = new CopyAttachmentInput(upId, facet, objectIds);

      // When
      service.copyAttachments(input, isSystemUser);

      // Then
      verify(mockContext).setUpId(upId);
      verify(mockContext).setFacet(facet);
      verify(mockContext).setObjectIds(objectIds);
      verify(mockContext).setSystemUser(true);
      verify(service).emit(mockContext);
    }
  }

  @Test
  void testCopyAttachments_WithNonSystemUser() {
    // Given
    String upId = "test-up-id-2";
    String facet = "test-facet-2";
    List<String> objectIds = Arrays.asList("obj4", "obj5");
    boolean isSystemUser = false;

    try (MockedStatic<AttachmentCopyEventContext> mockedStatic =
        mockStatic(AttachmentCopyEventContext.class)) {
      AttachmentCopyEventContext mockContext = mock(AttachmentCopyEventContext.class);
      mockedStatic.when(AttachmentCopyEventContext::create).thenReturn(mockContext);

      CopyAttachmentInput input = new CopyAttachmentInput(upId, facet, objectIds);

      // When
      service.copyAttachments(input, isSystemUser);

      // Then
      verify(mockContext).setUpId(upId);
      verify(mockContext).setFacet(facet);
      verify(mockContext).setObjectIds(objectIds);
      verify(mockContext).setSystemUser(false);
      verify(service).emit(mockContext);
    }
  }

  @Test
  void testReadAttachment() {
    // Given
    String contentId = "test-content-id";
    InputStream expectedInputStream = new ByteArrayInputStream("test content".getBytes());

    try (MockedStatic<AttachmentReadEventContext> mockedContextStatic =
            mockStatic(AttachmentReadEventContext.class);
        MockedStatic<MediaData> mockedMediaDataStatic = mockStatic(MediaData.class)) {

      AttachmentReadEventContext mockContext = mock(AttachmentReadEventContext.class);
      MediaData mockMediaData = mock(MediaData.class);

      mockedContextStatic.when(AttachmentReadEventContext::create).thenReturn(mockContext);
      mockedMediaDataStatic.when(MediaData::create).thenReturn(mockMediaData);

      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockMediaData.getContent()).thenReturn(expectedInputStream);

      // When
      InputStream result = service.readAttachment(contentId);

      // Then
      verify(mockContext).setContentId(contentId);
      verify(mockContext).setData(mockMediaData);
      verify(service).emit(mockContext);
      assertEquals(expectedInputStream, result);
    }
  }

  @Test
  void testCreateAttachment() {
    // Given
    Map<String, Object> attachmentIds = Map.of("id1", "value1", "id2", "value2");
    String fileName = "test-file.pdf";
    String mimeType = "application/pdf";
    InputStream content = new ByteArrayInputStream("test content".getBytes());
    String expectedContentId = "generated-content-id";
    String expectedStatus = "SUCCESS";

    CreateAttachmentInput mockInput = mock(CreateAttachmentInput.class);
    when(mockInput.attachmentIds()).thenReturn(attachmentIds);
    when(mockInput.attachmentEntity()).thenReturn(mockAttachmentEntity);
    when(mockInput.fileName()).thenReturn(fileName);
    when(mockInput.mimeType()).thenReturn(mimeType);
    when(mockInput.content()).thenReturn(content);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("TestEntity");

    try (MockedStatic<AttachmentCreateEventContext> mockedContextStatic =
            mockStatic(AttachmentCreateEventContext.class);
        MockedStatic<MediaData> mockedMediaDataStatic = mockStatic(MediaData.class)) {

      AttachmentCreateEventContext mockContext = mock(AttachmentCreateEventContext.class);
      MediaData mockMediaData = mock(MediaData.class);

      mockedContextStatic.when(AttachmentCreateEventContext::create).thenReturn(mockContext);
      mockedMediaDataStatic.when(MediaData::create).thenReturn(mockMediaData);

      when(mockContext.getIsInternalStored()).thenReturn(true);
      when(mockContext.getContentId()).thenReturn(expectedContentId);
      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockMediaData.getStatus()).thenReturn(expectedStatus);

      // When
      AttachmentModificationResult result = service.createAttachment(mockInput);

      // Then
      verify(mockContext).setAttachmentIds(attachmentIds);
      verify(mockContext).setAttachmentEntity(mockAttachmentEntity);
      verify(mockContext).setData(mockMediaData);
      verify(mockMediaData).setFileName(fileName);
      verify(mockMediaData).setMimeType(mimeType);
      verify(mockMediaData).setContent(content);
      verify(service).emit(mockContext);

      assertTrue(result.isInternalStored());
      assertEquals(expectedContentId, result.contentId());
      assertEquals(expectedStatus, result.status());
    }
  }

  @Test
  void testCreateAttachment_WithNullInternalStored() {
    // Given
    Map<String, Object> attachmentIds = Map.of("att1", "value1");
    String fileName = "test.txt";
    String mimeType = "text/plain";
    InputStream content = new ByteArrayInputStream("content".getBytes());

    CreateAttachmentInput mockInput = mock(CreateAttachmentInput.class);
    when(mockInput.attachmentIds()).thenReturn(attachmentIds);
    when(mockInput.attachmentEntity()).thenReturn(mockAttachmentEntity);
    when(mockInput.fileName()).thenReturn(fileName);
    when(mockInput.mimeType()).thenReturn(mimeType);
    when(mockInput.content()).thenReturn(content);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("TestEntity");

    try (MockedStatic<AttachmentCreateEventContext> mockedContextStatic =
            mockStatic(AttachmentCreateEventContext.class);
        MockedStatic<MediaData> mockedMediaDataStatic = mockStatic(MediaData.class)) {

      AttachmentCreateEventContext mockContext = mock(AttachmentCreateEventContext.class);
      MediaData mockMediaData = mock(MediaData.class);

      mockedContextStatic.when(AttachmentCreateEventContext::create).thenReturn(mockContext);
      mockedMediaDataStatic.when(MediaData::create).thenReturn(mockMediaData);

      when(mockContext.getIsInternalStored()).thenReturn(null);
      when(mockContext.getContentId()).thenReturn("test-id");
      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockMediaData.getStatus()).thenReturn("PENDING");

      // When
      AttachmentModificationResult result = service.createAttachment(mockInput);

      // Then
      assertFalse(result.isInternalStored()); // Boolean.TRUE.equals(null) returns false
      assertEquals("test-id", result.contentId());
      assertEquals("PENDING", result.status());
    }
  }

  @Test
  void testMarkAttachmentAsDeleted() {
    // Given
    String contentId = "delete-content-id";
    String userName = "test-user";

    when(mockUserInfo.getName()).thenReturn(userName);

    MarkAsDeletedInput mockInput = mock(MarkAsDeletedInput.class);
    when(mockInput.contentId()).thenReturn(contentId);
    when(mockInput.userInfo()).thenReturn(mockUserInfo);

    try (MockedStatic<AttachmentMarkAsDeletedEventContext> mockedStatic =
        mockStatic(AttachmentMarkAsDeletedEventContext.class)) {
      AttachmentMarkAsDeletedEventContext mockContext =
          mock(AttachmentMarkAsDeletedEventContext.class);
      mockedStatic.when(AttachmentMarkAsDeletedEventContext::create).thenReturn(mockContext);

      // When
      service.markAttachmentAsDeleted(mockInput);

      // Then
      verify(mockContext).setContentId(contentId);
      ArgumentCaptor<com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo>
          captor =
              ArgumentCaptor.forClass(
                  com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo
                      .class);
      verify(mockContext).setDeletionUserInfo(captor.capture());
      verify(service).emit(mockContext);

      // Verify DeletionUserInfo was created correctly
      com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo
          deletionUserInfo = captor.getValue();
      assertNotNull(deletionUserInfo);
    }
  }

  @Test
  void testRestoreAttachment() {
    // Given
    Instant restoreTimestamp = Instant.now();

    try (MockedStatic<AttachmentRestoreEventContext> mockedStatic =
        mockStatic(AttachmentRestoreEventContext.class)) {
      AttachmentRestoreEventContext mockContext = mock(AttachmentRestoreEventContext.class);
      mockedStatic.when(AttachmentRestoreEventContext::create).thenReturn(mockContext);

      // When
      service.restoreAttachment(restoreTimestamp);

      // Then
      verify(mockContext).setRestoreTimestamp(restoreTimestamp);
      verify(service).emit(mockContext);
    }
  }

  @Test
  void testFillDeletionUserInfo() {
    // Given
    String userName = "deletion-user";
    when(mockUserInfo.getName()).thenReturn(userName);

    try (MockedStatic<com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo>
        mockedStatic =
            mockStatic(
                com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo
                    .class)) {
      com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo
          mockDeletionUserInfo =
              mock(
                  com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo
                      .class);
      mockedStatic
          .when(
              com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo::create)
          .thenReturn(mockDeletionUserInfo);

      // When - call the private method through markAttachmentAsDeleted
      MarkAsDeletedInput mockInput2 = mock(MarkAsDeletedInput.class);
      when(mockInput2.contentId()).thenReturn("test");
      when(mockInput2.userInfo()).thenReturn(mockUserInfo);

      try (MockedStatic<AttachmentMarkAsDeletedEventContext> mockedContextStatic =
          mockStatic(AttachmentMarkAsDeletedEventContext.class)) {
        AttachmentMarkAsDeletedEventContext mockContext =
            mock(AttachmentMarkAsDeletedEventContext.class);
        mockedContextStatic
            .when(AttachmentMarkAsDeletedEventContext::create)
            .thenReturn(mockContext);

        service.markAttachmentAsDeleted(mockInput2);

        // Then
        verify(mockDeletionUserInfo).setName(userName);
      }
    }
  }

  @Test
  void testCopyAttachments_WithEmptyObjectIds() {
    // Given
    String upId = "test-up-id";
    String facet = "test-facet";
    List<String> objectIds = Arrays.asList(); // Empty list
    boolean isSystemUser = false;

    try (MockedStatic<AttachmentCopyEventContext> mockedStatic =
        mockStatic(AttachmentCopyEventContext.class)) {
      AttachmentCopyEventContext mockContext = mock(AttachmentCopyEventContext.class);
      mockedStatic.when(AttachmentCopyEventContext::create).thenReturn(mockContext);

      CopyAttachmentInput input = new CopyAttachmentInput(upId, facet, objectIds);

      // When
      service.copyAttachments(input, isSystemUser);

      // Then
      verify(mockContext).setUpId(upId);
      verify(mockContext).setFacet(facet);
      verify(mockContext).setObjectIds(objectIds);
      verify(mockContext).setSystemUser(false);
      verify(service).emit(mockContext);
    }
  }

  @Test
  void testReadAttachment_WithNullContent() {
    // Given
    String contentId = "null-content-id";

    try (MockedStatic<AttachmentReadEventContext> mockedContextStatic =
            mockStatic(AttachmentReadEventContext.class);
        MockedStatic<MediaData> mockedMediaDataStatic = mockStatic(MediaData.class)) {

      AttachmentReadEventContext mockContext = mock(AttachmentReadEventContext.class);
      MediaData mockMediaData = mock(MediaData.class);

      mockedContextStatic.when(AttachmentReadEventContext::create).thenReturn(mockContext);
      mockedMediaDataStatic.when(MediaData::create).thenReturn(mockMediaData);

      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockMediaData.getContent()).thenReturn(null);

      // When
      InputStream result = service.readAttachment(contentId);

      // Then
      verify(mockContext).setContentId(contentId);
      verify(mockContext).setData(mockMediaData);
      verify(service).emit(mockContext);
      assertNull(result);
    }
  }
}
