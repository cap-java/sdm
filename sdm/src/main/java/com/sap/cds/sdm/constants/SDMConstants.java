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

  public static final String GENERIC_ERROR = "Could not %s the document.";

  public static final String VIRUS_ERROR = "%s contains potential malware and cannot be uploaded.";

  public static final String SDM_MISSING_ROLES_EXCEPTION_MSG =
      "You do not have the required permissions to update attachments. Kindly contact the admin";

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
  public static final String SINGLE_DUPLICATE_FILENAME =
      "An object named \"%s\" already exists. Rename the object and try again.";
  public static final String VIRUS_DETECTED_ERROR_MSG =
      "You can't save your changes because some files are unsafe. Delete the unsafe files manually before continuing. You can use a filter to help you find the affected files.";
  public static final String SCAN_FAILED_ERROR_MSG =
      "You can't save your changes because some files not scanned. Delete the unscanned files manually before continuing.";
  public static final String VIRUS_SCAN_IN_PROGRESS_ERROR_MSG =
      "Refresh the page to see scanning is completed.";

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

  // Duplicate file names error message
  public static String duplicateFilenameFormat(Collection<String> duplicateFileNames) {
    // if only 1 duplicate file, so different error will throw
    if (duplicateFileNames.size() == 1) {
      return String.format(SINGLE_DUPLICATE_FILENAME, duplicateFileNames.iterator().next());
    }
    StringBuilder prefix = new StringBuilder();
    prefix.append("Objects with the following names already exist:\n\n");
    String closingRemark = "Rename the objects and try again";
    return buildErrorMessage(duplicateFileNames, prefix, closingRemark);
  }

  public static String fileNotFound(List<String> fileNameNotFound) {
    // Create the base message
    String prefixMessage =
        "Update unsuccessful. The following filename(s) could not be updated as they do not exist. \n\n";

    // Create the formatted prefix message
    String formattedPrefixMessage = String.format(prefixMessage);

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(formattedPrefixMessage);

    // Append each unsupported file name to the StringBuilder
    for (String file : fileNameNotFound) {
      bulletPoints.append(String.format("\t• %s%n", file));
    }
    bulletPoints.append("\nDelete and upload the files again.");
    return bulletPoints.toString();
  }

  public static String noSDMRolesMessage(List<String> files, String operation) {
    // Create the base message
    String prefixMessage = "Could not " + operation + " the following files. \n\n";

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(prefixMessage);

    // Append each file name and its error message to the StringBuilder
    for (String item : files) {
      bulletPoints.append(String.format("\t• %s%n", item));
    }
    bulletPoints.append(System.lineSeparator());
    if (operation.equals("create")) {
      bulletPoints.append(USER_NOT_AUTHORISED_ERROR);
    } else {
      bulletPoints.append(SDM_MISSING_ROLES_EXCEPTION_MSG);
    }

    return bulletPoints.toString();
  }

  public static String unsupportedPropertiesMessage(List<String> propertiesList) {
    // Create the base message
    String prefixMessage = "The following secondary properties are not supported.\n\n";

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(prefixMessage);

    // Append each unsupported file name to the StringBuilder
    for (String file : propertiesList) {
      bulletPoints.append(String.format("\t• %s%n", file));
    }
    bulletPoints.append(
        "\nPlease contact your administrator for assistance with any necessary adjustments.");
    return bulletPoints.toString();
  }

  public static String getGenericError(String event) {
    return String.format(GENERIC_ERROR, event);
  }

  public static String getVirusFilesError(String filename) {
    return String.format(VIRUS_ERROR, filename);
  }
}
