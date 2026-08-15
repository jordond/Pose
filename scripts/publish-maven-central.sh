#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/publish-maven-central.sh [version]

Loads Maven Central and signing credentials from .env, then runs:
  ./gradlew publishAllPublicationsToMavenCentral --no-configuration-cache -Ppose.version=<version>

Publishes dev.jordond.pose:annotations and dev.jordond.pose:processor.
If version is omitted, the default in build.gradle.kts is used.

Required .env keys:
  MAVEN_CENTRAL_USERNAME
  MAVEN_CENTRAL_PASSWORD
  SIGNING_KEY_ID
  SIGNING_PASSWORD
  GPG_KEY_CONTENTS

Optional:
  ENV_FILE=/path/to/.env scripts/publish-maven-central.sh 0.7.0-jordond.1
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing .env file: $ENV_FILE" >&2
  echo "Create it with the required keys, or set ENV_FILE=/path/to/.env." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

required_vars=(
  MAVEN_CENTRAL_USERNAME
  MAVEN_CENTRAL_PASSWORD
  SIGNING_KEY_ID
  SIGNING_PASSWORD
  GPG_KEY_CONTENTS
)

missing_vars=()
for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    missing_vars+=("$var_name")
  fi
done

if (( ${#missing_vars[@]} > 0 )); then
  echo "Missing required .env keys: ${missing_vars[*]}" >&2
  exit 1
fi

export ORG_GRADLE_PROJECT_mavenCentralUsername="$MAVEN_CENTRAL_USERNAME"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$MAVEN_CENTRAL_PASSWORD"
export ORG_GRADLE_PROJECT_signingInMemoryKeyId="$SIGNING_KEY_ID"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$SIGNING_PASSWORD"
export ORG_GRADLE_PROJECT_signingInMemoryKey="$GPG_KEY_CONTENTS"

cd "$ROOT_DIR"

gradle_args=(publishAllPublicationsToMavenCentral --no-configuration-cache)
if [[ -n "${1:-}" ]]; then
  gradle_args+=("-Ppose.version=$1")
fi

./gradlew "${gradle_args[@]}"
