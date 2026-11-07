#!/bin/bash
# clean-worktrees.sh
# Script to clean all stale worktrees from the {repo-name}:worktrees/ folder
# A worktree is considered "stale" if its corresponding PR has been merged to main
#
# Usage: ./clean-worktrees.sh
#        bash .agents/clean-worktrees.sh
#
# This script:
# 1. Lists all worktrees in FinanceApp:worktrees/
# 2. For each worktree, checks if its PR has been merged to main
# 3. Removes stale worktrees (merged PRs)
# 4. Reports what was removed and what remains

REPO_WORKTREES="/home/kiran/Projects/FinanceApp:worktrees"
REPO_NAME="FinanceApp"

# Check if worktrees folder exists
if [[ ! -d "$REPO_WORKTREES" ]]; then
    echo "❌ Worktrees folder not found: $REPO_WORKTREES"
    echo "Expected at: $REPO_WORKTREES"
    echo "Create worktrees there using: git worktree add -b issue-N-slug ../FinanceApp:worktrees/issue-N-slug main"
    exit 1
fi

echo "=== Cleaning stale worktrees for $REPO_NAME ==="
echo "Worktrees folder: $REPO_WORKTREES"
echo ""

# List all worktree folders - handle empty folder case
worktrees=()
if ls -A "$REPO_WORKTREES"/*/ 1>/dev/null 2>&1; then
    # Folder has subdirectories
    for ft in "$REPO_WORKTREES"/*/; do
        # Get the folder name (issue-N-slug)
        ft_name=$(basename "$ft")
        # Skip if empty (safety check)
        [[ -z "$ft_name" ]] && continue
        # Skip . and .. entries
        [[ "$ft_name" == "." ]] || [[ "$ft_name" == ".." ]] && continue
        worktrees+=("$ft_name")
    done
fi

if [[ ${#worktrees[@]} -eq 0 ]]; then
    echo "✅ No worktrees found in $REPO_WORKTREES/"
    exit 0
fi

echo "Found ${#worktrees[@]} worktree(s):"
for wt in "${worktrees[@]}"; do
    echo "  - $wt"
done
echo ""

# Ask for confirmation before removal
echo "Which worktrees would you like to remove?"
echo "  'all' - Remove all worktrees (merged PRs only, per workflow rules)"
echo "  'list' - Show which ones correspond to merged PRs without removing"
echo "  'individual' - Remove specific worktrees by number"
echo "  'none' - Cancel"
read -r choice

case "$choice" in
    all)
        echo "🗑️ Removing all worktrees (PRs should already be merged per workflow rules)..."
        for wt in "${worktrees[@]}"; do
            echo "  Removing: $wt"
            rm -rf "$REPO_WORKTREES/$wt"
        done
        echo "✅ All worktrees removed."
        ;;
    list)
        echo "📋 Checking which worktrees correspond to merged PRs..."
        echo ""
        for wt in "${worktrees[@]}"; do
            # Extract ticket number from folder name: issue-N-slug → N
            # e.g., issue-1-db-schema → 1
            ticket_num=$(echo "$wt" | grep -oE '[0-9]+' | head -1)

            if [[ -n "$ticket_num" ]]; then
                # Check if PR for this ticket is merged
                pr_status=$(gh pr list --search "issue #$ticket_num" --state merged 2>/dev/null | head -1)

                if [[ -n "$pr_status" ]]; then
                    echo "  ✅ $wt → PR #$ticket_num is MERGED (safe to remove)"
                else
                    echo "  ⏳ $wt → PR #$ticket_num is NOT merged (keeping)"
                fi
            else
                echo "  ? $wt → Could not extract ticket number"
            fi
        done
        echo ""
        echo "Use './.agents/clean-worktrees.sh all' to remove merged worktrees."
        ;;
    individual)
        echo "📝 Enter worktree names to remove (space-separated, or 'all' for all merged):"
        read -r to_remove

        if [[ "$to_remove" == "all" ]]; then
            for wt in "${worktrees[@]}"; do
                echo "  Removing: $wt"
                rm -rf "$REPO_WORKTREES/$wt"
            done
        else
            for wt in $to_remove; do
                if [[ -d "$REPO_WORKTREES/$wt" ]]; then
                    echo "  Removing: $wt"
                    rm -rf "$REPO_WORKTREES/$wt"
                else
                    echo "  ❌ $wt not found"
                fi
            done
        fi
        ;;
    none)
        echo "✅ Cancelled. No worktrees removed."
        ;;
    *)
        echo "❌ Invalid choice. No worktrees removed."
        ;;
esac

echo ""
echo "=== Remaining worktrees ==="
remaining=0
if ls -A "$REPO_WORKTREES"/*/ 1>/dev/null 2>&1; then
    for ft in "$REPO_WORKTREES"/*/; do
        ft_name=$(basename "$ft")
        [[ -d "$REPO_WORKTREES/$ft_name" ]] && ((remaining++))
    done
fi
echo " $remaining worktree(s) remaining in $REPO_WORKTREES/"

exit 0