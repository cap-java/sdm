package integration.com.sap.cds.sdm;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;

public interface ApiInterface {
  public String createEntityDraft(
      String appUrl, String serviceName, String entityName, String entityName2, String srvpath);

  public String editEntityDraft(
      String appUrl, String serviceName, String entityName, String srvpath, String entityID);

  public String saveEntityDraft(
      String appUrl, String serviceName, String entityName, String srvpath, String entityID);

  public String deleteEntity(String appUrl, String serviceName, String entityName, String entityID);

  public String checkEntity(String appUrl, String serviceName, String entityName, String entityID);

  public List<String> createAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String srvpath,
      Map<String, Object> postData,
      File file)
      throws IOException;

  public String readAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID)
      throws IOException;

  public String readAttachmentDraft(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID)
      throws IOException;

  public String deleteAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID);

  public String renameAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      String name);

  public String updateSecondaryProperty(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      RequestBody requestBody);

  public String updateInvalidSecondaryProperty(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      String invalidSecondaryProperty);

  public Map<String, Object> fetchMetadata(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID)
      throws IOException;
}
