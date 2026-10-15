"""三线产品正文漂移和失效入口必须使文档门禁失败。"""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify-three-line-product-docs.py")


class ThreeLineProductDocsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.roots = [self.base / name for name in ("jdk8", "jdk17", "jdk21")]
        for root in self.roots:
            (root / "docs").mkdir(parents=True)
            for name in ("5、ddd4j-技术方案与路线.md", "6、ddd4j-产品与版本规划.md",
                         "7、ddd4j-领域模型设计.md", "8、ddd4j-Architecture.zh_CN.md",
                         "8、ddd4j-1.0.x-Architecture.zh_CN.md", "auth-satoken.md",
                         "webmvc-flow.md", "webflux-flow.md", "cqrs-es-base-capabilities.md"):
                (root / "docs" / name).write_text("共同正文\n", encoding="utf-8")
            for name in ("architecture/architecture.md", "superpowers/historical-evidence-index.md"):
                document = root / "docs" / name
                document.parent.mkdir(parents=True, exist_ok=True)
                document.write_text("共同正文\n", encoding="utf-8")
            for name in ("README.md", "README.en.md"):
                (root / name).write_text(
                    "[方案](docs/5、ddd4j-技术方案与路线.md) "
                    "[架构](docs/8、ddd4j-Architecture.zh_CN.md)\n", encoding="utf-8")
            for name in ("5、ddd4j-技术方案与路线.md", "6、ddd4j-产品与版本规划.md",
                         "7、ddd4j-领域模型设计.md", "8、ddd4j-Architecture.zh_CN.md"):
                (root / name).write_text(
                    f"# 文档已迁移\n\n请阅读 [{name}](docs/{name})。\n", encoding="utf-8")

    def check(self):
        output = self.base / "report"
        result = subprocess.run([sys.executable, str(SCRIPT), "--roots", *map(str, self.roots),
                                 "--output", str(output)], capture_output=True, text=True)
        return result, json.loads((output / "product-docs.json").read_text(encoding="utf-8"))

    def test_identical_canonical_documents_pass(self):
        result, report = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("PASS", report["status"])

    def test_changed_business_text_fails(self):
        (self.roots[1] / "docs/6、ddd4j-产品与版本规划.md").write_text("不同正文\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/6、ddd4j-产品与版本规划.md", report["differences"])

    def test_changed_shared_usage_guide_fails(self):
        (self.roots[1] / "docs/webmvc-flow.md").write_text("不同业务链路\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/webmvc-flow.md", report["differences"])

    def test_architecture_document_drift_fails(self):
        (self.roots[1] / "docs/architecture/architecture.md").write_text(
            "过时的单线架构\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/architecture/architecture.md", report["differences"])

    def test_missing_historical_index_fails(self):
        (self.roots[2] / "docs/superpowers/historical-evidence-index.md").unlink()
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/superpowers/historical-evidence-index.md", report["differences"])

    def test_missing_document_and_broken_readme_link_fail(self):
        (self.roots[2] / "docs/7、ddd4j-领域模型设计.md").unlink()
        (self.roots[0] / "README.md").write_text("[方案](missing.md)\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/7、ddd4j-领域模型设计.md", report["differences"])
        self.assertTrue(report["brokenLinks"])

    def test_legacy_root_entry_with_stale_body_fails(self):
        (self.roots[0] / "5、ddd4j-技术方案与路线.md").write_text("旧正文\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("5、ddd4j-技术方案与路线.md", report["differences"])

    def test_missing_legacy_root_entry_fails(self):
        (self.roots[1] / "8、ddd4j-Architecture.zh_CN.md").unlink()
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("8、ddd4j-Architecture.zh_CN.md", report["differences"])


if __name__ == "__main__":
    unittest.main()
