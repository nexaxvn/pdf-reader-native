# pdf-reader-native

Source + artifact host cho 2 AAR + 1 dist tarball của [`nexaxvn/PDF-Reader`](https://github.com/nexaxvn/PDF-Reader).

| Artifact | Source | Distribution |
|---|---|---|
| `global.nexax:pdf-native-libs` | `:native-libs` (trong PDF-Reader, `-PincludePublishers=true`) | GitHub Packages |
| `global.nexax:libreoffice-editor` | [`libreoffice-editor/`](./libreoffice-editor) (repo này) | GitHub Packages |
| `libreoffice-dist-vX.Y.Z.tar.gz` | [`libreoffice-dist/`](./libreoffice-dist) (repo này) | Releases |

Maven URL: `https://maven.pkg.github.com/nexaxvn/pdf-reader-native`

## Repo layout

```
pdf-reader-native/
├── libreoffice-editor/    ← Android library module — source của AAR libreoffice-editor
│   ├── build.gradle.kts   ← khai báo version + fetchDist + publishing
│   └── src/main/          ← Java + res + assets (program/, share/, …)
├── libreoffice-dist/      ← source web bundle (HTML/JS/CSS) → đóng gói thành Releases tarball
├── settings.gradle.kts    ← standalone Gradle root
├── build.gradle.kts
└── gradle/                ← wrapper + libs.versions.toml
```

`:libreoffice-editor` build độc lập trong repo này (không cần PDF-Reader). Lúc build, `fetchDist` task tự tải tarball web bundle từ Releases và bung vào `assets/dist/`.

## Setup (1 lần)

`local.properties`:

```properties
sdk.dir=/Users/<bạn>/Library/Android/sdk
gpr.user=<bot-username>
gpr.token=ghp_xxx   # Classic PAT, scope: repo + write:packages. Bot có role Write trên repo này.
```

## Build local (verify)

```sh
./gradlew :libreoffice-editor:assembleRelease
# Output: libreoffice-editor/build/outputs/aar/libreoffice-editor-release.aar
```

## Quy trình bump version

### Case 1 — Sửa code `libreoffice-editor` (Java/res/manifest)

```sh
# trong clone pdf-reader-native, branch main

# 1. Sửa code trong libreoffice-editor/src/, commit
git commit -am "feat(libreoffice-editor): ..."

# 2. Bump version trong libreoffice-editor/build.gradle.kts
#       version = "1.0.1"

# 3. Publish
./gradlew :libreoffice-editor:publishReleasePublicationToGitHubPackagesRepository

# 4. Push
git commit -am "chore: bump libreoffice-editor to 1.0.1" && git push

# 5. Trong PDF-Reader, app/build.gradle.kts:
#       implementation("global.nexax:libreoffice-editor:1.0.1")
#    → smoke test → commit → push
```

### Case 2 — Đổi `.so`

```sh
# trong PDF-Reader (vì :native-libs vẫn ở đó), branch chính sau merge

# 1. Thay file vào native-libs/src/main/jniLibs/arm64-v8a/

# 2. Bump native-libs/build.gradle.kts → version = "1.0.1", publish
./gradlew -PincludePublishers=true \
  :native-libs:publishReleasePublicationToGitHubPackagesRepository --no-daemon

# 3. Trong pdf-reader-native, libreoffice-editor/build.gradle.kts:
#       api("global.nexax:pdf-native-libs:1.0.1")
#       version = "1.0.1"
./gradlew :libreoffice-editor:publishReleasePublicationToGitHubPackagesRepository
git commit -am "chore: bump native-libs + libreoffice-editor to 1.0.1" && git push

# 4. PDF-Reader app/build.gradle.kts: implementation("global.nexax:libreoffice-editor:1.0.1")
#    → smoke test → commit → push
```

### Case 3 — Đổi web bundle

```sh
# trong pdf-reader-native, branch main

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

# 4. libreoffice-editor/build.gradle.kts:
#       val distVersion = "1.0.1"
#       val distSha256  = "<sha256>"
#       version = "1.0.1"
./gradlew :libreoffice-editor:publishReleasePublicationToGitHubPackagesRepository
git commit -am "chore: bump dist + libreoffice-editor to 1.0.1" && git push

# 5. PDF-Reader app/build.gradle.kts: bump → smoke test → commit → push
```

> **Đổi nhiều thứ cùng lúc**: chạy Case 3 trước (release dist) → Case 2 (release .so) → cuối cùng publish `libreoffice-editor` **một lần** với cả 3 thay đổi.

## Quy tắc

- Version đã publish lên GitHub Packages **không thể ghi đè** — luôn bump version mới.
- Mỗi lần publish `libreoffice-editor`: bắt buộc bump `version`, kể cả nếu chỉ đổi 1 dòng code.
