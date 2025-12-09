namespace sap.attachments;

using {sap.attachments.Attachments} from `com.sap.cds/cds-feature-attachments`;

type UploadStatusCode : String enum {
    UPLOAD_IN_PROGRESS;
    VIRUS_SCAN_INPROGRESS;
    SUCCESS;
    VIRUS_DETECTED;
    SCAN_FAILED;
}
extend aspect Attachments with {
    folderId : String;
    repositoryId : String;
    objectId : String;
    linkUrl : String default null;
    type : String @(UI: {IsImageURL: true}) default 'sap-icon://document';
    uploadStatus: UploadStatusCode @readonly default 'Upload In Progress';
    statusCriticality: Integer @Core.Computed @odata.Type: 'Edm.Byte' default 5;
}
annotate Attachments with @UI: {

    HeaderInfo: {
        $Type         : 'UI.HeaderInfoType',
        TypeName      : '{i18n>Attachment}',
        TypeNamePlural: '{i18n>Attachments}',
    },
    LineItem  : [
        {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
               {Value: content, @HTML5.CssDefaults: {width: '0%'}},
               {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
               {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
               {Value: note, @HTML5.CssDefaults: {width: '25%'}},
{Value: uploadStatus, @HTML5.CssDefaults: {width: '15%'}, Criticality: statusCriticality},
    ]
} {
    note       @(title: '{i18n>Description}');
    fileName  @(title: '{i18n>Filename}');
    modifiedAt @(odata.etag: null);
    uploadStatus  @(title: '{i18n>Upload Status}', UI.Criticality: statusCriticality);
    content
       @Core.ContentDisposition: { Filename: fileName, Type: 'inline' }
        @(title: '{i18n>Attachment}');
    folderId @UI.Hidden;
    repositoryId  @UI.Hidden ;
    objectId  @UI.Hidden ;
    mimeType @UI.Hidden;
    status @UI.Hidden;
}

annotate Attachments with @Common: {SideEffects #ContentChanged: {
    SourceProperties: [content],
    TargetProperties: ['status']
}}{};