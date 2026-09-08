"""依赖清单门禁回归：缺失、BOM 覆盖、坐标替代及 multi-release JAR。"""
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from verify_dependency_alignment import bytecode, verify


def pom(artifact='demo', version='1', group='example'):
    return f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><dependencyManagement><dependencies>
    <dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version></dependency>
    </dependencies></dependencyManagement></project>'''


class DependencyAlignmentTest(unittest.TestCase):
    def test_multirelease_java21_does_not_reject_java8_base(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / 'example.jar'
            with zipfile.ZipFile(jar, 'w') as output:
                output.writestr('example/A.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 52))
                output.writestr('META-INF/versions/21/example/A.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65))
            self.assertEqual(bytecode(jar), {8: 1})

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

    def test_missing_lower_line_component_fails(self):
        errors, _, _ = self.exercise(pom('unrelated'))
        self.assertIn('JDK 17: missing example:demo', errors)
        self.assertIn('JDK 21: missing example:demo', errors)

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
        self.assertIn('JDK 21: missing example:deleted', errors)

    def test_unsupported_is_never_counted_as_success(self):
        policy = {'unsupported': {'example:demo': {'17': {'reason': 'requires Spring 5'}}}}
        errors, _, rows = self.exercise(pom(), policy)
        self.assertIn('JDK 17: unsupported example:demo: requires Spring 5', errors)
        self.assertTrue(any(row[0] == 'example:demo' and row[3] == 'unsupported' for row in rows))

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
