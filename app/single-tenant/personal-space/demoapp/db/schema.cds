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
cHapters : Composition of many Chapters on cHapters.book = $self;

// top-level pages composition (same pattern as chapters)
pages : Composition of many Pages on pages.book = $self;

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

entity Chapters : cuid, managed {
  book        : Association to Books;
  title       : String @title: 'Chapter Title';
  description : String;
  url         : String;
  chapterType : String @title: 'Chapter Type';
  sections    : Composition of many Sections on sections.chapter = $self;
}

entity Sections : cuid, managed {
  chapter     : Association to Chapters;
  title       : String @title: 'Section Title';
  content     : String;
  subSections : Composition of many SubSections on subSections.section = $self;
}

entity SubSections : cuid, managed {
  section    : Association to Sections;
  title      : String @title: 'SubSection Title';
  content    : String;
  paragraphs : Composition of many Paragraphs on paragraphs.subSection = $self;
}

entity Paragraphs : cuid, managed {
  subSection : Association to SubSections;
  title      : String @title: 'Paragraph Title';
  content    : String;
  lines      : Composition of many Lines on lines.paragraph = $self;
}

entity Lines : cuid, managed {
  paragraph  : Association to Paragraphs;
  title      : String @title: 'Line Title';
  content    : String;
  subLines   : Composition of many SubLines on subLines.line = $self;
}

entity SubLines : cuid, managed {
  line    : Association to Lines;
  title   : String @title: 'SubLine Title';
  content : String;
}

/** Adding {Notebooks,Writers} for user service */
entity Notebooks : managed, cuid {
  @mandatory title  : localized String(111);
  descr             : localized String(1111);
  @mandatory writer : Association to Writers;
  stock             : Integer;
  price             : Decimal;
  currency          : Currency;
  image             : LargeBinary @Core.MediaType: 'image/png';
}

entity Pages : cuid, managed {
  book       : Association to Books;
  title          : String @title: 'Page Title';
  description    : String;
  url            : String;
  pageType  : String @title: 'Page Type';
}

entity Writers : managed, cuid {
  @mandatory name : String(111);
  dateOfBirth     : Date;
  dateOfDeath     : Date;
  placeOfBirth    : String;
  placeOfDeath    : String;
  notebooks       : Association to many Notebooks
                      on notebooks.writer = $self;
}

