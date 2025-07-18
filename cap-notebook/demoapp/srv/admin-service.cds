using {sap.capire.bookshop as my} from '../db/schema';

service AdminService @(requires: ['admin','system-user']) {
  entity Books   as projection on my.Books;
  entity Authors as projection on my.Authors;
  entity Books.attachments as projection on my.Books.attachments
  actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self,up__ID:String,objectIds:String);
  }
}
