"""Platform dependency effective-version contract tests."""

import tempfile
import unittest
from pathlib import Path

from verify_platform_version_contract import verify, verify_source_authority


def effective_pom(dependencies):
    rows = "".join(
        f"<dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId>"
        f"<version>{version}</version></dependency>"
        for group, artifact, version in dependencies)
    return ("<project xmlns=\"http://maven.apache.org/POM/4.1.0\">"
            "<dependencyManagement><dependencies>" + rows +
            "</dependencies></dependencyManagement></project>")


class PlatformVersionContractTest(unittest.TestCase):

    def test_rejects_ecosystem_starters_in_platform_source_bom(self):
        path = self.write(effective_pom([
            ("com.baomidou", "mybatis-plus-spring-boot4-starter", "3.5.17"),
            ("io.github.resilience4j", "resilience4j-spring-boot2", "2.4.0"),
        ]))

        errors = verify_source_authority(path)

        self.assertTrue(any("mybatis-plus-spring-boot4-starter" in error for error in errors))
        self.assertTrue(any("resilience4j-spring-boot2" in error for error in errors))

    def write(self, content):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "effective.xml"
        path.write_text(content, encoding="utf-8")
        return path

    def test_reports_missing_and_mismatched_versions(self):
        path = self.write(effective_pom([
            ("org.springframework.security", "spring-security-core", "7.0.6"),
            ("org.testcontainers", "testcontainers-localstack", "2.0.3"),
        ]))
        expected = {
            ("org.springframework.security", "spring-security-core"): "7.1.0",
            ("org.testcontainers", "testcontainers-localstack"): "2.0.5",
            ("io.github.easy4j", "zxing-extension"): "3.0.x.20260630-SNAPSHOT",
        }

        errors = verify(path, expected, set())

        self.assertIn("org.springframework.security:spring-security-core expected 7.1.0 but was 7.0.6", errors)
        self.assertIn("org.testcontainers:testcontainers-localstack expected 2.0.5 but was 2.0.3", errors)
        self.assertIn("io.github.easy4j:zxing-extension is missing", errors)

    def test_rejects_forbidden_legacy_coordinates(self):
        path = self.write(effective_pom([
            ("de.schlichtherle.truelicense", "truelicense-core", "1.33"),
        ]))

        errors = verify(path, {}, {
            ("de.schlichtherle.truelicense", "truelicense-core"),
        })

        self.assertIn("forbidden coordinate de.schlichtherle.truelicense:truelicense-core is managed", errors)

    def test_accepts_expected_platform_versions(self):
        expected = {
            ("org.springframework.security", "spring-security-core"): "7.1.0",
            ("org.testcontainers", "testcontainers-localstack"): "2.0.5",
            ("io.github.easy4j", "zxing-extension"): "3.0.x.20260630-SNAPSHOT",
        }
        path = self.write(effective_pom([
            (group, artifact, version) for (group, artifact), version in expected.items()
        ]))

        self.assertEqual(verify(path, expected, set()), [])


if __name__ == "__main__":
    unittest.main()
