package com.sap.cds.sdm.utilities;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
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

  public static Set<String> isFileNameDuplicateInDrafts(List<CdsData> data) {
    Set<String> uniqueFilenames = new HashSet<>();
    Set<String> duplicateFilenames = new HashSet<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
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

  public static List<String> getSecondaryTypeProperties(
      Optional<CdsEntity> attachmentEntity, Map<String, Object> attachment) {
    List<String> keysList = new ArrayList<>(attachment.keySet());
    List<String> secondaryTypeProperties = new ArrayList<>();
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if ("DRAFT_READONLY_CONTEXT".equals(key)) {
          continue; // Skip updateProperties processing for DRAFT_READONLY_CONTEXT
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          // Check if secondary property is present
          Optional<CdsAnnotation<Object>> annotation =
              element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
          if (annotation.isPresent()) {
            secondaryTypeProperties.add(element.getName());
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
      List<String> secondaryTypeProperties,
      Map<String, String> propertiesInDB) {
    Map<String, String> updatedSecondaryProperties = new HashMap<>();
    // Checking and storing the modified values of the secondary type properties
    Map<String, Object> propertiesMap = new HashMap<>();
    for (String property : secondaryTypeProperties) {
      Object value = attachment.get(property);
      propertiesMap.put(property, value);
    }
    // Check the value of secondary properties in DB
    for (String property : secondaryTypeProperties) {
      String valueInDB = propertiesInDB.get(property);
      Object valueInMap = propertiesMap.get(property);

      if ((valueInMap == null && valueInDB != null)
          || (valueInMap != null && !valueInMap.equals(valueInDB))) {
        if (valueInMap != null) {
          updatedSecondaryProperties.put(property, valueInMap.toString());
        } else {
          updatedSecondaryProperties.put(property, null);
        }
      }
    }

    return updatedSecondaryProperties;
  }
}
