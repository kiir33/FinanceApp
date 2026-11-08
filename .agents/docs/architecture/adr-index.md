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

## ADR-003: GitHub Project Board as State Machine (supersedes label-based status)

**Date**: 2026-11-08
**Status**: Accepted

**Decision**: Issue status (Backlog, Todo, In Progress, In Review, Done) is tracked on the FinanceApp GitHub project board, not via issue labels.

**Board**: https://github.com/users/kiir33/projects/1/views/4

- Move issues across board columns to reflect status (Backlog → Todo → In Progress → In Review → Done).
- **Ticket movement permissions:**
  - **Lead Engineer**: Backlog → Todo (only Todo items are ready to implement); In Review → Done only after verifying the PR was merged
  - **Junior Engineer**: Todo → In Progress → In Review
  - **PR Reviewer**: In Review → In Progress when changes are requested and rework is needed
- **Skip rule**: Skip the ticket-moving step if the ticket has already been manually moved to the correct column.
- Do NOT add `status/*` labels to issues — the board is the source of truth for status.
- Agent label (`agent/<model>-jN`) remains a permanent record of who built it (may still be applied as a label).
- PRs still use review-state labels: exactly one review-state label + permanent `pr/author-deepseek-jN`.

**Consequences**:
- Visual Kanban board via GitHub project board views
- Status transitions via board column moves (not `gh issue edit` labels)
- Human-readable progress at a glance via board views
- Single source of truth for issue status across mobile and backend repos

**See also**: `../../../FinanceApp-KnowledgeBase/AGENTS_AND_IMPLEMENTATION_FLOW.md` — agent roles and implementation flow (single source of truth).

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