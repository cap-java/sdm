package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;

public class DBQuery {
  public static String getAttachmentsForUP__ID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String up__ID) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("fileName", "ID", "IsActiveEntity", "folderId", "repositoryId")
            .where(doc -> doc.get("up__ID").eq(up__ID));
    Result result = persistenceService.run(q);
    List<Row> rows = result.list();
    String folderId = null;
    if (rows.size() != 0) {
      folderId = rows.get(0).get("folderId").toString();
    }
    return folderId;
  }
}
