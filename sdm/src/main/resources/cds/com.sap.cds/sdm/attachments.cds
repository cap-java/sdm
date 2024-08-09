namespace sap.attachments;

using {
    cuid,
    managed
} from '@sap/cds/common';

type StatusCode : String enum {
    Unscanned;
    Scanning;
    Clean;
    Infected;
    Failed;
}

aspect MediaData           @(_is_media_data) {
    content   : LargeBinary @title: 'Attachment'; // stored only for db-based services
    mimeType  : String;
    fileName  : String @title: 'Filename';
    contentId : String     @readonly; // id of attachment in external storage, if database storage is used, same as id
    status    : StatusCode @readonly;
    scannedAt : Timestamp  @readonly;
}

aspect Attachments : cuid, managed, MediaData {
    note : String;
    folderId : String;
    repositoryId : String;
    url : String;
}
