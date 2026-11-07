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

### Review Process
All PRs require review and passing CI checks before merging.
