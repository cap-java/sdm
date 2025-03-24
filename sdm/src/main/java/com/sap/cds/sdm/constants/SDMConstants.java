package com.sap.cds.sdm.constants;

import java.util.List;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = System.getenv("REPOSITORY_ID");
  public static final String SDM_ANNOTATION = "@SDM.Attachments.AdditionalProperty";
  public static final String DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE =
      "The file(s) %s have been added multiple times. Please rename and try again.";
  public static final String FILES_RENAME_WARNING_MESSAGE =
      "The following files could not be renamed as they already exist:\n%s\n";
  public static final String COULD_NOT_RENAME_THE_ATTACHMENT = "Could not rename the attachment";
  public static final String ATTACHMENT_NOT_FOUND = "Attachment not found";
  public static final String DUPLICATE_FILES_ERROR = "%s already exists.";
  public static final String GENERIC_ERROR = "Could not %s the document.";
  public static final String VERSIONED_REPO_ERROR =
      "Upload not supported for versioned repositories.";
  public static final String VIRUS_ERROR = "%s contains potential malware and cannot be uploaded.";
  public static final String REPOSITORY_ERROR = "Failed to get repository info.";
  public static final String NOT_FOUND_ERROR = "Failed to read document.";
  public static final String NAME_CONSTRAINT_WARNING_MESSAGE =
      "Enter a valid file name for %s. The following characters are not supported: /, \\";
  public static final String SDM_MISSING_ROLES_EXCEPTION_MSG =
      "You do not have the required permissions to rename attachments. Kindly contact the admin";
  public static final String SDM_ROLES_ERROR_MESSAGE =
      "Unable to rename the file due to an error at the server";
  public static final String SDM_ENV_NAME = "sdm";

  public static final String SDM_TOKEN_EXCHANGE_DESTINATION = "sdm-token-exchange-flow";
  public static final String SDM_TECHNICAL_CREDENTIALS_FLOW_DESTINATION = "sdm-technical-user-flow";
  public static final String SDM_CONNECTIONPOOL_PREFIX = "cds.attachments.sdm.http.%s";
  public static final String USER_NOT_AUTHORISED_ERROR =
      "You do not have the required permissions to upload attachments. Please contact your administrator for access.";
  public static final String FILE_NOT_FOUND_ERROR = "Object not found in repository";
  public static final Integer MAX_CONNECTIONS = 100;
  public static final int CONNECTION_TIMEOUT = 1200;
  public static final String ONBOARD_REPO_MESSAGE =
      "Repository with name %s  and id %s onboarded successfully";
  public static final String ONBOARD_REPO__ERROR_MESSAGE =
      "Error in onboarding repository with name %s";

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
