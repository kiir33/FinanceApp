# Development Workflow Rules
# ⚠️ This file combines GLOBAL BASE RULES (from ~/.agents/) with REPO-SPECIFIC overrides.
# When working on this repo, agents will read BOTH sets of rules.
# For other repos, copy this structure with appropriate overrides.

# --- GLOBAL BASE RULES (from ~/.agents/rules/software-rules.md) ---
# These apply to ALL repos. Do not duplicate them here unless overriding/extending.

# Global Model Configuration (see ~/.agents/config/models.yaml)
# Role-Specific Priority Chains defined in ~/.agents/config/models.yaml
# Model Fallback Protocol defined in ~/.agents/rules/software-rules.md
# Co-Author Attribution defined in ~/.agents/rules/software-rules.md
# General Code Quality Standards defined in ~/.agents/rules/software-rules.md
# Worktree Isolation defined in ~/.agents/rules/software-rules.md
# Prompt Enforcement defined in ~/.agents/rules/software-rules.md
# Daily Workflow Checklist defined in ~/.agents/rules/software-rules.md
# Role Definitions defined in ~/.agents/rules/software-rules.md

# --- REPO-SPECIFIC RULES (this file) ---

## Multi-Agent Pipeline Protocol

### Team Composition
- **Lead Engineer** (Nemotron 3 Ultra): Orchestrates, reviews, approves
- **Junior Engineers** (DeepSeek v4.1 Flash): Implement code in isolated worktrees
- **PR Reviewers** (GLM 5.3 Flash): Review for correctness, security, style

### Worktree Isolation
- Each ticket gets its own worktree — all work happens inside worktree only — no cross-contamination
- **Worktree root folder**: `{repo-parent}/{repo-name}:worktrees/` (sibling to repo, NOT inside it)
- **For this repo**: `/home/kiran/Projects/{repo-name}:worktrees/`
  - This folder is at the same level as the repo (`/home/kiran/Projects/FinanceApp/`)
  - Both are children of `/home/kiran/Projects/`
- **Branch naming**: `issue-{N}-{slug}` (e.g., `issue-4-auth-service`)
- **Each worktree folder name**: `issue-{N}-{slug}` (e.g., `issue-1-db-schema`, `issue-2-password-hasher`)

### Worktree Structure Visualization
```
# Parent directory level
/home/kiran/Projects/
├── FinanceApp/              ← Repo folder (main working directory)
├── FinanceApp:worktrees/    ← Worktrees root folder (sibling to repo)
│   ├── issue-1-db-schema/   ← Ticket #1 worktree
│   ├── issue-2-password-hasher/ ← Ticket #2 worktree
│   └── issue-3-session-manager/  ← Ticket #3 worktree
└── ... (other projects)

# NOT inside the repo:
# /home/kiran/Projects/FinanceApp/worktrees/  ❌ (would pollute repo)
# /home/kiran/Projects/FinanceApp/.worktrees/  ❌ (hidden but still inside repo)

# CORRECT (current setup):
# /home/kiran/Projects/FinanceApp:worktrees/  ✅ (sibling to repo)
```

### Worktree Commands (configuration-based)
Customize the variables below for your environment, then use the commands:

```bash
# Configuration (easy to change in .agents/rules/development-workflow.md):
REPO_WORKTREES_PARENT="/home/kiran/Projects"       # Parent directory
REPO_NAME="FinanceApp"                              # Repo folder name
REPO_WORKTREES="$REPO_WORKTREES_PARENT/${REPO_NAME}:worktrees"  # Full path

# Derived paths:
# Repo:             /home/kiran/Projects/FinanceApp/
# Worktrees:        /home/kiran/Projects/FinanceApp:worktrees/

# Create a new worktree for ticket #N with slug "some-feature"
cd /home/kiran/Projects/FinanceApp
git worktree add -b issue-N-some-feature "$REPO_WORKTREES/issue-N-some-feature" main

# Switch to a worktree
cd "$REPO_WORKTREES/issue-N-some-feature"

# Remove a worktree (after PR merge) — USE GIT WORKTREE REMOVE (SAFER)
git worktree remove ../FinanceApp:worktrees/issue-N-some-feature

# Prune all stale/unlinked worktrees at once
git worktree prune

# List all worktrees
ls "$REPO_WORKTREES"
```

