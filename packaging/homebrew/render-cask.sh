#!/usr/bin/env bash
# Prints the Homebrew cask for one release to stdout.
#
#   packaging/homebrew/render-cask.sh 1.4.0 <sha256 of ContainerDashboard-1.4.0.dmg>
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <version> <dmg sha256>" >&2
  exit 2
fi

version="$1"
sha256="$2"

# Stable versions only: the tap never carries a pre-release.
if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: version must look like 1.2.3, got '$version'" >&2
  exit 1
fi
if ! [[ "$sha256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "error: sha256 must be 64 lowercase hex characters, got '$sha256'" >&2
  exit 1
fi

template="$(dirname "$0")/container-dashboard.rb"

# Drop the template's leading comment block; the tap gets only the cask.
sed -e '/^#/d' -e "s/@VERSION@/$version/" -e "s/@SHA256@/$sha256/" "$template"
