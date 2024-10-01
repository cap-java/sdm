package com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMAttachmentsServiceHandlerTest {

  @Mock private PersistenceService persistenceService;

  @Mock private SDMService sdmService;

  @Mock private AttachmentReadEventContext context;

  @Mock private AttachmentMarkAsDeletedEventContext attachmentMarkAsDeletedEventContext;

  @Mock private AuthenticationInfo authInfo;

  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;

  @Mock private SDMCredentials sdmCredentials;

  @Mock private CdsModel cdsModel;

  @Mock private CdsEntity cdsEntity;

  @InjectMocks private SDMAttachmentsServiceHandler handler;

  String objectId = "objectId";
  String folderId = "folderId";
  String userEmail = "email";
  String subdomain = "subdomain";

  @BeforeEach
  public void setUp() {
    when(attachmentMarkAsDeletedEventContext.getContentId())
        .thenReturn("objectId:folderId:email:entity:subdomain");
  }

  @Test
  public void testDocumentDeletion() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));

      mockedDBQuery
          .when(() -> DBQuery.isFolderEmpty(cdsEntity, persistenceService, folderId))
          .thenReturn(false);

      handler.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
      verify(sdmService).deleteDocument("delete", objectId, userEmail, subdomain);
    }
  }

  @Test
  public void testFolderDeletion() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
      mockedDBQuery
          .when(() -> DBQuery.isFolderEmpty(cdsEntity, persistenceService, folderId))
          .thenReturn(true);
      handler.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
      verify(sdmService).deleteDocument("deleteTree", folderId, userEmail, subdomain);
    }
  }
}
