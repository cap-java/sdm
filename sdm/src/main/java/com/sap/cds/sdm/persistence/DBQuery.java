package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DBQuery {
    public static Result getAttachmentsForUP__ID(CdsEntity attachmentEntity, PersistenceService persistenceService, String up__ID){
        CqnSelect q = Select.from(attachmentEntity).columns("fileName", "ID","IsActiveEntity","folderId","repositoryId").where(doc -> doc.get("up__ID").eq(up__ID));
     return persistenceService.run(q);
    }

    public static void addAttachmentToDraft(CdsEntity attachmentEntity, PersistenceService persistenceService, CmisDocument cmisDocument){
        String repositoryId = SDMConstants.REPOSITORY_ID;
        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put("url", cmisDocument.getObjectId());
        updatedFields.put("repositoryId", repositoryId);
        updatedFields.put("folderId", cmisDocument.getFolderId());

        CqnUpdate updateQuery = Update.entity(attachmentEntity)
                .data(updatedFields)
                .where(doc -> doc.get("ID").eq(cmisDocument.getAttachmentId()));
        persistenceService.run(updateQuery);
    }

    public static void deleteAttachmentFromDraft(CdsEntity attachmentEntity, PersistenceService persistenceService, String attachmentId) {
        CqnDelete deleteQuery = Delete.from(attachmentEntity)
                .where(doc -> doc.get("ID").eq(attachmentId));
        persistenceService.run(deleteQuery);
    }

    public static String getFolderIdForActiveEntity(CdsEntity attachmentEntity, PersistenceService persistenceService, String up__ID) {
        String res = null;
        CqnSelect query = Select.from(attachmentEntity)
                .columns("folderId")
                .where(doc -> doc.get("up__ID").eq(up__ID)
                        .and(doc.get("IsActiveEntity").eq(true)));
        Result result = persistenceService.run(query);

        for (Map<String, Object> row : result.listOf(Map.class)) {
            Object folderIdObj = row.get("folderId");
            if (folderIdObj != null) {
                res = folderIdObj.toString();
                break;  // Exit the loop after finding the first non-null folderId
            }
        }
        System.out.println("Res : "+res);
        return res;
    }

}
