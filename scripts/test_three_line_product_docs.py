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
            for name in ("ddd4j-sample-quarkus-satoken", "ddd4j-sample-quarkus-shiro"):
                document = root / "ddd4j-samples" / name / "README.md"
                document.parent.mkdir(parents=True, exist_ok=True)
                document.write_text("共同鉴权样例\n", encoding="utf-8")
            (root / "REFACTOR_MIGRATION.md").write_text(
                "[示例总览](ddd4j-samples/README.md)\n", encoding="utf-8")
            (root / "ddd4j-samples/README.md").write_text(
                "[Sa-Token](ddd4j-sample-quarkus-satoken/README.md)\n", encoding="utf-8")
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

    def test_quarkus_auth_sample_readme_drift_fails(self):
        document = self.roots[1] / "ddd4j-samples/ddd4j-sample-quarkus-satoken/README.md"
        document.write_text("旧版登录接口\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ddd4j-samples/ddd4j-sample-quarkus-satoken/README.md", report["differences"])

    def test_drift_in_current_migration_or_sample_guide_fails(self):
        for relative in ("REFACTOR_MIGRATION.md", "ddd4j-samples/README.md"):
            with self.subTest(relative=relative):
                document = self.roots[1] / relative
                before = document.read_text(encoding="utf-8")
                document.write_text(before + "\n过时的接入说明\n", encoding="utf-8")
                result, report = self.check()
                self.assertNotEqual(0, result.returncode)
                self.assertIn(relative, report["differences"])
                document.write_text(before, encoding="utf-8")

    def test_current_sample_matrix_requires_linked_readme(self):
        relative = "ddd4j-samples/README.md"
        target = "ddd4j-sample-vertx/README.md"
        for root in self.roots:
            (root / relative).write_text(f"[Vert.x]({target})\n", encoding="utf-8")
            sample = root / "ddd4j-samples" / target
            sample.parent.mkdir()
            sample.write_text("Vert.x 运行说明\n", encoding="utf-8")
        (self.roots[0] / "ddd4j-samples" / target).unlink()
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn({"root": str(self.roots[0].resolve()), "source": relative,
                       "target": target}, report["brokenLinks"])

    def test_current_guides_allow_preserved_history_to_differ(self):
        markers = {
            "REFACTOR_MIGRATION.md": "\n---\n\n## 历史迁移记录\n",
            "ddd4j-samples/README.md": "\n## 历史验收记录\n",
        }
        for relative, marker in markers.items():
            with (self.roots[1] / relative).open("a", encoding="utf-8") as document:
                document.write(marker + "\n[当时的构件记录](previous-run.md)\n")
        result, report = self.check()
        self.assertEqual(0, result.returncode, report)
        for relative in markers:
            self.assertEqual(1, len(set(report["documents"][relative])))

    def test_missing_document_and_broken_readme_link_fail(self):
        (self.roots[2] / "docs/7、ddd4j-领域模型设计.md").unlink()
        (self.roots[0] / "README.md").write_text("[方案](missing.md)\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/7、ddd4j-领域模型设计.md", report["differences"])
        self.assertTrue(report["brokenLinks"])

    def test_missing_html_image_in_readme_fails(self):
        (self.roots[1] / "README.md").write_text(
            '<img src="assets/ddd4j-hero-v2.png" alt="hero" />\n', encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn({"root": str(self.roots[1].resolve()), "source": "README.md",
                       "target": "assets/ddd4j-hero-v2.png"}, report["brokenLinks"])

    def test_missing_local_script_in_usage_guide_fails(self):
        (self.roots[0] / "README.en.md").write_text(
            "[Run verifier](scripts/missing-verifier.py)\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn({"root": str(self.roots[0].resolve()), "source": "README.en.md",
                       "target": "scripts/missing-verifier.py"}, report["brokenLinks"])

    def test_legacy_root_entry_with_stale_body_fails(self):
        (self.roots[0] / "5、ddd4j-技术方案与路线.md").write_text("旧正文\n", encoding="utf-8")
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("5、ddd4j-技术方案与路线.md", report["differences"])

    def test_absent_middle_redirects_pass_without_dangling_links(self):
        names = ("5、ddd4j-技术方案与路线.md", "6、ddd4j-产品与版本规划.md",
                 "7、ddd4j-领域模型设计.md", "8、ddd4j-Architecture.zh_CN.md")
        for name in names:
            (self.roots[1] / name).unlink()
        result, report = self.check()
        self.assertEqual(0, result.returncode, report)
        self.assertEqual({}, report["differences"])
        self.assertEqual([], report["brokenLinks"])

    def test_missing_jdk8_and_jdk21_redirects_still_fail(self):
        for index in (0, 2):
            for name in ("5、ddd4j-技术方案与路线.md", "6、ddd4j-产品与版本规划.md",
                         "7、ddd4j-领域模型设计.md", "8、ddd4j-Architecture.zh_CN.md"):
                with self.subTest(jdk=(8, 17, 21)[index], name=name):
                    path = self.roots[index] / name
                    text = path.read_text(encoding="utf-8")
                    path.unlink()
                    result, report = self.check()
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(name, report["differences"])
                    self.assertIn({"root": str(self.roots[index].resolve()), "source": name,
                                   "target": "MISSING"}, report["brokenLinks"])
                    path.write_text(text, encoding="utf-8")

    def test_any_present_middle_redirect_requires_exact_migrated_text(self):
        for name in ("5、ddd4j-技术方案与路线.md", "6、ddd4j-产品与版本规划.md",
                     "7、ddd4j-领域模型设计.md", "8、ddd4j-Architecture.zh_CN.md"):
            with self.subTest(name=name):
                path = self.roots[1] / name
                original = path.read_text(encoding="utf-8")
                path.write_text("旧正文\n", encoding="utf-8")
                result, report = self.check()
                self.assertNotEqual(0, result.returncode)
                self.assertIn(name, report["differences"])
                path.write_text(original, encoding="utf-8")

    def test_readme_link_to_absent_middle_redirect_still_fails(self):
        for name in ("5、ddd4j-技术方案与路线.md", "6、ddd4j-产品与版本规划.md",
                     "7、ddd4j-领域模型设计.md", "8、ddd4j-Architecture.zh_CN.md"):
            with self.subTest(name=name):
                path = self.roots[1] / name
                original = path.read_text(encoding="utf-8")
                path.unlink()
                (self.roots[1] / "README.md").write_text(f"[旧入口]({name})\n", encoding="utf-8")
                result, report = self.check()
                self.assertNotEqual(0, result.returncode)
                self.assertIn({"root": str(self.roots[1].resolve()), "source": "README.md",
                               "target": name}, report["brokenLinks"])
                self.assertNotIn(name, report["differences"])
                path.write_text(original, encoding="utf-8")

    def test_absent_middle_redirect_does_not_make_canonical_document_optional(self):
        name = "7、ddd4j-领域模型设计.md"
        (self.roots[1] / name).unlink()
        (self.roots[1] / "docs" / name).unlink()
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("docs/" + name, report["differences"])

    def test_other_missing_middle_root_document_still_fails(self):
        (self.roots[1] / "README.en.md").unlink()
        result, report = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn({"root": str(self.roots[1].resolve()), "source": "README.en.md",
                       "target": "MISSING"}, report["brokenLinks"])


if __name__ == "__main__":
    unittest.main()
