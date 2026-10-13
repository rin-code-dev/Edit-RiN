#!/usr/bin/env bash
# ==============================================================================
# Edit:RiN Google Drive Non-deleting Copy Runner
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${SCRIPT_DIR}/sync-gdrive.py" "$@"
