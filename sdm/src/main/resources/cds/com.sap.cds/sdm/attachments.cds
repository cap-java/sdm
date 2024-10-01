namespace sap.attachments;

using {
    sap.attachments.Attachments,sap.attachments.StatusCode
    managed
} from 'com.sap.cds/cds-feature-attachments';

extend aspect Attachments with{
    folderId : String;
    repositoryId : String;
    url : String;
}
