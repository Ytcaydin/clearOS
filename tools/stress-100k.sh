#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
javac -d "$DIR" app/src/main/java/com/clearos/ultra/SafeScanner.java tools/LargeTreeSmokeTest.java
java -Xmx512m -cp "$DIR" LargeTreeSmokeTest "${1:-100000}"
