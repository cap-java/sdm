package com.sap.cds.sdm.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.CdsData;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.AttachmentInfo;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMUtils {
  private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

  private SDMUtils() {
    // Doesn't do anything
  }

  public static Set<String> FileNameContainsWhitespace(
      List<CdsData> data, String composition, String targetEntity) {
    Set<String> filenamesWithWhitespace = new HashSet<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments =
          AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, composition);
      if (attachments != null) {
        Iterator<Map<String, Object>> iterator = attachments.iterator();
        while (iterator.hasNext()) {
          Map<String, Object> attachment = iterator.next();
          String filenameInRequest = (String) attachment.get("fileName");
          if (filenameInRequest == null || filenameInRequest.isBlank()) {
            filenamesWithWhitespace.add("Whitespace/null");
          }
        }
      }
    }
    return filenamesWithWhitespace;
  }

  public static Set<String> FileNameDuplicateInDrafts(
      List<CdsData> data, String composition, String targetEntity, String upIdKey) {
    Set<String> uniqueFilenames = new HashSet<>();
    Set<String> duplicateFilenames = new HashSet<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments =
          AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, composition);
      if (attachments != null) {
        Iterator<Map<String, Object>> iterator = attachments.iterator();
        while (iterator.hasNext()) {
          Map<String, Object> attachment = iterator.next();
          String filenameInRequest = (String) attachment.get("fileName");
          if (filenameInRequest != null && !filenameInRequest.isBlank()) {
            String repositoryInRequest = (String) attachment.get("repositoryId");
            String upId = (String) attachment.get(upIdKey);
            String fileRepositorySpecific =
                filenameInRequest + "#" + repositoryInRequest + "#" + upId;
            logger.info("Filename key check : " + fileRepositorySpecific);
            if (!uniqueFilenames.add(fileRepositorySpecific)) {
              duplicateFilenames.add(filenameInRequest);
            }
          }
        }
      }
    }
    return duplicateFilenames;
  }

  public static List<String> FileNameContainsRestrictedCharaters(
      List<CdsData> data, String composition, String targetEntity) {
    List<String> restrictedFilenames = new ArrayList<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments =
          AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, composition);
      if (attachments != null) {
        Iterator<Map<String, Object>> iterator = attachments.iterator();
        while (iterator.hasNext()) {
          Map<String, Object> attachment = iterator.next();
          String filenameInRequest = (String) attachment.get("fileName");
          if (hasRestrictedCharactersInName(filenameInRequest)) {
            restrictedFilenames.add(filenameInRequest);
          }
        }
      }
    }
    return restrictedFilenames;
  }

  public static boolean hasRestrictedCharactersInName(String cmisName) {
    if (cmisName == null || cmisName.isEmpty()) {
      return false;
    }
    String regex = "[/\\\\]";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(cmisName);
    return matcher.find();
  }

  public static void prepareSecondaryProperties(
      Map<String, String> requestBody, Map<String, String> secondaryProperties) {
    Iterator<Map.Entry<String, String>> iterator = secondaryProperties.entrySet().iterator();

    int index = 1;
    while (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      if ("filename".equals(entry.getKey())) {
        requestBody.put("propertyId[" + index + "]", "cmis:name");
        requestBody.put("propertyValue[" + index + "]", entry.getValue());
      } else if ("description".equals(entry.getKey())) {
        requestBody.put("propertyId[" + index + "]", "cmis:description");
        requestBody.put("propertyValue[" + index + "]", entry.getValue());
      } else {
        requestBody.put("propertyId[" + index + "]", entry.getKey());
        requestBody.put("propertyValue[" + index + "]", entry.getValue());
      }
      index++;
    }
  }

  public static Boolean checkMCM(HttpEntity responseEntity, List<String> secondaryPropertyIds)
      throws IOException {
    Boolean flag = false;
    String responseString = EntityUtils.toString(responseEntity, "UTF-8");

    if (responseString == null || responseString.isEmpty()) {
      return flag;
    }

    JSONObject jsonObject = new JSONObject(responseString);

    if (!jsonObject.has("propertyDefinitions")) {
      return flag;
    }

    JSONObject propertyDefinitions = jsonObject.getJSONObject("propertyDefinitions");

    if (propertyDefinitions == null) {
      return flag;
    }

    for (String key : propertyDefinitions.keySet()) {
      JSONObject property = propertyDefinitions.optJSONObject(key);
      JSONObject miscellaneous =
          (property != null) ? property.optJSONObject("mcm:miscellaneous") : null;

      if (miscellaneous != null
          && "true".equals(miscellaneous.optString("isPartOfTable", "false"))) {
        secondaryPropertyIds.add(key);
        flag = true;
      }
    }
    return flag;
  }

  public static void assembleRequestBodySecondaryTypes(
      MultipartEntityBuilder builder, Map<String, String> requestBody, String objectId) {
    for (Map.Entry<String, String> entry : requestBody.entrySet()) {
      builder.addTextBody(entry.getKey(), entry.getValue(), ContentType.TEXT_PLAIN);
    }

    builder.addTextBody("objectId", objectId, ContentType.TEXT_PLAIN);
    builder.addTextBody("cmisaction", "update", ContentType.TEXT_PLAIN);
  }

  public static void extractSecondaryTypeIds(JSONArray jsonArray, List<String> result) {
    String secondaryType;
    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject jsonObject = jsonArray.getJSONObject(i);

      // Extract and store the type ID if it exists
      if (jsonObject.has("type") && jsonObject.getJSONObject("type").has("id")) {
        secondaryType = jsonObject.getJSONObject("type").getString("id");
        result.add(secondaryType);
      }

      // If this object has children, recursively process them
      if (jsonObject.has("children")) {
        JSONArray children = jsonObject.getJSONArray("children");
        extractSecondaryTypeIds(children, result);
      }
    }
  }

  /*
   * Create a map of property names to their UI titles for intuitive error
   * messages.
   */
  public static Map<String, String> getPropertyTitles(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    Map<String, String> titleMap = new HashMap<>();
    if (attachmentEntity.isEmpty()) {
      return titleMap;
    }
    CdsEntity entity = attachmentEntity.get();
    for (String key : attachment.keySet()) {
      if (SDMConstants.DRAFT_READONLY_CONTEXT.equals(key) || entity.getElement(key) == null) {
        continue;
      }

      CdsElement element = entity.getElement(key);
      String propertyName = extractPropertyName(element);
      String title = extractTitle(element);

      if (propertyName != null && title != null) {
        titleMap.put(propertyName, title);
      }
    }
    return titleMap;
  }

  private static String extractPropertyName(CdsElement element) {
    /*
     * Check both old and new SDM annotations to track titles for properties needing
     * error handling.
     */
    if (element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME).isPresent()) {
      return element
          .findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME)
          .get()
          .getValue()
          .toString();
    } else if (element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY).isPresent()) {
      return element.getName(); // This is in case the user has not specified a title for the column
    }
    return null;
  }

  private static String extractTitle(CdsElement element) {
    return element
        .findAnnotation("title")
        .map(annotation -> annotation.getValue().toString())
        .orElse(element.getName());
  }

  /*
   * Identify incorrectly defined properties in the CDS file to group them with
   * unsupported ones where "MCM" is not true.
   */
  public static Map<String, String> getSecondaryPropertiesWithInvalidDefinition(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    List<String> keysList = new ArrayList<>(attachment.keySet());
    Map<String, String> invalidProperties = new HashMap<>();
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if (SDMConstants.DRAFT_READONLY_CONTEXT.equals(key)) {
          continue; // Skip updateProperties processing for DRAFT_READONLY_CONTEXT
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          // Checking the outdated/old SDM Annotation
          Optional<CdsAnnotation<Object>> sdmAnnotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
          if (sdmAnnotation.isPresent()) {
            Optional<CdsAnnotation<Object>> titleAnnotation = element.findAnnotation("title");
            String title = null;
            if (titleAnnotation.isPresent()) {
              title = titleAnnotation.get().getValue().toString();
            } else {
              title = element.getName(); /*
                               * This is in case the user has not specified a title for the column in the cds
                               * file (which is optional)
                               */
            }
            invalidProperties.put(key, title);
          }
        }
      }
    }
    return invalidProperties;
  }

  // Create a map of secondary property name and its title that appears on the UI.
  public static Map<String, String> getSecondaryTypeProperties(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    List<String> keysList = new ArrayList<>(attachment.keySet());
    Map<String, String> secondaryTypeProperties = new HashMap<>();
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if (SDMConstants.DRAFT_READONLY_CONTEXT.equals(key)) {
          continue; // Skip updateProperties processing for DRAFT_READONLY_CONTEXT
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          // Checking the SDM Annotation, both the old (outdated method) and the correct
          // method.
          Optional<CdsAnnotation<Object>> annotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
          Optional<CdsAnnotation<Object>> nameAnnotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME);
          if (annotation.isPresent()) {
            // If the property was defined using the old method, we will use the actual name
            // of the
            // property
            secondaryTypeProperties.put(element.getName(), element.getName());
          }
          if (nameAnnotation.isPresent()) {
            // If the property was defined using the new method, we will use the name
            // specified in
            // the annotation
            secondaryTypeProperties.put(
                element.getName(), nameAnnotation.get().getValue().toString());
          }
        }
      }
    }
    return secondaryTypeProperties;
  }

  public static Map<String, String> getUpdatedSecondaryProperties(
      Optional<CdsEntity> attachmentEntity,
      Map<String, Object> attachment,
      PersistenceService persistenceService,
      Map<String, String> secondaryTypeProperties,
      Map<String, String> propertiesInDB) {
    Map<String, String> updatedSecondaryProperties = new HashMap<>();
    // Checking and storing the modified values of the secondary type properties
    Map<String, Object> propertiesMap = new HashMap<>();
    for (Map.Entry<String, String> entry : secondaryTypeProperties.entrySet()) {
      String property = entry.getKey();
      Object value = attachment.get(property);
      propertiesMap.put(property, value);
    }
    // Check the value of secondary properties in DB
    for (Map.Entry<String, String> entry : secondaryTypeProperties.entrySet()) {
      String property = entry.getKey();
      String value = entry.getValue();
      String valueInDB = propertiesInDB.get(property);
      Object valueInMap = propertiesMap.get(property);
      if ((valueInMap == null && valueInDB != null)
          || (valueInMap != null && !valueInMap.equals(valueInDB))) {
        if (valueInMap != null) {
          updatedSecondaryProperties.put(value, valueInMap.toString());
        } else {
          updatedSecondaryProperties.put(value, null);
        }
      }
    }

    return updatedSecondaryProperties;
  }

  public static String getAttachmentCountAndMessage(
      List<CdsEntity> entities, CdsEntity attachmentEntity) {
    String maxCount =
        CacheConfig.getMaxAllowedAttachmentsCache().get(attachmentEntity.getQualifiedName());

    if (maxCount == null) {
      AttachmentInfo attachmentInfo = new AttachmentInfo();
      determineAttachmentDetails(attachmentEntity, entities, attachmentInfo);
      maxCount = attachmentInfo.getAttachmentCount() + "__" + attachmentInfo.getErrorMessage();
      CacheConfig.getMaxAllowedAttachmentsCache()
          .put(attachmentEntity.getQualifiedName(), maxCount);
    }
    return maxCount;
  }

  private static void determineAttachmentDetails(
      CdsEntity attachmentEntity, List<CdsEntity> entities, AttachmentInfo attachmentInfo) {

    for (CdsEntity cdsEntity : entities) {
      if (isRelatedEntity(attachmentEntity, cdsEntity)) {
        processCompositions(cdsEntity, attachmentInfo, attachmentEntity);
      }
    }
  }

  public static boolean isRelatedEntity(CdsEntity attachmentEntity, CdsEntity cdsEntity) {
    String attachmentQualifiedName = attachmentEntity.getQualifiedName();
    return attachmentQualifiedName.contains(cdsEntity.getQualifiedName())
        && !attachmentQualifiedName.equals(cdsEntity.getQualifiedName());
  }

  public static String getUpIdKey(CdsEntity attachmentDraftEntity) {
    String upIdKey = "";
    Optional<CdsElement> upAssociation = attachmentDraftEntity.findAssociation("up_");
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      // get association type
      CdsAssociationType associationType = association.getType();
      // get the refs of the association
      List<String> fkElements = associationType.refs().map(ref -> "up__" + ref.path()).toList();
      if (!fkElements.isEmpty()) {
        upIdKey = fkElements.get(0);
      }
    }
    // Fallback: if no association found, try to find element starting with "up__"
    if (upIdKey.isEmpty()) {
      Optional<CdsElement> upElement =
          attachmentDraftEntity.elements().filter(e -> e.getName().startsWith("up__")).findFirst();
      if (upElement.isPresent()) {
        upIdKey = upElement.get().getName();
      }
    }
    return upIdKey;
  }

  private static void processCompositions(
      CdsEntity cdsEntity, AttachmentInfo attachmentInfo, CdsEntity attachmentEntity) {
    List<CdsElement> compositions = cdsEntity.compositions().toList();

    for (CdsElement cdsElement : compositions) {
      String elementName = cdsElement.getQualifiedName().replaceAll(":", ".");
      if (elementName.equalsIgnoreCase(attachmentEntity.getQualifiedName())) {
        retrieveAnnotations(cdsElement, attachmentInfo);
      }
    }
  }

  private static void retrieveAnnotations(CdsElement cdsElement, AttachmentInfo attachmentInfo) {

    Optional<CdsAnnotation<Object>> maxcountAnnotation =
        cdsElement.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT);
    maxcountAnnotation.ifPresent(
        annotation ->
            attachmentInfo.setAttachmentCount(Long.parseLong(annotation.getValue().toString())));

    Optional<CdsAnnotation<Object>> errormsgAnnotation =
        cdsElement.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT_ERROR_MSG);
    errormsgAnnotation.ifPresent(
        annotation -> attachmentInfo.setErrorMessage(annotation.getValue().toString()));
  }

  private static List<String> getKeyElementNames(CdsEntity entity) {
    return entity.elements().filter(CdsElement::isKey).map(CdsElement::getName).toList();
  }

  /**
   * Extracts UP ID from CQN select statement by parsing the JSON representation.
   *
   * @param select the CQN select statement
   * @return the UP ID extracted from the query
   * @throws com.sap.cds.services.ServiceException if UP ID cannot be extracted
   */
  public static String fetchUPIDFromCQN(CqnSelect select, CdsEntity parentEntity) {
    try {
      String upID = null;
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(select.toString());
      JsonNode refArray = root.path("SELECT").path("from").path("ref");

      JsonNode secondLast = refArray.get(refArray.size() - 2);
      JsonNode whereArray;
      if (secondLast != null) {
        whereArray = secondLast.path("where");
      } else {
        whereArray = refArray;
      }

      // Get the actual key field names from the parent entity
      List<String> keyElementNames = getKeyElementNames(parentEntity);

      for (int i = 0; i < whereArray.size(); i++) {
        JsonNode node = whereArray.get(i);

        if (node.has("ref") && node.get("ref").isArray()) {
          String fieldName = node.get("ref").get(0).asText();

          if (keyElementNames.contains(fieldName) && !fieldName.equals("IsActiveEntity")) {
            JsonNode valNode = whereArray.get(i + 2);
            upID = valNode.path("val").asText();
            break;
          }
        }
      }
      if (upID == null) {
        throw new ServiceException(SDMConstants.ENTITY_PROCESSING_ERROR_LINK);
      }
      return upID;
    } catch (Exception e) {
      logger.error(SDMConstants.ENTITY_PROCESSING_ERROR_LINK, e);
      throw new ServiceException(SDMConstants.ENTITY_PROCESSING_ERROR_LINK, e);
    }
  }

  /**
   * Get criticality value based on upload status for UI display
   *
   * @param uploadStatus The upload status string
   * @return Integer criticality value (1=Error/Red, 2=Warning/Yellow, 3=Success/Green,
   *     0=None/Neutral)
   */
  public static Integer getCriticalityForStatus(String uploadStatus) {
    if (uploadStatus == null) {
      return 0; // None/Neutral
    }

    switch (uploadStatus) {
      case SDMConstants.UPLOAD_STATUS_IN_PROGRESS:
      case SDMConstants.VIRUS_SCAN_INPROGRESS:
        return 5; // Warning (yellow)
      case SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED:
      case SDMConstants.UPLOAD_STATUS_FAILED:
        return 1; // Error (red)
      default:
        return 0; // None (neutral)
    }
  }
}
