"""API 归一化只能消除允许的命名空间变化，不能抹去参数类型身份。"""

import importlib.util
import pathlib
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "structure_api_parity",
    pathlib.Path(__file__).with_name("verify-three-line-structure-api-parity.py"),
)
parity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parity)


class DescriptorNormalizationTest(unittest.TestCase):
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
            parity.normalize_descriptor("(Lcom/fasterxml/jackson/databind/JsonNode;)V"),
            parity.normalize_descriptor("(Ltools/jackson/databind/JsonNode;)V"),
        )

    def test_different_jackson_parameter_types_remain_different(self):
        self.assertNotEqual(
            parity.normalize_descriptor("(Lcom/fasterxml/jackson/databind/ObjectMapper;)V"),
            parity.normalize_descriptor("(Ltools/jackson/databind/JsonNode;)V"),
        )

    def test_parameter_order_remains_observable(self):
        self.assertNotEqual(
            parity.normalize_descriptor("(Ltools/jackson/databind/JsonNode;Ltools/jackson/core/JsonParser;)V"),
            parity.normalize_descriptor("(Ltools/jackson/core/JsonParser;Ltools/jackson/databind/JsonNode;)V"),
        )

    def test_array_and_return_type_preserve_identity(self):
        self.assertEqual(
            "([LJACKSON/databind/JsonNode;)LJACKSON/core/JsonParser;",
            parity.normalize_descriptor("([Lcom/fasterxml/jackson/databind/JsonNode;)Ltools/jackson/core/JsonParser;"),
        )


if __name__ == "__main__":
    unittest.main()
