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
            migration = policy['replacements']['com.github.hiwepy:' + artifact]
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

    def test_easy4j_component_must_use_its_own_release_line(self):
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
            errors, _, _ = verify(roots, effective, {}, base, None, False)
            self.assertTrue(any('JDK 8: Easy4J io.github.easy4j:component' in error for error in errors), errors)

    def test_easy4j_import_bom_cannot_evade_line_check(self):
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
            errors, _, _ = verify(roots, effective, {}, base, None, False)
            self.assertTrue(any('JDK 8: Easy4J io.github.easy4j:template-bom' in error for error in errors), errors)

    def test_multirelease_java21_does_not_reject_java8_base(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / 'example.jar'
            with zipfile.ZipFile(jar, 'w') as output:
                output.writestr('example/A.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 52))
                output.writestr('META-INF/versions/21/example/A.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65))
            self.assertEqual(bytecode(jar), {8: 1})

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


if __name__ == '__main__':
    unittest.main()