### Status Label Lifecycle (Issues)
```
status/backlog → status/in-progress → status/in-review → status/changes-requested → status/fixing → status/in-review → ... → status/approved → status/done
```
- Exactly ONE status label per issue at all times
- Agent label (`agent/deepseek-j{N}`) permanent record of who built it

### PR Label Lifecycle
```
pr/author-deepseek-j{N} (permanent) → pr/reviewed-by-glm → pr/changes-requested → pr/reviewed-by-glm → ... → pr/approved-by-nemotron + pr/merge-ready
```

### Git/GitHub Protocol

#### Commit Standards
- Message format: `feat: implement #N — <description>` or `fix: address review feedback for #N — <details>`
- Co-author required on EVERY commit:
  ```
  Co-authored-by: <model-id> <model-id>@users.noreply.github.com
  ```

#### PR Creation
- Base: `main` (always rebase onto latest main before PR)
- Title: `[PHASE-x] <descriptive title>`
- Body: `Closes #N\n\n<summary>\nPlan section refs`
- Auto-assign `pr/author-deepseek-j{N}` label

#### Rebase Protocol
- Before PR: `git fetch origin && git rebase origin/main`
- Remove ALL duplicate files from downstream PRs (entities, DAOs, migrations owned by foundation PR)
- Force push after rebase: `git push --force-with-lease`

#### Review Loop (Max 3 rounds)
1. Junior pushes fix → PR auto-updates
2. Lead spawns GLM reviewer on updated diff
3. GLM posts `REQUEST_CHANGES` with numbered items or `COMMENT` for minor nits
4. Loop until clean or escalated

## Code Quality Standards

### Security (Finance App)
- PBKDF2: 120k iterations, 256-bit key, 16-byte salt, Base64 NO_WRAP ✅
- AES-256-GCM with 12-byte IV, 128-bit tag ✅
- Constant-time comparison for password verification ✅
- All crypto operations on `Dispatchers.IO` ✅

### Database
- NEVER use `fallbackToDestructiveMigration()` — explicit migrations only ✅
- Migration timestamps: `strftime('%s','now')*1000` (runtime, not compile-time) ✅
- `@Transaction` for multi-table atomic operations ✅
- Room suspend DAO methods → use `db.withTransaction` not `runInTransaction` ✅

### Architecture
- Clean MVVM: Repository → ViewModel → Compose UI ✅
- Offline-first, no cloud sync required for core features ✅
- StateFlow for reactive UI state ✅
- Navigation 3 type-safe keys (`NavKey`) ✅

## Model Fallback Protocol

**Configuration Source**: `.agents/config/models.yaml` (single source of truth)

### Universal Fallback Chain (all roles)
When ANY model in the pipeline hits token limit / rate limit / 429 / 503 / context window exceeded:
1. **Log** to `.agents/logs/model-fallback.log`:
   ```json
   {"timestamp": "ISO8601", "role": "lead|worker|reviewer", "original_model": "...", "fallback_model": "...", "reason": "token_limit|rate_limit|context_overflow|error"}
   ```
2. **Read** next model from `.agents/config/models.yaml` → `<role>.fallbacks[]`
3. **Retry** the exact same operation with fallback model
4. **Continue** chain until success or all models exhausted

### Role-Specific Priority Chains (defined in `.agents/config/models.yaml`)

| Role | Primary | Fallbacks | Last Resort |
|------|---------|-----------|-------------|
| **Lead** | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` | `openrouter/nvidia/nemotron-3-ultra-550b-a55b:free` | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` |
| **Worker** | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` | `nvidia/z-ai/glm-5.3-flash` |
| **Reviewer** | `nvidia/z-ai/glm-5.3-flash` | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` |

> **To change models**: Edit `.agents/config/models.yaml` — all agents read from here.

### Implementation Notes
- Lead agent MUST implement fallback logic when spawning subagents (read from `models.yaml`)
- Each subagent invocation should wrap in try/catch with fallback retry
- Fallback log is append-only; rotate weekly
- If ALL models in chain fail → escalate to human with full log context
- Global settings (log path, triggers, co-author domain) in `models.yaml` → `global`

