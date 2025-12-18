package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.ErrorMessageKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorKeys;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.Map;
import org.ehcache.Cache;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsHandler implements EventHandler {

  public SDMReadAttachmentsHandler() {}

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      CqnSelect copy =
          CQL.copy(
              context.getCqn(),
              new Modifier() {
                @Override
                public Predicate where(Predicate where) {
                  return CQL.and(where, CQL.get("repositoryId").eq(repositoryId));
                }
              });
      setErrorMessagesInCache(context);
      context.setCqn(copy);

    } else {
      context.setCqn(context.getCqn());
    }
  }

  /*
    Error message caching requires the CAP context to retrieve localized messages, which may not be
    available at all error throw sites. To ensure availability, error messages are cached during the
    before read event when the context is guaranteed to be present.
  */
  private void setErrorMessagesInCache(CdsReadEventContext context) {
    // Check if cache is available
    Cache<ErrorMessageKey, String> errorMessageCache = CacheConfig.getErrorMessageCache();
    if (errorMessageCache == null) {
      return; // Cache not initialized, skip
    }

    // Check if localized error messages are already cached
    ErrorMessageKey cacheCheckKey = new ErrorMessageKey();
    cacheCheckKey.setKey("localizedErrorMessagesSetInCache");
    String cacheValue = errorMessageCache.get(cacheCheckKey);

    if ("true".equals(cacheValue)) {
      return; // Skip processing if already cached
    }

    Map<String, Object> errorMessages = SDMErrorMessages.getAllErrorMessages();
    Map<String, Object> errorKeys = SDMErrorKeys.getAllErrorKeys();
    String localizedMessage;
    String localizedErrorMessageKey;
    for (String errorMessage : errorMessages.keySet()) {
      localizedErrorMessageKey = String.valueOf(errorKeys.get(errorMessage + "_KEY"));
      localizedMessage =
          context
              .getCdsRuntime()
              .getLocalizedMessage(
                  localizedErrorMessageKey, null, context.getParameterInfo().getLocale());
      ErrorMessageKey errorMessageKey = new ErrorMessageKey();
      errorMessageKey.setKey(errorMessage);
      errorMessageCache.put(
          errorMessageKey,
          java.util.Objects.equals(localizedMessage, localizedErrorMessageKey)
              ? String.valueOf(errorMessages.get(errorMessage))
              : localizedMessage);
    }

    // Mark that localized error messages have been cached
    errorMessageCache.put(cacheCheckKey, "true");
  }
}
