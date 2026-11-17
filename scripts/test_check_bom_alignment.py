"""回归验证 BOM 检查不会漏掉子模块的硬编码 ddd4j 版本。"""

import pathlib
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("check-bom-alignment.sh")


class BomAlignmentTest(unittest.TestCase):
    def run_fixture(self, version, namespace, parent_version=None, root_properties="", dependency_properties=""):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            scripts = root / "scripts"
            scripts.mkdir()
            shutil.copy2(SCRIPT, scripts / SCRIPT.name)
            (root / "pom.xml").write_text(
                f"<project><properties>{root_properties}</properties></project>")
            dependencies = root / "ddd4j-dependencies"
            dependencies.mkdir()
            (dependencies / "pom.xml").write_text(
                f"<project><properties>{dependency_properties}</properties></project>")
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

    def test_conflicting_version_property_in_parent_and_dependencies_fails(self):
        result = self.run_fixture("${revision}", "4.0.0",
                                  root_properties="<spring-security.version>6.5.10</spring-security.version>",
                                  dependency_properties="<spring-security.version>6.5.11</spring-security.version>")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("spring-security.version", result.stdout)

    def test_identical_duplicate_version_property_also_fails_to_prevent_future_drift(self):
        declaration = "<jackson.version>2.22.4</jackson.version>"
        result = self.run_fixture("${revision}", "4.0.0",
                                  root_properties=declaration, dependency_properties=declaration)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("jackson.version", result.stdout)

    def test_dependency_property_has_one_owner(self):
        result = self.run_fixture("${revision}", "4.0.0",
                                  root_properties="<maven-jandex-plugin.version>3.6.0</maven-jandex-plugin.version>",
                                  dependency_properties="<spring-security.version>6.5.11</spring-security.version>")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_jdk17_line_rejects_spring7(self):
        result = self.run_fixture("${revision}", "4.0.0",
                                  root_properties="<revision>2.0.x.20260630-SNAPSHOT</revision><java.version>17</java.version>",
                                  dependency_properties="<spring-framework.version>7.0.10</spring-framework.version><jakarta-servlet.version>6.1.0</jakarta-servlet.version>")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("spring-framework.version", result.stdout)

    def test_jdk17_line_rejects_spring7_companion_components(self):
        result = self.run_fixture("${revision}", "4.0.0",
                                  root_properties="<revision>2.0.x.20260630-SNAPSHOT</revision><java.version>17</java.version>",
                                  dependency_properties="<spring-framework.version>6.2.19</spring-framework.version><spring-batch.version>6.0.4</spring-batch.version><spring-integration.version>7.1.0</spring-integration.version><jakarta-servlet.version>6.1.0</jakarta-servlet.version>")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("spring-batch.version", result.stdout)
        self.assertIn("spring-integration.version", result.stdout)

    def test_jdk17_spring6_servlet6_contract_passes(self):
        for version in ("6.0.0", "6.1.0"):
            with self.subTest(version=version):
                result = self.run_fixture("${revision}", "4.0.0",
                                          root_properties="<revision>2.0.x.20260630-SNAPSHOT</revision><java.version>17</java.version>",
                                          dependency_properties=f"<spring-framework.version>6.2.19</spring-framework.version><jakarta-servlet.version>{version}</jakarta-servlet.version>")
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
