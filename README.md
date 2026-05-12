# pdf-reader-native

Hosting cho 2 AAR + 1 dist tarball của [`nexaxvn/PDF-Reader`](https://github.com/nexaxvn/PDF-Reader).

| Artifact | Nơi lưu | Bump khi |
|---|---|---|
| `global.nexax:pdf-native-libs` | GitHub Packages | đổi `.so` |
| `global.nexax:libreoffice-editor` | GitHub Packages | đổi code Java/res/manifest module `:LibreOfficeEditor` **hoặc** đổi `.so` **hoặc** đổi web bundle |
| `libreoffice-dist-vX.Y.Z.tar.gz` | Releases (source: [`libreoffice-dist/`](./libreoffice-dist)) | đổi web bundle |

Maven URL: `https://maven.pkg.github.com/nexaxvn/pdf-reader-native`

## Setup (1 lần)

`local.properties` trong workspace **PDF-Reader**:

```properties
gpr.user=<bot-username>
gpr.token=ghp_xxx   # Classic PAT, scope: repo + write:packages. Bot phải có role Write.
```

## Quy trình bump version

Sau khi PR đã merge vào branch chính (`master-1.1` / `main`), checkout branch đó → pull → làm các bước tương ứng với loại thay đổi.

### Case 1 — Sửa code `:LibreOfficeEditor` (Java/Kotlin/res/manifest)

```sh
# trong PDF-Reader, đã ở branch chính sau merge

# 1. Bump version trong LibreOfficeEditor/build.gradle.kts
#       version = "1.0.1"

# 2. Publish
./gradlew -PincludePublishers=true \
  :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 3. Bump consumer trong app/build.gradle.kts
#       implementation("global.nexax:libreoffice-editor:1.0.1")

# 4. Smoke test + commit
./gradlew :app:assembleDebug --no-daemon
git commit -am "chore: bump libreoffice-editor to 1.0.1" && git push
```

### Case 2 — Đổi `.so`

```sh
# 1. Thay 15 file vào native-libs/src/main/jniLibs/arm64-v8a/

# 2. Bump native-libs/build.gradle.kts → version = "1.0.1", publish
./gradlew -PincludePublishers=true \
  :native-libs:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 3. Bump LibreOfficeEditor/build.gradle.kts:
#       api("global.nexax:pdf-native-libs:1.0.1")
#       version = "1.0.1"
./gradlew -PincludePublishers=true \
  :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 4. app/build.gradle.kts: implementation("global.nexax:libreoffice-editor:1.0.1")
#    → smoke test → commit → push
```

### Case 3 — Đổi web bundle

```sh
# trong clone của repo này (pdf-reader-native), branch main

# 1. Sửa libreoffice-dist/, commit, push.

# 2. Build tarball + sha256
VERSION=1.0.1
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

# 4. Trong PDF-Reader, LibreOfficeEditor/build.gradle.kts:
#       val distVersion = "1.0.1"
#       val distSha256  = "<sha256>"
#       version = "1.0.1"
./gradlew -PincludePublishers=true \
  :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 5. app/build.gradle.kts: bump → smoke test → commit → push
```

> **Đổi nhiều thứ cùng lúc**: chạy Case 3 trước (release dist) → Case 2 (release .so) → cuối cùng publish `libreoffice-editor` **một lần** với cả 3 thay đổi (dist version, native-libs version, code mới), rồi bump consumer.

## Quy tắc

- Version đã publish lên GitHub Packages **không thể ghi đè** — luôn bump version mới.
- Mỗi lần publish `libreoffice-editor`: bắt buộc bump `version`, kể cả nếu chỉ đổi 1 dòng code.
