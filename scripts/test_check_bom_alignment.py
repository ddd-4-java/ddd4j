"""回归验证 BOM 检查不会漏掉子模块的硬编码 ddd4j 版本。"""

import pathlib
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("check-bom-alignment.sh")


class BomAlignmentTest(unittest.TestCase):
    def run_fixture(self, version, namespace, parent_version=None, root_properties="",
                    dependency_properties="", orphan_poms=False, broken_module=None, profile=False,
                    reactor_artifact="", bom_dependencies="", deploy_skip=None, group_deploy_skip=False):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            scripts = root / "scripts"
            scripts.mkdir()
            shutil.copy2(SCRIPT, scripts / SCRIPT.name)
            dependencies = root / "ddd4j-dependencies"
            dependencies.mkdir()
            (dependencies / "pom.xml").write_text(
                f"<project><properties>{dependency_properties}</properties></project>")
            bom = root / "ddd4j-bom"
            bom.mkdir()
            (bom / "pom.xml").write_text(
                "<project><dependencyManagement><dependencies>"
                + bom_dependencies + "</dependencies></dependencyManagement></project>")
            modules, entry = ("subprojects", "subproject") if namespace == "4.1.0" else ("modules", "module")
            root_declaration = f"<{modules}><{entry}>group</{entry}></{modules}>"
            (root / "pom.xml").write_text(
                f"<project><properties>{root_properties}</properties>{root_declaration}</project>")
            group = root / "group"
            group.mkdir()
            (group / "pom.xml").write_text(
                f"<project><groupId>io.ddd4j</groupId><artifactId>group</artifactId>"
                f"<packaging>pom</packaging><{modules}><{entry}>sample</{entry}></{modules}>"
                + ("<properties><maven.deploy.skip>true</maven.deploy.skip></properties>" if group_deploy_skip else "")
                + "</project>")
            module = group / "sample"
            module.mkdir()
            parent = ("  <parent><groupId>io.ddd4j</groupId>"
                      "<artifactId>ddd4j-dependencies</artifactId>"
                      f"<version>{parent_version}</version></parent>\n") if parent_version else ""
            if group_deploy_skip:
                parent = ("<parent><groupId>io.ddd4j</groupId><artifactId>group</artifactId>"
                          "<version>1</version></parent>")
            profile_start = "<profiles><profile><id>optional</id>" if profile else ""
            profile_end = "</profile></profiles>" if profile else ""
            model = (
                f'<project xmlns="http://maven.apache.org/POM/{namespace}">\n'
                + parent
                + ("<groupId>io.ddd4j</groupId><artifactId>" + reactor_artifact + "</artifactId>"
                   if reactor_artifact else "")
                + (f"<properties><maven.deploy.skip>{str(deploy_skip).lower()}</maven.deploy.skip></properties>"
                   if deploy_skip is not None else "")
                + profile_start +
                '  <dependencies><dependency>\n'
                '    <groupId>io.ddd4j</groupId>\n'
                '    <artifactId>ddd4j-core</artifactId>\n'
                '    <scope>compile</scope>\n'
                f'    <version>{version}</version>\n'
                '  </dependency></dependencies>\n'
                + profile_end + '</project>\n')
            if broken_module != "missing":
                (module / "pom.xml").write_text("<project>" if broken_module == "malformed" else model)
            if orphan_poms:
                consumer = module / "src/test/resources/consumer"
                consumer.mkdir(parents=True)
                (consumer / "pom.xml").write_text(model.replace(version, "9.9.9"))
                evidence = root / ".superpowers/history"
                evidence.mkdir(parents=True)
                (evidence / "pom.xml").write_text("<project>")
            return subprocess.run(["bash", str(scripts / SCRIPT.name)],
                                  cwd=root, capture_output=True, text=True, check=False)

    def test_public_jar_requires_a_main_artifact_bom_entry(self):
        coordinate = ("<dependency><groupId>io.ddd4j</groupId>"
                      "<artifactId>ddd4j-web-example</artifactId>"
                      "<version>${revision}</version>")
        for namespace in ("4.0.0", "4.1.0"):
            for entry in ("", coordinate + "<classifier>tests</classifier></dependency>"):
                with self.subTest(namespace=namespace, entry=entry):
                    result = self.run_fixture("${revision}", namespace, reactor_artifact="ddd4j-web-example",
                                              bom_dependencies=entry)
                    self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                    self.assertIn("BOM 未管理公开 Jar io.ddd4j:ddd4j-web-example", result.stdout)
            result = self.run_fixture("${revision}", namespace, reactor_artifact="ddd4j-web-example",
                                      bom_dependencies=coordinate + "</dependency>")
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_bom_rejects_removed_and_duplicate_reactor_artifacts(self):
        coord = ("<dependency><groupId>io.ddd4j</groupId><artifactId>ddd4j-web-example</artifactId>"
                 "<version>${revision}</version></dependency>")
        stale = self.run_fixture("${revision}", "4.0.0", bom_dependencies=coord)
        self.assertEqual(stale.returncode, 1, stale.stdout + stale.stderr)
        self.assertIn("BOM 指向不存在的 reactor 模块", stale.stdout)
        duplicate = self.run_fixture("${revision}", "4.0.0", reactor_artifact="ddd4j-web-example",
                                     bom_dependencies=coord * 2)
        self.assertEqual(duplicate.returncode, 1, duplicate.stdout + duplicate.stderr)
        self.assertIn("BOM 重复坐标", duplicate.stdout)

    def test_explicit_deploy_enabled_child_requires_bom_under_skipped_parent(self):
        result = self.run_fixture("${revision}", "4.0.0", reactor_artifact="ddd4j-sample-shared",
                                  group_deploy_skip=True, deploy_skip=False)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("BOM 未管理公开 Jar io.ddd4j:ddd4j-sample-shared", result.stdout)

    def test_deploy_skipped_modules_and_internal_testkits_are_optional_in_bom(self):
        skipped = self.run_fixture("${revision}", "4.0.0", reactor_artifact="ddd4j-sample-example",
                                    deploy_skip=True)
        self.assertEqual(skipped.returncode, 0, skipped.stdout + skipped.stderr)
        testkit = self.run_fixture("${revision}", "4.0.0", reactor_artifact="ddd4j-web-testkit")
        self.assertEqual(testkit.returncode, 0, testkit.stdout + testkit.stderr)

    def test_hardcoded_dependency_version_fails_for_both_maven_models(self):
        for namespace in ("4.0.0", "4.1.0"):
            with self.subTest(namespace=namespace):
                result = self.run_fixture("9.9.9", namespace)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn("group/sample/pom.xml", result.stdout)

    def test_skips_standalone_consumer_and_archive_poms(self):
        for namespace in ("4.0.0", "4.1.0"):
            with self.subTest(namespace=namespace):
                result = self.run_fixture("${revision}", namespace, orphan_poms=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("扫描模块总数: 3", result.stdout)

    def test_declared_missing_or_malformed_module_fails_closed(self):
        for broken in ("missing", "malformed"):
            with self.subTest(broken=broken):
                result = self.run_fixture("${revision}", "4.1.0", broken_module=broken)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("group/sample/pom.xml", result.stderr)

    def test_hardcoded_profile_dependency_in_reactor_still_fails(self):
        result = self.run_fixture("9.9.9", "4.0.0", profile=True)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("group/sample/pom.xml", result.stdout)

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
