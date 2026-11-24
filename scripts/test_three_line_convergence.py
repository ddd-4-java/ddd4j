"""三线总门禁的失败关闭与场景映射自测。"""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


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
                  "historical-index": {"status": "PASS"},
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

    def test_missing_dependency_catalog_evidence_blocks_verify(self):
        self.assertIn('dependency-catalog', MODULE.VERIFY_CHECKS)
        checks = {name: {'status': 'PASS'} for name in MODULE.VERIFY_CHECKS}
        del checks['dependency-catalog']
        self.assertEqual('BLOCKED', MODULE.combined_status(checks, 'verify', True))

    def test_jdk8_quarkus_durable_sample_is_required(self):
        name = "quarkus-durable-sample-8"
        checks = {key: {"status": "PASS"} for key in MODULE.VERIFY_CHECKS}
        self.assertIn(name, checks)
        del checks[name]
        self.assertEqual("BLOCKED", MODULE.combined_status(checks, "verify", True))

    def test_jdk8_quarkus_durable_sample_requires_fresh_profile_report(self):
        module = "ddd4j-samples/ddd4j-sample-quarkus-cqrs"
        java_class = "io.ddd4j.sample.quarkus.cqrs.NativeSharedCqrsTest"
        method = "durableSourceProjectionFailureRetryAndSparsePosition"
        for mode in ("missing", "default", "stale", "skipped", "wrong-case", "native"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                stage = root / "evidence"
                stage.mkdir()
                target = "target" if mode == "default" else "target/durable-order"
                directory = root / module / target / "surefire-reports"
                directory.mkdir(parents=True)
                report = directory / f"TEST-{java_class}.xml"

                def run_profile(name, command, output, checks, cwd, env):
                    self.assertEqual(root, cwd)
                    self.assertIn("-Pdurable-order", command)
                    self.assertIn(f"-Dtest={java_class}", command)
                    self.assertIn("-am", command)
                    self.assertEqual("test", command[-1])
                    self.assertNotIn("clean", command)
                    if mode != "missing":
                        case = "anotherMethod" if mode == "wrong-case" else method
                        skipped = int(mode == "skipped")
                        report.write_text(
                            f'<testsuite name="{java_class}" tests="1" failures="0" errors="0" skipped="{skipped}">'
                            f'<testcase name="{case}" classname="{java_class}">'
                            + ('<skipped/>' if skipped else '') + '</testcase></testsuite>',
                            encoding="utf-8")
                        if mode == "stale":
                            os.utime(report, ns=(1, 1))
                    checks[name] = {"status": "PASS", "artifacts": {}}
                    return True

                checks = {}
                with patch.object(MODULE, "execute", side_effect=run_profile):
                    MODULE.verify_quarkus_sample_durable(root, stage, checks, {})
                result = checks["quarkus-durable-sample-8"]
                self.assertEqual("PASS" if mode == "native" else "FAIL", result["status"])
                if mode == "native":
                    self.assertEqual(1, result["testReports"]["executedTests"])
                    self.assertEqual(1, len(result["artifacts"]))

    def test_missing_historical_index_blocks_both_phases(self):
        self.assertIn("historical-index", MODULE.INVENTORY_CHECKS)
        self.assertIn("historical-index", MODULE.VERIFY_CHECKS)
        for phase, required in (("inventory", MODULE.INVENTORY_CHECKS),
                                ("verify", MODULE.VERIFY_CHECKS)):
            checks = {name: {"status": "PASS"} for name in required}
            del checks["historical-index"]
            self.assertEqual("BLOCKED", MODULE.combined_status(checks, phase, True))

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
        self.assertIn("-am", capacity)
        self.assertIn("-Dsurefire.failIfNoSpecifiedTests=false", capacity)
        self.assertIn("-Dtest=RedisStreamNativeCausalityIT", redis)
        self.assertIn("-Dddd4j.redis.native.url=redis://127.0.0.1:16379", redis)
        self.assertIsNone(MODULE.explicit_scenario_commands("2.0.x", None)[1])

    def test_reactor_uses_only_compatible_native_redis_ports(self):
        endpoints = (
            ("redis://127.0.0.1:16379", 16379),
            ("redis://127.0.0.1:16380/0", 16380),
            (None, None),
            ("redis://example.test:16379", None),
            ("redis://127.0.0.2:16379", None),
            ("redis://localhost:16379", None),
            ("redis://[::1]:16379", None),
            ("redis://user:password@127.0.0.1:16379", None),
            ("rediss://127.0.0.1:16379", None),
            ("redis://127.0.0.1:16379/1", None),
            ("redis://127.0.0.1:16379?db=1", None),
            ("redis://127.0.0.1", None),
            ("redis://127.0.0.1:0", None),
            ("redis://127.0.0.1:65536", None),
        )
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / version for _, version in MODULE.EXPECTED]
            for root in roots:
                root.mkdir()
                (root / "pom.xml").touch()
                (root / "mvnw").touch()
            policy = base / "policy.json"
            policy.touch()

            def fake_execute(name, command, stage, checks, **kwargs):
                passed = not name.startswith("capacity-")
                checks[name] = {"status": "PASS" if passed else "BLOCKED", "artifacts": {}}
                return passed

            for redis_url, port in endpoints:
                with self.subTest(redis_url=redis_url), \
                     patch.object(MODULE, "git_input", return_value={}), \
                     patch.object(MODULE, "jdk_info", return_value={}), \
                     patch.object(MODULE, "validate_checkout"), \
                     patch.object(MODULE, "value_probe_classpaths", return_value=[]), \
                     patch.object(MODULE, "execute", side_effect=fake_execute) as executed, \
                     patch.object(MODULE, "reactor_reports", return_value={"status": "PASS"}), \
                     patch.object(MODULE, "reactor_javadocs", return_value={"status": "PASS"}), \
                     patch.object(MODULE, "verify_quarkus_sample_durable"), \
                     patch.object(MODULE, "publish_report"):
                    MODULE.run(MODULE.argparse.Namespace(
                        roots=roots, jdks=roots, policy=policy, output=base / "reports",
                        phase="verify", expected_shas=None, redis_native_url=redis_url))
                    commands = {call.args[0]: call.args[1] for call in executed.call_args_list}
                    expected = ["./mvnw", "-q", "clean", "verify"]
                    if port is not None:
                        expected.append(f"-Dddd4j.test.redis.port={port}")
                    for major, _ in MODULE.EXPECTED:
                        self.assertEqual(expected, commands[f"reactor-{major}"])
                        if redis_url is None:
                            self.assertNotIn(f"redis-native-{major}", commands)
                        else:
                            self.assertIn(f"-Dddd4j.redis.native.url={redis_url}",
                                          commands[f"redis-native-{major}"])

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

    def test_scenario_baseline_rejects_identical_regressions_on_all_lines(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            versions = [version for _, version in MODULE.EXPECTED]
            supported = {version: {"status": "supported", "tests": ["Old.java", "Extra.java"]}
                         for version in versions}
            case_mappings = {version: {"status": "supported", "tests": ["Old.java"]}
                             for version in versions}
            pending = {version: {"status": "pending", "reason": "awaiting cloud evidence"}
                       for version in versions}
            baseline = {"schemaVersion": 1, "scenarios": [
                {"id": "CORE", "lines": supported},
                {"id": "CASES", "lines": case_mappings,
                 "caseNames": {version: ["first", "second"] for version in versions}},
                {"id": "CLOUD", "lines": pending},
                {"id": "MQ-MQTT-UNSUPPORTED-NACK", "lines": case_mappings,
                 "caseNames": {version: "shouldTreatMessageIdAsPayloadConcernAndRejectNativeNack"
                               for version in versions}},
            ]}
            baseline_path = base / "baseline.json"
            baseline_path.write_text(json.dumps(baseline), encoding="utf-8")
            baseline_bytes = baseline_path.read_bytes()
            roots = [base / version for version in versions]
            for root in roots:
                (root / "verification/three-line-parity").mkdir(parents=True)
                for path in ("Old.java", "Extra.java", "Moved.java"):
                    (root / path).touch()
            for operation, expected in (
                ("delete", "CORE removed"),
                ("supported downgrade", "CORE:1.0.x supported -> pending"),
                ("pending downgrade", "CLOUD:1.0.x pending -> unsupported"),
                ("reduce files", "CORE:1.0.x required test coverage reduced"),
                ("reduce cases", "CASES:1.0.x required named cases reduced"),
                ("replace cases", "CASES:1.0.x required named cases removed"),
                ("move tests and add scenario", None),
                ("recorded MQTT case rename", None),
                ("consolidate with named cases", None),
            ):
                with self.subTest(operation=operation):
                    changed = json.loads(baseline_bytes)
                    core, cases, cloud, mqtt = changed["scenarios"]
                    if operation == "delete":
                        changed["scenarios"].remove(core)
                    for version in versions:
                        if operation == "supported downgrade":
                            core["lines"][version] = {"status": "pending", "reason": "unverified"}
                        elif operation == "pending downgrade":
                            cloud["lines"][version] = {"status": "unsupported", "reason": "retired"}
                        elif operation == "reduce files":
                            core["lines"][version]["tests"] = ["Old.java"]
                        elif operation == "reduce cases":
                            cases["caseNames"][version] = "first"
                        elif operation == "replace cases":
                            cases["caseNames"][version] = ["replacementFirst", "replacementSecond"]
                        elif operation == "recorded MQTT case rename":
                            mqtt["caseNames"][version] = (
                                "shouldRejectUnsupportedOperationsWithoutAcknowledgingAndAllowAck")
                            cloud["lines"][version] = {"status": "supported", "tests": ["Moved.java"]}
                        elif operation == "consolidate with named cases":
                            core["lines"][version]["tests"] = ["Moved.java"]
                            core.setdefault("caseNames", {})[version] = ["retainedOne", "retainedTwo"]
                            cloud["lines"][version] = {"status": "supported", "tests": ["Moved.java"]}
                        elif expected is None:
                            core["lines"][version]["tests"][0] = "Moved.java"
                            cases["lines"][version]["tests"] = ["Moved.java"]
                            cloud["lines"][version] = {"status": "supported", "tests": ["Moved.java"]}
                    if expected is None:
                        changed["scenarios"].append({"id": "NEW", "lines": case_mappings})
                    for root in roots:
                        (root / "verification/three-line-parity/scenario-matrix.json").write_text(
                            json.dumps(changed), encoding="utf-8")
                    legacy_result = MODULE.scenario_matrix(base, roots)
                    self.assertIn(legacy_result["status"], ("PASS", "BLOCKED"))
                    result = MODULE.scenario_matrix(base, roots, baseline_bytes)
                    if expected is None:
                        self.assertEqual("PASS", result["status"], result)
                    else:
                        self.assertEqual("FAIL", result["status"], result)
                        self.assertTrue(any(expected in issue for issue in result["regressions"]), result)

    def test_scenario_baseline_preflight_and_drift_in_both_phases(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / version for _, version in MODULE.EXPECTED]
            pending = {version: {"status": "pending", "reason": "awaiting real cloud"}
                       for _, version in MODULE.EXPECTED}
            matrix = {"schemaVersion": 1, "scenarios": [{"id": "CLOUD", "lines": pending}]}
            content = json.dumps(matrix).encode()
            for root in roots:
                path = root / "verification/three-line-parity/scenario-matrix.json"
                path.parent.mkdir(parents=True)
                path.write_bytes(content)
                (root / "pom.xml").touch()
                (root / "mvnw").touch()
            policy = base / "policy.json"
            policy.touch()
            baseline = base / "pinned.json"
            for phase in ("inventory", "verify"):
                for condition in ("missing", "invalid", "valid", "drift"):
                    # Drift reaches the final report via inventory without running a reactor.
                    if phase == "verify" and condition in ("valid", "drift"):
                        continue
                    with self.subTest(phase=phase, condition=condition):
                        output = base / f"{phase}-{condition}"
                        if condition == "missing":
                            baseline.unlink(missing_ok=True)
                        else:
                            baseline.write_bytes(b"invalid" if condition == "invalid" else content)
                        args = MODULE.argparse.Namespace(
                            roots=roots, jdks=roots, policy=policy, output=output, phase=phase,
                            expected_shas=None, scenario_baseline=baseline)
                        def fake_execute(name, command, stage, checks, **kwargs):
                            checks[name] = {"status": "PASS"}
                            if condition == "drift":
                                baseline.write_bytes(content + b"\n")

                        with patch.object(MODULE, "git_input", return_value={}), \
                             patch.object(MODULE, "jdk_info", return_value={}), \
                             patch.object(MODULE, "validate_checkout"), \
                             patch.object(MODULE, "execute", side_effect=fake_execute) as executed:
                            result = MODULE.run(args)
                        report_path, = output.glob("*/report.json")
                        report = json.loads(report_path.read_text())
                        check = report["checks"]["scenario-matrix"]
                        self.assertEqual(0 if condition == "valid" else 1, result)
                        if condition in ("missing", "invalid"):
                            executed.assert_not_called()
                            self.assertIn("scenario baseline", check["reason"])
                            self.assertEqual(3, len(report["roots"]))
                            self.assertEqual(str(policy.resolve()), report["policy"])
                            self.assertEqual(report["policySha256"], report["policyAfterSha256"])
                        elif condition == "drift":
                            self.assertFalse(report["inputStable"])
                            self.assertIn("changed", check["reason"])
                            self.assertNotEqual(check["baselineSha256"], check["baselineAfterSha256"])
                        else:
                            self.assertEqual("BLOCKED", check["status"])
                            self.assertEqual(["CLOUD:" + version for _, version in MODULE.EXPECTED],
                                             check["pending"])
                            self.assertEqual(check["baselineSha256"], check["baselineAfterSha256"])
                        if condition != "missing":
                            artifact = report_path.with_name(check["baselineArtifact"])
                            self.assertEqual(b"invalid" if condition == "invalid" else content,
                                             artifact.read_bytes())
                            self.assertEqual(MODULE.sha256(artifact), check["baselineSha256"])

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
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="0">'
                '<testcase name="requiredCase" classname="example.RequiredTest"/>'
                '</testsuite>', encoding="utf-8")
            os.utime(report, ns=(1, 1))
            self.assertEqual("FAIL", MODULE.scenario_test_reports(
                root, scenarios, "1.0.x", 2)["status"])
            self.assertEqual("PASS", MODULE.scenario_test_reports(
                root, scenarios, "1.0.x", 0)["status"])
            report.write_text(
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="1">'
                '<testcase name="requiredCase" classname="example.RequiredTest"><skipped/></testcase>'
                '</testsuite>', encoding="utf-8")
            self.assertEqual("FAIL", MODULE.scenario_test_reports(
                root, scenarios, "1.0.x", 0)["status"])

    def test_scenario_execution_accepts_nested_and_failsafe_reports(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            reports = root / "core/target/failsafe-reports"
            reports.mkdir(parents=True)
            report = reports / "TEST-example.RequiredTest$Nested.xml"
            report.write_text(
                '<testsuite name="example.RequiredTest$Nested" tests="2" failures="0" errors="0" skipped="0">'
                '<testcase name="parameters[1]" classname="example.RequiredTest"><flakyFailure/></testcase>'
                '<testcase name="parameters[2]" classname="example.RequiredTest$Nested"/>'
                '</testsuite>', encoding="utf-8")
            duplicate = root / "core/target/surefire-reports" / report.name
            duplicate.parent.mkdir(parents=True)
            duplicate.write_bytes(report.read_bytes())
            scenarios = [{"id": "CORE-002", "caseNames": {"3.0.x": ["parameters[1]", "parameters[2]"]},
                          "lines": {
                "3.0.x": {"status": "supported", "tests": [
                    "core/src/test/java/example/RequiredTest.java"]}}}]
            result = MODULE.scenario_test_reports(root, scenarios, "3.0.x", 0)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(2, result["executedTests"])
            self.assertEqual(1, result["uniqueReports"])
            self.assertEqual(2, len(result["tests"][0]["artifacts"]))
            scenarios.append({"id": "CORE-003", "lines": scenarios[0]["lines"]})
            repeated = MODULE.scenario_test_reports(root, scenarios, "3.0.x", 0)
            self.assertEqual(2, repeated["mappedTests"])
            self.assertEqual(1, repeated["uniqueReports"])
            self.assertEqual(2, repeated["executedTests"])

    def test_scenario_execution_requires_the_mapped_case_not_only_its_class(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            reports = root / "core/target/surefire-reports"
            reports.mkdir(parents=True)
            report = reports / "TEST-example.RequiredTest.xml"
            report.write_text(
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="0">'
                '<testcase name="unrelatedCase" classname="example.RequiredTest"/>'
                '</testsuite>', encoding="utf-8")
            scenarios = [{"id": "CORE-CASE", "caseNames": {"2.0.x": "requiredCase"},
                          "lines": {"2.0.x": {"status": "supported", "tests": [
                              "core/src/test/java/example/RequiredTest.java"]}}}]
            result = MODULE.scenario_test_reports(root, scenarios, "2.0.x", 0)
            self.assertEqual("FAIL", result["status"])
            self.assertIn("missing executed test case requiredCase", result["issues"][0])

            report.write_text(
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="0">'
                '<testcase name="requiredCase" classname="example.RequiredTest"/>'
                '</testsuite>', encoding="utf-8")
            self.assertEqual("PASS", MODULE.scenario_test_reports(
                root, scenarios, "2.0.x", 0)["status"])

            scenarios[0]["caseNames"]["2.0.x"] = ["requiredCase", "secondCase"]
            self.assertEqual("FAIL", MODULE.scenario_test_reports(
                root, scenarios, "2.0.x", 0)["status"])
            report.write_text(
                '<testsuite name="example.RequiredTest" tests="2" failures="0" errors="0" skipped="0">'
                '<testcase name="requiredCase" classname="example.RequiredTest"/>'
                '<testcase name="secondCase" classname="example.RequiredTest"/>'
                '</testsuite>', encoding="utf-8")
            self.assertEqual("PASS", MODULE.scenario_test_reports(
                root, scenarios, "2.0.x", 0)["status"])

    def test_scenario_execution_rejects_inconsistent_or_unrelated_xml(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            reports = root / "core/target/surefire-reports"
            reports.mkdir(parents=True)
            report = reports / "TEST-example.RequiredTest.xml"
            scenario = {"id": "CORE-XML", "lines": {
                "2.0.x": {"status": "supported", "tests": [
                    "core/src/test/java/example/RequiredTest.java"]}}}
            suite = ('<testsuite name="{}" tests="1" failures="0" errors="0" skipped="0">'
                     '{}</testsuite>')
            fixtures = (
                ("missing testcase", "example.RequiredTest", "", False),
                ("hidden failure", "example.RequiredTest",
                 '<testcase name="case" classname="example.RequiredTest"><failure/></testcase>', False),
                ("hidden skip", "example.RequiredTest",
                 '<testcase name="case" classname="example.RequiredTest"><skipped/></testcase>', False),
                ("wrong suite", "different.RequiredTest",
                 '<testcase name="case" classname="example.RequiredTest"/>', True),
                ("wrong class", "example.RequiredTest",
                 '<testcase name="case" classname="different.RequiredTest"/>', True),
            )
            for label, suite_name, testcases, named in fixtures:
                with self.subTest(label=label):
                    report.write_text(suite.format(suite_name, testcases), encoding="utf-8")
                    row = {**scenario, "caseNames": {"2.0.x": "case"}} if named else scenario
                    result = MODULE.scenario_test_reports(root, [row], "2.0.x", 0)
                    self.assertEqual("FAIL", result["status"])
                    self.assertEqual(0, result["executedTests"])

    def test_reactor_rejects_surefire_summary_without_executed_testcases(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "pom.xml").write_text("<project/>", encoding="utf-8")
            source = root / "src/test/java/example/RequiredTest.java"
            source.parent.mkdir(parents=True)
            source.write_text("package example; class RequiredTest {}", encoding="utf-8")
            report = root / "target/surefire-reports/TEST-example.RequiredTest.xml"
            report.parent.mkdir(parents=True)
            report.write_text(
                '<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="0"/>',
                encoding="utf-8")
            result = MODULE.reactor_reports(root, 0)
            self.assertEqual("FAIL", result["status"])
            self.assertEqual(0, result["executedTests"])

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
            xml.write_text(
                '<testsuite name="ExampleTest" tests="2" failures="0" errors="0" skipped="0">'
                '<testcase name="one" classname="ExampleTest"/>'
                '<testcase name="two" classname="ExampleTest"/>'
                '</testsuite>', encoding="utf-8")
            os.utime(xml, ns=(1, 1))
            self.assertEqual("FAIL", MODULE.reactor_reports(root, 2)["status"])
            result = MODULE.reactor_reports(root, 0)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(2, result["executedTests"])
            xml.write_text(
                '<testsuite name="ExampleTest" tests="2" failures="0" errors="0" skipped="1">'
                '<testcase name="one" classname="ExampleTest"/>'
                '<testcase name="two" classname="ExampleTest"><skipped/></testcase>'
                '</testsuite>', encoding="utf-8")
            result = MODULE.reactor_reports(root, 0)
            self.assertEqual("FAIL", result["status"])
            self.assertIn("core: tests=2 failures=0 errors=0 skipped=1", result["issues"])

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
