#!/usr/bin/env python3
"""Fault-injection tests for the required JPA MySQL container report."""

import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-jpa-mysql-test-report.py")
REPORT = Path("ddd4j-data/ddd4j-data-event-store-jpa/target/surefire-reports/") / (
    "TEST-io.ddd4j.data.event.store.jpa.JpaEventStoreMysqlIT.xml"
)


class JpaMysqlReportTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def report(self, *, tests=3, skipped=0, errors=0, failures=0):
        path = self.root / REPORT
        path.parent.mkdir(parents=True, exist_ok=True)
        suite = ET.Element("testsuite", {
            "tests": str(tests),
            "skipped": str(skipped),
            "errors": str(errors),
            "failures": str(failures),
        })
        ET.ElementTree(suite).write(path, encoding="utf-8", xml_declaration=True)

    def check(self):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root)],
            capture_output=True, text=True, check=False,
        )

    def test_three_executed_cases_pass(self):
        self.report()
        self.assertEqual(0, self.check().returncode)

    def test_missing_report_fails(self):
        self.assertNotEqual(0, self.check().returncode)

    def test_zero_executed_cases_fails(self):
        self.report(tests=0)
        self.assertNotEqual(0, self.check().returncode)

    def test_incomplete_case_count_fails(self):
        self.report(tests=2)
        self.assertNotEqual(0, self.check().returncode)

    def test_skipped_case_fails(self):
        self.report(skipped=1)
        self.assertNotEqual(0, self.check().returncode)

    def test_failed_case_fails(self):
        self.report(failures=1)
        self.assertNotEqual(0, self.check().returncode)


if __name__ == "__main__":
    unittest.main()
