package com.sap.cds.sdm.constants;

import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SDMErrorKeys {
  private SDMErrorKeys() {
    // Doesn't do anything
  }

  public static final String COULD_NOT_UPDATE_THE_ATTACHMENT_KEY =
      "SDM.couldNotUpdateTheAttachment";
  public static final String ATTACHMENT_NOT_FOUND_KEY = "SDM.attachmentNotFound";
  public static final String GENERIC_ERROR_KEY = "SDM.genericError";
  public static final String EVENT_UPLOAD_KEY = "SDM.eventUpload";
  public static final String VERSIONED_REPO_ERROR_KEY = "SDM.Repository.versionedRepoError";
  public static final String VIRUS_REPO_ERROR_MORE_THAN_400MB_KEY =
      "SDM.virusRepoErrorMoreThan400MB";
  public static final String VIRUS_ERROR_KEY = "SDM.virusError";
  public static final String REPOSITORY_ERROR_KEY = "SDM.repositoryError";
  public static final String SDM_MISSING_ROLES_EXCEPTION_KEY = "SDM.sdmMissingRolesException";
  public static final String SDM_SERVER_ERROR_KEY = "SDM.sdmServerError";
  public static final String ENTITY_PROCESSING_ERROR_LINK_KEY = "SDM.entityProcessingErrorLink";
  public static final String USER_NOT_AUTHORISED_ERROR_KEY = "SDM.userNotAuthorisedError";
  public static final String MIMETYPE_INVALID_ERROR_KEY = "SDM.mimetypeInvalidError";
  public static final String USER_NOT_AUTHORISED_ERROR_LINK_KEY = "SDM.userNotAuthorisedErrorLink";
  public static final String FILE_NOT_FOUND_ERROR_KEY = "SDM.fileNotFoundError";
  public static final String ONBOARD_REPO_MESSAGE_KEY = "SDM.onboardRepoMessage";
  public static final String REPOSITORY_ALREADY_EXIST_KEY = "SDM.repositoryAlreadyExist";
  public static final String ONBOARD_REPO_ERROR_MESSAGE_KEY = "SDM.onboardRepoErrorMessage";
  public static final String UPDATE_ATTACHMENT_ERROR_KEY = "SDM.updateAttachmentError";
  public static final String DRAFT_NOT_FOUND_KEY = "SDM.draftNotFound";
  public static final String UNSUPPORTED_PROPERTIES_KEY = "SDM.unsupportedProperties";
  public static final String FAILED_TO_COPY_ATTACHMENT_KEY = "SDM.failedToCopyAttachment";
  public static final String PARENT_ENTITY_NOT_FOUND_ERROR_KEY = "SDM.parentEntityNotFoundError";
  public static final String COMPOSITION_NOT_FOUND_ERROR_KEY = "SDM.compositionNotFoundError";
  public static final String TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR_KEY =
      "SDM.targetAttachmentEntityNotFoundError";
  public static final String INVALID_FACET_FORMAT_ERROR_KEY = "SDM.invalidFacetFormatError";
  public static final String FETCH_ATTACHMENT_COMPOSITION_ERROR_KEY =
      "SDM.fetchAttachmentCompositionError";
  public static final String FAILED_TO_EDIT_LINK_KEY = "SDM.failedToEditLink";
  public static final String ERROR_IN_SETTING_TIMEOUT_KEY = "SDM.errorInSettingTimeout";
  public static final String SDM_CREDENTIALS_MISSING_OR_INVALID_KEY =
      "SDM.sdmCredentialsMissingOrInvalid";
  public static final String FAILED_TO_RETRIEVE_SDM_CREDENTIALS_KEY =
      "SDM.failedToRetrieveSdmCredentials";
  public static final String FAILED_TO_CREATE_HTTP_CLIENT_KEY = "SDM.failedToCreateHttpClient";
  public static final String ERROR_WHILE_CREATING_HTTP_CLIENT_KEY =
      "SDM.errorWhileCreatingHttpClient";
  public static final String FAILED_TO_SET_REPOSITORY_DETAILS_KEY =
      "SDM.failedToSetRepositoryDetails";
  public static final String FAILED_TO_SERIALIZE_REPOSITORY_OBJECT_TO_JSON_KEY =
      "SDM.failedToSerializeRepositoryObjectToJson";
  public static final String FAILED_TO_CREATE_STRING_ENTITY_KEY = "SDM.failedToCreateStringEntity";
  public static final String CLIENT_CREDENTIALS_MISSING_OR_INVALID_KEY =
      "SDM.clientCredentialsMissingOrInvalid";
  public static final String FAILED_TO_CREATE_CLIENT_CREDENTIALS_KEY =
      "SDM.failedToCreateClientCredentials";
  public static final String FAILED_TO_REPLACE_SUBDOMAIN_IN_BASE_TOKEN_URL_KEY =
      "SDM.failedToReplaceSubdomainInBaseTokenUrl";
  public static final String ERROR_WHILE_FETCHING_REPOSITORY_ID_KEY =
      "SDM.errowWhileFetchingRepositoryId";
  public static final String UNEXPECTED_ERROR_WHILE_FETCHING_REPOSITORY_ID_KEY =
      "SDM.unexpectedErrorWhileFetchingRepositoryId";
  public static final String FAILED_TO_OFFBOARD_REPOSITORY_KEY = "SDM.failedToOffboardRepository";
  public static final String ERROR_WHILE_OFFBOARDING_REPOSITORY_KEY =
      "SDM.errorWhileOffboardingRepository";
  public static final String UNEXPECTED_ERROR_WHILE_OFFBOARDING_REPOSITORY_KEY =
      "SDM.unexpectedErrorWhileOffboardingRepository";
  public static final String FAILED_TO_PARSE_REPOSITORY_RESPONSE_KEY =
      "SDM.failedToParseRepositoryResponse";
  public static final String FAILED_TO_CREATE_FOLDER_KEY = "SDM.failedToCreateFolder";
  public static final String FILENAME_WHITESPACE_ERROR_MESSAGE_KEY =
      "SDM.filenameWhitespaceErrorMessage";
  public static final String SINGLE_RESTRICTED_CHARACTER_IN_FILE_KEY =
      "SDM.singleRestrictedCharacterInFile";
  public static final String SINGLE_DUPLICATE_FILENAME_KEY = "SDM.singleDuplicateFilename";
  public static final String VIRUS_DETECTED_FILE_ERROR_KEY = "SDM.virusDetectedFileError";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILE_ERROR_KEY =
      "SDM.virusScanInProgressFileError";
  public static final String VIRUS_DETECTED_FILES_PREFIX_KEY = "SDM.virusDetectedFilesPrefix";
  public static final String VIRUS_DETECTED_FILES_SUFFIX_KEY = "SDM.virusDetectedFilesSuffix";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILES_PREFIX_KEY =
      "SDM.virusScanInProgressFilesPrefix";
  public static final String VIRUS_SCAN_IN_PROGRESS_FILES_SUFFIX_KEY =
      "SDM.virusScanInProgressFilesSuffix";
  public static final String SCAN_FAILED_FILES_PREFIX_KEY = "SDM.scanFailedFilesPrefix";
  public static final String SCAN_FAILED_FILES_SUFFIX_KEY = "SDM.scanFailedFilesSuffix";
  public static final String RESTRICTED_CHARACTERS_IN_MULTIPLE_FILES_KEY =
      "SDM.restrictedCharactersInMultipleFiles";
  public static final String MULTIPLE_DUPLICATE_FILENAMES_PREFIX_KEY =
      "SDM.multipleDuplicateFilenamesPrefix";
  public static final String MULTIPLE_DUPLICATE_FILENAMES_SUFFIX_KEY =
      "SDM.multipleDuplicateFilenamesSuffix";
  public static final String FILE_NOT_FOUND_PREFIX_KEY = "SDM.fileNotFoundPrefix";
  public static final String FILE_NOT_FOUND_SUFFIX_KEY = "SDM.fileNotFoundSuffix";
  public static final String BAD_REQUEST_PREFIX_KEY = "SDM.badRequestPrefix";
  public static final String BAD_REQUEST_SUFFIX_KEY = "SDM.badRequestSuffix";
  public static final String EVENT_CREATE_KEY = "SDM.eventCreate";
  public static final String EVENT_UPDATE_KEY = "SDM.eventUpdate";
  public static final String NO_SDM_ROLES_PREFIX_KEY = "SDM.noSdmRolesPrefix";
  public static final String CONTEXT_INFO_TABLE = "SDM.contextInfoTable";
  public static final String CONTEXT_INFO_PAGE = "SDM.contextInfoPage";
  public static final String UNSUPPORTED_PROPERTIES_PREFIX_KEY = "SDM.unsupportedPropertiesPrefix";
  public static final String UNSUPPORTED_PROPERTIES_SUFFIX_KEY = "SDM.unsupportedPropertiesSuffix";
  public static final String MAX_COUNT_ERROR_MESSAGE_KEY = "SDM.maxCountErrorMessage";
  public static final String FETCH_CHANGELOG_ERROR_KEY = "SDM.fetchChangelogError";
  public static final String FAILED_TO_MOVE_ATTACHMENT_KEY = "SDM.failedToMoveAttachment";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_MOVE_PREFIX_KEY =
      "SDM.invalidSecondaryPropertiesForMovePrefix";
  public static final String INVALID_SECONDARY_PROPERTIES_FOR_MOVE_SUFFIX_KEY =
      "SDM.invalidSecondaryPropertiesForMoveSuffix";
  public static final String SDM_MOVE_OPERATION_FAILED_KEY = "SDM.sdmMoveOperationFailed";
  public static final String FAILED_TO_ACCESS_ERROR_KEY_FIELDS_KEY =
      "SDM.failedToAccessErrorKeyFields";
  public static final String FAILED_TO_ACCESS_ERROR_MESSAGES_FIELDS_KEY =
      "SDM.failedToAccessErrorMessagesFields";

  public static Map<String, Object> getAllErrorKeys() {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Field f : SDMErrorKeys.class.getDeclaredFields()) {
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
