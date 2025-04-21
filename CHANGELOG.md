# Change Log

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).
The format is based on [Keep a Changelog](http://keepachangelog.com/).

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