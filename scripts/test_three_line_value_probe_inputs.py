"""四对象探针必须使用每条分支自己的探针与显式编译类路径。"""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify-three-line-value-parity.py")
SOURCES = (
    "ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunInfo.java",
    "ddd4j-data/ddd4j-data-mybatis/src/main/java/io/ddd4j/data/mybatis/adapter/SqlObservation.java",
    "ddd4j-mq/ddd4j-mq-redis-stream/src/main/java/io/ddd4j/mq/redisstream/RedisStreamRecord.java",
    "ddd4j-extensions/ddd4j-extension-qlexpress/src/main/java/io/ddd4j/extension/qlexpress/model/QLExpressValidationResult.java",
)


class ThreeLineValueProbeInputsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.jdks = [Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version],
                                                  text=True).strip()) for version in ("1.8", "17", "21")]
        self.roots = [self.base / name for name in ("jdk8", "jdk17", "jdk21")]
        for root in self.roots:
            root.mkdir()
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            for index, name in enumerate(SOURCES):
                self.write(root / name, "class Input" + str(index) + " {}\n")
            self.write(root / "verification/three-line-parity/ValueContractProbe.java",
                       'public class ValueContractProbe { public static void main(String[] args) { System.out.println("VALUE_OK"); } }\n')

    @staticmethod
    def write(path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def run_checker(self, extra=()):
        output = self.base / "report"
        result = subprocess.run([sys.executable, str(SCRIPT), "--roots", *map(str, self.roots),
                                 "--jdks", *map(str, self.jdks), "--output", str(output), *extra],
                                capture_output=True, text=True)
        return result, output

    def test_explicit_classpath_compiles_each_branch(self):
        annotation = self.base / "annotation/com/fasterxml/jackson/annotation/JsonProperty.java"
        self.write(annotation,
                   'package com.fasterxml.jackson.annotation; public @interface JsonProperty { String value() default ""; }\n')
        classes = self.base / "annotation-classes"
        classes.mkdir()
        subprocess.run([str(self.jdks[0] / "bin/javac"), "-d", str(classes), str(annotation)], check=True)
        jar = self.base / "annotation.jar"
        subprocess.run([str(self.jdks[0] / "bin/jar"), "cf", str(jar), "-C", str(classes), "."], check=True)
        for root in self.roots:
            self.write(root / SOURCES[0],
                       "import com.fasterxml.jackson.annotation.JsonProperty; class Input0 { @JsonProperty String value; }\n")
        result, output = self.run_checker(("--classpaths", str(jar), str(jar), str(jar)))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(json.loads((output / "results.json").read_text())["passed"])

    def test_missing_probe_in_third_branch_fails_explicitly(self):
        (self.roots[2] / "verification/three-line-parity/ValueContractProbe.java").unlink()
        result, output = self.run_checker()
        self.assertNotEqual(0, result.returncode, result.stdout)
        report = json.loads((output / "results.json").read_text())
        self.assertTrue(report["runs"][2]["probe_missing"])


if __name__ == "__main__":
    unittest.main()
