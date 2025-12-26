package com.sap.cds.sdm.constants;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = System.getenv("REPOSITORY_ID");
  public static final String MIMETYPE_INTERNET_SHORTCUT = "application/internet-shortcut";
  public static final String SYSTEM_USER = "system-internal";
  public static final String SDM_ANNOTATION_ADDITIONALPROPERTY_NAME =
      "SDM.Attachments.AdditionalProperty.name";
  public static final String SDM_ANNOTATION_ADDITIONALPROPERTY =
      "SDM.Attachments.AdditionalProperty";
  public static final String DRAFT_READONLY_CONTEXT = "DRAFT_READONLY_CONTEXT";
  public static final Integer TIMEOUT_MILLISECONDS = 900000;
  public static final Integer MAX_CONNECTIONS_PER_ROUTE = 50;
  public static final Integer MAX_CONNECTIONS_TOTAL = 50;
  public static final String REST_V2_REPOSITORIES = "rest/v2/repositories";
  public static final String TECHNICAL_USER_FLOW = "TECHNICAL_CREDENTIALS_FLOW";
  public static final String NAMED_USER_FLOW = "TOKEN_EXCHANGE";
  public static final String ANNOTATION_IS_MEDIA_DATA = "_is_media_data";
  public static final Integer MAX_CONNECTIONS = 100;
  public static final int CONNECTION_TIMEOUT = 1200;
  public static final int CHUNK_SIZE = 20 * 1024 * 1024; // 20MB Chunk Size
  public static final String SDM_ENV_NAME = "sdm";
  public static final String SDM_TOKEN_EXCHANGE_DESTINATION = "sdm-token-exchange-flow";
  public static final String SDM_TECHNICAL_CREDENTIALS_FLOW_DESTINATION = "sdm-technical-user-flow";
  public static final String SDM_TOKEN_FETCH = "sdm-token-fetch";
  public static final String SDM_DESTINATION_KEY = "name";
  public static final String SDM_CONNECTIONPOOL_PREFIX = "cds.attachments.sdm.http.%s";
  public static final String ATTACHMENT_MAXCOUNT = "SDM.Attachments.maxCount";
}
