#!/usr/bin/env python3
"""Regression tests for the weekly dependency version checker."""

import json
import io
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

from check_weekly_dependency_versions import (
    check,
    latest_stable_version,
    managed_dependencies,
    matching_rule,
)


class WeeklyDependencyVersionsTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.pom = Path(self.temp.name) / "pom.xml"
        self.pom.write_text("""<project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <properties><lib.version>1.0</lib.version></properties>
          <dependencyManagement><dependencies>
            <dependency><groupId>example</groupId><artifactId>lib</artifactId>
              <version>${lib.version}</version></dependency>
            <dependency><groupId>com.lmax</groupId><artifactId>disruptor</artifactId>
              <version>3.4.4</version></dependency>
            <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId>
              <version>5.3.39</version></dependency>
          </dependencies></dependencyManagement>
        </project>""")
        self.policy = {
            "fixed": [{
                "pattern": "com.lmax:disruptor",
                "versions": {"1.0.x": "3.4.4"},
                "reason": "JDK 8"
            }],
            "excluded": [{
                "pattern": "org.springframework*:*",
                "reason": "platform managed"
            }]
        }

    def test_resolves_property_and_filters_platform_rules(self):
        rows = managed_dependencies(self.pom)
        self.assertEqual(rows[0]["current"], "1.0")
        self.assertEqual(matching_rule(self.policy["fixed"], "com.lmax:disruptor", "3.4.4")["reason"],
                         "JDK 8")
        self.assertIsNone(matching_rule(self.policy["excluded"], "example:lib", "1.0"))

    @patch("check_weekly_dependency_versions.latest_stable_version", return_value="2.0")
    def test_reports_unpinned_outdated_version(self, lookup):
        report = check(self.pom, self.policy, "1.0.x", "https://repo.maven.apache.org/maven2", 1, 1)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(report["counts"], {"outdated": 1, "fixed": 1, "excluded": 1})
        lookup.assert_called_once()

    @patch("check_weekly_dependency_versions.latest_stable_version", return_value="1.0")
    def test_passes_current_version(self, _lookup):
        report = check(self.pom, self.policy, "1.0.x", "https://repo.maven.apache.org/maven2", 1, 1)
        self.assertEqual(report["status"], "PASS")

    @patch("check_weekly_dependency_versions.latest_stable_version", return_value="1.0")
    def test_fixed_version_drift_fails(self, _lookup):
        self.policy["fixed"][0]["versions"]["1.0.x"] = "3.4.5"
        report = check(self.pom, self.policy, "1.0.x", "https://repo.maven.apache.org/maven2", 1, 1)
        self.assertEqual(report["counts"]["fixed-drift"], 1)
        self.assertEqual(report["status"], "FAIL")

    @patch("check_weekly_dependency_versions.latest_stable_version", side_effect=ValueError("missing"))
    def test_lookup_failure_does_not_pass(self, _lookup):
        report = check(self.pom, self.policy, "1.0.x", "https://repo.maven.apache.org/maven2", 1, 1)
        self.assertEqual(report["counts"]["lookup-error"], 1)
        self.assertEqual(report["status"], "FAIL")

    @patch("check_weekly_dependency_versions.time.sleep")
    @patch("check_weekly_dependency_versions.urllib.request.urlopen")
    def test_preview_only_artifact_retries_network_and_uses_latest_published(self, open_url, sleep):
        metadata = b"<metadata><versioning><versions><version>1.0.0-beta.1</version><version>1.0.0-beta.2</version></versions></versioning></metadata>"
        open_url.side_effect = [urllib.error.URLError("temporary TLS failure"), io.BytesIO(metadata)]
        self.assertEqual(latest_stable_version("example:preview", "https://repo.maven.apache.org/maven2", 1),
                         "1.0.0-beta.2")
        self.assertEqual(open_url.call_count, 2)
        sleep.assert_called_once_with(1)

    def test_real_bom_manages_latest_ofdrw_and_has_policy(self):
        root = Path(__file__).resolve().parent.parent
        rows = managed_dependencies(root / "ddd4j-dependencies/pom.xml")
        self.assertEqual(
            next(row["current"] for row in rows if row["coordinate"] == "org.ofdrw:ofdrw-core"),
            "2.4.0",
        )
        policy = json.loads((root / "config/dependencies/weekly-update-policy.json").read_text())
        self.assertIsNone(matching_rule(policy["fixed"] + policy["excluded"],
                                        "org.ofdrw:ofdrw-core", "2.4.0"))


if __name__ == "__main__":
    unittest.main()
