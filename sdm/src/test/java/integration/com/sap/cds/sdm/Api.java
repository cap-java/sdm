package integration.com.sap.cds.sdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.*;
import okhttp3.*;
import okio.ByteString;

public class Api {
  private final Map<String, String> config;
  private final OkHttpClient httpClient;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final String token;

  public Api(Map<String, String> config) {
    this.config = new HashMap<>(config);
    this.httpClient = new OkHttpClient();
    this.token = this.config.get("Authorization");
  }

  public String createEntityDraft(
      String appUrl, String serviceName, String entityName, String srvpath) {
    MediaType mediaType = MediaType.parse("application/json");

    // Creating the Entity (draft)
    RequestBody body =
        RequestBody.create(
            mediaType,
            "{\n    \"title\": \"IntegrationTestEntity\",\n    \"author\": {\n        \"ID\": \"41cf82fb-94bf-4d62-9e45-fa25f959b5b0\",\n        \"name\": \"Rishi\"\n    }\n}");

    Request request =
        new Request.Builder()
            .url("https://" + appUrl + "/odata/v4/" + serviceName + "/" + entityName)
            .method("POST", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Could not create entity");
      }
      Map<String, Object> responseMap = objectMapper.readValue(response.body().string(), Map.class);
      return (String) responseMap.get("ID");
    } catch (IOException e) {
      System.out.println("Could not create entity");
    }
    return ("Could not create entity");
  }

  public String editEntityDraft(
      String appUrl, String serviceName, String entityName, String srvpath, String entityID) {
    MediaType mediaType = MediaType.parse("application/json");
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=true)/"
                    + srvpath
                    + ".draftEdit")
            .post(RequestBody.create("{\"PreserveChanges\":true}", mediaType))
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.code() != 200) {
        throw new IOException("Could not edit entity");
      }
      return "Entity in draft mode";
    } catch (IOException e) {
      System.out.println("Could not edit entity");
    }
    return "Could not edit entity";
  }

  public String saveEntityDraft(
      String appUrl, String serviceName, String entityName, String srvpath, String entityID) {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=false)/"
                    + srvpath
                    + ".draftPrepare")
            .post(
                RequestBody.create(
                    "{\"SideEffectsQualifier\":\"\"}", MediaType.parse("application/json")))
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.code() != 200) {
        throw new IOException("Could not save entity");
      } else {
        request =
            new Request.Builder()
                .url(
                    "https://"
                        + appUrl
                        + "/odata/v4/"
                        + serviceName
                        + "/"
                        + entityName
                        + "(ID="
                        + entityID
                        + ",IsActiveEntity=false)/"
                        + srvpath
                        + ".draftActivate")
                .post(RequestBody.create("", null))
                .addHeader("Authorization", token)
                .build();

        try (Response draftResponse = httpClient.newCall(request).execute()) {
          if (draftResponse.code() != 200) {
            return (draftResponse.body().string());
          }
          return "Saved";
        } catch (IOException e) {
          System.out.println("Could not save entity");
        }
      }
    } catch (IOException e) {
      System.out.println("Could not save entity");
    }

    return "Could not save entity";
  }

  public String deleteEntity(
      String appUrl, String serviceName, String entityName, String entityID) {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=true)")
            .delete()
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Could not delete entity");
      }
      return "Entity Deleted";
    } catch (IOException e) {
      System.out.println("Could not delete entity");
    }
    return ("Could not delete entity");
  }

  public String checkEntity(String appUrl, String serviceName, String entityName, String entityID) {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=true)")
            .addHeader("Authorization", token)
            .build();

    try (Response checkResponse = httpClient.newCall(request).execute()) {
      if (checkResponse.code() != 200) {
        throw new IOException("Entity doesn't exist");
      } else {
        return "Entity exists";
      }
    } catch (IOException e) {
      System.out.println("Could not verify entity");
    }
    return ("Entity doesn't exist");
  }

  public List<String> createAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String entityID,
      String srvpath,
      Map<String, Object> postData,
      File file)
      throws IOException {
    String attachmentID;
    String error = "";

    // Creating empty attachments
    String fileName = file.getName();

    MediaType mediaType = MediaType.parse("application/json");
    RequestBody body =
        RequestBody.create(
            mediaType, ByteString.encodeUtf8("{\n    \"fileName\" : \"" + fileName + "\"\n}"));
    Request postRequest =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=false)/attachments")
            .method("POST", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(postRequest).execute()) {
      if (response.code() != 201) {
        throw new IOException("Could not create attachment");
      }
      Map<String, Object> responseMap = objectMapper.readValue(response.body().string(), Map.class);
      attachmentID = (String) responseMap.get("ID");

      long startTime = System.nanoTime();
      // Upload file content into the empty attachment
      RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
      Request fileRequest =
          new Request.Builder()
              .url(
                  "https://"
                      + appUrl
                      + "/odata/v4/"
                      + serviceName
                      + "/Books_attachments(up__ID="
                      + entityID
                      + ",ID="
                      + attachmentID
                      + ",IsActiveEntity=false)/content")
              .put(fileBody)
              .addHeader("Authorization", token)
              .build();

      try (Response fileResponse = httpClient.newCall(fileRequest).execute()) {
        if (fileResponse.code() != 204) {
          // Deleting the incorrect attachment before saving the entity
          error = fileResponse.body().string();
          Request request =
              new Request.Builder()
                  .url(
                      "https://"
                          + appUrl
                          + "/odata/v4/"
                          + serviceName
                          + "/Books_attachments(up__ID="
                          + entityID
                          + ",ID="
                          + attachmentID
                          + ",IsActiveEntity=false)")
                  .delete()
                  .addHeader("Authorization", token)
                  .build();

          try (Response deleteResponse = httpClient.newCall(request).execute()) {
            if (deleteResponse.code() != 204) {
              throw new IOException("Attachment was not created and its container was not deleted");
            }
            List<String> createResponse = new ArrayList<>();
            createResponse.add(error);
            return createResponse;
          } catch (IOException e) {
            System.out.println("Attachment was not created and its container was not deleted");
          }
        }
        long endTime = System.nanoTime(); // Record end time
        double duration = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("Time taken to create(s) : " + duration);
        List<String> createResponse = new ArrayList<>();
        createResponse.add("Attachment created");
        createResponse.add(attachmentID);
        return createResponse;
      } catch (IOException e) {
        System.out.println("Attachment was not created and its container was not deleted");
      }
    } catch (IOException e) {
      System.out.println("Attachment was not created");
    }
    List<String> createResponse = new ArrayList<>();
    createResponse.add("Attachment was not created");
    return createResponse;
  }

  public String readAttachment(
      String appUrl, String serviceName, String entityName, String entityID, String attachmentID)
      throws IOException {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=true)/attachments(up__ID="
                    + entityID
                    + ",ID="
                    + attachmentID
                    + ",IsActiveEntity=true)/content")
            .addHeader("Authorization", token)
            .get()
            .build();

    try {
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        throw new IOException("Could not read attachment");
      }
      return "OK";
    } catch (IOException e) {
      return "Could not read attachment";
    }
  }

  public String readAttachmentDraft(
      String appUrl, String serviceName, String entityName, String entityID, String attachmentID)
      throws IOException {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "(ID="
                    + entityID
                    + ",IsActiveEntity=false)/attachments(up__ID="
                    + entityID
                    + ",ID="
                    + attachmentID
                    + ",IsActiveEntity=false)/content")
            .addHeader("Authorization", token)
            .get()
            .build();

    try {
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        throw new IOException("Could not read attachment");
      }
      return "OK";
    } catch (IOException e) {
      return "Could not read attachment";
    }
  }

  public String deleteAttachment(
      String appUrl, String serviceName, String entityID, String attachmentID) {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/Books_attachments(up__ID="
                    + entityID
                    + ",ID="
                    + attachmentID
                    + ",IsActiveEntity=false)")
            .delete()
            .addHeader("Authorization", token)
            .build();

    try (Response deleteResponse = httpClient.newCall(request).execute()) {
      if (deleteResponse.code() != 204) {
        throw new IOException("Attachment was not deleted");
      }
      return "Deleted";
    } catch (IOException e) {
      return "Attachment was not deleted";
    }
  }

  public String renameAttachment(
      String appUrl, String serviceName, String entityID, String attachmentID, String name) {
    MediaType mediaType = MediaType.parse("application/json");
    RequestBody body =
        RequestBody.create(
            mediaType, ByteString.encodeUtf8("{\n    \"fileName\" : \"" + name + "\"\n}"));
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/Books_attachments(up__ID="
                    + entityID
                    + ",ID="
                    + attachmentID
                    + ",IsActiveEntity=false)")
            .method("PATCH", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response renameResponse = httpClient.newCall(request).execute()) {
      if (renameResponse.code() != 200) {
        throw new IOException("Attachment was not renamed");
      }
      return "Renamed";
    } catch (IOException e) {
      return "Attachment was not renamed";
    }
  }
}
