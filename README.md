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
- [Use @cap-java/sdm plugin](#use-cap-javasdm-plugin)
- [Deploying and testing the application](#deploying-and-testing-the-application)
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

## Use @cap-java/sdm plugin

**To use sdm plugin in the bookshop demoapp, create an element with an `Attachments` type.** Following the [best practice of separation of concerns](https://cap.cloud.sap/docs/guides/domain-modeling#separation-of-concerns), create a separate file _srv/attachment-extension.cds_ and paste the below content in it:

```
using {my.bookshop.Books } from '../db/books';
using {sap.attachments.Attachments, sap.attachments.StatusCode} from`com.sap.cds/sdm`;
 
extend entity Books with {
    attachments : Composition of many Attachments;
}
```

## Deploying and testing the application

1. Log in to Cloud Foundry space:

   ```sh
   cf login -a <CF-API> -o <ORG-NAME> -s <SPACE-NAME>
   ```

2. Bind CAP application to SAP Document Management Integration Option. Check the following reference from `mta.yaml` of the bookshop demoapp

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
3. Create a SAP Document Management Integration Option [Service instance and key](https://help.sap.com/docs/document-management-service/sap-document-management-service/creating-service-instance-and-service-key). Using credentials from key [onboard a repository](https://help.sap.com/docs/document-management-service/sap-document-management-service/onboarding-repository). In mta.yaml, under properties of SERVER MODULE add the repository id as given below. Currently only non versioned repositories are supported. 

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

4. Build the project by running following command from root folder of bookshop demoapp
   ```sh
   mbt build
   ```
   Above step will generate .mtar file inside mta_archives folder.

5. Deploy the application
   ```sh
   cf deploy mta_archives/*.mtar
   ```

6. Launch the application
   ```sh
   * Navigate to applications in the specific BTP subaccount space and open the bookshop-app application. Click on the application url provided.
   ```  

7. The `Attachments` type has generated an out-of-the-box Attachments table (see highlighted box) at the bottom of the Object page:
   <img width="1300" alt="Attachments Table" style="border-radius:0.5rem;" src="etc/facet.png">


## Known Restrictions

- Repository : This plugin does not support the use of versioned repositories.
- File size : Attachments are limited to a maximum size of 100 MB.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/sdm/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2024 SAP SE or an SAP affiliate company and <your-project> contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/cap-java/sdm).

