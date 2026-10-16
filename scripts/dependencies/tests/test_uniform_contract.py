import importlib.util
import pathlib
import tempfile
import unittest
import base64

spec = importlib.util.spec_from_file_location('uniform', pathlib.Path(__file__).parents[1] / 'check_dependencies.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class UniformContract(unittest.TestCase):
    def test_pattern_applies_without_exact_branch_entry(self):
        policy = checker.resolve_policy({'branches': {'feature/*': {'fixed_properties': {'v': '2'}}}}, 'feature/new')
        self.assertEqual(policy['fixed_properties'], {'v': '2'})

    def test_fixed_dependency_preserves_managed_property(self):
        policy = checker.resolve_policy({'fixed_dependencies': {'a:b': '1'}}, 'main')
        self.assertIn('-DprocessProperties=true', checker.update_commands(policy)[-1])

    def test_cloud_parent_selects_actual_build_line(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for line, expected in [('2.4.x', '8'), ('3.4.x', '17'), ('4.0.x', '21')]:
                (root / 'pom.xml').write_text('<project><modelVersion>4.0.0</modelVersion><parent><groupId>io.ddd4j.boot</groupId><artifactId>ddd4j-boot-parent</artifactId><version>' + line + '.20260630-SNAPSHOT</version></parent></project>')
                self.assertEqual(checker.runtime_config(root)['java'], expected)

    def test_settings_accept_raw_and_existing_base64_without_disclosure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'settings.xml'
            raw = '<settings><servers/></settings>'
            for value in [raw, base64.b64encode(raw.encode()).decode()]:
                checker.configure_settings(value, path)
                self.assertEqual(path.read_text(), raw)
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_central_fallback_bypasses_mirror_and_preserves_private_server(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'settings.xml'
            raw = ('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">'
                   '<servers><server><id>private</id><username>user</username>'
                   '<password>secret</password></server></servers>'
                   '<mirrors><mirror><id>mirror</id><mirrorOf>*</mirrorOf>'
                   '<url>https://mirror.example.test/maven</url></mirror></mirrors>'
                   '</settings>')
            checker.configure_settings(raw, path)
            checker.ensure_central_fallback(path)
            checker.ensure_central_fallback(path)
            root = checker.ET.parse(path).getroot()
            namespace = {'m': 'http://maven.apache.org/SETTINGS/1.2.0'}
            self.assertEqual(root.findtext('./m:servers/m:server/m:password', namespaces=namespace), 'secret')
            mirror = root.findtext('./m:mirrors/m:mirror/m:mirrorOf', namespaces=namespace)
            self.assertIn('!ddd4j-central-direct', mirror.split(','))
            repositories = root.findall('./m:profiles/m:profile/m:repositories/m:repository', namespace)
            self.assertEqual(len(repositories), 1)
            self.assertEqual(repositories[0].findtext('m:id', namespaces=namespace), 'ddd4j-central-direct')
            self.assertEqual(repositories[0].findtext('m:url', namespaces=namespace),
                             'https://repo.maven.apache.org/maven2')
            active = [node.text for node in root.findall('./m:activeProfiles/m:activeProfile', namespace)]
            self.assertEqual(active.count('ddd4j-central-fallback'), 1)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_jdk21_dropwizard_family_uses_compatible_fixed_versions(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        expected = {'hibernate-validator.version': '8.0.6.Final',
                    'jersey.version': '3.1.12',
                    'logback.version': '1.5.38'}
        jdk21 = checker.resolve_policy(raw, 'feature/3.0.x')
        self.assertEqual({key: jdk21['fixed_properties'].get(key) for key in expected}, expected)
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties']
        self.assertEqual(jdk8.get('logback.version'), '1.2.13')
        self.assertEqual(jdk8.get('hibernate-validator.version'), '6.2.6.Final')
        self.assertNotIn('jersey.version', jdk8)
        jdk17 = checker.resolve_policy(raw, 'feature/2.0.x')['fixed_properties']
        self.assertTrue(expected.keys().isdisjoint(jdk17))
        self.assertFalse(any(value in jdk21['exclude'] for value in expected))

    def test_jdk21_micronaut_family_remains_on_supported_java_line(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk21 = checker.resolve_policy(raw, 'feature/3.0.x')
        self.assertEqual(jdk21['fixed_properties'].get('micronaut.version'), '4.10.21')
        self.assertEqual(jdk21['fixed_properties'].get('micronaut.core.version'), '4.10.26')
        self.assertIn('io.micronaut*:*', jdk21['exclude'])
        for branch in ('feature/1.0.x', 'feature/2.0.x'):
            policy = checker.resolve_policy(raw, branch)
            self.assertNotIn('io.micronaut*:*', policy['exclude'])
            self.assertNotIn('micronaut.version', policy['fixed_properties'])

    def test_jdk8_junit_family_remains_on_supported_java_line(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(jdk8['fixed_properties'].get('junit-jupiter.version'), '5.14.4')
        self.assertEqual(jdk8['fixed_properties'].get('junit-platform-launcher.version'), '1.14.4')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            policy = checker.resolve_policy(raw, branch)
            self.assertNotIn('junit-jupiter.version', policy['fixed_properties'])
            self.assertNotIn('junit-platform-launcher.version', policy['fixed_properties'])

    def test_all_lines_redisson_keeps_script_return_contract(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        for branch in ('feature/1.0.x', 'feature/2.0.x', 'feature/3.0.x'):
            self.assertEqual(checker.resolve_policy(raw, branch)['fixed_properties'].get('redisson.version'),
                             '3.52.0')

    def test_jdk8_mockito_remains_on_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(jdk8['fixed_properties'].get('mockito.version'), '4.11.0')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('mockito.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_legacy_commons_date_versions_do_not_beat_current_releases(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        expected = {'commons-cli.version': '1.11.0',
                    'commons-beanutils.version': '1.11.0',
                    'commons-codec.version': '1.23.0',
                    'commons-collections.version': '3.2.2',
                    'commons-io.version': '2.22.0'}
        for branch in ('feature/1.0.x', 'feature/2.0.x', 'feature/3.0.x'):
            policy = checker.resolve_policy(raw, branch)
            self.assertEqual({key: policy['fixed_properties'].get(key) for key in expected}, expected)

    def test_classgraph_stays_on_supported_java_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        for branch in ('feature/1.0.x', 'feature/2.0.x', 'feature/3.0.x'):
            policy = checker.resolve_policy(raw, branch)
            self.assertEqual(policy['fixed_properties'].get('classgraph.version'), '4.8.199')

    def test_jdk8_hikari_pool_stays_on_java8_compatible_line(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        policy = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(policy['fixed_properties'].get('hikaricp-jdk8.version'), '4.0.3')
        self.assertEqual(policy['fixed_properties'].get('hikaricp.version'), '4.0.3')

    def test_jdk8_mybatis_stays_on_spring5_line(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        fixed = checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties']
        self.assertEqual(fixed.get('mybatis-spring.version'), '2.1.2')
        self.assertEqual(fixed.get('spring-boot-starter-mybatis.version'), '2.3.2')

    def test_jedis_keeps_pooled_client_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        for branch in ('feature/1.0.x', 'feature/2.0.x', 'feature/3.0.x'):
            policy = checker.resolve_policy(raw, branch)
            self.assertEqual(policy['fixed_properties'].get('jedis.version'), '7.5.3')

    def test_jdk8_logback_keeps_slf4j_17_mdc_binding(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(jdk8['fixed_properties'].get('logback.version'), '1.2.13')
        self.assertEqual(jdk8['fixed_properties'].get('logback-legacy.version'), '1.2.13')
        self.assertEqual(jdk8['fixed_properties'].get('logback-slf4j2.version'), '1.3.14')
        self.assertEqual(jdk8['fixed_properties'].get('slf4j-legacy.version'), '1.7.36')
        self.assertNotIn('logback.version', checker.resolve_policy(raw, 'feature/2.0.x')['fixed_properties'])

    def test_jdk8_javalin_remains_on_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(jdk8['fixed_properties'].get('javalin.version'), '4.6.8')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('javalin.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_dropwizard_keeps_java8_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(jdk8['fixed_properties'].get('dropwizard.version'), '2.1.12')
        self.assertEqual(jdk8['fixed_properties'].get('dropwizard-bom.version'), '2.1.12')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('dropwizard.version', checker.resolve_policy(raw, branch)['fixed_properties'])
            self.assertNotIn('dropwizard-bom.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_caffeine_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('caffeine.version'), '2.9.3')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('caffeine.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_vertx_stack_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('vertx.version'), '4.5.35')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('vertx.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_helidon_loader_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_dependencies'].get(
            'io.helidon.common:helidon-common-service-loader'), '1.4.19')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('io.helidon.common:helidon-common-service-loader',
                             checker.resolve_policy(raw, branch)['fixed_dependencies'])

    def test_jdk8_quarkus_keeps_javax_cdi_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('quarkus.version'),
                         '1.13.7.Final')
        self.assertIn('org.eclipse.microprofile.config:microprofile-config-api',
                      checker.resolve_policy(raw, 'feature/1.0.x')['exclude'])
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('quarkus.version', checker.resolve_policy(raw, branch)['fixed_properties'])
            self.assertNotIn('org.eclipse.microprofile.config:microprofile-config-api',
                             checker.resolve_policy(raw, branch)['exclude'])

    def test_jdk8_panache_keeps_javax_persistence_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        coordinate = 'jakarta.persistence:jakarta.persistence-api'
        self.assertIn(coordinate, checker.resolve_policy(raw, 'feature/1.0.x')['exclude'])
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(coordinate, checker.resolve_policy(raw, branch)['exclude'])

    def test_jdk8_hibernate_orm_keeps_javax_line(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('hibernate5.version'),
                         '5.6.15.Final')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('hibernate5.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_h2_driver_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('h2.version'),
                         '2.2.224')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('h2.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_jdbi_uses_last_java8_release(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('jdbi3.version'),
                         '3.39.1')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('jdbi3.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_boot2_snakeyaml_keeps_compatible_constructor(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'snakeyaml-boot2.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '1.33')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_r2dbc_h2_keeps_compatible_driver(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        expected = {'r2dbc-h2-jdk8.version': '1.0.0.RELEASE', 'r2dbc-h2.version': '1.0.0.RELEASE'}
        policy = checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties']
        self.assertEqual({key: policy.get(key) for key in expected}, expected)
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            other = checker.resolve_policy(raw, branch)['fixed_properties']
            self.assertTrue(expected.keys().isdisjoint(other))

    def test_jdk8_jsqlparser_uses_last_java8_release(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('jsqlparser.version'),
                         '4.9')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('jsqlparser.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_dependency_check_plugin_stays_on_java8(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'maven-dependency-check-plugin.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '10.0.4')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_redis_testcontainer_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'testcontainers-redis.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '2.0.2')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_dozer_core_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'dozer-core.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '6.5.2')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_artemis_client_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'artemis.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '2.19.0')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_kafka_client_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'kafka.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '3.9.2')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_pulsar_client_keeps_compatible_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        name = 'pulsar.version'
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get(name), '4.0.14')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(name, checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_micronaut_keeps_java8_annotation_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        coordinate = 'jakarta.annotation:jakarta.annotation-api'
        self.assertIn(coordinate, checker.resolve_policy(raw, 'feature/1.0.x')['exclude'])
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn(coordinate, checker.resolve_policy(raw, branch)['exclude'])

    def test_jdk8_helidon_platform_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        expected = {'io.helidon.microprofile.server:helidon-microprofile-server': '1.4.19',
                    'io.helidon.microprofile.health:helidon-microprofile-health': '1.4.19'}
        policy = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual({key: policy['fixed_dependencies'].get(key) for key in expected}, expected)
        self.assertEqual(policy['fixed_properties'].get('helidon.version'), '1.4.19')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            other = checker.resolve_policy(raw, branch)
            self.assertTrue(expected.keys().isdisjoint(other['fixed_dependencies']))
            self.assertNotIn('helidon.version', other['fixed_properties'])

    def test_jdk8_helidon_json_binding_keeps_javax_api(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        coordinates = {'org.glassfish.jersey:jersey-bom',
                       'org.glassfish.jersey.media:jersey-media-json-binding',
                       'jakarta.json.bind:jakarta.json.bind-api'}
        self.assertTrue(coordinates.issubset(checker.resolve_policy(raw, 'feature/1.0.x')['exclude']))
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertTrue(coordinates.isdisjoint(checker.resolve_policy(raw, branch)['exclude']))

    def test_jdk17_helidon_and_testcontainers_boms_keep_managed_modules(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk17 = checker.resolve_policy(raw, 'feature/2.0.x')['fixed_properties']
        expected = {'helidon-bom.version': '3.2.21',
                    'helidon-jakarta-annotation.version': '2.0.0',
                    'helidon-jakarta-cdi.version': '3.0.0',
                    'helidon-jakarta-inject.version': '2.0.0',
                    'helidon-jakarta-interceptor.version': '2.0.0',
                    'helidon-jakarta-ws-rs.version': '3.0.0',
                    'helidon-jersey.version': '3.0.18',
                    'testcontainers-bom.version': '1.21.4'}
        self.assertEqual({key: jdk17.get(key) for key in expected}, expected)
        for branch in ('feature/1.0.x', 'feature/3.0.x'):
            other = checker.resolve_policy(raw, branch)['fixed_properties']
            self.assertTrue(expected.keys().isdisjoint(other))

    def test_jdk8_quartz_and_cdi_keep_javax_contract(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        jdk8 = checker.resolve_policy(raw, 'feature/1.0.x')
        self.assertEqual(jdk8['fixed_properties'].get('quartz.version'), '2.4.1')
        java8_api = {'jakarta.enterprise:jakarta.enterprise.cdi-api',
                     'jakarta.transaction:jakarta.transaction-api'}
        self.assertTrue(java8_api.issubset(jdk8['exclude']))
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            other = checker.resolve_policy(raw, branch)
            self.assertNotIn('quartz.version', other['fixed_properties'])
            self.assertTrue(java8_api.isdisjoint(other['exclude']))

    def test_jdk8_shiro_keeps_java8_bytecode(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('shiro.version'),
                         '1.13.0')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('shiro.version', checker.resolve_policy(raw, branch)['fixed_properties'])

    def test_jdk8_guice_keeps_javax_inject(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        self.assertEqual(checker.resolve_policy(raw, 'feature/1.0.x')['fixed_properties'].get('guice.version'),
                         '5.1.0')
        for branch in ('feature/2.0.x', 'feature/3.0.x'):
            self.assertNotIn('guice.version', checker.resolve_policy(raw, branch)['fixed_properties'])


    def test_jdk21_quarkus_uses_compatible_vertx4_artifacts(self):
        repository = pathlib.Path(__file__).parents[3]
        raw = checker.json.loads((repository / '.github/dependency-policy.json').read_text())
        expected = {'io.vertx:vertx-core': '4.5.35',
                    'io.vertx:vertx-junit5': '4.5.35',
                    'io.vertx:vertx-web': '4.5.35'}
        jdk21 = checker.resolve_policy(raw, 'feature/3.0.x')
        self.assertEqual({key: jdk21['fixed_dependencies'].get(key) for key in expected}, expected)
        for branch in ('feature/1.0.x', 'feature/2.0.x'):
            self.assertTrue(expected.keys().isdisjoint(checker.resolve_policy(raw, branch)['fixed_dependencies']))

    def test_non_maven_branch_is_explicitly_not_applicable(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(checker.runtime_config(pathlib.Path(directory))['project'], 'false')

    def test_branch_patterns_apply_in_order(self):
        policy = checker.resolve_policy({'branches': {'feature/*': {'fixed_properties': {'v': '2'}},
                                                      'feature/old': {'fixed_properties': {'v': '1'}}}}, 'feature/old')
        self.assertEqual(policy['fixed_properties'], {'v': '1'})

    def test_internal_properties_are_always_protected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><revision>1</revision><java.version>8</java.version></properties></project>')
            policy = checker.resolve_policy({}, 'main')
            checker.protected_properties(root, policy)
            self.assertIn('revision', policy['exclude_properties'])
            self.assertIn('java.version', policy['exclude_properties'])

    def test_shared_excluded_artifact_property_is_protected_before_update(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><shared>1</shared></properties><dependencies><dependency><groupId>a</groupId><artifactId>b</artifactId><version>${shared}</version></dependency></dependencies></project>')
            policy = checker.resolve_policy({'exclude': ['a:*']}, 'main')
            checker.protected_properties(root, policy)
            self.assertIn('shared', policy['exclude_properties'])

    def test_scan_does_not_use_nvd_or_update_exclusions(self):
        command = checker.scan_command({'cvss_threshold': 7}, pathlib.Path('/tmp/report'))
        self.assertFalse(any('NVD' in part or 'exclude' in part for part in command))

    def test_no_literal_java_baseline_does_not_guess(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'pom.xml').write_text('<project><modelVersion>4.0.0</modelVersion></project>')
            with self.assertRaises(ValueError):
                checker.runtime_config(root)


if __name__ == '__main__':
    unittest.main()
