namespace my.bookshop;

using {
    cuid
} from '@sap/cds/common';

entity Repository : cuid {
    repositoryId        : localized String(111);
    externalId        : localized String(1111);
    name       : localized String(1111);
}