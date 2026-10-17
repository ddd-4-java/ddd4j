"""工作流必须使用所属发布线的 JDK，并让必需构建失败可见。"""

from pathlib import Path
import os
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkflowLineBaselineTest(unittest.TestCase):
    def setUp(self):
        pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
        self.jdk = re.search(r"<java.version>([^<]+)</java.version>", pom).group(1)

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
            "ddd4j-mq/ddd4j-mq-rabbitmq",
            "ddd4j-mq/ddd4j-mq-nats",
            "ddd4j-mq/ddd4j-mq-mqtt",
            "ddd4j-mq/ddd4j-mq-mqtt-mica",
            "ddd4j-mq/ddd4j-mq-activemq",
        ):
            with self.subTest(module=module):
                self.assertIn(module, modules)

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


if __name__ == "__main__":
    unittest.main()
