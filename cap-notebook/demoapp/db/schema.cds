using {
Currency,
managed,
cuid,
sap.common.CodeList
} from '@sap/cds/common';

namespace sap.capire.bookshop;

entity Books : managed, cuid {
@mandatory title : localized String(111);
descr : localized String(1111);
@mandatory author : Association to Authors;
genre : Association to Genres;
stock : Integer;
price : Decimal;
currency : Currency;
image : LargeBinary @Core.MediaType: 'image/png';

// top-level chapters composition (root of the nested hierarchy)
chapters : Composition of many Chapters on chapters.book = $self;

// keep any other fields as before
}

entity Authors : managed, cuid {
@mandatory name : String(111);
dateOfBirth : Date;
dateOfDeath : Date;
placeOfBirth : String;
placeOfDeath : String;
books : Association to many Books
on books.author = $self;
}

/** Hierarchically organized Code List for Genres */
entity Genres : CodeList {
key ID : Integer;
parent : Association to Genres;
children : Composition of many Genres
on children.parent = $self;
}

// --- Nested composition entities to emulate deep structure ---

// entity Chapters : cuid, managed {
// title : String(255);

// // backlink to parent Book
// book : Association to Books;

// // each chapter has many sections
// sections : Composition of many Sections on sections.chapter = $self;
// }

entity Sections : cuid, managed {
title : String(255);

// backlink to parent chapter
chapter : Association to Chapters;

// attachments will be added via attachments-extension.cds so we don't create a hard dependency on SDM in the core schema
// attachments : Composition of many sap.attachments.Attachments; // added by extension
}

entity Chapters : cuid, managed {
  book       : Association to Books;
  title          : String @title: 'Chapter Title';
  description    : String;
  url            : String;
  chapterType  : String @title: 'Chapter Type';
}