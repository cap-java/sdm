namespace sap.attachments;

using {sap.attachments.Attachments} from `com.sap.cds/cds-feature-attachments`;

using {
  sap.common.CodeList
} from '@sap/cds/common';


type UploadStatusCode : String(32) enum {
   UploadInProgress;
   Success;
   Failed;
   VirusDetected;
   VirusScanInprogress;
}
extend aspect Attachments with {
    folderId : String;
    repositoryId : String;
    objectId : String;
    linkUrl : String default null;
    type : String @(UI: {IsImageURL: true}) default 'sap-icon://document';
    uploadStatus : UploadStatusCode default 'UploadInProgress';
    uploadStatusNav : Association to one UploadScanStates on uploadStatusNav.code = uploadStatus;
}

     entity UploadScanStates : CodeList {
         key code        : UploadStatusCode  @Common.Text: name  @Common.TextArrangement: #TextOnly;
             name        : localized String(64) ;
             criticality : Integer     @UI.Hidden;
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
               {Value: note, @HTML5.CssDefaults: {width: '20%'}},

{
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'},
        @UI.Hidden: IsActiveEntity
      },
    ]
} {
    note       @(title: '{i18n>Description}');
    fileName  @(title: '{i18n>Filename}');
    uploadStatus @(title: '{i18n>attachment_status}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
    modifiedAt @(odata.etag: null);
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
}};