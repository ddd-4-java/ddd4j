"""验证发布候选性能报告的输入契约和归档行为。"""

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class PerformanceReportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()

    def setUp(self):
        self.report = {
            "schemaVersion": "1.0",
            "reportKind": "ddd4j-performance",
            "commit": self.head,
            "baseline": {"version": "2.0.0", "commit": "e" * 40},
            "environment": {
                "runnerId": "test-runner",
                "javaVersion": "17.0.20",
                "warmupIterations": 10,
                "measurementIterations": 20,
            },
            "generator": {"command": "fixture only"},
            "rawOutputSha256": "f" * 64,
            "scenarios": [
                {
                    "name": name,
                    "p95Millis": 100,
                    "throughputPerSecond": 1000,
                    "rssMegabytes": 250,
                    "baseline": {"p95Millis": 100, "throughputPerSecond": 1000, "rssMegabytes": 250},
                }
                for name in ("idempotency-lease", "mq-outbox-dispatch", "web-request-contract")
            ],
        }

    def assert_report(self, accepted, contents=None):
        if contents is None:
            contents = json.dumps(self.report).encode("utf-8")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "fixture.json"
            source.write_bytes(contents)
            output = root / "release-quality"
            result = subprocess.run(
                ["bash", "scripts/verify-performance-report.sh"],
                cwd=ROOT,
                env={**os.environ, "DDD4J_PERFORMANCE_REPORT": str(source), "DDD4J_REPORT_DIR": str(output)},
                capture_output=True,
                text=True,
            )
            archived = output / "performance/performance-report.json"
            detail = result.stdout + result.stderr
            if accepted:
                self.assertEqual(result.returncode, 0, detail)
                self.assertEqual(archived.read_bytes(), contents)
            else:
                self.assertEqual(result.returncode, 1, detail)
                self.assertIn("[FAIL] Performance evidence", result.stderr)
                self.assertFalse(output.exists())

    def test_java_17_forms_and_minimum_iterations_are_archived(self):
        environment = self.report["environment"]
        environment.update(warmupIterations=0, measurementIterations=1)
        for version in ("17", "17.0.20", "17.0.20.1+9-LTS", "17.0.20-ea+9", "17-ea", "17+-LTS"):
            with self.subTest(version=version):
                environment["javaVersion"] = version
                self.assert_report(accepted=True)

    def test_requires_a_nonblank_release_baseline_version(self):
        baseline = self.report["baseline"]
        del baseline["version"]
        with self.subTest(version="missing"):
            self.assert_report(accepted=False)
        for version in (None, "", " \t\n", 2, "2.0.0-SNAPSHOT", " 2.0.0-snapshot "):
            with self.subTest(version=version):
                baseline["version"] = version
                self.assert_report(accepted=False)

    def test_rejects_other_jdks_and_malformed_java_versions(self):
        for version in (None, 17, "1.8.0_432", "21.0.7", "170", "17test", "17.", "17.0", "17.00.7",
                        "17.0.20+", "17.0.20+01", "17.0.20\n"):
            with self.subTest(version=version):
                self.report["environment"]["javaVersion"] = version
                self.assert_report(accepted=False)

    def test_requires_integer_warmup_and_measurement_counts(self):
        environment = self.report["environment"]
        for field, extra_invalid in (("warmupIterations", ()), ("measurementIterations", (0,))):
            expected = environment.pop(field)
            with self.subTest(field=field, value="missing"):
                self.assert_report(accepted=False)
            for invalid in (None, True, "10", -1, 0.5, float("inf"), float("nan")) + extra_invalid:
                with self.subTest(field=field, value=invalid):
                    environment[field] = invalid
                    self.assert_report(accepted=False)
            environment[field] = expected

    def test_requires_exactly_one_json_document(self):
        valid = json.dumps(self.report).encode("utf-8")
        for contents in (b"", b"{", b"{}\n" + valid, valid + b"\n" + valid, valid + b"\ninvalid"):
            with self.subTest(contents=contents[:24]):
                self.assert_report(accepted=False, contents=contents)

    def test_requires_the_candidate_commit_and_each_scenario_once(self):
        self.report["commit"] = "0" * 40
        with self.subTest(change="commit mismatch"):
            self.assert_report(accepted=False)
        self.report["commit"] = self.head
        scenarios = self.report["scenarios"]
        last = scenarios.pop()
        with self.subTest(change="missing scenario"):
            self.assert_report(accepted=False)
        scenarios.append(scenarios[0])
        with self.subTest(change="duplicate replaces required scenario"):
            self.assert_report(accepted=False)
        scenarios[-1] = last
        scenarios.append(last)
        with self.subTest(change="extra scenario"):
            self.assert_report(accepted=False)

    def test_accepts_exactly_twenty_percent_and_rejects_each_overage(self):
        scenario = self.report["scenarios"][0]
        scenario.update(p95Millis=120, throughputPerSecond=800, rssMegabytes=300)
        self.assert_report(accepted=True)
        for field, overage in (("p95Millis", 120.01), ("throughputPerSecond", 799.99), ("rssMegabytes", 300.01)):
            with self.subTest(field=field):
                boundary = scenario[field]
                scenario[field] = overage
                self.assert_report(accepted=False)
                scenario[field] = boundary


    def test_accepts_decimal_twenty_percent_boundaries_and_rejects_any_overage(self):
        scenario = self.report["scenarios"][0]
        for metric, baseline, boundary, overage in (
            ("p95Millis", 0.19, 0.228, "0.228000000000000000000001"),
            ("throughputPerSecond", 100.01, 80.008, "80.007999999999999999999999"),
            ("rssMegabytes", 655.4, 786.48, "786.480000000000000000000001"),
        ):
            initial = scenario[metric]
            scenario["baseline"][metric] = baseline
            scenario[metric] = boundary
            with self.subTest(metric=metric, change="exact decimal limit"):
                self.assert_report(accepted=True)
            with self.subTest(metric=metric, change="just beyond decimal limit"):
                valid = json.dumps(self.report)
                number = f'"{metric}": {boundary},'
                self.assertEqual(valid.count(number), 1)
                rejected = valid.replace(number, f'"{metric}": {overage},').encode("utf-8")
                self.assert_report(accepted=False, contents=rejected)
            scenario[metric] = initial
            scenario["baseline"][metric] = initial

    def test_requires_positive_candidate_and_baseline_metrics(self):
        scenario = self.report["scenarios"][0]
        for metrics in (scenario, scenario["baseline"]):
            for metric in ("p95Millis", "throughputPerSecond", "rssMegabytes"):
                with self.subTest(metric=metric, baseline=metrics is scenario["baseline"]):
                    previous = metrics[metric]
                    metrics[metric] = 0
                    self.assert_report(accepted=False)
                    metrics[metric] = previous

    def test_rejects_nonstandard_json_and_requires_an_array_of_scenarios(self):
        self.report["invalidJsonConstant"] = float("nan")
        with self.subTest(change="non-JSON numeric constant"):
            self.assert_report(accepted=False)
        del self.report["invalidJsonConstant"]
        self.report["scenarios"] = {scenario["name"]: scenario for scenario in self.report["scenarios"]}
        with self.subTest(change="object instead of scenario array"):
            self.assert_report(accepted=False)

    def test_rejects_rounded_iterations_duplicate_fields_and_invalid_sha(self):
        data = json.dumps(self.report).encode()
        for title, previous, modified in (
            ("fractional iterations", b'"measurementIterations": 20', b'"measurementIterations": 20.000000000000000000000000000001'),
            ("duplicate candidate digest", b'"commit": ', b'"commit": "invalid", "commit": '),
            ("duplicate metric", b'"p95Millis": ', b'"p95Millis": 9000, "p95Millis": '),
            ("digest ending in newline", b'"' + b'e'*40 + b'"', b'"' + b'e'*40 + b'\\n"'),
            ("blank runner", b'"test-runner"', b'" \\t "'),
            ("blank generator", b'"fixture only"', b'" \\t "'),
        ):
            with self.subTest(title=title):
                self.assert_report(accepted=False, contents=data.replace(previous, modified, 1))


    def test_requires_exactly_one_json_report_in_downloaded_artifact(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact = root / "artifact"
            nested = artifact / "nested reports"
            nested.mkdir(parents=True)
            output = root / "release-quality"
            archived = output / "performance/performance-report.json"
            env = {**os.environ, "DDD4J_PERFORMANCE_REPORT": str(artifact), "DDD4J_REPORT_DIR": str(output)}
            command = ["bash", "scripts/verify-performance-report.sh"]

            empty = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(empty.returncode, 1, empty.stdout + empty.stderr)
            report = nested / "performance report.json"
            report.write_text(json.dumps(self.report))
            (artifact / "raw-output.txt").write_text("runner output")
            single = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(single.returncode, 0, single.stdout + single.stderr)
            self.assertEqual(archived.read_bytes(), report.read_bytes())

            (artifact / "second.json").write_text("{}")
            ambiguous = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(ambiguous.returncode, 1, ambiguous.stdout + ambiguous.stderr)
            self.assertFalse(archived.exists())

    def test_invalid_report_removes_only_previous_performance_archive(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            source = output / "fixture.json"
            source.write_text(json.dumps(self.report))
            env = {**os.environ, "DDD4J_PERFORMANCE_REPORT": str(source), "DDD4J_REPORT_DIR": str(output)}
            command = ["bash", "scripts/verify-performance-report.sh"]
            result = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            unrelated = output / "other-evidence.txt"
            unrelated.write_text("retained")
            source.write_text("{}")
            invalid = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True)
            self.assertEqual(invalid.returncode, 1, invalid.stdout + invalid.stderr)
            self.assertFalse((output / "performance/performance-report.json").exists())
            self.assertEqual(unrelated.read_text(), "retained")


