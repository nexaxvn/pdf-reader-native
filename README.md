# pdf-reader-native

Hosting cho 2 AAR + 1 dist tarball của [`nexaxvn/PDF-Reader`](https://github.com/nexaxvn/PDF-Reader).

| Loại | Tên | Nơi lưu |
|---|---|---|
| AAR | `global.nexax:pdf-native-libs` | GitHub Packages |
| AAR | `global.nexax:libreoffice-editor` | GitHub Packages |
| Dist tarball | `libreoffice-dist-vX.Y.Z.tar.gz` | Releases (source ở [`libreoffice-dist/`](./libreoffice-dist)) |

Maven URL: `https://maven.pkg.github.com/nexaxvn/pdf-reader-native`

---

## Setup 1 lần

`local.properties` trong workspace **PDF-Reader** (gitignored):

```properties
gpr.user=<bot-username>
gpr.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx   # Classic PAT, scope: repo + write:packages
```

> Fine-grained PAT bị `403` khi publish Maven — phải dùng Classic PAT.
> Bot account cần role **Write** trên repo này.

---

## Bump version & release

### A. Chỉ đổi `.so` → release `pdf-native-libs`

```sh
# 1. Đặt 15 file .so mới vào: native-libs/src/main/jniLibs/arm64-v8a/
# 2. Bump version trong native-libs/build.gradle.kts:  version = "1.0.1"
# 3. Publish
./gradlew -PincludePublishers=true \
  :native-libs:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 4. Trong LibreOfficeEditor/build.gradle.kts: bump dep + module version
#    api("global.nexax:pdf-native-libs:1.0.1")
#    version = "1.0.1"
./gradlew -PincludePublishers=true \
  :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 5. Trong app/build.gradle.kts: implementation("global.nexax:libreoffice-editor:1.0.1")
```

### B. Chỉ đổi web bundle → release `libreoffice-dist` + `libreoffice-editor`

```sh
# 1. Sửa file trong libreoffice-dist/ của repo này, commit + push.

# 2. Build tarball + lấy sha256
VERSION=1.0.1
tar --no-xattrs -czf /tmp/libreoffice-dist-$VERSION.tar.gz \
    --transform "s,^libreoffice-dist,dist," libreoffice-dist
shasum -a 256 /tmp/libreoffice-dist-$VERSION.tar.gz

# 3. Tạo release + upload asset
TOKEN=<Classic PAT>
TAG=libreoffice-dist-v$VERSION
RESP=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/nexaxvn/pdf-reader-native/releases" \
  -d "{\"tag_name\":\"$TAG\",\"name\":\"LibreOffice dist $VERSION\"}")
UPLOAD=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['upload_url'].split('{')[0])")
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/gzip" \
  --data-binary @/tmp/libreoffice-dist-$VERSION.tar.gz \
  "$UPLOAD?name=libreoffice-dist-$VERSION.tar.gz"

# 4. Trong PDF-Reader/LibreOfficeEditor/build.gradle.kts:
#    val distVersion = "1.0.1"
#    val distSha256  = "<sha256 mới>"
#    version = "1.0.1"
./gradlew -PincludePublishers=true \
  :LibreOfficeEditor:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 5. Trong app/build.gradle.kts: bump implementation("global.nexax:libreoffice-editor:1.0.1")
```

### C. Đổi cả 2 → làm tuần tự B (bước 1–4) rồi A (bước 1–5).

---

## Verify

```sh
TOKEN=$(grep '^gpr.token=' local.properties | cut -d= -f2-)
curl -sI -H "Authorization: Bearer $TOKEN" \
  "https://maven.pkg.github.com/nexaxvn/pdf-reader-native/global/nexax/<artifact>/<v>/<artifact>-<v>.aar" \
  | head -1     # → HTTP/2 200
./gradlew :app:assembleDebug --no-daemon
```

---

## Lỗi thường gặp

| Lỗi | Fix |
|---|---|
| `403 Forbidden` khi publish | Dùng Classic PAT; bot phải có role Write. |
| `409 Conflict` | Đã publish version đó rồi — bump version mới (GitHub Packages không cho ghi đè). |
| `fetchDist`: `401 Unauthorized` | Set `gpr.token` trong `local.properties`. |
| `Checksum mismatch` | `distSha256` trong `LibreOfficeEditor/build.gradle.kts` không khớp tarball — cập nhật lại. |
| `Asset … not found in release` | Tag có nhưng quên upload tarball — upload lại. |
