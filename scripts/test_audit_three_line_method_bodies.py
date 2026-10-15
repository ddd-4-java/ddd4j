import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("audit-three-line-method-bodies.py")
SOURCE = "ddd4j-core/src/main/java/io/ddd4j/core/Sample.java"


class MethodBodyAuditTest(unittest.TestCase):
    def test_reads_current_source_without_codegraph_and_detects_changed_body(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / name for name in ("jdk8", "jdk17", "jdk21")]
            for root, result in zip(roots, ("1", "2", "/* comment */ 1")):
                source = root / SOURCE
                source.parent.mkdir(parents=True)
                source.write_text(
                    "package io.ddd4j.core;\n"
                    "public class Sample { public int value() { return " + result + "; } }\n",
                    encoding="utf-8",
                )
                subprocess.run(["git", "init", "-q", str(root)], check=True)
                subprocess.run(["git", "-C", str(root), "add", SOURCE], check=True)

            extra = roots[2] / "ddd4j-core/src/main/java/io/ddd4j/core/Extra.java"
            extra.write_text("package io.ddd4j.core; public class Extra { public int value() { return 3; } }\n",
                             encoding="utf-8")

            output = base / "report"
            subprocess.run(
                [sys.executable, str(SCRIPT), "--roots", *map(str, roots), "--output", str(output)],
                check=True, capture_output=True, text=True,
            )
            report = json.loads((output / "method-body-report.json").read_text(encoding="utf-8"))
            self.assertTrue(report["inputStable"])
            self.assertEqual(1, len(report["pairs"]["1-2"]["body_mismatches"]))
            self.assertEqual(0, len(report["pairs"]["1-3"]["body_mismatches"]))
            self.assertEqual(1, report["pairs"]["1-2"]["common_keys"])
            self.assertEqual(1, report["pairs"]["1-3"]["only_right"])
            self.assertTrue(any("Extra#value()" in key
                                for key in report["pairs"]["1-3"]["only_right_keys"]))


if __name__ == "__main__":
    unittest.main()
