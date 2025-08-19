using {sap.capire.bookshop as my} from '../db/schema';

service AdminService @(requires: ['admin','system-user']) {
  entity Books as projection on my.Books;
  entity Authors as projection on my.Authors;
  entity Books.attachments as projection on my.Books.attachments
  actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter name',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Enter URL'
    ); 
    
    action editLink(url:String);     
    action openAttachment() returns String;
  }

  entity Books.references as projection on my.Books.references
  actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter name',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Enter URL'
    ); 
    
    action editLink(url:String);     
    action openAttachment() returns String;
  }
  
  entity Books.footnotes as projection on my.Books.footnotes
  actions {
    @(Common.SideEffects : {TargetEntities: ['']},)
    action copyAttachments(in:many $self, up__ID:String, objectIds:String);

    @(Common.SideEffects : {TargetEntities: ['']},)
    action createLink(
      in:many $self,
      @mandatory @Common.Label:'Name' name: String @UI.Placeholder: 'Enter name',
      @mandatory @assert.format:'^(https?:\/\/)(([a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}|localhost)(:\d{2,5})?(\/[^\s]*)?$' @Common.Label:'URL' url: String @UI.Placeholder: 'Enter URL'
    ); 
    
    action editLink(url:String);     
    action openAttachment() returns String;
  }
}
