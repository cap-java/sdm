
using { sap.capire.bookshop as my } from '../db/schema';
service AdminService @(requires: ['admin','system-user']) {

  entity Books as projection on my.Books;
  entity Authors as projection on my.Authors;
  entity Chapters as projection on my.Chapters;
  entity Pages as projection on my.Pages;

  // Define a return type for the action result
  type MoveAttachmentsResult {
      failedObjectIds : array of String;
  }

  entity Books.attachments as projection on my.Books.attachments
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  entity Books.references as projection on my.Books.references
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  entity Books.footnotes as projection on my.Books.footnotes
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  entity Pages.attachments as projection on my.Pages.attachments
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  entity Pages.references as projection on my.Pages.references
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  // Chapters projections
  entity Chapters.attachments as projection on my.Chapters.attachments
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  entity Chapters.references as projection on my.Chapters.references
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  entity Chapters.footnotes as projection on my.Chapters.footnotes
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };

  // Pages footnotes projection
  entity Pages.footnotes as projection on my.Pages.footnotes
    actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action createAttachmentInActive(in:many $self);
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);
    // moveAttachments action signature
    @(Common.SideEffects : {TargetEntities: ['']})
    action moveAttachments(
        in: many $self, 
        up__ID: String, 
        sourceFolderId: String,
        objectIds: String,
        targetFacet: String,
        sourceFacet: String,      // Optional: if not provided, no source cleanup
    ) returns MoveAttachmentsResult;  // Return structured type

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter a name for the link',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    
    action editLink(
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Example: https://www.example.com'
    ); 
    action openAttachment() returns String;
    action changelog() returns String;
    action downloadSelectedAttachments(ids: String) returns String;
  };
}
