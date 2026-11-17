"""依赖家族门禁：真实解析混版、子 BOM 覆盖、兼容线与输入覆盖。"""
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
import tempfile
import struct
import zipfile
from types import SimpleNamespace
from unittest.mock import patch


CHECKER = Path(__file__).with_name('verify_dependency_family_alignment.py')
SPEC = importlib.util.spec_from_file_location('family', CHECKER) if CHECKER.exists() else None
FAMILY = importlib.util.module_from_spec(SPEC) if SPEC else None
if SPEC:
    SPEC.loader.exec_module(FAMILY)


def model(name, deps=(), props=''):
    dependencies = ''.join(f'<dependency><groupId>{g}</groupId><artifactId>{a}</artifactId>'
                           f'<version>{v}</version></dependency>' for g, a, v in deps)
    return ET.fromstring(f'<project><artifactId>{name}</artifactId><packaging>jar</packaging>'
                         f'<properties>{props}</properties><dependencyManagement><dependencies>'
                         f'{dependencies}</dependencies></dependencyManagement></project>')


def owner():
    props = {'netty.version': '4.2.21.Final', 'netty-4_1.version': '4.1.141.Final',
             'asm.version': '9.11.1', 'jackson3.version': '3.3.0', 'artemis.version': '2.57.0',
             'vertx.version': '5.2.3', 'vertx-compat4.version': '4.5.36',
             'netty-tcnative.version': '2.0.85.Final', 'logback.version': '1.6.7'}
    props.update({'json-smart.version': '2.6.1', 'zstd-jni.version': '1.5.7-28',
                  'json.version': '20260814', 'bouncycastle-lts.version': '2.73.13',
                  'glassfish-jaxb.version': '4.0.9', 'shiro.version': '3.0.2'})
    props.update({'micrometer.version': '1.17.2', 'micrometer-tracing.version': '1.7.2'})
    props['helidon-jersey.version'] = '3.0.18'
    props['quarkus-hibernate.version'] = '7.4.9.Final'
    props['quarkus-agroal.version'] = '3.2.1'
    props['hibernate.version'] = '6.6.59.Final'
    props['jakarta.persistence-api.version'] = '3.2.0'
    return model('ddd4j-dependencies', [('io.projectreactor', 'reactor-core', '3.8.8'),
                 ('io.vertx', 'vertx-core', '5.2.3'), ('io.vertx', 'vertx-docgen-processor', '0.9.9'),
                 ('org.glassfish.jersey.core', 'jersey-common', '3.1.12'),
                 ('org.glassfish.hk2', 'hk2-locator', '3.0.6'),
                 ('io.dropwizard.metrics', 'metrics-core', '4.2.39'),
                 ('org.glassfish.jaxb', 'jaxb-runtime', '4.0.9'),
                 ('io.micrometer', 'context-propagation', '1.2.1'),
                 ('org.hibernate.orm', 'hibernate-graalvm', '6.6.59.Final'),
                 ('jakarta.persistence', 'jakarta.persistence-api', '3.1.0')],
                 ''.join(f'<{k}>{v}</{k}>' for k, v in props.items()))


def tree(name, deps=()):
    return dict(groupId='io.ddd4j', artifactId=name, version='2.0.x.20260630-SNAPSHOT',
                children=[dict(groupId=g, artifactId=a, version=v, scope='compile', children=[])
                          for g, a, v in deps])


class DependencyFamilyAlignmentTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(FAMILY, 'dependency family checker must be implemented')

    def check(self, deps=(), name='ddd4j-web-webflux', actual=None):
        models = {'ddd4j-dependencies': owner(), name: model(name, deps)}
        return FAMILY.audit(models, {name: tree(name, deps if actual is None else actual)}, {name})

    def test_rejects_old_reactor_from_child_bom(self):
        errors = self.check([('io.projectreactor', 'reactor-core', '3.7.12')])['errors']
        self.assertTrue(any(e['coordinate'] == 'io.projectreactor:reactor-core' for e in errors))

    def test_rejects_split_netty_core_in_actual_classpath(self):
        deps = [('io.netty', 'netty-common', '4.1.141.Final'),
                ('io.netty', 'netty-codec-http', '4.2.21.Final')]
        errors = self.check(deps, 'ddd4j-runtime-quarkus')['errors']
        self.assertTrue(any(e['code'] == 'resolved-version' for e in errors))

    def test_accepts_explicit_runtime_compatibility_line(self):
        deps = [('io.netty', 'netty-common', '4.1.141.Final'),
                ('io.vertx', 'vertx-core', '4.5.36'), ('io.netty', 'netty-tcnative-classes', '2.0.85.Final')]
        self.assertEqual([], self.check([], 'ddd4j-runtime-quarkus', actual=deps)['errors'])

    def test_current_micronaut_http_requires_netty42(self):
        self.assertEqual([], self.check([], 'ddd4j-web-micronaut', actual=[
            ('io.netty', 'netty-transport', '4.2.21.Final'), ('io.vertx', 'vertx-core', '5.2.3')])['errors'])

    def test_rejects_native_classifier_old_core(self):
        item = tree('ddd4j-runtime-quarkus', [('io.netty', 'netty-transport-native-epoll', '4.1.128.Final')])
        item['children'][0]['classifier'] = 'linux-x86_64'
        models = {'ddd4j-dependencies': owner(), 'ddd4j-runtime-quarkus': model('ddd4j-runtime-quarkus')}
        self.assertTrue(FAMILY.audit(models, {'ddd4j-runtime-quarkus': item}, {'ddd4j-runtime-quarkus'})['errors'])

    def test_rejects_managed_asm_drift_even_if_unused(self):
        self.assertTrue(self.check([('org.ow2.asm', 'asm-commons', '9.8')], actual=[])['errors'])

    def test_rejects_old_artemis_coordinate(self):
        self.assertTrue(self.check([('org.apache.activemq', 'artemis-core-client', '2.44.0')])['errors'])

    def test_official_bom_relocation_metadata_is_allowed_at_exact_current_version(self):
        self.assertEqual([], self.check([('org.apache.activemq', 'artemis-core-client', '2.57.0')], actual=[])['errors'])

    def test_vertx_independent_docgen_version_follows_bom_instead_of_core(self):
        self.assertEqual([], self.check([('io.vertx', 'vertx-docgen-processor', '0.9.9')])['errors'])

    def test_rejects_unapproved_netty_generation(self):
        self.assertTrue(self.check([('io.netty', 'netty-common', '5.0.0')])['errors'])

    def test_platform_cannot_downgrade_observability_or_aws_management(self):
        for group, artifact, fixed, old in (
                ('io.opentelemetry', 'opentelemetry-api', '1.68.0', '1.54.1'),
                ('io.micrometer', 'micrometer-core', '1.17.2', '1.15.4'),
                ('software.amazon.awssdk', 'cloudfront', '2.59.2', '2.31.66')):
            with self.subTest(group=group):
                base = owner()
                base.find('dependencyManagement/dependencies').append(model('x', [(group, artifact, fixed)]).find('dependencyManagement/dependencies/dependency'))
                models = {'ddd4j-dependencies': base, 'sample': model('sample', [(group, artifact, old)])}
                self.assertTrue(FAMILY.audit(models, {'sample': tree('sample')}, {'sample'})['errors'])

    def test_rejects_central_micrometer_mix_even_when_every_module_matches_it(self):
        mixed = [('io.micrometer', 'micrometer-core', '1.10.4'),
                 ('io.micrometer', 'micrometer-registry-prometheus', '1.7.2')]
        base = owner()
        for dep in model('x', mixed).findall('dependencyManagement/dependencies/dependency'):
            base.find('dependencyManagement/dependencies').append(dep)
        models = {'ddd4j-dependencies': base, 'sample': model('sample', mixed)}
        errors = FAMILY.audit(models, {'sample': tree('sample', mixed)}, {'sample'})['errors']
        for artifact in ('micrometer-core', 'micrometer-registry-prometheus'):
            self.assertTrue(any(e.get('module') == 'ddd4j-dependencies' and
                                e.get('coordinate') == 'io.micrometer:' + artifact and
                                e.get('expected') == '1.17.2' for e in errors))

    def test_metrics_and_tracing_keep_independent_versions(self):
        deps = [('io.micrometer', 'micrometer-core', '1.17.2'),
                ('io.micrometer', 'micrometer-observation', '1.17.2'),
                ('io.micrometer', 'micrometer-registry-prometheus', '1.17.2'),
                ('io.micrometer', 'micrometer-registry-prometheus-simpleclient', '1.17.2'),
                ('io.micrometer', 'micrometer-tracing', '1.7.2'),
                ('io.micrometer', 'micrometer-tracing-bridge-otel', '1.7.2'),
                ('io.micrometer', 'context-propagation', '1.2.1')]
        self.assertEqual([], self.check(deps)['errors'])

    def test_tracing_cannot_use_metrics_version(self):
        errors = self.check([('io.micrometer', 'micrometer-tracing-bridge-otel', '1.17.2')])['errors']
        self.assertTrue(any(e.get('expected') == '1.7.2' for e in errors))

    def test_rejects_old_tracing_even_when_central_management_matches(self):
        deps = [('io.micrometer', 'micrometer-tracing', '1.6.1')]
        base = owner()
        base.find('dependencyManagement/dependencies').append(
            model('x', deps).find('dependencyManagement/dependencies/dependency'))
        errors = FAMILY.audit({'ddd4j-dependencies': base}, {}, set())['errors']
        self.assertTrue(any(e.get('coordinate') == 'io.micrometer:micrometer-tracing' and
                            e.get('expected') == '1.7.2' for e in errors))

    def test_central_jaxb_core_cannot_be_its_own_wrong_baseline(self):
        base = owner()
        base.find('dependencyManagement/dependencies').append(model('x', [
            ('org.glassfish.jaxb', 'jaxb-core', '2.3.9')]).find('dependencyManagement/dependencies/dependency'))
        errors = FAMILY.audit({'ddd4j-dependencies': base}, {}, set())['errors']
        self.assertTrue(any(e.get('coordinate') == 'org.glassfish.jaxb:jaxb-core' and
                            e.get('expected') == '4.0.9' for e in errors))

    def test_rejects_jersey_and_hk2_javax_generation(self):
        for g, a in [('org.glassfish.jersey.core', 'jersey-common'), ('org.glassfish.hk2', 'hk2-locator')]:
            self.assertTrue(self.check([(g, a, '2.46')])['errors'])

    def test_helidon_can_explicitly_choose_jersey30_from_central_property(self):
        self.assertEqual([], self.check([], 'ddd4j-runtime-helidon',
                         actual=[('org.glassfish.jersey.core', 'jersey-common', '3.0.18')])['errors'])

    def test_platform_cannot_downgrade_metrics_jaxb_or_split_hibernate(self):
        for group, artifact, old in [('io.dropwizard.metrics', 'metrics-core', '4.0.1'),
                                     ('org.glassfish.jaxb', 'jaxb-runtime', '2.3.9'),
                                     ('org.hibernate.orm', 'hibernate-graalvm', '6.6.40.Final')]:
            with self.subTest(group=group):
                self.assertTrue(self.check([(group, artifact, old)])['errors'])

    def test_spring6_cannot_receive_jpa32(self):
        self.assertTrue(self.check([], 'ddd4j-runtime-spring',
                        actual=[('jakarta.persistence', 'jakarta.persistence-api', '3.2.0')])['errors'])

    def test_quarkus_can_explicitly_choose_platform_jpa32(self):
        self.assertEqual([], self.check([], 'ddd4j-runtime-quarkus',
                         actual=[('jakarta.persistence', 'jakarta.persistence-api', '3.2.0')])['errors'])

    def test_logback16_is_blocked_for_current_dropwizard_baseline(self):
        base = owner()
        base.find('properties/logback.version').text = '1.6.6'
        self.assertTrue(FAMILY.audit({'ddd4j-dependencies': base}, {}, set())['errors'])

    def test_inventory_includes_imported_management_even_when_unused(self):
        self.assertTrue(hasattr(FAMILY, 'inventories'))
        models = {'ddd4j-dependencies': owner(), 'sample': model('sample', [('unused', 'library', '1')])}
        inventory = FAMILY.inventories(models, {'sample': tree('sample', [('used', 'library', '2')])})
        self.assertIn('unused:library:1', {c['group'] + ':' + c['name'] + ':' + c['version']
                                        for c in inventory['managed']['components']})
        self.assertNotIn('unused:library:1', {c['group'] + ':' + c['name'] + ':' + c['version']
                                           for c in inventory['reactor']['components']})

    def test_managed_ranges_are_recorded_as_policies_instead_of_fake_releases(self):
        self.assertTrue(hasattr(FAMILY, 'inventories'))
        inventory = FAMILY.inventories({'sample': model('sample', [('unused', 'library', '[1,2)')])}, {})
        self.assertEqual([], inventory['managed']['components'])
        self.assertEqual('[1,2)', inventory['unresolved_management'][0]['version'])

    def test_rejects_legacy_android_json_even_in_test_scope(self):
        result = self.check([], actual=[('com.vaadin.external.google', 'android-json', '0.0.20131108.vaadin1')])
        self.assertTrue(any(error['code'] == 'obsolete-json-provider' for error in result['errors']))

    def test_rejects_old_security_dependencies_from_transitive_boms(self):
        for dependency in [('net.minidev', 'json-smart', '2.6.0'),
                           ('net.minidev', 'accessors-smart', '2.6.0'),
                           ('org.bouncycastle', 'bcprov-lts8on', '2.73.12.1'),
                           ('com.github.luben', 'zstd-jni', '1.5.7-11'),
                           ('org.apache.shiro', 'shiro-core', '2.2.1'),
                           ('ch.qos.logback', 'logback-classic', '1.5.38'),
                           ('org.glassfish.jaxb', 'jaxb-runtime', '2.3.9')]:
            with self.subTest(dependency=dependency):
                self.assertTrue(self.check([], actual=[dependency])['errors'])

    def test_rejects_shared_property_downgrade_even_without_version_drift(self):
        base = owner()
        base.find('properties/logback.version').text = '1.5.38'
        result = FAMILY.audit({'ddd4j-dependencies': base}, {}, set())
        self.assertTrue(any(error['code'] == 'security-baseline' for error in result['errors']))

    def test_rejects_default_ehcache_variant_with_jakarta_jaxb(self):
        result = self.check([], actual=[('org.ehcache', 'ehcache', '3.12.0')])
        self.assertTrue(any(error['code'] == 'javax-jaxb-consumer' for error in result['errors']))

    def test_accepts_upgraded_security_dependencies(self):
        dependencies = [('net.minidev', 'json-smart', '2.6.1'),
                        ('net.minidev', 'accessors-smart', '2.6.1'),
                        ('org.bouncycastle', 'bcprov-lts8on', '2.73.13'),
                        ('com.github.luben', 'zstd-jni', '1.5.7-28'),
                        ('org.apache.shiro', 'shiro-core', '3.0.2'),
                        ('ch.qos.logback', 'logback-classic', '1.6.7'),
                        ('org.glassfish.jaxb', 'jaxb-runtime', '4.0.9')]
        self.assertEqual([], self.check([], actual=dependencies)['errors'])

    def test_missing_tree_fails_closed(self):
        self.assertTrue(FAMILY.audit({'ddd4j-dependencies': owner()}, {}, {'demo'})['errors'])

    def test_tree_for_different_module_fails_closed(self):
        self.assertTrue(FAMILY.audit({'ddd4j-dependencies': owner()}, {'demo': tree('other')}, {'demo'})['errors'])

    def test_empty_central_reactor_management_fails_closed(self):
        base = owner()
        base.find('dependencyManagement/dependencies').clear()
        self.assertTrue(FAMILY.audit({'ddd4j-dependencies': base}, {}, set())['errors'])

    def test_nested_dependency_and_test_scope_are_checked(self):
        nested = tree('ddd4j-web-webflux')
        child = tree('third-party', [('tools.jackson.core', 'jackson-core', '3.2.3')])
        child['scope'] = 'test'
        nested['children'] = [child]
        models = {'ddd4j-dependencies': owner(), 'ddd4j-web-webflux': model('ddd4j-web-webflux')}
        self.assertTrue(FAMILY.audit(models, {'ddd4j-web-webflux': nested}, {'ddd4j-web-webflux'})['errors'])

    def test_missing_artifact_is_not_a_bytecode_pass(self):
        self.assertTrue(hasattr(FAMILY, 'audit_bytecode'), 'bytecode gate must be implemented')
        with tempfile.TemporaryDirectory() as repo:
            trees = {'demo': tree('demo', [('example', 'lib', '1')])}
            self.assertTrue(FAMILY.audit_bytecode(trees, Path(repo))['errors'])

    def test_bytecode_gate_checks_classifier_and_rejects_java21(self):
        self.assertTrue(hasattr(FAMILY, 'audit_bytecode'), 'bytecode gate must be implemented')
        with tempfile.TemporaryDirectory() as repo:
            root = Path(repo)
            path = root / 'example/lib/1/lib-1-tests.jar'
            path.parent.mkdir(parents=True)
            with zipfile.ZipFile(path, 'w') as jar:
                jar.writestr('Example.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65))
            trees = {'demo': tree('demo', [('example', 'lib', '1')])}
            trees['demo']['children'][0]['classifier'] = 'tests'
            result = FAMILY.audit_bytecode(trees, root)
            self.assertEqual('bytecode-jdk', result['errors'][0]['code'])

    def test_functional_module_cannot_add_its_own_management(self):
        self.assertTrue(hasattr(FAMILY, 'source_management_errors'))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'pom.xml').write_text('<project><dependencyManagement/></project>')
            self.assertTrue(FAMILY.source_management_errors(root, {'sample': '.'}))

    def test_central_version_owner_can_manage_dependencies(self):
        self.assertTrue(hasattr(FAMILY, 'source_management_errors'))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'pom.xml').write_text('<project><dependencyManagement/></project>')
            self.assertEqual([], FAMILY.source_management_errors(root, {'ddd4j-dependencies': '.'}))


