package com.sap.cds.sdm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmisDocument {
    private String attachmentId;
    private String objectId;
    private String fileName;
    private InputStream content;
    private String parentId;
    private String folderId;
    private String repositoryId;
    private String status;
}
