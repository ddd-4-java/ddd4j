"""Spring 门禁必须发现属性、有效管理表和三线平台边界的漂移。"""

import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET


SPEC = importlib.util.spec_from_file_location(
    "spring_alignment", Path(__file__).with_name("verify_spring_version_alignment.py"))
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


def fixture(line="2.0.x"):
    java, framework, security, data, boot, servlet = {
        "1.0.x": ("1.8", "5.3.39", "5.8.16", "2.7.18", "2.7.18", "4.0.1"),
        "2.0.x": ("17", "6.2.19", "6.5.11", "3.5.13", "3.5.16", "6.1.0"),
        "3.0.x": ("21", "7.0.10", "7.1.2", "4.1.2", "4.1.2", "6.1.0"),
    }[line]
    props = {"revision": line + ".20260630-SNAPSHOT", "java.version": java,
             "maven.compiler.source": java, "maven.compiler.target": java}
    managed = {"org.springframework:spring-core": framework,
               "org.springframework:spring-tx": framework,
               "org.springframework.security:spring-security-core": security,
               "org.springframework.data:spring-data-jpa": data,
               "org.springframework.boot:spring-boot": boot}

    def project(name, properties, management):
        node = ET.Element("project")
        ET.SubElement(node, "artifactId").text = name
        ET.SubElement(node, "version").text = props["revision"]
        ps = ET.SubElement(node, "properties")
        for key, value in properties.items():
            ET.SubElement(ps, key).text = value
        ds = ET.SubElement(ET.SubElement(node, "dependencyManagement"), "dependencies")
        for coordinate, version in management.items():
            dep = ET.SubElement(ds, "dependency")
            group, artifact = coordinate.split(":")
            for key, value in (("groupId", group), ("artifactId", artifact), ("version", version)):
                ET.SubElement(dep, key).text = value
        return node

    root = project("ddd4j", props, {})
    props = {**props, "spring-framework.version": framework,
             "spring-security.version": security,
             "javax-servlet.version" if line == "1.0.x" else "jakarta-servlet.version": servlet}
    base = project("ddd4j-dependencies", props, managed)
    child = project("sample", props, managed)
    return {"ddd4j": root, "ddd4j-dependencies": base, "sample": child}


