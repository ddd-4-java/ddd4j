#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
exec python3 scripts/sbom.py "${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/sbom"
