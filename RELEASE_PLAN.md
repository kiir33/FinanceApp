# FinanceApp Release Plan — v1.0.0 (Regular Release)

**Status:** Draft — for verification. No code or issue changes made yet.

## Goal

Ship the first **regular (non-beta) release** of FinanceApp as `v1.0.0`, with:

1. APK signing configured (release keystore, never committed to git).
2. Automatic version bump driven by CI/CD (version derived from git tag + auto-incremented versionCode).
3. Tag-triggered GitHub Actions that builds the signed release APK and publishes it to GitHub Releases as a non-draft, non-prerelease release.

## Why not beta

Per discussion, we are skipping the beta track and going straight to a regular `v1.0.0` release. The existing CI workflow already publishes non-prerelease, non-draft releases on `v*` tags — we just need to add signing and version automation.

## Current state

| Item | Value |
|------|-------|
| `versionName` | `1.0` (hardcoded in `app/build.gradle.kts`) |
| `versionCode` | `1` (hardcoded in `app/build.gradle.kts`) |
| Signing | None — CI builds/publishes `app-release-unsigned.apk` |
| Git tags | Only `latest-debug` exists; no `v1.0.0` |
| CI trigger | `push` to `main`, PRs to `main`, `workflow_dispatch`, tags `v*` |
| Release job | Runs only on `refs/tags/v*`; `prerelease: false`, `draft: false` |
| Changelog | None |

## Desired state

| Item | Value |
|------|-------|
| `versionName` | Derived from git tag (e.g. tag `v1.0.0` → `1.0.0`) |
| `versionCode` | Auto-incremented by CI from the last committed value |
| Signing | Release keystore injected into CI via encrypted secrets; APK signed before publish |
| Release artifact | Signed APK (`.apk`) installable on devices |
| Git tag | `v1.0.0` created on `main` and pushed |
| GitHub Release | Published (not draft, not prerelease) from tag `v1.0.0` |

## Files to change

### 1. `app/build.gradle.kts`

Make version and signing configurable via Gradle properties, with safe defaults for local builds:

```kotlin
android {
    namespace = "com.example.financeapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.financeapp"
        minSdk = 24
        targetSdk = 36
        versionCode = project.findProperty("versionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("versionName") ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = project.findProperty("RELEASE_STORE_FILE")?.let { file(it) }
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD")
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS")
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    ...
}
```

Notes:
- Local builds without the properties fall back to `versionCode = 1`, `versionName = "1.0.0"` and no signing config (debug-only behavior; release build locally will fail until properties are supplied, which is intentional — release signing must be explicit).
- `findProperty` reads from `-P` flags, `gradle.properties`, and environment variables prefixed with `ORG_GRADLE_PROJECT_`.

### 2. `gradle.properties`

Add placeholder signing properties (blank by default; injected in CI):

```properties
# Release signing configuration (values injected via CI secrets; never commit real values)
RELEASE_STORE_FILE=
RELEASE_STORE_PASSWORD=
RELEASE_KEY_ALIAS=
RELEASE_KEY_PASSWORD=
```

The keystore file itself (`release-keystore.jks`) must **not** be committed. It is base64-encoded and stored as a GitHub secret, decoded by CI at build time.

### 3. `.github/workflows/build-and-release.yml`

Update the release job to:

