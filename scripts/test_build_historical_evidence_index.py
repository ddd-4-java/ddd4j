"""Regression tests for the immutable three-line history index."""

import importlib.util
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


if __name__ == "__main__":
    unittest.main()
