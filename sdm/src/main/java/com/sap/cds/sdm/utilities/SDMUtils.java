package com.sap.cds.sdm.utilities;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.AttachmentInfo;
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

public class SDMUtils {

  private SDMUtils() {
    // Doesn't do anything
  }

  public static Set<String> isFileNameDuplicateInDrafts(List<CdsData> data, String composition) {
    Set<String> uniqueFilenames = new HashSet<>();
    Set<String> duplicateFilenames = new HashSet<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get(composition);
      if (attachments != null) {
        Iterator<Map<String, Object>> iterator = attachments.iterator();
        while (iterator.hasNext()) {
          Map<String, Object> attachment = iterator.next();
          String filenameInRequest = (String) attachment.get("fileName");
          String repositoryInRequest = (String) attachment.get("repositoryId");
          String fileRepositorySpecific = filenameInRequest + "#" + repositoryInRequest;
          if (!uniqueFilenames.add(fileRepositorySpecific)) {
            duplicateFilenames.add(filenameInRequest);
          }
        }
      }
    }
    return duplicateFilenames;
  }

  public static List<String> isFileNameContainsRestrictedCharaters(List<CdsData> data) {
    List<String> restrictedFilenames = new ArrayList();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
      if (attachments != null) {
        Iterator<Map<String, Object>> iterator = attachments.iterator();
        while (iterator.hasNext()) {
          Map<String, Object> attachment = iterator.next();
          String filenameInRequest = (String) attachment.get("fileName");
          if (isRestrictedCharactersInName(filenameInRequest)) {
            restrictedFilenames.add(filenameInRequest);
          }
        }
      }
    }
    return restrictedFilenames;
  }

  public static boolean isRestrictedCharactersInName(String cmisName) {
    String regex = "[/\\\\]";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(cmisName);
    return matcher.find();
  }

  public static void prepareSecondaryProperties(
      Map<String, String> requestBody, Map<String, String> secondaryProperties, String fileName) {
    Iterator<Map.Entry<String, String>> iterator = secondaryProperties.entrySet().iterator();

    int index = 1;
    while (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      if ("filename".equals(entry.getKey())) {
        requestBody.put("propertyId[" + index + "]", "cmis:name");
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

  // In this method we form a map of property name and it's title that appears on the UI. This is so
  // that we can refer to this map while propagating an error to the UI using the title so that it
  // is intuitive for the user.
  public static Map<String, String> getPropertyTitles(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    List<String> keysList = new ArrayList<>(attachment.keySet());
    Map<String, String> titleMap = new HashMap<>();
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if ("DRAFT_READONLY_CONTEXT".equals(key)) {
          continue; // Skip updateProperties processing for DRAFT_READONLY_CONTEXT
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          // Checking the SDM Annotation, both the old (outdated method) and the correct method.
          // This is because we will have to throw an error for the properties defined using the old
          // method, and for that we will keep track of those titles as well
          Optional<CdsAnnotation<Object>> titleAnnotation = element.findAnnotation("title");
          Optional<CdsAnnotation<Object>> propertyNameInSDM =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME);
          Optional<CdsAnnotation<Object>> propertyNameInSDMIncorrect =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
          String propertyName = null;
          String title = null;
          if (propertyNameInSDM.isPresent()) {
            propertyName = propertyNameInSDM.get().getValue().toString();
          }
          if (propertyNameInSDMIncorrect.isPresent()) {
            propertyName = element.getName();
          }
          if (titleAnnotation.isPresent()) {
            title = titleAnnotation.get().getValue().toString();
          } else {
            title =
                element
                    .getName(); // This is in case the user has not specified a title for the column
            // in the cds file (which is optional)
          }
          if (propertyName != null && title != null) {
            titleMap.put(propertyName, title);
          }
        }
      }
    }
    return titleMap;
  }

  // In this method we identify the properties that were defined incorrectly in the CDS file. This
  // is so that we can club them with the properties for which the "MCM" value is not true, making
  // it an invalid/unsupported property from the context of this plugin
  public static Map<String, String> getSecondaryPropertiesWithInvalidDefinition(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    List<String> keysList = new ArrayList<>(attachment.keySet());
    Map<String, String> invalidProperties = new HashMap<>();
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if ("DRAFT_READONLY_CONTEXT".equals(key)) {
          continue; // Skip updateProperties processing for DRAFT_READONLY_CONTEXT
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          // Checking the outdated/old SDM Annotation
          Optional<CdsAnnotation<Object>> SDMAnnotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
          if (SDMAnnotation.isPresent()) {
            Optional<CdsAnnotation<Object>> titleAnnotation = element.findAnnotation("title");
            String title = null;
            if (titleAnnotation.isPresent()) {
              title = titleAnnotation.get().getValue().toString();
            } else {
              title = element.getName(); // This is in case the user has not specified a title for
              // the column in the cds file (which is optional)
            }
            invalidProperties.put(key, title);
          }
        }
      }
    }
    return invalidProperties;
  }

  // In this method we form a map of secondary property name and its title that appears on the UI.
  public static Map<String, String> getSecondaryTypeProperties(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    List<String> keysList = new ArrayList<>(attachment.keySet());
    Map<String, String> secondaryTypeProperties = new HashMap<>();
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if ("DRAFT_READONLY_CONTEXT".equals(key)) {
          continue; // Skip updateProperties processing for DRAFT_READONLY_CONTEXT
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          // Checking the SDM Annotation, both the old (outdated method) and the correct method.
          Optional<CdsAnnotation<Object>> annotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
          Optional<CdsAnnotation<Object>> nameAnnotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME);
          if (annotation.isPresent()) {
            // If the property was defined using the old method, we will use the actual name of the
            // property
            secondaryTypeProperties.put(element.getName(), element.getName());
          }
          if (nameAnnotation.isPresent()) {
            // If the property was defined using the new method, we will use the name specified in
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

  private static boolean isRelatedEntity(CdsEntity attachmentEntity, CdsEntity cdsEntity) {
    String attachmentQualifiedName = attachmentEntity.getQualifiedName();
    return attachmentQualifiedName.contains(cdsEntity.getQualifiedName())
        && !attachmentQualifiedName.equals(cdsEntity.getQualifiedName());
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
}
