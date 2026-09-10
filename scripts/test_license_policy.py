"""第三方许可证策略引擎测试。"""

import tempfile
import unittest
from pathlib import Path

from license_policy import (
    InventoryEntry,
    LicenseSelection,
    load_selections,
    parse_inventory,
    verify_policy,
)


class LicensePolicyTest(unittest.TestCase):

    def write(self, name, content):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / name
        path.write_text(content, encoding="utf-8")
        return path

    def test_parses_complete_coordinate_and_declared_licenses(self):
        inventory = self.write("THIRD-PARTY.txt", """\
Lists of 1 third-party dependencies.
     (EPL-2.0) (LGPL-2.1-only) Logback (ch.qos.logback:logback-core:1.5.36 - https://logback.qos.ch)
""")

        entries = parse_inventory(inventory)

        self.assertEqual(entries, [InventoryEntry(
            coordinate="ch.qos.logback:logback-core:1.5.36",
            declared_licenses=("EPL-2.0", "LGPL-2.1-only"),
            name="Logback",
        )])

    def test_parses_nested_parentheses_without_losing_next_license(self):
        inventory = self.write("THIRD-PARTY.txt", """\
Lists of 1 third-party dependencies.
     (GNU Library or Lesser General Public License (LGPL) V2.1) (The Apache Software License, Version 2.0) JSQLParser library (com.github.jsqlparser:jsqlparser:5.3 - https://github.com/JSQLParser/JSqlParser)
""")

        entry = parse_inventory(inventory)[0]

        self.assertEqual(entry.declared_licenses, (
            "GNU Library or Lesser General Public License (LGPL) V2.1",
            "The Apache Software License, Version 2.0",
        ))

    def test_accepts_plain_permissive_license_without_selection(self):
        entry = InventoryEntry("g:a:1", ("Apache License, Version 2.0",), "A")
        self.assertEqual([], verify_policy([entry], {}))

    def test_requires_evidenced_choice_for_dual_license(self):
        entry = InventoryEntry("g:a:1", ("EPL-2.0", "LGPL-2.1-only"), "A")
        missing = verify_policy([entry], {})
        selection = LicenseSelection(
            coordinate="g:a:1",
            declared_expression="EPL-2.0 OR LGPL-2.1-only",
            selected_spdx="EPL-2.0",
            evidence_url="https://github.com/example/project/blob/v1/LICENSE",
            evidence_type="LICENSE",
            justification="Select the upstream EPL-2.0 option.",
        )

        self.assertTrue(any("requires an evidenced selection" in error for error in missing))
        self.assertEqual([], verify_policy([entry], {entry.coordinate: selection}))

    def test_accepts_mulan_and_unmodified_weak_copyleft(self):
        entries = [
            InventoryEntry("g:cddl:1", ("CDDL-1.1",), "C"),
            InventoryEntry("g:mpl:2", ("MPL 2.0",), "P"),
            InventoryEntry("g:mulan:2", ("Mulan Permissive Software License，Version 2",), "M"),
        ]

        self.assertEqual([], verify_policy(entries, {}))

    def test_blocks_strong_copyleft_unknown_and_wtfpl(self):
        entries = [
            InventoryEntry("g:gpl:1", ("GPL-3.0-only",), "G"),
            InventoryEntry("g:unknown:1", ("Unknown License",), "U"),
            InventoryEntry("g:wtfpl:1", ("WTFPL",), "W"),
        ]
        errors = verify_policy(entries, {})
        self.assertEqual(len(errors), 3)

    def test_rejects_unversioned_coordinate_drifting_url_and_stale_selection(self):
        entries = [InventoryEntry("g:a:1", ("Apache-2.0",), "A")]
        selections = {
            "g:b": LicenseSelection(
                "g:b", "Apache-2.0", "Apache-2.0",
                "https://github.com/example/project/blob/main/LICENSE", "LICENSE", "stale",
            )
        }

        errors = verify_policy(entries, selections)

        self.assertTrue(any("complete version" in error for error in errors))
        self.assertTrue(any("stale selection" in error for error in errors))

    def test_loads_six_column_tsv_and_rejects_legacy_schema(self):
        current = self.write("current.tsv", """\
coordinate\tdeclared_expression\tselected_spdx\tevidence_url\tevidence_type\tjustification
g:a:1\tApache-2.0 OR MIT\tApache-2.0\thttps://github.com/example/p/blob/v1/LICENSE\tLICENSE\tselected Apache
""")
        legacy = self.write("legacy.tsv", "g:a:1\tApache.*\n")

        self.assertIn("g:a:1", load_selections(current))
        with self.assertRaisesRegex(ValueError, "six-column"):
            load_selections(legacy)

    def test_allows_exact_build_tool_exclusion_when_absent_from_sbom(self):
        entry = InventoryEntry("g:tool:1", ("GPL-2.0-only",), "Tool")
        self.assertEqual([], verify_policy(
            [entry], {}, sbom_coordinates=set(),
            build_tool_exclusions={"g:tool:1": "build plugin dependency"},
        ))


if __name__ == "__main__":
    unittest.main()
