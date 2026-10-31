# Multi-Agent Pipeline Workflow

## Phase 0: Setup (Lead)
1. Parse IMPLEMENTATION_PLAN.md → create GitHub issues with specs
2. Create labels: phase-N, type, agent, status, PR labels
3. Create worktrees for independent tickets (wave 1)
4. Assign agent IDs (j1..jN) to issues
5. Set initial `status/in-progress` + `agent/deepseek-jN`
6. Document in `.agents/docs/reports/wave-N-setup.md`

## Phase 1: Implementation (Juniors — Parallel)
For each ticket in wave:
1. Junior reads issue + plan sections
2. Implements in worktree
3. Commits with co-author
4. Pushes branch
5. Opens PR with `Closes #N`
6. Lead updates: `status/in-review` + `agent/glm-reviewer` + `pr/reviewed-by-glm`
7. Junior replies with PR URL, sessionID

## Phase 2: Review (GLM — Per PR)
1. Lead spawns GLM reviewer with PR + issue context
2. GLM reviews diff, posts `REQUEST_CHANGES` or `COMMENT`
3. If `REQUEST_CHANGES`:
   - Lead updates: `status/changes-requested` + `pr/changes-requested`
   - Lead sends change requests to junior's sessionID
   - Junior fixes, commits, pushes → auto-updates PR
   - Lead updates: `status/in-review` + `pr/reviewed-by-glm`
   - Loop to step 2 (max 3 rounds)
4. If clean:
   - Lead updates: `status/approved` + `agent/nemotron-lead` + `pr/approved-by-nemotron` + `pr/merge-ready`

## Phase 3: Lead Approval
1. Lead personally re-reads final diff for each PR
2. If satisfied: posts approval comment with co-author
3. If blockers: one more fix round to junior
4. PRs remain unmerged — human merges from GitHub

## Phase 4: Wave Completion
1. Lead updates all merged PR issues: `status/done`
2. Next wave unblocked → repeat from Phase 0
3. Final report: PR URLs, review loops, verdicts, escalations

## Wave 1 (Foundation) — 6 Parallel
| Ticket | Worktree | Agent |
|--------|----------|-------|
| #1 Room v7 schema | issue-1-db-schema | j1 |
| #2 PasswordHasher | issue-2-password-hasher | j2 |
| #3 SessionManager | issue-3-session-manager | j3 |
| #12 CsvExporter | issue-12-csv-exporter | j4 |
| #16 BackupCryptoEngine | issue-16-backup-crypto | j5 |
| #18 Build config | issue-18-build-config | j6 |

## Wave 2 (Core Features) — 4 Parallel
| Ticket | Worktree | Agent |
|--------|----------|-------|
| #4 AuthService | issue-4-auth-service | j1 |
| #10 Treasury Sync | issue-10-treasury-sync | j2 |
| #14 CsvImporter | issue-14-csv-importer | j3 |
| #17 BackupManager | issue-17-backup-manager | j4 |

## Wave 3 (UI) — 8 Parallel (After Wave 1+2 merged)
| Ticket | Depends On |
|--------|------------|
| #5 AdminSetup | #1, #2, #4 |
| #6 LoginScreen | #4 |
| #7 Nav Routing | #3, #5, #6 |
| #8 MemberDashboard | #1, #3, #7 |
| #9 UserManagement | #1, #4, #7 |
| #11 GroupAccountScreen | #7, #10 |
| #13 ReportsDashboard | #1, #10, #12 |
| #15 CsvImportScreen | #14 |

## Escalation Protocol
- After 3 review loops: `status/changes-requested` + lead comment on issue
- Security blockers: immediate lead intervention
- Build failures (env): documented, not junior fault