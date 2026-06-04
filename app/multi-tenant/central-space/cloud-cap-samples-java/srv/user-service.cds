using {my.bookshop as my} from '../db/books';

service UserService @(requires: [
    'admin',
    'system-user'
]) {
    @odata.draft.enabled
    entity Notebooks as projection on my.Notebooks;
    entity Writers   as projection on my.Writers;
}