class MicrometerSourceOwnershipTest(unittest.TestCase):
    def write_owner(self, root, late=False, tracing='1.7.2'):
        folder = root / 'ddd4j-dependencies'
        folder.mkdir()
        def bom(group, artifact, version):
            return (f'<dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId>'
                    f'<version>{version}</version><type>pom</type><scope>import</scope></dependency>')
        platform = bom('org.hibernate.orm', 'hibernate-platform', '6.6.59.Final')
        metrics = bom('io.micrometer', 'micrometer-bom', '${micrometer.version}')
        trace = bom('io.micrometer', 'micrometer-tracing-bom', '${micrometer-tracing.version}')
        imports = platform + metrics + trace if late else metrics + trace + platform
        (folder / 'pom.xml').write_text(
            '<project><properties><micrometer.version>1.17.2</micrometer.version>'
            f'<micrometer-tracing.version>{tracing}</micrometer-tracing.version></properties>'
            f'<dependencyManagement><dependencies>{imports}</dependencies></dependencyManagement></project>')

    def test_shared_boms_precede_hibernate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_owner(root)
            self.assertEqual([], FAMILY.source_bom_errors(root))

    def test_hibernate_cannot_precede_metrics_or_tracing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_owner(root, late=True)
            errors = FAMILY.source_bom_errors(root)
            self.assertEqual({'micrometer-bom', 'micrometer-tracing-bom'},
                             {e['artifact'] for e in errors if e['code'] == 'micrometer-bom-priority'})

    def test_tracing_property_cannot_alias_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_owner(root, tracing='${micrometer.version}')
            self.assertTrue(any(e.get('property') == 'micrometer-tracing.version'
                                for e in FAMILY.source_bom_errors(root)))

    def test_wrong_bom_version_property_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_owner(root)
            pom = root / 'ddd4j-dependencies/pom.xml'
            pom.write_text(pom.read_text().replace(
                '<version>${micrometer-tracing.version}</version>',
                '<version>${micrometer.version}</version>'))
            self.assertTrue(any(e['code'] == 'micrometer-bom-owner' for e in FAMILY.source_bom_errors(root)))

    def test_source_only_reports_failure_without_calling_maven(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_owner(root, late=True)
            args = SimpleNamespace(root=root, output=root / 'report', source_only=True)
            with patch.object(FAMILY.spring, 'discover_projects', return_value=(
                    {'ddd4j-dependencies': 'ddd4j-dependencies'}, [])), \
                    patch.object(FAMILY.spring, 'run', side_effect=AssertionError('Maven must not run')), \
                    patch.object(FAMILY.subprocess, 'run', side_effect=AssertionError('Maven must not run')):
                self.assertEqual(1, FAMILY.run(args))
            import json
            report = json.loads((args.output / 'summary.json').read_text())
            self.assertEqual('source-only', report['verification'])
            self.assertEqual('FAIL', report['status'])


class FamilyWorkflowTest(unittest.TestCase):
    def test_jdk17_workflow_preserves_failure_and_runs_real_checks(self):
        path = Path(__file__).parents[1] / '.github/workflows/dependency-family-alignment.yml'
        self.assertTrue(path.exists(), 'dependency family CI must be implemented')
        text = path.read_text()
        for required in ('pull_request:', 'feature/2.0.x', "java-version: '17'",
                         'verify_dependency_family_alignment.py', '--check-bytecode',
                         './mvnw -B -ntp verify', 'if: always()', 'if-no-files-found: error'):
            self.assertIn(required, text)
        self.assertNotIn('continue-on-error', text)
        self.assertLess(text.index('--source-only'), text.index('-DskipTests install'))


if __name__ == '__main__':
    unittest.main()
