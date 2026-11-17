"""三条线必须进入独立阻断矩阵，并保留失败证据。"""

from pathlib import Path
import unittest


class SpringWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (Path(__file__).resolve().parents[1] /
                         ".github/workflows/spring-version-alignment.yml").read_text()

    def test_push_pr_and_manual_run_cover_all_lines(self):
        self.assertIn("pull_request:", self.workflow)
        self.assertIn("workflow_dispatch:", self.workflow)
        for line in ("1.0.x", "2.0.x", "3.0.x"):
            self.assertGreaterEqual(self.workflow.count("feature/" + line), 2)

    def test_matrix_uses_immutable_target_and_event_checker(self):
        self.assertIn("checker.resolve_matrix", self.workflow)
        self.assertIn("ref: ${{ matrix.sha }}", self.workflow)
        self.assertIn("java-version: ${{ matrix.jdk }}", self.workflow)
        self.assertIn("controller/scripts/verify_spring_version_alignment.py", self.workflow)
        self.assertIn("github.base_ref || github.ref_name", self.workflow)
        self.assertIn("EVENT_SHA: ${{ github.sha }}", self.workflow)

    def test_failure_is_not_suppressed_and_evidence_is_always_uploaded(self):
        self.assertIn("fail-fast: false", self.workflow)
        self.assertNotIn("continue-on-error", self.workflow)
        self.assertNotIn("pull_request_target", self.workflow)
        self.assertIn("if: always()", self.workflow)
        self.assertIn("if-no-files-found: error", self.workflow)


if __name__ == "__main__":
    unittest.main()
