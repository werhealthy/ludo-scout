#!/usr/bin/env bash
set -eu
: "${GSON_JAR:?Set GSON_JAR to a local Gson 2.10.1 jar (test-only)}"
root_dir="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="$root_dir/.regression-build"
mkdir -p "$output_dir"
java_dir="${JAVA_HOME:+$JAVA_HOME/bin/}"
"${java_dir}javac" -cp "$GSON_JAR" -d "$output_dir" "$root_dir"/regression/org/json/*.java "$root_dir/regression/Regression.java" "$root_dir"/app/src/main/java/it/vintedaffari/app/{PurchaseMath,GalleryPosition,EmbeddedJson,VintedStructuredData,QualityComposite}.java
"${java_dir}java" -cp "$output_dir:$GSON_JAR" Regression "$root_dir/regression/quality-fixtures.tsv"
python3 "$root_dir/regression/migrations.py"
python3 "$root_dir/regression/performance_stability_v5124.py"
python3 "$root_dir/regression/pipeline_integrity_v51242.py"

"${java_dir}javac" -d "$output_dir" "$root_dir/regression/PipelineIntegrityRegression.java" "$root_dir/app/src/main/java/it/vintedaffari/app/BggProductCompatibility.java"
"${java_dir}java" -cp "$output_dir" PipelineIntegrityRegression

"${java_dir}javac" -d "$output_dir" "$root_dir/regression/XmlRegression.java" "$root_dir/app/src/main/java/it/vintedaffari/app/SafeXml.java"
"${java_dir}java" -cp "$output_dir" XmlRegression

"${java_dir}javac" -d "$output_dir" "$root_dir/regression/BundleRegression.java" "$root_dir"/app/src/main/java/it/vintedaffari/app/{BundlePlanner,BundleSuggestion,DealRecord,DealPolicy,PhotoIdentity,SearchRanking,RelativeTime}.java
"${java_dir}java" -cp "$output_dir" BundleRegression
