package com.sap.cds.sdm.configuration;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.applicationservice.SDMCreateAttachmentsHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMReadAttachmentsHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMUpdateAttachmentsHandler;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.SDMAttachmentsService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import com.sap.cds.sdm.service.handler.SDMServiceGenericHandler;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class {@link Registration} is a configuration class that registers the services and event
 * handlers for the attachments feature.
 */
public class Registration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(Registration.class);

  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    configurer.service(buildAttachmentService());
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    logger.info("Registering event handler for attachment service");
    CacheConfig.initializeCache();
    CdsRuntime runtime = configurer.getCdsRuntime();
    CdsEnvironment environment = runtime.getEnvironment();
    var persistenceService =
        configurer
            .getCdsRuntime()
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);
    List<ServiceBinding> bindings =
        environment
            .getServiceBindings()
            .filter(b -> ServiceBindingUtils.matches(b, SDMConstants.SDM_ENV_NAME))
            .toList();
    var binding = !bindings.isEmpty() ? bindings.get(0) : null;

    // get HTTP connection pool configuration
    var connectionPool = getConnectionPool(environment);
    List<DraftService> draftServiceList =
        configurer.getCdsRuntime().getServiceCatalog().getServices(DraftService.class).toList();
    SDMService sdmService = new SDMServiceImpl(binding, connectionPool);
    DocumentUploadService documentService = new DocumentUploadService();
    configurer.eventHandler(buildReadHandler());
    configurer.eventHandler(new SDMCreateAttachmentsHandler(persistenceService, sdmService));
    configurer.eventHandler(new SDMUpdateAttachmentsHandler(persistenceService, sdmService));
    configurer.eventHandler(
        new SDMAttachmentsServiceHandler(persistenceService, sdmService, documentService));
    configurer.eventHandler(
        new SDMServiceGenericHandler(persistenceService, sdmService, draftServiceList.get(0)));
  }

  private AttachmentService buildAttachmentService() {
    logger.info("Registering SDM attachment service");
    return new SDMAttachmentsService();
  }

  private static CdsProperties.ConnectionPool getConnectionPool(CdsEnvironment env) {
    // the common prefix for the connection pool configuration
    final String prefix = SDMConstants.SDM_CONNECTIONPOOL_PREFIX;
    Duration timeout =
        Duration.ofSeconds(
            env.getProperty(
                prefix.formatted("timeout"), Integer.class, SDMConstants.CONNECTION_TIMEOUT));
    int maxConnections =
        env.getProperty(
            prefix.formatted("maxConnections"), Integer.class, SDMConstants.MAX_CONNECTIONS);
    logger.debug(
        "Connection pool configuration: timeout={}, maxConnections={}", timeout, maxConnections);
    return new CdsProperties.ConnectionPool(timeout, maxConnections, maxConnections);
  }

  protected EventHandler buildReadHandler() {
    return new SDMReadAttachmentsHandler();
  }
}
