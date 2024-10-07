// package com.sap.cds.sdm.service.handler;
//
// import com.sap.cds.CdsData;
// import com.sap.cds.sdm.constants.SDMConstants;
// import com.sap.cds.sdm.handler.applicationservice.SDMCreateEventHandler;
// import com.sap.cds.sdm.service.SDMService;
// import com.sap.cds.sdm.service.SDMServiceImpl;
// import com.sap.cds.services.authentication.AuthenticationInfo;
// import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
// import com.sap.cds.services.persistence.PersistenceService;
// import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import com.sap.cds.services.messages.Messages;
//
// import java.io.IOException;
// import java.util.List;
//
// import static org.mockito.Mockito.*;
//
// public class SDMAttachmentsServiceHandlerTest {
//    @Mock
//    private AttachmentCreateEventContext mockContext;
//    @Mock
//    private Messages messages;
//    private SDMAttachmentsServiceHandler handlerSpy;
//    private PersistenceService persistenceService;
//    private SDMService sdmService;
//
//    @BeforeEach
//    public void setUp() throws IOException {
//        MockitoAnnotations.openMocks(this); // Initialize mocks
//        persistenceService = mock(PersistenceService.class);
//        sdmService = mock(SDMServiceImpl.class);
//        handlerSpy = spy(new SDMAttachmentsServiceHandler(persistenceService, sdmService));
//    }
//
//    @Test
//    public void testCreateAttachmentVersioned() throws IOException {
//        when(sdmService.checkRepositoryType(SDMConstants.REPOSITORY_ID)).thenReturn("Versioned");
//        handlerSpy.createAttachment(mockContext);
//    }
// }
