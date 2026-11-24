"""API 归一化只能消除允许的命名空间变化，不能抹去参数类型身份。"""

import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "structure_api_parity",
    pathlib.Path(__file__).with_name("verify-three-line-structure-api-parity.py"),
)
parity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parity)


class DescriptorNormalizationTest(unittest.TestCase):
    OBJECT_MAPPER_MAPPING = {
        "id": "jackson-objectmapper-namespace",
        "types": {
            "1.0.x": "com.fasterxml.jackson.databind.ObjectMapper",
            "2.0.x": "com.fasterxml.jackson.databind.ObjectMapper",
            "3.0.x": "tools.jackson.databind.ObjectMapper",
        },
        "reason": "exact Jackson 2/3 ObjectMapper adaptation",
        "evidence": "ddd4j-core/src/test/java/io/ddd4j/core/cqrs/eventstore/jackson/EventPayloadSerializerTest.java",
    }

    def parse_parameter_table(self, table):
        output = """public class example.Adapter {
  public java.lang.Object handle(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
    flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
""" + table + "\n}\n"
        with patch.object(parity, "run", return_value=output):
            return parity.javap_api({"module|example/Adapter": pathlib.Path("Adapter.class")})

    def test_unnamed_parameter_is_not_a_parameter_named_no(self):
        unnamed = "    MethodParameters:\n      Name                           Flags\n      <no name>                      synthetic\n"
        self.assertEqual(self.parse_parameter_table(""), self.parse_parameter_table(unnamed))

    def test_real_parameter_name_is_not_erased(self):
        named = "    MethodParameters:\n      Name                           Flags\n      request\n"
        self.assertNotEqual(self.parse_parameter_table(""), self.parse_parameter_table(named))

    def test_equivalent_jackson_namespace_migration(self):
        self.assertEqual(
            parity.normalize_descriptor("(Lcom/fasterxml/jackson/databind/ObjectMapper;)V",
                                        [self.OBJECT_MAPPER_MAPPING], "1.0.x"),
            parity.normalize_descriptor("(Ltools/jackson/databind/ObjectMapper;)V",
                                        [self.OBJECT_MAPPER_MAPPING], "3.0.x"),
        )

    def test_unmapped_jackson_types_remain_different(self):
        self.assertNotEqual(
            parity.normalize_descriptor("(Lcom/fasterxml/jackson/databind/JsonNode;)V",
                                        [self.OBJECT_MAPPER_MAPPING], "1.0.x"),
            parity.normalize_descriptor("(Ltools/jackson/databind/JsonNode;)V"),
        )

    def test_parameter_order_remains_observable(self):
        self.assertNotEqual(
            parity.normalize_descriptor("(Ltools/jackson/databind/JsonNode;Ltools/jackson/core/JsonParser;)V"),
            parity.normalize_descriptor("(Ltools/jackson/core/JsonParser;Ltools/jackson/databind/JsonNode;)V"),
        )

    def test_array_and_return_type_preserve_identity(self):
        self.assertEqual(
            "([LDDD4J_MAPPED/jackson-objectmapper-namespace;)Ltools/jackson/core/JsonParser;",
            parity.normalize_descriptor(
                "([Lcom/fasterxml/jackson/databind/ObjectMapper;)Ltools/jackson/core/JsonParser;",
                [self.OBJECT_MAPPER_MAPPING], "1.0.x"),
        )


class StaticImportNormalizationTest(unittest.TestCase):
    BASE = ("package example; import static org.junit.jupiter.api.Assertions.*; "
            "class ContractTest { void check() { assertTrue(true); } }")

    def test_explicit_import_covered_by_same_owner_wildcard_is_equivalent(self):
        redundant = self.BASE.replace(
            "import static org.junit.jupiter.api.Assertions.*;",
            "import static org.junit.jupiter.api.Assertions.assertTrue; "
            "import static org.junit.jupiter.api.Assertions.*;")
        self.assertEqual(parity.java_tokens_with_normalized_imports(self.BASE),
                         parity.java_tokens_with_normalized_imports(redundant))

    def test_different_owner_and_changed_assertion_remain_distinct(self):
        different_owner = self.BASE.replace(
            "import static org.junit.jupiter.api.Assertions.*;",
            "import static example.Other.assertTrue; "
            "import static org.junit.jupiter.api.Assertions.*;")
        changed_assertion = self.BASE.replace("assertTrue(true)", "assertTrue(false)")
        self.assertNotEqual(parity.java_tokens_with_normalized_imports(self.BASE),
                            parity.java_tokens_with_normalized_imports(different_owner))
        self.assertNotEqual(parity.java_tokens_with_normalized_imports(self.BASE),
                            parity.java_tokens_with_normalized_imports(changed_assertion))

    def test_explicit_import_with_multiple_static_wildcards_remains_distinct(self):
        wildcards = ("package example; import static example.First.*; "
                     "import static example.Second.*; class ContractTest { "
                     "void check() { select(\"value\"); } }")
        explicit = wildcards.replace("import static example.First.*;",
                                     "import static example.First.select; "
                                     "import static example.First.*;")
        self.assertNotEqual(parity.java_tokens_with_normalized_imports(wildcards),
                            parity.java_tokens_with_normalized_imports(explicit))


