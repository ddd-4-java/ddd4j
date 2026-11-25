#!/usr/bin/env python3
"""Verify the cloud MQ report validator with synthetic evidence only."""

import hashlib
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
        return self.verify_path(self.report_path)

    def verify_path(self, path):
        return subprocess.run(
            ["bash", str(SCRIPT)],
            env={
                **os.environ,
                "DDD4J_CLOUD_MQ_RC_REPORT": str(path),
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

    def test_downloaded_artifact_requires_exactly_one_json_report(self):
        artifact = self.root / "downloaded artifact"
        nested = artifact / "nested"
        nested.mkdir(parents=True)
        empty = self.verify_path(artifact)
        self.assertEqual(empty.returncode, 1, empty.stdout + empty.stderr)
        report = nested / "cloud report.json"
        report.write_text(json.dumps(self.report), encoding="utf-8")
        (artifact / "raw.log").write_text("runner log", encoding="utf-8")
        valid = self.verify_path(artifact)
        self.assertEqual(valid.returncode, 0, valid.stdout + valid.stderr)
        self.assertEqual(self.archived_report.read_bytes(), report.read_bytes())
        (artifact / "other.json").write_text("{}", encoding="utf-8")
        ambiguous = self.verify_path(artifact)
        self.assertEqual(ambiguous.returncode, 1, ambiguous.stdout + ambiguous.stderr)
        self.assertFalse(self.archived_report.exists())

    def test_invalid_report_removes_previous_archive(self):
        valid = self.verify()
        self.assertEqual(valid.returncode, 0, valid.stdout + valid.stderr)
        self.assertTrue(self.archived_report.is_file())
        unrelated = self.report_dir / "unrelated.txt"
        unrelated.write_text("retained", encoding="utf-8")
        self.report["executions"].pop()
        self.assert_rejected()
        self.assertEqual(unrelated.read_text(encoding="utf-8"), "retained")

    def test_report_requires_both_brokers(self):
        self.report["executions"].pop()
        self.assert_rejected()

    def broker_log_artifact(self):
        artifact = self.root / "protected-artifact"
        artifact.mkdir()
        for execution in self.report["executions"]:
            broker = execution["broker"]
            data = f"{broker} native proof fixture\n".encode("ascii")
            (artifact / f"{broker}.raw.log").write_bytes(data)
            execution["rawLogSha256"] = hashlib.sha256(data).hexdigest()
        (artifact / "cloud-mq-rc-report.json").write_text(json.dumps(self.report), encoding="utf-8")
        return artifact

    def test_attached_broker_logs_match_report_sha256(self):
        artifact = self.broker_log_artifact()
        valid = self.verify_path(artifact)
        self.assertEqual(valid.returncode, 0, valid.stdout + valid.stderr)
        self.assertEqual(self.archived_report.read_bytes(), (artifact / "cloud-mq-rc-report.json").read_bytes())

    def test_changed_broker_log_invalidates_previously_archived_report(self):
        artifact = self.broker_log_artifact()
        self.assertEqual(self.verify_path(artifact).returncode, 0)
        (artifact / "tdmq.raw.log").write_text("modified after report creation\n", encoding="utf-8")
        self.assertEqual(self.verify_path(artifact).returncode, 1)
        self.assertFalse(self.archived_report.exists())

    def test_broker_log_attachment_requires_both_logs(self):
        artifact = self.broker_log_artifact()
        (artifact / "ons.raw.log").unlink()
        self.assertEqual(self.verify_path(artifact).returncode, 1)
        self.assertFalse(self.archived_report.exists())

    def test_duplicate_broker_log_is_rejected(self):
        artifact = self.broker_log_artifact()
        (artifact / "another").mkdir()
        (artifact / "another" / "ons.raw.log").write_bytes((artifact / "ons.raw.log").read_bytes())
        self.assertEqual(self.verify_path(artifact).returncode, 1)
        self.assertFalse(self.archived_report.exists())

    def test_report_requires_current_commit(self):
        self.report["commit"] = "z" * 40
        self.assert_rejected()


if __name__ == "__main__":
    unittest.main()
