using {
    sap.attachments.MediaData,
    sap.attachments.Attachments
} from './attachments';

annotate MediaData with @UI.MediaResource: { Stream: content } {
  content  @Core.MediaType: mimeType @odata.draft.skip;
  mimeType @Core.IsMediaType;
  status @readonly;
}

annotate Attachments with @UI: {
    HeaderInfo: {
        $Type         : 'UI.HeaderInfoType',
        TypeName      : '{i18n>attachment}',
        TypeNamePlural: '{i18n>attachments}',
    },
    LineItem  : [
        {Value: content},
        {Value: createdAt},
        {Value: createdBy},
        {Value: note}
    ]
}
{
  content
    @Core.ContentDisposition: { Filename: fileName }
    @Core.Immutable
}
annotate Attachments with @Common: {SideEffects #ContentChanged: {
    SourceProperties: [content],
    TargetProperties: ['status']
}} {};
