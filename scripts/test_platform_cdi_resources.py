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
AVAILABLE_TEST = '@QuarkusTest Arc.container().instance(Filter.class).isAvailable()\n'
HTTP_EVIDENCE = 'sample/src/test/java/example/FilterHttpContractTest.java'
HTTP_TEST = '@QuarkusTest class FilterHttpContractTest extends AbstractWebContractTest {}\n'


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

    def test_panache_descriptor_accepts_only_the_exact_cdi_schema_transition(self):
        name = "ddd4j-data/ddd4j-data-event-store-panache/src/main/resources/META-INF/beans.xml"
        modern = ('<beans xmlns="https://jakarta.ee/xml/ns/jakartaee" '
                  'bean-discovery-mode="annotated" version="4.0"/>')
        for root, content in zip(self.roots, (XML, modern, modern)):
            descriptor = root / name
            descriptor.parent.mkdir(parents=True)
            descriptor.write_text(content, encoding="utf-8")

        self.assertTrue(PARITY.cdi_descriptor_platform_match(name, self.roots))
        for modified in (modern.replace("annotated", "all"),
                         modern.replace("4.0", "3.0"),
                         modern.replace("/>", "><alternatives/></beans>"),
                         modern.replace("/>", ' extra="true"/>')):
            with self.subTest(modified=modified):
                (self.roots[2] / name).write_text(modified, encoding="utf-8")
                self.assertFalse(PARITY.cdi_descriptor_platform_match(name, self.roots))

    def test_helidon_auth_descriptors_only_adapt_the_cdi_schema(self):
        legacy = XML.replace('bean-discovery-mode',
                             'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
                             'xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee '
                             'http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd" bean-discovery-mode')
        modern = ('<beans xmlns="https://jakarta.ee/xml/ns/jakartaee" '
                  'bean-discovery-mode="annotated" version="3.0"/>')
        for auth in ("satoken", "shiro"):
            name = f"ddd4j-samples/ddd4j-sample-helidon-{auth}/src/main/resources/META-INF/beans.xml"
            with self.subTest(auth=auth):
                for root, content in zip(self.roots, (legacy, modern, modern)):
                    descriptor = root / name
                    descriptor.parent.mkdir(parents=True)
                    descriptor.write_text(content, encoding="utf-8")
                self.assertTrue(PARITY.cdi_descriptor_platform_match(name, self.roots))
                (self.roots[0] / name).write_text(XML, encoding="utf-8")
                self.assertFalse(PARITY.cdi_descriptor_platform_match(name, self.roots))
                (self.roots[0] / name).write_text(legacy, encoding="utf-8")
                (self.roots[2] / name).write_text(modern.replace("annotated", "all"), encoding="utf-8")
                self.assertFalse(PARITY.cdi_descriptor_platform_match(name, self.roots))

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

    def test_accepts_available_bean_with_three_line_http_contract(self):
        for root in self.roots:
            (root / EVIDENCE).write_text(AVAILABLE_TEST)
            http = root / HTTP_EVIDENCE
            http.parent.mkdir(parents=True, exist_ok=True)
            http.write_text(HTTP_TEST)
        self.mapping.update({
            'assertion': 'available',
            'testSha256': hashlib.sha256(AVAILABLE_TEST.encode()).hexdigest(),
            'httpEvidence': HTTP_EVIDENCE,
            'httpTestSha256': [hashlib.sha256(HTTP_TEST.encode()).hexdigest()] * 3,
        })
        self.assertIn(DESCRIPTOR,
                      PARITY.validate_platform_cdi_resources_only_on_jdk8(
                          [self.mapping], self.roots))

    def test_rejects_changed_http_contract_for_available_bean(self):
        self.test_accepts_available_bean_with_three_line_http_contract()
        (self.roots[2] / HTTP_EVIDENCE).write_text(HTTP_TEST + '// changed\n')
        with self.assertRaises(ValueError):
            PARITY.validate_platform_cdi_resources_only_on_jdk8(
                [self.mapping], self.roots)


if __name__ == "__main__":
    unittest.main()