## Worktree Lifecycle Management

### Safe Worktree Removal (PREFERRED)
**Remove a worktree after PR merge** using git's native command (safer than `rm -rf`):

```bash
# From the repo root directory:
git worktree remove ../FinanceApp:worktrees/issue-N-some-feature

# Or from inside the worktree directory:
cd /home/kiran/Projects/FinanceApp:worktrees/issue-N-some-feature
git worktree remove
```

### Prune Stale Worktrees
Remove all orphaned/unlinked worktrees at once:

```bash
git worktree prune
```

Or with verbose output:
```bash
git worktree prune --dry-run   # Show what would be removed
git worktree prune             # Actually remove them
```

### Complete Worktree Lifecycle Example

```bash
# 1. Create worktree for ticket
cd /home/kiran/Projects/FinanceApp
git worktree add -b issue-N-some-feature ../FinanceApp:worktrees/issue-N-some-feature main

# 2. Make code changes in the worktree (all implementation happens HERE)
# ... code, tests, verification ...

# 3. Remove worktree safely (after PR merge or when done)
git worktree remove ../FinanceApp:worktrees/issue-N-some-feature

# 4. Or prune all stale worktrees at once
git worktree prune
```

## 10. PR Creation Rules (LOCAL)
# Repo-specific PR creation rules that build upon the GLOBAL rules (see ~/.agents/rules/software-rules.md ## 9)

### Local PR Creation Workflow
```
1. Navigate to repo root: cd /home/kiran/Projects/FinanceApp
2. Checkout main and pull latest: git checkout main && git pull origin main
3. Create feature branch using issue number and title:
   git checkout -b issue-{N}-{slug} main
   - Example: git checkout -b issue-4-auth-service main
   - slug format: lowercase, underscores instead of spaces
4. IMPLEMENTATION REQUIREMENT: All code changes MUST happen in a separate worktree
   - Worktree path: ../FinanceApp:worktrees/issue-{N}-{slug}
   - Example: /home/kiran/Projects/FinanceApp:worktrees/issue-4-auth-service
   - All coding, testing, and verification occurs in the worktree
   - Only after completion do these steps:
     a. Make changes in worktree
     b. cd to repo root
     c. Commit from worktree changes to the feature branch
     d. Push the branch: git push -u origin HEAD
     e. Create PR: gh pr create --base main --head issue-{N}-{slug}
5. Fill PR template:
   - Title: "[PHASE-x] <descriptive title>"
   - Body: "Closes #N\n\n<summary>\nPlan section refs"
   - Include model chain used and co-author info
6. Do NOT merge PR directly - human merges after Daily Workflow Checklist completion

### Local Branch Naming Conventions
- Format: `issue-{N}-{slug}` (matches global standard)
- N = issue number from issue tracking
- slug = lowercase, underscores instead of spaces, no special characters
- If working on a task without a formal issue: use `feature/{short-description}` as fallback

### Local PR Verification Checklist (ADDITIONAL to Global)
```
- [ ] Code changes implemented in separate worktree per workflow rules
- [ ] Worktree path follows: ../FinanceApp:worktrees/issue-{N}-{slug}
- [ ] Branch created from latest main (`git checkout main && git pull origin main`)
- [ ] Commit format: `feat: implement #N — <description>` or `fix: address #N — <details>`
- [ ] Co-author attribution on all commits: `Co-authored-by: <model> <model>@users.noreply.github.com`
- [ ] No `fallbackToDestructiveMigration()` in changes
- [ ] PBKDF2: 120k iterations, 256-bit key, 16-byte salt, Base64 NO_WRAP
- [ ] AES-256-GCM: 12-byte IV, 128-bit tag, constant-time compare
- [ ] Navigation 3 type-safe keys (`NavKey`) used correctly
- [ ] Room migrations explicit, no fallbackToDestructiveMigration()
- [ ] UTC-8 BOM CSV format if CSV changes included
- [ ] FLAG_SECURE set in MainActivity if UI changes
- [ ] android:allowBackup="false" in AndroidManifest.xml if applicable
```

### Local Worktree Integration Protocol
```
# Step 1: Create worktree (MANDATORY before coding)
cd /home/kiran/Projects/FinanceApp
git worktree add -b issue-N-some-feature ../FinanceApp:worktrees/issue-N-some-feature main

