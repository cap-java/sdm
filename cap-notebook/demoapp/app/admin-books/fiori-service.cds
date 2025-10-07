using {AdminService} from '../../srv/admin-service.cds';

////////////////////////////////////////////////////////////////////////////
//
//	Books Object Page
//
annotate AdminService.Books with @(UI: {
  Facets             : [
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>General}',
      Target: '@UI.FieldGroup#General'
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Translations}',
      Target: 'texts/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Details}',
      Target: '@UI.FieldGroup#Details'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'AttachmentsFacet',
      Label : '{i18n>attachments}',
      Target: 'attachments/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'ReferencesFacet',
      Label : '{i18n>references}',
      Target: 'references/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'FootnotesFacet',
      Label : 'Footnotes',
      Target: 'footnotes/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Admin}',
      Target: '@UI.FieldGroup#Admin'
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Chapters}',
      ID : 'i18nChapters',
      Target : 'chapters/@UI.LineItem#i18nChapters',
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Pages}',
      ID : 'i18nPages',
      Target : 'pages/@UI.LineItem#i18nPages',
    },
  ],
  FieldGroup #General: {Data: [
    {Value: title},
    {Value: author_ID},
    {Value: genre_ID},
    {Value: descr},
  ]},
  FieldGroup #Details: {Data: [
    {Value: stock},
    {Value: price},
    {
      Value: currency_code,
      Label: '{i18n>Currency}'
    },
  ]},
  FieldGroup #Admin  : {Data: [
    {Value: createdBy},
    {Value: createdAt},
    {Value: modifiedBy},
    {Value: modifiedAt}
  ]}
});

//////////

// Chapters annotations
annotate AdminService.Chapters with @title : '{i18n>Chapter}';
 
annotate AdminService.Books.chapters with @(
    title : '{i18n>Chapters}'
);
 
annotate AdminService.Chapters with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : '{i18n>ChapterTitle}',
        },
        {
            $Type : 'UI.DataField',
            Value : chapterType,
            Label : '{i18n>ChapterType}',
        },
        {
            $Type : 'UI.DataField',
            Value : description,
            Label : '{i18n>Description}',
        },
    ],
    UI.LineItem #i18nChapters : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : '{i18n>ChapterTitle}',
        },
        {
            $Type : 'UI.DataField',
            Value : chapterType,
            Label : '{i18n>ChapterType}',
        },
        {
            $Type : 'UI.DataField',
            Value : description,
            Label : '{i18n>Description}',
        },
    ]
);
 
annotate AdminService.Chapters with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : '{i18n>Chapter}',
        TypeNamePlural : '{i18n>Chapters}',
        Description : {
            $Type : 'UI.DataField',
            Value : description,
        },
    }
);
 
annotate AdminService.Chapters with @(
    UI.FieldGroup #GeneratedGroup1 : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : '{i18n>ChapterTitle}',
            },
            {
                $Type : 'UI.DataField',
                Value : chapterType,
                Label : '{i18n>ChapterType}',
            },
            {
                $Type : 'UI.DataField',
                Value : description,
                Label : '{i18n>Description}',
            },
            {
                $Type : 'UI.DataField',
                Value : url,
                Label : '{i18n>URL}',
            },
        ],
    },
  UI.Facets : [
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'GeneratedFacet1',
      Label : '{i18n>GeneralInformation}',
      Target : '@UI.FieldGroup#GeneratedGroup1',
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'AttachmentsFacet',
      Label : '{i18n>attachments}',
      Target : 'attachments/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'ReferencesFacet',
      Label : '{i18n>references}',
      Target : 'references/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'FootnotesFacet',
      Label : '{i18n>Footnotes}',
      Target : 'footnotes/@UI.LineItem'
    }
  ]
);

//////////

// Pages annotations
annotate AdminService.Pages with @title : '{i18n>Page}';
 
annotate AdminService.Books.pages with @(
    title : '{i18n>Pages}'
);
 
