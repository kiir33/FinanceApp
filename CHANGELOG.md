# Changelog

All notable changes to FinanceApp are documented in this file.

## [v1.0.0] - First Release

### Features
- **Authentication & RBAC** — Login with password hashing (PBKDF2), account lockout after failed attempts, session management, admin/member role-based navigation, auto-logout on inactivity.
- **Admin Setup** — Initial master account generation with admin setup screen.
- **Member Dashboard** — Passbook view and active loan readouts for members.
- **Group Treasury Ledger** — Deposit/withdraw savings, disburse/repay loans, manual cash-bank transfers, operating expense tracking.
- **CSV Import/Export** — UTF-8 BOM CSV export for all 10 reports via SAF; CSV import with validation, dry-run preview, and automatic member account creation.
- **Encrypted Backup & Restore** — AES-256-GCM + PBKDF2 + GZIP `.apex` backup format; local file and Google Drive (`appDataFolder`) backup/restore; full wipe & restore cycle with data integrity verification.
- **Security Hardening** — `FLAG_SECURE` to prevent screenshots/recording, `android:allowBackup="false"`, non-debuggable release build.

### Commits
- `9da1f22` feat: safe worktree removal and stale cleanup — git worktree commands
- `8fab0f7` feat: agentic workflow changes — rules and enforcement (#58)
- `2f37b7c` feat(member): Auto-suggest next Member ID in Add Member form (#54)
- `fb1022d` feat(loan): Add configurable loan-to-savings ratio with Settings screen (#52) (#56)
- `61224dd` feat(validation): Add unique validation for phone number and Member ID+Name combination (#50) (#55)
- `e6c5c43` fix(admin): Remove member ID from admin setup and exclude admin from member list (#51) (#53)
- `9d5bc23` feat: add logout functionality with audit logging to admin and member dashboards (#49)

### Release Notes
- Signed release APK published via GitHub Actions on tag `v1.0.0`.
- `versionCode` auto-incremented by CI on each release tag.
