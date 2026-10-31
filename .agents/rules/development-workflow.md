# Development Workflow Rules

## Multi-Agent Pipeline Protocol

### Team Composition
- **Lead Engineer** (Nemotron 3 Ultra): Orchestrates, reviews, approves
- **Junior Engineers** (DeepSeek v4.1 Flash): Implement code in isolated worktrees
- **PR Reviewers** (GLM 5.3 Flash): Review for correctness, security, style

### Worktree Isolation
- Each ticket gets its own worktree: `.local/share/opencode/worktree/{id}/issue-{N}-{slug}`
- All work happens inside worktree only — no cross-contamination
- Branch naming: `issue-{N}-{slug}` (e.g., `issue-4-auth-service`)

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

## Git/GitHub Protocol

### Commit Standards
- Message format: `feat: implement #N — <description>` or `fix: address review feedback for #N — <details>`
- Co-author required on EVERY commit:
  ```
  Co-authored-by: <model-id> <model-id>@users.noreply.github.com
  ```

### PR Creation
- Base: `main` (always rebase onto latest main before PR)
- Title: `[PHASE-x] <descriptive title>`
- Body: `Closes #N\n\n<summary>\nPlan section refs`
- Auto-assign `pr/author-deepseek-j{N}` label

### Rebase Protocol
- Before PR: `git fetch origin && git rebase origin/main`
- Remove ALL duplicate files from downstream PRs (entities, DAOs, migrations owned by foundation PR)
- Force push after rebase: `git push --force-with-lease`

### Review Loop (Max 3 rounds)
1. Junior pushes fix → PR auto-updates
2. Lead spawns GLM reviewer on updated diff
3. GLM posts `REQUEST_CHANGES` with numbered items or `COMMENT` for minor nits
4. Loop until clean or escalated

## Code Quality Standards

### Security (Finance App)
- PBKDF2: 120k iterations, 256-bit key, 16-byte salt, Base64 NO_WRAP
- AES-256-GCM with 12-byte IV, 128-bit tag
- Constant-time comparison for password verification
- All crypto operations on `Dispatchers.IO`

### Database
- NEVER use `fallbackToDestructiveMigration()` — explicit migrations only
- Migration timestamps: `strftime('%s','now')*1000` (runtime, not compile-time)
- `@Transaction` for multi-table atomic operations
- Room suspend DAO methods → use `db.withTransaction` not `runInTransaction`

### Architecture
- Clean MVVM: Repository → ViewModel → Compose UI
- Offline-first, no cloud sync required for core features
- StateFlow for reactive UI state
- Navigation 3 type-safe keys (`NavKey`)

### Co-Author Attribution
ALL GitHub interactions must end with:
```
---
Co-authored-by: <model-id> <model-id>@users.noreply.github.com
```

## Agent Documentation Structure
```
.agents/
├── config/           # Agent role configs
├── docs/
│   ├── architecture/ # ADRs
│   ├── decisions/    # Technical decisions
│   ├── reviews/      # Review summaries
│   └── reports/      # Implementation reports
├── prompts/          # System prompts per role
├── workflows/        # Pipeline definitions
└── logs/             # Ephemeral (gitignored)

docs/agents/          # Mirrored for commit
├── architecture/
├── decisions/
├── reviews/
└── reports/
```