# Step 2: Code in worktree (ALL implementation happens HERE)
# - Make all code changes, tests, and verification in worktree
# - Do NOT commit directly to the branch yet

# Step 3: Commit from worktree to branch
cd /home/kiran/Projects/FinanceApp
# Copy/commit changes from worktree to the feature branch
git add .
git commit -m "feat: implement #N — <description>"
# Co-author auto-added per model role

# Step 4: Push branch
git push -u origin HEAD

# Step 5: Create PR
gh pr create --base main --head issue-{N}-{slug}

# Step 6: Remove worktree (after PR created and mergeable)
git worktree remove ../FinanceApp:worktrees/issue-N-some-feature
```

### Local Enforcement Notes
- The `--model-enforce` flag is STRONGLY RECOMMENDED for all code implementation prompts
- Global model chains from ~/.agents/config/models.yaml are primary, with local overrides
- Daily Workflow Checklist (section 7 of global + this section) must be 100% complete before merge
- Human operator merges only after checklist completion (no `--approve` by agents)

# Daily Workflow Checklist (MANDATORY for every PR)
*(Combining GLOBAL checklist with REPO-specific additions)*

```
- [ ] Model role enforcement verified: all implementation commits use correct model chain
  from .agents/config/models.yaml (primary + fallbacks respected)
- [ ] Co-author attribution verified on all commits/PRs:
  `Co-authored-by: <model> <model>@users.noreply.github.com`
- [ ] Fallback logs checked: `.agents/logs/model-fallback.log` 
  (rotate weekly; >2 fallbacks/wave → escalate)
- [ ] No `fallbackToDestructiveMigration()` in any merged code
- [ ] Security checks verified:
  - PBKDF2: 120k iterations, 256-bit key, 16-byte salt, Base64 NO_WRAP ✅
  - AES-256-GCM: 12-byte IV, 128-bit tag ✅
  - Constant-time comparison (`slowEquals`) implemented ✅
- [ ] Code standards verified:
  - Kotlin 2.x, Jetpack Compose M3, Navigation 3, Room ✅
  - Coroutines: `Dispatchers.IO` for DB/crypto, `Dispatchers.Default` for CPU ✅
  - Sealed interfaces for result types (`AuthResult`, `BackupResult`) ✅
  - Flow for reactive data ✅
  - RFC 4180 CSV with UTF-8 BOM ✅
- [ ] Architecture verified:
  - Clean MVVM: Repository → ViewModel → Compose UI ✅
  - Offline-first, no cloud sync required for core features ✅
  - StateFlow for reactive UI state ✅
  - Navigation 3 type-safe keys (`NavKey`) ✅
- [ ] Phase-specific checks:
  - Phase 1+2: Auth/crypto, DB migration SQL, FK, indices, transaction boundaries ✅
  - Phase 3+4: CSV RFC 4180, BOM, escaping, dry-run/commit atomicity ✅
  - Phase 5+6: .apex format, AES-GCM, PBKDF2, integrity checks, passphrase ✅
  - Phase 7: `FLAG_SECURE` in MainActivity, `allowBackup="false"` ✅
```

### Checklist Enforcement Protocol
1. **Lead agent** verifies ALL PRs before labeling `pr/merge-ready`
2. **GLM reviewer** posts review including checklist summary in body
3. **Human merge** cannot proceed without checklist completion (enforced via GitHub branch protection rules)
4. **Escalation**: If any checklist item fails → return to junior for fixes → re-review loop

### PR Creation Gate
- PRs will be HELD in `status/in-review` until Daily Workflow Checklist is complete
- Lead agent signs off on checklist completion before updating status to `pr/merge-ready`
- Human operator merges only after checklist `100% complete`

## Phase 7: Final Hardening & QA

- [ ] Add `FLAG_SECURE` in `MainActivity.onCreate()` to prevent unauthorized screenshots.
- [ ] Set `android:allowBackup="false"` in `AndroidManifest.xml`.
- [ ] Conduct edge-case testing: invalid passwords, corrupted `.apex` files, loan exceeding 80% limit, expired lockouts.
- [ ] Tag release commit in git.

