#!/usr/bin/env python3
"""Verify the cloud MQ report validator with synthetic evidence only."""

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().with_name("verify-cloud-mq-rc-evidence.sh")


class CloudMqEvidenceTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.report_path = self.root / "report.json"
        self.report_dir = self.root / "release-quality"
        self.archived_report = self.report_dir / "cloud-mq-rc" / "cloud-mq-rc-report.json"
        self.report = {
            "schemaVersion": "1.0",
            "reportKind": "ddd4j-cloud-mq-rc",
            "commit": subprocess.check_output(
                ["git", "rev-parse", "HEAD"], cwd=SCRIPT.parent, text=True
            ).strip(),
            "generatedAt": "2026-08-03T12:00:00Z",
            "executions": [
                {
                    "broker": broker,
                    "result": "passed",
                    "region": "cn-example",
                    "endpointHost": f"{broker}.example.test",
                    "clientVersion": "1.0",
                    "checks": ["publish", "consume", "ack", "retry", "message-id"],
                    "rawLogSha256": "a" * 64,
                    "evidenceUri": uri,
                }
                for broker, uri in (
                    ("ons", "https://audit.example.test/artifacts/ons@release?run=1#report"),
                    ("tdmq", "artifact://cloud-mq/tdmq"),
                )
            ],
        }

    def verify(self, content=None):
        if content is None:
            content = json.dumps(self.report)
        self.report_path.write_text(content, encoding="utf-8")
        return subprocess.run(
            ["bash", str(SCRIPT)],
            env={
                **os.environ,
                "DDD4J_CLOUD_MQ_RC_REPORT": str(self.report_path),
                "DDD4J_REPORT_DIR": str(self.report_dir),
            },
            capture_output=True,
            text=True,
            check=False,
        )

    def assert_rejected(self):
        result = self.verify()
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("[FAIL] Cloud MQ evidence", result.stderr)
        self.assertFalse(self.archived_report.exists())

    def test_valid_hostnames_and_uri_formats_archive_evidence(self):
        self.report["executions"][1]["endpointHost"] = "TDMQ-1.Example.Test."
        result = self.verify()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.archived_report.read_bytes(), self.report_path.read_bytes())

    def test_endpoint_host_rejects_values_other_than_hostnames(self):
        for endpoint in (
            "https://ons.example.test",
            "account@ons.example.test",
            "ons.example.test:443",
            "ons.example.test/path",
            "ons.example.test\n",
            "-ons.example.test",
            "ons..example.test",
            "a" * 64 + ".example.test",
        ):
            with self.subTest(endpoint=endpoint):
                self.report["executions"][0]["endpointHost"] = endpoint
                self.assert_rejected()

    def test_evidence_uri_rejects_embedded_credentials(self):
        for uri in (
            "https://account:synthetic-value@audit.example.test/artifact",
            "artifact://account@cloud-mq/ons",
            "https://audit.example.test/artifact?run=1&access_key=synthetic-value",
            "https://audit.example.test/artifact?TOKEN=synthetic-value",
            "https://audit.example.test/artifact?password=synthetic-value",
            "https://audit.example.test/artifact#access_token=synthetic-value",
            "https://audit.example.test/artifact?client%5Fsecret=synthetic-value",
            "https://audit.example.test/artifact?refresh_token=synthetic-value",
        ):
            with self.subTest(uri=uri):
                self.report["executions"][0]["evidenceUri"] = uri
                self.assert_rejected()

    def test_report_rejects_explicit_credential_field(self):
        self.report["executions"][0]["access_key"] = "synthetic-value"
        self.assert_rejected()

    def test_rejects_multiple_json_documents_without_archiving(self):
        first = json.loads(json.dumps(self.report))
        first["executions"][0]["password"] = "synthetic-value"
        outcome = self.verify(json.dumps(first) + "\n" + json.dumps(self.report))
        self.assertNotEqual(outcome.returncode, 0)
        self.assertFalse(self.archived_report.exists())

    def test_rejects_duplicate_member_without_archiving(self):
        content = json.dumps(self.report).replace(
            '"evidenceUri":', '"evidenceUri": "https://example.test?secret=synthetic-value", "evidenceUri":', 1
        )
        outcome = self.verify(content)
        self.assertNotEqual(outcome.returncode, 0)
        self.assertFalse(self.archived_report.exists())

    def test_failed_validation_does_not_echo_report_values(self):
        self.report["executions"][0]["rawLogSha256"] = {"synthetic-value": "Z9"}
        outcome = self.verify()
        self.assertNotEqual(outcome.returncode, 0)
        self.assertNotIn("Z9", outcome.stdout + outcome.stderr)
        self.assertFalse(self.archived_report.exists())

    def test_report_requires_both_brokers(self):
        self.report["executions"].pop()
        self.assert_rejected()

    def test_report_requires_current_commit(self):
        self.report["commit"] = "z" * 40
        self.assert_rejected()


if __name__ == "__main__":
    unittest.main()
