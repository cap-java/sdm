package com.sap.cds.sdm.utilities;

import com.sap.cds.CdsData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
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

  public static int prepareSecondaryProperties(
      Map<String, String> requestBody, Map<String, String> secondaryProperties, String fileName) {
    int index = 1;
    Iterator<Map.Entry<String, String>> iterator = secondaryProperties.entrySet().iterator();

    if (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      if ("fileName".equals(entry.getKey())) {
        requestBody.put("propertyId[1]", "cmis:name");
        requestBody.put("propertyValue[1]", entry.getValue());
        index++;
      } else {
        requestBody.put("propertyId[1]", "cmis:name");
        requestBody.put("propertyValue[1]", fileName);
      }

      while (iterator.hasNext()) {
        entry = iterator.next();
        String updatedKey = "propertyId[" + index + "]";
        String updatedValue = entry.getKey().replace("___", ":");
        requestBody.put(updatedKey, updatedValue);

        if (!"cmis___rm_holdIds".equals(entry.getKey()) || entry.getValue() != null) {
          String valueKey = "propertyValue[" + index + "]";
          requestBody.put(valueKey, entry.getValue());
        }
        index++;
      }
    }

    return index;
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
    String secondaryType = new String();
    List<String> excludedSecondaryTypes = new ArrayList<>();
    Collections.addAll(
        excludedSecondaryTypes,
        "cmis:rm_clientMgtRetention",
        "cmis:rm_destructionRetention",
        "sap:createLink",
        "sap:restoreVersion",
        "sap:createFavorite");
    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject jsonObject = jsonArray.getJSONObject(i);

      // Extract and store the type ID if it exists
      if (jsonObject.has("type") && jsonObject.getJSONObject("type").has("id")) {
        secondaryType = jsonObject.getJSONObject("type").getString("id");

        // Check if the secondaryType is in the excludedSecondaryTypes list
        if (excludedSecondaryTypes.contains(secondaryType)) {
          continue; // Skip the current iteration
        }

        System.out.println("Found a type : " + secondaryType);
        result.add(jsonObject.getJSONObject("type").getString("id"));
      }

      // If this object has children, recursively process them
      if (jsonObject.has("children")) {
        JSONArray children = jsonObject.getJSONArray("children");
        extractSecondaryTypeIds(children, result);
      }
    }
  }
}
