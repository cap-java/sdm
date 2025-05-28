[![REUSE status](https://api.reuse.software/badge/github.com/cap-java/sdm)](https://api.reuse.software/info/github.com/cap-java/sdm)

# CAP plugin for SAP Document Management Service
The `com.sap.cds:sdm` dependency is a [CAP Java plugin](https://cap.cloud.sap/docs/java/building-plugins) that provides an easy CAP-level integration with [SAP Document Management Service](https://discovery-center.cloud.sap/serviceCatalog/document-management-service-integration-option). This package supports handling of attachments(documents) by using an aspect Attachments in SAP Document Management Service.  
This plugin can be consumed by the CAP application deployed on BTP to store their documents in the form of attachments in Document Management Repository.

## Key features

- Create attachment : Provides the capability to upload new attachments.
- Read attachment : Provides the capability to preview attachments.
- Delete attachment : Provides the capability to remove attachments.
- Rename attachment : Provides the capability to rename attachments.
- Virus scanning : Provides the capability to support virus scan for virus scan enabled repositories.
- Draft functionality : Provides the capability of working with draft attachments.
- Display attachments specific to repository: Lists attachments contained in the repository that is configured with the CAP application.
- Maximum allowed uploads: Provides the capability to define the maximum number of uploads allowed for the user.
- Multiple attachment facets: Provides the capability to define multiple attachment facets/sections in the CAP Entity.

## Table of Contents

- [Pre-Requisites](#pre-requisites)
- [Setup](#setup)
- [Deploying and testing the application](#deploying-and-testing-the-application)
- [Use com.sap.cds:sdm dependency](#use-comsapcdssdm-dependency)
- [Support for Multitenancy](#support-for-multitenancy)
- [Support for Custom Properties](#support-for-custom-properties)
- [Support for Maximum allowed uploads](#support-for-maximum-allowed-uploads)
- [Support for Multiple attachment facets](#support-for-multiple-attachment-facets)
- [Known Restrictions](#known-restrictions)
- [Support, Feedback, Contributing](#support-feedback-contributing)
- [Code of Conduct](#code-of-conduct)
- [Licensing](#licensing)

## Pre-Requisites
* Java 17 or higher
* [MTAR builder](https://www.npmjs.com/package/mbt) (`npm install -g mbt`)
* [Cloud Foundry CLI](https://docs.cloudfoundry.org/cf-cli/install-go-cli.html), Install cf-cli and run command `cf install-plugin multiapps`
* UI5 version 1.131.0 or higher

> **cds-services**
>
> The behaviour of clicking attachment and previewing it varies based on the version of cds-services used by the CAP application. 
>
> - For cds-services version >= 3.4.0, clicking on attachment will
>   - open the file in new browser tab, if browser supports the file type.
>   - download the file to the computer, if browser does not support the file type.
>
> - For cds-services version < 3.4.0, clicking on attachment will download the file to the computer
>
> A reference to adding this can be found [here](https://github.com/cap-java/sdm/blob/691c329f4c3c17ae390cfcb2db1ef02650585aee/cap-notebook/demoapp/pom.xml#L20)

## Setup

In this guide, we use the Bookshop sample app in the [deploy branch](https://github.com/cap-java/sdm/tree/deploy) of this repository, to integrate SDM CAP plugin.  Follow the steps in this section for a quick way to deploy and test the plugin without needing to create your own custom CAP application.

### Using the released version
If you want to use the version of SDM CAP plugin released on the central maven repository follow the below steps:

1. Remove the sdm and sdm-root folders from your local .m2 repository. This ensures that the CAP application uses the plugin version from the central Maven repository, as the local .m2 repository is prioritized during the build process.

2. Clone the sdm repository:

```sh
   git clone https://github.com/cap-java/sdm
```

3. Checkout to the branch **deploy**:

```sh
   git checkout deploy
```

4. Navigate to the demoapp folder:

```sh
   cd cap-notebook/demoapp
```

5. Configure the [REPOSITORY_ID](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/mta.yaml#L21) with the repository you want to use for deploying the application. Set the SDM instance name to match the SAP Document Management integration option instance you created in BTP and update this in the mta.yaml file under the [srv module](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/mta.yaml#L31) and the [resources section](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/mta.yaml#L98) values in the **mta.yaml**. 

6. Build the application:

```sh
   mbt build
```
Now the application will pick the released version of the plugin from the central maven repository as the dependency is added in the [pom.xml](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/srv/pom.xml#L18)

7. Log in to Cloud Foundry space:

```sh
   cf login -a <CF-API> -o <ORG-NAME> -s <SPACE-NAME>
```
8. Deploy the application:

```sh
   cf deploy mta_archives/*.mtar
```

### Using the development version
To use a development version of the SDM CAP plugin, follow these steps. This is useful if you want to test changes made in a separate branch of this github repository or use a version not yet released on the central Maven repository.

1. Clone the sdm repository:

```sh
   git clone https://github.com/cap-java/sdm
```
2. Install the plugin in the root folder after switiching to the branch you want to use:

```sh
   mvn clean install
```
The plugin is now added to your local .m2 repository, giving it priority over the version available in the central Maven repository during the application build.

3. Checkout to the branch **deploy**:

```sh
   git checkout deploy
```

4. Navigate to the demoapp folder:

```sh
   cd cap-notebook/demoapp
```

5. Configure the [REPOSITORY_ID](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/mta.yaml#L21) with the repository you want to use for deploying the application. Set the SDM instance name to match the SAP Document Management integration option instance you created in BTP and update this in the mta.yaml file under the [srv module](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/mta.yaml#L31) and the [resources section](https://github.com/cap-java/sdm/blob/4180e501ecd792770174aa4972b06aff54ac139d/cap-notebook/demoapp/mta.yaml#L98) values in the **mta.yaml**. 

6. Build the application:

```sh
   mbt build
```
7. Log in to Cloud Foundry space:

```sh
   cf login -a <CF-API> -o <ORG-NAME> -s <SPACE-NAME>
```
8. Deploy the application:

```sh
   cf deploy mta_archives/*.mtar
```

## Use com.sap.cds:sdm dependency
Follow these steps if you want to integrate the SDM CAP Plugin with your own CAP application. 

1. Add the following dependency in pom.xml in the srv folder
   
   ```xml
   <dependency>
      <groupId>com.sap.cds</groupId>
      <artifactId>sdm</artifactId>
      <version>{version}</version>
   </dependency>
   ```

   To be able to also use the cds models defined in this plugin the `cds-maven-plugin` needs to be used with the
   `resolve` goal to make the cds models available in the project:

   ```xml
   <plugin>
      <groupId>com.sap.cds</groupId>
      <artifactId>cds-maven-plugin</artifactId>
      <version>${cds.services.version}</version>
      <executions>
         <execution>
            <id>cds.resolve</id>
            <goals>
               <goal>resolve</goal>
            </goals>
         </execution>
      </executions>
   </plugin>
   ```

   If the cds models needs to be used in the `db` folder the `cds-maven-plugin` needs to be included also in the
   `db` folder of the project.
   This means the `db` folder needs to have a `pom.xml` with the `cds-maven-plugin` included and the `cds-maven-plugin`
   needs to be run.

   If the `cds-maven-plugin` is used correctly and executed the following lines should be visible in the build log:

   ````log
   [INFO] --- cds:3.4.1:resolve (cds.resolve) @ your-project ---
   [INFO] CdsResolveMojo: Extracting models from com.sap.cds:sdm:jar:<latest-version>:compile (<project-folder>)
   [INFO] CdsResolveMojo: Extracting models from com.sap.cds:cds-feature-attachments:jar:1.0.5:compile (<project-folder>)
   ````

   After that the models can be used.
   
2. To use sdm plugin in your CAP application, create an element with an `Attachments` type. Following the [best practice of separation of concerns](https://cap.cloud.sap/docs/guides/domain-modeling#separation-of-concerns), create a separate file _srv/attachment-extension.cds_ and extend your entity with attachments. Refer the following example from a sample Bookshop app:

   ```cds
   using {my.bookshop.Books } from '../db/books';
   using {sap.attachments.Attachments} from`com.sap.cds/sdm`;
   
   extend entity Books with {
      attachments : Composition of many Attachments;
   }
   ```

3. Create a SAP Document Management Integration Option [Service instance and key](https://help.sap.com/docs/document-management-service/sap-document-management-service/creating-service-instance-and-service-key). Bind your CAP application to this SDM instance. Add the details of this instance to the resources section in the `mta.yaml` of your CAP application. Refer the following example from a sample Bookshop app.

   ```yaml
   modules:
      - name: bookshop-srv
      type: java
      path: srv
      requires:
         - name: sdm-di-instance
  
   resources:
      - name: sdm-di-instance
      type: org.cloudfoundry.managed-service
      parameters:
         service: sdm
         service-plan: standard
   ```

4. Using the created SDM instance's credentials from key [onboard a repository](https://help.sap.com/docs/document-management-service/sap-document-management-service/onboarding-repository). In mta.yaml, under properties of the srv module add the repository id. Refer the following example from a sample Bookshop app. Currently only non versioned repositories are supported. 

    ```yaml
    modules:
      - name: bookshop-srv
      type: java
      path: srv
      properties:
            REPOSITORY_ID: <REPO ID>
      requires:
         - name: sdm-di-instance
    ```

5. To allow the application to upload large files, add the connection and request timeouts in mta.yaml under properties of srv and app module. Refer the following example from a sample Bookshop app.

   ```yaml
   modules:
      - name: bookshop-srv
      type: java
      path: srv
      properties:
            REPOSITORY_ID: <REPO ID>
            INCOMING_CONNECTION_TIMEOUT: 3600000
            INCOMING_REQUEST_TIMEOUT: 3600000
            INCOMING_SESSION_TIMEOUT: 3600000
            timeout: 3600000

      - name: demoappjava-app
        type: approuter.nodejs
        path: app
        properties:
            INCOMING_REQUEST_TIMEOUT: 3600000
            INCOMING_SESSION_TIMEOUT: 3600000
            INCOMING_CONNECTION_TIMEOUT: 3600000
        requires:
        - name: srv-api
         group: destinations
         properties:
            timeout: 3600000  
   ```

6. Add the following facet in _fiori-service.cds_ in the _app_ folder. Refer the following [example](https://github.com/cap-java/sdm/blob/16c1b17d521a141ef1b1adfbed1e06c5bf7a980f/cap-notebook/demoapp/app/admin-books/fiori-service.cds#L24) from a sample Bookshop app.

   ```cds
      {
         $Type : 'UI.ReferenceFacet',
         ID     : 'AttachmentsFacet',
         Label : '{i18n>attachments}',
         Target: 'attachments/@UI.LineItem'
      }
   ```

## Deploying and testing the application

1. Log in to Cloud Foundry space:

   ```sh
   cf login -a <CF-API> -o <ORG-NAME> -s <SPACE-NAME>
   ```

2. Build the project by running following command from root folder of your CAP application
   ```sh
   mbt build
   ```
   Above step will generate .mtar file inside mta_archives folder.

3. Deploy the application
   ```sh
   cf deploy mta_archives/*.mtar
   ```

4. Go to your BTP subaccount and launch your application.

5. The `Attachments` type has generated an out-of-the-box Attachments table (see highlighted box) at the bottom of the Object page:

   <img width="1300" alt="Attachments Table" style="border-radius:0.5rem;" src="resources/attachments.png">

6. **Upload a file** by going into Edit mode by using the **Upload** button on the Attachments table. The file is then stored in SAP Document Management Integration Option. We demonstrate this by uploading a TXT file:

   <img width="1300" alt="Upload an attachment" style="border-radius:0.5rem;" src="resources/create.gif">

7. **Open a file** by clicking on the attachment. We demonstrate this by opening the previously uploaded TXT file:

   <img width="1300" alt="Delete an attachment" style="border-radius:0.5rem;" src="resources/read.gif">

8. **Rename a file** by going into Edit mode and setting a new name for the file in the filename field. Then click the **Save** button to have that file renamed in SAP Document Management Integration Option. We demonstrate this by renaming the previously uploaded TXT file: 

   <img width="1300" alt="Delete an attachment" style="border-radius:0.5rem;" src="resources/rename.gif">

9. **Delete a file** by going into Edit mode and selecting the file(s) and by using the **Delete** button on the Attachments table. Then click the **Save** button to have that file deleted from the resource (SAP Document Management Integration Option). We demonstrate this by deleting the previously uploaded TXT file:

   <img width="1300" alt="Delete an attachment" style="border-radius:0.5rem;" src="resources/delete.gif">

## Support for Multitenancy

This plugin provides APIs for onboarding and offboarding of repositories for multitenant CAP SaaS applications. Refer the below example where onboarding and offboarding APIs are used on tenant subscription and tenant unsubscription events of SaaS application.
  
```java
@After(event = DeploymentService.EVENT_SUBSCRIBE)
public void onSubscribe(SubscribeEventContext context) {
   final SaasRegistrySubscriptionOptions options = Struct
      .access(context.getOptions())
      .as(SaasRegistrySubscriptionOptions.class);
   final String subdomain = options.getSubscribedSubdomain();

   // Create repository instance and initialise params
   Repository repository = new Repository();
   repository.setDescription("Onboarding Repo Demo");
   repository.setDisplayName(" Test Onboarding repo");
   repository.setSubdomain(subdomain);

   // Using SDMAdminServiceImpl onboardRepository() to onboard repository
   SDMAdminService sdmAdminService =  new SDMAdminServiceImpl();
   String response = sdmAdminService.onboardRepository(repository);
}
 ```

 ```java
 @After(event = DeploymentService.EVENT_UNSUBSCRIBE)
 public void afterUnsubscribe(UnsubscribeEventContext context) {
     //delete onboarded repository
         final SaasRegistrySubscriptionOptions options = Struct
        .access(context.getOptions())
        .as(SaasRegistrySubscriptionOptions.class);
 // Access the specific property
 final String subdomain = options.getSubscribedSubdomain();
 
 SDMAdminService sdmAdminService =  new SDMAdminServiceImpl();
 String res = sdmAdminService.offboardRepository(subdomain);
 }
 ```
When the application is deployed as a SaaS application with above code, a repository is onboarded automatically when a tenant subscribes the SaaS application. The same repository is deleted when the tenant unsubscribes from the SaaS application.
The necessary params for the Repository onboarding can be found in the [documentation](https://help.sap.com/docs/document-management-service/sap-document-management-service/internal-repository).

## Support for Custom Properties

Custom properties are supported via the usage of CMIS secondary type properties. Follow the below steps to add and use custom properties.

1. If the repository does not contain secondary types and properties, create CMIS secondary types and properties using the [Create Secondary Type API](https://api.sap.com/api/CreateSecondaryTypeApi/overview). The property definition must contain the following section for the CAP plugin to process the property.

   ```json
   "mcm:miscellaneous": {        
      "isPartOfTable": "true"  
   } 
   ```

   With this, the secondary type and properties definition will be as per the sample given below

      ```json
      {
         "id": "Working:DocumentInfo",
         "displayName": "Document Info",
         "baseId": "cmis:secondary",
         "parentId": "cmis:secondary",
         ...
         },
         "propertyDefinitions": {
            "Working:DocumentInfoRecord": {
                  "id": "Working:DocumentInfoRecord",
                  "displayName": "Document Info Record",
                  ...
                  "mcm:miscellaneous": {     <-- Required section in the property definition
                     "isPartOfTable": "true"
                  }
            }
         }
      }
      ```

2. Using secondary properties in CAP Application.
   - Extend the `Attachments` aspect with the secondary properties in the previously created _attachment-extension.cds_ file. 
   - Annotate the secondary properties with `@SDM.Attachments.AdditionalProperty`. 
   - If the property id contains a `:`, replace it with a triple underscore `___`. 
   
   Refer the following example from a sample Bookshop app:

      ```cds
      extend Attachments with {
         Working___DocumentInfoRecord : String @SDM.Attachments.AdditionalProperty @(title: '{i18n>property1}');
      }
      ```

   > **Note**
   >
   > SDM supports secondary properties with data types `String`, `Boolean`, `Decimal`, `Integer` and `DateTime`. 

## Support for Maximum allowed uploads
This plugin allows you to customize the maximum number of uploads a user can perform. Once a user exceeds the defined limit, any further upload attempts will trigger an error. The error message shown to the user is also fully customizable. The annotation `@SDM.Attachments` should be used for defining the maximum upload limit and the error message.

Refer the following example from a sample Bookshop app:
-  maxCount: Specifies the maximum number of documents a user is allowed to upload.
-  maxCountError: Defines the error message displayed when the upload limit (maxCount) is exceeded.

```cds
  extend entity Books with {
    attachments : Composition of many Attachments @SDM.Attachments:{maxCount: 4, maxCountError:'Only 4 attachments allowed.'};
    }
   
   ``` 
> **Note**
>
> Once the maxCount is configured, it is recommended not to alter it. If the maxCount is altered, the previously uploaded documents will still be visible.

## Support for Multiple attachment facets
The plugin supports creating multiple attachment facets or sections, each allowing various documents to be uploaded. The names of these facets are fully customizable. All existing operations available for the default attachment facet are also supported for any additional facets you create.

Refer the following example from a sample Bookshop app,

- attachments: Will create a section named attachments on UI.
- references: Will create a section named references on UI.
- footnotes: Will create a section named footnotes on UI.
```cds
   extend entity Books with {
    attachments : Composition of many Attachments;
    references : Composition of many Attachments;
    footnotes : Composition of many Attachments;
}
```
Add the following facet in _fiori-service.cds_ in the _app_ folder. Refer the following [example](https://github.com/cap-java/sdm/blob/develop_deploy/cap-notebook/demoapp/app/admin-books/fiori-service.cds) from a sample Bookshop app.
```cds
{
      $Type : 'UI.ReferenceFacet',
      ID    : 'AttachmentsFacet',
      Label : '{i18n>attachments}',
      Target: 'attachments/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'ReferencesFacet',
      Label : 'References',
      Target: 'references/@UI.LineItem'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'FootnotesFacet',
      Label : 'Footnotes',
      Target: 'footnotes/@UI.LineItem'
    }
    
  ``` 
> **Note**
>
> Once a facet or section name is defined in the CDS file, it is strongly recommended not to modify it. For instance, in the example provided, section names such as attachments, references, and footnotes should remain unchanged after initial configuration. Renaming these sections will result in the creation of new tables, causing any data associated with the original sections to become inaccessible in the UI.



## Known Restrictions

- UI5 Version 1.135.0: This version causes error in upload of attachments.
- Repository : This plugin does not support the use of versioned repositories.
- File size : If the repository is [onboarded](https://help.sap.com/docs/document-management-service/sap-document-management-service/internal-repository?version=Cloud&locale=en-US) with virus scan enabled for all files, attachments are limited to a maximum size of 400 MB. 
- Datatypes for custom properties : Custom properties are supported for the following data types `String`, `Boolean`, `Decimal`, `Integer` and `DateTime`.  

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/sdm/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2024 SAP SE or an SAP affiliate company and <your-project> contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/cap-java/sdm).

