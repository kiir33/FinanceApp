# Architecture Decision Records

## ADR-001: Multi-Agent Parallel Implementation Pipeline

**Date**: 2026-10-31
**Status**: Accepted
**Context**: Need to implement 21 tickets from IMPLEMENTATION_PLAN.md efficiently

**Decision**: Use a 3-role agent pipeline:
- Lead Engineer (Nemotron 3 Ultra): orchestration, architecture review, approval
- Junior Engineers (DeepSeek v4.1 Flash): parallel implementation in isolated worktrees
- PR Reviewers (GLM 5.3 Flash): automated code review for correctness/security/style

**Consequences**:
- 6-8 tickets can proceed in parallel per wave
- Clear separation of concerns: code vs review vs approval
- GitHub issues + labels as Jira-style tracker
- Worktrees provide true isolation
- Co-author attribution maintains audit trail

## ADR-002: Worktree-Based Isolation

**Date**: 2026-10-31
**Status**: Accepted

**Decision**: Each ticket gets its own git worktree at `.local/share/opencode/worktree/{id}/issue-{N}-{slug}`

**Consequences**:
- True parallel development without branch conflicts
- Each agent sees clean repo state + only their changes
- Force-push to remote branch for PR updates
- Rebase onto main before PR to avoid conflicts

## ADR-003: GitHub Labels as State Machine

**Date**: 2026-10-31
**Status**: Accepted

**Decision**: Use GitHub labels for issue/PR state tracking:
- Issues: exactly one `status/*` label + permanent `agent/deepseek-jN`
- PRs: exactly one review-state label + permanent `pr/author-deepseek-jN`

**Consequences**:
- Visual Kanban board via GitHub label filters
- Automated transitions via `gh issue edit` / `gh pr edit`
- Human-readable progress at a glance

## ADR-004: Co-Author Attribution Convention

**Date**: 2026-10-31
**Status**: Accepted

**Decision**: Every commit, PR body, review, and comment ends with:
```
Co-authored-by: <model-id> <model-id>@users.noreply.github.com
```

**Models**:
- Lead: `nemotron-3-ultra-550b-a55b`
- Junior: `deepseek-v4.1-flash`
- Reviewer: `glm-5.3-flash`

**Consequences**:
- Full audit trail of AI contributions
- GitHub shows co-authors on commits
- Clear accountability per artifact

## ADR-005: Review Loop with Max 3 Rounds

**Date**: 2026-10-31
**Status**: Accepted

**Decision**: Junior → GLM review → fix loop, maximum 3 rounds, then escalate to human

**Consequences**:
- Prevents infinite review cycles
- Escalation documented in issue comments
- Lead makes final architectural judgment