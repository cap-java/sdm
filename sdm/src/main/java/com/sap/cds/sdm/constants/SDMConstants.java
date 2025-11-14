package com.sap.cds.sdm.constants;

import java.util.List;
import java.util.Map;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = "RISHI-WORKFLOW-REPO";
  public static final String SYSTEM_USER = "system-internal";
  public static final String DESTINATION_EXCEPTION =
      "Unable to get the destination for sdm service binding";

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
  public static final String DUPLICATE_FILES_ERROR = "%s already exists.";
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

  // Localized error message keys
  public static final String VERSIONED_REPO_ERROR_MSG = "SDM.Repository.versionedRepoError";
  public static final String USER_NOT_AUTHORISED_ERROR_MSG =
      "SDM.Authorization.userNotAuthorizedError";
  public static final String USER_NOT_AUTHORISED_ERROR_LINK_MSG =
      "SDM.Authorization.userNotAuthorizedLinkError";
  public static final String FAILED_TO_EDIT_LINK_MSG = "SDM.Link.failedToEditLinkError";
  public static final String REPOSITORY_ERROR_MSG = "SDM.Repository.repositoryError";
  public static final String FILE_NOT_FOUND_ERROR_MSG = "SDM.File.fileNotFoundError";
  public static final String MIMETYPE_INVALID_ERROR_MSG = "SDM.File.mimetypeInvalidError";
  public static final String FAILED_TO_FETCH_FACET_MSG = "SDM.Facet.failedToFetchFacetError";
  public static final String NO_SDM_BINDING = "No SDM binding found";
  public static final String DI_TOKEN_EXCHANGE_ERROR = "Error fetching DI token with JWT bearer";
  public static final String DI_TOKEN_EXCHANGE_PARAMS =
      "/oauth/token?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer";
  public static final String DRAFT_NOT_FOUND = "Attachment draft entity not found";
  public static final String UNSUPPORTED_PROPERTIES = "Unsupported properties";
  public static final String REPOSITORY_VERSIONED = "Versioned";
  public static final Integer TIMEOUT_MILLISECONDS = 900000;
  public static final Integer MAX_CONNECTIONS_PER_ROUTE = 50;
  public static final Integer MAX_CONNECTIONS_TOTAL = 50;
  public static final String REST_V2_REPOSITORIES = "rest/v2/repositories";
  public static final String TECHNICAL_USER_FLOW = "TECHNICAL_CREDENTIALS_FLOW";
  public static final String NAMED_USER_FLOW = "TOKEN_EXCHANGE";
  public static final String ANNOTATION_IS_MEDIA_DATA = "_is_media_data";
  public static final String DRAFT_READONLY_CONTEXT = "DRAFT_READONLY_CONTEXT";
  public static final String FAILED_TO_COPY_ATTACHMENT = "Failed to copy attachment";
  public static final String FAILED_TO_FETCH_UP_ID = "Failed to fetch up_id";
  public static final String FAILED_TO_FETCH_FACET =
      "Invalid facet format, unable to extract required information.";
  public static final String PARENT_ENTITY_NOT_FOUND_ERROR = "Unable to find parent entity: %s";
  public static final String COMPOSITION_NOT_FOUND_ERROR =
      "Unable to find composition '%s' in entity: %s";
  public static final String TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR =
      "Unable to find target attachment entity: %s";
  public static final String INVALID_FACET_FORMAT_ERROR =
      "Invalid facet format. Expected: Service.Entity.Composition, got: %s";
  public static final String FETCH_ATTACHMENT_COMPOSITION_ERROR =
      "Failed to fetch attachment composition";

  // Error messages for ServiceException
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
  public static final String ERROR_IN_SETTING_TIMEOUT_MESSAGE = "Error in setting timeout";
  public static final String FAILED_TO_CREATE_FOLDER = "Failed to create folder";

  public static String nameConstraintMessage(
      List<String> fileNameWithRestrictedCharacters, String operation) {
    // Create the base message
    String prefixMessage =
        "%s unsuccessful. The following filename(s) contain unsupported characters (/, \\). \n\n";

    // Create the formatted prefix message
    String formattedPrefixMessage = String.format(prefixMessage, operation);

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(formattedPrefixMessage);

    // Append each unsupported file name to the StringBuilder
    for (String file : fileNameWithRestrictedCharacters) {
      bulletPoints.append(String.format("\t• %s%n", file));
    }
    bulletPoints.append("\nRename the files and try again.");
    return bulletPoints.toString();
  }

  public static String linkNameConstraintMessage(
      List<String> fileNameWithRestrictedCharacters, String operation) {
    // Create the base message
    String prefixMessage =
        "Link could not be %s. The following name(s) contain unsupported characters (/, \\). \n\n";

    // Create the formatted prefix message
    String formattedPrefixMessage = String.format(prefixMessage, operation);

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(formattedPrefixMessage);

    // Append each unsupported file name to the StringBuilder
    for (String file : fileNameWithRestrictedCharacters) {
      bulletPoints.append(String.format("\t• %s%n", file));
    }
    bulletPoints.append("\nRename the link and try again.");
    return bulletPoints.toString();
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

  public static String badRequestMessage(Map<String, String> badRequest) {
    // Create the base message
    String prefixMessage = "Could not update the following files. \n\n";

    // Initialize the StringBuilder with the formatted message prefix
    StringBuilder bulletPoints = new StringBuilder(prefixMessage);

    // Append each file name and its error message to the StringBuilder
    for (Map.Entry<String, String> entry : badRequest.entrySet()) {
      bulletPoints.append(String.format("\t• %s : %s%n", entry.getKey(), entry.getValue()));
    }
    bulletPoints.append("\nPlease try again.");
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

  public static String getDuplicateFilesError(String filename) {
    return String.format(DUPLICATE_FILES_ERROR, filename);
  }

  public static String getGenericError(String event) {
    return String.format(GENERIC_ERROR, event);
  }

  public static String getVirusFilesError(String filename) {
    return String.format(VIRUS_ERROR, filename);
  }
}
