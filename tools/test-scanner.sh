#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" app/src/main/java/com/clearos/ultra/{SafeScanner,TrashStore,QuarantineManager,ExcludedPaths,SimilarityIndex,RiskEngine,DisplayPaths}.java tools/{ScannerSelfTest,UltraCoreSelfTest,QuarantineRegressionTest,BatchPurgeSelfTest,V5RecoverySelfTest}.java
java -cp "$OUT" ScannerSelfTest
java -cp "$OUT" UltraCoreSelfTest

java -cp "$OUT" QuarantineRegressionTest
java -cp "$OUT" BatchPurgeSelfTest
java -cp "$OUT" V5RecoverySelfTest

# Parse-only Android Java verification; the Android SDK/API linker runs in CircleCI.
javac -d "$OUT" tools/JavaSyntaxCheck.java
java -cp "$OUT" JavaSyntaxCheck
