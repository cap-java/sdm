package com.sap.cds.sdm.constants;

import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** UI-Facing Error Keys for Localization. */
public final class SDMUIErrorKeys {
  private SDMUIErrorKeys() {}

  // Document Operations
  public static final String COULD_NOT_UPLOAD_DOCUMENT_KEY = "SDM.couldNotUploadDocument";
  public static final String COULD_NOT_DELETE_DOCUMENT_KEY = "SDM.couldNotDeleteDocument";
  public static final String COULD_NOT_UPDATE_THE_ATTACHMENT_KEY =
      "SDM.couldNotUpdateTheAttachment";
  public static final String ATTACHMENT_NOT_FOUND_KEY = "SDM.attachmentNotFound";
  public static final String UPDATE_ATTACHMENT_ERROR_KEY = "SDM.updateAttachmentError";
  public static final String FAILED_TO_COPY_ATTACHMENT_KEY = "SDM.failedToCopyAttachment";
  public static final String FAILED_TO_MOVE_ATTACHMENT_KEY = "SDM.failedToMoveAttachment";
  public static final String SDM_MOVE_OPERATION_FAILED_KEY = "SDM.sdmMoveOperationFailed";
  public static final String FETCH_CHANGELOG_ERROR_KEY = "SDM.fetchChangelogError";

  // Repository Errors
  public static final String VERSIONED_REPO_ERROR_KEY = "SDM.Repository.versionedRepoError";
  public static final String VIRUS_REPO_ERROR_MORE_THAN_400MB_KEY =
      "SDM.virusRepoErrorMoreThan400MB";
  public static final String REPOSITORY_ERROR_KEY = "SDM.repositoryError";
  public static final String FILE_NOT_FOUND_ERROR_KEY = "SDM.fileNotFoundError";

  // Authorization Errors
  public static final String SDM_MISSING_ROLES_EXCEPTION_KEY = "SDM.sdmMissingRolesException";
  public static final String USER_NOT_AUTHORISED_ERROR_KEY = "SDM.userNotAuthorisedError";
  public static final String USER_NOT_AUTHORISED_ERROR_LINK_KEY = "SDM.userNotAuthorisedErrorLink";
  public static final String MIMETYPE_INVALID_ERROR_KEY = "SDM.mimetypeInvalidError";

  // Virus Scanning Errors
  public static final String VIRUS_ERROR_KEY = "SDM.virusError";
  public static final String VIRUS_DETECTED_FILE_ERROR_KEY = "SDM.virusDetectedFileError";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILE_ERROR_KEY =
      "SDM.virusScanInProgressFileError";
  public static final String UPLOAD_IN_PROGRESS_FILE_ERROR_KEY = "SDM.uploadInProgressFileError";
  public static final String VIRUS_DETECTED_FILES_PREFIX_KEY = "SDM.virusDetectedFilesPrefix";
  public static final String VIRUS_DETECTED_FILES_SUFFIX_KEY = "SDM.virusDetectedFilesSuffix";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILES_PREFIX_KEY =
      "SDM.virusScanInProgressFilesPrefix";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILES_SUFFIX_KEY =
      "SDM.virusScanInProgressFilesSuffix";
  public static final String SCAN_FAILED_FILES_PREFIX_KEY = "SDM.scanFailedFilesPrefix";
  public static final String SCAN_FAILED_FILES_SUFFIX_KEY = "SDM.scanFailedFilesSuffix";
  public static final String UPLOAD_IN_PROGRESS_FILES_PREFIX_KEY =
      "SDM.uploadInProgressFilesPrefix";
  public static final String UPLOAD_IN_PROGRESS_FILES_SUFFIX_KEY =
      "SDM.uploadInProgressFilesSuffix";

  // File Validation Errors
  public static final String FILENAME_WHITESPACE_ERROR_MESSAGE_KEY =
      "SDM.filenameWhitespaceErrorMessage";
  public static final String SINGLE_RESTRICTED_CHARACTER_IN_FILE_KEY =
      "SDM.singleRestrictedCharacterInFile";
  public static final String RESTRICTED_CHARACTERS_IN_MULTIPLE_FILES_KEY =
      "SDM.restrictedCharactersInMultipleFiles";
  public static final String SINGLE_DUPLICATE_FILENAME_KEY = "SDM.singleDuplicateFilename";
  public static final String MULTIPLE_DUPLICATE_FILENAMES_PREFIX_KEY =
      "SDM.multipleDuplicateFilenamesPrefix";
  public static final String MULTIPLE_DUPLICATE_FILENAMES_SUFFIX_KEY =
      "SDM.multipleDuplicateFilenamesSuffix";
  public static final String FILE_EXTENSION_CHANGE_NOT_ALLOWED_KEY =
      "SDM.fileExtensionChangeNotAllowed";

  // Update Operation Errors
  public static final String FILE_NOT_FOUND_PREFIX_KEY = "SDM.fileNotFoundPrefix";
  public static final String FILE_NOT_FOUND_SUFFIX_KEY = "SDM.fileNotFoundSuffix";
  public static final String BAD_REQUEST_PREFIX_KEY = "SDM.badRequestPrefix";
  public static final String BAD_REQUEST_SUFFIX_KEY = "SDM.badRequestSuffix";
  public static final String NO_SDM_ROLES_PREFIX_KEY = "SDM.noSdmRolesPrefix";

  // Server/Other Errors
  public static final String SDM_SERVER_ERROR_KEY = "SDM.sdmServerError";
  public static final String UNSUPPORTED_PROPERTIES_KEY = "SDM.unsupportedProperties";
  public static final String UNSUPPORTED_PROPERTIES_PREFIX_KEY = "SDM.unsupportedPropertiesPrefix";
  public static final String UNSUPPORTED_PROPERTIES_SUFFIX_KEY = "SDM.unsupportedPropertiesSuffix";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_MOVE_PREFIX_KEY =
      "SDM.invalidSecondaryPropertiesForMovePrefix";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_MOVE_SUFFIX_KEY =
      "SDM.invalidSecondaryPropertiesForMoveSuffix";
  public static final String FAILED_TO_COPY_ATTACHMENTS_PREFIX_KEY =
      "SDM.failedToCopyAttachmentsPrefix";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_COPY_PREFIX_KEY =
      "SDM.invalidSecondaryPropertiesForCopyPrefix";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_COPY_SUFFIX_KEY =
      "SDM.invalidSecondaryPropertiesForCopySuffix";
  public static final String MAX_COUNT_ERROR_MESSAGE_KEY = "SDM.maxCountErrorMessage";

  public static Map<String, Object> getAllUIErrorKeys() {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Field f : SDMUIErrorKeys.class.getDeclaredFields()) {
      int m = f.getModifiers();
      if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m)) {
        try {
          out.put(f.getName(), f.get(null));
        } catch (IllegalAccessException ignored) {
          throw new ServiceException(
              SDMUtils.getErrorMessage("FAILED_TO_ACCESS_ERROR_KEY_FIELDS"), ignored);
        }
      }
    }
    return Collections.unmodifiableMap(out);
  }
}
