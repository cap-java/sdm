/*
 Common Annotations shared by all apps
*/

using { sap.capire.bookshop as my } from '../db/schema';
using { sap.common, sap.common.Currencies } from '@sap/cds/common';

////////////////////////////////////////////////////////////////////////////
//
//	Books Lists
//
annotate my.Books with @(
  Common.SemanticKey: [ID],
  UI: {
    Identification: [{ Value: title }],
    SelectionFields: [
      ID,
      author_ID,
      price,
      currency_code
    ],
    LineItem: [
      { Value: ID, Label: '{i18n>Title}' },
      { Value: author.ID, Label: '{i18n>Author}' },
      { Value: genre.name },
      { Value: stock },
      { Value: price },
      { Value: currency.symbol },
    ]
  }
) {
  ID @Common: {
    SemanticObject: 'Books',
    Text: title,
    TextArrangement: #TextOnly
  };
  author @ValueList.entity: 'Authors';
};

annotate Currencies with {
  symbol @Common.Label: '{i18n>Currency}';
}

////////////////////////////////////////////////////////////////////////////
//
//	Books Details
//
annotate my.Books with @(UI : {HeaderInfo : {
  TypeName      : '{i18n>Book}',
  TypeNamePlural: '{i18n>Books}',
  Title         : { Value: title },
  Description   : { Value: author.name }
}, });

////////////////////////////////////////////////////////////////////////////
//
//	Attachments Details
//

annotate my.Books.attachments with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Attachment}',
    TypeNamePlural: '{i18n>Attachments}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Attachments',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}
annotate Attachments with @Common: {SideEffects #ContentChanged: {
  SourceProperties: [content],
  TargetProperties: ['status'],
  TargetEntities : [Books.attachments]
}}{};

annotate my.Books.references with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Attachment}',
    TypeNamePlural: '{i18n>Attachments}',
  },
  LineItem  : [
     {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Attachments',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Books.footnotes with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Attachment}',
    TypeNamePlural: '{i18n>Attachments}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Attachments',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Chapters.attachments with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Attachment}',
    TypeNamePlural: '{i18n>Attachments}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Attachments',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Chapters.references with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Reference}',
    TypeNamePlural: '{i18n>References}',
  },
  LineItem  : [
     {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy References',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Chapters.footnotes with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Footnote}',
    TypeNamePlural: '{i18n>Footnotes}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Footnotes',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Pages.attachments with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Attachment}',
    TypeNamePlural: '{i18n>Attachments}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Attachments',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Pages.references with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Reference}',
    TypeNamePlural: '{i18n>References}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy References',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');  
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

