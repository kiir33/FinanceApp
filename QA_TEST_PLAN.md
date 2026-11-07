# FinanceApp - Hardening & QA Test Plan (Phase 7)

## Overview
This document outlines the manual and automated test procedures for Phase 7 hardening and QA verification.

## Security Verification Checklist

### 1. FLAG_SECURE - Screenshot Prevention
- [ ] **Test**: Launch app, navigate to any screen
- [ ] **Action**: Attempt screenshot (Power + Volume Down)
- [ ] **Expected**: "Couldn't capture screenshot" or blank/black image
- [ ] **Test**: Attempt screen recording via ADB or built-in recorder
- [ ] **Expected**: Recording shows black frames or fails
- [ ] **Verify**: `MainActivity.onCreate()` contains `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)`

### 2. allowBackup=false - Backup Prevention
- [ ] **Verify**: `AndroidManifest.xml` has `android:allowBackup="false"`
- [ ] **Test**: Build release APK (`./gradlew assembleRelease`)
- [ ] **Action**: `adb backup -f backup.ab com.example.financeapp`
- [ ] **Expected**: Backup fails or produces empty backup
- [ ] **Verify**: No `android:allowBackup="true"` anywhere in manifest

### 3. Release Build Verification
- [ ] **Build**: `./gradlew assembleRelease`
- [ ] **Verify**: `android:debuggable="false"` (default for release)
- [ ] **Verify**: No `android:testOnly="true"` in manifest
- [ ] **Check**: APK Analyzer - no debug symbols in classes.dex
- [ ] **Check**: No hardcoded secrets, API keys, or passwords in release build

## Edge Case Testing

### 4. Password & Lockout Behavior
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Invalid password | Enter wrong password 3x | Error shown, failedAttempts=3 |
| Lockout trigger | Enter wrong password 5x | Account locked, lockout message shown |
| Lockout expiry | Wait 15+ minutes, try correct password | Login succeeds, failedAttempts reset |
| Successful login resets | Fail 3x, then correct password | Login succeeds, failedAttempts=0 |
| Inactive account | Deactivate user, try login | "Account is inactive" error |

### 5. Corrupted .apex File Handling
| Corruption Type | Test Method | Expected Result |
|-----------------|-------------|-----------------|
| Wrong magic bytes | Modify first 4 bytes | "Invalid file format" error |
| Wrong version | Change version to 2 | "Unsupported backup version" error |
| Truncated file | Remove last 100 bytes | Decryption/parse error |
| Tampered ciphertext | Flip bit in ciphertext | GCM authentication error |
| Tampered GCM tag | Modify last 16 bytes | GCM authentication error |
| Wrong passphrase | Use different passphrase | "Wrong passphrase" error |

### 6. Business Rule Enforcement
| Rule | Test | Expected |
|------|------|----------|
| 80% savings cap | Borrower 50k + Guarantor 50k = 100k cap, request 90k | Rejected |
| 80% savings cap | Borrower 50k + Guarantor 50k = 100k cap, request 70k | Accepted |
| Treasury negative balance | Cash=5k, withdraw 10k | Rejected |
| Treasury negative balance | Cash=20k, withdraw 10k | Accepted |
| Loan disbursement > treasury | Treasury=50k, loan=80k (within 80% cap) | Rejected |

### 7. Full Wipe & Restore Cycle
| Scenario | Steps | Verification |
|----------|-------|--------------|
| Local SAF backup/restore | 1. Create data 2. Backup to file 3. Clear app data 4. Restore from file | All data matches original |
| Google Drive backup/restore | 1. Create data 2. Backup to Drive 3. Clear app data 4. Restore from Drive | All data matches original |
| Cross-device restore | Backup on device A, restore on device B | Data integrity maintained |

## Automated Test Suite

### Unit Tests (Run: `./gradlew test`)
- `PasswordHasherTest` - Hashing, verification, edge cases
- `BackupCryptoEngineTest` - Encryption/decryption, corruption handling
- `BankRepositoryTest` - 80% cap, treasury balance validation
- `AuthServiceTest` - Login, lockout, password change

### Instrumented Tests (Run: `./gradlew connectedAndroidTest`)
- Database migration tests
- DAO integration tests
- Navigation flow tests

## Release Preparation

### Pre-Release Checklist
- [ ] All unit tests pass
- [ ] All instrumented tests pass
- [ ] No lint errors (`./gradlew lint`)
- [ ] ProGuard/R8 rules verified
- [ ] Version code/name updated in `build.gradle.kts`
- [ ] CHANGELOG updated
- [ ] Release APK is signed (`apksigner verify --verbose app-release.apk`)
- [ ] Version code/name match the git tag
- [ ] `*.jks` / `*.keystore` not present in git history
- [ ] Git tag created: `git tag -a v1.0.0 -m "Release v1.0.0"`
- [ ] Tag pushed: `git push origin v1.0.0`

### Post-Release Verification
- [ ] Release APK installs on clean device
- [ ] First-run admin setup works
- [ ] Member login works
- [ ] All admin features accessible
- [ ] Backup/restore works (local + Drive)
- [ ] No crashes in logcat during normal use

## Known Limitations / Future Improvements
1. Biometric unlock for app (optional, not implemented)
2. Automatic cloud sync (not in scope - offline-first)
3. Multi-device conflict resolution (not applicable - appDataFolder is per-account)

## Sign-Off
| Role | Name | Date | Signature |
|------|------|------|-----------|
| Lead Engineer | | | |
| QA Engineer | | | |