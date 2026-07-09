# Change Log

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).
The format is based on [Keep a Changelog](http://keepachangelog.com/).

## Version 1.10.1

### Fixed
- User-defined error message not displayed when maximum attachment upload limit is exceeded
- Fix issue in discarding newly created entities which has multi-level of nested children
- Fix populateUploadableFlags breaks reads on non-draft services

## Version 1.10.0

### Added
- Support download multiple attachments
- Support disabling of upload button when maximum allowed attachments count is reached

### Fixed
- Localization for attachment reference fields
- Service binding lookup to correctly resolve SDM bindings by tags
- Technical user detection in the delete API to align with other API implementations
- File extension change during attachment rename
- Copying of invalid secondary properties during copy operation

## Version 1.9.0

### Added
- Support attachment creation in active entities

### Fixed
- Separate UI and backend error keys for localization
- Fix upload status when attachments are copied

## Version 1.8.1

### Fixed
- Enhanced logging across all plugin code for improved debugging and traceability

## Version 1.8.0

### Added
- Support for Maximum File Size during upload

### Fixed
- Move attachemnts support for Active entities
- Copy attachments issue when date type custom property is added with attachment
- Move attachment issue when drop-down codeList type custom property is added with attachment
- Link creation in nested entities
- Issue with parsing nested entity when entities name is camel case

## Version 1.7.0

### Added
- ChangeLog support
- MoveAttachments support
- Upload Status support in Attachments
- Internationalization support for error messages

### Fixed
- Prevent unwanted update calls on edit without changes
- Heap Out of Memory error in Large file upload

## Version 1.6.2

### Fixed
- Deep copying of custom properties and notes when copying attachments
- Dynamic primary key extraction from entity schema
- Facet parsing for parent entity and composition extraction during attachment copy
- Draft discard operation with attachments
- Service namespace support in createLink feature

## Version 1.6.1

### Fixed
- Incorrect filename shown after copying attachment
- Defensive EhCache initialization
- Improved filename validation
- Removed harcoded ServiceName type 
- Improved error handling for nested entities
- Link doesn't get reverted in SDM when changes are discarded on the UI 
- Support for entities defined with namespace  

## Version 1.6.0

### Added
- Support for Link type Attachments.
- Support to Edit URL in Link type Attachments.
- Support for translation of error messages in the plugin.

### Fixed
- Allow update or creation of subscription in case a repository with the configured external ID already exists by skipping the onboarding step.
- Improved error handling for cases where the mimetype of the uploaded attachment is blocked on a repository level.
- Improved error handling for cases where repository offboarding fails during unsubscription.
- Issue where repository offboarding was failing if the instance only contains a single repository.
- Improved error handling for repository onboarding during subscription.
- Blocked upload for scenarios where file size > 400mb and repository is virus scan enabled.
- Improved error handling for large file upload. 
- Update of attachments present in entities which are not direct compositions to the root entity.
- Copying of attachments present in projection entities. 

## Version 1.5.0

### Added
- Ability to copy attachments between entities.

### Fixed
- Added authorities to token to improve logging during deletion of attachment.
- Improved error handling for cases where the user lacks SDM roles.

## Version 1.4.1

### Fixed

- An issue related to IAS token fetch. Now plugin works with IAS flow as well.

## Version 1.4.0

### Added
- Support technical user flow.
- Support codelist for custom properties.

### Fixed
- An issue where attachments uploaded with one repository was visible when application is redeployed with another repository.

## Version 1.3.1

### Fixed

- An issue in uploading large size attachments.

## Version 1.3.0

### Added
- Support multiple attachments composition in CAP Entity.
- Support repository off-boarding in multi tenant use case.
- Support maximum allowed attachments upload.

## Version 1.2.0

### Fixed

- An issue in create mode when deleting an attachment resulted in deletion of all the attachments of the entity.

### Added

- Support custom properties in attachments.
- Support large file uploads.

## Version 1.1.0

### Fixed

- Allow any name in the primary key for the entity. 
- Duplicate filename check with multiple repository switch.
- Error message for special characters in filename.

### Added

- Support repository onboarding for multitenant use case.

## Version 1.0.2

### Added

- Validation of special characters in attachment names.
- Implemented API requests to SDM using Cloud SDK library.

### Fixed

- Check for SDM roles while renaming attachments.
- Error message when a user with no SDM roles uploads an attachment.

## Version 1.0.1

### Fixed

- This plugin can be used in a multi-tenant SaaS CAP application.

## Version 1.0.0

### Added

Initial release that provides the following features 

- Create attachment : Provides the capability to upload new attachments.
- Open attachment : Provides the capability to preview attachments.
- Delete attachment : Provides the capability to remove attachments.
- Rename attachment : Provides the capability to rename attachments.
- Virus scanning : Provides the capability to support virus scan for virus scan enabled repositories.
- Draft functionality : Provides the capability of working with draft attachments.
- Display attachments specific to repository: Lists attachments contained in the repository that is configured with the CAP application.
