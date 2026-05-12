# pdf-reader-native

Hosting repository for the binary assets consumed by the
[`nexaxvn/PDF-Reader`](https://github.com/nexaxvn/PDF-Reader) Android app.

This repo serves three purposes:

1. **GitHub Packages (Maven)** — hosts the published Android AAR artifacts.
2. **Releases** — hosts large binary assets (the LibreOffice web bundle).
3. **`libreoffice-dist/`** — versioned source of the LibreOffice web bundle.

---

## Published Maven artifacts

| Group | Artifact | Description |
|---|---|---|
| `global.nexax` | `pdf-native-libs` | Prebuilt `arm64-v8a` `.so` libraries (LibreOfficeKit, NSS, sqlite, …) packaged as an AAR. |
| `global.nexax` | `libreoffice-editor` | LibreOffice-based document editor module (Java + resources + assets). Declares `api("global.nexax:pdf-native-libs:<v>")` so consumers transitively get the native libraries. |

Maven URL: `https://maven.pkg.github.com/nexaxvn/pdf-reader-native`

---

## Prerequisites

### 1. Bot account & token

Publishing to GitHub Packages requires a Personal Access Token (**Classic**,
not fine-grained — fine-grained tokens currently return `403` on Maven
uploads).

Recommended setup: a bot account (e.g. `tester1-dotcom`) with **Write**
role on this repo, and a Classic PAT with scopes:

- `repo`
- `write:packages`
- `read:packages`

### 2. Local credentials

Add to `local.properties` of the **PDF-Reader** workspace (gitignored):

```properties
gpr.user=<bot-username>
gpr.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Or export as environment variables (`GITHUB_ACTOR` / `GITHUB_TOKEN`).

### 3. Required source artifacts

The `:native-libs` module ships prebuilt `.so` files that are **not** in
git. Drop them in:

```
PDF-Reader/native-libs/src/main/jniLibs/arm64-v8a/
  ├── libandroidapp.so
  ├── libc++_shared.so
  ├── liblo-native-code.so
  └── … (15 files total)
```

The `:LibreOfficeEditor` module fetches its web bundle automatically from
this repo's Releases (see [Updating the LibreOffice dist](#updating-the-libreoffice-dist)).

---

## Publishing `pdf-native-libs`

From the PDF-Reader workspace root:

```sh
# 1. Bump version in native-libs/build.gradle.kts
#    version = "1.0.1"

# 2. Verify the 15 .so files are present
ls native-libs/src/main/jniLibs/arm64-v8a/ | wc -l   # → 15

# 3. Publish
./gradlew -PincludePublishers=true \
  :native-libs:publishReleasePublicationToGitHubPackagesRepository \
  --no-daemon
```

The flag `-PincludePublishers=true` is required — both publisher modules
are excluded from `settings.gradle.kts` by default to keep day-to-day app
builds fast.

Verify the upload:

```sh
TOKEN=$(grep '^gpr.token=' local.properties | cut -d= -f2-)
curl -sI -H "Authorization: Bearer $TOKEN" \
  "https://maven.pkg.github.com/nexaxvn/pdf-reader-native/global/nexax/pdf-native-libs/<VERSION>/pdf-native-libs-<VERSION>.aar" \
  | head -1
```

Should print `HTTP/2 200`.

---

## Publishing `libreoffice-editor`

From the PDF-Reader workspace root:

```sh
# 1. Bump version in LibreOfficeEditor/build.gradle.kts
#    version = "1.0.1"
#
# 2. If you also bumped pdf-native-libs, update the api dependency:
#    api("global.nexax:pdf-native-libs:1.0.1")

# 3. Publish (this also runs :LibreOfficeEditor:fetchDist if needed)
./gradlew -PincludePublishers=true \
  :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository \
  --no-daemon
```

After publishing, bump the consumer in `app/build.gradle.kts`:

```kotlin
implementation("global.nexax:libreoffice-editor:1.0.1")
```

---

## Updating the LibreOffice dist

The web bundle (`assets/dist/`) is **not** stored in the PDF-Reader repo.
Its source lives in [`libreoffice-dist/`](./libreoffice-dist) of this repo,
and the binary tarball is hosted on this repo's Releases. The
`:LibreOfficeEditor:fetchDist` Gradle task downloads + verifies (sha256) +
extracts it into `LibreOfficeEditor/src/main/assets/dist/` before
`preBuild`.

### Cutting a new dist release

```sh
# In a clone of this repo
cd libreoffice-dist
# … edit / replace files, commit ...

VERSION=1.0.1
cd ..
tar --no-xattrs -czf /tmp/libreoffice-dist-$VERSION.tar.gz \
    --transform "s,^libreoffice-dist,dist," libreoffice-dist
shasum -a 256 /tmp/libreoffice-dist-$VERSION.tar.gz
# → record the hash
```

Create the release on GitHub:

```sh
TOKEN=<classic PAT with repo scope>
TAG=libreoffice-dist-v$VERSION
SHA=<sha256 from shasum>

# Create release
RESP=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/nexaxvn/pdf-reader-native/releases" \
  -d "{\"tag_name\":\"$TAG\",\"name\":\"LibreOffice dist $VERSION\",\"body\":\"sha256: $SHA\"}")

UPLOAD=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['upload_url'].split('{')[0])")

# Upload tarball
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/gzip" \
  --data-binary @/tmp/libreoffice-dist-$VERSION.tar.gz \
  "$UPLOAD?name=libreoffice-dist-$VERSION.tar.gz"
```

Then update `LibreOfficeEditor/build.gradle.kts` in PDF-Reader:

```kotlin
val distVersion = "1.0.1"
val distSha256 = "<new sha256>"
```

…and re-publish `libreoffice-editor` with a bumped version.

---

## Quick reference: full bump cycle

When all three artifacts (dist, native-libs, libreoffice-editor) change:

```sh
# 1. Build + release new dist tarball                      (this repo)
# 2. Bump distVersion + distSha256                         (PDF-Reader: LibreOfficeEditor/build.gradle.kts)
# 3. Bump native-libs version, publish                     (PDF-Reader)
./gradlew -PincludePublishers=true :native-libs:publishReleasePublicationToGitHubPackagesRepository
# 4. Bump api("…:pdf-native-libs:<new>") + module version  (PDF-Reader: LibreOfficeEditor/build.gradle.kts)
./gradlew -PincludePublishers=true :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository
# 5. Bump implementation("…:libreoffice-editor:<new>")     (PDF-Reader: app/build.gradle.kts)
# 6. ./gradlew :app:assembleDebug          # smoke test
# 7. Commit + push the version bumps to PDF-Reader
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `403 Forbidden` on publish | Fine-grained PAT, or bot has Read role only | Use a Classic PAT; grant Write role on this repo. |
| `409 Conflict` on publish | Re-uploading the same `version` | Bump version — GitHub Packages does not allow overwrites. |
| `:LibreOfficeEditor:fetchDist` fails with `401 Unauthorized` | Missing or invalid `gpr.token` | Set `gpr.token` in `local.properties` (or `GITHUB_TOKEN` env). |
| `Checksum mismatch for libreoffice-dist-…tar.gz` | `distSha256` in `build.gradle.kts` doesn't match the tarball | Update `distSha256` (or re-upload the asset). |
| `Asset libreoffice-dist-X.Y.Z.tar.gz not found in release …` | Tag exists but no asset uploaded | Re-upload the tarball to the release. |
