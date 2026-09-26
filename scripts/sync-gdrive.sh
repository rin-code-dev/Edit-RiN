#!/usr/bin/env bash
# ==============================================================================
# Edit:RiN Google Drive Append-Only Sync Runner
# ==============================================================================
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "${SCRIPT_DIR}/sync-gdrive.py" "$@"
