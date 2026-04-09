---
name: localization
description: Guide for localization in Geoviite project
---

Geoviite localizations reside in the file `geoviite/infra/src/main/resources/i18n/translations.fi.json`. There is an
`.en.json` file as well, but it's not currently used and does not need to be updated. Finnish is currently the only
language. Frontend gets the same translations file by requesting it from the backend, so there is only one place to
update.
