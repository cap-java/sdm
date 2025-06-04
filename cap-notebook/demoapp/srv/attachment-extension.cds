using {sap.capire.bookshop.Books, sap.capire.bookshop.Notebooks} from '../db/schema';
using {sap.attachments.Attachments, sap.attachments.StatusCode} from`com.sap.cds/sdm`;
using {sap,managed,sap.common.CodeList} from '@sap/cds/common';

extend entity Books with {
    attachments : Composition of many Attachments @SDM.Attachments:{maxCount: 4, maxCountError:'Only 4 attachments allowed.'};
}

extend entity Notebooks with {
    attachments : Composition of many Attachments @SDM.Attachments:{maxCount: 4, maxCountError:'Only 4 attachments allowed.'};
    references  : Composition of many Attachments @SDM.attachments:{maxCount: 2, maxCountError:'Only 2 attachments allowed.'};
    footnotes   : Composition of many Attachments;
    
}
entity Statuses @cds.autoexpose @readonly {
    key code : StatusCode;
        text : localized String(255);
}

extend Attachments with {
    statusText : Association to Statuses on statusText.code = $self.status;
    Working___DocumentInfoRecordString : String
        @SDM.Attachments.AdditionalProperty: {
            name: 'Working:DocumentInfoRecordString'
        } 
        @(title: 'DocumentInfoRecordString');
    Working___DocumentInfoRecordInt : Integer
        @SDM.Attachments.AdditionalProperty: {
            name: 'Working:DocumentInfoRecordInt'
        };
    abc___myId1 : String
    @SDM.Attachments.AdditionalProperty: {
        name: 'abc:myId1'
    }  
    @(title: 'id1');
    abc___myId2 : String
    @SDM.Attachments.AdditionalProperty: {
        name: 'abc:myId2'
    }  
    @(title: 'id2');
    Working___DocumentInfoRecordDate : DateTime
    @SDM.Attachments.AdditionalProperty: {
        name: 'Working:DocumentInfoRecordDate'
    }  
    @(title: 'DocumentInfoRecordDate');
    Working___DocumentInfoRecordBoolean : Boolean
    @SDM.Attachments.AdditionalProperty: {
        name: 'Working:DocumentInfoRecordBoolean'
    }  
    @(title: 'DocumentInfoRecordBoolean');
}

annotate Books.attachments with {
    status @(
        Common.Text: {
            $value: ![statusText.text],
            ![@UI.TextArrangement]: #TextOnly
        },
        ValueList: {entity:'Statuses'}
    );
}
