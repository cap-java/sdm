[![REUSE status](https://api.reuse.software/badge/github.com/cap-java/sdm)](https://api.reuse.software/info/github.com/cap-java/sdm)

# CAP plugin for SAP Document Management Service
The project is currently in the development stage, where our team is actively working on building and refining its core components to ensure it meets all specified requirements and objectives.

The **@cap-java/sdm** package is [cds-plugin](https://cap.cloud.sap/docs/java/cds-plugins#cds-plugin-packages) that provides an easy CAP-level integration with [SAP Document Management Service](https://discovery-center.cloud.sap/serviceCatalog/document-management-service-integration-option). This package supports handling of attachments(documents) by using an aspect Attachments in SAP Document Management Service.  
This plugin can be consumed by the CAP application deployed on BTP to store their documents in the form of attachments in Document Management Repository.

# Key features

- Create attachment : Provides the capability to upload new attachments.
- Virus scanning : Provides the capability to support virus scan for virus scan enabled repositories.
- Draft functionality : Provides the capability of working with draft attachments.

### Table of Contents

- [Pre-Requisites](#pre-requisites)
- [Deploying and testing the application](#deploying-and-testing-the-application)
- [Use @cap-java/sdm plugin](#use-cap-javasdm-plugin)
- [Known Restrictions](#known-restrictions)
- [Support, Feedback, Contributing](#support-feedback-contributing)
- [Code of Conduct](#code-of-conduct)
- [Licensing](#licensing)

## Pre-Requisites
* Java 17 or higher
* CAP Development Kit (`npm install -g @sap/cds-dk`)
* SAP Build WorkZone should be subscribed to view the HTML5Applications.
* [MTAR builder](https://www.npmjs.com/package/mbt) (`npm install -g mbt`)
* [Cloud Foundry CLI](https://docs.cloudfoundry.org/cf-cli/install-go-cli.html), Install cf-cli and run command `cf install-plugin multiapps`.

## Deploying and testing the application

1. Log in to Cloud Foundry space:

   ```sh
   cf login -a <CF-API> -o <ORG-NAME> -s <SPACE-NAME>
   ```

2. Bind your CAP application to SAP Document Management Integration Option. Check the following reference from `mta.yaml` of your CAP application. See the following example from a sample Bookshop app.

   ```
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
3. Create a SAP Document Management Integration Option [Service instance and key](https://help.sap.com/docs/document-management-service/sap-document-management-service/creating-service-instance-and-service-key). Using credentials from key [onboard a repository](https://help.sap.com/docs/document-management-service/sap-document-management-service/onboarding-repository). In mta.yaml, under properties of SERVER MODULE add the repository id. See the following example from a sample Bookshop app. Currently only non versioned repositories are supported. 

    ```
    modules:
      - name: bookshop-srv
      type: java
      path: srv
      properties:
            REPOSITORY_ID: <REPO ID>
      requires:
         - name: sdm-di-instance
    ```

4. Add the following pom dependency in both the _db_ and _srv_ folders
   
   ```sh
   <dependency>
      <groupId>com.sap.cds</groupId>
      <artifactId>sdm</artifactId>
      <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

5. In the _app/index.html_ file you will find this line 
   ```sh
      <script id="sap-ui-bootstrap" src="https://sapui5.hana.ondemand.com/resources/sap-ui-core.js"
   ```
   Replace the src url with this instead
   ```sh
      "https://sapui5nightly.int.sap.eu2.hana.ondemand.com/resources/sap-ui-core.js"
   ```

6. Add the following facet in _fiori-service.cds_ in the _app_ folder
   ```sh
      {
         $Type : 'UI.ReferenceFacet',
         ID     : 'AttachmentsFacet',
         Label : '{i18n>attachments}',
         Target: 'attachments/@UI.LineItem'
      }
   ```

7. Build the project by running following command from root folder of your CAP application
   ```sh
   mbt build
   ```
   Above step will generate .mtar file inside mta_archives folder.

8. Deploy the application
   ```sh
   cf deploy mta_archives/*.mtar
   ```

9. Launch the application. Navigate to applications in the specific BTP subaccount space and open the bookshop-app application. Click on the application url provided.

10. The `Attachments` type has generated an out-of-the-box Attachments table.

## Use @cap-java/sdm plugin

**To use sdm plugin in your CAP application, create an element with an `Attachments` type.** Following the [best practice of separation of concerns](https://cap.cloud.sap/docs/guides/domain-modeling#separation-of-concerns), create a separate file _srv/attachment-extension.cds_ and _db/attachment-extension.cds_ paste the below content in both the files. See the following example from a sample Bookshop app:

```
using {my.bookshop.Books } from '../db/books';
using {sap.attachments.Attachments} from`com.sap.cds/sdm`;
 
extend entity Books with {
    attachments : Composition of many Attachments;
}
```

## Known Restrictions

- Repository : This plugin does not support the use of versioned repositories.
- File size : Attachments are limited to a maximum size of 100 MB.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/sdm/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2024 SAP SE or an SAP affiliate company and <your-project> contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/cap-java/sdm).

