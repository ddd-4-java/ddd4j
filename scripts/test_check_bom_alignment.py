"""回归验证 BOM 检查不会漏掉子模块的硬编码 ddd4j 版本。"""

import pathlib
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("check-bom-alignment.sh")


class BomAlignmentTest(unittest.TestCase):
    def run_fixture(self, version, namespace, parent_version=None):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            scripts = root / "scripts"
            scripts.mkdir()
            shutil.copy2(SCRIPT, scripts / SCRIPT.name)
            bom = root / "ddd4j-bom"
            bom.mkdir()
            (bom / "pom.xml").write_text("<project/>")
            module = root / "sample"
            module.mkdir()
            parent = ("  <parent><groupId>io.ddd4j</groupId>"
                      "<artifactId>ddd4j-dependencies</artifactId>"
                      f"<version>{parent_version}</version></parent>\n") if parent_version else ""
            (module / "pom.xml").write_text(
                f'<project xmlns="http://maven.apache.org/POM/{namespace}">\n'
                + parent +
                '  <dependencies><dependency>\n'
                '    <groupId>io.ddd4j</groupId>\n'
                '    <artifactId>ddd4j-core</artifactId>\n'
                '    <scope>compile</scope>\n'
                f'    <version>{version}</version>\n'
                '  </dependency></dependencies>\n'
                '</project>\n')
            return subprocess.run(["bash", str(scripts / SCRIPT.name)],
                                  cwd=root, capture_output=True, text=True, check=False)

    def test_hardcoded_dependency_version_fails_for_both_maven_models(self):
        for namespace in ("4.0.0", "4.1.0"):
            with self.subTest(namespace=namespace):
                result = self.run_fixture("9.9.9", namespace)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn("sample/pom.xml", result.stdout)

    def test_revision_placeholder_passes(self):
        result = self.run_fixture("${revision}", "4.0.0")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_maven4_fixed_parent_is_not_a_dependency_override(self):
        result = self.run_fixture("${revision}", "4.1.0", "3.0.x.20260630-SNAPSHOT")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
