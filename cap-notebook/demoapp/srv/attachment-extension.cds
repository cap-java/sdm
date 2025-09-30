using {sap.capire.bookshop.Books, sap.capire.bookshop.Chapters} from '../db/schema';
using {sap.attachments.Attachments, sap.attachments.StatusCode} from`com.sap.cds/sdm`;
using {sap,managed,sap.common.CodeList} from '@sap/cds/common';

// keep the original shallow attachments on Books
extend entity Books with {
  attachments : Composition of many Attachments;
  //references: Composition of many Attachments;
  footnotes: Composition of many Attachments;
}

// Add attachments to Sections
extend entity sap.capire.bookshop.Sections with {
  attachments : Composition of many Attachments;
  //references: Composition of many Attachments;
  footnotes: Composition of many Attachments;
}

extend entity Chapters with { attachments: Composition of many Attachments }

entity Statuses @cds.autoexpose @readonly {
  key code : StatusCode;
  text     : localized String(255);
}

extend Attachments with {
  statusText : Association to Statuses on statusText.code = $self.status;
}

annotate Books.attachments with {
  status @(
    Common.Text: {
      $value: ![statusText.text],
      ![@UI.TextArrangement]: #TextOnly
    },
    ValueList: { entity: 'Statuses' },
    sap.value.list: 'fixed-values'
  );
}

annotate sap.capire.bookshop.Sections.attachments with {
  status @(
    Common.Text: {
      $value: ![statusText.text],
      ![@UI.TextArrangement]: #TextOnly
    },
    ValueList: { entity: 'Statuses' },
    sap.value.list: 'fixed-values'
  );
}
