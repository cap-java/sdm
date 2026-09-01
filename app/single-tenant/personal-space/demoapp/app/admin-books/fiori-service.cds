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
      Target : 'cHapters/@UI.LineItem#i18nChapters',
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
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID : 'SectionsFacet',
      Label : 'Sections',
      Target : 'sections/@UI.LineItem#Sections',
    }
  ]
);

//////////

// Sections annotations
annotate AdminService.Sections with @title : 'Section';

annotate AdminService.Sections with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'Section Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ],
    UI.LineItem #Sections : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'Section Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ]
);

annotate AdminService.Sections with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : 'Section',
        TypeNamePlural : 'Sections',
        Description : {
            $Type : 'UI.DataField',
            Value : content,
        },
    }
);

annotate AdminService.Sections with @(
    UI.FieldGroup #SectionGeneral : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : 'Section Title',
            },
            {
                $Type : 'UI.DataField',
                Value : content,
                Label : 'Content',
            },
        ],
    },
    UI.Facets : [
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SectionGeneralFacet',
            Label : 'General Information',
            Target : '@UI.FieldGroup#SectionGeneral',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SectionAttachmentsFacet',
            Label : 'Attachments',
            Target : 'attachments/@UI.LineItem',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SubSectionsFacet',
            Label : 'SubSections',
            Target : 'subSections/@UI.LineItem#SubSections',
        }
    ]
);

//////////

// SubSections annotations
annotate AdminService.SubSections with @title : 'SubSection';

annotate AdminService.SubSections with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'SubSection Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ],
    UI.LineItem #SubSections : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'SubSection Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ]
);

annotate AdminService.SubSections with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : 'SubSection',
        TypeNamePlural : 'SubSections',
        Description : {
            $Type : 'UI.DataField',
            Value : content,
        },
    }
);

annotate AdminService.SubSections with @(
    UI.FieldGroup #SubSectionGeneral : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : 'SubSection Title',
            },
            {
                $Type : 'UI.DataField',
                Value : content,
                Label : 'Content',
            },
        ],
    },
    UI.Facets : [
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SubSectionGeneralFacet',
            Label : 'General Information',
            Target : '@UI.FieldGroup#SubSectionGeneral',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SubSectionAttachmentsFacet',
            Label : 'Attachments',
            Target : 'attachments/@UI.LineItem',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'ParagraphsFacet',
            Label : 'Paragraphs',
            Target : 'paragraphs/@UI.LineItem#Paragraphs',
        }
    ]
);

//////////

// Paragraphs annotations
annotate AdminService.Paragraphs with @title : 'Paragraph';

annotate AdminService.Paragraphs with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'Paragraph Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ],
    UI.LineItem #Paragraphs : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'Paragraph Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ]
);

annotate AdminService.Paragraphs with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : 'Paragraph',
        TypeNamePlural : 'Paragraphs',
        Description : {
            $Type : 'UI.DataField',
            Value : content,
        },
    }
);

annotate AdminService.Paragraphs with @(
    UI.FieldGroup #ParagraphGeneral : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : 'Paragraph Title',
            },
            {
                $Type : 'UI.DataField',
                Value : content,
                Label : 'Content',
            },
        ],
    },
    UI.Facets : [
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'ParagraphGeneralFacet',
            Label : 'General Information',
            Target : '@UI.FieldGroup#ParagraphGeneral',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'ParagraphAttachmentsFacet',
            Label : 'Attachments',
            Target : 'attachments/@UI.LineItem',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'LinesFacet',
            Label : 'Lines',
            Target : 'lines/@UI.LineItem#Lines',
        }
    ]
);

//////////

// Lines annotations
annotate AdminService.Lines with @title : 'Line';

annotate AdminService.Lines with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'Line Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ],
    UI.LineItem #Lines : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'Line Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ]
);

annotate AdminService.Lines with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : 'Line',
        TypeNamePlural : 'Lines',
        Description : {
            $Type : 'UI.DataField',
            Value : content,
        },
    }
);

annotate AdminService.Lines with @(
    UI.FieldGroup #LineGeneral : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : 'Line Title',
            },
            {
                $Type : 'UI.DataField',
                Value : content,
                Label : 'Content',
            },
        ],
    },
    UI.Facets : [
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'LineGeneralFacet',
            Label : 'General Information',
            Target : '@UI.FieldGroup#LineGeneral',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'LineAttachmentsFacet',
            Label : 'Attachments',
            Target : 'attachments/@UI.LineItem',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SubLinesFacet',
            Label : 'SubLines',
            Target : 'subLines/@UI.LineItem#SubLines',
        }
    ]
);

//////////

// SubLines annotations
annotate AdminService.SubLines with @title : 'SubLine';

annotate AdminService.SubLines with @(
    UI.LineItem : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'SubLine Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ],
    UI.LineItem #SubLines : [
        {
            $Type : 'UI.DataField',
            Value : title,
            Label : 'SubLine Title',
        },
        {
            $Type : 'UI.DataField',
            Value : content,
            Label : 'Content',
        },
    ]
);

annotate AdminService.SubLines with @(
    UI.HeaderInfo : {
        Title : {
            $Type : 'UI.DataField',
            Value : title,
        },
        TypeName : 'SubLine',
        TypeNamePlural : 'SubLines',
        Description : {
            $Type : 'UI.DataField',
            Value : content,
        },
    }
);

annotate AdminService.SubLines with @(
    UI.FieldGroup #SubLineGeneral : {
        $Type : 'UI.FieldGroupType',
        Data : [
            {
                $Type : 'UI.DataField',
                Value : title,
                Label : 'SubLine Title',
            },
            {
                $Type : 'UI.DataField',
                Value : content,
                Label : 'Content',
            },
        ],
    },
    UI.Facets : [
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SubLineGeneralFacet',
            Label : 'General Information',
            Target : '@UI.FieldGroup#SubLineGeneral',
        },
        {
            $Type : 'UI.ReferenceFacet',
            ID : 'SubLineAttachmentsFacet',
            Label : 'Attachments',
            Target : 'attachments/@UI.LineItem',
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

// In addition we need to expose Languages through AdminService as a target for ValueList
using {sap} from '@sap/cds/common';

extend service AdminService {
  @readonly
  entity Languages as projection on sap.common.Languages;
}

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
