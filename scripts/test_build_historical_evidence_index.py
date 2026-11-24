"""Regression tests for the immutable three-line history index."""

import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("build-historical-evidence-index.py")
SPEC = importlib.util.spec_from_file_location("historical_evidence_index", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class HistoricalEvidenceIndexTest(unittest.TestCase):
    def test_dated_ecosystem_audit_is_in_index_scope(self):
        self.assertIn("docs/ECOSYSTEM-ARCHITECTURE.md", MODULE.SOURCE_DIRS)

    def test_dated_release_and_audit_documents_are_in_index_scope(self):
        for path in (
            "docs/RELEASE-NOTES-3.0.x-2026-09.md",
            "docs/architecture/cloud-agents-migration-evidence.md",
            "docs/architecture/cloud-exception-contract-audit.md",
            "docs/architecture/cloud-tenant-context-contract-audit.md",
            "docs/architecture/shiro-http-login-audit.md",
            "docs/architecture/shiro-real-provider-audit.md",
            "docs/migrations/truelicense4-framework-consumer-audit.md",
        ):
            with self.subTest(path=path):
                self.assertIn(path, MODULE.SOURCE_DIRS)

    def test_active_plan_is_not_indexed_as_history(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "pom.xml").write_text(
                "<project><properties><revision>3.0.x.20260630-SNAPSHOT"
                "</revision></properties></project>", encoding="utf-8")
            tracked = b"\0".join((
                b"docs/superpowers/plans/2026-10-10-three-line-product-convergence.md",
                b"docs/superpowers/plans/2026-09-01-historical.md",
                b"docs/superpowers/historical-evidence-index.md",
                b""))

            with mock.patch.object(MODULE, "git", return_value=tracked):
                paths = MODULE.indexed_paths(root, "3.0.x")

            self.assertEqual(
                ["docs/superpowers/plans/2026-09-01-historical.md"], paths)


class HistoricalEvidenceContentTest(unittest.TestCase):
    ARCHIVED_PLAN = "docs/superpowers/plans/2026-09-01-historical.md"
    NEW_PLAN = "docs/superpowers/plans/2026-11-24-current.md"

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        directory = Path(temporary.name)
        self.output = directory / "index.json"
        self.roots = [directory / line for line in MODULE.LINES]
        for line, root in zip(MODULE.LINES, self.roots):
            root.mkdir()
            self.git(root, "init", "-q")
            (root / "pom.xml").write_text(
                f"<project><properties><revision>{line}.20260630-SNAPSHOT"
                "</revision></properties></project>", encoding="utf-8")
            archive = root / self.ARCHIVED_PLAN
            archive.parent.mkdir(parents=True)
            archive.write_text(f"Committed history for {line}\n", encoding="utf-8")
            self.git(root, "add", "--", "pom.xml", self.ARCHIVED_PLAN)
            self.commit(root)
        result = self.run_index()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.copy_index()

    def git(self, root, *args):
        return subprocess.check_output(["git", "-C", str(root), *args])

    def commit(self, root):
        self.git(root, "-c", "user.name=History Fixture", "-c",
                 "user.email=history@example.test", "-c", "commit.gpgsign=false",
                 "commit", "-qm", "Index fixture")

    def copy_index(self):
        for root in self.roots:
            destination = root / MODULE.INDEX_PATH
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(self.output, destination)

    def run_index(self, *args):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--roots", *map(str, self.roots),
             "--output", str(self.output), *args], capture_output=True, text=True)

    def stage_new_plan(self):
        root = self.roots[1]
        (root / self.NEW_PLAN).write_text("New plan\n", encoding="utf-8")
        self.git(root, "add", "--", self.NEW_PLAN)
        return root

    def test_content_check_reads_the_committed_paths_and_bytes(self):
        root = self.stage_new_plan()
        (root / self.ARCHIVED_PLAN).write_text("Local edit\n", encoding="utf-8")
        self.git(root, "add", "--", self.ARCHIVED_PLAN)

        result = self.run_index("--check-content")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("verified: 3 records", result.stdout)

        result = self.run_index("--check")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("indexed documents have uncommitted changes", result.stderr)

    def test_staged_deletion_cannot_hide_committed_archive_from_index(self):
        self.git(self.roots[1], "rm", "--", self.ARCHIVED_PLAN)
        index = json.loads(self.output.read_text(encoding="utf-8"))
        index["entries"] = [entry for entry in index["entries"]
                            if entry["line"] != MODULE.LINES[1]]
        self.output.write_text(json.dumps(index), encoding="utf-8")
        self.copy_index()

        result = self.run_index("--check-content")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("historical evidence content is stale", result.stderr)

    def test_content_check_requires_a_new_archive_after_commit(self):
        self.commit(self.stage_new_plan())

        result = self.run_index("--check-content")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("historical evidence content is stale", result.stderr)


if __name__ == "__main__":
    unittest.main()
