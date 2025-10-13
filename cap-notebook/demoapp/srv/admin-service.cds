using { sap.capire.bookshop as my } from '../db/schema';

service AdminService @(requires: 'admin') {

  entity Books as projection on my.Books;
  entity Authors as projection on my.Authors;
  entity Chapters as projection on my.Chapters;
  entity Pages as projection on my.Pages;

  entity Books.attachments as projection on my.Books.attachments
    actions {
    // Table-level actions
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  entity Books.references as projection on my.Books.references
    actions {
    // Table-level actions
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  entity Pages.attachments as projection on my.Pages.attachments
    actions {
    // Table-level actions
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  entity Pages.references as projection on my.Pages.references
    actions {
    // Table-level actions
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  // Chapters projections
  entity Chapters.attachments as projection on my.Chapters.attachments
    actions {
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  entity Chapters.references as projection on my.Chapters.references
    actions {
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  entity Chapters.footnotes as projection on my.Chapters.footnotes
    actions {
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };

  // Pages footnotes projection
  entity Pages.footnotes as projection on my.Pages.footnotes
    actions {
    @(Common.SideEffects : { TargetEntities: [''] })
    action copyAttachments(in: many $self, up__ID: String, objectIds: String);
  };
}
