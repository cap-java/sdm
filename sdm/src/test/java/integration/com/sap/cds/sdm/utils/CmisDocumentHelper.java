package integration.com.sap.cds.sdm.utils;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import integration.com.sap.cds.sdm.Credentials;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class CmisDocumentHelper {

  private static final String CREATE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/create.sh";
  private static final String CREATE_FOLDER_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/create-folder.sh";
  private static final String GET_OBJECT_ID_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/get-object-id.sh";
  private static final String DELETE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/delete.sh";
  private static final String READ_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/read.sh";
  private static final String GET_METADATA_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/get-metadata.sh";

  private static Map<String, String> getCmisEnv() {
    String tenancyModel = System.getProperty("tenancyModel");
    if ("multi".equals(tenancyModel)) {
      Properties props = Credentials.getCredentials();
      String repoId = props.getProperty("defaultRepositoryIDMT");
      String tenant = System.getProperty("tenant");
      String suffix = "TENANT1".equals(tenant) ? "1" : "2";
      String authUrl = props.getProperty("authUrlMT" + suffix);
      Map<String, String> env = new HashMap<>();
      if (repoId != null && !repoId.isEmpty()) {
        env.put("SDM_REPOSITORY_ID", repoId);
      }
      if (authUrl != null && !authUrl.isEmpty()) {
        env.put("SDM_AUTH_URL", authUrl);
      }
      return env.isEmpty() ? null : env;
    }
    return null;
  }

  public static Map<String, String> getCmisEnvPublic() {
    return getCmisEnv();
  }

  /**
   * Creates a folder in the CMIS repository root via create-folder.sh and returns its object ID.
   *
   * @param folderName the name of the folder to create
   * @return the CMIS object ID of the created folder
   */
  public static String createFolderInCmis(String folderName) {
    try {
      Map<String, String> env = getCmisEnv();
      String output = ShellScriptRunner.runAndCaptureOutput(env, CREATE_FOLDER_SCRIPT, folderName);
      if (output != null && output.contains("Object ID:")) {
        return output.substring(output.lastIndexOf("Object ID:") + 11).trim();
      }
      fail("create-folder.sh did not return an Object ID. Output: " + output);
      return null;
    } catch (Exception e) {
      fail("Failed to create folder in CMIS: " + e.getMessage());
      return null;
    }
  }

  /**
   * Uploads a local file to a specific CMIS folder (by folder object ID) via create.sh and returns
   * the created document's object ID.
   *
   * @param cmisName the name the document will have in the CMIS repository
   * @param filePath path to the local file to upload
   * @param parentFolderObjectId the CMIS object ID of the parent folder
   * @return the CMIS object ID of the created document
   */
  public static String createDocumentInFolder(
      String cmisName, String filePath, String parentFolderObjectId) {
    try {
      Map<String, String> env = getCmisEnv();
      String output =
          ShellScriptRunner.runAndCaptureOutput(
              env, CREATE_SCRIPT, cmisName, filePath, parentFolderObjectId);
      if (output != null && output.contains("Object ID:")) {
        return output.substring(output.lastIndexOf("Object ID:") + 11).trim();
      }
      fail("create.sh did not return an Object ID. Output: " + output);
      return null;
    } catch (Exception e) {
      fail("Failed to create document in CMIS folder: " + e.getMessage());
      return null;
    }
  }

  /**
   * Deletes a CMIS object (folder or document) by its object ID via delete.sh.
   *
   * @param objectId the CMIS object ID to delete
   */
  public static void deleteObjectFromCmis(String objectId) {
    try {
      Map<String, String> env = getCmisEnv();
      int exitCode = ShellScriptRunner.run(env, DELETE_SCRIPT, objectId);
      if (exitCode != 0) {
        System.out.println("WARNING: delete.sh exited with non-zero code: " + exitCode);
      }
    } catch (Exception e) {
      System.out.println("WARNING: Failed to delete CMIS object: " + e.getMessage());
    }
  }

  /**
   * @param cmisName the name the document will have in the CMIS repository
   * @param filePath path to the local file to upload
   * @param entityId the entity ID whose attachments folder is the upload target
   */
  public static void createDocumentInCmis(String cmisName, String filePath, String entityId) {
    try {
      Map<String, String> env = getCmisEnv();
      String folderLine =
          ShellScriptRunner.runAndCaptureOutput(
              env, GET_OBJECT_ID_SCRIPT, entityId + "__attachments");
      String parentFolderObjectId =
          folderLine != null && folderLine.contains(": ")
              ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
              : folderLine;

      int exitCode =
          ShellScriptRunner.run(env, CREATE_SCRIPT, cmisName, filePath, parentFolderObjectId);
      if (exitCode != 0) {
        fail("create.sh exited with non-zero code: " + exitCode);
      }
    } catch (Exception e) {
      fail("Failed to create document in CMIS: " + e.getMessage());
    }
  }

  /**
   * Resolves the CMIS object ID of a document by name inside the folder named {@code entityId +
   * "__attachments"}, then deletes it via the delete.sh script.
   *
   * @param entityId the entity ID whose attachments folder is the parent
   * @param fileName the cmis:name of the document to delete
   */
  public static void deleteDocumentFromCmis(String entityId, String fileName) {
    try {
      Map<String, String> env = getCmisEnv();
      String folderLine =
          ShellScriptRunner.runAndCaptureOutput(
              env, GET_OBJECT_ID_SCRIPT, entityId + "__attachments");
      String parentFolderObjectId =
          folderLine != null && folderLine.contains(": ")
              ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
              : folderLine;

      String docLine =
          ShellScriptRunner.runAndCaptureOutput(
              env, GET_OBJECT_ID_SCRIPT, fileName, parentFolderObjectId, "cmis:document");
      String documentObjectId =
          docLine != null && docLine.contains(": ")
              ? docLine.substring(docLine.lastIndexOf(": ") + 2).trim()
              : docLine;

      int deleteExitCode =
          ShellScriptRunner.run(env, DELETE_SCRIPT, documentObjectId, parentFolderObjectId);
      if (deleteExitCode != 0) {
        fail("delete.sh failed with exit code: " + deleteExitCode);
      }
    } catch (Exception e) {
      fail("Failed to delete document from CMIS: " + e.getMessage());
    }
  }

  /**
   * Reads (downloads) a CMIS document by resolving its object ID from the entity's attachments
   * folder, then downloads it to the specified output path via read.sh.
   *
   * @param entityId the entity ID whose attachments folder contains the document
   * @param fileName the cmis:name of the document to read
   * @param outputPath local path to save the downloaded content
   */
  public static void readDocumentFromCmis(String entityId, String fileName, String outputPath) {
    try {
      Map<String, String> env = getCmisEnv();
      String folderLine =
          ShellScriptRunner.runAndCaptureOutput(
              env, GET_OBJECT_ID_SCRIPT, entityId + "__attachments");
      String parentFolderObjectId =
          folderLine != null && folderLine.contains(": ")
              ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
              : folderLine;

      String docLine =
          ShellScriptRunner.runAndCaptureOutput(
              env, GET_OBJECT_ID_SCRIPT, fileName, parentFolderObjectId, "cmis:document");
      String documentObjectId =
          docLine != null && docLine.contains(": ")
              ? docLine.substring(docLine.lastIndexOf(": ") + 2).trim()
              : docLine;

      int exitCode = ShellScriptRunner.run(env, READ_SCRIPT, documentObjectId, outputPath);
      if (exitCode != 0) {
        fail("read.sh exited with non-zero code: " + exitCode);
      }
    } catch (Exception e) {
      fail("Failed to read document from CMIS: " + e.getMessage());
    }
  }

  /**
   * Reads CMIS metadata (properties) for a document by resolving its object ID from the entity's
   * attachments folder, then fetching its properties via get-metadata.sh.
   *
   * @param entityId the entity ID whose attachments folder contains the document
   * @param fileName the cmis:name of the document to get metadata for
   * @return the JSON metadata string returned by the CMIS API
   */
  public static String readDocumentMetadataFromCmis(String entityId, String fileName) {
    try {
      Map<String, String> env = getCmisEnv();
      String folderName = entityId + "__attachments";
      String folderLine =
          ShellScriptRunner.runAndCaptureOutput(env, GET_OBJECT_ID_SCRIPT, folderName);
      String parentFolderObjectId =
          folderLine != null && folderLine.contains(": ")
              ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
              : folderLine;

      String docLine =
          ShellScriptRunner.runAndCaptureOutput(
              env, GET_OBJECT_ID_SCRIPT, fileName, parentFolderObjectId, "cmis:document");
      String documentObjectId =
          docLine != null && docLine.contains(": ")
              ? docLine.substring(docLine.lastIndexOf(": ") + 2).trim()
              : docLine;

      String metadata =
          ShellScriptRunner.runAndCaptureOutput(env, GET_METADATA_SCRIPT, documentObjectId);
      return metadata;
    } catch (Exception e) {
      fail("Failed to read document metadata from CMIS: " + e.getMessage());
      return null;
    }
  }

  /**
   * Retrieves the value of a specific CMIS property for a document.
   *
   * @param entityId the entity ID whose attachments folder contains the document
   * @param fileName the cmis:name of the document
   * @param propertyName the CMIS property name (e.g. "cmis:createdBy")
   * @return the property value as a String, or null if the property is not found
   */
  public static String getCmisProperty(String entityId, String fileName, String propertyName) {
    try {
      String metadata = readDocumentMetadataFromCmis(entityId, fileName);
      JsonNode root = new ObjectMapper().readTree(metadata);
      JsonNode valueNode = root.path("properties").path(propertyName).path("value");
      if (valueNode.isMissingNode()) {
        fail("CMIS property '" + propertyName + "' not found in metadata");
        return null;
      }
      return valueNode.asText();
    } catch (Exception e) {
      fail("Failed to get CMIS property '" + propertyName + "': " + e.getMessage());
      return null;
    }
  }

  public static String getCmisPropertyOrNull(
      String entityId, String fileName, String propertyName) {
    try {
      String metadata = readDocumentMetadataFromCmis(entityId, fileName);
      JsonNode root = new ObjectMapper().readTree(metadata);
      JsonNode valueNode = root.path("properties").path(propertyName).path("value");
      if (valueNode.isMissingNode() || valueNode.isNull()) {
        return null;
      }
      return valueNode.asText();
    } catch (Exception e) {
      return null;
    }
  }
}