annotate my.Pages.footnotes with @UI: {
  HeaderInfo: {
    $Type         : 'UI.HeaderInfoType',
    TypeName      : '{i18n>Footnote}',
    TypeNamePlural: '{i18n>Footnotes}',
  },
  LineItem  : [
    {Value: type, @HTML5.CssDefaults: {width: '10%'}},
    {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
    {Value: content, @HTML5.CssDefaults: {width: '0%'}},
    {Value: createdAt, @HTML5.CssDefaults: {width: '15%'}},
    {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
    {Value: note, @HTML5.CssDefaults: {width: '20%'}},
    {
        Value             : uploadStatus,
        Criticality: uploadStatusNav.criticality,
        @Common.FieldControl: #ReadOnly,
        @HTML5.CssDefaults: {width: '20%'}      },
    {
      $Type : 'UI.DataFieldForAction',
      Label : 'Copy Footnotes',
      Action: 'AdminService.copyAttachments',
    },
    {
      $Type  : 'UI.DataFieldForActionGroup',
      ID     : 'TableActionGroup',
      Label  : 'Create',
      ![@UI.Hidden]: {$edmJson: {$Eq: [ {$Path: 'IsActiveEntity'}, true ]}},
      Actions: [
        {
          $Type : 'UI.DataFieldForAction',
          Label : 'Link',
          Action: 'AdminService.createLink'
        }
      ]
    },
    {
      @UI.Hidden: {$edmJson: {
          $If: [
            { $Eq: [ { $Path: 'IsActiveEntity' }, true ] },
            true,
            {
              $If: [
                { $Ne: [ { $Path: 'mimeType' }, 'application/internet-shortcut' ] },
                true,
                false
              ]
            }
          ]
        }
      },
      $Type : 'UI.DataFieldForAction',
      Label : 'Edit Link',
      Action: 'AdminService.editLink',
      Inline: true,
      IconUrl: 'sap-icon://edit',
      @HTML5.CssDefaults: {width: '4%'}         
    }
  ],
} 
{
  note       @(title: '{i18n>Note}');
  fileName  @(title: '{i18n>Filename}');
  modifiedAt @(odata.etag: null);
  content
    @Core.ContentDisposition: { Filename: fileName }
    @(title: '{i18n>Attachment}');
  uploadStatus    @(title: '{i18n>uploadStatus}', Common.Text : uploadStatusNav.name, Common.TextArrangement : #TextOnly);
  type  @(title: '{i18n>type}');    
  folderId @UI.Hidden;
  repositoryId  @UI.Hidden ;
  objectId  @UI.Hidden ;
  mimeType @UI.Hidden;
  status @UI.Hidden;
}

////////////////////////////////////////////////////////////////////////////
//
//	Books Elements
//
annotate my.Books with {
  ID     @title: '{i18n>ID}';
  title  @title: '{i18n>Title}';
  genre  @title: '{i18n>Genre}'   @Common: { Text: genre.name, TextArrangement: #TextOnly };
  author @title: '{i18n>Author}'  @Common: { Text: author.name, TextArrangement: #TextOnly };
  price  @title: '{i18n>Price}'   @Measures.ISOCurrency: currency_code;
  stock  @title: '{i18n>Stock}';
  descr  @title: '{i18n>Description}' @UI.MultiLineText;
  image  @title: '{i18n>Image}';
}

////////////////////////////////////////////////////////////////////////////
//
//	Genres List
//
annotate my.Genres with @(
  Common.SemanticKey: [name],
  UI: {
    SelectionFields: [name],
    LineItem: [
      { Value: name },
      {
        Value: parent.name,
        Label: 'Main Genre'
      },
    ],
  }
);

annotate my.Genres with {
  ID  @Common.Text : name  @Common.TextArrangement : #TextOnly;
}

////////////////////////////////////////////////////////////////////////////
//
//	Genre Details
//
annotate my.Genres with @(UI : {
  Identification: [{ Value: name}],
  HeaderInfo: {
    TypeName      : '{i18n>Genre}',
    TypeNamePlural: '{i18n>Genres}',
    Title         : { Value: name },
    Description   : { Value: ID }
  },
  Facets: [{
    $Type : 'UI.ReferenceFacet',
    Label : '{i18n>SubGenres}',
    Target: 'children/@UI.LineItem'
  }, ],
});

////////////////////////////////////////////////////////////////////////////
//
//	Genres Elements
//
annotate my.Genres with {
  ID   @title: '{i18n>ID}';
  name @title: '{i18n>Genre}';
}

////////////////////////////////////////////////////////////////////////////
//
//	Authors List
//
annotate my.Authors with @(
  Common.SemanticKey: [ID],
  UI: {
    Identification : [{ Value: name}],
    SelectionFields: [ name ],
    LineItem       : [
      { Value: ID },
      { Value: dateOfBirth },
      { Value: dateOfDeath },
      { Value: placeOfBirth },
      { Value: placeOfDeath },
    ],
  }
) {
  ID @Common: {
    SemanticObject: 'Authors',
    Text: name,
    TextArrangement: #TextOnly,
  };
};

////////////////////////////////////////////////////////////////////////////
//
//	Author Details
//
annotate my.Authors with @(UI : {
  HeaderInfo: {
    TypeName      : '{i18n>Author}',
    TypeNamePlural: '{i18n>Authors}',
    Title         : { Value: name },
    Description   : { Value: dateOfBirth }
  },
  Facets: [{
    $Type : 'UI.ReferenceFacet',
    Target: 'books/@UI.LineItem'
  }],
});


////////////////////////////////////////////////////////////////////////////
//
//	Authors Elements
//
annotate my.Authors with {
  ID           @title: '{i18n>ID}';
  name         @title: '{i18n>Name}';
  dateOfBirth  @title: '{i18n>DateOfBirth}';
  dateOfDeath  @title: '{i18n>DateOfDeath}';
  placeOfBirth @title: '{i18n>PlaceOfBirth}';
  placeOfDeath @title: '{i18n>PlaceOfDeath}';
}

////////////////////////////////////////////////////////////////////////////
//
//	Languages List
//
annotate common.Languages with @(
  Common.SemanticKey: [code],
  Identification: [{ Value: code }],
  UI: {
    SelectionFields: [ name, descr ],
    LineItem: [
      { Value: code },
      { Value: name },
    ],
  }
);

////////////////////////////////////////////////////////////////////////////
//
//	Language Details
//
annotate common.Languages with @(UI : {
  HeaderInfo: {
    TypeName      : '{i18n>Language}',
    TypeNamePlural: '{i18n>Languages}',
    Title         : { Value: name },
    Description   : { Value: descr }
  },
  Facets: [{
    $Type : 'UI.ReferenceFacet',
    Label : '{i18n>Details}',
    Target: '@UI.FieldGroup#Details'
  }, ],
  FieldGroup #Details: {Data : [
    { Value: code },
    { Value: name },
    { Value: descr }
  ]},
});

////////////////////////////////////////////////////////////////////////////
//
//	Currencies List
//
annotate common.Currencies with @(
  Common.SemanticKey: [code],
  Identification: [{ Value: code}],
  UI: {
    SelectionFields: [
      name,
      descr
    ],
    LineItem: [
      { Value: descr },
      { Value: symbol },
      { Value: code },
    ],
  }
);

////////////////////////////////////////////////////////////////////////////
//
//	Currency Details
//
annotate common.Currencies with @(UI : {
  HeaderInfo: {
    TypeName      : '{i18n>Currency}',
    TypeNamePlural: '{i18n>Currencies}',
    Title         : { Value: descr },
    Description   : { Value: code }
  },
  Facets: [
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Details}',
      Target: '@UI.FieldGroup#Details'
    }
  ],
  FieldGroup #Details: {Data : [
    { Value: name },
    { Value: symbol },
    { Value: code },
    { Value: descr }
  ]}
});
