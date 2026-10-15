"""Release candidate API baselines must belong to the checkout's own line."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify-api-compatibility.sh")


class VerifyApiBaselineTest(unittest.TestCase):
    def test_cross_line_baseline_fails_before_maven(self):
        for line, wrong_line in (
            ("1.0.x", "2.0.x"),
            ("2.0.x", "3.0.x"),
            ("3.0.x", "1.0.x"),
            ("3.0.x", "3.1.x"),
        ):
            with self.subTest(line=line), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                (root / "scripts").mkdir()
                shutil.copyfile(SCRIPT, root / "scripts" / SCRIPT.name)
                (root / "pom.xml").write_text(
                    "<project><properties><revision>"
                    + line
                    + ".20260630-SNAPSHOT</revision></properties></project>",
                    encoding="utf-8",
                )
                marker = root / "maven-called"
                (root / "mvnw").write_text(
                    "#!/bin/sh\ntouch " + str(marker) + "\nexit 0\n", encoding="utf-8"
                )
                (root / "mvnw").chmod(0o755)
                result = subprocess.run(
                    ["bash", str(root / "scripts" / SCRIPT.name)],
                    env={"DDD4J_API_BASELINE_VERSION": wrong_line + ".20260630"},
                    capture_output=True,
                    text=True,
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(marker.exists(), result.stderr)

    def test_matching_line_reaches_maven(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "scripts").mkdir()
            shutil.copyfile(SCRIPT, root / "scripts" / SCRIPT.name)
            (root / "pom.xml").write_text(
                "<project><properties><revision>1.0.x.20260630-SNAPSHOT</revision></properties></project>",
                encoding="utf-8",
            )
            marker = root / "maven-called"
            (root / "mvnw").write_text(
                "#!/bin/sh\ntouch " + str(marker) + "\nexit 0\n", encoding="utf-8"
            )
            (root / "mvnw").chmod(0o755)
            subprocess.run(
                ["bash", str(root / "scripts" / SCRIPT.name)],
                env={"DDD4J_API_BASELINE_VERSION": "1.0.x.20260630"},
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertTrue(marker.exists())


if __name__ == "__main__":
    unittest.main()
