"""Inherited CQRS contract mappings require exact adapter and base-test evidence."""

import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify-three-line-structure-api-parity.py")
SPEC = importlib.util.spec_from_file_location("three_line_parity", SCRIPT)
PARITY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PARITY)

ADAPTER = "runtime/src/test/java/example/RuntimeContractTest.java"
PRODUCTION = "core/src/main/java/example/DefaultCommandBus.java"
CONTRACT = "testkit/src/main/java/example/AbstractCqrsRuntimeContractTest.java"
ADAPTER_SOURCE = ("class RuntimeContractTest extends AbstractCqrsRuntimeContractTest {\n"
                  "  protected CqrsRuntimeContract createContract() { return null; }\n"
                  "}\n")
CONTRACT_SOURCE = ("class AbstractCqrsRuntimeContractTest {\n"
                   "  @Test\n"
                   "  void routesCommand() {}\n"
                   "}\n")
PRODUCTION_SOURCE = "class DefaultCommandBus {}\n"


class PlatformInheritedContractSyntaxTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        base = Path(temporary.name)
        self.roots = [base / str(index) for index in range(3)]
        for root in self.roots:
            for path, source in ((ADAPTER, ADAPTER_SOURCE),
                                 (PRODUCTION, PRODUCTION_SOURCE),
                                 (CONTRACT, CONTRACT_SOURCE)):
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(source)
        self.mapping = {
            "path": ADAPTER,
            "productionPath": PRODUCTION,
            "sourceSha256": [self.digest(ADAPTER, root) for root in self.roots],
            "productionSha256": [self.digest(PRODUCTION, root) for root in self.roots],
            "contractPath": CONTRACT,
            "contractSha256": self.digest(CONTRACT, self.roots[0]),
            "testMethods": ["routesCommand"],
            "reason": "same inherited contract and production logic",
        }

    @staticmethod
    def digest(path, root):
        return hashlib.sha256((root / path).read_bytes()).hexdigest()

    def test_accepts_exact_inherited_contract(self):
        result = PARITY.validate_platform_test_syntax_mappings(
            [self.mapping], self.roots)
        self.assertIn(ADAPTER, result)

    def test_rejects_changed_contract(self):
        (self.roots[2] / CONTRACT).write_text(CONTRACT_SOURCE + "// changed\n")
        with self.assertRaises(ValueError):
            PARITY.validate_platform_test_syntax_mappings(
                [self.mapping], self.roots)

    def test_rejects_adapter_without_inheritance(self):
        source = ADAPTER_SOURCE.replace("extends AbstractCqrsRuntimeContractTest", "")
        (self.roots[2] / ADAPTER).write_text(source)
        self.mapping["sourceSha256"][2] = self.digest(ADAPTER, self.roots[2])
        with self.assertRaises(ValueError):
            PARITY.validate_platform_test_syntax_mappings(
                [self.mapping], self.roots)


if __name__ == "__main__":
    unittest.main()
