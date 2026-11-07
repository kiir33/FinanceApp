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

### PR Title Casing — Release PRs

Release PRs must follow this exact format:

```
Release: v1.0.0
```

Or with a release type for major/minor/patch:

```
Major Release: v2.0.0
Minor Release: v1.1.0
Patch Release: v1.0.1
```

Do not use lowercase prefixes (e.g. `release: v1.0.0` or `feat: first release`). Capitalize `Release` and use the `vx.x.x` version format.

Non-release PRs may use conventional commit prefixes (`feat:`, `fix:`, `ci:`, etc.) with Title Case.

### Review Process
All PRs require review and passing CI checks before merging.
