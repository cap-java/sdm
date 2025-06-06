namespace sap.attachments;

using {sap.attachments.Attachments} from `com.sap.cds/cds-feature-attachments`;
extend aspect Attachments with {
    folderId : String ;
    repositoryId : String ;
    objectId : String ;
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
          {Value: note, @HTML5.CssDefaults: {width: '20%'}},
          {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Link',
       ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
 
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Create Link',
          Action: 'AdminService.createLink',
        },
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Edit Link',
          Action: 'AdminService.editLink',
        },
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Open Link',
          Action: 'AdminService.openLink',
        }
      ]
    },
    ]
} {
    note       @(title: '{i18n>Note}');
    fileName  @(title: '{i18n>Filename}');
    modifiedAt @(odata.etag: null);
    content
       @Core.ContentDisposition: { Filename: fileName }
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
