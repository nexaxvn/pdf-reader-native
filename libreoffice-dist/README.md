# libreoffice-dist

LibreOffice Online web bundle (HTML / CSS / JS / images / fonts) consumed by
the `:LibreOfficeEditor` Android module of `nexaxvn/PDF-Reader`.

The `:LibreOfficeEditor` Gradle build does NOT read this folder directly; it
downloads a tarball from a GitHub Release of this repo (tag
`libreoffice-dist-v<VERSION>`, asset `libreoffice-dist-<VERSION>.tar.gz`) and
extracts it into `LibreOfficeEditor/src/main/assets/dist/`.

## Updating

1. Edit / replace files in this folder, commit on a feature branch.
2. From the repo root run:

   ```sh
   VERSION=1.0.1
   tar --no-xattrs -C . -czf /tmp/libreoffice-dist-$VERSION.tar.gz \
       --transform "s,^libreoffice-dist,dist," libreoffice-dist
   shasum -a 256 /tmp/libreoffice-dist-$VERSION.tar.gz
   ```

3. Create a release `libreoffice-dist-v$VERSION` on this repo and upload the
   tarball as an asset.
4. In `nexaxvn/PDF-Reader`, bump `distVersion` + `distSha256` in
   `LibreOfficeEditor/build.gradle.kts`, bump the module `version`, then
   publish a new `global.nexax:libreoffice-editor` artifact.
