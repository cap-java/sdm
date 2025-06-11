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
      String appUrl, String serviceName, String entityName, String entityName2, String srvpath) {
    MediaType mediaType = MediaType.parse("application/json");

    // Creating the Entity (draft)
    RequestBody body =
        RequestBody.create(
            mediaType,
            "{\n    \"title\": \"IntegrationTestEntity\",\n    \""
                + entityName2
                + "\": {\n        \"ID\": \"41cf82fb-94bf-4d62-9e45-fa25f959b5b0\",\n        \"name\": \"Akshat\"\n    }\n}");

    Request request =
        new Request.Builder()
            .url("https://" + appUrl + "/odata/v4/" + serviceName + "/" + entityName)
            .method("POST", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        if (response.code() == 401) {
          System.out.println(
              "Create entity failed due to incorrect token. Please check the credentials");
        }
        System.out.println("Create entity failed. Error : " + response.body().string());
        throw new IOException("Could not create entity");
      }
      Map<String, Object> responseMap = objectMapper.readValue(response.body().string(), Map.class);
      return (String) responseMap.get("ID");
    } catch (IOException e) {
      System.out.println("Could not create entity : " + e);
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
        System.out.println("Edit entity failed. Error : " + response.body().string());
        throw new IOException("Could not edit entity");
      }
      return "Entity in draft mode";
    } catch (IOException e) {
      System.out.println("Could not edit entity : " + e);
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
        System.out.println("Save entity failed. Error : " + response.body().string());
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
            String draftResponseBodyString = draftResponse.body().string();
            System.out.println("Save entity failed. Error : " + draftResponseBodyString);
            return (draftResponseBodyString);
          }
          String sapMessages = draftResponse.header("sap-messages");
          if (sapMessages != null && !sapMessages.isEmpty()) {
            return sapMessages;
          }
          return "Saved";
        } catch (IOException e) {
          System.out.println("Could not save entity : " + e);
        }
      }
    } catch (IOException e) {
      System.out.println("Could not save entity : " + e);
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
        System.out.println("Delete entity failed. Error : " + response.body().string());
        throw new IOException("Could not delete entity");
      }
      return "Entity Deleted";
    } catch (IOException e) {
      System.out.println("Could not delete entity : " + e);
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
        System.out.println("Verify entity failed. Error : " + checkResponse.body().string());
        throw new IOException("Entity doesn't exist");
      } else {
        return "Entity exists";
      }
    } catch (IOException e) {
      System.out.println("Could not verify entity : " + e);
    }
    return ("Entity doesn't exist");
  }

  public List<String> createAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String srvpath,
      Map<String, Object> postData,
      File file)
      throws IOException {
    String ID;
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
                    + ",IsActiveEntity=false)/"
                    + facetName)
            .method("POST", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response response = httpClient.newCall(postRequest).execute()) {
      if (response.code() != 201) {
        System.out.println(
            "Create Attachment in the section: "
                + facetName
                + " failed. Error : "
                + response.body().string());
        throw new IOException("Could not read Attachment");
      }
      Map<String, Object> responseMap = objectMapper.readValue(response.body().string(), Map.class);
      ID = (String) responseMap.get("ID");

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
                      + "/"
                      + entityName
                      + "_"
                      + facetName
                      + "(up__ID="
                      + entityID
                      + ",ID="
                      + ID
                      + ",IsActiveEntity=false)/content")
              .put(fileBody)
              .addHeader("Authorization", token)
              .build();

      try (Response fileResponse = httpClient.newCall(fileRequest).execute()) {
        if (fileResponse.code() != 204) {
          String responseBodyString = fileResponse.body().string();
          System.out.println(
              "Create Attachment in the section: "
                  + facetName
                  + " failed. Error : "
                  + responseBodyString);
          error = responseBodyString;
          Request request =
              new Request.Builder()
                  .url(
                      "https://"
                          + appUrl
                          + "/odata/v4/"
                          + serviceName
                          + "/"
                          + entityName
                          + "_"
                          + facetName
                          + "(up__ID="
                          + entityID
                          + ",ID="
                          + ID
                          + ",IsActiveEntity=false)")
                  .delete()
                  .addHeader("Authorization", token)
                  .build();

          try (Response deleteResponse = httpClient.newCall(request).execute()) {
            if (deleteResponse.code() != 204) {
              System.out.println(
                  "Delete Attachment in section :"
                      + facetName
                      + " failed. Error : "
                      + deleteResponse.body().string());
              throw new IOException(
                  "Attachment was not created in section : "
                      + facetName
                      + " and its container was not deleted : ");
            }
            List<String> createResponse = new ArrayList<>();
            createResponse.add(error);
            return createResponse;
          } catch (IOException e) {
            System.out.println(
                "Attachment was not created in section : "
                    + facetName
                    + " and its container was not deleted : "
                    + e);
          }
        }
        long endTime = System.nanoTime(); // Record end time
        double duration = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("Time taken to create(s) : " + duration);
        List<String> createResponse = new ArrayList<>();
        createResponse.add("Attachment created");
        createResponse.add(ID);
        return createResponse;
      } catch (IOException e) {
        System.out.println("Attachment was not created in section: " + facetName + " : " + e);
      }
    } catch (IOException e) {
      System.out.println("Attachment was not created in section: " + facetName + " : " + e);
    }
    List<String> createResponse = new ArrayList<>();
    createResponse.add("Attachment was not created in section: " + facetName);
    return createResponse;
  }

  public String readAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID)
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
                    + ",IsActiveEntity=true)/"
                    + facetName
                    + "(up__ID="
                    + entityID
                    + ",ID="
                    + ID
                    + ",IsActiveEntity=true)/content")
            .addHeader("Authorization", token)
            .get()
            .build();

    try {
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        System.out.println(
            "Read Attachnent failed in the"
                + facetName
                + " section. Error :"
                + response.body().string());
        throw new IOException("Read Attachnent failed in the" + facetName + " section");
      }
      return "OK";
    } catch (IOException e) {
      System.out.println("Could not read Attachment :" + e);
      return "Could not read Attachment";
    }
  }

  public String readAttachmentDraft(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID)
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
                    + ",IsActiveEntity=false)/"
                    + facetName
                    + "(up__ID="
                    + entityID
                    + ",ID="
                    + ID
                    + ",IsActiveEntity=false)/content")
            .addHeader("Authorization", token)
            .get()
            .build();

    try {
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        System.out.println("Read draft attachment failed. Error : " + response.body().string());
        throw new IOException("Could not read attachment");
      }
      return "OK";
    } catch (IOException e) {
      System.out.println("Could not read Attachment : " + e);
      return "Could not read Attachment";
    }
  }

  public String deleteAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID) {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "_"
                    + facetName
                    + "(up__ID="
                    + entityID
                    + ",ID="
                    + ID
                    + ",IsActiveEntity=false)")
            .delete()
            .addHeader("Authorization", token)
            .build();

    try (Response deleteResponse = httpClient.newCall(request).execute()) {
      if (deleteResponse.code() != 204) {
        System.out.println(
            "Delete Attachment failed in the"
                + facetName
                + " section. Error :"
                + deleteResponse.body().string());
        throw new IOException("Attachment was not deleted in section : " + facetName);
      }
      return "Deleted";
    } catch (IOException e) {
      System.out.println("Could not delete Attachment:" + facetName + " :" + e);
      return "Could not delete Attachment";
    }
  }

  public String renameAttachment(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      String name) {
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
                    + "/"
                    + entityName
                    + "_"
                    + facetName
                    + "(up__ID="
                    + entityID
                    + ",ID="
                    + ID
                    + ",IsActiveEntity=false)")
            .method("PATCH", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response renameResponse = httpClient.newCall(request).execute()) {
      if (renameResponse.code() != 200) {
        System.out.println(
            "Rename Attachment failed in the"
                + facetName
                + " section. Error : "
                + renameResponse.body().string());
        throw new IOException("Attachment was not renamed in section: " + facetName);
      }
      return "Renamed";
    } catch (IOException e) {
      System.out.println("Attachment was not renamed in section: " + facetName + " : " + e);
      return "Attachment was not renamed in section: " + facetName;
    }
  }

  public String updateSecondaryProperty(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      RequestBody requestBody) {

    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "_"
                    + facetName
                    + "(up__ID="
                    + entityID
                    + ",ID="
                    + ID
                    + ",IsActiveEntity=false)")
            .method("PATCH", requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response updateResponse = httpClient.newCall(request).execute()) {
      if (updateResponse.code() != 200) {
        System.out.println(
            "Updating secondary property failed. Error: " + updateResponse.body().string());
        throw new IOException("Secondary Property was not updated");
      }
      return "Updated";
    } catch (IOException e) {
      System.out.println("Secondary Property was not updated: " + e);
      return "Secondary Property was not updated";
    }
  }

  public String updateInvalidSecondaryProperty(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID,
      String invalidSecondaryProperty) {
    MediaType mediaType = MediaType.parse("application/json");
    String jsonPayload = "{\n    \"abc___myId1\": \"" + invalidSecondaryProperty + "\"\n}";
    RequestBody body = RequestBody.create(mediaType, ByteString.encodeUtf8(jsonPayload));
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + appUrl
                    + "/odata/v4/"
                    + serviceName
                    + "/"
                    + entityName
                    + "_"
                    + facetName
                    + "(up__ID="
                    + entityID
                    + ",ID="
                    + ID
                    + ",IsActiveEntity=false)")
            .method("PATCH", body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();

    try (Response updateResponse = httpClient.newCall(request).execute()) {
      if (updateResponse.code() != 200) {
        System.out.println(
            "Updating secondary property failed. Error : " + updateResponse.body().string());
        throw new IOException("Secondary Property was not updated");
      }
      return "Updated";
    } catch (IOException e) {
      System.out.println("Secondary Property was not updated : " + e);
      return "Secondary Property was not updated";
    }
  }

  public Map<String, Object> fetchMetadata(
      String appUrl,
      String serviceName,
      String entityName,
      String facetName,
      String entityID,
      String ID)
      throws IOException {
    // Construct the URL for fetching attachment metadata
    String url =
        "https://"
            + appUrl
            + "/odata/v4/"
            + serviceName
            + "/"
            + entityName
            + "_"
            + facetName
            + "(up__ID="
            + entityID
            + ",ID="
            + ID
            + ",IsActiveEntity=true)";

    // Make a GET request to fetch the attachment metadata
    Request request =
        new Request.Builder().url(url).get().addHeader("Authorization", token).build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.code() != 200) {
        System.out.println("Response code: " + response.code());
        System.out.println(
            "Fetch metadata failed for "
                + facetName
                + " Section. Error: "
                + response.body().string());
        throw new IOException("Could not fetch " + facetName + " metadata");
      } else {
        // Parse the JSON response to extract metadata
        return objectMapper.readValue(
            response.body().string(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
      }
    }
  }
}
