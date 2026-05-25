#!/usr/bin/env bash
# Re-run the Khasab import chain against the running stack (does NOT touch
# containers). Use when data is corrupted or you want to refresh from source.
# Assumes the bipros-api container is already up and admin / admin123 works.
set -eo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# Set --force on deploy.sh to drop the "skip if KHASAB-2026 exists" guard.
exec ./deploy.sh --skip-build --force
