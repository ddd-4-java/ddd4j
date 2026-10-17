"""三线结构清单必须暴露样例、测试和当前新增源码差异。"""

import csv
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("verify-three-line-structure-api-parity.py")


class ThreeLineInventoryTest(unittest.TestCase):
    def test_object_equals_parameter_name_is_not_public_api(self):
        source = "ddd4j-core/src/main/java/example/Foo.java"
        base = ("package example; public class Foo { "
                "public boolean equals(Object o) { return this == o; } "
                "public boolean verify(Object input) { return input != null; } }")
        renamed_equals = base.replace("equals(Object o)", "equals(Object obj)").replace(
            "this == o", "this == obj")
        self.write(self.roots[0], source, base)
        self.write(self.roots[1], source, renamed_equals)
        self.write(self.roots[2], source, renamed_equals)

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertFalse(any(row["category"] == "api" and row["path"].endswith("Foo#equals(Object)")
                             for row in rows))

        self.write(self.roots[1], source,
                   renamed_equals.replace("verify(Object input)", "verify(Object value)").replace(
                       "input != null", "value != null"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["category"] == "api"
                                      and row["path"].endswith("Foo#verify(Object)")))

    def test_implicit_public_constructor_matches_explicit_noarg_constructor(self):
        source = "ddd4j-core/src/main/java/example/Foo.java"
        self.write(self.roots[0], source, "package example; public class Foo {}")
        self.write(self.roots[1], source,
                   "package example; public class Foo { public Foo() {} }")
        self.write(self.roots[2], source,
                   "package example; public class Foo { public Foo() {} }")

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        constructor = "ddd4j-core/src/main/java/example/Foo.java#example.Foo#<init>()"
        self.assertFalse(any(row["path"] == constructor for row in rows))

        self.write(self.roots[0], source,
                   "package example; public class Foo { private Foo() {} }")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == constructor))

    def test_lombok_constructor_annotations_do_not_invent_implicit_constructor(self):
        source = "ddd4j-core/src/main/java/example/Foo.java"
        constructor = "ddd4j-core/src/main/java/example/Foo.java#example.Foo#<init>()"
        self.write(self.roots[0], source,
                   "package example; @Value public class Foo { final String value = \"x\"; }")
        for root in self.roots[1:]:
            self.write(root, source,
                       "package example; public class Foo { public Foo() {} }")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == constructor))

        self.write(self.roots[0], source,
                   "package example; @Data @NoArgsConstructor @AllArgsConstructor "
                   "public class Foo { String value; }")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertFalse(any(row["path"] == constructor for row in rows))

    def test_indexed_javadoc_history_requires_locked_file_hash(self):
        path = "docs/verification/javadoc-fill-report-01.md"
        content = "# Historical Javadoc report\n"
        self.write(self.roots[2], path, content)
        digest = hashlib.sha256(content.encode()).hexdigest()
        index = ("| 文件 | 原所属线与时间 | 历史来源 | 文件 SHA-256 |\n"
                 f"| `javadoc-fill-report-01.md` | 3.0.x；2026-10-10T18:12:52+08:00 | "
                 f"[原始文件](https://github.com/ddd-4-java/ddd4j/blob/{'a' * 40}/{path}) | "
                 f"`{digest}` |\n")
        for root in self.roots:
            self.write(root, "docs/superpowers/historical-evidence-index.md", index)

        _, output = self.run_checker("inventory")
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("HISTORICAL", next(row["status"] for row in rows if row["path"] == path))

        self.write(self.roots[2], path, content + "changed\n")
        _, output = self.run_checker("inventory")
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == path))

    def test_indexed_javadoc_batch_requires_locked_file_hash(self):
        path = "docs/verification/javadoc-fill-batches/batch-01.txt"
        content = "Historical batch file list\n"
        self.write(self.roots[2], path, content)
        digest = hashlib.sha256(content.encode()).hexdigest()
        index = ("| 文件 | 原所属线与时间 | 历史来源 | 文件 SHA-256 |\n"
                 f"| `batch-01.txt` | 3.0.x；2026-10-10T18:12:52+08:00 | "
                 f"[原始文件](https://github.com/ddd-4-java/ddd4j/blob/{'a' * 40}/{path}) | "
                 f"`{digest}` |\n")
        for root in self.roots:
            self.write(root, "docs/superpowers/historical-evidence-index.md", index)

        _, output = self.run_checker("inventory")
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("HISTORICAL", next(row["status"] for row in rows if row["path"] == path))

        self.write(self.roots[2], path, content + "changed\n")
        _, output = self.run_checker("inventory")
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == path))

    def test_compile_uses_explicit_jdks_without_macos_discovery(self):
        specification = importlib.util.spec_from_file_location("structure_gate", SCRIPT)
        module = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            roots = [base / str(major) for major in (8, 17, 21)]
            for root in roots:
                root.mkdir()
            homes = {f"DDD4J_JDK{major}_HOME": str(base / f"java-{major}")
                     for major in (8, 17, 21)}
            with mock.patch.dict(os.environ, homes), mock.patch.object(
                    module, "run", side_effect=AssertionError("macOS JDK discovery used")), mock.patch.object(
                    module.subprocess, "run", return_value=SimpleNamespace(
                        returncode=0, stdout="", stderr="")) as execute:
                results = module.compile_roots(roots, base)
            self.assertEqual([0, 0, 0], [item["exit"] for item in results])
            self.assertEqual([f"compile-line-{index}.log" for index in (1, 2, 3)],
                             [item["log"] for item in results])
            self.assertEqual([homes[f"DDD4J_JDK{major}_HOME"] for major in (8, 17, 21)],
                             [call.kwargs["env"]["JAVA_HOME"] for call in execute.call_args_list])

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.roots = [self.base / name for name in ("jdk8", "jdk17", "jdk21")]
        for root in self.roots:
            root.mkdir()
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            self.write(root, "ddd4j-core/pom.xml", "<project/>")
            self.write(root, "ddd4j-samples/demo/pom.xml", "<project/>")
            self.write(root, "ddd4j-core/src/main/java/example/Foo.java", "package example; public class Foo {}")
            self.write(root, "docs/guide.md", "same content\n")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
        self.write(self.roots[0], "ddd4j-core/src/test/java/example/FooTest.java", "class FooTest {}")
        self.write(self.roots[1], "docs/guide.md", "different business description\n")
        self.write(self.roots[2], "ddd4j-samples/demo/src/main/java/example/Demo.java", "class Demo {}")
        self.policy = self.base / "policy.json"
        self.policy.write_text(json.dumps({"schemaVersion": 1, "platformItemsMissingOnJdk8": [],
                                           "typeMappings": [], "unclassifiedDifferenceIsFailure": True}))

    @staticmethod
    def write(root, name, content):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def run_checker(self, phase):
        output = self.base / phase
        command = [sys.executable, str(SCRIPT), "--roots", *map(str, self.roots),
                   "--output", str(output), "--policy", str(self.policy), "--phase", phase]
        result = subprocess.run(command, capture_output=True, text=True)
        return result, output

    def test_exact_platform_api_allows_jvm_generic_wildcard_but_not_descriptor_glob(self):
        specification = importlib.util.spec_from_file_location("structure_gate", SCRIPT)
        module = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(module)
        evidence = "ddd4j-core/src/test/java/example/FooPlatformContractTest.java"
        for root in self.roots:
            self.write(root, evidence, "class FooPlatformContractTest {}")
        entry = ("ddd4j-core|example/Foo|filter|(Lexample/Request;)V|"
                 "generic=(Lexample/Request<*>;)V|throws=|constant=|params=request")
        item = {"sourceSymbol": "ddd4j-core/src/main/java/example/Foo.java#example.Foo#filter(Request<?>)",
                "compiledEntry": entry, "evidence": evidence, "reason": "Exact JVM generic signature"}

        self.assertEqual([item], module.validate_platform_api_mappings([item], self.roots))
        invalid = dict(item, compiledEntry=entry.replace("Lexample/Request;", "Lexample/*;"))
        with self.assertRaises(ValueError):
            module.validate_platform_api_mappings([invalid], self.roots)

    def test_compiler_bridge_mapping_requires_bridge_flag_and_explicit_source_method(self):
        source = "ddd4j-core/src/main/java/example/Foo.java"
        evidence = "ddd4j-core/src/test/java/example/FooPlatformContractTest.java"
        for index, root in enumerate(self.roots):
            content = ("package example; public class Foo {}" if index == 0 else
                       "package example; interface Port<T> { T load(); } "
                       "public class Foo implements Port<String> { "
                       "public String load() { return \"x\"; } "
                       "public Object load(int count) { return count; } }")
            self.write(root, source, content)
            self.write(root, evidence, "class FooPlatformContractTest {}")
            executable = root / "mvnw"
            executable.write_text(
                "#!/bin/sh\nmkdir -p ddd4j-core/target/classes\n"
                '"$JAVA_HOME/bin/javac" -parameters -d ddd4j-core/target/classes '
                "ddd4j-core/src/main/java/example/Foo.java\n")
            executable.chmod(0o755)

        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        raw = json.loads((output / "compiled-api.json").read_text())
        high = raw["pairs"]["1-2"]["only_right"]
        explicit = next(item for item in high if "|example/Foo|load|()Ljava/lang/String;" in item)
        bridge = next(item for item in high if "|example/Foo|load|()Ljava/lang/Object;" in item)
        not_bridge = next(item for item in high if "|example/Foo|load|(I)Ljava/lang/Object;" in item)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        symbol = next(row["path"] for row in rows if row["category"] == "api"
                      and "Foo#load()" in row["path"])

        policy = json.loads(self.policy.read_text())
        policy["platformApiMembersMissingOnJdk8"] = [{
            "sourceSymbol": symbol, "compiledEntry": explicit,
            "compilerBridgeEntry": bridge, "evidence": evidence,
            "reason": "Generic platform interface generates an exact JVM bridge",
        }]
        self.policy.write_text(json.dumps(policy))
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)  # overload and inventory remain open
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual([], report["stalePlatformApiMappings"])
        self.assertIn(bridge, report["platformCompilerBridges"])
        self.assertEqual([not_bridge], report["unresolved"]["1-2"]["only_right"])

        policy["platformApiMembersMissingOnJdk8"][0]["compilerBridgeEntry"] = not_bridge
        self.policy.write_text(json.dumps(policy))
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertIn(not_bridge, report["stalePlatformApiMappings"])

    def test_inventory_reports_untracked_test_sample_and_doc_change(self):
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        with (output / "differences.csv").open(newline="") as stream:
            rows = list(csv.DictReader(stream))
        paths = {row["path"] for row in rows}
        self.assertIn("ddd4j-core/src/test/java/example/FooTest.java", paths)
        self.assertIn("ddd4j-samples/demo/src/main/java/example/Demo.java", paths)
        self.assertIn("docs/guide.md", paths)
        tasks = {row["path"]: row["task"] for row in rows}
        self.assertEqual("T04", tasks["ddd4j-core/src/test/java/example/FooTest.java"])
        self.assertEqual("T08", tasks["ddd4j-samples/demo/src/main/java/example/Demo.java"])
        self.assertEqual("T09", tasks["docs/guide.md"])
        self.assertTrue((output / "inventory.json").is_file())
        self.assertTrue((output / "summary.md").is_file())

    def test_jdk8_only_platform_file_requires_exact_source_and_three_line_evidence(self):
        platform = "ddd4j-core/src/main/java/example/LegacyResponseFilter.java"
        counterpart = "ddd4j-core/src/main/java/example/Foo.java"
        evidence = "ddd4j-core/src/test/java/example/FooPlatformContractTest.java"
        content = "package example; public class LegacyResponseFilter {}"
        self.write(self.roots[0], platform, content)
        for root in self.roots:
            self.write(root, evidence, "class FooPlatformContractTest {}")
        policy = json.loads(self.policy.read_text())
        policy["platformFilesOnlyOnJdk8"] = [{
            "path": platform, "sha256": hashlib.sha256(content.encode()).hexdigest(),
            "counterpart": counterpart, "evidence": evidence,
            "reason": "JDK 8 platform provider entry",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(row for row in rows if row["path"] == platform)
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        self.write(self.roots[0], platform, content + " // changed")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale JDK 8-only platform file evidence", result.stderr)

    def test_verify_fails_for_unexplained_differences(self):
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        self.assertTrue((output / "differences.csv").is_file(), result.stderr)

    def test_pom_content_is_platform_config_only_when_module_identity_and_topology_match(self):
        path = "ddd4j-platform/pom.xml"
        old = ('<project xmlns="http://maven.apache.org/POM/4.0.0">'
               '<modelVersion>4.0.0</modelVersion><groupId>io.ddd4j</groupId>'
               '<artifactId>ddd4j-platform</artifactId>'
               '<packaging>pom</packaging><modules><module>child</module></modules>'
               '<properties><client.version>1</client.version></properties></project>')
        modern = ('<project xmlns="http://maven.apache.org/POM/4.1.0">'
                  '<modelVersion>4.1.0</modelVersion><groupId>io.ddd4j</groupId>'
                  '<artifactId>ddd4j-platform</artifactId>'
                  '<packaging>pom</packaging><subprojects><subproject>child</subproject></subprojects>'
                  '<properties><client.version>3</client.version></properties></project>')
        self.write(self.roots[0], path, old)
        self.write(self.roots[1], path, old.replace('>1<', '>2<'))
        self.write(self.roots[2], path, modern)

        def status():
            result, output = self.run_checker("inventory")
            self.assertEqual(0, result.returncode, result.stderr)
            rows = json.loads((output / "inventory.json").read_text())["differences"]
            return next(row["status"] for row in rows if row["path"] == path)

        self.assertEqual("PLATFORM_CONFIG", status())
        self.write(self.roots[2], path, modern.replace('<subproject>child</subproject>',
                                                       '<subproject>other</subproject>'))
        self.assertEqual("OPEN", status())
        self.write(self.roots[2], path, modern)
        self.write(self.roots[1], path, old.replace('<groupId>io.ddd4j</groupId>',
                                                    '<groupId>io.other</groupId>'))
        self.assertEqual("OPEN", status())
        self.write(self.roots[1], path, old.replace('>1<', '>2<'))
        self.write(self.roots[1], path, old.replace('<packaging>pom</packaging>',
                                                    '<packaging>jar</packaging>'))
        self.assertEqual("OPEN", status())
        (self.roots[0] / path).unlink()
        self.assertEqual("OPEN", status())

    def test_verify_reports_compiled_api_blocker_even_with_open_differences(self):
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        report = json.loads((output / "inventory.json").read_text())
        self.assertTrue(any(row["status"] == "OPEN" for row in report["differences"]))
        self.assertEqual("BLOCKED", report["compiledApi"]["status"])
        self.assertIn("Maven Wrapper", report["compiledApi"]["reason"])

    def test_dependency_config_exception_requires_exact_three_line_inputs(self):
        path = "config/dependencies/component-matrix.csv"
        contents = ("jdk8\n", "jdk17\n", "jdk21\n")
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
        policy = json.loads(self.policy.read_text())
        policy["platformDependencyConfigFiles"] = [{
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "T03 strict dependency gate",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_CONFIG", row["status"])
        self.write(self.roots[1], path, "unexpected\n")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)

    def test_build_config_exception_requires_exact_three_line_inputs(self):
        path = ".mvn/wrapper/maven-wrapper.properties"
        contents = ("maven 3\n", "maven 3\n", "maven 4\n")
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
        policy = json.loads(self.policy.read_text())
        policy["platformBuildConfigFiles"] = [{
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "JDK 8/17 Maven 3; JDK 21 Maven 4 build gate",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_CONFIG", row["status"])
        self.write(self.roots[2], path, "unexpected\n")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)

    def test_single_wildcard_makes_same_package_explicit_import_redundant(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = "package example; import java.util.*; public class Foo { List<String> values; }"
        redundant = ("package example; import java.util.*; import java.util.List; "
                     "public class Foo { List<String> values; }")
        for root, source in zip(self.roots, (common, redundant, common)):
            self.write(root, path, source)

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("LEXICALLY_EQUIVALENT", next(row["status"] for row in rows
                                                 if row["path"] == path and row["category"] == "production"))

        ambiguous = common.replace("import java.util.*;", "import java.util.*; import java.awt.*;")
        ambiguous_redundant = redundant.replace(
            "import java.util.*;", "import java.util.*; import java.awt.*;")
        for root, source in zip(self.roots, (ambiguous, ambiguous_redundant, ambiguous)):
            self.write(root, path, source)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == path and row["category"] == "production"))

    def test_platform_import_namespace_requires_unchanged_java_body(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        old = ("package example;\nimport javax.servlet.ServletRequest;\n"
               "public class Foo { ServletRequest request; int size() { return 1; } }\n")
        modern = old.replace("javax.servlet.", "jakarta.servlet.")
        for root, source in zip(self.roots, (old, modern, modern)):
            self.write(root, path, source)

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                             if row["path"] == path and row["category"] == "production"))

        self.write(self.roots[2], path, modern.replace("return 1", "return 2"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == path and row["category"] == "production"))

        jackson_two = ("package example;\nimport com.fasterxml.jackson.databind.ObjectMapper;\n"
                       "public class Foo { ObjectMapper mapper; int size() { return 1; } }\n")
        jackson_three = jackson_two.replace("com.fasterxml.jackson.databind.",
                                            "tools.jackson.databind.")
        for root, source in zip(self.roots, (jackson_two, jackson_two, jackson_three)):
            self.write(root, path, source)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                             if row["path"] == path and row["category"] == "production"))

        unapproved = old.replace("javax.servlet.", "javax.crypto.")
        for root, source in zip(self.roots, (unapproved,
                                             unapproved.replace("javax.crypto.", "jakarta.crypto."),
                                             unapproved.replace("javax.crypto.", "jakarta.crypto."))):
            self.write(root, path, source)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == path and row["category"] == "production"))

    def test_verify_compares_compiled_api_while_inventory_remains_open(self):
        wrapper = ('#!/bin/sh\n'
                   'mkdir -p ddd4j-core/target/classes\n'
                   '"$JAVA_HOME/bin/javac" -d ddd4j-core/target/classes '
                   'ddd4j-core/src/main/java/example/Foo.java\n')
        for root in self.roots:
            executable = root / "mvnw"
            executable.write_text(wrapper)
            executable.chmod(0o755)
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        self.assertTrue(any(row["status"] == "OPEN" for row in report["differences"]))
        self.assertEqual("PASS", report["compiledApi"]["status"])
        self.assertTrue((output / "compiled-api.json").is_file())

    def test_exact_jdk8_absent_api_keeps_raw_diff_and_rejects_stale_mapping(self):
        source = "ddd4j-core/src/main/java/example/Foo.java"
        evidence = "ddd4j-core/src/test/java/example/FooPlatformContractTest.java"
        for index, root in enumerate(self.roots):
            constructor = "" if index == 0 else "public Foo() {} public Foo(String client) {}"
            self.write(root, source, "package example; public class Foo { " + constructor + " }")
            self.write(root, evidence, "class FooPlatformContractTest {}")
            executable = root / "mvnw"
            executable.write_text(
                "#!/bin/sh\nmkdir -p ddd4j-core/target/classes\n"
                '"$JAVA_HOME/bin/javac" -d ddd4j-core/target/classes '
                "ddd4j-core/src/main/java/example/Foo.java\n")
            executable.chmod(0o755)

        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        raw = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual("FAIL", raw["status"])
        entry = next(item for item in raw["pairs"]["1-2"]["only_right"]
                     if "|example/Foo|<init>|(Ljava/lang/String;)" in item)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        source_symbol = next(row["path"] for row in rows
                             if row["category"] == "api" and "Foo#<init>(String)" in row["path"])

        policy = json.loads(self.policy.read_text())
        policy["platformApiMembersMissingOnJdk8"] = [{
            "sourceSymbol": source_symbol,
            "compiledEntry": entry,
            "evidence": evidence,
            "reason": "A platform constructor unavailable on JDK 8",
        }]
        self.policy.write_text(json.dumps(policy))
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode, result.stderr)  # other inventory differences remain
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual("PASS", report["status"], report)
        self.assertIn(entry, report["pairs"]["1-2"]["only_right"])
        self.assertEqual([], report["unresolved"]["1-2"]["only_right"])
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == source_symbol)
        self.assertEqual("PLATFORM_API_ADAPTED", row["status"])

        self.write(self.roots[1], source,
                   "package example; public class Foo { public Foo() {} public Foo(String transport) {} }")
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual("FAIL", report["status"])
        self.assertIn(entry, report["stalePlatformApiMappings"])

        self.write(self.roots[1], source,
                   "package example; public class Foo { public Foo() {} public Foo(Object client) {} }")
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual("FAIL", report["status"])
        self.assertIn(entry, report["stalePlatformApiMappings"])

    def test_exact_jdk8_only_api_keeps_raw_diff_and_rejects_stale_mapping(self):
        source = "ddd4j-core/src/main/java/example/Foo.java"
        evidence = "ddd4j-core/src/test/java/example/FooPlatformContractTest.java"
        for index, root in enumerate(self.roots):
            constructor = "public Foo(String client) {}" if index == 0 else "public Foo() {}"
            self.write(root, source, "package example; public class Foo { " + constructor + " }")
            self.write(root, evidence, "class FooPlatformContractTest {}")
            executable = root / "mvnw"
            executable.write_text(
                "#!/bin/sh\nmkdir -p ddd4j-core/target/classes\n"
                '"$JAVA_HOME/bin/javac" -d ddd4j-core/target/classes '
                "ddd4j-core/src/main/java/example/Foo.java\n")
            executable.chmod(0o755)

        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        raw = json.loads((output / "compiled-api.json").read_text())
        entry = next(item for item in raw["pairs"]["1-2"]["only_left"]
                     if "|example/Foo|<init>|(Ljava/lang/String;)" in item)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        source_row = next(row for row in rows
                          if row["category"] == "api" and "Foo#<init>(String)" in row["path"])
        source_symbol = source_row["path"]
        policy = json.loads(self.policy.read_text())
        policy["platformApiMembersOnlyOnJdk8"] = [{
            "sourceSymbol": source_symbol,
            "sourceDeclaration": source_row["jdk8"],
            "compiledEntry": entry,
            "evidence": evidence,
            "reason": "A platform constructor only available on JDK 8",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertIn(entry, report["pairs"]["1-2"]["only_left"])
        self.assertNotIn(entry, report["unresolved"]["1-2"]["only_left"])
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_API_ADAPTED",
                         next(row["status"] for row in rows if row["path"] == source_symbol))

        self.write(self.roots[0], source,
                   "package example; public class Foo { public Foo(String transport) {} }")
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertIn(entry, report["stalePlatformApiMappings"])

    def test_compile_alias_does_not_require_codegraph_index(self):
        wrapper = ('#!/bin/sh\n'
                   'mkdir -p ddd4j-core/target/classes\n'
                   '"$JAVA_HOME/bin/javac" -d ddd4j-core/target/classes '
                   'ddd4j-core/src/main/java/example/Foo.java\n')
        for root in self.roots:
            executable = root / "mvnw"
            executable.write_text(wrapper)
            executable.chmod(0o755)
        output = self.base / "compile-alias"
        command = [sys.executable, str(SCRIPT), "--roots", *map(str, self.roots),
                   "--output", str(output), "--policy", str(self.policy), "--compile"]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertTrue((output / "inventory.json").is_file(), result.stderr)
        self.assertEqual("PASS", json.loads((output / "inventory.json").read_text())
                         ["compiledApi"]["status"])

    def test_exact_platform_source_mapping_rejects_extra_behavior_change(self):
        path = "ddd4j-core/src/main/java/example/ServletAdapter.java"
        evidence = "ddd4j-core/src/test/java/example/ServletAdapterTest.java"
        before = ("package example; import javax.servlet.http.HttpServletRequest; "
                  "public class ServletAdapter { public void handle(HttpServletRequest request) {} }")
        after = before.replace("import javax.servlet.http.HttpServletRequest;",
                               "import jakarta.servlet.http.HttpServletRequest;")
        for index, root in enumerate(self.roots):
            self.write(root, path, before if index == 0 else after)
            self.write(root, evidence, "class ServletAdapterTest {}")
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path,
            "replacements": [{"from": "import javax.servlet.http.HttpServletRequest;",
                              "to": "import jakarta.servlet.http.HttpServletRequest;"}],
            "evidence": evidence,
            "reason": "Servlet namespace migration",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        row = next(item for item in report["differences"] if item["path"] == path
                   and item["category"] == "production")
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        self.write(self.roots[1], path,
                   after.replace("{} }", "{} public int changed() { return 1; } }"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        row = next(item for item in report["differences"] if item["path"] == path
                   and item["category"] == "production")
        self.assertEqual("OPEN", row["status"])

    def test_historical_task_section_keeps_shared_document_body_guarded(self):
        path = "docs/reference/fuin-api-patterns/01-aggregate-root.md"
        index = "docs/superpowers/historical-evidence-index.md"
        marker = "## 落地计划\n"
        bodies = ["# 共同正文\n\n", "# 共同正文\n\n", "# 共同正文\n\n"]
        sections = ["\n- [x] 已完成并有本线证据\n",
                    "\n- [ ] 本线尚待验证\n", "\n- [ ] 本线尚待验证\n"]
        hashes = {version: hashlib.sha256(section.encode()).hexdigest()
                  for version, section in zip(("1.0.x", "2.0.x", "3.0.x"), sections)}
        listing = path + " " + " ".join(hashes.values())
        for root, body, section in zip(self.roots, bodies, sections):
            self.write(root, path, body + marker + section)
            self.write(root, index, listing)
        policy = json.loads(self.policy.read_text())
        policy["historicalDocumentSections"] = [{
            "path": path, "marker": marker, "sectionSha256": hashes,
            "evidence": index, "reason": "只保留各线历史任务与证据状态"
        }]
        self.policy.write_text(json.dumps(policy))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("HISTORICAL_SECTION", row["status"])

        self.write(self.roots[1], path, "# 业务正文漂移\n\n" + marker + sections[1])
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("OPEN", row["status"])

        self.write(self.roots[1], path, bodies[1] + marker + "- [x] 无证据改写\n")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("OPEN", row["status"])

    def test_hashed_syntax_adaptation_requires_three_line_contract_and_current_sources(self):
        path = "ddd4j-core/src/main/java/example/BrokerType.java"
        evidence = "ddd4j-core/src/test/java/example/BrokerTypeContractTest.java"
        sources = ["class BrokerType { int value() { return 8; } }",
                   "class BrokerType { int value() { return 17; } }",
                   "class BrokerType { int value() { return 21; } }"]
        test_source = "class BrokerTypeContractTest {}"
        for root, source in zip(self.roots, sources):
            self.write(root, path, source)
            self.write(root, evidence, test_source)
        policy = json.loads(self.policy.read_text())
        policy["platformSyntaxMappings"] = [{
            "path": path,
            "sourceSha256": [hashlib.sha256(source.encode()).hexdigest() for source in sources],
            "evidence": evidence,
            "testSha256": hashlib.sha256(test_source.encode()).hexdigest(),
            "reason": "JDK-specific syntax with a shared observable contract",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                         if row["path"] == path))

        self.write(self.roots[1], path, sources[1] + " // changed")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == path))

        self.write(self.roots[1], path, sources[1])
        self.write(self.roots[2], evidence, test_source + " // changed")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == path))

    def test_exact_pulsar_broker_image_mapping_rejects_behavior_change(self):
        path = ("ddd4j-mq/ddd4j-mq-pulsar/src/test/java/io/ddd4j/mq/pulsar/"
                "PulsarContainerIntegrationTest.java")
        before = ('class PulsarContainerIntegrationTest { String image = '
                  '"apachepulsar/pulsar:2.10.6"; void same() {} }')
        after = before.replace("apachepulsar/pulsar:2.10.6", "apachepulsar/pulsar:4.2.4")
        for index, root in enumerate(self.roots):
            self.write(root, path, before if index == 0 else after)
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path,
            "replacements": [{"from": "apachepulsar/pulsar:2.10.6",
                              "to": "apachepulsar/pulsar:4.2.4"}],
            "evidence": path,
            "reason": "Exact Pulsar broker image adaptation",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        row = next(item for item in report["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        self.write(self.roots[2], path, after.replace("void same()", "void changed()"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        row = next(item for item in report["differences"] if item["path"] == path)
        self.assertEqual("OPEN", row["status"])

    def test_exact_jackson_namespace_mapping_rejects_behavior_change(self):
        path = "ddd4j-core/src/test/java/example/JacksonContractTest.java"
        before = ("package example; import com.fasterxml.jackson.databind.ObjectMapper; "
                  "class JacksonContractTest { String read(ObjectMapper mapper) { return \"same\"; } }")
        after = before.replace("import com.fasterxml.jackson.databind.ObjectMapper;",
                               "import tools.jackson.databind.ObjectMapper;")
        for index, root in enumerate(self.roots):
            self.write(root, path, before if index < 2 else after)
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path,
            "targetLines": ["3.0.x"],
            "replacements": [{"from": "import com.fasterxml.jackson.databind.ObjectMapper;",
                              "to": "import tools.jackson.databind.ObjectMapper;"}],
            "evidence": path,
            "reason": "Exact Jackson ObjectMapper package migration",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        row = next(item for item in report["differences"] if item["path"] == path
                   and item["category"] == "test")
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        self.write(self.roots[2], path, after.replace('"same"', '"different"'))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        row = next(item for item in report["differences"] if item["path"] == path
                   and item["category"] == "test")
        self.assertEqual("OPEN", row["status"])

    def test_exact_jackson_json_mapper_namespace_mapping_rejects_behavior_change(self):
        path = "ddd4j-core/src/test/java/example/JsonMapperContractTest.java"
        before = ("package example; import com.fasterxml.jackson.databind.json.JsonMapper; "
                  "class JsonMapperContractTest { String read(JsonMapper mapper) { return \"same\"; } }")
        after = before.replace("import com.fasterxml.jackson.databind.json.JsonMapper;",
                               "import tools.jackson.databind.json.JsonMapper;")
        for index, root in enumerate(self.roots):
            self.write(root, path, before if index < 2 else after)
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path,
            "targetLines": ["3.0.x"],
            "replacements": [{"from": "import com.fasterxml.jackson.databind.json.JsonMapper;",
                              "to": "import tools.jackson.databind.json.JsonMapper;"}],
            "evidence": path,
            "reason": "Exact Jackson JsonMapper package migration",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path and item["category"] == "test")
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        self.write(self.roots[2], path, after.replace('"same"', '"different"'))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path and item["category"] == "test")
        self.assertEqual("OPEN", row["status"])
    def test_java_comment_and_formatting_only_change_is_token_equivalent(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = 'package example; public class Foo { public String value() { return "same"; } }'
        formatted = ('package example;\n// explanatory comment\npublic class Foo {\n'
                     '  public String value() { /* detail */ return "same"; }\n}\n')
        self.write(self.roots[0], path, common)
        self.write(self.roots[1], path, formatted)
        self.write(self.roots[2], path, common)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path and item["category"] == "production")
        self.assertEqual("LEXICALLY_EQUIVALENT", row["status"])

    def test_serial_field_annotation_is_exact_jdk_adaptation(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        old = ("package example; import java.io.Serializable; "
               "public class Foo implements Serializable { "
               "private static final long serialVersionUID = 1L; "
               "int value() { return 7; } }")
        modern = ("package example; import java.io.Serial; import java.io.Serializable; "
                  "public class Foo implements Serializable {\n"
                  "    @Serial\n    private static final long serialVersionUID = 1L;\n"
                  "    int value() { return 7; } }")
        self.write(self.roots[0], path, old)
        self.write(self.roots[1], path, modern)
        self.write(self.roots[2], path, modern)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path)
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        for changed in (modern.replace("serialVersionUID = 1L", "serialVersionUID = 2L"),
                        modern.replace("return 7", "return 8"),
                        modern.replace("@Serial\n    private", "@Serial int other;\n    private")):
            with self.subTest(changed=changed):
                self.write(self.roots[1], path, changed)
                result, output = self.run_checker("inventory")
                self.assertEqual(0, result.returncode, result.stderr)
                rows = json.loads((output / "inventory.json").read_text())["differences"]
                row = next(item for item in rows if item["path"] == path)
                self.assertEqual("OPEN", row["status"])


    def test_java_import_order_only_is_equivalent_but_import_target_changes_remain_open(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = ("package example; import java.util.List; import java.util.Map; "
                  "import static java.util.Collections.emptyList; "
                  "public class Foo { List<String> values() { return emptyList(); } Map<String, String> map; }")
        reordered = ("package example; import static java.util.Collections.emptyList; "
                     "import java.util.Map; import java.util.List; "
                     "public class Foo { List<String> values() { return emptyList(); } Map<String, String> map; }")
        self.write(self.roots[0], path, common)
        self.write(self.roots[1], path, reordered)
        self.write(self.roots[2], path, common)

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path and item["category"] == "production")
        self.assertEqual("LEXICALLY_EQUIVALENT", row["status"])

        for changed in (reordered.replace("java.util.Map", "java.util.Set"),
                        reordered.replace("import java.util.List;", "import java.util.*;")):
            with self.subTest(changed=changed):
                self.write(self.roots[1], path, changed)
                result, output = self.run_checker("inventory")
                self.assertEqual(0, result.returncode, result.stderr)
                rows = json.loads((output / "inventory.json").read_text())["differences"]
                row = next(item for item in rows if item["path"] == path and item["category"] == "production")
                self.assertEqual("OPEN", row["status"])

    def test_java_token_change_is_not_hidden_by_formatting_normalization(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = 'package example; public class Foo { public String value() { return "same"; } }'
        cases = [
            common.replace('"same"', '"different"'),
            common.replace('return "same";', 'return other;'),
            common.replace('String value()', 'String changed()'),
            common.replace('package example;', 'package other;'),
        ]
        for changed in cases:
            with self.subTest(changed=changed):
                self.write(self.roots[0], path, common)
                self.write(self.roots[1], path, changed)
                self.write(self.roots[2], path, common)
                result, output = self.run_checker("inventory")
                self.assertEqual(0, result.returncode, result.stderr)
                rows = json.loads((output / "inventory.json").read_text())["differences"]
                row = next(item for item in rows if item["path"] == path and item["category"] == "production")
                self.assertEqual("OPEN", row["status"])

    def test_explicit_and_compiler_generated_constructor_share_compiled_api(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        self.write(self.roots[1], path, "package example; public class Foo { public Foo() {} }")
        wrapper = ('#!/bin/sh\n'
                   'mkdir -p ddd4j-core/target/classes\n'
                   '"$JAVA_HOME/bin/javac" -d ddd4j-core/target/classes '
                   'ddd4j-core/src/main/java/example/Foo.java\n')
        for root in self.roots:
            executable = root / "mvnw"
            executable.write_text(wrapper)
            executable.chmod(0o755)
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        self.assertFalse(any(row["category"] == "api" and row["path"].endswith("Foo#<init>()")
                             for row in report["differences"]))
        self.assertEqual("OPEN", next(row for row in report["differences"]
                                      if row["category"] == "production" and row["path"] == path)["status"])
        self.assertEqual("PASS", report["compiledApi"]["status"])

    def test_compiled_class_drift_keeps_source_api_open(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        self.write(self.roots[1], path,
                   'package example; public class Foo { public Foo() {} public String extra() { return "x"; } }')
        wrapper = ('#!/bin/sh\n'
                   'mkdir -p ddd4j-core/target/classes\n'
                   '"$JAVA_HOME/bin/javac" -d ddd4j-core/target/classes '
                   'ddd4j-core/src/main/java/example/Foo.java\n')
        for root in self.roots:
            executable = root / "mvnw"
            executable.write_text(wrapper)
            executable.chmod(0o755)
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        self.assertEqual("FAIL", report["compiledApi"]["status"])
        self.assertEqual("OPEN", next(row for row in report["differences"]
                                      if row["category"] == "api" and row["path"].endswith("Foo#extra()"))["status"])

    def test_inventory_identifies_method_return_type_drift(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = "package example; public class Foo { public String handle(String input) { return input; } }"
        different = "package example; public class Foo { public int handle(String input) { return 0; } }"
        self.write(self.roots[0], path, common)
        self.write(self.roots[1], path, different)
        self.write(self.roots[2], path, common)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        with (output / "differences.csv").open(newline="") as stream:
            rows = list(csv.DictReader(stream))
        api_rows = [row for row in rows if row["category"] == "api" and "example.Foo#handle" in row["path"]]
        self.assertTrue(api_rows, rows)
        self.assertIn("String", api_rows[0]["jdk8"])
        self.assertIn("int", api_rows[0]["jdk17"])

    def test_private_nested_methods_are_not_public_api(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = "package example; public class Foo { private static class Hidden { public String value() { return \"x\"; } } }"
        different = "package example; public class Foo { private static class Hidden { public int value() { return 1; } } }"
        self.write(self.roots[0], path, common)
        self.write(self.roots[1], path, different)
        self.write(self.roots[2], path, common)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertTrue(any(row["path"] == path and row["category"] == "production" for row in rows))
        self.assertFalse(any(row["category"] == "api" and "Foo.Hidden" in row["path"] for row in rows))

    def test_public_nested_method_drift_remains_visible(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        common = "package example; public class Foo { public static class Nested { public String value() { return \"x\"; } } }"
        different = "package example; public class Foo { public static class Nested { public int value() { return 1; } } }"
        self.write(self.roots[0], path, common)
        self.write(self.roots[1], path, different)
        self.write(self.roots[2], path, common)
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertTrue(any(row["category"] == "api" and "Foo.Nested#value()" in row["path"] for row in rows))

    def test_verify_cannot_pass_without_compiled_api_evidence(self):
        (self.roots[0] / "ddd4j-core/src/test/java/example/FooTest.java").unlink()
        (self.roots[2] / "ddd4j-samples/demo/src/main/java/example/Demo.java").unlink()
        self.write(self.roots[1], "docs/guide.md", "same content\n")
        result, output = self.run_checker("verify")
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("compiled API", (output / "summary.md").read_text())

    def test_missing_production_type_and_test_are_both_open(self):
        (self.roots[0] / "ddd4j-core/src/main/java/example/Foo.java").unlink()
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        with (output / "differences.csv").open(newline="") as stream:
            rows = list(csv.DictReader(stream))
        by_path = {row["path"]: row for row in rows}
        for path, category in (("ddd4j-core/src/main/java/example/Foo.java", "production"),
                               ("ddd4j-core/src/test/java/example/FooTest.java", "test")):
            self.assertEqual(category, by_path[path]["category"])
            self.assertEqual("OPEN", by_path[path]["status"])
            self.assertEqual("missing", by_path[path]["reason"])
        verify, _ = self.run_checker("verify")
        self.assertNotEqual(0, verify.returncode)

    def test_changed_business_document_is_open(self):
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        with (output / "differences.csv").open(newline="") as stream:
            rows = list(csv.DictReader(stream))
        document = next(row for row in rows if row["path"] == "docs/guide.md")
        self.assertEqual("product-document", document["category"])
        self.assertEqual("content", document["reason"])
        self.assertEqual("OPEN", document["status"])

    def test_dated_ecosystem_analysis_is_preserved_as_history(self):
        path = "docs/ECOSYSTEM-ARCHITECTURE.md"
        self.write(self.roots[0], path, "# 2026-10-06 ecosystem checkout audit\n")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path)
        self.assertEqual("historical-document", row["category"])
        self.assertEqual("HISTORICAL", row["status"])

    def test_dated_release_and_audit_documents_are_preserved_as_history(self):
        paths = (
            "docs/RELEASE-NOTES-3.0.x-2026-09.md",
            "docs/architecture/cloud-agents-migration-evidence.md",
            "docs/architecture/cloud-exception-contract-audit.md",
            "docs/architecture/cloud-tenant-context-contract-audit.md",
            "docs/architecture/shiro-http-login-audit.md",
            "docs/architecture/shiro-real-provider-audit.md",
            "docs/migrations/truelicense4-framework-consumer-audit.md",
        )
        for path in paths:
            self.write(self.roots[2] if "RELEASE-NOTES" in path else self.roots[0],
                       path, "# Dated audit\n")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {item["path"]: item for item in
                json.loads((output / "inventory.json").read_text())["differences"]}
        for path in paths:
            with self.subTest(path=path):
                self.assertEqual("historical-document", rows[path]["category"])
                self.assertEqual("HISTORICAL", rows[path]["status"])

    def test_old_target_class_does_not_count_as_current_api(self):
        for root in self.roots:
            self.write(root, "ddd4j-core/target/classes/example/Old.class", "stale")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "inventory.json").read_text())
        self.assertFalse(any("target/classes" in row["path"] for row in report["differences"]))


if __name__ == "__main__":
    unittest.main()
