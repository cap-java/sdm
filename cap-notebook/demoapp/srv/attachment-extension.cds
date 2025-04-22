using {sap.capire.bookshop.Books} from '../db/schema';
using {sap.attachments.Attachments, sap.attachments.StatusCode} from`com.sap.cds/sdm`;

extend entity Books with {
    attachments : Composition of many Attachments @SDM.Attachments:{maxCount: 4, maxCountError:'Only 4 attachments allowed.'};
}

// extend entity Books with {
//     references : Composition of many Attachments;
// }

entity Statuses @cds.autoexpose @readonly {
    key code : StatusCode;
        text : localized String(255);
}

extend Attachments with {
    statusText : Association to Statuses on statusText.code = $self.status;
    abc___myId1 : String @SDM.Attachments.AdditionalProperty @(title: '{i18n>id1}');
    abc___myId2 : String @SDM.Attachments.AdditionalProperty @(title: '{i18n>id2}');
    Working___DocumentInfoRecordString : String @SDM.Attachments.AdditionalProperty @(title: '{i18n>DocumentInfoRecordString}');
    Working___DocumentInfoRecordInt : Integer @SDM.Attachments.AdditionalProperty @(title: '{i18n>DocumentInfoRecordInt}');
    Working___DocumentInfoRecordBoolean : Boolean @SDM.Attachments.AdditionalProperty @(title: '{i18n>DocumentInfoRecordBoolean}');
    Working___DocumentInfoRecordDate : DateTime @SDM.Attachments.AdditionalProperty @(title: '{i18n>DocumentInfoRecordDate}');
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
