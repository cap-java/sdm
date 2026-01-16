package com.sap.cds.sdm.constants;

import java.util.Collection;
import java.util.List;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = System.getenv("REPOSITORY_ID");
  public static final String MIMETYPE_INTERNET_SHORTCUT = "application/internet-shortcut";
  public static final String SYSTEM_USER = "system-internal";
  public static final String SDM_READONLY_CONTEXT = "SDM_READONLY_CONTEXT";

  public static final String SDM_ANNOTATION_ADDITIONALPROPERTY_NAME =
      "SDM.Attachments.AdditionalProperty.name";
  public static final String SDM_ANNOTATION_ADDITIONALPROPERTY =
      "SDM.Attachments.AdditionalProperty";

  public static final String SDM_ENV_NAME = "sdm";
  public static final String ENTITY_PROCESSING_ERROR_LINK =
      "Failed to create link due to error while processing entity";
  public static final String SDM_TOKEN_EXCHANGE_DESTINATION = "sdm-token-exchange-flow";
  public static final String SDM_TECHNICAL_CREDENTIALS_FLOW_DESTINATION = "sdm-technical-user-flow";
  public static final String SDM_TOKEN_FETCH = "sdm-token-fetch";
  public static final String SDM_DESTINATION_KEY = "name";
  public static final String SDM_CONNECTIONPOOL_PREFIX = "cds.attachments.sdm.http.%s";
  public static final String USER_NOT_AUTHORISED_ERROR =
      "You do not have the required permissions to upload attachments. Please contact your administrator for access.";

  public static final String USER_NOT_AUTHORISED_ERROR_OPEN_LINK =
      "You do not have the required permissions to open links. Please contact your administrator for access.";
  public static final String FILE_NOT_FOUND_ERROR = "Object not found in repository";
  public static final Integer MAX_CONNECTIONS = 100;
  public static final int CONNECTION_TIMEOUT = 1200;
  public static final int CHUNK_SIZE = 20 * 1024 * 1024; // 20MB Chunk Size
  public static final String ATTACHMENT_MAXCOUNT = "SDM.Attachments.maxCount";
  public static final String MAX_COUNT_ERROR_MESSAGE =
      "Cannot upload more than %s attachments as set up by the application";
  public static final String DRAFT_READONLY_CONTEXT = "DRAFT_READONLY_CONTEXT";
  public static final Integer TIMEOUT_MILLISECONDS = 900000;
  public static final Integer MAX_CONNECTIONS_PER_ROUTE = 50;
  public static final Integer MAX_CONNECTIONS_TOTAL = 50;
  public static final String REST_V2_REPOSITORIES = "rest/v2/repositories";
  public static final String TECHNICAL_USER_FLOW = "TECHNICAL_CREDENTIALS_FLOW";
  public static final String NAMED_USER_FLOW = "TOKEN_EXCHANGE";
  public static final String ANNOTATION_IS_MEDIA_DATA = "_is_media_data";

  public static final String SINGLE_RESTRICTED_CHARACTER_IN_FILE =
      "\"%s\" contains unsupported characters (‘/’ or ‘\\’). Rename and try again.";

  // Upload Status Constants
  public static final String UPLOAD_STATUS_SUCCESS = "Success";
  public static final String UPLOAD_STATUS_VIRUS_DETECTED = "VirusDetected";
  public static final String UPLOAD_STATUS_IN_PROGRESS = "uploading";
  public static final String UPLOAD_STATUS_FAILED = "Failed";
  public static final String UPLOAD_STATUS_SCAN_FAILED = "Failed";
  public static final String VIRUS_SCAN_INPROGRESS = "VirusScanInprogress";

  // New scan status constants

  public enum ScanStatus {
    BLANK(""),
    PENDING("PENDING"),
    SCANNING("SCANNING"),
    CLEAN("CLEAN"),
    QUARANTINED("QUARANTINED"),
    FAILED("FAILED");

    private final String value;

    ScanStatus(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static ScanStatus fromValue(String value) {
      if (value == null || value.trim().isEmpty()) {
        return BLANK;
      }
      for (ScanStatus status : values()) {
        if (status.value.equalsIgnoreCase(value)) {
          return status;
        }
      }
      return BLANK; // Default to blank for unknown values
    }
  }

  // Helper Methods to create error/warning messages
  public static String buildErrorMessage(
      Collection<String> filenames, StringBuilder prefixTemplate, String closingRemark) {
    for (String file : filenames) {
      prefixTemplate.append(String.format("\t• %s%n", file));
    }
    if (closingRemark != null && !closingRemark.isEmpty())
      prefixTemplate.append("\n ").append(closingRemark);
    return prefixTemplate.toString();
  }

  // Restricted characters: / and \
  public static String nameConstraintMessage(List<String> invalidFileNames) {
    // if only 1 restricted character is there in file, so different error will throw
    if (invalidFileNames.size() == 1) {
      return String.format(SINGLE_RESTRICTED_CHARACTER_IN_FILE, invalidFileNames.iterator().next());
    }
    StringBuilder prefix = new StringBuilder();
    prefix.append(
        "The following names contain unsupported characters (‘/’ or ‘\\’). Rename and try again:\n\n");
    return buildErrorMessage(invalidFileNames, prefix, null);
  }
}
