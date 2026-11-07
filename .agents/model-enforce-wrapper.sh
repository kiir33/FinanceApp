#!/bin/bash
# model-enforce-wrapper.sh
# Opt-in model role enforcement for code implementation prompts
# Usage: ./model-enforce-wrapper.sh "<your prompt>"
#        ./model-enforce-wrapper.sh --model-enforce "<your prompt>"
#        env MODEL_ENFORCE=1 ./model-enforce-wrapper.sh "<your prompt>"
#
# This script checks if the prompt involves CODE IMPLEMENTATION.
# If yes AND --model-enforce was explicitly used, it prepends the
# model role instruction from .agents/config/models.yaml.
# If --model-enforce is NOT used, prompt passes through unchanged.
# If prompt is NOT code implementation, always passes through unchanged.
#
# HEURISTICS (if ANY match → CODE_IMPLEMENTATION):
# - SQL, Dao, Room, migration, entities
# - PBKDF2, hashPassword, SessionManager, crypto
# - CsvExporter, export, UTF-8 BOM, RFC 4180
# - BackupCryptoEngine, .apex, encrypt/decrypt
# - NavKey, Navigation, Compose, Compose UI
# - Business rules: 80% cap, interest rates, guarantor logic
# - compileDebugKotlin, test, given/when/then
# - Any Dao method, DB table, entity reference

PROMPT="$1"

# Determine if model enforcement should be activated
FORCE_ENFORCE=false

# If first arg is exactly --model-enforce, enforcement is enabled
# and the actual prompt is the second arg
if [[ "$1" == "--model-enforce" ]] && [[ -n "$2" ]]; then
    FORCE_ENFORCE=true
    PROMPT="$2"
# Check env var for opt-in (set MODEL_ENFORCE=1 in shell env)
elif [[ "${MODEL_ENFORCE:-}" == "1" ]]; then
    FORCE_ENFORCE=true
fi

# Heuristics check - detect if prompt involves code implementation
detect_code_implementation() {
    local p="$1"
    local p_lower=$(echo "$p" | tr '[:upper:]' '[:lower:]')

    # SQL/Dao/DB patterns
    if echo "$p_lower" | grep -qE "(migrate|dao|room|query|insert|update|select|deletefrom|entity|table)"; then
        return 0
    fi

    # Crypto/Auth patterns
    if echo "$p_lower" | grep -qE "(pbkdf2|hashpassword|verifypassword|sessionmanager|crypto|aes|encryption)"; then
        return 0
    fi

    # CSV/Export patterns
    if echo "$p_lower" | grep -qE "(csvexporter|export|utf-8|bom|rfc 4180|csv)"; then
        return 0
    fi

    # Backup/.apex patterns
    if echo "$p_lower" | grep -qE "(backupcryptoengine|apex|encrypt|decrypt|\.apex)"; then
        return 0
    fi

    # Navigation/Compose patterns
    if echo "$p_lower" | grep -qE "(navkey|navigation|compose|remembernav|stateflow)"; then
        return 0
    fi

    # Business rules patterns
    if echo "$p_lower" | grep -qE "(80%.|interestrate|guarantor|loandisbursement|savingscap)"; then
        return 0
    fi

    # Test/build patterns
    if echo "$p_lower" | grep -qE "(compiledebugkotlin|unittest|giventhenthen|kotlin)"; then
        return 0
    fi

    # General code patterns - Dao methods, entities, etc.
    if echo "$p_lower" | grep -qE "(savingsdao|clientdao|loandao|userdao|groupaccountdao)"; then
        return 0
    fi

    return 1
}

# Detect code implementation in prompt
if detect_code_implementation "$PROMPT"; then
    IS_CODE=1
else
    IS_CODE=0
fi

# Decide: enforce only if (code implementation AND enforcement enabled)
ENFORCE=false
if [[ "$IS_CODE" -eq 1 ]] && [[ "$FORCE_ENFORCE" == "true" ]]; then
    ENFORCE=true
fi

# Model role instruction to prepend (worker role by default when enforcement activated)
PREPEND_INSTRUCTION="# MODEL ROLE ENFORCEMENT
# Role: worker (implementation) per .agents/config/models.yaml
# Primary: nvidia/nemotron-3-nano-omni-30b-a3b-reasoning
# Fallbacks: nvidia/nemotron-3-nano-omni-30b-a3b-reasoning, nvidia/z-ai/glm-5.3-flash
# Co-author: nvidia/nemotron-3-nano-omni-30b-a3b-reasoning nvidia/nemotron-3-nano-omni-30b-a3b-reasoning@users.noreply.github.com
#
# ⚠️ ACTIVATE: Using model chain from .agents/config/models.yaml for code implementation.
#   Fallback chain must be respected. See .agents/config/models.yaml for full specs.
#
"

# If enforcement active and code implementation detected, prepend instruction
if [[ "$ENFORCE" == "true" ]] && [[ "$IS_CODE" -eq 1 ]]; then
    echo "$PREPEND_INSTRUCTION" > /tmp/model_enforce_header.txt
    echo "## MODEL ENFORCEMENT ACTIVATED"
    echo "Prompt involves code implementation + model enforcement enabled"
    echo "Header prepended. Prompt below:"
    echo "=================================="
    cat /tmp/model_enforce_header.txt
    echo ""
    echo "ORIGINAL PROMPT:"
    echo "=================================="
    echo "$PROMPT"
else
    # No enforcement - pass through unchanged
    echo "## MODEL ENFORCEMENT NOT ACTIVATED"
    # Show helpful message only if prompt is code but enforcement wasn't requested
    if [[ "$IS_CODE" -eq 1 ]] && [[ "$FORCE_ENFORCE" != "true" ]]; then
        echo ""
        echo "Note: Prompt involves code implementation but model enforcement was NOT used."
        echo "Activate with:"
        echo "  ./model-enforce-wrapper.sh --model-enforce \"<your prompt>\""
        echo "Or:              export MODEL_ENFORCE=1 && ./model-enforce-wrapper.sh \"<your prompt>\""
    fi
    echo ""
    echo "ORIGINAL PROMPT (unchanged):"
    echo "=================================="
    echo "$PROMPT"
fi