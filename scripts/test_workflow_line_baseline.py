"""工作流必须使用所属发布线的 JDK，并让必需构建失败可见。"""

from pathlib import Path
import os
import re
import shlex
import subprocess
import tempfile
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]


def convergence_regression_unittest_args(workflow):
    section = workflow
    for indent, heading in (
        (0, "jobs:"),
        (2, "convergence:"),
        (4, "steps:"),
        (6, "- name: Run convergence gate regression tests"),
    ):
        indentation = " " * indent
        match = re.search(
            rf"(?ms)^{indentation}{re.escape(heading)}\n(.*?)(?=^ {{0,{indent}}}\S|\Z)",
            section,
        )
        if match is None:
            return []
        section = match[1]

    # In a folded scalar, the first blank or more-indented line preserves a newline.
    block = re.search(r"(?m)^        run: >-\n((?:^ {10}\S[^\n]*(?:\n|\Z))+)", section)
    if block is None:
        return []
    arguments = shlex.split(" ".join(block[1].splitlines()), comments=True)
    prefix = ["python3", "-m", "unittest", "-q"]
    if arguments[:len(prefix)] != prefix:
        return []
    selectors = arguments[len(prefix):]
    if any(not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_./]*", selector) for selector in selectors):
        return []
    return selectors


class WorkflowLineBaselineTest(unittest.TestCase):
    def setUp(self):
        pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
        self.jdk = re.search(r"<java.version>([^<]+)</java.version>", pom).group(1)

    def test_convergence_supersedes_only_older_pushes_on_same_branch(self):
        workflow = (ROOT / ".github/workflows/three-line-convergence.yml").read_text(encoding="utf-8")
        for line in ("1", "2", "3"):
            self.assertIn(f"feature/{line}.0.x", workflow)
        self.assertIn("github.event_name == 'workflow_dispatch' && github.run_id || github.ref", workflow)
        self.assertNotIn("'all-lines'", workflow)
        self.assertIn("cancel-in-progress: true", workflow)

    def test_convergence_runs_fault_injection_before_the_push_preflight(self):
        workflow = (ROOT / ".github/workflows/three-line-convergence.yml").read_text(encoding="utf-8")
        after_python = workflow.split("      - name: Set up Python", 1)[1]
        before_preflight = after_python.split("      - name: Check unresolved three-line inventory on push", 1)[0]
        self.assertIn("Run convergence gate regression tests", before_preflight)
        self.assertIn("working-directory: driver", before_preflight)
        self.assertIn("PYTHONPATH: scripts", before_preflight)
        self.assertIn("DDD4J_JDK21_HOME: ${{ env.JAVA_HOME_21_X64 }}", before_preflight)
        for required in (
            "scripts/test_three_line_convergence.py",
            "scripts/test_dependency_alignment.py",
            "scripts/test_platform_cdi_resources.py",
            "test_missing_production_type_and_test_are_both_open",
            "test_changed_error_code_fails_inventory_gate",
            "test_old_target_class_does_not_count_as_current_api",
            "test_exact_platform_api_allows_jvm_generic_wildcard_but_not_descriptor_glob",
        ):
            self.assertIn(required, before_preflight)
        self.assertNotIn("continue-on-error: true", before_preflight)

    def test_convergence_regression_step_runs_history_and_type_mapping_tests(self):
        workflow = (ROOT / ".github/workflows/three-line-convergence.yml").read_text(encoding="utf-8")
        arguments = convergence_regression_unittest_args(workflow)
        for script in (
            "scripts/test_build_historical_evidence_index.py",
            "scripts/test_three_line_type_mappings.py",
        ):
            with self.subTest(script=script):
                self.assertIn(script, arguments)

    def test_convergence_regression_ignores_arguments_outside_its_command(self):
        step = (
            "    steps:\n"
            "      - name: Run convergence gate regression tests\n"
            "        run: >-\n"
            "          python3 -m unittest -q\n"
            "          scripts/test_build_historical_evidence_index.py\n"
            "          scripts/test_three_line_type_mappings.py\n"
        )
        target_job = "jobs:\n  convergence:\n"
        test_path = "          scripts/test_build_historical_evidence_index.py"
        for fixture, workflow in (
            ("other job",
             "jobs:\n  unrelated:\n" + step + "  convergence:\n    steps:\n      - run: 'true'\n"),
            ("shell comment",
             target_job + step.replace(test_path, "          # hidden by a folded shell comment\n" + test_path)),
            ("preserved newline",
             target_job + step.replace(test_path, "\n" + test_path)),
        ):
            with self.subTest(fixture=fixture):
                self.assertNotIn("scripts/test_three_line_type_mappings.py",
                                 convergence_regression_unittest_args(workflow))

    def test_convergence_supplies_the_previous_committed_scenario_matrix(self):
        workflow = (ROOT / ".github/workflows/three-line-convergence.yml").read_text(encoding="utf-8")
        before_verification = workflow.split("      - name: Verify three-line convergence", 1)[0]
        self.assertIn("Prepare committed scenario baseline", before_verification)
        self.assertIn("SCENARIO_BEFORE: ${{ github.event.before }}", before_verification)
        self.assertIn('git -C driver show "$baseline_sha:$matrix_path"', before_verification)
        self.assertIn("scenario-baseline.commit", before_verification)
        self.assertIn("--scenario-baseline target/three-line-convergence/scenario-baseline.json", workflow)

    def test_release_candidate_keeps_reports_outside_maven_clean(self):
        workflow = (ROOT / ".github/workflows/release-candidate.yml").read_text(encoding="utf-8")
        before_reports = workflow.split("      - name: Verify source", 1)[0]
        self.assertIn('"${RUNNER_TEMP}/ddd4j-release-quality" >> "$GITHUB_ENV"', before_reports)
        self.assertIn("path: ${{ env.DDD4J_REPORT_DIR }}", workflow)
        self.assertNotIn("target/release-quality", workflow)

    def test_release_candidate_configures_existing_maven_repositories_before_build(self):
        workflow = (ROOT / ".github/workflows/release-candidate.yml").read_text(encoding="utf-8")
        before_first_maven = workflow.split("      - name: Verify source", 1)[0]
        self.assertIn("Configure Easy4J Maven repositories", before_first_maven)
        self.assertIn("MAVEN_SETTINGS_XML: ${{ secrets.MAVEN_SETTINGS_XML }}", before_first_maven)
        self.assertIn("umask 077", before_first_maven)

    def test_verify_uses_own_jdk_and_keeps_required_jobs_blocking(self):
        workflow = (ROOT / ".github/workflows/verify.yml").read_text(encoding="utf-8")
        target_jdk = self.jdk.replace("1.8", "8")
        unit_job = workflow.split("  unit-and-contract:\n", 1)[1].split("  coverage-gate:\n", 1)[0]
        before_maven = unit_job.split("      - name: Configure Easy4J Maven repositories", 1)[0]
        checker_jdks = re.findall(r"java-version: ['\"]?([0-9]+)['\"]?", before_maven)
        self.assertEqual({"8", "17", "21"}, set(checker_jdks))
        self.assertEqual(target_jdk, checker_jdks[-1], "Maven must use this branch's JDK")
        for version in ("8", "17", "21"):
            self.assertIn(f"DDD4J_JDK{version}_HOME=$JAVA_HOME", before_maven)
        other_jobs = workflow.replace("  unit-and-contract:\n" + unit_job, "", 1)
        other_jdks = re.findall(r"java-version: ['\"]?([0-9]+)['\"]?", other_jobs)
        self.assertEqual({target_jdk}, set(other_jdks))
        self.assertNotIn("continue-on-error: true", workflow)
        self.assertNotIn("SAMPLE_EXCLUSIONS", workflow)
        self.assertNotIn("QUARKUS_TEST_EXCLUSIONS", workflow)

    def test_infrastructure_job_runs_native_broker_adapters(self):
        workflow = (ROOT / ".github/workflows/verify.yml").read_text(encoding="utf-8")
        job = re.search(
            r"(?ms)^  infrastructure-integration:\n(.*?)(?=^  [a-z][a-z-]*:|\Z)",
            workflow)
        self.assertIsNotNone(job)
        module_list = re.search(r"-pl ([^\s]+)", job.group(1))
        self.assertIsNotNone(module_list)
        modules = set(module_list.group(1).split(","))
        for module in (
            "ddd4j-mq/ddd4j-mq-kafka",
            "ddd4j-mq/ddd4j-mq-rabbitmq",
            "ddd4j-mq/ddd4j-mq-nats",
            "ddd4j-mq/ddd4j-mq-mqtt",
            "ddd4j-mq/ddd4j-mq-mqtt-mica",
            "ddd4j-mq/ddd4j-mq-activemq",
            "ddd4j-mq/ddd4j-mq-pulsar",
            "ddd4j-mq/ddd4j-mq-rocketmq",
            "ddd4j-mq/ddd4j-mq-redis-stream",
            "ddd4j-mq/ddd4j-mq-sqs",
        ):
            with self.subTest(module=module):
                self.assertIn(module, modules)

    def test_workflow_lint_runs_native_broker_report_selftests(self):
        workflow = (ROOT / ".github/workflows/verify.yml").read_text(encoding="utf-8")
        lint_job = workflow.split("  workflow-lint:\n", 1)[1].split(
            "  unit-and-contract:\n", 1)[0]
        self.assertIn("scripts/test_verify_native_broker_test_reports.py", lint_job)

    def test_release_candidate_uses_own_jdk_and_baseline_line(self):
        workflow = (ROOT / ".github/workflows/release-candidate.yml").read_text(encoding="utf-8")
        version = self.jdk.replace("1.8", "8")
        self.assertIn(f"java-version: '{version}'", workflow)
        self.assertIn(f"{version} release candidate evidence", workflow)
        self.assertIn(f"non-SNAPSHOT {1 if version == '8' else 2 if version == '17' else 3}.0.x API baseline", workflow)

    def test_other_line_api_baseline_is_rejected_before_maven(self):
        line = "1" if self.jdk == "1.8" else "2" if self.jdk == "17" else "3"
        wrong = "2" if line == "1" else "1"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "scripts/verify-api-compatibility.sh").write_bytes(
                (ROOT / "scripts/verify-api-compatibility.sh").read_bytes())
            (root / "pom.xml").write_text(
                f"<project><properties><revision>{line}.0.x.20260630-SNAPSHOT</revision></properties></project>")
            marker = root / "maven-invoked"
            (root / "mvnw").write_text(f"#!/bin/sh\ntouch '{marker}'\n")
            result = subprocess.run(
                ["bash", str(root / "scripts/verify-api-compatibility.sh")],
                env={**os.environ, "DDD4J_API_BASELINE_VERSION": f"{wrong}.0.0"},
                capture_output=True, text=True, timeout=5)
            self.assertEqual(1, result.returncode, result.stdout)
            self.assertFalse(marker.exists())



    def test_release_api_compares_only_primary_published_jars(self):
        line = {"1.8": "1", "17": "2", "21": "3"}[self.jdk] + ".0.x"
        version = line + ".20260630-SNAPSHOT"
        baseline = line + ".20990101-fixture"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            for name in ("verify-api-compatibility.sh", "verify-three-line-convergence.py"):
                (root / "scripts" / name).write_bytes((ROOT / "scripts" / name).read_bytes())
            (root / "pom.xml").write_text(
                f"<project><packaging>pom</packaging><properties><revision>{version}</revision></properties>"
                "<modules><module>ddd4j-core</module><module>ddd4j-samples</module></modules></project>")
            (root / "ddd4j-samples").mkdir()
            (root / "ddd4j-samples/pom.xml").write_text(
                "<project><packaging>pom</packaging><properties><maven.deploy.skip>true</maven.deploy.skip>"
                "</properties><modules><module>app</module><module>library</module></modules></project>")

            modules = {
                "ddd4j-core": ("ddd4j-core", ""),
                "ddd4j-samples/app": ("sample-app", "<parent/>"),
                "ddd4j-samples/library": ("sample-library",
                    "<parent/><properties><maven.deploy.skip>false</maven.deploy.skip></properties>"),
                ".superpowers/archive/ddd4j-core": ("ddd4j-core", ""),
            }
            for folder, (artifact, model) in modules.items():
                module = root / folder
                module.mkdir(parents=True)
                (module / "pom.xml").write_text(f"<project><artifactId>{artifact}</artifactId>{model}</project>")

            entries = {
                f"ddd4j-core/target/ddd4j-core-{version}.jar": "core",
                f"ddd4j-core/target/original-ddd4j-core-{version}.jar": "core",
                f"ddd4j-core/target/ddd4j-core-{version}-launcher.jar": "core",
                f"ddd4j-core/target/quarkus-app/lib/main/dependency-{version}.jar": "embedded",
                f"ddd4j-samples/app/target/sample-app-{version}.jar": "app",
                f"ddd4j-samples/library/target/sample-library-{version}.jar": "library",
                f".superpowers/archive/ddd4j-core/target/ddd4j-core-{version}.jar": "archive",
            }
            for relative, namespace in entries.items():
                jar = root / relative
                jar.parent.mkdir(parents=True, exist_ok=True)
                with ZipFile(jar, "w") as archive:
                    archive.writestr(f"io/ddd4j/{namespace}/Fixture.class", b"")
                    archive.writestr("io/ddd4j/cqrs/Fixture.class", b"")

            wrapper = root / "mvnw"
            wrapper.write_text(
                """#!/usr/bin/env bash
case " $* " in
    *" help:evaluate "*)
        for arg in "$@"; do
            if [[ "$arg" == -Doutput=* ]]; then
                printf '%s\\n' "$FIXTURE_VERSION" > "${arg#-Doutput=}"
            fi
        done
        printf '[INFO] Unrelated Maven console output\\n'
        ;;
    *":get "*)
        case " $* " in
            *" -Dartifact=io.ddd4j:ddd4j-core:"*|*" -Dartifact=io.ddd4j:sample-library:"*)
                [[ "$FIXTURE_BASELINE_AVAILABLE" == yes ]]
                ;;
            *) exit 42 ;;
        esac
        ;;
    *" -Papi-compatibility "*)
        [[ " $* " == *" -Dddd4j.api.include=io.ddd4j.** "* ]] || exit 45
        [[ " $* " == *" clean verify "* ]] || exit 46
        rm -rf target
        for module in ddd4j-core ddd4j-samples/library; do
            mkdir -p "$module"/target/japicmp
            printf '<japicmp/>\\n' > "$module"/target/japicmp/verify-public-api-compatibility.xml
        done
        ;;
    *) exit 44 ;;
esac
""")
            wrapper.chmod(0o755)
            reports = root / "target/release-quality/api"
            env = {**os.environ, "DDD4J_API_BASELINE_VERSION": baseline, "FIXTURE_VERSION": version,
                   "FIXTURE_BASELINE_AVAILABLE": "yes"}
            env.pop("DDD4J_REPORT_DIR", None)
            script = ["bash", str(root / "scripts" / "verify-api-compatibility.sh")]
            result = subprocess.run(script, env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(["ddd4j-core.xml", "sample-library.xml"], sorted(p.name for p in reports.iterdir()))

            result = subprocess.run(
                script, env={**env, "FIXTURE_BASELINE_AVAILABLE": "no"},
                capture_output=True, text=True, timeout=15)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("Published baseline JAR is unavailable: io.ddd4j:ddd4j-core", result.stderr)

            (root / f"ddd4j-samples/library/target/sample-library-{version}.jar").unlink()
            result = subprocess.run(script, env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("Missing primary JAR for sample-library", result.stderr)

    def test_same_major_other_release_line_is_rejected_before_maven(self):
        line = {"1.8": "1", "17": "2", "21": "3"}[self.jdk]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "scripts/verify-api-compatibility.sh").write_bytes(
                (ROOT / "scripts/verify-api-compatibility.sh").read_bytes())
            (root / "pom.xml").write_text(
                f"<project><properties><revision>{line}.0.x.20260630-SNAPSHOT</revision></properties></project>")
            marker = root / "maven-invoked"
            (root / "mvnw").write_text(f"#!/bin/sh\ntouch '{marker}'\n")
            (root / "mvnw").chmod(0o755)
            result = subprocess.run(
                ["bash", str(root / "scripts/verify-api-compatibility.sh")],
                env={**os.environ, "DDD4J_API_BASELINE_VERSION": f"{line}.1.x.20990101"},
                capture_output=True, text=True, timeout=10)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertFalse(marker.exists(), result.stdout + result.stderr)

if __name__ == "__main__":
    unittest.main()