class ClassFileScopeTest(unittest.TestCase):
    """编译 API 只读取当前 Git 文件清单中仍有 POM 的模块输出。"""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.poms = ["pom.xml", "ddd4j-core/pom.xml", "ddd4j-samples/ddd4j-sample-quarkus/pom.xml"]
        self.core = self.write_class("ddd4j-core", "example/Core")
        self.sample = self.write_class("ddd4j-samples/ddd4j-sample-quarkus", "example/Sample")
        for name in self.poms:
            self.write_pom(name)
        self.write_pom("pom.xml", ["ddd4j-core", "ddd4j-samples/ddd4j-sample-quarkus"])

    def write_pom(self, name, modules=()):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        children = "".join("<module>" + module + "</module>" for module in modules)
        path.write_text("<project><modelVersion>4.0.0</modelVersion><modules>" + children
                        + "</modules></project>\n")

    def write_class(self, module, binary_name):
        path = self.root / module / "target/classes" / (binary_name + ".class")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"class scanner fixture; javap is not invoked")
        return path

    def scan(self):
        listed = b"\0".join(name.encode() for name in self.poms) + b"\0"
        with patch.object(parity.subprocess, "check_output", return_value=listed):
            return parity.class_files(self.root)

    def expected_live(self):
        return {"ddd4j-core|example/Core": self.core,
                "ddd4j-samples/ddd4j-sample-quarkus|example/Sample": self.sample}

    def test_ignored_archive_and_removed_pom_outputs_are_absent(self):
        archive = ".superpowers/sdd/source-builds/upstream"
        self.write_pom(archive + "/pom.xml")
        self.write_class(archive, "foreign/Provider")
        self.write_class("removed-module", "example/Removed")
        self.poms.append("removed-module/pom.xml")  # Git still lists a deleted tracked POM.
        self.write_pom("pom.xml", ["ddd4j-core", "ddd4j-samples/ddd4j-sample-quarkus", "removed-module"])
        self.write_pom("unlisted-copy/pom.xml")
        self.write_class("unlisted-copy", "foreign/Copy")

        self.assertEqual(self.expected_live(), self.scan())

    def test_current_root_pom_output_is_scanned_without_module_prefix_error(self):
        root_class = self.write_class(".", "example/Root")
        expected = dict(self.expected_live(), **{".|example/Root": root_class})

        self.assertEqual(expected, self.scan())

    def test_metadata_and_output_subtrees_are_not_live_modules_even_if_listed(self):
        for module in (".superpowers/copy", ".codegraph/copy", ".git/copy",
                       "ddd4j-core/target/copy", "output/copy"):
            self.write_pom(module + "/pom.xml")
            self.poms.append(module + "/pom.xml")
            self.write_class(module, "foreign/Archived")
        self.write_pom("pom.xml", ["ddd4j-core", "ddd4j-samples/ddd4j-sample-quarkus",
                                  ".superpowers/copy", ".codegraph/copy", ".git/copy",
                                  "ddd4j-core/target/copy", "output/copy"])

        self.assertEqual(self.expected_live(), self.scan())

    def test_generated_filter_is_preserved_but_named_member_classes_remain(self):
        nested = self.write_class("ddd4j-core", "example/Core$Nested")
        for binary_name in ("example/Core$1", "example/Core$Definition", "example/Core$Definition$Exec",
                            "example/Core$Reference", "example/Core$Exec", "example/Core$InitializeInterceptor"):
            self.write_class("ddd4j-core", binary_name)
        expected = dict(self.expected_live(), **{"ddd4j-core|example/Core$Nested": nested})

        self.assertEqual(expected, self.scan())

    def test_nul_git_paths_keep_whitespace_and_untracked_live_modules(self):
        module = "new module\nwith newline"
        self.write_pom(module + "/pom.xml")
        self.poms.append(module + "/pom.xml")
        self.write_pom("pom.xml", ["ddd4j-core", "ddd4j-samples/ddd4j-sample-quarkus", module])
        new_class = self.write_class(module, "example/New")
        expected = dict(self.expected_live(), **{module + "|example/New": new_class})

        self.assertEqual(expected, self.scan())

    def test_only_default_reactor_descendants_include_nested_and_sample_modules(self):
        self.poms.extend(["pom.xml", "nested/pom.xml", "nested/child/pom.xml",
                          "ddd4j-core/src/test/resources/convergence-consumers/client/pom.xml"])
        self.write_pom("nested/child/pom.xml")
        self.write_pom("ddd4j-core/src/test/resources/convergence-consumers/client/pom.xml")
        nested = self.write_class("nested/child", "example/Nested")
        self.write_class("ddd4j-core/src/test/resources/convergence-consumers/client", "example/Consumer")
        for container, item in (("modules", "module"), ("subprojects", "subproject")):
            with self.subTest(container=container):
                (self.root / "pom.xml").write_text(
                    f"<project><{container}><{item}>ddd4j-core</{item}>"
                    f"<{item}>ddd4j-samples/ddd4j-sample-quarkus</{item}>"
                    f"<{item}>nested</{item}></{container}></project>")
                (self.root / "nested/pom.xml").write_text(
                    f"<project><{container}><{item}>child</{item}></{container}></project>")
                expected = dict(self.expected_live(), **{"nested/child|example/Nested": nested})
                self.assertEqual(expected, self.scan())


if __name__ == "__main__":
    unittest.main()
