"""Maven 4 effective-model regression contract tests."""

import tempfile
import unittest
from pathlib import Path

from verify_maven4_model_contract import verify, verify_owned_source, verify_quarkus_config


class Maven4ModelContractTest(unittest.TestCase):

    def test_repository_has_no_owned_warning_sources(self):
        root = Path(__file__).resolve().parents[1]
        self.assertEqual([], verify_owned_source(root))

    def write(self, name, content):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / name
        path.write_text(content, encoding="utf-8")
        return path

    def test_rejects_ddd4j_effective_model_warning(self):
        log = self.write(
            "maven.log",
            "[WARNING] 1 problem was encountered while building the effective model "
            "for 'io.ddd4j:ddd4j-core:jar:3.0.x.20260630-SNAPSHOT'\n",
        )
        pom = self.write("pom.xml", "<project/>")

        self.assertIn("ddd4j effective-model warning", verify(log, pom))

    def test_rejects_imported_bom_in_platform_source(self):
        log = self.write("maven.log", "[INFO] BUILD SUCCESS\n")
        pom = self.write(
            "pom.xml",
            """<project><dependencyManagement><dependencies><dependency>
<type>pom</type><scope>import</scope>
</dependency></dependencies></dependencyManagement></project>""",
        )

        self.assertIn("platform dependencyManagement still imports a BOM", verify(log, pom))

    def test_allows_third_party_warnings_when_ddd4j_model_is_clean(self):
        log = self.write(
            "maven.log",
            "[WARNING] 13 problems were encountered while building the effective model "
            "for 'io.smallrye.config:smallrye-config:jar:3.17.2'\n"
            "[INFO] BUILD SUCCESS\n",
        )
        pom = self.write("pom.xml", "<project><dependencyManagement/></project>")

        self.assertEqual([], verify(log, pom))

    def test_rejects_unknown_or_changed_third_party_model_warning(self):
        pom = self.write("pom.xml", "<project><dependencyManagement/></project>")
        unknown = self.write(
            "unknown.log",
            "[WARNING] 2 problems were encountered while building the effective model "
            "for 'example:unknown:jar:1.0'\n[INFO] BUILD SUCCESS\n",
        )
        changed = self.write(
            "changed.log",
            "[WARNING] 12 problems were encountered while building the effective model "
            "for 'io.smallrye.config:smallrye-config:jar:3.17.2'\n"
            "[INFO] BUILD SUCCESS\n",
        )

        self.assertTrue(any("unexpected upstream" in error for error in verify(unknown, pom)))
        self.assertTrue(any("count changed" in error for error in verify(changed, pom)))

    def test_requires_explicit_quarkus_native_builder_configuration(self):
        missing = self.write("maven.config", "-DskipTests=false\n")
        configured = self.write(
            "maven.config",
            "-Dquarkus.native.builder-image=mandrel\n",
        )

        self.assertEqual(
            ["Quarkus native builder image is not configured"],
            verify_quarkus_config(missing),
        )
        self.assertEqual([], verify_quarkus_config(configured))


if __name__ == "__main__":
    unittest.main()
