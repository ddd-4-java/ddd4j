"""编译 API 比较必须覆盖擦除描述符之外的泛型和异常契约。"""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).with_name("verify-three-line-structure-api-parity.py")
SPEC = importlib.util.spec_from_file_location("three_line_structure", SOURCE)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ThreeLineCompiledApiMetadataTest(unittest.TestCase):
    def compile_api(self, directory, name, method):
        root = Path(directory) / name
        source = root / "src/example/Api.java"
        source.parent.mkdir(parents=True)
        source.write_text("package example; public class Api { " + method + " }", encoding="utf-8")
        classes = root / "ddd4j-core/target/classes"
        classes.mkdir(parents=True)
        subprocess.run(["javac", "-parameters", "-d", str(classes), str(source)], check=True,
                       capture_output=True, text=True)
        return MODULE.javap_api(MODULE.class_files(root))

    def test_generic_arguments_are_part_of_public_api(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.compile_api(directory, "old",
                                   "public java.util.List<String> value(java.util.List<String> input) { return input; }")
            new = self.compile_api(directory, "new",
                                   "public java.util.List<Integer> value(java.util.List<Integer> input) { return input; }")
            self.assertNotEqual(old, new)

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
            api = MODULE.javap_api(MODULE.class_files(root), bridge_entries=bridges)

            self.assertTrue(any("|example/Api|load|()Ljava/lang/String;" in item for item in api))
            self.assertEqual(1, len(bridges))
            self.assertTrue(any("|example/Api|load|()Ljava/lang/Object;" in item for item in bridges))
            self.assertTrue(bridges.issubset(api))


if __name__ == "__main__":
    unittest.main()
