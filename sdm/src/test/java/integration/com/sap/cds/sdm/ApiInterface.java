package integration.com.sap.cds.sdm;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;

public interface ApiInterface {
  public String createEntityDraft(
      String appUrl, String entityName, String entityName2, String srvpath);

  public String editEntityDraft(String appUrl, String entityName, String srvpath, String entityID);

  public String saveEntityDraft(String appUrl, String entityName, String srvpath, String entityID);

  public String deleteEntity(String appUrl, String entityName, String entityID);

  public String checkEntity(String appUrl, String entityName, String entityID);

  public List<String> createAttachment(
      String appUrl,
      String entityName,
      String facetName,
      String entityID,
      String srvpath,
      Map<String, Object> postData,
      File file)
      throws IOException;

  public String readAttachment(
      String appUrl, String entityName, String facetName, String entityID, String ID)
      throws IOException;

  public String readAttachmentDraft(
      String appUrl, String entityName, String facetName, String entityID, String ID)
      throws IOException;

  public String deleteAttachment(
      String appUrl, String entityName, String facetName, String entityID, String ID);

  public String renameAttachment(
      String appUrl, String entityName, String facetName, String entityID, String ID, String name);

  public String updateSecondaryProperty(
      String appUrl,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      RequestBody requestBody);

  public String updateInvalidSecondaryProperty(
      String appUrl,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      String invalidSecondaryProperty);

  public String copyAttachment(
      String appUrl,
      String entityName,
      String facetName,
      String entityID,
      List<String> sourceObjectIds)
      throws IOException;

  public Map<String, Object> moveAttachment(
      String appUrl,
      String entityName,
      String facetName,
      String targetEntityID,
      String sourceFolderId,
      List<String> objectIds,
      String sourceFacet)
      throws IOException;

  public Map<String, Object> fetchMetadata(
      String appUrl, String entityName, String facetName, String entityID, String ID)
      throws IOException;

  public Map<String, Object> fetchMetadataDraft(
      String appUrl, String entityName, String facetName, String entityID, String ID)
      throws IOException;

  public List<Map<String, Object>> fetchEntityMetadata(
      String appUrl, String entityName, String facetName, String entityID) throws IOException;

  public List<Map<String, Object>> fetchEntityMetadataDraft(
      String appUrl, String entityName, String facetName, String entityID) throws IOException;

  public String createLink(
      String appUrl,
      String entityName,
      String facetName,
      String entityID,
      String linkName,
      String linkUrl)
      throws IOException;

  public String editLink(
      String appUrl,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      String linkUrl)
      throws IOException;

  public String openAttachment(
      String appUrl, String entityName, String facetName, String entityID, String ID)
      throws IOException;

  String deleteEntityDraft(String appUrl, String entityName, String entityID);

  public Map<String, Object> fetchChangelog(
      String appUrl, String entityName, String facetName, String entityID, String ID)
      throws IOException;
}
