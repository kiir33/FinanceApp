# Contributing Guidelines

## Pull Requests

### Link GitHub Issues
Every PR **must** reference the GitHub issue it addresses. Include a `Closes #<issue-number>` or `Refs #<issue-number>` line in the PR description so the issue is automatically closed or linked when the PR is merged.

Example:
```
Closes #47
```

or

```
Refs #22
```

### PR Description Template
Use the template in `.github/PULL_REQUEST_TEMPLATE.md` when opening a PR.

### Branch Naming
- Features: `feat/<description>`
- Fixes: `fix/<description>`
- CI/Build: `ci/<description>`

### PR Title Casing
PR titles must use proper casing. The descriptive portion should be Title Case (capitalize significant words; keep prepositions/articles lowercase unless first).

Example: `Feat: First Regular Release v1.0.0 — Signing + Automated Version Bump`

Do not use all-lowercase titles (e.g. `feat: first regular release`).

### Review Process
All PRs require review and passing CI checks before merging.
