"""编译 API 比较必须覆盖擦除描述符之外的泛型和异常契约。"""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).with_name("verify-three-line-structure-api-parity.py")
SPEC = importlib.util.spec_from_file_location("three_line_structure", SOURCE)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ThreeLineCompiledApiMetadataTest(unittest.TestCase):
    def scan_live_classes(self, root):
        # 临时编译产物也须具备当前模块 POM 和 Git 清单；javac/javap 保持真实执行。
        pom = root / "ddd4j-core/pom.xml"
        pom.parent.mkdir(parents=True, exist_ok=True)
        pom.write_text("<project><modelVersion>4.0.0</modelVersion>"
                       "<groupId>example</groupId><artifactId>ddd4j-core</artifactId>"
                       "<version>1</version></project>\n", encoding="utf-8")
        (root / "pom.xml").write_text(
            "<project><modules><module>ddd4j-core</module></modules></project>\n", encoding="utf-8")
        with patch.object(MODULE.subprocess, "check_output", return_value=b"pom.xml\0ddd4j-core/pom.xml\0"):
            return MODULE.class_files(root)

    def compile_api(self, directory, name, method, *, type_parameters=""):
        root = Path(directory) / name
        source = root / "src/example/Api.java"
        source.parent.mkdir(parents=True)
        source.write_text(f"package example; public class Api{type_parameters} {{ {method} }}",
                          encoding="utf-8")
        classes = root / "ddd4j-core/target/classes"
        classes.mkdir(parents=True)
        subprocess.run(["javac", "-parameters", "-d", str(classes), str(source)], check=True,
                       capture_output=True, text=True)
        return MODULE.javap_api(self.scan_live_classes(root))

    def test_generic_arguments_are_part_of_public_api(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old",
                                   "public java.util.List<String> value(java.util.List<String> input) { return input; }")
            new = self.compile_api(directory, "new",
                                   "public java.util.List<Integer> value(java.util.List<Integer> input) { return input; }")
            self.assertNotEqual(old, new)

    def test_last_generic_method_signature_excludes_class_signature(self):
        with tempfile.TemporaryDirectory() as directory:
            api = self.compile_api(
                directory, "generic-owner",
                "public java.util.List<T> value(int key, java.util.List<T> entries) "
                "throws java.io.IOException { switch (key) { "
                "case 1: return entries; default: throw new java.io.IOException(); } }",
                type_parameters="<T>",
            )
            self.assertIn(
                "ddd4j-core|example/Api|value|(ILjava/util/List;)Ljava/util/List;"
                "|generic=(ILjava/util/List<TT;>;)Ljava/util/List<TT;>;"
                "|throws=Ljava/io/IOException;|constant=|params=key,entries",
                api,
            )

    def test_declared_checked_exceptions_are_part_of_public_api(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old",
                                   "public void value() throws java.io.IOException { }")
            new = self.compile_api(directory, "new",
                                   "public void value() throws java.sql.SQLException { }")
            self.assertNotEqual(old, new)

    def test_public_field_generic_arguments_are_part_of_public_api(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old", "public java.util.List<String> values;")
            new = self.compile_api(directory, "new", "public java.util.List<Integer> values;")
            self.assertNotEqual(old, new)

    def test_public_constant_value_is_part_of_public_api(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old", "public static final int LIMIT = 1;")
            new = self.compile_api(directory, "new", "public static final int LIMIT = 2;")
            self.assertNotEqual(old, new)

    def test_protected_nested_type_methods_are_part_of_public_api(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old",
                                   "protected static class Nested { public String value() { return \"x\"; } }")
            new = self.compile_api(directory, "new",
                                   "protected static class Nested { public int value() { return 1; } }")
            self.assertNotEqual(old, new)

    def test_equals_object_parameter_name_is_not_a_public_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old",
                                   "@Override public boolean equals(Object o) { return super.equals(o); }")
            new = self.compile_api(directory, "new",
                                   "@Override public boolean equals(Object obj) { return super.equals(obj); }")
            self.assertEqual(old, new)

    def test_other_method_parameter_name_remains_in_public_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old", "public String value(String key) { return key; }")
            new = self.compile_api(directory, "new", "public String value(String name) { return name; }")
            self.assertNotEqual(old, new)

    def test_same_binary_name_keeps_each_maven_module_api(self):
        with tempfile.TemporaryDirectory(prefix="module api ") as directory:
            root = Path(directory)
            classes = {}
            for module, method in (("data-plain", "String read(int key);"),
                                   ("data-plus", "long write(String value);")):
                project = root / module
                source = project / "src/example/Shared.java"
                source.parent.mkdir(parents=True)
                source.write_text("package example; public interface Shared { " + method + " }",
                                  encoding="utf-8")
                output = project / "target/classes"
                output.mkdir(parents=True)
                subprocess.run(["javac", "-parameters", "-d", str(output), str(source)], check=True,
                               capture_output=True, text=True)
                classes[f"{module}|example/Shared"] = output / "example/Shared.class"

            self.assertEqual({
                "data-plain|example/Shared|read|(I)Ljava/lang/String;|generic=|throws=|constant=|params=key",
                "data-plus|example/Shared|write|(Ljava/lang/String;)J|generic=|throws=|constant=|params=value",
            }, MODULE.javap_api(classes))

    def test_missing_classfile_header_cannot_reuse_previous_module(self):
        plain = Path.cwd() / "data-plain/target/classes/example/Shared.class"
        plus = Path.cwd() / "data-plus/target/classes/example/Shared.class"
        classes = {"data-plain|example/Shared": plain, "data-plus|example/Shared": plus}
        output = (f"Classfile {plain}\n"
                  "public interface example.Shared {\n"
                  "  public abstract java.lang.String read(int);\n"
                  "    descriptor: (I)Ljava/lang/String;\n"
                  "}\n"
                  "public interface example.Shared {\n"
                  "  public abstract long write(java.lang.String);\n"
                  "    descriptor: (Ljava/lang/String;)J\n"
                  "}\n")
        with patch.object(MODULE, "run", return_value=output):
            with self.assertRaisesRegex(ValueError, "cannot identify javap class example.Shared"):
                MODULE.javap_api(classes)

    def test_compiler_bridge_is_distinguished_from_explicit_method(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "bridge"
            source = root / "src/example/Api.java"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package example; interface Port<T> { T load(); } "
                "public class Api implements Port<String> { public String load() { return \"x\"; } }",
                encoding="utf-8")
            classes = root / "ddd4j-core/target/classes"
            classes.mkdir(parents=True)
            subprocess.run(["javac", "-parameters", "-d", str(classes), str(source)],
                           check=True, capture_output=True, text=True)
            bridges = set()
            api = MODULE.javap_api(self.scan_live_classes(root), bridge_entries=bridges)

            self.assertTrue(any("|example/Api|load|()Ljava/lang/String;" in item for item in api))
            self.assertEqual(1, len(bridges))
            self.assertTrue(any("|example/Api|load|()Ljava/lang/Object;" in item for item in bridges))
            self.assertTrue(bridges.issubset(api))


if __name__ == "__main__":
    unittest.main()
