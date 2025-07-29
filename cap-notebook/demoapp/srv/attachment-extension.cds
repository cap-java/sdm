using {sap.capire.bookshop.Books, sap.capire.bookshop.Notebooks} from '../db/schema';
using {sap.attachments.Attachments, sap.attachments.StatusCode} from`com.sap.cds/sdm`;
using {sap,managed,sap.common.CodeList} from '@sap/cds/common';

extend entity Books with {
    attachments : Composition of many Attachments @SDM.Attachments:{maxCount: 4, maxCountError:'Only 4 attachments allowed.'};
    references  : Composition of many Attachments  @SDM.Attachments:{maxCount: 2, maxCountError:'Only 2 attachments allowed.'};
    footnotes   : Composition of many Attachments;
}
extend entity Notebooks with {
    attachments : Composition of many Attachments @SDM.Attachments:{maxCount: 4, maxCountError:'Only 4 attachments allowed.'};
}
entity Statuses @cds.autoexpose @readonly {
    key code : StatusCode;
        text : localized String(255);
}

extend Attachments with {
    statusText : Association to Statuses on statusText.code = $self.status;
    customProperty1 : WDIRS_CodeList_TYPE
        @SDM.Attachments.AdditionalProperty: {
            name: 'Working:DocumentInfoRecordString'
        } 
        @(title: 'DocumentInfoRecordString');
    customProperty2 : Integer
        @SDM.Attachments.AdditionalProperty: {
            name: 'Working:DocumentInfoRecordInt'
        };
    customProperty3 : String
    @SDM.Attachments.AdditionalProperty: {
        name: 'abc:myId1'
    }  
    @(title: 'id1');
    customProperty4 : String
    @SDM.Attachments.AdditionalProperty: {
        name: 'abc:myId2'
    }  
    @(title: 'id2');
    customProperty5 : DateTime
    @SDM.Attachments.AdditionalProperty: {
        name: 'Working:DocumentInfoRecordDate'
    }  
    @(title: 'DocumentInfoRecordDate');
    customProperty6 : Boolean
    @SDM.Attachments.AdditionalProperty: {
        name: 'Working:DocumentInfoRecordBoolean'
    }  
    @(title: 'DocumentInfoRecordBoolean');
}

entity WDIRSCodeList : CodeList {
    key code  : String(30) @Common.Text : name @Common.TextArrangement: #TextFirst;
};

type WDIRS_CodeList_TYPE : Association to one WDIRSCodeList;

annotate Books.attachments with {
    status @(
        Common.Text: {
            $value: ![statusText.text],
            ![@UI.TextArrangement]: #TextOnly
        },
        ValueList: {entity:'Statuses'}
    );
}
