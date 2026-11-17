"""依赖家族门禁：真实解析混版、子 BOM 覆盖、兼容线与输入覆盖。"""
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
import tempfile
import struct
import zipfile


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
    props = {'netty.version': '4.2.21.Final', 'netty-compat4.version': '4.1.141.Final',
             'asm.version': '9.11.1', 'jackson3.version': '3.3.0', 'artemis.version': '2.57.0',
             'vertx.version': '5.2.3', 'vertx-compat4.version': '4.5.36',
             'netty-tcnative.version': '2.0.85.Final', 'logback.version': '1.5.38'}
    return model('ddd4j-dependencies', [('io.projectreactor', 'reactor-core', '3.8.8'),
                 ('io.vertx', 'vertx-core', '5.2.3'), ('io.vertx', 'vertx-docgen-processor', '0.9.9')],
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
        self.assertEqual([], self.check(deps, 'ddd4j-runtime-quarkus')['errors'])

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


if __name__ == '__main__':
    unittest.main()
