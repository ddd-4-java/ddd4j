"""三线总门禁的失败关闭与场景映射自测。"""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SOURCE = Path(__file__).with_name("verify-three-line-convergence.py")
SPEC = importlib.util.spec_from_file_location("three_line_convergence", SOURCE)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ThreeLineConvergenceTest(unittest.TestCase):
    def test_missing_required_evidence_blocks_verify(self):
        checks = {"structure": {"status": "PASS"},
                  "scenario-matrix": {"status": "NOT_RUN"}}
        self.assertEqual("BLOCKED", MODULE.combined_status(checks, "verify", True))

    def test_changed_input_fails_even_when_checks_pass(self):
        checks = {"structure": {"status": "PASS"}}
        self.assertEqual("FAIL", MODULE.combined_status(checks, "verify", False))

    def test_changed_external_policy_invalidates_input(self):
        roots = [{"sha": "same", "treeFingerprint": "same"}]
        self.assertFalse(MODULE.input_stable(roots, roots, "old-policy", "new-policy"))

    def test_inventory_never_reports_product_pass(self):
        checks = {"structure": {"status": "PASS"},
                  "product-docs": {"status": "PASS"},
                  "values": {"status": "NOT_RUN"},
                  "dependencies": {"status": "NOT_RUN"},
                  "reactor": {"status": "NOT_RUN"},
                  "scenario-matrix": {"status": "NOT_RUN"}}
        self.assertEqual("INVENTORY", MODULE.combined_status(checks, "inventory", True))

    def test_inventory_missing_document_check_is_blocked(self):
        checks = {"structure": {"status": "PASS"}}
        self.assertEqual("BLOCKED", MODULE.combined_status(checks, "inventory", True))

    def test_missing_required_gate_blocks_verify(self):
        checks = {name: {"status": "PASS"} for name in MODULE.VERIFY_CHECKS}
        del checks["product-docs"]
        self.assertEqual("BLOCKED", MODULE.combined_status(checks, "verify", True))

    def test_reactor_runs_after_independent_structure_or_dependency_failure(self):
        checks = {f"effective-{jdk}": {"status": "PASS"} for jdk in (8, 17, 21)}
        checks["structure"] = {"status": "FAIL"}
        checks["dependencies"] = {"status": "FAIL"}
        self.assertTrue(MODULE.reactors_can_run(checks))

    def test_reactor_waits_for_all_line_effective_poms(self):
        checks = {f"effective-{jdk}": {"status": "PASS"} for jdk in (8, 17, 21)}
        checks["effective-17"] = {"status": "FAIL"}
        self.assertFalse(MODULE.reactors_can_run(checks))

    def test_explicit_scenarios_use_line_branch_and_isolated_redis(self):
        capacity, redis = MODULE.explicit_scenario_commands(
            "2.0.x", "redis://127.0.0.1:16379")
        self.assertIn("-Dddd4j.baseline.branch=feature/2.0.x", capacity)
        self.assertIn("-Dtest=EventStoreAppendCapacityBaselineIT", capacity)
        self.assertIn("-Dtest=RedisStreamNativeCausalityIT", redis)
        self.assertIn("-Dddd4j.redis.native.url=redis://127.0.0.1:16379", redis)
        self.assertIsNone(MODULE.explicit_scenario_commands("2.0.x", None)[1])

    def test_successful_command_without_report_is_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            stage = Path(temporary)
            checks = {}
            succeeded = MODULE.execute(
                "missing-report", [sys.executable, "-c", "pass"], stage, checks,
                required_artifacts=("expected/report.json",))
            self.assertFalse(succeeded)
            self.assertEqual("FAIL", checks["missing-report"]["status"])
            self.assertIn("expected/report.json", checks["missing-report"]["missingArtifacts"])

    def test_successful_command_with_invalid_json_report_is_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            stage = Path(temporary)
            report = stage / "expected/report.json"
            report.parent.mkdir()
            report.write_text("not json", encoding="utf-8")
            checks = {}
            succeeded = MODULE.execute(
                "invalid-report", [sys.executable, "-c", "pass"], stage, checks,
                required_artifacts=("expected/report.json",))
            self.assertFalse(succeeded)
            self.assertEqual("FAIL", checks["invalid-report"]["status"])

    def test_scenario_mapping_rejects_missing_test(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / str(index) for index in range(3)]
            for root in roots:
                (root / "verification/three-line-parity").mkdir(parents=True)
            matrix = {"scenarios": [{"id": "CORE-001",
                    "lines": {version: {"status": "supported", "tests": ["missing/Test.java"]}
                              for _, version in MODULE.EXPECTED}}]}
            for root in roots:
                path = root / "verification/three-line-parity/scenario-matrix.json"
                path.write_text(json.dumps(matrix), encoding="utf-8")
            self.assertEqual("FAIL", MODULE.scenario_matrix(base, roots)["status"])

    def test_pending_scenario_blocks_without_claiming_platform_unsupported(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / str(index) for index in range(3)]
            for root in roots:
                (root / "verification/three-line-parity").mkdir(parents=True)
            matrix = {"scenarios": [{"id": "ORDER-JDBC",
                    "lines": {version: {"status": "pending", "reason": "JDBC integration not run"}
                              for _, version in MODULE.EXPECTED}}]}
            for root in roots:
                path = root / "verification/three-line-parity/scenario-matrix.json"
                path.write_text(json.dumps(matrix), encoding="utf-8")
            self.assertEqual("BLOCKED", MODULE.scenario_matrix(base, roots)["status"])

    def test_scenario_mapping_rejects_cross_line_drift(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / str(index) for index in range(3)]
            matrix = {"scenarios": [{"id": "CORE-001",
                    "lines": {version: {"status": "pending", "reason": "not run"}
                              for _, version in MODULE.EXPECTED}}]}
            for root in roots:
                path = root / "verification/three-line-parity/scenario-matrix.json"
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps(matrix), encoding="utf-8")
            path = roots[0] / "verification/three-line-parity/scenario-matrix.json"
            drifted = json.loads(path.read_text(encoding="utf-8"))
            drifted["scenarios"][0]["lines"]["1.0.x"]["reason"] = "silently changed"
            path.write_text(json.dumps(drifted), encoding="utf-8")
            self.assertEqual("FAIL", MODULE.scenario_matrix(base, roots)["status"])

    def test_scenario_execution_requires_its_own_fresh_report(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            module = root / "core"
            reports = module / "target/surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-example.OtherTest.xml").write_text(
                '<testsuite name="example.OtherTest" tests="1" failures="0" errors="0" skipped="0"/>',
                encoding="utf-8")
            scenarios = [{"id": "CORE-001", "lines": {
                "1.0.x": {"status": "supported", "tests": [
                    "core/src/test/java/example/RequiredTest.java"]}}}]
            result = MODULE.scenario_test_reports(root, scenarios, "1.0.x", 0)
            self.assertEqual("FAIL", result["status"])
            self.assertIn("CORE-001", result["issues"][0])

            report = reports / "TEST-example.RequiredTest.xml"
            report.write_text(
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="0"/>',
                encoding="utf-8")
            os.utime(report, ns=(1, 1))
            self.assertEqual("FAIL", MODULE.scenario_test_reports(
                root, scenarios, "1.0.x", 2)["status"])
            self.assertEqual("PASS", MODULE.scenario_test_reports(
                root, scenarios, "1.0.x", 0)["status"])
            report.write_text(
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="1"/>',
                encoding="utf-8")
            self.assertEqual("FAIL", MODULE.scenario_test_reports(
                root, scenarios, "1.0.x", 0)["status"])

    def test_scenario_execution_accepts_nested_and_failsafe_reports(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            reports = root / "core/target/failsafe-reports"
            reports.mkdir(parents=True)
            report = reports / "TEST-example.RequiredTest$Nested.xml"
            report.write_text(
                '<testsuite name="example.RequiredTest$Nested" tests="2" failures="0" errors="0" skipped="0"/>',
                encoding="utf-8")
            scenarios = [{"id": "CORE-002", "lines": {
                "3.0.x": {"status": "supported", "tests": [
                    "core/src/test/java/example/RequiredTest.java"]}}}]
            result = MODULE.scenario_test_reports(root, scenarios, "3.0.x", 0)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(2, result["executedTests"])
            scenarios.append({"id": "CORE-003", "lines": scenarios[0]["lines"]})
            repeated = MODULE.scenario_test_reports(root, scenarios, "3.0.x", 0)
            self.assertEqual(2, repeated["mappedTests"])
            self.assertEqual(1, repeated["uniqueReports"])
            self.assertEqual(2, repeated["executedTests"])

    def test_reactor_requires_fresh_executed_test_reports(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "pom.xml").write_text(
                "<project><modules><module>core</module></modules></project>", encoding="utf-8")
            module = root / "core"
            (module / "src/test/java").mkdir(parents=True)
            (module / "pom.xml").write_text("<project/>", encoding="utf-8")
            (module / "src/test/java/ExampleTest.java").write_text(
                "class ExampleTest {}", encoding="utf-8")
            self.assertEqual("FAIL", MODULE.reactor_reports(root, 2)["status"])

            reports = module / "target/surefire-reports"
            reports.mkdir(parents=True)
            xml = reports / "TEST-ExampleTest.xml"
            xml.write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>',
                           encoding="utf-8")
            os.utime(xml, ns=(1, 1))
            self.assertEqual("FAIL", MODULE.reactor_reports(root, 2)["status"])
            result = MODULE.reactor_reports(root, 0)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(2, result["executedTests"])

    def test_reactor_javadocs_rejects_suppressed_errors_and_missing_or_stale_jars(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "pom.xml").write_text(
                "<project><modules><module>core</module></modules></project>", encoding="utf-8")
            module = root / "core"
            source = module / "src/main/java/Example.java"
            source.parent.mkdir(parents=True)
            source.write_text("class Example {}", encoding="utf-8")
            (module / "pom.xml").write_text("<project/>", encoding="utf-8")
            log = root / "verify.log"
            log.write_text("[ERROR] MavenReportException: Error while generating Javadoc:\n",
                           encoding="utf-8")
            jar = module / "target/core-javadoc.jar"
            jar.parent.mkdir()
            jar.write_bytes(b"jar")
            self.assertEqual("FAIL", MODULE.reactor_javadocs(root, 0, log)["status"])

            log.write_text("", encoding="utf-8")
            jar.unlink()
            self.assertEqual("FAIL", MODULE.reactor_javadocs(root, 0, log)["status"])
            jar.write_bytes(b"jar")
            os.utime(jar, ns=(1, 1))
            self.assertEqual("FAIL", MODULE.reactor_javadocs(root, 2, log)["status"])
            result = MODULE.reactor_javadocs(root, 0, log)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(1, result["documentedModules"])


    def test_fixed_sha_accepts_detached_checkout_and_rejects_drift(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(["git", "init", "-q", "--initial-branch=feature/1.0.x", str(root)],
                           check=True)
            subprocess.run(["git", "-C", str(root), "config", "user.name", "Test"], check=True)
            subprocess.run(["git", "-C", str(root), "config", "user.email", "test@example.invalid"],
                           check=True)
            (root / "pom.xml").write_text("<project><properties><revision>1.0.x.20260630-SNAPSHOT</revision></properties></project>", encoding="utf-8")
            (root / "tracked.txt").write_text("input", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "tracked.txt", "pom.xml"], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "fixture"], check=True)
            sha = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"],
                                          text=True).strip()
            self.assertEqual(sha, MODULE.validate_checkout(root, "1.0.x", None))
            subprocess.run(["git", "-C", str(root), "checkout", "-q", "--detach", sha],
                           check=True)
            self.assertEqual(sha, MODULE.validate_checkout(root, "1.0.x", sha))
            with self.assertRaises(ValueError):
                MODULE.validate_checkout(root, "1.0.x", None)
            with self.assertRaises(ValueError):
                MODULE.validate_checkout(root, "1.0.x", "0" * 40)
            with self.assertRaises(ValueError):
                MODULE.validate_checkout(root, "1.0.x", "not-a-sha")

if __name__ == "__main__":
    unittest.main()
