package unit.com.sap.cds.sdm.configuration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.configuration.Registration;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import com.sap.cds.services.Service;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class RegistrationTest {
  private Registration registration;
  private CdsRuntimeConfigurer configurer;
  private ServiceCatalog serviceCatalog;
  private PersistenceService persistenceService;
  private AttachmentService attachmentService;
  private OutboxService outboxService;
  private ArgumentCaptor<Service> serviceArgumentCaptor;
  private ArgumentCaptor<EventHandler> handlerArgumentCaptor;
  private MockedStatic<CacheConfig> cacheConfigMock;

  @BeforeEach
  void setup() {
    // Mock CacheConfig to avoid cache initialization issues
    cacheConfigMock = mockStatic(CacheConfig.class);

    registration = new Registration();
    configurer = mock(CdsRuntimeConfigurer.class);
    CdsRuntime cdsRuntime = mock(CdsRuntime.class);
    when(configurer.getCdsRuntime()).thenReturn(cdsRuntime);
    serviceCatalog = mock(ServiceCatalog.class);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    CdsEnvironment environment = mock(CdsEnvironment.class);
    when(cdsRuntime.getEnvironment()).thenReturn(environment);
    when(environment.getProperty("cds.attachments.sdm.http.timeout", Integer.class, 1200))
        .thenReturn(1800);
    when(environment.getProperty("cds.attachments.sdm.http.maxConnections", Integer.class, 100))
        .thenReturn(200);

    persistenceService = mock(PersistenceService.class);
    attachmentService = mock(AttachmentService.class);
    outboxService = mock(OutboxService.class);
    serviceArgumentCaptor = ArgumentCaptor.forClass(Service.class);
    handlerArgumentCaptor = ArgumentCaptor.forClass(EventHandler.class);
  }

  @AfterEach
  void cleanup() {
    // Close the static mock to avoid interference between tests
    if (cacheConfigMock != null) {
      cacheConfigMock.close();
    }
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

    var handlerSize = 5;
    verify(configurer, times(handlerSize)).eventHandler(handlerArgumentCaptor.capture());
    var handlers = handlerArgumentCaptor.getAllValues();
    assertThat(handlers).hasSize(handlerSize);
    isHandlerForClassIncluded(handlers, SDMAttachmentsServiceHandler.class);
  }

  @Test
  void testEventHandlersWithEmptyServiceBindings() {
    // Arrange
    CdsRuntime cdsRuntime = mock(CdsRuntime.class);
    when(configurer.getCdsRuntime()).thenReturn(cdsRuntime);
    ServiceCatalog serviceCatalog = mock(ServiceCatalog.class);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    CdsEnvironment environment = mock(CdsEnvironment.class);
    when(cdsRuntime.getEnvironment()).thenReturn(environment);

    // Empty bindings stream
    Stream<ServiceBinding> emptyBindingsStream = Stream.empty();
    when(environment.getServiceBindings()).thenReturn(emptyBindingsStream);

    when(environment.getProperty("cds.attachments.sdm.http.timeout", Integer.class, 1200))
        .thenReturn(1200);
    when(environment.getProperty("cds.attachments.sdm.http.maxConnections", Integer.class, 100))
        .thenReturn(100);

    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    when(serviceCatalog.getServices(any())).thenReturn(Stream.empty());

    // Act
    registration.eventHandlers(configurer);

    // Assert - should still register handlers even with empty bindings
    verify(configurer, atLeast(1)).eventHandler(any(EventHandler.class));
  }

  @Test
  void testEventHandlersWithCustomConnectionPoolSettings() {
    // Arrange
    CdsRuntime cdsRuntime = mock(CdsRuntime.class);
    when(configurer.getCdsRuntime()).thenReturn(cdsRuntime);
    ServiceCatalog serviceCatalog = mock(ServiceCatalog.class);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    CdsEnvironment environment = mock(CdsEnvironment.class);
    when(cdsRuntime.getEnvironment()).thenReturn(environment);

    Stream<ServiceBinding> emptyBindingsStream = Stream.empty();
    when(environment.getServiceBindings()).thenReturn(emptyBindingsStream);

    // Custom timeout and connection settings
    when(environment.getProperty("cds.attachments.sdm.http.timeout", Integer.class, 1200))
        .thenReturn(2400);
    when(environment.getProperty("cds.attachments.sdm.http.maxConnections", Integer.class, 100))
        .thenReturn(300);

    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    when(serviceCatalog.getServices(any())).thenReturn(Stream.empty());

    // Act
    registration.eventHandlers(configurer);

    // Assert - verify environment properties were accessed with custom values
    verify(environment).getProperty("cds.attachments.sdm.http.timeout", Integer.class, 1200);
    verify(environment).getProperty("cds.attachments.sdm.http.maxConnections", Integer.class, 100);
  }

  @Test
  void testRegistrationImplementsCdsRuntimeConfiguration() {
    // Test that Registration properly implements the CdsRuntimeConfiguration interface
    assertThat(registration)
        .isInstanceOf(com.sap.cds.services.runtime.CdsRuntimeConfiguration.class);
  }

  @Test
  void testServicesWithMultipleServiceCalls() {
    // Act
    registration.services(configurer);
    registration.services(configurer); // Call twice

    // Assert - service should be registered each time
    verify(configurer, times(2)).service(any(Service.class));
  }

  @Test
  void testEventHandlersRegistersCorrectNumberOfHandlers() {
    // Arrange
    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    when(serviceCatalog.getServices(any())).thenReturn(Stream.empty());

    // Act
    registration.eventHandlers(configurer);

    // Assert - exactly 5 handlers should be registered
    verify(configurer, times(5)).eventHandler(handlerArgumentCaptor.capture());
    var handlers = handlerArgumentCaptor.getAllValues();
    assertThat(handlers).hasSize(5);

    // Verify we have different types of handlers
    var handlerClassNames =
        handlers.stream().map(handler -> handler.getClass().getSimpleName()).toList();

    assertThat(handlerClassNames).contains("SDMAttachmentsServiceHandler");
  }

  private void isHandlerForClassIncluded(
      List<EventHandler> handlers, Class<? extends EventHandler> includedClass) {
    var isHandlerIncluded =
        handlers.stream().anyMatch(handler -> handler.getClass() == includedClass);
    assertThat(isHandlerIncluded).isTrue();
  }
}
