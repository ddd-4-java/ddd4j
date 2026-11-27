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
    def test_inherited_web_contract_requires_each_line_contract_hash(self):
        test = "ddd4j-web/demo/src/test/java/example/AdapterWebContractTest.java"
        contract = "ddd4j-web/testkit/src/main/java/example/AbstractWebContractTest.java"
        production = "ddd4j-web/demo/src/main/java/example/WebAdapter.java"
        adapters = [("class AdapterWebContractTest extends AbstractWebContractTest {\n"
                     f"WebContractClient client() {{ return null; }} // {jdk}\n}}")
                    for jdk in (8, 17, 21)]
        bases = ["class AbstractWebContractTest {\n@Test\nvoid shouldHandleRequest() {}\n}",
                 "class AbstractWebContractTest {\n@Test\nvoid shouldHandleRequest() { /*17*/ }\n}",
                 "class AbstractWebContractTest {\n@Test\nvoid shouldHandleRequest() { /*21*/ }\n}"]
        for root, base, adapter in zip(self.roots, bases, adapters):
            self.write(root, test, adapter)
            self.write(root, contract, base)
            self.write(root, production, "class WebAdapter {}")
        digest = lambda source: hashlib.sha256(source.encode()).hexdigest()
        policy = json.loads(self.policy.read_text())
        policy["platformTestSyntaxMappings"] = [{
            "path": test,
            "productionPath": production,
            "sourceSha256": [digest(adapter) for adapter in adapters],
            "productionSha256": [digest("class WebAdapter {}")]*3,
            "contractPath": contract,
            "contractSha256ByJdk": dict(zip(("8", "17", "21"), map(digest, bases))),
            "testMethods": ["shouldHandleRequest"],
            "reason": "Three adapters inherit the same request contract",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                                  if row["path"] == test))

        self.write(self.roots[2], contract, bases[2] + " // changed")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale inherited contract mapping", result.stderr)

    def test_java8_map_contract_only_normalizes_exact_approved_call(self):
        path = ("ddd4j-samples/ddd4j-sample-javalin-satoken/src/test/java/"
                "io/ddd4j/sample/javalin/satoken/rbac/web/Java8MapsContractTest.java")
        evidence = ("ddd4j-samples/ddd4j-sample-javalin-satoken/src/test/java/"
                    "io/ddd4j/sample/javalin/satoken/rbac/web/AuthControllerTest.java")
        before = 'Java8Maps.of("name", "alice", "count", 2)'
        after = 'Map.of("name", "alice", "count", 2)'
        original = "class Java8MapsContractTest { String value = " + before + "; }"
        for index, root in enumerate(self.roots):
            self.write(root, path, original if index == 0 else original.replace(before, after))
            self.write(root, evidence, "class AuthControllerTest {}")
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path,
            "replacements": [{"from": before, "to": after}],
            "evidence": evidence,
            "reason": "JDK 8 helper and JDK Map.of have the same output contract",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                                  if row["path"] == path))

        self.write(self.roots[1], path, original.replace(before, after) + " // changed")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == path))

    def test_platform_test_helper_requires_exact_sources_and_shared_http_contract(self):
        helper = "ddd4j-samples/demo/src/test/java/example/TestSupport.java"
        contract = "ddd4j-samples/demo/src/test/java/example/OrderHttpTest.java"
        sources = ["class TestSupport { int port() { return 8; } }",
                   "class TestSupport { int port() { return 17; } }",
                   "class TestSupport { int port() { return 21; } }"]
        test_source = "import org.junit.jupiter.api.Test;\nclass OrderHttpTest {\n@Test\nvoid order() {}\n}"
        for root, source in zip(self.roots, sources):
            self.write(root, helper, source)
            self.write(root, contract, test_source)
        policy = json.loads(self.policy.read_text())
        policy["platformTestHelperMappings"] = [{
            "path": helper,
            "sourceSha256": [hashlib.sha256(source.encode()).hexdigest() for source in sources],
            "contractPath": contract,
            "contractSha256": [hashlib.sha256(test_source.encode()).hexdigest()] * 3,
            "testMethods": ["order"],
            "reason": "Three platform test adapters run one shared HTTP contract",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                                  if row["path"] == helper))

        self.write(self.roots[1], helper, sources[1] + " // changed")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform test helper mapping", result.stderr)

        self.write(self.roots[1], helper, sources[1])
        (self.roots[2] / contract).unlink()
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform test helper mapping", result.stderr)

    def test_platform_tool_script_requires_exact_line_and_content(self):
        path = "scripts/test-maven-wrapper-jdk-compatibility.sh"
        source = "#!/bin/sh\nexit 0\n"
        self.write(self.roots[0], path, source)
        policy = json.loads(self.policy.read_text())
        policy["platformToolScripts"] = [{
            "path": path,
            "sha256ByJdk": {"8": hashlib.sha256(source.encode()).hexdigest(),
                            "17": None, "21": None},
            "evidence": "JDK 8 Maven Wrapper compatibility check",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_CONFIG", next(row["status"] for row in rows
                                                  if row["path"] == path))

        self.write(self.roots[0], path, source + "# changed\n")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform tool script", result.stderr)

    def test_maven4_tool_script_cannot_be_reassigned_to_jdk8(self):
        path = "scripts/test_maven4_model_contract.py"
        source = "print('maven 4')\n"
        self.write(self.roots[2], path, source)
        policy = json.loads(self.policy.read_text())
        digest = hashlib.sha256(source.encode()).hexdigest()
        policy["platformToolScripts"] = [{
            "path": path,
            "sha256ByJdk": {"8": None, "17": None, "21": digest},
            "evidence": "Maven 4 model contract check",
        }]
        self.policy.write_text(json.dumps(policy))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_CONFIG", next(row["status"] for row in rows
                                                  if row["path"] == path))

        policy["platformToolScripts"][0]["sha256ByJdk"] = {
            "8": digest, "17": None, "21": None}
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid platform tool script hashes", result.stderr)

    def test_dependency_policy_tests_require_all_three_locked_variants(self):
        path = "scripts/dependencies/tests/test_uniform_contract.py"
        sources = [f"def test_jdk{jdk}(): pass\n" for jdk in (8, 17, 21)]
        for root, source in zip(self.roots, sources):
            self.write(root, path, source)
        policy = json.loads(self.policy.read_text())
        policy["platformToolScripts"] = [{
            "path": path,
            "sha256ByJdk": {jdk: hashlib.sha256(source.encode()).hexdigest()
                            for jdk, source in zip(("8", "17", "21"), sources)},
            "evidence": "line-specific dependency policy assertions",
        }]
        self.policy.write_text(json.dumps(policy))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_CONFIG", next(row["status"] for row in rows
                                                  if row["path"] == path))

        (self.roots[1] / path).unlink()
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform tool script", result.stderr)

    def test_helidon_cdi_service_descriptor_tracks_namespace_without_missing_provider(self):
        prefix = "ddd4j-runtime/ddd4j-runtime-helidon/src/main/resources/META-INF/services/"
        legacy = prefix + "javax.enterprise.inject.spi.Extension"
        modern = prefix + "jakarta.enterprise.inject.spi.Extension"
        provider = "io.ddd4j.helidon.Ddd4jHelidonExtension\n"
        self.write(self.roots[0], legacy, provider)
        self.write(self.roots[1], modern, provider)
        self.write(self.roots[2], modern, provider)

        def status(path):
            result, output = self.run_checker("inventory")
            self.assertEqual(0, result.returncode, result.stderr)
            rows = json.loads((output / "inventory.json").read_text())["differences"]
            return next(row["status"] for row in rows if row["path"] == path)

        self.assertEqual("PLATFORM_ADAPTED", status(legacy))
        self.assertEqual("PLATFORM_ADAPTED", status(modern))
        self.write(self.roots[2], modern, "io.ddd4j.helidon.WrongExtension\n")
        self.assertEqual("OPEN", status(legacy))
        self.assertEqual("OPEN", status(modern))
        self.write(self.roots[2], modern, provider)
        (self.roots[1] / modern).unlink()
        self.assertEqual("OPEN", status(legacy))
        self.assertEqual("OPEN", status(modern))

    def test_known_cdi_descriptors_allow_only_namespace_and_schema_transition(self):
        paths = {
            "ddd4j-runtime/ddd4j-runtime-quarkus/src/main/resources/META-INF/beans.xml": ("4.0", "4_0"),
            "ddd4j-samples/ddd4j-sample-helidon-cqrs/src/main/resources/META-INF/beans.xml": ("3.0", "3_0"),
        }
        for path, (version, schema_suffix) in paths.items():
            legacy_schema = (
                ' xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
                'xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee '
                'http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd"'
                if "helidon-cqrs" in path else "")
            legacy = ('<beans xmlns="http://xmlns.jcp.org/xml/ns/javaee"'
                      f'{legacy_schema} bean-discovery-mode="annotated" version="2.0"/>')
            modern = ('<beans xmlns="https://jakarta.ee/xml/ns/jakartaee" '
                      'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
                      'xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee '
                      f'https://jakarta.ee/xml/ns/jakartaee/beans_{schema_suffix}.xsd" '
                      f'bean-discovery-mode="annotated" version="{version}"/>')
            for root, source in zip(self.roots, (legacy, modern, modern)):
                self.write(root, path, source)

        def status(path):
            result, output = self.run_checker("inventory")
            self.assertEqual(0, result.returncode, result.stderr)
            rows = json.loads((output / "inventory.json").read_text())["differences"]
            return next(row["status"] for row in rows if row["path"] == path)

        for path in paths:
            self.assertEqual("PLATFORM_ADAPTED", status(path))

        path = next(iter(paths))
        modern = (self.roots[1] / path).read_text()
        self.write(self.roots[1], path, modern.replace("/>", "<alternatives/></beans>"))
        self.assertEqual("OPEN", status(path))
        self.write(self.roots[1], path, modern.replace('annotated', 'all'))
        self.assertEqual("OPEN", status(path))
        self.write(self.roots[1], path, modern)
        (self.roots[2] / path).unlink()
        self.assertEqual("OPEN", status(path))

    def dropwizard_access_log_fixture(self):
        runtime = "ddd4j-runtime/ddd4j-runtime-dropwizard/src/"
        package = "java/io/ddd4j/dropwizard/"
        owner_sources = {
            runtime + "main/" + package + "Ddd4jLogbackAccessRequestLayout.java":
                "final class Ddd4jLogbackAccessRequestLayout {}",
            runtime + "main/" + package + "Ddd4jLogbackAccessRequestLogFactory.java":
                "public class Ddd4jLogbackAccessRequestLogFactory { public void extra() {} }",
            runtime + "test/" + package + "Ddd4jLogbackAccessRequestLogFactoryTest.java":
                "class Ddd4jLogbackAccessRequestLogFactoryTest {}",
        }
        counterpart_contents = {
            runtime + "main/" + package + "Ddd4jBundle.java": "class Ddd4jBundle {}",
            runtime + "test/" + package + "Ddd4jBundleTest.java": "class Ddd4jBundleTest {}",
            "ddd4j-samples/ddd4j-sample-dropwizard/src/test/java/"
            "io/ddd4j/sample/dropwizard/NativeDefaultTest.java": "class NativeDefaultTest {}",
            "ddd4j-samples/ddd4j-sample-dropwizard/src/main/java/"
            "io/ddd4j/sample/dropwizard/DropwizardOrderApplication.java":
                "class DropwizardOrderApplication {}",
            "ddd4j-samples/ddd4j-sample-dropwizard/src/main/resources/dropwizard-order.yml":
                "server:\n  requestLog:\n    appenders: []",
        }

        def sha(content):
            return hashlib.sha256(content.encode()).hexdigest()

        for path, content in owner_sources.items():
            self.write(self.roots[1], path, content)
        counterpart_hashes = {}
        for path, content in counterpart_contents.items():
            comment = "#" if path.endswith(".yml") else "//"
            per_line = {jdk: f"{content} {comment} {jdk}" for jdk in ("8", "17", "21")}
            for root, body in zip(self.roots, per_line.values()):
                self.write(root, path, body)
            counterpart_hashes[path] = {jdk: sha(body) for jdk, body in per_line.items()}
        policy = json.loads(self.policy.read_text())
        policy["platformFilesOnlyOnJdk17"] = {
            "sources": {path: sha(content) for path, content in owner_sources.items()},
            "counterparts": counterpart_hashes,
            "reason": "JDK 17 access logging adapter verified by three lines",
        }
        self.policy.write_text(json.dumps(policy))
        return policy, owner_sources, counterpart_contents

    def test_jdk17_dropwizard_access_log_files_leave_api_differences_independent(self):
        _, owner_sources, _ = self.dropwizard_access_log_fixture()
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        file_rows = [row for row in rows if row["path"] in owner_sources]
        self.assertEqual(3, len(file_rows))
        self.assertTrue(all(row["status"] == "PLATFORM_ADAPTED" for row in file_rows))
        api_rows = [row for row in rows if row["path"].endswith("#extra()")]
        self.assertEqual(1, len(api_rows))
        self.assertEqual("OPEN", api_rows[0]["status"])

    def test_jdk17_dropwizard_access_log_files_reject_stale_inputs(self):
        _, owner_sources, counterpart_contents = self.dropwizard_access_log_fixture()
        source_paths = list(owner_sources)
        contract = next(path for path in counterpart_contents if path.endswith("NativeDefaultTest.java"))
        sample_yaml = next(path for path in counterpart_contents if path.endswith("dropwizard-order.yml"))
        sample_application = next(path for path in counterpart_contents
                                  if path.endswith("DropwizardOrderApplication.java"))
        cases = (
            ("mutated production", self.roots[1], source_paths[0], "// changed"),
            ("mutated owner test", self.roots[1], source_paths[2], "// changed"),
            ("missing owner", self.roots[1], source_paths[1], None),
            ("appears on JDK 8", self.roots[0], source_paths[0], owner_sources[source_paths[0]]),
            ("appears on JDK 21", self.roots[2], source_paths[2], owner_sources[source_paths[2]]),
            ("mutated JDK 8 contract", self.roots[0], contract, "// changed"),
            ("missing JDK 21 contract", self.roots[2], contract, None),
            ("mutated native YAML", self.roots[2], sample_yaml, "server: {}"),
            ("mutated native startup", self.roots[1], sample_application, "class Other {}"),
        )
        for label, root, relative, new_content in cases:
            with self.subTest(label=label):
                path = root / relative
                previous = path.read_text() if path.is_file() else None
                if new_content is None:
                    path.unlink()
                else:
                    self.write(root, relative, new_content)
                try:
                    result, _ = self.run_checker("inventory")
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(f"stale JDK 17-only platform file: {relative}", result.stderr)
                finally:
                    if previous is None:
                        path.unlink()
                    else:
                        self.write(root, relative, previous)

    def test_jdk17_dropwizard_access_log_files_reject_broadened_policy(self):
        policy, owner_sources, counterpart_contents = self.dropwizard_access_log_fixture()
        mapping = policy["platformFilesOnlyOnJdk17"]
        original_source = next(iter(owner_sources))
        original_contract = next(iter(counterpart_contents))
        for field, original, replacement in (
            ("sources", original_source, original_source.replace(".java", "*.java")),
            ("sources", original_source, "ddd4j-core/src/main/java/example/Foo.java"),
            ("counterparts", original_contract, "ddd4j-core/src/test/java/example/FooTest.java"),
        ):
            with self.subTest(field=field, replacement=replacement):
                original_mapping = mapping[field].copy()
                mapping[field][replacement] = mapping[field].pop(original)
                self.policy.write_text(json.dumps(policy))
                try:
                    result, _ = self.run_checker("inventory")
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn("invalid JDK 17-only platform file mapping", result.stderr)
                finally:
                    mapping[field] = original_mapping

    def test_jdk8_only_test_requires_locked_counterpart_contract(self):
        low_only = "ddd4j-core/src/test/java/example/LegacyContainerIT.java"
        counterpart = "ddd4j-core/src/test/java/example/ContainerIT.java"
        low_source = "class LegacyContainerIT {}"
        contract_sources = [f"class ContainerIT {{ int line = {line}; }}"
                            for line in (8, 17, 21)]
        self.write(self.roots[0], low_only, low_source)
        for root, source in zip(self.roots, contract_sources):
            self.write(root, counterpart, source)
        policy = json.loads(self.policy.read_text())
        policy["platformTestsOnlyOnJdk8"] = [{
            "path": low_only,
            "sha256": hashlib.sha256(low_source.encode()).hexdigest(),
            "counterpart": counterpart,
            "counterpartSha256": [hashlib.sha256(source.encode()).hexdigest()
                                  for source in contract_sources],
            "reason": "低线专用容器夹具由三线共同契约覆盖",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                                  if row["path"] == low_only))

        self.write(self.roots[0], low_only, low_source + " changed")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale JDK 8-only platform test", result.stderr)

        self.write(self.roots[0], low_only, low_source)
        self.write(self.roots[2], counterpart, contract_sources[2] + " changed")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale JDK 8-only platform test", result.stderr)

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

    def test_indexed_raw_verification_records_require_locked_hashes(self):
        records = (
            (0, "docs/verification/redline-cleanup-evidence-20261009.txt", "redline evidence\n"),
            (2, "docs/verification/javadoc-gaps-raw.txt", "javadoc gaps\n"),
        )
        index_rows = []
        for line_index, path, content in records:
            self.write(self.roots[line_index], path, content)
            digest = hashlib.sha256(content.encode()).hexdigest()
            line = ("1.0.x", "2.0.x", "3.0.x")[line_index]
            index_rows.append(
                f"| `{Path(path).name}` | {line}；2026-10-10T18:12:52+08:00 | "
                f"[原始文件](https://github.com/ddd-4-java/ddd4j/blob/{'a' * 40}/{path}) | "
                f"`{digest}` |")
        index = "\n".join(index_rows) + "\n"
        for root in self.roots:
            self.write(root, "docs/superpowers/historical-evidence-index.md", index)

        _, output = self.run_checker("inventory")
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        for _, path, _ in records:
            self.assertEqual("HISTORICAL", rows[path])

        line_index, path, content = records[0]
        self.write(self.roots[line_index], path, content + "changed\n")
        _, output = self.run_checker("inventory")
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("OPEN", rows[path])
        self.assertEqual("HISTORICAL", rows[records[1][1]])

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
            # 临时产物遵守当前 class_files 的默认 reactor 根 POM 契约。
            self.write(root, "pom.xml", "<project><modules><module>ddd4j-core</module>"
                       "<module>ddd4j-samples/demo</module></modules></project>")
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

    def run_checker(self, phase, *extra_args):
        output = self.base / phase
        command = [sys.executable, str(SCRIPT), "--roots", *map(str, self.roots),
                   "--output", str(output), "--policy", str(self.policy), "--phase", phase,
                   *extra_args]
        result = subprocess.run(command, capture_output=True, text=True)
        return result, output

    def test_inventory_can_fail_fast_on_open_differences_and_keep_evidence(self):
        result, output = self.run_checker("inventory", "--fail-on-open")
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertTrue((output / "inventory.json").is_file())
        self.assertTrue((output / "differences.csv").is_file())
        self.assertTrue((output / "summary.md").is_file())
        self.assertTrue(any(row["status"] == "OPEN" for row in
                            json.loads((output / "inventory.json").read_text())["differences"]))

        for root in self.roots:
            self.write(root, "docs/guide.md", "same content\n")
        result, _ = self.run_checker("inventory", "--fail-on-open")
        self.assertEqual(1, result.returncode, result.stderr)
        for root in self.roots:
            self.write(root, "ddd4j-samples/demo/src/main/java/example/Demo.java", "class Demo {}")
            self.write(root, "ddd4j-core/src/test/java/example/FooTest.java", "class FooTest {}")
        result, _ = self.run_checker("inventory", "--fail-on-open")
        self.assertEqual(0, result.returncode, result.stderr)

    def test_scoped_compiled_api_ignores_other_modules_and_detects_real_drift(self):
        wrapper = ('#!/bin/sh\n'
                   'printf "%s\\n" "$@" > invoked.txt\n'
                   'mkdir -p ddd4j-core/target/classes\n'
                   '"$JAVA_HOME/bin/javac" -parameters -d ddd4j-core/target/classes '
                   'ddd4j-core/src/main/java/example/Foo.java\n')
        for root in self.roots:
            executable = root / "mvnw"
            executable.write_text(wrapper)
            executable.chmod(0o755)
        self.write(self.roots[1], "ddd4j-samples/demo/target/classes/example/Stale.class", "not a class")

        result, output = self.run_checker("verify", "--module", "ddd4j-core")
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual("PASS", report["status"])
        self.assertEqual("ddd4j-core", report["module"])
        self.assertEqual("module-compiled-api-only", report["scope"])
        self.assertEqual("NOT_RUN", report["fullConvergence"])
        self.assertEqual(["-B", "-ntp", "-pl", "ddd4j-core", "-am", "clean", "compile"],
                         (self.roots[0] / "invoked.txt").read_text().splitlines())

        self.write(self.roots[1], "ddd4j-core/src/main/java/example/Foo.java",
                   "package example; public class Foo { public String drift() { return \"x\"; } }")
        result, output = self.run_checker("verify", "--module", "ddd4j-core")
        self.assertEqual(1, result.returncode, result.stderr)
        report = json.loads((output / "compiled-api.json").read_text())
        self.assertEqual("FAIL", report["status"])
        self.assertTrue(report["unresolved"]["1-2"]["only_right"])

    def test_scoped_api_rejects_invalid_module_or_phase(self):
        for arguments in (("verify", "--module", "../ddd4j-core"),
                          ("inventory", "--module", "ddd4j-core")):
            result, _ = self.run_checker(*arguments)
            self.assertEqual(2, result.returncode)
            self.assertIn("invalid module for scoped verification", result.stderr)

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
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        inventory = json.loads((output / "inventory.json").read_text())
        source_row = next(row for row in inventory["differences"] if row["path"] == symbol)
        self.assertEqual("PLATFORM_API_PENDING_COMPILE", source_row["status"])
        self.assertEqual("NOT_RUN", inventory["compiledApi"]["status"])

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

    def test_jdk17_quarkus_manager_must_keep_cron_contract(self):
        resource = "ddd4j-runtime/ddd4j-runtime-quarkus/src/main/resources/application.properties"
        counterpart = "ddd4j-runtime/demo/src/main/java/example/ProjectionService.java"
        evidence = "ddd4j-runtime/demo/src/test/java/example/ProjectionContractTest.java"
        manager = ("ddd4j-runtime/ddd4j-runtime-quarkus/src/main/java/"
                   "io/ddd4j/quarkus/cqrs/QuarkusJpaViewManager.java")
        prop = "ddd4j.quarkus.cqrs.projection.cron"
        cron = "*/30 * * * * ?"
        config = f"{prop}={cron}\n"
        self.write(self.roots[0], resource, config)
        self.write(self.roots[0], counterpart, f'class ProjectionService {{ @Scheduled(cron = "{{{prop}}}") void run() {{}} }}')
        self.write(self.roots[1], counterpart, "class ProjectionService {}")
        annotation = '@Scheduled(cron = "$' + f'{{{prop}:{cron}}}")'
        self.write(self.roots[2], counterpart, "class ProjectionService { " + annotation + " void run() {} }")
        for root in self.roots:
            self.write(root, evidence, "class ProjectionContractTest {}")
        source = (
            'class QuarkusJpaViewManager {\n'
            f'String CRON_KEY = "{prop}";\n'
            f'String DEFAULT_CRON = "{cron}";\n'
            'void run() {\n'
            'config.getOptionalValue(CRON_KEY, String.class).orElse(DEFAULT_CRON);\n'
            'scheduler.schedule("ddd4j-legacy-projection", cron, action);\n'
            '}\n}\n'
        )
        self.write(self.roots[1], manager, source)
        policy = json.loads(self.policy.read_text())
        policy["platformResourcesOnlyOnJdk8"] = [{
            "path": resource, "sha256": hashlib.sha256(config.encode()).hexdigest(),
            "counterpart": counterpart, "evidence": evidence, "jdk17Manager": manager,
            "property": prop, "default": cron, "reason": "Keep the same Quartz cron contract",
        }]
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)

        self.write(self.roots[1], manager, source.replace(cron, "0 * * * * ?"))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale JDK 8-only platform resource evidence", result.stderr)

    def test_jdk8_only_quarkus_cron_resource_requires_same_inline_default(self):
        resource = "ddd4j-runtime/ddd4j-runtime-quarkus/src/main/resources/application.properties"
        counterpart = ("ddd4j-runtime/ddd4j-runtime-quarkus/src/main/java/"
                       "example/ProjectionService.java")
        evidence = ("ddd4j-runtime/ddd4j-runtime-quarkus/src/test/java/"
                    "example/ProjectionContractTest.java")
        property_name = "ddd4j.quarkus.cqrs.projection.cron"
        default = "*/30 * * * * ?"
        content = f"{property_name}={default}\n"
        self.write(self.roots[0], resource, content)
        self.write(self.roots[0], counterpart,
                   f'@Scheduled(cron = "{{{property_name}}}")')
        for root in self.roots[1:]:
            self.write(root, counterpart,
                       f'@Scheduled(cron = "${{{property_name}:{default}}}")')
        for root in self.roots:
            self.write(root, evidence, "class ProjectionContractTest {}")
        policy = json.loads(self.policy.read_text())
        policy["platformResourcesOnlyOnJdk8"] = [{
            "path": resource, "sha256": hashlib.sha256(content.encode()).hexdigest(),
            "counterpart": counterpart, "evidence": evidence,
            "property": property_name, "default": default,
            "reason": "Quarkus cron default moved from resource to annotation",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                                  if row["path"] == resource))

        self.write(self.roots[2], counterpart,
                   f'@Scheduled(cron = "${{{property_name}:0 * * * * ?}}")')
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale JDK 8-only platform resource evidence", result.stderr)

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

    def test_reports_all_stale_dependency_policy_bindings_before_inventory(self):
        config_paths = (
            "config/dependencies/alignment-policy.json",
            "config/dependencies/component-matrix.csv",
        )
        test_path = "scripts/dependencies/tests/test_uniform_contract.py"
        policy = json.loads(self.policy.read_text())
        jdks = ("8", "17", "21")
        mappings = []
        for path in (*config_paths, test_path):
            contents = tuple(f"{path} on JDK {jdk}\n" for jdk in jdks)
            for root, content in zip(self.roots, contents):
                self.write(root, path, content)
            mappings.append({
                "path": path,
                "sha256ByJdk": {
                    jdk: hashlib.sha256(content.encode()).hexdigest()
                    for jdk, content in zip(jdks, contents)
                },
                "evidence": "locked platform input",
            })
        policy["platformDependencyConfigFiles"] = mappings[:2]
        policy["platformToolScripts"] = mappings[2:]
        self.policy.write_text(json.dumps(policy))

        self.write(self.roots[0], config_paths[0], "changed JDK 8 policy\n")
        self.write(self.roots[1], config_paths[1], "changed JDK 17 matrix\n")
        self.write(self.roots[2], config_paths[1], "changed JDK 21 matrix\n")
        (self.roots[0] / test_path).unlink()
        result, output = self.run_checker("inventory")

        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"stale platform dependency config mapping: {config_paths[0]} (JDK 8)", result.stderr)
        self.assertIn(f"stale platform dependency config mapping: {config_paths[1]} (JDK 17, 21)", result.stderr)
        self.assertIn(f"stale platform tool script: {test_path} (JDK 8)", result.stderr)
        self.assertFalse((output / "inventory.json").exists())

    def test_license_governance_files_require_exact_line_hashes(self):
        paths = ("config/license-selections.tsv", "config/license-build-tool-exclusions.tsv")
        policy = json.loads(self.policy.read_text())
        mappings = []
        for path in paths:
            contents = tuple(f"{path}:jdk{jdk}\n" for jdk in (8, 17, 21))
            for root, content in zip(self.roots, contents):
                self.write(root, path, content)
            mappings.append({
                "path": path,
                "sha256ByJdk": {jdk: hashlib.sha256(content.encode()).hexdigest()
                                for jdk, content in zip(("8", "17", "21"), contents)},
                "evidence": "line-specific license audit",
            })
        policy["platformDependencyConfigFiles"] = mappings
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {item["path"]: item for item in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertTrue(all(rows[path]["status"] == "PLATFORM_CONFIG" for path in paths))

        self.write(self.roots[1], paths[0], "unexpected\n")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform dependency config mapping", result.stderr)

        policy["platformDependencyConfigFiles"][0]["path"] = "config/unrelated.tsv"
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid platform dependency config mapping", result.stderr)

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

    def test_jdk21_license_header_mapping_requires_exact_content(self):
        path = "config/apache-2.0-header.txt"
        content = "Copyright (c) ddd4j\n"
        self.write(self.roots[2], path, content)
        policy = json.loads(self.policy.read_text())
        policy["platformBuildConfigFiles"] = [{
            "path": path,
            "sha256ByJdk": {
                "8": None,
                "17": None,
                "21": hashlib.sha256(content.encode()).hexdigest(),
            },
            "evidence": "JDK 21 Maven license plugin references this header",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_CONFIG", row["status"])
        self.write(self.roots[2], path, "changed\n")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)

    def test_panache_projection_config_requires_exact_path_and_hashes(self):
        path = "ddd4j-data/ddd4j-data-event-store-panache/src/test/resources/application.properties"
        neighbor = path.replace("ddd4j-data-event-store-panache", "ddd4j-data-event-store-jpa")
        base = "quarkus.datasource.db-kind=h2\n"
        contents = (base + "quarkus.index-dependency.ddd4j-core.group-id=io.ddd4j\n"
                    + "quarkus.index-dependency.ddd4j-core.artifact-id=ddd4j-core\n", base, base)
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
            self.write(root, neighbor, content)
        policy = json.loads(self.policy.read_text())
        mapping = {
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "Quarkus 1 requires the core Jandex index for the legacy hierarchy",
        }
        policy["platformBuildConfigFiles"] = [mapping]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("PLATFORM_CONFIG", rows[path]["status"])
        self.assertEqual("OPEN", rows[neighbor]["status"])

        self.write(self.roots[0], path, base)
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"stale platform build config mapping: {path}", result.stderr)

        mapping["path"] = neighbor
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"invalid platform build config mapping: {neighbor}", result.stderr)

    def test_quarkus_cqrs_packaged_config_requires_exact_path_and_hashes(self):
        path = "ddd4j-samples/ddd4j-sample-quarkus-cqrs/src/main/resources/application.properties"
        neighbor = path.replace("sample-quarkus-cqrs", "sample-quarkus")
        mode_property = "ddd4j.quarkus.cqrs.projection.mode=durable\n"
        contents = ("%durable." + mode_property, mode_property, mode_property)
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
            self.write(root, neighbor, content)
        policy = json.loads(self.policy.read_text())
        mapping = {
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "Exact Quarkus CQRS packaged profiles",
        }
        policy["platformBuildConfigFiles"] = [mapping]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {entry["path"]: entry for entry in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("PLATFORM_CONFIG", rows[path]["status"])
        self.assertEqual("OPEN", rows[neighbor]["status"])

        self.write(self.roots[0], path, mode_property)
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"stale platform build config mapping: {path}", result.stderr)

        mapping["path"] = neighbor
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"invalid platform build config mapping: {neighbor}", result.stderr)

    def test_framework_resource_mappings_require_exact_line_inputs(self):
        resources = (
            ("ddd4j-data/ddd4j-data-event-store-jpa/src/test/resources/META-INF/persistence.xml",
             (None, "jakarta persistence\n", "jakarta persistence\n")),
            ("ddd4j-data/ddd4j-data-event-store-panache/src/main/resources/META-INF/beans.xml",
             ("javaee panache\n", None, None)),
            ("ddd4j-samples/ddd4j-sample-helidon/src/main/resources/META-INF/beans.xml",
             ("javaee helidon\n", None, None)),
        )
        policy = json.loads(self.policy.read_text())
        policy["platformBuildConfigFiles"] = []
        for path, contents in resources:
            for root, content in zip(self.roots, contents):
                if content is not None:
                    self.write(root, path, content)
            policy["platformBuildConfigFiles"].append({
                "path": path,
                "sha256ByJdk": {
                    jdk: hashlib.sha256(content.encode()).hexdigest() if content else None
                    for jdk, content in zip(("8", "17", "21"), contents)
                },
                "evidence": "Framework generation requires this exact resource",
            })
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {item["path"]: item for item in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertTrue(all(rows[path]["status"] == "PLATFORM_CONFIG"
                            for path, _ in resources))
        self.write(self.roots[0], resources[1][0], "changed\n")
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)

    def test_quarkus_arch_namespace_mapping_rejects_changed_rule(self):
        path = ("ddd4j-runtime/ddd4j-runtime-quarkus/src/test/java/"
                "io/ddd4j/quarkus/command/arch/CqrsQuarkusModuleIndependenceTest.java")
        contents = (
            '@since 1.0.x\n@ArchTest "javax.." Javax API\n"jakarta.persistence.."\n"javax.persistence.."\n',
            '@since 2.0.x\n@ArchTest "jakarta.." Jakarta API\n"jakarta.persistence.."\n"javax.persistence.."\n',
            '@since 2.0.x\n@ArchTest "jakarta.." Jakarta API\n"jakarta.persistence.."\n"javax.persistence.."\n',
        )
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
        policy = json.loads(self.policy.read_text())
        policy["platformTestNamespaceMappings"] = [{
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "Quarkus Java EE/Jakarta architecture rule",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        changed = contents[1].replace("jakarta..", "io.spring..")
        self.write(self.roots[1], path, changed)
        policy["platformTestNamespaceMappings"][0]["sha256ByJdk"]["17"] = (
            hashlib.sha256(changed.encode()).hexdigest())
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("namespace contract differs", result.stderr)

        policy["platformTestNamespaceMappings"][0]["path"] = "other/UnrelatedTest.java"
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid platform test namespace mapping", result.stderr)

    def test_quarkus_arch_namespace_mapping_requires_both_persistence_packages(self):
        path = ("ddd4j-runtime/ddd4j-runtime-quarkus/src/test/java/"
                "io/ddd4j/quarkus/command/arch/CqrsQuarkusModuleIndependenceTest.java")
        contents = (
            '@since 1.0.x\n@ArchTest "javax.." Javax API\n'
            '"jakarta.persistence.."\n"javax.persistence.."\n',
            '@since 2.0.x\n@ArchTest "jakarta.." Jakarta API\n'
            '"jakarta.persistence.."\n"javax.persistence.."\n',
            '@since 2.0.x\n@ArchTest "jakarta.." Jakarta API\n'
            '"jakarta.persistence.."\n"javax.persistence.."\n',
        )
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
        policy = json.loads(self.policy.read_text())
        policy["platformTestNamespaceMappings"] = [{
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "Quarkus CDI and JPA namespace architecture rule",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_ADAPTED", row["status"])

        changed = contents[0].replace('"jakarta.persistence.."', '"jakarta.other.."')
        self.write(self.roots[0], path, changed)
        policy["platformTestNamespaceMappings"][0]["sha256ByJdk"]["8"] = (
            hashlib.sha256(changed.encode()).hexdigest())
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("namespace contract differs", result.stderr)

    def test_workflow_version_adaptation_requires_exact_files_and_line_markers(self):
        path = ".github/workflows/deploy.yml"
        contents = tuple(
            f"name: Set up JDK {jdk}\njava-version: '{jdk}'\n"
            f"spring-extension/{line}.20260630-SNAPSHOT\n"
            for jdk, line in ((8, "1.0.x"), (17, "2.0.x"), (21, "3.0.x"))
        )
        for root, content in zip(self.roots, contents):
            self.write(root, path, content)
        policy = json.loads(self.policy.read_text())
        policy["platformWorkflowFiles"] = [{
            "path": path,
            "sha256ByJdk": {
                jdk: hashlib.sha256(content.encode()).hexdigest()
                for jdk, content in zip(("8", "17", "21"), contents)
            },
            "evidence": "Line-specific JDK and Easy4J snapshot; workflow lint runs separately",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        row = next(item for item in json.loads((output / "inventory.json").read_text())
                   ["differences"] if item["path"] == path)
        self.assertEqual("PLATFORM_CONFIG", row["status"])

        self.write(self.roots[2], path, contents[2].replace("3.0.x", "2.0.x"))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)

        changed = contents[2].replace("JDK 21", "JDK 17")
        self.write(self.roots[2], path, changed)
        policy["platformWorkflowFiles"][0]["sha256ByJdk"]["21"] = hashlib.sha256(changed.encode()).hexdigest()
        self.policy.write_text(json.dumps(policy))
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

    def test_dropwizard_import_relocation_requires_exact_approved_type(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        old = ("package example;\nimport io.dropwizard.Application;\n"
               "public class Foo { Application<?> app; int size() { return 1; } }\n")
        modern = old.replace("io.dropwizard.Application", "io.dropwizard.core.Application")
        for root, source in zip(self.roots, (old, modern, modern)):
            self.write(root, path, source)

        def status():
            result, output = self.run_checker("inventory")
            self.assertEqual(0, result.returncode, result.stderr)
            rows = json.loads((output / "inventory.json").read_text())["differences"]
            return next(row["status"] for row in rows
                        if row["path"] == path and row["category"] == "production")

        self.assertEqual("OPEN", status())
        evidence = "ddd4j-core/src/test/java/example/DropwizardTest.java"
        for root in self.roots:
            self.write(root, evidence, "class DropwizardTest {}")
        policy = json.loads(self.policy.read_text())
        policy["typeMappings"] = [{"id": "dropwizard-application-package",
                                   "types": {"1.0.x": "io.dropwizard.Application",
                                             "2.0.x": "io.dropwizard.core.Application",
                                             "3.0.x": "io.dropwizard.core.Application"},
                                   "reason": "exact relocation", "evidence": evidence}]
        self.policy.write_text(json.dumps(policy))
        self.assertEqual("PLATFORM_ADAPTED", status())

        self.write(self.roots[2], path, modern.replace("return 1", "return 2"))
        self.assertEqual("OPEN", status())
        self.write(self.roots[2], path, modern)
        unapproved = old.replace("io.dropwizard.Application", "io.dropwizard.Other").replace(
            "Application<?> app", "Other<?> app")
        for root, source in zip(self.roots, (unapproved,
                                             unapproved.replace("io.dropwizard.Other", "io.dropwizard.core.Other"),
                                             unapproved.replace("io.dropwizard.Other", "io.dropwizard.core.Other"))):
            self.write(root, path, source)
        self.assertEqual("OPEN", status())

    def test_unused_explicit_import_does_not_create_structure_difference(self):
        path = "ddd4j-core/src/main/java/example/Foo.java"
        source = "package example;\npublic class Foo { int size() { return 1; } }\n"
        self.write(self.roots[0], path, source)
        self.write(self.roots[1], path, source.replace("public class", "import java.util.Map;\npublic class"))
        self.write(self.roots[2], path, source)

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("LEXICALLY_EQUIVALENT", next(row["status"] for row in rows
                                               if row["path"] == path and row["category"] == "production"))

        used = source.replace("int size() { return 1; }", "Map size() { return null; }")
        for root in self.roots:
            self.write(root, path, used)
        self.write(self.roots[1], path, used.replace("public class", "import java.util.Map;\npublic class"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                                      if row["path"] == path and row["category"] == "production"))

        for extra_import in ("import static java.util.Map.of;", "import java.util.*;"):
            for root in self.roots:
                self.write(root, path, source)
            self.write(self.roots[1], path, source.replace("public class", extra_import + "\npublic class"))
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
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        inventory = json.loads((output / "inventory.json").read_text())
        source_row = next(row for row in inventory["differences"] if row["path"] == source_symbol)
        self.assertEqual("PLATFORM_API_PENDING_COMPILE", source_row["status"])
        self.assertEqual("NOT_RUN", inventory["compiledApi"]["status"])

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
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == source_symbol))
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

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        inventory = json.loads((output / "inventory.json").read_text())
        source_row = next(row for row in inventory["differences"] if row["path"] == source_symbol)
        self.assertEqual("PLATFORM_API_PENDING_COMPILE", source_row["status"])
        self.assertEqual("NOT_RUN", inventory["compiledApi"]["status"])

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
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == source_symbol))
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

    def test_exact_platform_test_adaptations_keep_other_test_content_identical(self):
        cases = [
            ("ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/projection/jpa/arch/ProjectionJpaModuleIndependenceTest.java",
             '                            "javax..",\n', "", ["2.0.x", "3.0.x"]),
            ("ddd4j-runtime/ddd4j-runtime-helidon/src/test/java/io/ddd4j/helidon/HelidonServerBootTest.java",
             "server.cdiContainer().select(RuntimeReadinessRegistry.class).get()",
             "jakarta.enterprise.inject.spi.CDI.current().select(RuntimeReadinessRegistry.class).get()",
             ["2.0.x", "3.0.x"]),
            ("ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/SpringJpaProjectionPositionTestApp.java",
             "import org.springframework.boot.autoconfigure.domain.EntityScan;",
             "import org.springframework.boot.persistence.autoconfigure.EntityScan;",
             ["3.0.x"]),
            ("ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/projection/jpa/TestApp.java",
             "import org.springframework.boot.autoconfigure.domain.EntityScan;",
             "import org.springframework.boot.persistence.autoconfigure.EntityScan;",
             ["3.0.x"]),
        ]
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = []
        for path, before, after, target_lines in cases:
            original = "class Contract {\n" + before + "void same() {}\n}\n"
            for index, root in enumerate(self.roots):
                self.write(root, path, original.replace(before, after)
                           if ("1.0.x", "2.0.x", "3.0.x")[index] in target_lines
                           else original)
            policy["platformContentMappings"].append({
                "path": path, "replacements": [{"from": before, "to": after}],
                "targetLines": target_lines, "evidence": path,
                "reason": "exact platform test API adaptation",
            })
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        for path, _, _, _ in cases:
            self.assertEqual("PLATFORM_ADAPTED", rows[path])

        path = cases[1][0]
        self.write(self.roots[2], path, (self.roots[2] / path).read_text().replace("same", "changed"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("OPEN", rows[path])

    def test_jackson_three_projection_run_info_mapping_rejects_other_changes(self):
        path = "ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunInfoValueContractTest.java"
        replacements = [
            ("import com.fasterxml.jackson.databind.ObjectMapper;",
             "import tools.jackson.databind.json.JsonMapper;"),
            ("ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();",
             "JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();"),
        ]
        original = "class Contract {\n" + "\n".join(pair[0] for pair in replacements) + "\nvoid same() {}\n}\n"
        adapted = original
        for before, after in replacements:
            adapted = adapted.replace(before, after)
        for root, value in zip(self.roots, (original, original, adapted)):
            self.write(root, path, value)
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path,
            "replacements": [{"from": before, "to": after} for before, after in replacements],
            "targetLines": ["3.0.x"], "evidence": path,
            "reason": "Jackson 3 mapper construction in a common projection value test",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("PLATFORM_ADAPTED", rows[path])

        self.write(self.roots[2], path, adapted.replace("same", "changed"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("OPEN", rows[path])

    def test_rabbitmq_container_import_adapts_only_jdk17_line(self):
        path = "ddd4j-mq/ddd4j-mq-rabbitmq/src/test/java/io/ddd4j/mq/rabbitmq/RabbitMQContainerIntegrationTest.java"
        before = "import org.testcontainers.rabbitmq.RabbitMQContainer;"
        after = "import org.testcontainers.containers.RabbitMQContainer;"
        original = before + "\nclass Contract { void same() {} }\n"
        for root, value in zip(self.roots, (original, original.replace(before, after), original)):
            self.write(root, path, value)
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = [{
            "path": path, "replacements": [{"from": before, "to": after}],
            "targetLines": ["2.0.x"], "evidence": path,
            "reason": "Testcontainers RabbitMQ package adaptation only on JDK 17 line",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("PLATFORM_ADAPTED", rows[path])

        self.write(self.roots[1], path, (self.roots[1] / path).read_text().replace("same", "changed"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("OPEN", rows[path])

    def test_jdk21_container_and_webflux_test_api_adaptations_are_exact(self):
        cases = [
            ("ddd4j-data/ddd4j-data-event-store-jdbi/src/test/java/io/ddd4j/data/event/store/jdbi/MysqlEventStoreContainerSupport.java", [
                ("import org.testcontainers.containers.MySQLContainer;", "import org.testcontainers.mysql.MySQLContainer;"),
                ("static final MySQLContainer<?> MYSQL = new MySQLContainer<>(", "static final MySQLContainer MYSQL = new MySQLContainer("),
            ]),
            ("ddd4j-web/ddd4j-web-webflux/src/test/java/io/ddd4j/web/webflux/ApiVersionWebFilterTest.java", [
                ("import com.fasterxml.jackson.databind.ObjectMapper;", "import tools.jackson.databind.json.JsonMapper;"),
                ("new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),",
                 "new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), JsonMapper.builder().build(),"),
            ]),
        ]
        policy = json.loads(self.policy.read_text())
        policy["platformContentMappings"] = []
        for path, replacements in cases:
            original = "\n".join(pair[0] for pair in replacements) + "\nclass Contract { void same() {} }\n"
            adapted = original
            for before, after in replacements:
                adapted = adapted.replace(before, after)
            for root, value in zip(self.roots, (original, original, adapted)):
                self.write(root, path, value)
            policy["platformContentMappings"].append({
                "path": path,
                "replacements": [{"from": before, "to": after} for before, after in replacements],
                "targetLines": ["3.0.x"], "evidence": path,
                "reason": "exact JDK 21 platform test API adaptation",
            })
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        for path, _ in cases:
            self.assertEqual("PLATFORM_ADAPTED", rows[path])

        path = cases[0][0]
        self.write(self.roots[2], path, (self.roots[2] / path).read_text().replace("same", "changed"))
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = {row["path"]: row["status"] for row in
                json.loads((output / "inventory.json").read_text())["differences"]}
        self.assertEqual("OPEN", rows[path])

    def test_changed_error_code_fails_inventory_gate(self):
        path = "ddd4j-core/src/main/java/example/OrderErrorCode.java"
        original = ('package example; public enum OrderErrorCode {'
                    ' NOT_FOUND("ORDER_NOT_FOUND");'
                    ' public final String code;'
                    ' OrderErrorCode(String code) { this.code = code; } }')
        for root in self.roots:
            self.write(root, path, original)
        self.write(self.roots[1], path,
                   original.replace("ORDER_NOT_FOUND", "ORDER_MISSING"))

        result, output = self.run_checker("inventory", "--fail-on-open")
        self.assertNotEqual(0, result.returncode)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        row = next(item for item in rows if item["path"] == path
                   and item["category"] == "production")
        self.assertEqual("OPEN", row["status"])
        self.assertEqual("content", row["reason"])

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

    def test_platform_test_syntax_requires_equal_methods_and_production_logic(self):
        path = "ddd4j-core/src/test/java/example/ResponseTest.java"
        production = "ddd4j-core/src/main/java/example/Response.java"
        tests = ["class ResponseTest {\n void value() { /* Jackson 2 */ }\n}\n",
                 "class ResponseTest {\n void value() { /* Jackson 2 */ }\n}\n",
                 "class ResponseTest {\n void value() { /* Jackson 3 */ }\n}\n"]
        implementation = "class Response { int value() { return 1; } }\n"
        for root, source in zip(self.roots, tests):
            self.write(root, path, source)
            self.write(root, production, implementation)
        policy = json.loads(self.policy.read_text())
        digest = hashlib.sha256(implementation.encode()).hexdigest()
        policy["platformTestSyntaxMappings"] = [{
            "path": path, "productionPath": production,
            "sourceSha256": [hashlib.sha256(source.encode()).hexdigest() for source in tests],
            "productionSha256": [digest] * 3,
            "testMethods": ["value"], "reason": "Jackson test API differs by JDK",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                         if row["path"] == path))

        self.write(self.roots[2], path, tests[2].replace("value()", "other()"))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform test syntax mapping", result.stderr)

        self.write(self.roots[2], path, tests[2])
        self.write(self.roots[2], production, "class Response { int value() { return 2; } }\n")
        policy["platformTestSyntaxMappings"][0]["productionSha256"][2] = hashlib.sha256(
            (self.roots[2] / production).read_bytes()).hexdigest()
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("platform test production logic differs", result.stderr)

    def test_platform_test_syntax_accepts_only_locked_production_syntax(self):
        path = "ddd4j-core/src/test/java/example/AdapterTest.java"
        production = "ddd4j-core/src/main/java/example/Adapter.java"
        tests = [f"class AdapterTest {{\n void value() {{ /* JDK {jdk} */ }}\n}}\n"
                 for jdk in (8, 17, 21)]
        sources = [f"class Adapter {{ int value() {{ return {jdk}; }} }}\n"
                   for jdk in (8, 17, 21)]
        for root, test_source, source in zip(self.roots, tests, sources):
            self.write(root, path, test_source)
            self.write(root, production, source)
        test_hashes = [hashlib.sha256(source.encode()).hexdigest() for source in tests]
        production_hashes = [hashlib.sha256(source.encode()).hexdigest() for source in sources]
        policy = json.loads(self.policy.read_text())
        policy["platformSyntaxMappings"] = [{
            "path": production, "sourceSha256": production_hashes,
            "evidence": path,
            "testSha256ByJdk": dict(zip(("8", "17", "21"), test_hashes)),
            "reason": "Version-specific implementation with locked contract tests",
        }]
        policy["platformTestSyntaxMappings"] = [{
            "path": path, "productionPath": production,
            "sourceSha256": test_hashes, "productionSha256": list(production_hashes),
            "testMethods": ["value"], "reason": "Version-specific test API",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                         if row["path"] == path))

        self.write(self.roots[2], production, sources[2].replace("return 21", "return 22"))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale platform test syntax mapping", result.stderr)

        policy["platformTestSyntaxMappings"][0]["productionSha256"][2] = hashlib.sha256(
            (self.roots[2] / production).read_bytes()).hexdigest()
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("platform test production logic differs", result.stderr)

    def test_platform_test_syntax_recognizes_unicode_java_method_names(self):
        path = "ddd4j-core/src/test/java/example/UnicodeTest.java"
        production = "ddd4j-core/src/main/java/example/Unicode.java"
        tests = [f"class UnicodeTest {{\n void 装配期冲突() {{ /* JDK {jdk} */ }}\n}}\n"
                 for jdk in (8, 17, 21)]
        implementation = "class Unicode {}\n"
        for root, source in zip(self.roots, tests):
            self.write(root, path, source)
            self.write(root, production, implementation)
        policy = json.loads(self.policy.read_text())
        policy["platformTestSyntaxMappings"] = [{
            "path": path, "productionPath": production,
            "sourceSha256": [hashlib.sha256(source.encode()).hexdigest() for source in tests],
            "productionSha256": [hashlib.sha256(implementation.encode()).hexdigest()] * 3,
            "testMethods": ["装配期冲突"], "reason": "Unicode Java identifier contract",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                         if row["path"] == path))

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

    def test_hashed_syntax_adaptation_locks_additional_shared_contracts(self):
        path = "ddd4j-core/src/main/java/example/Client.java"
        evidence = "ddd4j-core/src/test/java/example/ClientContractTest.java"
        additional = "ddd4j-core/src/test/java/example/ClientSelectorTest.java"
        sources = [f"class Client {{ int line = {jdk}; }}" for jdk in (8, 17, 21)]
        for root, source in zip(self.roots, sources):
            self.write(root, path, source)
            self.write(root, evidence, "class ClientContractTest {}")
            self.write(root, additional, "class ClientSelectorTest {}")
        policy = json.loads(self.policy.read_text())
        policy["platformSyntaxMappings"] = [{
            "path": path,
            "sourceSha256": [hashlib.sha256(source.encode()).hexdigest() for source in sources],
            "evidence": evidence,
            "testSha256": hashlib.sha256(b"class ClientContractTest {}").hexdigest(),
            "additionalEvidence": [{
                "path": additional,
                "testSha256": hashlib.sha256(b"class ClientSelectorTest {}").hexdigest(),
            }],
            "reason": "The shared contracts cover initialization and selectors",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                                             if row["path"] == path))

        self.write(self.roots[2], additional, "class ClientSelectorTest { /* changed */ }")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows if row["path"] == path))

        policy["platformSyntaxMappings"][0]["additionalEvidence"][0]["path"] = "../outside.java"
        self.policy.write_text(json.dumps(policy))
        result, _ = self.run_checker("inventory")
        self.assertNotEqual(0, result.returncode)

    def test_hashed_syntax_adaptation_accepts_line_specific_contract_tests(self):
        path = "ddd4j-core/src/main/java/example/PlatformType.java"
        evidence = "ddd4j-core/src/test/java/example/PlatformTypeTest.java"
        sources = [f"class PlatformType {{ int value() {{ return {jdk}; }} }}"
                   for jdk in (8, 17, 21)]
        tests = [f"class PlatformTypeTest {{ int jdk = {jdk}; }}"
                 for jdk in (8, 17, 21)]
        for root, source, test in zip(self.roots, sources, tests):
            self.write(root, path, source)
            self.write(root, evidence, test)
        policy = json.loads(self.policy.read_text())
        policy["platformSyntaxMappings"] = [{
            "path": path,
            "sourceSha256": [hashlib.sha256(source.encode()).hexdigest() for source in sources],
            "evidence": evidence,
            "testSha256ByJdk": {str(jdk): hashlib.sha256(test.encode()).hexdigest()
                                for jdk, test in zip((8, 17, 21), tests)},
            "reason": "Each platform runs its own locked contract test",
        }]
        self.policy.write_text(json.dumps(policy))

        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                         if row["path"] == path))
        self.assertEqual("PLATFORM_ADAPTED", next(row["status"] for row in rows
                         if row["path"] == evidence))

        self.write(self.roots[1], evidence, tests[1] + " // changed")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                         if row["path"] == path))
        self.assertEqual("OPEN", next(row["status"] for row in rows
                         if row["path"] == evidence))

        self.write(self.roots[1], evidence, tests[1])
        self.write(self.roots[1], path, sources[1] + " // changed")
        result, output = self.run_checker("inventory")
        self.assertEqual(0, result.returncode, result.stderr)
        rows = json.loads((output / "inventory.json").read_text())["differences"]
        self.assertEqual("OPEN", next(row["status"] for row in rows
                         if row["path"] == evidence))

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


    def test_micronaut_interceptor_mapping_rejects_unreviewed_paths_and_source_drift(self):
        spec = importlib.util.spec_from_file_location("parity_auth_path", SCRIPT)
        checker = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(checker)
        base = ("ddd4j-samples/ddd4j-sample-micronaut-satoken/src/main/java/"
                "io/ddd4j/sample/micronaut/satoken/NativeAuth")
        modern, legacy = base + "Filter.java", base + "Interceptor.java"
        evidence = "demo/src/test/java/example/HttpTest.java"
        for index, root in enumerate(self.roots):
            self.write(root, legacy if index == 0 else modern, str(index))
            self.write(root, evidence, "locked test")
        digest = lambda value: hashlib.sha256(value.encode()).hexdigest()
        row = {"path": modern, "jdk8Source": legacy, "evidence": evidence,
               "sourceSha256": [digest(str(index)) for index in range(3)],
               "testSha256": digest("locked test"), "reason": "native HTTP authentication"}
        checker.validate_platform_syntax_mappings([row], self.roots)
        self.assertTrue(checker.platform_syntax_matches(modern, self.roots, row))
        self.write(self.roots[0], legacy, "changed")
        self.assertFalse(checker.platform_syntax_matches(modern, self.roots, row))
        with self.assertRaisesRegex(ValueError, "invalid JDK 8 authentication source"):
            checker.validate_platform_syntax_mappings([dict(row, jdk8Source="../Other.java")], self.roots)
        self.write(self.roots[1], legacy, "unexpected duplicate")
        with self.assertRaisesRegex(ValueError, "invalid JDK 8 authentication source"):
            checker.validate_platform_syntax_mappings([row], self.roots)

    def test_unicode_documentation_preserves_tokens_without_accepting_delimiter_escapes(self):
        spec = importlib.util.spec_from_file_location("parity_lexical", SCRIPT)
        checker = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(checker)
        tokens = checker.java_lexical_tokens
        self.assertEqual(tokens("int value;"), tokens(r"/** \u4fdd\u5b58\uff0c\u3002 */ int value;"))
        for source in (r"// \u000a return false;", r"/* \u002a\u002f return true; */",
                       r"/* \u005c */ int value;", r'int \u4fdd;', r'String s = "\u4fdd";'):
            with self.subTest(source=source):
                self.assertIsNone(tokens(source))

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


class EnumPlatformWitnessTest(unittest.TestCase):
    """真实 javac AST 枚举证明只供精确的 Java8 平台成员规则使用。"""

    SOURCE = "ddd4j-core/src/main/java/example/Outer.java"
    OWNER = "example.Outer$Access"
    DECLARATION = ("enum|public|implements=RouteRole|interfaceImports=example.RouteRole|"
                   "constants=LOGIN,PAY,CANCEL")
    MEMBERS = ("field:LOGIN", "field:PAY", "field:CANCEL", "values()", "valueOf(String)")
    ENUM = ("package example; import example.RouteRole; public class Outer { "
            "public enum Access implements RouteRole { LOGIN, PAY, CANCEL; "
            "public static final Access ALIAS = PAY; } }")

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.roots = [self.base / name for name in ("jdk8", "jdk17", "jdk21")]
        spec = importlib.util.spec_from_file_location("enum_parity", SCRIPT)
        self.parity = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.parity)
        for root in self.roots:
            root.mkdir()
            (root / "mvnw").write_text("fixture; Maven is never executed")
            self.write(root, "ddd4j-core/src/main/java/example/RouteRole.java",
                       "package example; public interface RouteRole {}")
            self.write(root, self.SOURCE, "package example; public class Outer {}")
        self.write(self.roots[0], self.SOURCE, self.ENUM)

    @staticmethod
    def write(root, path, text):
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def symbols(self, owner=None, members=None):
        return [self.SOURCE + "#" + (owner or self.OWNER) + "#" + member
                for member in (members or self.MEMBERS)]

    def scan(self, root=None, symbols=None):
        return self.parity.source_api(root or self.roots[0], {self.SOURCE: "current"},
                                      self.base / "scanner", enum_symbols=symbols or self.symbols())

    def test_real_enum_constants_and_implicit_methods_use_binary_owner(self):
        declarations = self.scan()
        for symbol, member in zip(self.symbols(), self.MEMBERS):
            self.assertEqual(self.DECLARATION + "|member=" + member, declarations[symbol])
        normal = self.parity.source_api(self.roots[0], {self.SOURCE: "current"}, self.base / "normal")
        self.assertEqual(normal, {key: value for key, value in declarations.items()
                                  if key not in self.symbols()})
        self.assertFalse(any("field:ALIAS" in symbol for symbol in declarations))
        self.assertFalse(any("Outer.Access#values" in symbol for symbol in declarations))

    def test_enclosing_visibility_and_implicit_interface_enum_are_respected(self):
        self.write(self.roots[0], self.SOURCE,
                   "package example; public class Outer { "
                   "private enum Hidden { PAY } class PackageHidden { public enum Access { PAY } } "
                   "protected enum Protected { PAY } public interface Api { enum Public { PAY } } "
                   "public void local() { class Local { public enum Access { PAY } } } }")
        owners = ["example.Outer$Hidden", "example.Outer$PackageHidden$Access",
                  "example.Outer$Protected", "example.Outer$Api$Public", "example.Outer$Local$Access"]
        symbols = [self.SOURCE + "#" + owner + "#values()" for owner in owners]
        declarations = self.scan(symbols=symbols)
        self.assertNotIn(symbols[0], declarations)
        self.assertNotIn(symbols[1], declarations)
        self.assertNotIn(symbols[4], declarations)
        self.assertIn("enum|protected|", declarations[symbols[2]])
        self.assertIn("enum|public|", declarations[symbols[3]])

    def test_class_fields_and_explicit_lookalike_methods_are_not_enum_witnesses(self):
        self.write(self.roots[0], self.SOURCE,
                   "package example; public class Outer { public static class Access { "
                   "public static final Access PAY = new Access(); "
                   "public static Access[] values() { return null; } "
                   "public static Access valueOf(String name) { return null; } } }")
        declarations = self.scan()
        self.assertTrue(all(symbol not in declarations for symbol in self.symbols()))

    def test_unlisted_members_and_relocated_owner_have_no_witness(self):
        requested = self.symbols() + self.symbols(owner="example.Other$Access") + self.symbols(
            members=("field:ALIAS", "field:UNDECLARED", "missing()", "valueOf(Object)"))
        declarations = self.scan(symbols=requested)
        self.assertEqual(set(self.symbols()), set(requested) & declarations.keys())

    def test_every_member_pins_constant_set_and_interface_import(self):
        baseline = self.scan()
        for changed in (self.ENUM.replace("LOGIN, PAY, CANCEL", "LOGIN, PAY, CANCEL, EXTRA"),
                        self.ENUM.replace("LOGIN, PAY, CANCEL", "LOGIN, CANCEL, PAY"),
                        self.ENUM.replace("implements RouteRole", "implements OtherRole"),
                        self.ENUM.replace("import example.RouteRole", "import other.RouteRole")):
            with self.subTest(source=changed):
                self.write(self.roots[0], self.SOURCE, changed)
                current = self.scan()
                for symbol in self.symbols():
                    self.assertNotEqual(baseline[symbol], current.get(symbol))

    def compile_classes(self, root):
        classes = root / "ddd4j-core/target/classes"
        classes.mkdir(parents=True, exist_ok=True)
        for stale in classes.rglob("*.class"):
            stale.unlink()
        java = (os.environ.get("DDD4J_JDK21_HOME")
                or self.parity.run("/usr/libexec/java_home", "-v", "21").strip())
        sources = list((root / "ddd4j-core/src/main/java").rglob("*.java"))
        subprocess.run([java + "/bin/javac", "-parameters", "-d", str(classes), *map(str, sources)],
                       check=True, capture_output=True, text=True)
        return {"ddd4j-core|" + path.relative_to(classes).as_posix()[:-6]: path
                for path in classes.rglob("*.class")}

    def test_exact_platform_gate_requires_independent_source_and_compiled_absence(self):
        # Real bytes and real AST are independent inputs; no Maven or Javalin shadow types.
        self.write(self.roots[0], self.SOURCE, self.ENUM.replace("public static final Access ALIAS = PAY;", ""))
        classes = [self.compile_classes(root) for root in self.roots]
        low_api = self.parity.javap_api(classes[0])
        witnesses = self.scan()
        items = []
        evidence = "ddd4j-core/src/test/java/example/RoleContractTest.java"
        for root in self.roots:
            self.write(root, evidence, "class RoleContractTest {}")
        for member, symbol in zip(self.MEMBERS, self.symbols()):
            method = member.split("(", 1)[0]
            entry = next(value for value in low_api if value.startswith(
                "ddd4j-core|example/Outer$Access|" + method + "|"))
            items.append(dict(sourceSymbol=symbol, sourceDeclaration=self.DECLARATION + "|member=" + member,
                              compiledEntry=entry, evidence=evidence, reason="exact native enum binding"))
        policy = {"platformApiMembersOnlyOnJdk8": items}
        self.parity.validate_platform_api_mappings(items, self.roots)

        def gate():
            (self.base / "gate").mkdir(exist_ok=True)
            apis = [self.scan(root) for root in self.roots]
            rows = [{"category": "api", "path": symbol,
                     **{key: api.get(symbol, "MISSING") for key, api in zip(
                         ("jdk8", "jdk17", "jdk21"), apis)}} for symbol in self.symbols()]
            with mock.patch.object(self.parity, "compile_roots", return_value=[]), mock.patch.object(
                    self.parity, "class_files", side_effect=classes):
                self.parity.compiled_api_gate(self.roots, self.base / "gate", policy, rows)
            return json.loads((self.base / "gate/compiled-api.json").read_text())

        self.assertEqual("PASS", gate()["status"])
        for changed in (self.ENUM.replace("public enum", "private enum"),
                        self.ENUM.replace("Outer", "Relocated"),
                        self.ENUM.replace("LOGIN, PAY, CANCEL", "LOGIN, PAY, CANCEL, EXTRA"),
                        self.ENUM.replace("implements RouteRole", "implements OtherRole"),
                        "package example; public class Outer { public static class Access { "
                        "public static final Access PAY = new Access(); } }"):
            self.write(self.roots[0], self.SOURCE, changed)
            report = gate()
            self.assertEqual("FAIL", report["status"])
            self.assertEqual(5, len(report["stalePlatformApiMappings"]))
        self.write(self.roots[0], self.SOURCE, self.ENUM)
        # Source-only upper presence must fail even with unchanged absent upper class bytes.
        self.write(self.roots[1], self.SOURCE, self.ENUM)
        self.assertEqual("FAIL", gate()["status"])
        self.write(self.roots[1], self.SOURCE, "package example; public class Outer {}")
        # Compiled upper presence must independently fail with absent upper AST declaration.
        classes[2] = classes[0]
        self.assertEqual("FAIL", gate()["status"])


class Jdk17OnlyPlatformApiTest(unittest.TestCase):
    """单中线 API 适配必须同时固定独立源码及真实 JVM 条目。"""

    SOURCE = "ddd4j-core/src/main/java/example/Factory.java"
    CONFIG = "ddd4j-core/src/main/java/example/Configuration.java"
    EVIDENCE = "ddd4j-core/src/test/java/example/FactoryContractTest.java"
    FACTORY = ("package example; public class Factory { public Factory() {} "
               "public String build(String name) { return name; } "
               "public void configure(Configuration configuration) {} }")

    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.base = Path(cls.temporary.name)
        cls.roots = [cls.base / name for name in ("jdk8", "jdk17", "jdk21")]
        spec = importlib.util.spec_from_file_location("jdk17_only_parity", SCRIPT)
        cls.parity = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.parity)
        java = (os.environ.get("DDD4J_JDK21_HOME")
                or cls.parity.run("/usr/libexec/java_home", "-v", "21").strip())
        cls.classes = []
        cls.apis = []
        cls.sources = []
        for index, root in enumerate(cls.roots):
            (root / "mvnw").parent.mkdir(parents=True)
            (root / "mvnw").write_text("fixture only; no Maven")
            for path, text in ((cls.CONFIG, "package example; public class Configuration { public Configuration() {} }"),
                               (cls.EVIDENCE, "class FactoryContractTest {}")):
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)
            if index == 1:
                (root / cls.SOURCE).write_text(cls.FACTORY)
            files = sorted((root / "ddd4j-core/src/main/java").rglob("*.java"))
            classes = root / "ddd4j-core/target/classes"
            classes.mkdir(parents=True)
            subprocess.run([java + "/bin/javac", "-parameters", "-d", str(classes), *map(str, files)],
                           check=True, capture_output=True, text=True)
            compiled = {"ddd4j-core|" + path.relative_to(classes).as_posix()[:-6]: path
                        for path in classes.rglob("*.class")}
            cls.classes.append(compiled)
            cls.apis.append(cls.parity.javap_api(compiled))
            cls.sources.append(cls.parity.source_api(root, {str(p.relative_to(root)): "current" for p in files},
                                                    cls.base / "source"))
        cls.symbols = sorted(symbol for symbol in cls.sources[1] if "#example.Factory#" in symbol)
        assert len(cls.symbols) == 3
        cls.items = []
        for symbol in cls.symbols:
            method = symbol.split("#", 2)[2].split("(", 1)[0]
            entry = next(entry for entry in cls.apis[1]
                         if entry.startswith("ddd4j-core|example/Factory|" + method + "|"))
            cls.items.append({"sourceSymbol": symbol, "sourceDeclaration": cls.sources[1][symbol],
                              "compiledEntry": entry, "evidence": cls.EVIDENCE,
                              "reason": "native factory signature is only needed on Java17"})

    def setUp(self):
        self.policy = {"platformApiMembersOnlyOnJdk17": [dict(item) for item in self.items]}
        self.rows = [{"category": "api", "path": symbol, "status": "OPEN", "evidence": "",
                      **{key: api.get(symbol, "MISSING") for key, api in zip(
                          ("jdk8", "jdk17", "jdk21"), self.sources)}} for symbol in self.symbols]
        self.api_sets = [set(api) for api in self.apis]

    def gate(self, pair_diffs=None):
        output = self.base / "gate"
        output.mkdir(exist_ok=True)
        with mock.patch.object(self.parity, "compile_roots", return_value=[]), mock.patch.object(
                self.parity, "class_files", side_effect=self.classes), mock.patch.object(
                self.parity, "javap_api", side_effect=self.api_sets):
            if pair_diffs is None:
                self.parity.compiled_api_gate(self.roots, output, self.policy, self.rows)
            else:
                with mock.patch.object(self.parity, "pair_diff", side_effect=pair_diffs):
                    self.parity.compiled_api_gate(self.roots, output, self.policy, self.rows)
        return json.loads((output / "compiled-api.json").read_text())

    def test_real_middle_factory_entries_keep_raw_diffs_and_only_correct_directions_are_accepted(self):
        report = self.gate()
        self.assertEqual("PASS", report["status"])
        entries = {item["compiledEntry"] for item in self.items}
        self.assertEqual(entries, set(report["pairs"]["1-2"]["only_right"]))
        self.assertEqual(entries, set(report["pairs"]["2-3"]["only_left"]))
        self.assertEqual({"only_left": [], "only_right": []}, report["pairs"]["1-3"])
        self.assertTrue(all(not values[side] for values in report["unresolved"].values()
                            for side in ("only_left", "only_right")))
        self.assertEqual(3, len(report["platformAdaptations"]))

    def test_source_only_drift_and_both_unexpected_source_positions_are_rejected(self):
        for key, value in (("jdk17", "changed|public|name"), ("jdk8", "unexpected"), ("jdk21", "unexpected")):
            with self.subTest(line=key):
                before = self.rows[0][key]
                self.rows[0][key] = value
                report = self.gate()
                self.assertEqual("FAIL", report["status"])
                self.assertIn(self.items[0]["compiledEntry"], report["stalePlatformApiMappings"])
                self.rows[0][key] = before

    def test_compiled_only_drift_and_both_unexpected_compiled_positions_are_rejected(self):
        entry = self.items[0]["compiledEntry"]
        for index in (0, 1, 2):
            with self.subTest(line=index):
                original = set(self.api_sets[index])
                if index == 1:
                    self.api_sets[index].remove(entry)
                else:
                    self.api_sets[index].add(entry)
                report = self.gate()
                self.assertEqual("FAIL", report["status"])
                self.assertIn(entry, report["stalePlatformApiMappings"])
                self.api_sets[index] = original

    def test_wrong_directions_and_one_three_pair_never_receive_middle_exemption(self):
        entry = self.items[0]["compiledEntry"]
        wrong = [{"only_left": [entry], "only_right": []},
                 {"only_left": [], "only_right": [entry]},
                 {"only_left": [entry], "only_right": [entry]}]
        report = self.gate(wrong)
        self.assertEqual("FAIL", report["status"])
        self.assertEqual(wrong, list(report["unresolved"].values()))

    def test_same_class_unregistered_member_is_not_masked(self):
        unlisted = next(iter(self.api_sets[1] - self.api_sets[0])).replace("|build|", "|unregistered|")
        if "|unregistered|" not in unlisted:
            unlisted = "ddd4j-core|example/Factory|unregistered|()V|generic=|throws=|constant=|params="
        self.api_sets[1].add(unlisted)
        report = self.gate()
        self.assertEqual("FAIL", report["status"])
        self.assertEqual([unlisted], report["unresolved"]["1-2"]["only_right"])
        self.assertEqual([unlisted], report["unresolved"]["2-3"]["only_left"])

    def test_source_inventory_stays_pending_until_compilation_and_rejects_drift(self):
        self.parity.classify_declared_platform_api(self.rows, self.policy)
        self.assertTrue(all(row["status"] == "PLATFORM_API_PENDING_COMPILE" for row in self.rows))
        self.rows[0]["status"] = "OPEN"
        self.rows[0]["jdk21"] = "unexpected"
        self.parity.classify_declared_platform_api(self.rows, self.policy)
        self.assertEqual("OPEN", self.rows[0]["status"])

    def test_same_strict_validator_requires_declaration_and_three_line_evidence(self):
        combined = self.parity.platform_api_mappings(self.policy)
        self.assertEqual(self.policy["platformApiMembersOnlyOnJdk17"], combined)
        self.parity.validate_platform_api_mappings(combined, self.roots)
        for declaration in (None, "", "  ", 123):
            with self.subTest(declaration=declaration):
                self.policy["platformApiMembersOnlyOnJdk17"][0]["sourceDeclaration"] = declaration
                with self.assertRaises(ValueError):
                    self.parity.platform_api_mappings(self.policy)
        self.policy["platformApiMembersOnlyOnJdk17"][0] = dict(self.items[0])
        malformed = dict(self.items[0], compiledEntry=self.items[0]["compiledEntry"].replace("example/Factory", "example/Wrong"))
        with self.assertRaises(ValueError):
            self.parity.validate_platform_api_mappings([malformed], self.roots)
        (self.roots[2] / self.EVIDENCE).rename(self.roots[2] / (self.EVIDENCE + ".hidden"))
        try:
            with self.assertRaises(ValueError):
                self.parity.validate_platform_api_mappings(self.items, self.roots)
        finally:
            (self.roots[2] / (self.EVIDENCE + ".hidden")).rename(self.roots[2] / self.EVIDENCE)

    def test_successful_compile_upgrades_pending_source_rows(self):
        self.parity.classify_declared_platform_api(self.rows, self.policy)
        self.assertTrue(all(row["status"] == "PLATFORM_API_PENDING_COMPILE" for row in self.rows))
        self.assertEqual("PASS", self.gate()["status"])
        with mock.patch.object(self.parity, "class_files", side_effect=self.classes):
            self.parity.classify_compiled_source_api(self.rows, self.roots, self.base / "gate",
                                                    {"artifact": "compiled-api.json"})
        self.assertTrue(all(row["status"] == "PLATFORM_API_ADAPTED" for row in self.rows), self.rows)


if __name__ == "__main__":
    unittest.main()
