package com.sap.cds.sdm.constants;

import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SDMErrorMessages {
  private SDMErrorMessages() {
    // Doesn't do anything
  }

  public static final String COULD_NOT_UPDATE_THE_ATTACHMENT = "Could not update the attachment";
  public static final String ATTACHMENT_NOT_FOUND = "Attachment not found";
  public static final String COULD_NOT_UPLOAD_DOCUMENT = "Could not upload the document.";
  public static final String COULD_NOT_DELETE_DOCUMENT = "Could not delete the document.";
  public static final String VERSIONED_REPO_ERROR =
      "Upload not supported for versioned repositories.";
  public static final String VIRUS_REPO_ERROR_MORE_THAN_400MB =
      "You cannot upload files that are larger than 400 MB";
  public static final String VIRUS_ERROR = "%s contains potential malware and cannot be uploaded.";
  public static final String REPOSITORY_ERROR = "Failed to get repository info.";
  public static final String SDM_MISSING_ROLES_EXCEPTION =
      "You do not have the required permissions to update attachments. Kindly contact the admin";
  public static final String SDM_SERVER_ERROR =
      "Unable to rename the file due to an error at the server";
  public static final String ENTITY_PROCESSING_ERROR_LINK =
      "Failed to create link due to error while processing entity";
  public static final String USER_NOT_AUTHORISED_ERROR =
      "You do not have the required permissions to upload attachments. Please contact your administrator for access.";
  public static final String MIMETYPE_INVALID_ERROR =
      "This file type is not allowed in this repository. Contact your administrator for assistance.";
  public static final String USER_NOT_AUTHORISED_ERROR_LINK =
      "You do not have the required permissions to create links. Please contact your administrator for access.";
  public static final String FILE_NOT_FOUND_ERROR = "Object not found in repository";
  public static final String ONBOARD_REPO_MESSAGE =
      "Repository with name %s  and id %s onboarded successfully";
  public static final String REPOSITORY_ALREADY_EXIST =
      "Repository with name %s and id %s already exists. Skipping onboarding.";
  public static final String ONBOARD_REPO_ERROR_MESSAGE =
      "Error in onboarding repository with name %s";
  public static final String UPDATE_ATTACHMENT_ERROR = "Could not update the attachment";
  public static final String DRAFT_NOT_FOUND = "Attachment draft entity not found";
  public static final String UNSUPPORTED_PROPERTIES = "Unsupported properties";
  public static final String FAILED_TO_COPY_ATTACHMENT = "Failed to copy attachment";
  public static final String PARENT_ENTITY_NOT_FOUND_ERROR = "Unable to find parent entity: %s";
  public static final String COMPOSITION_NOT_FOUND_ERROR =
      "Unable to find composition '%s' in entity: %s";
  public static final String TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR =
      "Unable to find target attachment entity: %s";
  public static final String INVALID_FACET_FORMAT_ERROR =
      "Invalid facet format. Expected: Service.Entity.Composition, got: %s";
  public static final String FETCH_ATTACHMENT_COMPOSITION_ERROR =
      "Failed to fetch attachment composition";
  public static final String FAILED_TO_EDIT_LINK = "Failed to edit link";
  public static final String ERROR_IN_SETTING_TIMEOUT = "Error in setting timeout";
  public static final String SDM_CREDENTIALS_MISSING_OR_INVALID =
      "SDM credentials are missing or invalid.";
  public static final String FAILED_TO_RETRIEVE_SDM_CREDENTIALS =
      "Failed to retrieve SDM credentials.";
  public static final String FAILED_TO_CREATE_HTTP_CLIENT = "Failed to create HTTP client.";
  public static final String ERROR_WHILE_CREATING_HTTP_CLIENT = "Error while creating HTTP client.";
  public static final String FAILED_TO_SET_REPOSITORY_DETAILS = "Failed to set repository details.";
  public static final String FAILED_TO_SERIALIZE_REPOSITORY_OBJECT_TO_JSON =
      "Failed to serialize repository object to JSON.";
  public static final String FAILED_TO_CREATE_STRING_ENTITY = "Failed to create StringEntity.";
  public static final String CLIENT_CREDENTIALS_MISSING_OR_INVALID =
      "Client credentials are missing or invalid.";
  public static final String FAILED_TO_CREATE_CLIENT_CREDENTIALS =
      "Failed to create client credentials.";
  public static final String FAILED_TO_REPLACE_SUBDOMAIN_IN_BASE_TOKEN_URL =
      "Failed to replace subdomain in base token URL.";
  public static final String ERROR_WHILE_FETCHING_REPOSITORY_ID =
      "Error while fetching repository ID.";
  public static final String UNEXPECTED_ERROR_WHILE_FETCHING_REPOSITORY_ID =
      "Unexpected error while fetching repository ID.";
  public static final String FAILED_TO_OFFBOARD_REPOSITORY = "Failed to offboard repository.";
  public static final String ERROR_WHILE_OFFBOARDING_REPOSITORY =
      "Error while offboarding repository.";
  public static final String UNEXPECTED_ERROR_WHILE_OFFBOARDING_REPOSITORY =
      "Unexpected error while offboarding repository.";
  public static final String FAILED_TO_PARSE_REPOSITORY_RESPONSE =
      "Failed to parse repository response";
  public static final String FAILED_TO_CREATE_FOLDER = "Failed to create folder";
  public static final String FILENAME_WHITESPACE_ERROR_MESSAGE =
      "The object name cannot be empty or consist entirely of space characters. Enter a value.";
  public static final String SINGLE_RESTRICTED_CHARACTER_IN_FILE =
      "\"%s\" contains unsupported characters (‘/’ or ‘\\’). Rename and try again.";
  public static final String SINGLE_DUPLICATE_FILENAME =
      "An object named \"%s\" already exists. Rename the object and try again.";
  public static final String VIRUS_DETECTED_FILE_ERROR =
      "Virus detected. Remove the file and upload a clean version.";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILE_ERROR =
      "Scan in progress. Wait until the scan is complete before opening the file.";
  public static final String VIRUS_DETECTED_FILES_PREFIX =
      "We detected a virus, for the following files: \n\n";
  public static final String VIRUS_DETECTED_FILES_SUFFIX =
      "You can't save your changes because some files are unsafe. Delete the unsafe files manually before continuing. You can use a filter to help you find the affected files.";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILES_PREFIX =
      "The virus scanning is in progress for the following files: \n\n";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILES_SUFFIX =
      "Refresh the page to see scanning is completed.";
  public static final String SCAN_FAILED_FILES_PREFIX =
      "The virus scan failed, for the following files: \n\n";
  public static final String SCAN_FAILED_FILES_SUFFIX =
      "You can't save your changes because some files not scanned. Delete the unscanned files manually before continuing.";
  public static final String UPLOAD_IN_PROGRESS_FILES_PREFIX =
      "The upload is in progress for the following files: \n\n";
  public static final String UPLOAD_IN_PROGRESS_FILES_SUFFIX =
      "You can't save your changes until the upload completes. Refresh the page to check if the upload is complete.";
  public static final String RESTRICTED_CHARACTERS_IN_MULTIPLE_FILES =
      "The following names contain unsupported characters (‘/’ or ‘\\’). Rename and try again:\n\n";
  public static final String MULTIPLE_DUPLICATE_FILENAMES_PREFIX =
      "Objects with the following names already exist:\n\n";
  public static final String MULTIPLE_DUPLICATE_FILENAMES_SUFFIX =
      "Rename the objects and try again";
  public static final String FILE_NOT_FOUND_PREFIX =
      "Update unsuccessful. The following filename(s) could not be updated as they do not exist. \n\n";
  public static final String FILE_NOT_FOUND_SUFFIX = "\nDelete and upload the files again.";
  public static final String BAD_REQUEST_PREFIX = "Could not update the following files. \n\n";
  public static final String BAD_REQUEST_SUFFIX = "\nPlease try again.";
  public static final String EVENT_CREATE = "create";
  public static final String EVENT_UPDATE = "update";
  public static final String NO_SDM_ROLES_PREFIX = "Could not %s the following files. \n\n";
  public static final String CONTEXT_INFO_TABLE = "\n\nTable: %s";
  public static final String CONTEXT_INFO_PAGE = "\nPage: %s";
  public static final String UNSUPPORTED_PROPERTIES_PREFIX =
      "The following secondary properties are not supported.\n\n";
  public static final String UNSUPPORTED_PROPERTIES_SUFFIX =
      "\nPlease contact your administrator for assistance with any necessary adjustments.";
  public static final String MAX_COUNT_ERROR_MESSAGE = "Cannot upload more than %s attachments.";
  public static final String FETCH_CHANGELOG_ERROR = "Could not fetch the changelog";
  public static final String FAILED_TO_MOVE_ATTACHMENT = "Failed to move attachment";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_MOVE_PREFIX =
      "Invalid secondary properties detected: ";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_MOVE_SUFFIX =
      ". Attachment rolled back to source.";
  public static final String SDM_MOVE_OPERATION_FAILED = "SDM move operation failed";
  public static final String FAILED_TO_COPY_ATTACHMENTS_PREFIX =
      "Failed to copy the following attachments:\n";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_COPY_PREFIX =
      "Invalid secondary properties detected: ";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_COPY_SUFFIX =
      ". Attachment not copied.";
  public static final String FAILED_TO_ACCESS_ERROR_KEY_FIELDS =
      "Failed to access SDM error key fields";
  public static final String FAILED_TO_ACCESS_ERROR_MESSAGES_FIELDS =
      "Failed to access SDM error messages fields";
  public static final String FILE_EXTENSION_CHANGE_NOT_ALLOWED =
      "Changing the file extension is not allowed. The file \"%s\" must retain its original extension \"%s\".";

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
      return String.format(
          SDMUtils.getErrorMessage("SINGLE_RESTRICTED_CHARACTER_IN_FILE"),
          invalidFileNames.iterator().next());
    }
    StringBuilder prefix = new StringBuilder();
    prefix.append(SDMUtils.getErrorMessage("RESTRICTED_CHARACTERS_IN_MULTIPLE_FILES"));
    return buildErrorMessage(invalidFileNames, prefix, null);
  }

  // Duplicate file names error message
  public static String duplicateFilenameFormat(Collection<String> duplicateFileNames) {
    // if only 1 duplicate file, so different error will throw
    if (duplicateFileNames.size() == 1) {
      return String.format(
          SDMUtils.getErrorMessage("SINGLE_DUPLICATE_FILENAME"),
          duplicateFileNames.iterator().next());
    }
    StringBuilder prefix = new StringBuilder();
    prefix.append(SDMUtils.getErrorMessage("MULTIPLE_DUPLICATE_FILENAMES_PREFIX"));
    String closingRemark = SDMUtils.getErrorMessage("MULTIPLE_DUPLICATE_FILENAMES_SUFFIX");
    return buildErrorMessage(duplicateFileNames, prefix, closingRemark);
  }

  public static String fileNotFound(List<String> fileNameNotFound) {
    // Create the base message
    String prefixMessage = SDMUtils.getErrorMessage("FILE_NOT_FOUND_PREFIX");

    // Create the formatted prefix message
    String formattedPrefixMessage = String.format(prefixMessage);

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(formattedPrefixMessage);

    // Append each unsupported file name to the StringBuilder
    for (String file : fileNameNotFound) {
      bulletPoints.append(String.format("\t• %s%n", file));
    }
    bulletPoints.append(SDMUtils.getErrorMessage("FILE_NOT_FOUND_SUFFIX"));
    return bulletPoints.toString();
  }

  public static String badRequestMessage(Map<String, String> badRequest) {
    // Create the base message
    String prefixMessage = SDMUtils.getErrorMessage("BAD_REQUEST_PREFIX");

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(prefixMessage);

    // Append each file name and its error message to the StringBuilder
    for (Map.Entry<String, String> entry : badRequest.entrySet()) {
      bulletPoints.append(String.format("\t• %s : %s%n", entry.getKey(), entry.getValue()));
    }
    bulletPoints.append(SDMUtils.getErrorMessage("BAD_REQUEST_SUFFIX"));
    return bulletPoints.toString();
  }

  public static String noSDMRolesMessage(List<String> files, String operation) {
    // Create the base message
    String prefixMessage =
        String.format(SDMUtils.getErrorMessage("NO_SDM_ROLES_PREFIX"), operation);

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(prefixMessage);

    // Append each file name and its error message to the StringBuilder
    for (String item : files) {
      bulletPoints.append(String.format("\t• %s%n", item));
    }
    bulletPoints.append(System.lineSeparator());
    if (operation.equals(SDMUtils.getErrorMessage("EVENT_CREATE"))) {
      bulletPoints.append(SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR"));
    } else {
      bulletPoints.append(SDMUtils.getErrorMessage("SDM_MISSING_ROLES_EXCEPTION"));
    }

    return bulletPoints.toString();
  }

  public static String unsupportedPropertiesMessage(List<String> propertiesList) {
    // Create the base message
    String prefixMessage = SDMUtils.getErrorMessage("UNSUPPORTED_PROPERTIES_PREFIX");

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(prefixMessage);

    // Append each unsupported file name to the StringBuilder
    for (String file : propertiesList) {
      bulletPoints.append(String.format("\t• %s%n", file));
    }
    bulletPoints.append(SDMUtils.getErrorMessage("UNSUPPORTED_PROPERTIES_SUFFIX"));
    return bulletPoints.toString();
  }

  public static String getDuplicateFilesError(String filename) {
    Set<String> filenames = new HashSet<>();
    filenames.add(filename);
    return duplicateFilenameFormat(filenames);
  }

  public static String getCouldNotUploadDocument() {
    return SDMUtils.getErrorMessage("COULD_NOT_UPLOAD_DOCUMENT");
  }

  public static String getCouldNotDeleteDocument() {
    return SDMUtils.getErrorMessage("COULD_NOT_DELETE_DOCUMENT");
  }

  public static String getVirusFilesError(String filename) {
    return String.format(SDMUtils.getErrorMessage("VIRUS_ERROR"), filename);
  }

  public static String virusDetectedFilesMessage(List<String> files) {
    StringBuilder prefix = new StringBuilder();
    prefix.append(SDMUtils.getErrorMessage("VIRUS_DETECTED_FILES_PREFIX"));
    String closingRemark = SDMUtils.getErrorMessage("VIRUS_DETECTED_FILES_SUFFIX");
    return buildErrorMessage(files, prefix, closingRemark);
  }

  public static String scanFailedFilesMessage(List<String> files) {
    StringBuilder prefix = new StringBuilder();
    prefix.append(SDMUtils.getErrorMessage("SCAN_FAILED_FILES_PREFIX"));
    String closingRemark = SDMUtils.getErrorMessage("SCAN_FAILED_FILES_SUFFIX");
    return buildErrorMessage(files, prefix, closingRemark);
  }

  public static String virusScanInProgressFilesMessage(List<String> files) {
    StringBuilder prefix = new StringBuilder();
    prefix.append(SDMUtils.getErrorMessage("VIRUS_SCAN_IN_PROGRESS_FILES_PREFIX"));
    String closingRemark = SDMUtils.getErrorMessage("VIRUS_SCAN_IN_PROGRESS_FILES_SUFFIX");
    return buildErrorMessage(files, prefix, closingRemark);
  }

  public static String uploadInProgressFilesMessage(List<String> files) {
    StringBuilder prefix = new StringBuilder();
    prefix.append(SDMUtils.getErrorMessage("UPLOAD_IN_PROGRESS_FILES_PREFIX"));
    String closingRemark = SDMUtils.getErrorMessage("UPLOAD_IN_PROGRESS_FILES_SUFFIX");
    return buildErrorMessage(files, prefix, closingRemark);
  }

  public static Map<String, Object> getAllErrorMessages() {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Field f : SDMErrorMessages.class.getDeclaredFields()) {
      int m = f.getModifiers();
      if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m)) {
        try {
          out.put(f.getName(), f.get(null));
        } catch (IllegalAccessException ignored) {
          throw new ServiceException(
              SDMUtils.getErrorMessage("FAILED_TO_ACCESS_ERROR_MESSAGES_FIELDS"), ignored);
        }
      }
    }
    return Collections.unmodifiableMap(out);
  }
}
