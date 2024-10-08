package com.sap.cds.sdm.configuration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import com.sap.cds.services.Service;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class RegistrationTest {
  private Registration registration;
  private CdsRuntimeConfigurer configurer;
  private ServiceCatalog serviceCatalog;
  private PersistenceService persistenceService;
  private AttachmentService attachmentService;
  private OutboxService outboxService;
  private ArgumentCaptor<Service> serviceArgumentCaptor;
  private ArgumentCaptor<EventHandler> handlerArgumentCaptor;

  @BeforeEach
  void setup() {
    registration = new Registration();
    configurer = mock(CdsRuntimeConfigurer.class);
    CdsRuntime cdsRuntime = mock(CdsRuntime.class);
    when(configurer.getCdsRuntime()).thenReturn(cdsRuntime);
    serviceCatalog = mock(ServiceCatalog.class);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    CdsEnvironment environment = mock(CdsEnvironment.class);
    when(cdsRuntime.getEnvironment()).thenReturn(environment);
    persistenceService = mock(PersistenceService.class);
    attachmentService = mock(AttachmentService.class);
    outboxService = mock(OutboxService.class);
    serviceArgumentCaptor = ArgumentCaptor.forClass(Service.class);
    handlerArgumentCaptor = ArgumentCaptor.forClass(EventHandler.class);
  }

  @Test
  void serviceIsRegistered() {
    registration.services(configurer);

    verify(configurer).service(serviceArgumentCaptor.capture());
    var services = serviceArgumentCaptor.getAllValues();
    assertThat(services).hasSize(1);

    var attachmentServiceFound =
        services.stream().anyMatch(service -> service instanceof AttachmentService);

    assertThat(attachmentServiceFound).isTrue();
  }

  @Test
  void handlersAreRegistered() {
    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    when(serviceCatalog.getService(OutboxService.class, OutboxService.PERSISTENT_UNORDERED_NAME))
        .thenReturn(outboxService);

    registration.eventHandlers(configurer);

    var handlerSize = 1;
    verify(configurer, times(handlerSize)).eventHandler(handlerArgumentCaptor.capture());
    var handlers = handlerArgumentCaptor.getAllValues();
    assertThat(handlers).hasSize(handlerSize);
    isHandlerForClassIncluded(handlers, SDMAttachmentsServiceHandler.class);
  }

  private void isHandlerForClassIncluded(
      List<EventHandler> handlers, Class<? extends EventHandler> includedClass) {
    var isHandlerIncluded =
        handlers.stream().anyMatch(handler -> handler.getClass() == includedClass);
    assertThat(isHandlerIncluded).isTrue();
  }
}
