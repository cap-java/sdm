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
  public static final String SDM_ANNOTATION_ADDITIONALPROPERTY_NAME =
      "SDM.Attachments.AdditionalProperty.name";
  public static final String SDM_ANNOTATION_ADDITIONALPROPERTY =
      "SDM.Attachments.AdditionalProperty";
  public static final String DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE =
      "The file(s) %s have been added multiple times. Please rename and try again.";
  public static final String FILES_RENAME_WARNING_MESSAGE =
      "The following files could not be renamed as they already exist:\n%s\n";
  public static final String COULD_NOT_UPDATE_THE_ATTACHMENT = "Could not update the attachment";
  public static final String ATTACHMENT_NOT_FOUND = "Attachment not found";
  public static final String GENERIC_ERROR = "Could not %s the document.";
  public static final String VERSIONED_REPO_ERROR =
      "Upload not supported for versioned repositories.";
  public static final String VIRUS_REPO_ERROR_MORE_THAN_400MB =
      "You cannot upload files that are larger than 400 MB";
  public static final String VIRUS_REPO_ERROR_MORE_THAN_400MB_MESSAGE = "SDM.VirusRepoErrorMessage";
  public static final String VIRUS_ERROR = "%s contains potential malware and cannot be uploaded.";
  public static final String VIRUS_ERROR_MESSAGE = "SDM.VirusErrorMessage";
  public static final String SDM_DUPLICATE_ATTACHMENT = "SDM.DuplicateAttachment";
  public static final String REPOSITORY_ERROR = "Failed to get repository info.";
  public static final String SDM_MISSING_ROLES_EXCEPTION_MSG =
      "You do not have the required permissions to update attachments. Kindly contact the admin";
  public static final String SDM_ROLES_ERROR_MESSAGE =
      "Unable to rename the file due to an error at the server";
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
  public static final String MIMETYPE_INVALID_ERROR =
      "This file type is not allowed in this repository. Contact your administrator for assistance.";
  public static final String USER_NOT_AUTHORISED_ERROR_LINK =
      "You do not have the required permissions to create links. Please contact your administrator for access.";
  public static final String USER_NOT_AUTHORISED_ERROR_OPEN_LINK =
      "You do not have the required permissions to open links. Please contact your administrator for access.";
  public static final String FILE_NOT_FOUND_ERROR = "Object not found in repository";
  public static final Integer MAX_CONNECTIONS = 100;
  public static final int CONNECTION_TIMEOUT = 1200;
  public static final int CHUNK_SIZE = 20 * 1024 * 1024; // 20MB Chunk Size
  public static final String ONBOARD_REPO_MESSAGE =
      "Repository with name %s  and id %s onboarded successfully";
  public static final String REPOSITORY_ALREADY_EXIST =
      "Repository with name %s and id %s already exists. Skipping onboarding.";
  public static final String ONBOARD_REPO_ERROR_MESSAGE =
      "Error in onboarding repository with name %s";
  public static final String UPDATE_ATTACHMENT_ERROR = "Could not update the attachment";
  public static final String ATTACHMENT_MAXCOUNT = "SDM.Attachments.maxCount";
  public static final String ATTACHMENT_MAXCOUNT_ERROR_MSG = "SDM.Attachments.maxCountError";
  public static final String MAX_COUNT_ERROR_MESSAGE =
      "Cannot upload more than %s attachments as set up by the application";
  public static final String FETCH_CHANGELOG_ERROR = "Could not fetch the changelog";
  public static final String DRAFT_READONLY_CONTEXT = "DRAFT_READONLY_CONTEXT";
  public static final Integer TIMEOUT_MILLISECONDS = 900000;
  public static final Integer MAX_CONNECTIONS_PER_ROUTE = 50;
  public static final Integer MAX_CONNECTIONS_TOTAL = 50;
  public static final String REST_V2_REPOSITORIES = "rest/v2/repositories";
  public static final String TECHNICAL_USER_FLOW = "TECHNICAL_CREDENTIALS_FLOW";
  public static final String NAMED_USER_FLOW = "TOKEN_EXCHANGE";
  public static final String ANNOTATION_IS_MEDIA_DATA = "_is_media_data";
  public static final String FAILED_TO_COPY_ATTACHMENT = "Failed to copy attachment";

  // Error messages for move operations
  public static final String SDM_MOVE_OPERATION_FAILED = "SDM move operation failed";
  public static final String VALIDATION_FAILED_PREFIX = "Validation failed: ";
  public static final String VALIDATION_FAILED_DEFAULT_MESSAGE =
      "Validation failed: Unable to process attachment properties or metadata";
  public static final String INVALID_SECONDARY_PROPERTIES_PREFIX =
      "Invalid secondary properties detected: ";
  public static final String INVALID_SECONDARY_PROPERTIES_SUFFIX =
      ". Attachment rolled back to source.";
  public static final String FAILED_TO_MOVE_ATTACHMENT = "Failed to move attachment";
  public static final String FAILED_TO_MOVE_ATTACHMENT_MSG = "SDM.Move.failedToMoveAttachmentError";
  public static final String MOVE_OPERATION_PARTIAL_FAILURE =
      "Move operation completed with some failures";
  public static final String FAILED_TO_FETCH_UP_ID = "Failed to fetch up_id";
  public static final String FAILED_TO_FETCH_FACET =
      "Invalid facet format, unable to extract required information.";
  public static final String PARENT_ENTITY_NOT_FOUND_ERROR = "Unable to find parent entity: %s";
  public static final String COMPOSITION_NOT_FOUND_ERROR =
      "Unable to find composition '%s' in entity: %s";
  public static final String TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR =
      "Unable to find target attachment entity: %s";

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
