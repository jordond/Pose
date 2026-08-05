#!/usr/bin/env bash
#
# Extracts one version's section out of CHANGELOG.md for use as GitHub Release
# notes. Used by both release workflows; also handy locally to preview what a
# release will look like before you tag:
#
#     .github/scripts/extract-changelog.sh 0.5.0 --title
#     .github/scripts/extract-changelog.sh 0.5.0
#
# Exits non-zero when the version has no section, so a release can't be cut
# with empty notes.

set -euo pipefail

VERSION="${1:?usage: extract-changelog.sh <version> [--title]}"
MODE="${2:-body}"
CHANGELOG="${CHANGELOG_PATH:-CHANGELOG.md}"

if [[ ! -f "$CHANGELOG" ]]; then
    echo "error: $CHANGELOG not found (run from the repo root)" >&2
    exit 1
fi

# Section heading looks like: `## [0.5.0] - Some subtitle`
heading="$(grep -m1 -F "## [${VERSION}]" "$CHANGELOG" || true)"
if [[ -z "$heading" ]]; then
    echo "error: no '## [${VERSION}]' section in $CHANGELOG — add one before tagging." >&2
    exit 1
fi

if [[ "$MODE" == "--title" ]]; then
    # Strip the `## [x.y.z]` prefix and any leading dash to leave the subtitle.
    subtitle="$(sed -E 's/^## \[[^]]+\][[:space:]]*[-–—]?[[:space:]]*//' <<<"$heading")"
    if [[ -n "$subtitle" ]]; then
        echo "${VERSION} — ${subtitle}"
    else
        echo "${VERSION}"
    fi
    exit 0
fi

# Body: everything between this heading and the next `## [` heading, with
# leading/trailing blank lines trimmed. Done in awk rather than `sed | tac`
# because `tac` is GNU-only — this script runs on macOS locally and Ubuntu in CI.
awk -v ver="$VERSION" '
    index($0, "## [" ver "]") == 1 { found = 1; next }
    found && /^## \[/              { exit }
    found {
        if ($0 ~ /^[[:space:]]*$/) {
            if (started) pending++          # hold interior blanks
        } else {
            while (pending-- > 0) print ""  # emit them only once real content follows
            pending = 0
            started = 1
            print
        }
    }
' "$CHANGELOG"
