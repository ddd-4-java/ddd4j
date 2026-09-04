#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/api"
BASELINE_VERSION="${DDD4J_API_BASELINE_VERSION:-}"

package_prefix() {
    local jar_path="$1"
    local packages
    packages="$(jar tf "${jar_path}" | awk '
        /^io\/ddd4j\/.*\.class$/ {
            sub(/\/[^/]+\.class$/, "", $0)
            gsub("/", ".", $0)
            print
        }
    ' | sort -u)"

    if [[ -z "${packages}" ]]; then
        return 1
    fi

    local first_package
    first_package="$(printf '%s\n' "${packages}" | head -n 1)"
    local -a common_segments
    IFS='.' read -r -a common_segments <<< "${first_package}"

    local package_name segment_count index
    while IFS= read -r package_name; do
        local -a segments
        IFS='.' read -r -a segments <<< "${package_name}"
        segment_count=${#common_segments[@]}
        if (( ${#segments[@]} < segment_count )); then
            segment_count=${#segments[@]}
        fi
        for ((index = 0; index < segment_count; index++)); do
            if [[ "${common_segments[index]}" != "${segments[index]}" ]]; then
                break
            fi
        done
        common_segments=("${common_segments[@]:0:index}")
    done <<< "${packages}"

    if (( ${#common_segments[@]} < 3 )); then
        return 1
    fi

    local result="${common_segments[0]}"
    for ((index = 1; index < ${#common_segments[@]}; index++)); do
        result+=".${common_segments[index]}"
    done
    printf '%s\n' "${result}"
}

if [[ -z "${BASELINE_VERSION}" || "${BASELINE_VERSION}" == *SNAPSHOT ]]; then
    echo "[FAIL] DDD4J_API_BASELINE_VERSION must identify a published non-SNAPSHOT 2.0.x baseline." >&2
    exit 1
fi

cd "${ROOT_DIR}"
rm -rf "${REPORT_DIR}"
mkdir -p "${REPORT_DIR}"

./mvnw -B -ntp -DskipTests clean package
CURRENT_VERSION="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"

if [[ -z "${CURRENT_VERSION}" || "${CURRENT_VERSION}" == *'['* ]]; then
    echo "[FAIL] Could not resolve the current Maven project version." >&2
    exit 1
fi

mapfile -t current_jars < <(find . -path '*/target/*.jar' -type f \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | sort)

if (( ${#current_jars[@]} == 0 )); then
    echo "[FAIL] No module JARs were built for API comparison." >&2
    exit 1
fi

failures=0
for current_jar in "${current_jars[@]}"; do
    jar_name="$(basename "${current_jar}")"
    artifact_id="${jar_name%-${CURRENT_VERSION}.jar}"
    if [[ "${artifact_id}" == "${jar_name}" ]]; then
        echo "[FAIL] Cannot derive artifactId from ${current_jar}." >&2
        failures=$((failures + 1))
        continue
    fi

    module_dir="${current_jar%/target/*}"
    api_package="$(package_prefix "${current_jar}" || true)"
    if [[ -z "${api_package}" ]]; then
        echo "[FAIL] Cannot derive an io.ddd4j API package from ${current_jar}." >&2
        failures=$((failures + 1))
        continue
    fi

    baseline_jar="${HOME}/.m2/repository/io/ddd4j/${artifact_id}/${BASELINE_VERSION}/${artifact_id}-${BASELINE_VERSION}.jar"
    if [[ ! -f "${baseline_jar}" ]]; then
        if ! ./mvnw -q -ntp org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
            -Dartifact="io.ddd4j:${artifact_id}:${BASELINE_VERSION}:jar"; then
            echo "[FAIL] Published baseline JAR is unavailable: io.ddd4j:${artifact_id}:${BASELINE_VERSION}." >&2
            failures=$((failures + 1))
            continue
        fi
    fi

    echo "[INFO] Comparing ${artifact_id} (${api_package}.**) with ${BASELINE_VERSION}."
    if ! ./mvnw -B -ntp -pl "${module_dir}" -am -Papi-compatibility \
        -Dddd4j.api.baseline.version="${BASELINE_VERSION}" \
        -Dddd4j.api.include="${api_package}.**" \
        -DskipTests verify; then
        echo "[FAIL] Binary API incompatibility detected in ${artifact_id}." >&2
        failures=$((failures + 1))
        continue
    fi

    report_path="${module_dir}/target/japicmp/verify-public-api-compatibility.xml"
    if [[ ! -s "${report_path}" ]]; then
        echo "[FAIL] JApiCmp did not create a report for ${artifact_id}." >&2
        failures=$((failures + 1))
        continue
    fi
    cp "${report_path}" "${REPORT_DIR}/${artifact_id}.xml"
done

if (( failures > 0 )); then
    echo "[FAIL] API compatibility failed for ${failures} module(s)." >&2
    exit 1
fi

echo "[PASS] Binary API compatibility passed against ${BASELINE_VERSION}; reports are in ${REPORT_DIR}."