1. **Decode the keystore** from secret `RELEASE_KEYSTORE_BASE64` into a temp file.
2. **Write signing properties** to `gradle.properties` (or pass as `-P` flags) from secrets:
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`
3. **Auto-bump version**:
   - Extract `versionName` from the git tag (strip leading `v`), e.g. `v1.0.0` → `1.0.0`.
   - Read current `versionCode` from `app/build.gradle.kts`, increment by 1.
   - Write the bumped `versionCode` and `versionName` back to `app/build.gradle.kts`.
   - Commit the version bump to `main` with `[skip ci]` and push (workflow needs `contents: write` on that job).
4. **Build** `./gradlew assembleRelease -PversionCode=... -PversionName=...`
5. **Publish** signed APK to GitHub Releases from the tag (`prerelease: false`, `draft: false`).

Suggested structure (key job excerpts):

```yaml
jobs:
  build:
    name: Build Debug APK
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon

  release:
    name: Build & Release Production APK
    needs: build
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    permissions:
      contents: write   # needed to commit version bump + create release

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:           # persist credentials so we can push the version bump
          token: ${{ secrets.GITHUB_TOKEN }}
          persist-credentials: true

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Configure release signing
        run: |
          echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > release-keystore.jks
          echo "RELEASE_STORE_FILE=$PWD/release-keystore.jks" >> $GITHUB_ENV
          echo "RELEASE_STORE_PASSWORD=${{ secrets.RELEASE_STORE_PASSWORD }}" >> $GITHUB_ENV
          echo "RELEASE_KEY_ALIAS=${{ secrets.RELEASE_KEY_ALIAS }}" >> $GITHUB_ENV
          echo "RELEASE_KEY_PASSWORD=${{ secrets.RELEASE_KEY_PASSWORD }}" >> $GITHUB_ENV

      - name: Compute and bump version
        id: version
        run: |
          TAG=${GITHUB_REF_NAME#v}                 # strip leading 'v'
          CURRENT_CODE=$(grep 'versionCode =' app/build.gradle.kts | grep -o '[0-9]\+' | head -1)
          NEXT_CODE=$((CURRENT_CODE + 1))
          sed -i "s/versionCode = .*/versionCode = $NEXT_CODE/" app/build.gradle.kts
          sed -i "s/versionName = .*/versionName = \"$TAG\"/" app/build.gradle.kts
          echo "version_name=$TAG" >> $GITHUB_OUTPUT
          echo "version_code=$NEXT_CODE" >> $GITHUB_OUTPUT

      - name: Commit version bump
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add app/build.gradle.kts
          git commit -m "chore: bump version to $TAG ($NEXT_CODE) [skip ci]" || true
          git push origin main

      - name: Build Release APK
        run: ./gradlew assembleRelease --no-daemon -PversionCode=${{ steps.version.outputs.version_code }} -PversionName=${{ steps.version.outputs.version_name }}

      - name: Publish to GitHub Releases
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          name: ${{ format('Release {0}', github.ref_name) }}
          draft: false
          prerelease: false
          files: app/build/outputs/apk/release/app-release.apk
```

Note: the release APK output path changes from `app-release-unsigned.apk` to `app-release.apk` once signing is configured.

### 4. GitHub secrets (repository settings)

Create these in `kiir33/FinanceApp` Settings → Secrets and variables → Actions:

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore (`release-keystore.jks`) |
| `RELEASE_STORE_PASSWORD` | Keystore store password |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | Key password |

Generate the keystore locally (one-time):

```bash
keytool -genkeypair -v -keystore release-keystore.jks -alias financeapp -keyalg RSA -keysize 2048 -validity 10000
```

Then base64-encode it and add as the secret:

```bash
base64 -w 0 release-keystore.jks   # copy output into RELEASE_KEYSTORE_BASE64 secret
```

**Never commit** `release-keystore.jks` or the passwords to git. Add `*.jks` / `*.keystore` to `.gitignore` if not already present.

### 5. `IMPLEMENTATION_PLAN.md`

Add a new **Phase 8: Release & Signing** section after Phase 7, e.g.:

```
### Phase 8: Release & Signing
- [ ] Configure release signing config in `app/build.gradle.kts`
- [ ] Add signing property placeholders to `gradle.properties`
- [ ] Add keystore + signing secrets to GitHub Actions
- [ ] Update CI workflow: decode keystore, auto-bump version, build signed APK, publish release
- [ ] Add `*.jks`/`*.keystore` to `.gitignore`
- [ ] Create `CHANGELOG.md` with v1.0.0 entry
- [ ] Tag `v1.0.0` on `main` and push to trigger first release
```

### 6. `QA_TEST_PLAN.md`

Add to the Pre-Release Checklist:
- [ ] Release APK is signed (verify with `apksigner verify --verbose app-release.apk`)
- [ ] Version code/name match the git tag
- [ ] `*.jks` / `*.keystore` not present in git history

And add a keystore-safeguard note under Release Preparation.

### 7. `CHANGELOG.md` (new file)

Create with an initial `v1.0.0` entry summarizing features shipped to date (auth, RBAC, treasury, CSV import/export, backup/restore, hardening).

### 8. `.gitignore`

Ensure signing files are ignored:

```
*.jks
*.keystore
*.key
release-keystore.jks
```

## Release workflow (how v1.0.0 gets cut)

1. Verify `main` is clean and all QA checks pass (`./gradlew test`, `./gradlew lint`).
2. Create and push tag: `git tag -a v1.0.0 -m "Release v1.0.0" && git push origin v1.0.0`
3. CI `release` job triggers:
   - decodes keystore from secrets,
   - bumps `versionCode` (1 → 2), sets `versionName = 1.0.0`,
   - commits bump back to `main` `[skip ci]`,
   - builds signed release APK,
   - publishes GitHub Release from tag `v1.0.0` (not draft, not prerelease).
4. Verify the release APK installs on a device and `apksigner verify` passes.

## Version-bump strategy options (pick one)

**Recommended (above):** `versionName` from tag; `versionCode` auto-incremented and committed back to `main`. Simple, auditable, each release gets a higher code.

**Alternative A:** Both `versionName` and `versionCode` derived purely from the tag (e.g. `v1.0.0` → code `1000000`, `v1.0.1` → `1000001`). No repo commits from CI, but less flexible for patch versions.

**Alternative B:** `versionCode` = `GITHUB_RUN_NUMBER` (monotonically increasing per workflow run). Avoids reading/writing `build.gradle.kts` but codes aren't stable across re-runs of the same tag.

## Open questions for you to verify

1. Do you want to generate the release keystore now, or already have one to use?
2. Are you comfortable with CI committing the version bump back to `main` (`[skip ci]`)?
3. Should we also publish an Android App Bundle (`.aab`) alongside the APK for Play Store, or APK-only for now?
4. Any specific entries you want in the initial `CHANGELOG.md`?
5. Once you confirm this plan, I'll update GitHub issue #22 to align with it and then implement the changes.

## What I have NOT done yet

- No edits to `app/build.gradle.kts`, `gradle.properties`, the CI workflow, `IMPLEMENTATION_PLAN.md`, `QA_TEST_PLAN.md`, `.gitignore`, or `CHANGELOG.md`.
- No changes to GitHub secrets or issue #22.
- No git tag created.

Please review the draft above and let me know what to adjust before I implement.
