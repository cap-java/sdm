# Change Log

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).
The format is based on [Keep a Changelog](http://keepachangelog.com/).

## Version 1.5.0

### Added
- Ability to copy attachments between entities

### Fixed
- Added authorities to token to improve logging during deletion of attachment
- Improved error handling for cases where the user lacks SDM roles

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
