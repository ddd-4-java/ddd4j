"""依赖清单门禁回归：缺失、BOM 覆盖、坐标替代及 multi-release JAR。"""
import struct
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from verify_dependency_alignment import bytecode, module_dependencies, verify


def pom(artifact='demo', version='1', group='example'):
    return f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><dependencyManagement><dependencies>
    <dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version></dependency>
    </dependencies></dependencyManagement></project>'''


class DependencyAlignmentTest(unittest.TestCase):
    def test_jdk8_shardingsphere_uses_published_553_parser_coordinates(self):
        policy = json.loads((Path(__file__).parents[1] /
                             'config/dependencies/alignment-policy.json').read_text(encoding='utf-8'))
        for dialect in ('mysql', 'opengauss', 'oracle', 'postgresql', 'sql92', 'sqlserver'):
            prefix = 'org.apache.shardingsphere:shardingsphere-parser-sql-'
            replacement = policy['replacements'][prefix + dialect]['8']
            self.assertEqual([prefix + 'engine-' + dialect], replacement['coordinates'])

    def test_jdk17_does_not_require_the_retired_derbyshared_entry(self):
        policy = json.loads((Path(__file__).parents[1] /
                             'config/dependencies/alignment-policy.json').read_text(encoding='utf-8'))
        unsupported = policy['unsupported']['org.apache.derby:derbyshared']
        self.assertEqual('unsupported-retired', unsupported['17']['status'])
        self.assertNotIn('21', unsupported)

    def test_direct_usage_ignores_superpowers_build_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            history = root / '.superpowers/sdd/archived-candidate'
            history.mkdir(parents=True)
            (history / 'pom.xml').write_text('<project><incomplete>')
            module = root / 'feature'
            module.mkdir()
            (module / 'pom.xml').write_text(
                pom().replace('<dependencyManagement>', '').replace('</dependencyManagement>', ''))
            self.assertEqual(module_dependencies(root),
                             {('example', 'demo', 'jar', ''): ['feature/pom.xml']})

    def test_direct_usage_does_not_attribute_a_different_literal_version(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            key = ('example', 'demo', 'jar', '')
            for name, version in (('literal-match', '2'), ('literal-other', '1'),
                                  ('property', '${component.version}'), ('range', '[1,3)'),
                                  ('release', 'RELEASE'), ('managed', '')):
                module = root / name
                module.mkdir()
                project = pom(version=version).replace('<dependencyManagement>', '').replace('</dependencyManagement>', '')
                (module / 'pom.xml').write_text(project.replace('<version></version>', ''))
            self.assertEqual(module_dependencies(root, versions={key: '2'})[key],
                             ['literal-match/pom.xml', 'managed/pom.xml', 'property/pom.xml',
                              'range/pom.xml', 'release/pom.xml'])

    def test_required_baseline_uses_current_easy4j_coordinates(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        legacy_prefixes = ('com.github.hiwepy:', 'com.github.redacted-legacy-family:',
                           'io.github.redacted-legacy-family:')
        for section in ('baseline_components', 'baseline_variants'):
            self.assertFalse([coordinate for coordinate in policy[section]
                              if coordinate.startswith(legacy_prefixes)], section)
        for artifact in ('metrics-druid', 'httpconn', 'httputils',
                         'javassist-plus', 'pac4j-oauth-ext', 'shiro-extension-core',
                         'spring-extension'):
            self.assertIn('io.github.easy4j:' + artifact, policy['baseline_components'])
        self.assertEqual(8, policy['baseline_components']['io.github.easy4j:metrics-druid'])
        self.assertEqual(8, policy['baseline_variants']['io.github.easy4j:metrics-druid:jar:'])
        self.assertNotIn('io.github.easy4j:druid-metrics-prometheus', policy['baseline_components'])
        self.assertNotIn('io.github.easy4j:druid-metrics-prometheus:jar:', policy['baseline_variants'])

    def test_shardingsphere_parser_baseline_uses_published_coordinates(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        for dialect in ('mysql', 'opengauss', 'oracle', 'postgresql', 'sql92', 'sqlserver'):
            current = 'org.apache.shardingsphere:shardingsphere-parser-sql-' + dialect
            obsolete = 'org.apache.shardingsphere:shardingsphere-parser-sql-engine-' + dialect
            self.assertIn(current, policy['baseline_components'])
            self.assertIn(current + ':jar:', policy['baseline_variants'])
            self.assertNotIn(obsolete, policy['baseline_components'])
            self.assertNotIn(obsolete + ':jar:', policy['baseline_variants'])

    def test_three_line_easy4j_components_use_branch_revisions(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        for artifact in ('cxf-rt-javassist', 'dozer-extra-converters', 'metrics-druid',
                         'shiro-j2cache', 'undertow-metrics-prometheus'):
            coordinate = 'io.github.easy4j:' + artifact
            actual = policy['dependency_versions'].get(coordinate)
            expected = {str(jdk): line + '.20260630-SNAPSHOT'
                        for jdk, line in ((8, '1.0.x'), (17, '2.0.x'), (21, '3.0.x'))}
            self.assertEqual(expected, actual, coordinate)

    def test_flowable_legacy_ui_is_unsupported_on_seven_x_lines(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        artifacts = ('flowable-content-spring-configurator',
                     'flowable-form-spring-configurator', 'flowable-ui-admin-logic',
                     'flowable-ui-idm-logic', 'flowable-ui-modeler-conf',
                     'flowable-ui-modeler-logic', 'flowable-ui-modeler-rest',
                     'flowable-ui-task-conf')
        for artifact in artifacts:
            coordinate = 'org.flowable:' + artifact
            for jdk in ('17', '21'):
                self.assertIn(jdk, policy['unsupported'].get(coordinate, {}), coordinate)

    def test_jetty_nine_websocket_modules_are_not_managed_as_jetty_twelve(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        artifacts = ('javax-websocket-client-impl', 'javax-websocket-server-impl',
                     'websocket-api', 'websocket-client', 'websocket-common',
                     'websocket-server', 'websocket-servlet')
        for artifact in artifacts:
            coordinate = 'org.eclipse.jetty.websocket:' + artifact
            for jdk in ('17', '21'):
                self.assertIn(jdk, policy['unsupported'].get(coordinate, {}), coordinate)

    def test_retired_kafka_and_resilience4j_catalog_entries_have_support_boundaries(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        coordinates = {
            'org.apache.kafka:kafka-log4j-appender': ('17', '21'),
            'org.apache.kafka:kafka-streams-scala_2.12': ('17', '21'),
            'org.apache.kafka:kafka_2.12': ('17', '21'),
            'io.github.resilience4j:resilience4j-spring': ('17', '21'),
            'io.github.resilience4j:resilience4j-spring-boot2': ('17',),
            'io.github.resilience4j:resilience4j-spring-cloud2': ('17',),
        }
        for coordinate, jdks in coordinates.items():
            for jdk in jdks:
                self.assertIn(jdk, policy['unsupported'].get(coordinate, {}), coordinate)

    def test_swagger_legacy_integrations_keep_published_jdk8_versions(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        artifacts = ('swagger-hibernate-validations', 'swagger-jaxrs',
                     'swagger-jersey-jaxrs', 'swagger-jersey2-jaxrs', 'swagger-servlet')
        for artifact in artifacts:
            coordinate = 'io.swagger:' + artifact
            self.assertEqual('1.6.14', policy['dependency_versions'][coordinate]['8'])
            self.assertNotIn('8', policy['unsupported'].get(coordinate, {}))
            self.assertIn('17', policy['unsupported'].get(coordinate, {}), coordinate)

    def test_retired_logging_locking_cxf_and_pac4j_catalog_entries_are_bounded(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        coordinates = {
            'org.apache.logging.log4j:log4j-flume-ng': ('8', '17', '21'),
            'org.apache.logging.log4j:log4j-kubernetes': ('8', '17', '21'),
            'org.apache.logging.log4j:log4j-mongodb3': ('8', '17', '21'),
            'net.javacrumbs.shedlock:shedlock-provider-couchbase-javaclient': ('17', '21'),
            'net.javacrumbs.shedlock:shedlock-provider-redis-jedis': ('17', '21'),
            'org.pac4j:pac4j-couch': ('17', '21'),
            'org.pac4j:pac4j-gae': ('17', '21'),
            'org.pac4j:pac4j-openid': ('17', '21'),
            'org.apache.cxf:cxf-rt-rs-service-description-swagger': ('17', '21'),
        }
        for coordinate, jdks in coordinates.items():
            for jdk in jdks:
                self.assertIn(jdk, policy['unsupported'].get(coordinate, {}), coordinate)

    def test_javassist_plus_is_mapped_to_current_easy4j_extension_on_every_line(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        replacement = policy['replacements'].get('io.github.easy4j:javassist-plus', {})
        for jdk in ('8', '17', '21'):
            self.assertEqual(['io.github.easy4j:javassist-extension'],
                             replacement.get(jdk, {}).get('coordinates'))
        self.assertEqual(8, policy['baseline_components'].get('io.github.easy4j:javassist-extension'))

    def test_pac4j_oauth_ext_is_mapped_to_current_easy4j_extension(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        replacement = policy['replacements'].get('io.github.easy4j:pac4j-oauth-ext', {})
        for jdk in ('17', '21'):
            self.assertEqual(['io.github.easy4j:pac4j-oauth-extension'],
                             replacement.get(jdk, {}).get('coordinates'))

    def test_android_only_cronet_artifacts_are_not_claimed_as_jvm_jars(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        for artifact in ('cronet-api', 'cronet-common', 'cronet-embedded', 'cronet-fallback'):
            coordinate = 'org.chromium.net:' + artifact
            for jdk in ('8', '17', '21'):
                self.assertIn(jdk, policy['unsupported'].get(coordinate, {}), coordinate)

    def test_hbase_three_protocol_uses_published_shaded_artifact(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        replacement = policy['replacements'].get('org.apache.hbase:hbase-protocol', {})
        for jdk in ('8', '17', '21'):
            self.assertEqual(['org.apache.hbase:hbase-protocol-shaded'],
                             replacement.get(jdk, {}).get('coordinates'))
        self.assertEqual(8, policy['baseline_components'].get('org.apache.hbase:hbase-protocol-shaded'))

    def test_not_yet_commons_ssl_uses_its_published_group(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        for old in ('org.apache.commons.ssl:not-yet-commons-ssl',
                    'org.apache.commons:not-yet-commons-ssl'):
            replacement = policy['replacements'].get(old, {})
            for jdk in ('8', '17', '21'):
                self.assertEqual(['ca.juliusdavies:not-yet-commons-ssl'],
                                 replacement.get(jdk, {}).get('coordinates'))
        self.assertEqual(8, policy['baseline_components'].get('ca.juliusdavies:not-yet-commons-ssl'))

    def test_unpublished_xdocreport_and_jdk8_snmp_db_are_explicitly_unsupported(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        xdocreport = policy['unsupported'].get(
            'fr.opensagres.xdocreport:org.apache.poi.xwpf.converter.pdf.itext5', {})
        for jdk in ('8', '17', '21'):
            self.assertIn(jdk, xdocreport)
        self.assertIn('8', policy['unsupported'].get('org.snmp4j:snmp4j-agent-db', {}))

    def test_unpublished_schemacrawler_line_artifacts_are_recorded(self):
        policy_path = Path(__file__).parents[1] / 'config/dependencies/alignment-policy.json'
        policy = json.loads(policy_path.read_text(encoding='utf-8'))
        self.assertIn('8', policy['unsupported'].get('us.fatehi:schemacrawler-hana', {}))
        utility = policy['unsupported'].get('us.fatehi:schemacrawler-utility', {})
        for jdk in ('17', '21'):
            self.assertIn(jdk, utility)

    def test_unconfigured_third_party_release_does_not_inherit_ddd4j_version(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom('slf4j-api', '1.7.36', 'org.slf4j'))
                roots.append(root)
                effective.append(path)
            errors, unknown, rows = verify(roots, effective, {}, base, None, False)
            self.assertFalse(errors, errors)
            self.assertEqual(3, len(unknown))
            errors, _, _ = verify(roots, effective, {}, base, None, True)
            self.assertTrue(any('Unverified artifact:' in error for error in errors), errors)
            policy = {'dependency_versions': {'io.github.easy4j:easyexcel': '5.0.1'}}
            for path in effective:
                path.write_text(pom('easyexcel', '5.0.1', 'io.github.easy4j'))
            errors, _, _ = verify(roots, effective, policy, base, None, False)
            self.assertFalse(errors, errors)
            roots[0].joinpath('ddd4j-dependencies/pom.xml').write_text(
                pom('easyexcel', '1.0.x.20260630-SNAPSHOT', 'io.github.easy4j'))
            errors, _, _ = verify(roots, effective, policy, base, None, False)
            self.assertTrue(any('expected 5.0.1' in error for error in errors), errors)


    def test_unverified_classifier_identifies_exact_artifact(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom().replace('</dependency>',
                    '<classifier>test</classifier></dependency>'))
                roots.append(root)
                effective.append(path)
            errors, unknown, rows = verify(roots, effective, {}, base, None, True)
            self.assertIn('JDK 8: example:demo:jar:test:1', unknown)
            self.assertIn('Unverified artifact: JDK 8: example:demo:jar:test:1', errors)
            self.assertTrue(any(row[0] == 'example:demo:jar:test' and row[3] == 'unverified-artifact'
                                for row in rows))

    def test_direct_module_usage_excludes_bom_catalog_and_profile_only_dependencies(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'ddd4j-dependencies').mkdir()
            (root / 'ddd4j-dependencies/pom.xml').write_text(pom('catalog'))
            module = root / 'feature'
            module.mkdir()
            module.joinpath('pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
              <dependencies>
                <dependency><groupId>example</groupId><artifactId>runtime</artifactId></dependency>
                <dependency><groupId>example</groupId><artifactId>fixtures</artifactId>
                  <classifier>test</classifier></dependency>
              </dependencies>
              <profiles><profile><id>optional</id><dependencies>
                <dependency><groupId>example</groupId><artifactId>profile-only</artifactId></dependency>
              </dependencies></profile></profiles></project>''')
            usage = module_dependencies(root)
            self.assertNotIn(('example', 'catalog', 'jar', ''), usage)
            self.assertNotIn(('example', 'profile-only', 'jar', ''), usage)
            self.assertEqual(usage[('example', 'runtime', 'jar', '')], ['feature/pom.xml'])
            self.assertEqual(usage[('example', 'fixtures', 'jar', 'test')], ['feature/pom.xml'])

    def test_javaee_coordinates_have_explicit_jakarta_replacements_on_jdk21(self):
        policy = json.loads((Path(__file__).parents[1] /
                             'config/dependencies/alignment-policy.json').read_text())
        expected = {
            'javax.annotation:javax.annotation-api': 'jakarta.annotation:jakarta.annotation-api',
            'javax.inject:javax.inject': 'jakarta.inject:jakarta.inject-api',
            'javax.persistence:javax.persistence-api': 'jakarta.persistence:jakarta.persistence-api',
        }
        for old, new in expected.items():
            self.assertEqual(policy['replacements'][old]['21']['coordinates'], [new])

    def test_legacy_easy4j_coordinates_map_to_current_component_on_all_lines(self):
        policy = json.loads((Path(__file__).parents[1] /
                             'config/dependencies/alignment-policy.json').read_text())
        for artifact in ('shiro-j2cache', 'spring-javassist'):
            migration = policy['replacements']['com.github.redacted-legacy-family:' + artifact]
            for jdk in ('8', '17', '21'):
                self.assertEqual(migration[jdk]['coordinates'], ['io.github.easy4j:' + artifact])

    def test_easy4j_boot_starter_uses_boot_line_not_component_line(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            starter = 'security-biz-spring-boot-starter'
            for index, line in enumerate(('2.7.x', '3.4.x', '4.0.x')):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom(starter, line + '.20260630-SNAPSHOT', 'io.github.easy4j'))
                roots.append(root)
                effective.append(path)
            policy = {'boot_starter_versions': {
                'io.github.easy4j:' + starter: {'8': '2.7.x', '17': '3.4.x', '21': '4.0.x'}}}
            errors, _, _ = verify(roots, effective, policy, base, None, False)
            self.assertFalse(any('Easy4J' in error for error in errors), errors)
            roots[1].joinpath('ddd4j-dependencies/pom.xml').write_text(
                pom(starter, '2.0.x.20260630-SNAPSHOT', 'io.github.easy4j'))
            errors, _, _ = verify(roots, effective, policy, base, None, False)
            self.assertTrue(any('expected 3.4.x.20260630-SNAPSHOT' in error for error in errors), errors)

    def test_ordinary_easy4j_component_uses_owning_line_without_explicit_policy(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index, version in enumerate(('1.0.x.20260630-SNAPSHOT',
                                              '1.0.x.20260630-SNAPSHOT',
                                              '3.0.x.20260630-SNAPSHOT')):
                root = base / str(index)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.parent.mkdir(parents=True)
                path.write_text(pom('codex-java-sdk', version, 'io.github.easy4j'))
                roots.append(root)
                effective.append(path)
            errors, _, _ = verify(roots, effective, {}, base, None, False)
            self.assertEqual(1, len(errors), errors)
            self.assertIn('JDK 17: Easy4J io.github.easy4j:codex-java-sdk', errors[0])
            self.assertIn('expected 2.0.x.20260630-SNAPSHOT', errors[0])

    def test_explicit_component_version_is_enforced(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index, line in enumerate(('1.0.x', '2.0.x', '3.0.x')):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                version = ('2.0.x.20260630-SNAPSHOT' if index == 0
                           else line + '.20260630-SNAPSHOT')
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom('component', version, 'io.github.easy4j'))
                roots.append(root)
                effective.append(path)
            errors, _, _ = verify(roots, effective, {'dependency_versions': {
                'io.github.easy4j:component': {'8': '1.0.x.20260630-SNAPSHOT',
                                             '17': '2.0.x.20260630-SNAPSHOT',
                                             '21': '3.0.x.20260630-SNAPSHOT'}}}, base, None, False)
            self.assertTrue(any('JDK 8: dependency io.github.easy4j:component' in error for error in errors), errors)

    def test_import_bom_cannot_evade_explicit_version_check(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index, line in enumerate(('1.0.x', '2.0.x', '3.0.x')):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                version = '1.0.1.RELEASE' if index == 0 else line + '.20260630-SNAPSHOT'
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom('template-bom', version, 'io.github.easy4j').replace(
                    '</version></dependency>', '</version><type>pom</type><scope>import</scope></dependency>'))
                roots.append(root)
                effective.append(path)
            errors, _, _ = verify(roots, effective, {'dependency_versions': {
                'io.github.easy4j:template-bom': {'8': '1.0.x.20260630-SNAPSHOT',
                                                '17': '2.0.x.20260630-SNAPSHOT',
                                                '21': '3.0.x.20260630-SNAPSHOT'}}}, base, None, False)
            self.assertTrue(any('JDK 8: dependency io.github.easy4j:template-bom' in error for error in errors), errors)

    def test_multirelease_java21_does_not_reject_java8_base(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / 'example.jar'
            with zipfile.ZipFile(jar, 'w') as output:
                output.writestr('example/A.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 52))
                output.writestr('META-INF/versions/21/example/A.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65))
            self.assertEqual(bytecode(jar), {8: 1})

    def test_optional_higher_bytecode_only_allows_named_class_path(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            repository = base / 'repository'
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom('common', '1', 'example'))
                roots.append(root)
                effective.append(path)
            jar = repository / 'example/common/1/common-1.jar'
            jar.parent.mkdir(parents=True)
            with zipfile.ZipFile(jar, 'w') as output:
                output.writestr('example/Base.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 52))
                output.writestr('example/optional/VarHandle.class',
                                b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 55))
            policy = {'optional_bytecode': {'example:common:1': {
                'base_jdk': 8, 'allowed_prefixes': ['example/optional/']}}}
            errors, _, _ = verify(roots, effective, policy, repository, None, True)
            self.assertEqual(errors, [])
            with zipfile.ZipFile(jar, 'a') as output:
                output.writestr('example/Unexpected.class',
                                b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 55))
            errors, _, _ = verify(roots, effective, policy, repository, None, True)
            self.assertIn('JDK 8: example:common:1 contains Java 11 classes', errors)

    def test_easy4j_empty_jar_does_not_pass_bytecode_verification(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            repository = base / 'repository'
            roots, effective = [], []
            for index, line in enumerate(('1.0.x', '2.0.x', '3.0.x')):
                version = line + '.20260630-SNAPSHOT'
                root = base / line
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom('dozer-extra-converters', version, 'io.github.easy4j'))
                roots.append(root)
                effective.append(path)
                jar = repository / 'io/github/easy4j/dozer-extra-converters' / version / (
                    'dozer-extra-converters-' + version + '.jar')
                jar.parent.mkdir(parents=True)
                with zipfile.ZipFile(jar, 'w') as output:
                    output.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
            errors, unknown, rows = verify(roots, effective, {}, repository, None, True)
            self.assertEqual(unknown, [])
            self.assertEqual(sum(row[3] == 'bytecode-empty' for row in rows), 3)
            self.assertEqual(sum('JAR has no base classes' in error for error in errors), 3)

    def test_third_party_resource_only_jar_keeps_existing_status(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            repository = base / 'repository'
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom('resources-only', '1', 'example'))
                roots.append(root)
                effective.append(path)
            jar = repository / 'example/resources-only/1/resources-only-1.jar'
            jar.parent.mkdir(parents=True)
            with zipfile.ZipFile(jar, 'w') as output:
                output.writestr('META-INF/example.properties', 'enabled=true\n')
            errors, unknown, rows = verify(roots, effective, {}, repository, None, True)
            self.assertEqual(errors, [])
            self.assertEqual(unknown, [])
            self.assertEqual(sum(row[3] == 'bytecode-pass' for row in rows), 3)

    def exercise(self, replacement, policy=None):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            roots, effective = [], []
            for i in range(3):
                root = directory / str(i)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                # Newer line can inherit management through an imported BOM.
                direct = pom() if i == 0 else pom('unrelated')
                (root / 'ddd4j-dependencies/pom.xml').write_text(direct)
                path = root / 'effective.xml'
                path.write_text(pom() if i == 0 else replacement)
                roots.append(root)
                effective.append(path)
            return verify(roots, effective, policy or {}, directory / 'repository', None, False)

    def test_third_party_catalog_is_independent_between_jdk_lines(self):
        errors, _, _ = self.exercise(pom('unrelated'))
        self.assertNotIn('JDK 17: missing example:demo', errors)
        self.assertNotIn('JDK 21: missing example:demo', errors)

    def test_easy4j_component_still_requires_cross_line_coverage(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                source = root / 'ddd4j-dependencies/pom.xml'
                source.write_text(pom('component', '1.0.x.20260630-SNAPSHOT',
                                      'io.github.easy4j') if index == 0 else pom('unrelated'))
                resolved = root / 'effective.xml'
                resolved.write_text(source.read_text())
                roots.append(root)
                effective.append(resolved)
            errors, _, _ = verify(roots, effective, {}, base, None, False)
            self.assertIn('JDK 17: missing io.github.easy4j:component', errors)
            self.assertIn('JDK 21: missing io.github.easy4j:component', errors)

    def test_effective_bom_coverage_satisfies_component(self):
        errors, unknown, _ = self.exercise(pom())
        self.assertFalse(any('missing example:demo' in item for item in errors))
        self.assertTrue(unknown, 'An unavailable archive must remain explicitly unverified')

    def test_replacement_must_actually_be_managed(self):
        policy = {'replacements': {'example:demo': {
            '17': {'coordinates': ['example:replacement']},
            '21': {'coordinates': ['example:replacement']},
        }}}
        errors, _, _ = self.exercise(pom('unrelated'), policy)
        self.assertIn('JDK 17: missing example:demo', errors)
        errors, _, _ = self.exercise(pom('replacement'), policy)
        self.assertFalse(any('missing example:demo' in item for item in errors))

    def test_fixed_baseline_catches_component_deleted_from_every_line(self):
        policy = {'baseline_components': {'example:deleted': 8}}
        errors, _, _ = self.exercise(pom(), policy)
        self.assertIn('JDK 8: missing example:deleted', errors)
        self.assertNotIn('JDK 21: missing example:deleted', errors)

    def test_unsupported_explicit_catalog_entry_is_never_counted_as_success(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                source = root / 'ddd4j-dependencies/pom.xml'
                source.write_text(pom())
                roots.append(root)
                effective.append(source)
            policy = {'unsupported': {'example:demo': {'17': {'reason': 'requires Spring 5'}}}}
            errors, _, rows = verify(roots, effective, policy, base, None, False)
            self.assertIn('JDK 17: unsupported example:demo: requires Spring 5', errors)
            self.assertTrue(any(row[0] == 'example:demo' and row[1] == 17
                                and row[3] == 'unsupported' for row in rows))

    def test_unsupported_imported_catalog_entry_is_not_used_dependency(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                source = root / 'ddd4j-dependencies/pom.xml'
                source.write_text(pom('demo') if index == 1 else pom('unrelated'))
                resolved = root / 'effective.xml'
                resolved.write_text(pom('demo') if index < 2 else pom('unrelated'))
                roots.append(root)
                effective.append(resolved)
            policy = {'unsupported': {'example:demo': {'8': {'reason': 'requires newer JDK'}}}}
            errors, _, rows = verify(roots, effective, policy, base, None, False)
            self.assertNotIn('JDK 8: unsupported example:demo: requires newer JDK', errors)
            self.assertTrue(any(row[0] == 'example:demo' and row[1] == 8
                                and row[3] == 'unsupported-catalog-only' for row in rows))

    def test_unsupported_imported_catalog_entry_used_by_module_still_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                source = root / 'ddd4j-dependencies/pom.xml'
                source.write_text(pom('demo') if index == 1 else pom('unrelated'))
                resolved = root / 'effective.xml'
                resolved.write_text(pom('demo') if index < 2 else pom('unrelated'))
                roots.append(root)
                effective.append(resolved)
            module = roots[0] / 'feature'
            module.mkdir()
            module.joinpath('pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
                <dependencies><dependency><groupId>example</groupId>
                <artifactId>demo</artifactId></dependency></dependencies></project>''')
            policy = {'unsupported': {'example:demo': {'8': {'reason': 'requires newer JDK'}}}}
            errors, _, rows = verify(roots, effective, policy, base, None, False)
            self.assertIn('JDK 8: unsupported example:demo: requires newer JDK', errors)
            self.assertTrue(any(row[0] == 'example:demo' and row[1] == 8
                                and row[3] == 'unsupported' for row in rows))

    def test_unsupported_absent_catalog_entry_is_not_a_dependency_failure(self):
        policy = {'unsupported': {'example:demo': {'17': {'reason': 'requires newer JDK'}}}}
        errors, _, rows = self.exercise(pom('unrelated'), policy)
        self.assertFalse(any('JDK 17: unsupported example:demo' in error for error in errors), errors)
        self.assertTrue(any(row[0] == 'example:demo' and row[1] == 17
                            and row[3] == 'unsupported-absent' for row in rows))

    def test_unsupported_direct_coordinate_missing_from_effective_still_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                (root / 'ddd4j-dependencies/pom.xml').write_text(pom())
                resolved = root / 'effective.xml'
                resolved.write_text(pom('unrelated') if index == 1 else pom())
                roots.append(root)
                effective.append(resolved)
            policy = {'unsupported': {'example:demo': {'17': {'reason': 'requires newer JDK'}}}}
            errors, _, _ = verify(roots, effective, policy, base, None, False)
            self.assertIn('JDK 17: missing example:demo', errors)

    def test_explicit_boundary_exclusion_is_auditable_without_missing_error(self):
        policy = {'excluded_components': {'example:demo': {'17': 'belongs to framework adapter'}}}
        errors, _, rows = self.exercise(pom('unrelated'), policy)
        self.assertNotIn('JDK 17: missing example:demo', errors)
        self.assertTrue(any(row[0] == 'example:demo' and row[3] == 'excluded' for row in rows))

    def test_profile_only_component_cannot_disappear_from_audit(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            managed = pom().split('>', 1)[1].rsplit('</project>', 1)[0]
            for i in range(3):
                root = base / str(i)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                source = ('<project xmlns="http://maven.apache.org/POM/4.0.0">'
                          '<profiles><profile><id>higher-jdk</id>' + managed +
                          '</profile></profiles></project>')
                (root / 'ddd4j-dependencies/pom.xml').write_text(source)
                resolved = root / 'effective.xml'
                resolved.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
                roots.append(root)
                effective.append(resolved)
            errors, _, rows = verify(roots, effective, {}, base, None, False)
            self.assertTrue(any('only in profile higher-jdk' in error for error in errors))
            self.assertTrue(any(row[0] == 'example:demo' for row in rows))

    def test_newer_test_classifier_does_not_make_java8_runtime_missing(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            test_jar = ('<dependency><groupId>example</groupId><artifactId>demo</artifactId>'
                        '<version>1</version><classifier>test</classifier></dependency>')
            for i in range(3):
                root = base / str(i)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                text = pom() if i == 0 else pom().replace('</dependencies>', test_jar + '</dependencies>')
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(text)
                roots.append(root)
                effective.append(path)
            errors, _, rows = verify(roots, effective, {'baseline_components': {'example:demo': 8}}, base, None, False)
            self.assertEqual(errors, [])
            self.assertTrue(any(row[0] == 'example:demo:jar:test' for row in rows))

    def test_native_classifier_baseline_does_not_invent_plain_jar(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for i in range(3):
                root = base / str(i)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                path = root / 'ddd4j-dependencies/pom.xml'
                path.write_text(pom().replace('</dependency>', '<classifier>osx-x86_64</classifier></dependency>'))
                roots.append(root)
                effective.append(path)
            policy = {'baseline_components': {'example:demo': 8},
                      'baseline_variants': {'example:demo:jar:osx-x86_64': 8}}
            errors, _, rows = verify(roots, effective, policy, base, None, False)
            self.assertEqual(errors, [])
            self.assertEqual(len(rows), 3)


class PublishedGroupRelocationTest(unittest.TestCase):
    """只接受已发布 POM 证明且本线 BOM 管理的单跳同版本 group 迁移。"""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.repository = self.base / 'repository'
        self.cache = self.base / 'cache'
        self.roots, self.effective = [], []
        for index in range(3):
            root = self.base / str(index)
            (root / 'ddd4j-dependencies').mkdir(parents=True)
            (root / 'ddd4j-dependencies/pom.xml').write_text(pom(group='old.publisher'))
            effective = root / 'effective.xml'
            effective.write_text(pom(group='old.publisher').replace('</dependencies>',
                '<dependency><groupId>new.publisher</groupId><artifactId>demo</artifactId>'
                '<version>1</version></dependency></dependencies>'))
            self.roots.append(root)
            self.effective.append(effective)
        self.source_pom = self.artifact_path('old.publisher', 'pom')
        self.target_pom = self.artifact_path('new.publisher', 'pom')
        self.target_jar = self.artifact_path('new.publisher', 'jar')
        self.source_pom.parent.mkdir(parents=True)
        self.source_pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
          <parent><groupId>old.publisher</groupId><artifactId>relocations-parent</artifactId>
            <version>1</version></parent><artifactId>demo</artifactId><packaging>pom</packaging>
          <distributionManagement><relocation><groupId>new.publisher</groupId>
          </relocation></distributionManagement></project>''')
        self.target_pom.parent.mkdir(parents=True)
        self.target_pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
          <parent><groupId>new.publisher</groupId><artifactId>publisher-parent</artifactId>
            <version>1</version></parent><artifactId>demo</artifactId></project>''')
        self.write_jar(self.target_jar, 8)

    def artifact_path(self, group, extension):
        return self.repository / group.replace('.', '/') / 'demo/1' / ('demo-1.' + extension)

    def write_jar(self, path, jdk):
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, 'w') as output:
            output.writestr('example/Client.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, jdk + 44))

    def check(self, cache=None):
        return verify(self.roots, self.effective, {}, self.repository, cache, True)

    def old_row(self, rows, jdk=17):
        return next(row for row in rows if row[:2] == ['old.publisher:demo', jdk])

    def assert_unverified(self):
        errors, unknown, rows = self.check()
        self.assertIn('JDK 17: old.publisher:demo:1', unknown)
        self.assertIn('Unverified artifact: JDK 17: old.publisher:demo:1', errors)
        self.assertEqual('unverified-artifact', self.old_row(rows)[3])

    def test_published_group_relocation_checks_destination_base_jar(self):
        errors, unknown, rows = self.check()
        self.assertEqual([], errors)
        self.assertEqual([], unknown)
        self.assertEqual(['bytecode-pass', 8, str(self.target_jar)], self.old_row(rows)[3:])

    def test_flat_extra_cache_accepts_exact_poms_and_destination_jar(self):
        self.cache.mkdir()
        for source in (self.source_pom, self.target_pom, self.target_jar):
            target = self.cache / str(source.relative_to(self.repository)).replace('/', '__')
            source.replace(target)
        errors, unknown, rows = self.check(self.cache)
        self.assertEqual([], errors)
        self.assertEqual([], unknown)
        self.assertEqual('bytecode-pass', self.old_row(rows)[3])
        self.assertTrue(self.old_row(rows)[5].endswith('new__publisher__demo__1__demo-1.jar'))

    def test_direct_exact_jar_wins_over_relocation_even_if_cache_supplies_it(self):
        self.write_jar(self.artifact_path('old.publisher', 'jar'), 21)
        self.cache.mkdir()
        self.artifact_path('old.publisher', 'jar').replace(
            self.cache / 'old__publisher__demo__1__demo-1.jar')
        errors, _, rows = self.check(self.cache)
        self.assertEqual('bytecode-fail', self.old_row(rows)[3])
        self.assertTrue(any('JDK 17: old.publisher:demo:1 contains Java 21' in e for e in errors))

    def test_relocated_jar_above_line_jdk_still_fails(self):
        self.write_jar(self.target_jar, 21)
        errors, unknown, rows = self.check()
        self.assertEqual([], unknown)
        self.assertEqual('bytecode-fail', self.old_row(rows)[3])
        self.assertTrue(any('JDK 17: old.publisher:demo:1 contains Java 21' in e for e in errors))

    def test_each_missing_relocation_input_remains_unverified(self):
        for path in (self.source_pom, self.target_pom, self.target_jar):
            with self.subTest(path=path.name, group=path.parent.parent.parent):
                content = path.read_bytes()
                path.unlink()
                try:
                    self.assert_unverified()
                finally:
                    path.write_bytes(content)

    def test_destination_must_be_managed_at_same_version_on_the_same_line(self):
        original = self.effective[1].read_text()
        destination = ('<dependency><groupId>new.publisher</groupId><artifactId>demo</artifactId>'
                       '<version>1</version></dependency>')
        for replacement in ('', destination.replace('<version>1</version>', '<version>2</version>')):
            with self.subTest(replacement=replacement):
                self.effective[1].write_text(original.replace(destination, replacement))
                self.assert_unverified()
        self.effective[1].write_text(original)

    def test_source_pom_requires_exact_gav_explicit_pom_packaging_and_group_relocation(self):
        original = self.source_pom.read_text()
        invalid = (
            original.replace('<groupId>old.publisher</groupId>', '<groupId>wrong.publisher</groupId>'),
            original.replace('<artifactId>demo</artifactId>', '<artifactId>other</artifactId>'),
            original.replace('<version>1</version>', '<version>2</version>'),
            original.replace('<packaging>pom</packaging>', ''),
            original.replace('<packaging>pom</packaging>', '<packaging>jar</packaging>'),
            original.replace('<groupId>new.publisher</groupId>', '<groupId>old.publisher</groupId>'),
            original.replace('<groupId>new.publisher</groupId>', ''),
            original.replace('</relocation>', '<artifactId>other</artifactId></relocation>'),
            original.replace('</relocation>', '<version>2</version></relocation>'),
            original.replace('<version>1</version>', '<version>${revision}</version>'),
            '<project><broken>',
        )
        for text in invalid:
            with self.subTest(pom=text):
                self.source_pom.write_text(text)
                self.assert_unverified()
        self.source_pom.write_text(original)

    def test_explicit_same_artifact_and_version_in_relocation_are_accepted(self):
        text = self.source_pom.read_text().replace('</relocation>',
            '<artifactId>demo</artifactId><version>1</version></relocation>')
        self.source_pom.write_text(text)
        errors, unknown, _ = self.check()
        self.assertEqual([], errors)
        self.assertEqual([], unknown)

    def test_destination_pom_requires_exact_gav_and_cannot_relocate_again(self):
        original = self.target_pom.read_text()
        invalid = (
            original.replace('<groupId>new.publisher</groupId>', '<groupId>wrong.publisher</groupId>'),
            original.replace('<artifactId>demo</artifactId>', '<artifactId>other</artifactId>'),
            original.replace('<version>1</version>', '<version>2</version>'),
            original.replace('</project>', '<packaging>pom</packaging></project>'),
            original.replace('</project>', '<distributionManagement><relocation>'
                             '<groupId>third.publisher</groupId></relocation></distributionManagement></project>'),
            '<project><broken>',
        )
        for text in invalid:
            with self.subTest(pom=text):
                self.target_pom.write_text(text)
                self.assert_unverified()
        self.target_pom.write_text(original)

    def test_empty_relocated_jar_cannot_satisfy_real_base_class_evidence(self):
        with zipfile.ZipFile(self.target_jar, 'w') as output:
            output.writestr('META-INF/versions/21/example/Client.class',
                            b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65))
        self.assert_unverified()


if __name__ == '__main__':
    unittest.main()