class SpringAlignmentTest(unittest.TestCase):
    def errors(self, models, line="2.0.x", expected=None):
        return checker.audit_models(models, expected or set(models), line)["errors"]

    def test_each_line_accepts_its_own_platform(self):
        for line in checker.LINES:
            with self.subTest(line=line):
                self.assertEqual([], self.errors(fixture(line), line))

    def test_unused_managed_dependency_drift_is_rejected(self):
        models = fixture()
        models["sample"].find("dependencyManagement/dependencies/dependency[artifactId='spring-tx']/version").text = "6.2.16"
        errors = self.errors(models)
        self.assertTrue(any(e.get("coordinate") == "org.springframework:spring-tx" for e in errors))

    def test_direct_explicit_version_cannot_bypass_management(self):
        models = fixture()
        dep = ET.SubElement(ET.SubElement(models["sample"], "dependencies"), "dependency")
        for key, value in (("groupId", "org.springframework"), ("artifactId", "spring-core"), ("version", "6.2.16")):
            ET.SubElement(dep, key).text = value
        self.assertTrue(self.errors(models))

    def test_shared_property_override_is_rejected_even_without_spring_usage(self):
        models = fixture()
        models["sample"].find("properties/spring-framework.version").text = "6.2.18"
        self.assertTrue(self.errors(models))

    def test_root_and_dependency_property_conflict_is_rejected(self):
        models = fixture()
        ET.SubElement(models["ddd4j"].find("properties"), "spring-framework.version").text = "6.2.18"
        self.assertTrue(self.errors(models))

    def test_identical_legacy_root_property_is_not_a_false_conflict(self):
        models = fixture()
        ET.SubElement(models["ddd4j"].find("properties"), "spring-framework.version").text = "6.2.19"
        self.assertEqual([], self.errors(models))

    def test_central_framework_family_must_also_be_internally_consistent(self):
        models = fixture()
        for name in ("ddd4j-dependencies", "sample"):
            models[name].find("dependencyManagement/dependencies/dependency[artifactId='spring-tx']/version").text = "6.2.16"
        self.assertTrue(self.errors(models))

    def test_spring7_is_rejected_on_jdk17_line(self):
        models = fixture()
        for name in ("ddd4j-dependencies", "sample"):
            models[name].find("properties/spring-framework.version").text = "7.0.10"
        self.assertTrue(self.errors(models))

    def test_spring6_data_generation_is_rejected_on_spring7_line(self):
        models = fixture("3.0.x")
        for name in ("ddd4j-dependencies", "sample"):
            models[name].find("dependencyManagement/dependencies/dependency[artifactId='spring-data-jpa']/version").text = "3.5.13"
        self.assertTrue(self.errors(models, "3.0.x"))

    def test_java8_final_public_spring_version_is_fixed(self):
        models = fixture("1.0.x")
        models["ddd4j-dependencies"].find("properties/spring-framework.version").text = "5.3.38"
        self.assertTrue(self.errors(models, "1.0.x"))

    def test_wrong_java_and_compiler_baselines_are_rejected(self):
        for key in ("java.version", "maven.compiler.source", "maven.compiler.target"):
            with self.subTest(key=key):
                models = fixture()
                models["sample"].find("properties/" + key).text = "21"
                self.assertTrue(self.errors(models))

    def test_wrong_snapshot_line_is_rejected(self):
        models = fixture()
        models["sample"].find("version").text = "3.0.x.20260630-SNAPSHOT"
        self.assertTrue(self.errors(models))

    def test_servlet6_and_spring7_minimum_are_checked(self):
        for line, version in (("2.0.x", "5.0.0"), ("3.0.x", "6.0.0")):
            models = fixture(line)
            models["ddd4j-dependencies"].find("properties/jakarta-servlet.version").text = version
            self.assertTrue(self.errors(models, line))

    def test_missing_project_is_not_success(self):
        self.assertTrue(self.errors(fixture(), expected={"ddd4j", "ddd4j-dependencies", "sample", "missing"}))

    def test_unresolved_version_is_not_success(self):
        models = fixture()
        models["sample"].find("dependencyManagement/dependencies/dependency/version").text = "${unknown}"
        self.assertTrue(self.errors(models))

    def test_non_spring_library_versions_are_outside_this_gate(self):
        models = fixture()
        for name, version in (("ddd4j-dependencies", "1.0"), ("sample", "2.0")):
            ds = models[name].find("dependencyManagement/dependencies")
            dep = ET.SubElement(ds, "dependency")
            for key, value in (("groupId", "example"), ("artifactId", "library"), ("version", version)):
                ET.SubElement(dep, key).text = value
        self.assertEqual([], self.errors(models))

    def test_maven3_and_maven4_namespaces_are_supported(self):
        with tempfile.TemporaryDirectory() as tmp:
            for namespace in ("4.0.0", "4.1.0"):
                node = copy.deepcopy(fixture()["sample"])
                for element in node.iter():
                    element.tag = "{http://maven.apache.org/POM/" + namespace + "}" + element.tag
                path = Path(tmp) / (namespace + ".xml")
                ET.ElementTree(node).write(path)
                self.assertEqual({"sample"}, set(checker.read_models([path])))

    def test_duplicate_effective_models_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "model.xml"
            ET.ElementTree(fixture()["sample"]).write(path)
            with self.assertRaises(ValueError):
                checker.read_models([path, path])

    def test_matrix_freezes_all_three_lines_and_uses_event_commit(self):
        for number in (1, 2, 3):
            branch = f"feature/{number}.0.x"
            result = checker.resolve_matrix(branch, "a" * 40, lambda ref: "b" * 40)
            self.assertEqual(["8", "17", "21"], [r["jdk"] for r in result["include"]])
            self.assertEqual("a" * 40, result["include"][number - 1]["sha"])
            self.assertEqual(3, len(result["include"]))

    def test_missing_remote_branch_or_unknown_line_fails_closed(self):
        with self.assertRaises(ValueError):
            checker.resolve_matrix("feature/2.0.x", "a" * 40, lambda ref: "")
        with self.assertRaises(ValueError):
            checker.resolve_matrix("main", "a" * 40, lambda ref: "b" * 40)


if __name__ == "__main__":
    unittest.main()
