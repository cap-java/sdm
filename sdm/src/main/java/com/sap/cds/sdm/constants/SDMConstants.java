package com.sap.cds.sdm.constants;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = System.getenv("REPOSITORY_ID");
  public static final String BEARER_TOKEN = "Bearer ";
  public static final int TIMEOUT = 900;

  public static final String TENANT_ID = "X-zid";
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
