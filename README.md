# pdf-reader-native

Hosting cho 2 AAR + 1 dist tarball của [`nexaxvn/PDF-Reader`](https://github.com/nexaxvn/PDF-Reader).

| Loại | Tên |
|---|---|
| AAR (GitHub Packages) | `global.nexax:pdf-native-libs` |
| AAR (GitHub Packages) | `global.nexax:libreoffice-editor` |
| Tarball (Releases) | `libreoffice-dist-vX.Y.Z.tar.gz` — source: [`libreoffice-dist/`](./libreoffice-dist) |

Maven URL: `https://maven.pkg.github.com/nexaxvn/pdf-reader-native`

## Setup (1 lần)

`local.properties` trong workspace **PDF-Reader**:

```properties
gpr.user=<bot-username>
gpr.token=ghp_xxx   # Classic PAT, scope: repo + write:packages. Bot phải có role Write.
```

## Bump version

### A. Đổi `.so`

```sh
# 1. Thay 15 file .so trong native-libs/src/main/jniLibs/arm64-v8a/
# 2. Bump native-libs/build.gradle.kts: version = "X.Y.Z"
./gradlew -PincludePublishers=true :native-libs:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 3. Bump LibreOfficeEditor/build.gradle.kts:
#       api("global.nexax:pdf-native-libs:X.Y.Z")
#       version = "X.Y.Z"
./gradlew -PincludePublishers=true :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 4. app/build.gradle.kts: implementation("global.nexax:libreoffice-editor:X.Y.Z")
```

### B. Đổi web bundle

```sh
# 1. Sửa libreoffice-dist/ trong repo này, commit + push.

# 2. Build tarball + sha256
VERSION=X.Y.Z
tar --no-xattrs -czf /tmp/libreoffice-dist-$VERSION.tar.gz \
    --transform "s,^libreoffice-dist,dist," libreoffice-dist
shasum -a 256 /tmp/libreoffice-dist-$VERSION.tar.gz

# 3. Tạo release + upload
TOKEN=<Classic PAT>
RESP=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/nexaxvn/pdf-reader-native/releases" \
  -d "{\"tag_name\":\"libreoffice-dist-v$VERSION\",\"name\":\"LibreOffice dist $VERSION\"}")
UPLOAD=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['upload_url'].split('{')[0])")
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/gzip" \
  --data-binary @/tmp/libreoffice-dist-$VERSION.tar.gz \
  "$UPLOAD?name=libreoffice-dist-$VERSION.tar.gz"

# 4. Bump LibreOfficeEditor/build.gradle.kts:
#       val distVersion = "X.Y.Z"
#       val distSha256  = "<sha256>"
#       version = "X.Y.Z"
./gradlew -PincludePublishers=true :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 5. app/build.gradle.kts: implementation("global.nexax:libreoffice-editor:X.Y.Z")
```

### C. Đổi cả 2 → chạy B (1–4) rồi A (1–4).

## Verify

```sh
./gradlew :app:assembleDebug --no-daemon
```
