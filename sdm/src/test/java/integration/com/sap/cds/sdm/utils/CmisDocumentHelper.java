package integration.com.sap.cds.sdm.utils;

import static org.junit.jupiter.api.Assertions.fail;

public class CmisDocumentHelper {

  private static final String CREATE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/create.sh";
  private static final String GET_OBJECT_ID_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/get-object-id.sh";
  private static final String DELETE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/delete.sh";

  /**
   * Resolves the CMIS parent folder ID from {@code entityId + "__attachments"}, then uploads a
   * local file to that folder via create.sh.
   *
   * @param cmisName the name the document will have in the CMIS repository
   * @param filePath path to the local file to upload
   * @param entityId the entity ID whose attachments folder is the upload target
   */
  public static void createDocumentInCmis(String cmisName, String filePath, String entityId) {
    try {
      // Resolve the parent folder object ID from entityId__attachments
      String folderLine =
          ShellScriptRunner.runAndCaptureOutput(GET_OBJECT_ID_SCRIPT, entityId + "__attachments");
      String parentFolderObjectId =
          folderLine != null && folderLine.contains(": ")
              ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
              : folderLine;
      System.out.println("Resolved parent folder object ID: " + parentFolderObjectId);

      int exitCode = ShellScriptRunner.run(CREATE_SCRIPT, cmisName, filePath, parentFolderObjectId);
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
      // Step 1: resolve the parent folder object ID from entityId__attachments
      String folderLine =
          ShellScriptRunner.runAndCaptureOutput(GET_OBJECT_ID_SCRIPT, entityId + "__attachments");
      String parentFolderObjectId =
          folderLine != null && folderLine.contains(": ")
              ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
              : folderLine;
      System.out.println("Resolved parent folder object ID: " + parentFolderObjectId);

      // Step 2: resolve the document object ID by filename inside the parent folder
      String docLine =
          ShellScriptRunner.runAndCaptureOutput(
              GET_OBJECT_ID_SCRIPT, fileName, parentFolderObjectId, "cmis:document");
      String documentObjectId =
          docLine != null && docLine.contains(": ")
              ? docLine.substring(docLine.lastIndexOf(": ") + 2).trim()
              : docLine;
      System.out.println("Resolved document object ID: " + documentObjectId);

      // Step 3: delete the document
      int deleteExitCode =
          ShellScriptRunner.run(DELETE_SCRIPT, documentObjectId, parentFolderObjectId);
      if (deleteExitCode != 0) {
        fail("delete.sh failed with exit code: " + deleteExitCode);
      }
    } catch (Exception e) {
      fail("Failed to delete document from CMIS: " + e.getMessage());
    }
  }
}
