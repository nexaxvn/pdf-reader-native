# pdf-reader-native

Hosting repository for binary assets used by the PDF Reader Android app.

## GitHub Packages (Maven)

- `global.nexax:pdf-native-libs` — prebuilt arm64-v8a `.so` libraries
  (LibreOfficeKit, NSS, sqlite, …) packaged as an AAR.
- `global.nexax:libreoffice-editor` — LibreOffice-based document editor
  module (Java + resources + assets), depends transitively on
  `pdf-native-libs`.

## Releases (large binary assets)

- `libreoffice-dist-vX.Y.Z` — tarball of the LibreOffice Online web bundle
  consumed by `libreoffice-editor`. Downloaded by the `:LibreOfficeEditor`
  Gradle build into `src/main/assets/dist/` when missing.
