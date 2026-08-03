#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
git diff --check
git diff --cached --check
if [[ "${DDD4J_REQUIRE_CLEAN_WORKTREE:-false}" == "true" ]] && [[ -n "$(git status --porcelain --untracked-files=all)" ]]; then
  echo "[FAIL] release verification requires a clean worktree" >&2
  git status --short >&2
  exit 1
fi
echo "[PASS] release worktree verification"
