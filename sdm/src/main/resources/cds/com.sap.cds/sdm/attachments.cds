namespace sap.attachments;

using {sap.attachments.Attachments} from `com.sap.cds/cds-feature-attachments`;
extend aspect Attachments with {
    folderId : String ;
    repositoryId : String ;
    url : String ;
}
annotate Attachments with @UI: {
    HeaderInfo: {
        $Type         : 'UI.HeaderInfoType',
        TypeName      : '{i18n>Attachment}',
        TypeNamePlural: '{i18n>Attachments}',
    },
    LineItem  : [
        {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
         {Value: content, @HTML5.CssDefaults: {width: '20%'}},
          {Value: createdAt, @HTML5.CssDefaults: {width: '20%'}},
          {Value: createdBy, @HTML5.CssDefaults: {width: '20%'}},
          {Value: note, @HTML5.CssDefaults: {width: '20%'}}
    ]
} {
    note       @(title: '{i18n>Note}');
       fileName  @(title: '{i18n>Filename}');
      modifiedAt @(odata.etag: null);
      content
          @Core.ContentDisposition: { Filename: fileName, Type: 'inline' }
           @(title: '{i18n>Attachment}');
}
annotate Attachments with @Common: {SideEffects #ContentChanged: {
    SourceProperties: [content],
    TargetProperties: ['status']
}}{};