annotate AdminService.Pages with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : '{i18n>PageTitle}',
        },
        {
            $Type : 'UI.DataField',
            Value : pageType,
            Label : '{i18n>PageType}',
        },
        {
            $Type : 'UI.DataField',
            Value : description,
            Label : '{i18n>Description}',
        },
    ],
    UI.LineItem #i18nPages : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : '{i18n>PageTitle}',
        },
        {
            $Type : 'UI.DataField',
            Value : pageType,
            Label : '{i18n>PageType}',
        },
        {
            $Type : 'UI.DataField',
            Value : description,
            Label : '{i18n>Description}',
        },
    ]
);
 
annotate AdminService.Pages with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : '{i18n>Page}',
        TypeNamePlural : '{i18n>Pages}',
        Description : {
            $Type : 'UI.DataField',
            Value : description,
        },
    }
);
 
annotate AdminService.Pages with @(
    UI.FieldGroup #GeneratedGroup1 : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : '{i18n>PageTitle}',
            },
            {
                $Type : 'UI.DataField',
                Value : pageType,
                Label : '{i18n>PageType}',
            },
            {
                $Type : 'UI.DataField',
                Value : description,
                Label : '{i18n>Description}',
            },
            {
                $Type : 'UI.DataField',
                Value : url,
                Label : '{i18n>URL}',
            },
        ],
    },
  UI.Facets : [
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'GeneratedFacet1',
      Label : '{i18n>GeneralInformation}',
      Target : '@UI.FieldGroup#GeneratedGroup1',
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'AttachmentsFacet',
      Label : '{i18n>attachments}',
      Target : 'attachments/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'ReferencesFacet',
      Label : '{i18n>references}',
      Target : 'references/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'FootnotesFacet',
      Label : '{i18n>Footnotes}',
      Target : 'footnotes/@UI.LineItem'
    }
  ]
);


////////////////////////////////////////////////////////////
//
//  Draft for Localized Data
//
annotate sap.capire.bookshop.Books with @fiori.draft.enabled;
annotate AdminService.Books with @odata.draft.enabled;

annotate AdminService.Books.texts with @(UI: {
  Identification : [{Value: title}],
  SelectionFields: [
    locale,
    title
  ],
  LineItem       : [
    {
      Value: locale,
      Label: 'Locale'
    },
    {
      Value: title,
      Label: 'Title'
    },
    {
      Value: descr,
      Label: 'Description'
    },
  ]
});

annotate AdminService.Books.texts with {
  ID       @UI.Hidden;
  ID_texts @UI.Hidden;
};

// Add Value Help for Locales
annotate AdminService.Books.texts {
  locale @(
    ValueList.entity: 'Languages',
    Common.ValueListWithFixedValues, //show as drop down, not a dialog
  )
};

// Workaround for Fiori popup for asking user to enter a new UUID on Create
annotate AdminService.Books with {
  ID @Core.Computed;
}

// Show Genre as drop down, not a dialog
annotate AdminService.Books with {
  genre @Common.ValueListWithFixedValues;
}

annotate AdminService.Books.texts with @(UI: {
  Identification : [{Value: title}],
  SelectionFields: [
    locale,
    title
  ],
  LineItem       : [
    {
      Value: locale,
      Label: 'Locale'
    },
    {
      Value: title,
      Label: 'Title'
    },
    {
      Value: descr,
      Label: 'Description'
    },
  ]
});

annotate AdminService.Books.texts with {
  ID       @UI.Hidden;
  ID_texts @UI.Hidden;
};

// Add Value Help for Locales
annotate AdminService.Books.texts {
  locale @(
    ValueList.entity: 'Languages',
    Common.ValueListWithFixedValues, //show as drop down, not a dialog
  )
};

// Workaround for Fiori popup for asking user to enter a new UUID on Create
annotate AdminService.Books with {
  ID @Core.Computed;
}

// Show Genre as drop down, not a dialog
annotate AdminService.Books with {
  genre @Common.ValueListWithFixedValues;
}

annotate AdminService.Books.attachments with {
  customProperty1 @Common.ValueListWithFixedValues;
}
