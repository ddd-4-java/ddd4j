"""公开 JVM 类型映射只允许政策中逐项批准的精确类型。"""

import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path


SOURCE = Path(__file__).with_name("verify-three-line-structure-api-parity.py")
SPEC = importlib.util.spec_from_file_location("three_line_structure", SOURCE)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ThreeLineTypeMappingTest(unittest.TestCase):
    def setUp(self):
        self.mapping = {
            "id": "jaxrs-response-namespace",
            "types": {
                "1.0.x": "javax.ws.rs.core.Response",
                "2.0.x": "jakarta.ws.rs.core.Response",
                "3.0.x": "jakarta.ws.rs.core.Response",
            },
            "reason": "JAX-RS 命名空间适配",
            "evidence": "ddd4j-web/ddd4j-web-quarkus/src/test/java/ExampleTest.java",
        }

    def test_unapproved_namespace_change_remains_visible(self):
        old = "(Ljavax/ws/rs/core/Response;)Ljavax/ws/rs/core/Response;"
        new = "(Ljakarta/ws/rs/core/Response;)Ljakarta/ws/rs/core/Response;"
        self.assertNotEqual(MODULE.normalize_descriptor(old, [], "1.0.x"),
                            MODULE.normalize_descriptor(new, [], "2.0.x"))

    def test_approved_mapping_matches_only_the_exact_type(self):
        old = "(Ljavax/ws/rs/core/Response;Ljavax/ws/rs/core/HttpHeaders;)V"
        new = "(Ljakarta/ws/rs/core/Response;Ljakarta/ws/rs/core/HttpHeaders;)V"
        mappings = [self.mapping]
        normalized_old = MODULE.normalize_descriptor(old, mappings, "1.0.x")
        normalized_new = MODULE.normalize_descriptor(new, mappings, "2.0.x")
        self.assertNotEqual(normalized_old, normalized_new)
        self.assertIn("Ljavax/ws/rs/core/HttpHeaders;", normalized_old)
        self.assertIn("Ljakarta/ws/rs/core/HttpHeaders;", normalized_new)
        self.assertEqual(
            MODULE.normalize_descriptor("(Ljavax/ws/rs/core/Response;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/ws/rs/core/Response;)V", mappings, "2.0.x"),
        )

    def test_prefix_or_incomplete_mapping_is_rejected(self):
        invalid = dict(self.mapping)
        invalid["types"] = dict(self.mapping["types"], **{"1.0.x": "javax.ws.rs.*"})
        with self.assertRaises(ValueError):
            MODULE.normalize_descriptor("(Ljavax/ws/rs/core/Response;)V", [invalid], "1.0.x")

    def test_repository_policy_maps_only_reviewed_exact_types(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        old = MODULE.normalize_descriptor("()Ljavax/ws/rs/core/Response;", mappings, "1.0.x")
        new = MODULE.normalize_descriptor("()Ljakarta/ws/rs/core/Response;", mappings, "2.0.x")
        self.assertEqual(old, new)
        self.assertNotEqual(
            MODULE.normalize_descriptor("()Ljavax/ws/rs/core/HttpHeaders;", mappings, "1.0.x"),
            MODULE.normalize_descriptor("()Ljakarta/ws/rs/core/HttpHeaders;", mappings, "2.0.x"),
        )
        self.assertEqual(
            MODULE.normalize_descriptor("(Lcom/fasterxml/jackson/databind/ObjectMapper;)V", mappings, "2.0.x"),
            MODULE.normalize_descriptor("(Ltools/jackson/databind/ObjectMapper;)V", mappings, "3.0.x"),
        )
        self.assertNotEqual(
            MODULE.normalize_descriptor("()Lcom/fasterxml/jackson/databind/JsonNode;", mappings, "2.0.x"),
            MODULE.normalize_descriptor("()Ltools/jackson/databind/JsonNode;", mappings, "3.0.x"),
        )
        for old_type, new_type in (
            ("com/fasterxml/jackson/databind/JavaType", "tools/jackson/databind/JavaType"),
            ("com/fasterxml/jackson/core/type/TypeReference", "tools/jackson/core/type/TypeReference"),
            ("com/fasterxml/jackson/core/JacksonException", "tools/jackson/core/JacksonException"),
            ("com/fasterxml/jackson/databind/exc/InvalidFormatException",
             "tools/jackson/databind/exc/InvalidFormatException"),
        ):
            self.assertEqual(
                MODULE.normalize_descriptor(f"(L{old_type};)V", mappings, "2.0.x"),
                MODULE.normalize_descriptor(f"(L{new_type};)V", mappings, "3.0.x"),
            )
        self.assertEqual(
            MODULE.normalize_descriptor(
                "Lcom/fasterxml/jackson/core/type/TypeReference<Ljava/lang/String;>;", mappings, "2.0.x"),
            MODULE.normalize_descriptor(
                "Ltools/jackson/core/type/TypeReference<Ljava/lang/String;>;", mappings, "3.0.x"),
        )
        self.assertNotEqual(
            MODULE.normalize_descriptor("Lcom/fasterxml/jackson/core/type/TypeReferenceExtra;", mappings, "2.0.x"),
            MODULE.normalize_descriptor("Ltools/jackson/core/type/TypeReferenceExtra;", mappings, "3.0.x"),
        )
        for java_type in ("Payload", "ConstraintValidator", "ConstraintValidatorContext"):
            self.assertEqual(
                MODULE.normalize_descriptor(f"(Ljavax/validation/{java_type};)V", mappings, "1.0.x"),
                MODULE.normalize_descriptor(f"(Ljakarta/validation/{java_type};)V", mappings, "2.0.x"),
            )
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljavax/validation/ConstraintViolation;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/validation/ConstraintViolation;)V", mappings, "2.0.x"),
        )

    def test_servlet_namespace_mapping_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        for servlet_type in ("HttpServletRequest", "HttpServletResponse", "Cookie"):
            old = f"(Ljavax/servlet/http/{servlet_type};)V"
            new = f"(Ljakarta/servlet/http/{servlet_type};)V"
            self.assertEqual(MODULE.normalize_descriptor(old, mappings, "1.0.x"),
                             MODULE.normalize_descriptor(new, mappings, "2.0.x"))
            self.assertEqual(MODULE.normalize_descriptor(new, mappings, "2.0.x"),
                             MODULE.normalize_descriptor(new, mappings, "3.0.x"))
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljavax/servlet/http/HttpSession;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/servlet/http/HttpSession;)V", mappings, "2.0.x"),
        )

    def test_persistence_and_cdi_namespace_mappings_are_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        for old_type, new_type in (
            ("javax/persistence/EntityManager", "jakarta/persistence/EntityManager"),
            ("javax/enterprise/inject/Instance", "jakarta/enterprise/inject/Instance"),
        ):
            self.assertEqual(
                MODULE.normalize_descriptor(f"(L{old_type};)V", mappings, "1.0.x"),
                MODULE.normalize_descriptor(f"(L{new_type};)V", mappings, "2.0.x"),
            )
        for old_type, new_type in (
            ("javax/persistence/EntityManagerFactory", "jakarta/persistence/EntityManagerFactory"),
            ("javax/enterprise/inject/InstanceHandle", "jakarta/enterprise/inject/InstanceHandle"),
        ):
            self.assertNotEqual(
                MODULE.normalize_descriptor(f"(L{old_type};)V", mappings, "1.0.x"),
                MODULE.normalize_descriptor(f"(L{new_type};)V", mappings, "2.0.x"),
            )

    def test_jdk8_jpa_repository_jakarta_entity_manager_alias_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        self.assertEqual(
            MODULE.normalize_descriptor("(Ljakarta/persistence/EntityManager;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/persistence/EntityManager;)V", mappings, "2.0.x"),
        )
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljakarta/persistence/EntityManagerFactory;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/persistence/EntityManager;)V", mappings, "2.0.x"),
        )

    def test_helidon_cdi_bean_manager_namespace_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        self.assertEqual(
            MODULE.normalize_descriptor("(Ljavax/enterprise/inject/spi/BeanManager;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/enterprise/inject/spi/BeanManager;)V", mappings, "2.0.x"),
        )
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljavax/enterprise/inject/spi/Bean;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/enterprise/inject/spi/Bean;)V", mappings, "2.0.x"),
        )

    def test_jms_namespace_mapping_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        for jms_type in ("Session", "Message", "Destination", "JMSException"):
            old = f"(Ljavax/jms/{jms_type};)Ljavax/jms/{jms_type};"
            new = f"(Ljakarta/jms/{jms_type};)Ljakarta/jms/{jms_type};"
            self.assertEqual(MODULE.normalize_descriptor(old, mappings, "1.0.x"),
                             MODULE.normalize_descriptor(new, mappings, "2.0.x"))
            self.assertEqual(MODULE.normalize_descriptor(new, mappings, "2.0.x"),
                             MODULE.normalize_descriptor(new, mappings, "3.0.x"))
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljavax/jms/Connection;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/jms/Connection;)V", mappings, "2.0.x"),
        )

    def test_jaxrs_container_context_mapping_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        for context_type in ("ContainerRequestContext", "ContainerResponseContext"):
            old = f"(Ljavax/ws/rs/container/{context_type};)V"
            new = f"(Ljakarta/ws/rs/container/{context_type};)V"
            self.assertEqual(MODULE.normalize_descriptor(old, mappings, "1.0.x"),
                             MODULE.normalize_descriptor(new, mappings, "2.0.x"))
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljavax/ws/rs/container/ResourceInfo;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/ws/rs/container/ResourceInfo;)V", mappings, "2.0.x"),
        )

    def test_dropwizard_core_package_mapping_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        for old_type, new_type in (
            ("io/dropwizard/Application", "io/dropwizard/core/Application"),
            ("io/dropwizard/Configuration", "io/dropwizard/core/Configuration"),
            ("io/dropwizard/ConfiguredBundle", "io/dropwizard/core/ConfiguredBundle"),
            ("io/dropwizard/setup/Bootstrap", "io/dropwizard/core/setup/Bootstrap"),
            ("io/dropwizard/setup/Environment", "io/dropwizard/core/setup/Environment"),
        ):
            self.assertEqual(
                MODULE.normalize_descriptor(f"(L{old_type};)L{old_type};", mappings, "1.0.x"),
                MODULE.normalize_descriptor(f"(L{new_type};)L{new_type};", mappings, "2.0.x"),
            )
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Lio/dropwizard/setup/Command;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Lio/dropwizard/core/setup/Command;)V", mappings, "2.0.x"),
        )

    def test_jaxrs_exception_mapper_mapping_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        self.assertEqual(
            MODULE.normalize_descriptor("Ljavax/ws/rs/ext/ExceptionMapper<Ljava/lang/Throwable;>;", mappings, "1.0.x"),
            MODULE.normalize_descriptor("Ljakarta/ws/rs/ext/ExceptionMapper<Ljava/lang/Throwable;>;", mappings, "2.0.x"),
        )
        self.assertNotEqual(
            MODULE.normalize_descriptor("Ljavax/ws/rs/ext/MessageBodyReader<Ljava/lang/String;>;", mappings, "1.0.x"),
            MODULE.normalize_descriptor("Ljakarta/ws/rs/ext/MessageBodyReader<Ljava/lang/String;>;", mappings, "2.0.x"),
        )

    def test_validation_exception_mapping_is_exact(self):
        policy = json.loads((SOURCE.parents[1] / "config/parity/three-line-policy.json").read_text())
        mappings = policy["typeMappings"]
        for exception in (
            "ValidationException", "ConstraintViolationException", "ConstraintDeclarationException",
            "ConstraintDefinitionException", "GroupDefinitionException", "UnexpectedTypeException",
        ):
            self.assertEqual(
                MODULE.normalize_descriptor(f"(Ljavax/validation/{exception};)V", mappings, "1.0.x"),
                MODULE.normalize_descriptor(f"(Ljakarta/validation/{exception};)V", mappings, "2.0.x"),
            )
        self.assertNotEqual(
            MODULE.normalize_descriptor("(Ljavax/validation/ConstraintViolation;)V", mappings, "1.0.x"),
            MODULE.normalize_descriptor("(Ljakarta/validation/ConstraintViolation;)V", mappings, "2.0.x"),
        )

    def test_mapping_evidence_must_exist_in_all_three_roots(self):
        with tempfile.TemporaryDirectory() as directory:
            roots = [Path(directory) / str(index) for index in range(3)]
            for root in roots:
                root.mkdir()
            with self.assertRaises(ValueError):
                MODULE.validate_type_mappings([self.mapping], roots)

    def test_own_api_and_duplicate_ids_cannot_be_mapped(self):
        with tempfile.TemporaryDirectory() as directory:
            roots = [Path(directory) / str(index) for index in range(3)]
            for root in roots:
                evidence = root / self.mapping["evidence"]
                evidence.parent.mkdir(parents=True)
                evidence.write_text("test")
            own = dict(self.mapping)
            own["types"] = {line: "io.ddd4j.core.Foo" for line in self.mapping["types"]}
            with self.assertRaises(ValueError):
                MODULE.validate_type_mappings([own], roots)
            with self.assertRaises(ValueError):
                MODULE.validate_type_mappings([self.mapping, self.mapping], roots)

    def test_codegraph_diagnostics_do_not_hide_unapproved_namespace(self):
        source = "ddd4j-core/src/main/java/example/Handler.java"
        with tempfile.TemporaryDirectory() as directory:
            signatures = []
            for index, namespace in enumerate(("javax", "jakarta")):
                root = Path(directory) / str(index)
                database = root / ".codegraph/codegraph.db"
                database.parent.mkdir(parents=True)
                with sqlite3.connect(database) as connection:
                    connection.execute("CREATE TABLE nodes (file_path TEXT, qualified_name TEXT, "
                                       "name TEXT, signature TEXT, return_type TEXT, visibility TEXT, kind TEXT)")
                    connection.execute("INSERT INTO nodes VALUES (?, ?, ?, ?, ?, ?, ?)",
                                       (source, "example.Handler.handle", "handle",
                                        f"handle({namespace}.ws.rs.core.Response response)",
                                        "void", "public", "method"))
                signatures.append(MODULE.codegraph_signatures(root, {source}))
            self.assertNotEqual(signatures[0], signatures[1])


if __name__ == "__main__":
    unittest.main()
