# FinanceApp Mobile — Deployment Plan

> **Audience**: Mobile lead, Android developers, CI/CD engineers.
> **Status**: Draft.
> **Scope**: Deployment of the Android/Kotlin client — APK signing, CI/CD release pipeline, version bump, GitHub Releases, production `BASE_URL` default.
> **Source of truth**: [[../../FinanceApp-KnowledgeBase/DEPLOYMENT_PLAN.md]] (cross-repo overview).
> **Related**: [[../../FinanceApp-KnowledgeBase/MOBILE_ANDROID_PLAN.md]], [[RELEASE_PLAN.md]], [[IMPLEMENTATION_PLAN.md]]

---

## 1. Overview

Deploy the FinanceApp Android client as a signed release APK via CI/CD. The mobile deployment is independent of the backend deployment — the app connects to the backend via `BASE_URL`, which is configurable in Settings and defaults to the production domain.

## 2. Components to Deploy

### 2.1 Signed release APK

- APK signed with a release keystore (never committed to git).
- Keystore injected into CI via encrypted secrets.
- Version derived from git tag + auto-incremented `versionCode`.

### 2.2 GitHub Releases

- Tag-triggered GitHub Actions builds the signed release APK.
- Published to GitHub Releases as non-draft, non-prerelease.
- Changelog included per release.

## 3. Version & Signing Configuration

### 3.1 `app/build.gradle.kts`

Make version and signing configurable via Gradle properties:

```kotlin
android {
    namespace = "com.example.financeapp"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        versionName = "1.0"          // overridden by CI from git tag
        versionCode = 1              // auto-incremented by CI
    }

    signingConfigs {
        release {
            storeFile = project.rootProject.file("keystore/release-keystore.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release-key"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig signingConfigs.debug
        }
    }
}
```

### 3.2 Gradle properties

| Property | Source | Default |
|----------|--------|---------|
| `RELEASE_STORE_PASSWORD` | CI secret / `local.properties` | `android` |
| `RELEASE_KEY_ALIAS` | CI secret / `local.properties` | `release-key` |
| `RELEASE_KEY_PASSWORD` | CI secret / `local.properties` | `android` |

- `keystore/release-keystore.jks` — never committed to git; added to `.gitignore`.
- `local.properties` — local-only, not committed.

## 4. CI/CD Pipeline

### GitHub Actions workflow (`.github/workflows/release.yml`)

Triggers:
- Push to `main` → debug build + test
- Tag `v*` → signed release APK + GitHub Release

Jobs:

| Job | Trigger | Steps |
|-----|---------|-------|
| `build-debug` | push to `main` | Assemble debug APK, run unit tests |
| `release` | tag `v*` | Decrypt keystore, assemble release APK, sign, publish to GitHub Releases |

### Deployment strategy

- Tag-driven: `git tag v1.0.0 && git push origin v1.0.0`
- `versionName` derived from tag (e.g. `v1.0.0` → `1.0.0`)
- `versionCode` auto-incremented by CI from the last committed value

## 5. Mobile Config — BASE_URL

The app's `BASE_URL` setting controls which backend it syncs with.

| Setting | Phase 1 (tunnel) | Production |
|---------|------------------|------------|
| `BASE_URL` | `https://abc123.trycloudflare.com` | `https://financeapp.example.com` |
| Editable in Settings | Yes | Yes |
| Default ships with app | Tunnel URL (dev) | Production URL |

- No app release needed to switch from tunnel to production — user changes `BASE_URL` in Settings.
- For new installs, ship the production domain as the default `BASE_URL`.
- Admin config writes (savings/loan configs) go to the same production API endpoints.

## 6. Release Process

1. Finalize changes on `main`.
2. Create and push tag: `git tag v1.0.0 && git push origin v1.0.0`.
3. CI triggers release workflow → builds signed APK → publishes to GitHub Releases.
4. Communicate new version to users (in-app update prompt or release notes).
5. Users on tunnel can keep using it; new installs get production `BASE_URL` default.

## 7. Risks

| Risk | Mitigation |
|------|------------|
| Keystore loss | Back up release keystore securely; never commit to git. |
| Wrong `BASE_URL` default | Settings screen allows override without app update. |
| Release APK unsigned | CI enforces signing config before publishing. |
| Version collision | `versionCode` auto-incremented; enforce unique git tags. |

## 8. Obsidian Graph View Notes

- **Wikilinks**: `[[../../FinanceApp-KnowledgeBase/DEPLOYMENT_PLAN.md]]`, `[[../../FinanceApp-KnowledgeBase/MOBILE_ANDROID_PLAN.md]]`, `[[RELEASE_PLAN.md]]`, `[[IMPLEMENTATION_PLAN.md]]`
- **Tags**: `financeapp`, `mobile`, `android`, `deployment`, `release`