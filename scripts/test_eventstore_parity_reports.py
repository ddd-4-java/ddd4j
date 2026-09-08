#!/usr/bin/env python3
"""验证 EventStore 三线差分脚本只接受新鲜、完整的同输入报告。"""

import importlib.util
import os
import pathlib
import tempfile
import time
import unittest
import xml.etree.ElementTree as element_tree


SCRIPT = pathlib.Path(__file__).with_name("verify-three-line-eventstore-parity.py")
SPEC = importlib.util.spec_from_file_location("eventstore_parity", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
eventstore_parity = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(eventstore_parity)

RESOURCE_LOCAL_TESTS = {
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "participatingAppendMustLeaveCommitToCaller"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest",
     "participatingOperationsMustRejectWithoutCallerTransaction"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest",
     "participatingEmptyAppendMustRejectWithoutCallerTransaction"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest",
     "participatingAppendsMustShareCallerTransactionAndCommitTogether"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest",
     "participatingFailedBatchMustMarkCallerTransactionRollbackOnly"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "participatingAppendMustPreserveCommitFlushMode"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest",
     "participatingCustomRepositoryMustControlPositionAndPersistence"),
}
EXISTING_NINE_TESTS = {
    ("io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest", "appendMustNotMutateInputAggregateVersion"),
    ("io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest", "readAllLimitMustBePositive"),
    ("io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest", "persistedTimestampMustComeFromEvent"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "appendMustNotMutateInputAggregateVersion"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "readAllLimitMustBePositive"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "persistedTimestampMustComeFromEvent"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "failedBatchMustPreserveCommittedStateAndAllowNextAppend"),
    ("io.ddd4j.data.event.store.r2dbc.R2dbcAsyncEventStoreTest", "readAllLimitMustBePositive"),
    ("io.ddd4j.data.event.store.r2dbc.R2dbcAsyncEventStoreTest", "persistedTimestampMustComeFromEvent"),
}


class EventStoreParityReportsTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary_directory.name)
        self.suffix = "fresh-run"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_reports(self, cases, *, skipped_case=None, class_suffix=None) -> None:
        for module, class_name in eventstore_parity.REPORTS:
            selected = sorted(case for case in cases if case[0] == class_name)
            rendered_suite = class_name if class_suffix is None else f"{class_name}({class_suffix})"
            suite = element_tree.Element("testsuite", {
                "name": rendered_suite,
                "tests": str(len(selected)),
                "failures": "0",
                "errors": "0",
                "skipped": "1" if skipped_case in selected else "0",
            })
            for actual_class, method_name in selected:
                rendered_class = actual_class
                if class_suffix is not None:
                    rendered_class += f"({class_suffix})"
                case = element_tree.SubElement(suite, "testcase", {
                    "classname": rendered_class,
                    "name": method_name,
                })
                if (actual_class, method_name) == skipped_case:
                    element_tree.SubElement(case, "skipped")
            report = (self.root / module / "target" / "surefire-reports"
                      / f"TEST-{class_name}-{self.suffix}.xml")
            report.parent.mkdir(parents=True, exist_ok=True)
            element_tree.ElementTree(suite).write(report, encoding="utf-8", xml_declaration=True)

    def test_accepts_nine_existing_and_resource_local_assertions_from_fresh_suffixed_reports(self) -> None:
        expected = EXISTING_NINE_TESTS | RESOURCE_LOCAL_TESTS
        self.write_reports(expected, class_suffix=self.suffix)

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, 0)

        self.assertEqual(expected, observed)
        self.assertEqual(expected, eventstore_parity.EXPECTED)
        self.assertEqual([], invalid)
        self.assertEqual(16, len(observed))

    def test_rejects_stale_reports(self) -> None:
        self.write_reports(eventstore_parity.EXPECTED, class_suffix=self.suffix)
        started_ns = time.time_ns()
        for report in self.root.rglob("TEST-*.xml"):
            os.utime(report, ns=(started_ns - 1, started_ns - 1))

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, started_ns)

        self.assertEqual(set(), observed)
        self.assertEqual(3, len([entry for entry in invalid if entry.startswith("stale:")]))

    def test_rejects_skipped_report(self) -> None:
        skipped = next(iter(sorted(eventstore_parity.EXPECTED)))
        self.write_reports(eventstore_parity.EXPECTED, skipped_case=skipped, class_suffix=self.suffix)

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, 0)

        self.assertNotIn(skipped, observed)
        self.assertEqual(1, len([entry for entry in invalid if entry.startswith("non-green:")]))

    def test_reports_missing_expected_file_for_requested_suffix(self) -> None:
        self.write_reports(eventstore_parity.EXPECTED, class_suffix=self.suffix)
        missing_module, missing_class = eventstore_parity.REPORTS[0]
        missing_report = (self.root / missing_module / "target" / "surefire-reports"
                          / f"TEST-{missing_class}-{self.suffix}.xml")
        missing_report.unlink()

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, 0)

        self.assertTrue(any(entry.startswith("missing:") for entry in invalid))
        self.assertFalse(any(case[0] == missing_class for case in observed))

    def test_does_not_accept_testcase_tagged_with_another_suffix(self) -> None:
        self.write_reports(eventstore_parity.EXPECTED, class_suffix="another-run")

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, 0)

        self.assertEqual(set(), observed)
        self.assertEqual(3, len([entry for entry in invalid if entry.startswith("wrong-suffix:")]))

    def test_rejects_testcase_without_requested_suffix_when_suite_is_correct(self) -> None:
        self.write_reports(eventstore_parity.EXPECTED, class_suffix=self.suffix)
        report = (self.root / eventstore_parity.REPORTS[0][0] / "target" / "surefire-reports"
                  / f"TEST-{eventstore_parity.REPORTS[0][1]}-{self.suffix}.xml")
        document = element_tree.parse(report)
        suite = document.getroot()
        testcase = next(suite.iter("testcase"))
        testcase.attrib["classname"] = testcase.attrib["classname"].removesuffix(f"({self.suffix})")
        document.write(report, encoding="utf-8", xml_declaration=True)

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, 0)

        self.assertEqual(f"{eventstore_parity.REPORTS[0][1]}({self.suffix})", suite.attrib["name"])
        self.assertEqual(1, len([entry for entry in invalid if entry.startswith("wrong-suffix:")]))
        self.assertFalse(any(entry.startswith("non-green:") for entry in invalid))
        self.assertNotIn((testcase.attrib["classname"], testcase.attrib["name"]), observed)

    def test_does_not_accept_report_without_requested_suffix_marker(self) -> None:
        self.write_reports(eventstore_parity.EXPECTED)

        observed, invalid = eventstore_parity.observed_tests(self.root, self.suffix, 0)

        self.assertEqual(set(), observed)
        self.assertEqual(3, len([entry for entry in invalid if entry.startswith("wrong-suffix:")]))


if __name__ == "__main__":
    unittest.main()
