using {sap.capire.bookshop as my} from '../db/schema';

service UserService @(requires: [
    'admin',
    'system-user'
]) {
    @odata.draft.enabled
    entity Notebooks as projection on my.Notebooks;
    entity Writers   as projection on my.Writers;

    entity Notebooks.attachments as projection on my.Notebooks.attachments
        actions {
            @(Common.SideEffects : {TargetEntities: ['']},)
            action copyAttachments(in:many $self, up__ID:String, objectIds:String);
        };
}
