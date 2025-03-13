using {sap.capire.bookshop.Books} from '../db/schema';
using {sap.attachments.Attachments, sap.attachments.StatusCode} from`com.sap.cds/sdm`;

extend entity Books with {
    attachments : Composition of many Attachments;
}

entity Statuses @cds.autoexpose @readonly {
    key code : StatusCode;
        text : localized String(255);
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
        ValueList: {entity:'Statuses'},
        sap.value.list: 'fixed-values'
    );
}
