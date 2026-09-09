"""Maven 4 BOM import 冲突精确治理契约测试。"""

import tempfile
import unittest
from contextlib import redirect_stderr
from io import StringIO
from pathlib import Path

from verify_bom_import_conflicts import build_parser, parse_conflicts, verify


class BomImportConflictTest(unittest.TestCase):

    def test_cli_requires_effective_pom_evidence(self):
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            build_parser().parse_args(["maven.log", "--allowlist", "allowlist.tsv"])

    def write(self, name, content):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / name
        path.write_text(content, encoding="utf-8")
        return path

    def warning(self, ignored="2.13.5", current="2.21.2"):
        return (
            "[WARNING] Ignored POM import for: "
            f"com.fasterxml.jackson.core:jackson-core:jar:{ignored}@compile "
            "as already imported "
            f"com.fasterxml.jackson.core:jackson-core:jar:{current}@compile[optional]. "
            "Add the conflicting managed dependency directly to the dependencyManagement "
            "section of the POM.\n"
        )

    def test_parses_and_deduplicates_exact_conflict_tuple(self):
        log = self.write("maven.log", self.warning() + self.warning())

        conflicts = parse_conflicts(log)

        self.assertEqual(len(conflicts), 1)
        conflict = next(iter(conflicts))
        self.assertEqual(conflict.group_id, "com.fasterxml.jackson.core")
        self.assertEqual(conflict.artifact_id, "jackson-core")
        self.assertEqual(conflict.ignored_version, "2.13.5")
        self.assertEqual(conflict.current_version, "2.21.2")

    def test_unlisted_conflict_fails(self):
        log = self.write("maven.log", self.warning())
        allowlist = self.write(
            "allowlist.tsv",
            "group_id\tartifact_id\tcurrent_version\tignored_version\tfinal_version\tauthority\treason\n",
        )

        errors = verify(log, allowlist)

        self.assertEqual(len(errors), 1)
        self.assertIn("unlisted BOM conflict", errors[0])

    def test_exact_allowlist_passes_and_version_drift_fails(self):
        allowlist = self.write(
            "allowlist.tsv",
            """\
group_id\tartifact_id\tcurrent_version\tignored_version\tfinal_version\tauthority\treason
com.fasterxml.jackson.core\tjackson-core\t2.21.2\t2.13.5\t2.21.2\tddd4j-dependencies\tplatform baseline
""",
        )

        self.assertEqual(verify(self.write("matching.log", self.warning()), allowlist), [])
        errors = verify(self.write("drift.log", self.warning(current="2.22.0")), allowlist)
        self.assertTrue(any("unlisted BOM conflict" in error for error in errors))
        self.assertTrue(any("stale allowlist entry" in error for error in errors))

    def test_rejects_wrong_final_version_or_incomplete_justification(self):
        log = self.write("maven.log", self.warning())
        effective = self.write(
            "effective.xml",
            """\
<project xmlns="http://maven.apache.org/POM/4.1.0"><dependencyManagement><dependencies>
<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-core</artifactId>
<version>2.21.2</version></dependency>
</dependencies></dependencyManagement></project>
""",
        )
        allowlist = self.write(
            "allowlist.tsv",
            """\
group_id\tartifact_id\tcurrent_version\tignored_version\tfinal_version\tauthority\treason
com.fasterxml.jackson.core\tjackson-core\t2.21.2\t2.13.5\t2.13.5\t\t
""",
        )

        errors = verify(log, allowlist, effective)

        self.assertTrue(any("final version 2.13.5 does not match effective 2.21.2" in error
                            for error in errors))
        self.assertTrue(any("authority must be ddd4j-dependencies" in error for error in errors))
        self.assertTrue(any("reason must not be empty" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
