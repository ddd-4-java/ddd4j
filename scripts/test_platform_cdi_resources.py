"""JDK 8-only CDI descriptor mappings require exact three-line evidence."""

import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify-three-line-structure-api-parity.py")
SPEC = importlib.util.spec_from_file_location("three_line_parity", SCRIPT)
PARITY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PARITY)

DESCRIPTOR = "sample/src/main/resources/META-INF/beans.xml"
EVIDENCE = "sample/src/test/java/example/ResourceScopeContractTest.java"
XML = ('<?xml version="1.0" encoding="UTF-8"?>\n'
       '<beans xmlns="http://xmlns.jcp.org/xml/ns/javaee" '
       'bean-discovery-mode="annotated" version="2.0"/>\n')
TEST = ('@QuarkusTest\n'
        'class ResourceScopeContractTest {\n'
        '  void scope() { Arc.container().instance(Resource.class); '
        '"ApplicationScoped"; }\n'
        '}\n')


class PlatformCdiResourcesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        base = Path(self.temporary.name)
        self.roots = [base / str(index) for index in range(3)]
        descriptor = self.roots[0] / DESCRIPTOR
        descriptor.parent.mkdir(parents=True)
        descriptor.write_text(XML, encoding="utf-8")
        for root in self.roots:
            evidence = root / EVIDENCE
            evidence.parent.mkdir(parents=True)
            evidence.write_text(TEST, encoding="utf-8")
        self.mapping = {
            "path": DESCRIPTOR,
            "sha256": hashlib.sha256(XML.encode()).hexdigest(),
            "evidence": EVIDENCE,
            "testSha256": hashlib.sha256(TEST.encode()).hexdigest(),
            "reason": "JDK 8 CDI descriptor, with the same Arc scope contract in every line",
        }

    def test_accepts_exact_descriptor_and_three_line_scope_evidence(self):
        result = PARITY.validate_platform_cdi_resources_only_on_jdk8(
            [self.mapping], self.roots)
        self.assertIn(DESCRIPTOR, result)

    def test_rejects_changed_descriptor(self):
        (self.roots[0] / DESCRIPTOR).write_text(XML.replace("annotated", "all"))
        with self.assertRaises(ValueError):
            PARITY.validate_platform_cdi_resources_only_on_jdk8(
                [self.mapping], self.roots)

    def test_rejects_descriptor_on_higher_line(self):
        descriptor = self.roots[1] / DESCRIPTOR
        descriptor.parent.mkdir(parents=True)
        descriptor.write_text(XML)
        with self.assertRaises(ValueError):
            PARITY.validate_platform_cdi_resources_only_on_jdk8(
                [self.mapping], self.roots)

    def test_rejects_changed_scope_evidence(self):
        (self.roots[2] / EVIDENCE).write_text(TEST.replace("ApplicationScoped", "Singleton"))
        with self.assertRaises(ValueError):
            PARITY.validate_platform_cdi_resources_only_on_jdk8(
                [self.mapping], self.roots)


if __name__ == "__main__":
    unittest.main()
