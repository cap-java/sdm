using { sap.capire.bookshop as my } from '../db/schema';

service AdminService @(requires: 'admin') {

  entity Books as projection on my.Books;
  entity Authors as projection on my.Authors;
  entity Chapters as projection on my.Chapters;

  entity Books.attachments as projection on my.Books.attachments
    actions {
    // Table-level actions
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);

    @(Common.SideEffects : { TargetEntities: [''] })
    action createLink(
      in: many $self,
      @mandatory @assert.unique @placeholder: 'Enter name' name: String,
      @mandatory @placeholder: 'Enter URL' url: String
    );

    // Row-level actions
    action editLink(in: $self, url: String);
    action openAttachment() returns String;
  };
}
