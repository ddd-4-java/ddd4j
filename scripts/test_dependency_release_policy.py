"""第三方正式版本的固定策略与兼容性门禁回归。"""
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from verify_dependency_alignment import verify


class ReleasePolicyTest(unittest.TestCase):
    def test_release_pin_preserves_missing_and_incompatible_artifact_failures(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            roots, effective = [], []
            for index in range(3):
                root = base / str(index)
                (root / 'ddd4j-dependencies').mkdir(parents=True)
                pom = root / 'ddd4j-dependencies/pom.xml'
                pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><dependencyManagement><dependencies><dependency>
                    <groupId>io.github.easy4j</groupId><artifactId>easyexcel</artifactId>
                    <version>5.0.1</version></dependency></dependencies></dependencyManagement></project>''')
                roots.append(root)
                effective.append(pom)
            policy = {'dependency_versions': {'io.github.easy4j:easyexcel': '5.0.1'}}
            errors, unknown, _ = verify(roots, effective, policy, base, None, True)
            self.assertEqual(3, len(unknown))
            self.assertTrue(all(error.startswith('Unverified artifact:') for error in errors), errors)
            jar = base / 'io/github/easy4j/easyexcel/5.0.1/easyexcel-5.0.1.jar'
            jar.parent.mkdir(parents=True)
            for major, rejected in ((65, True), (52, False)):
                with zipfile.ZipFile(jar, 'w') as output:
                    output.writestr('example/Excel.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, major))
                errors, unknown, _ = verify(roots, effective, policy, base, None, True)
                self.assertFalse(unknown)
                self.assertEqual(rejected, bool(errors), errors)
            effective[2].write_text(effective[2].read_text().replace('5.0.1', '3.0.x.20260630-SNAPSHOT'))
            errors, _, _ = verify(roots, effective, policy, base, None, True)
            self.assertTrue(any('expected 5.0.1' in error for error in errors), errors)


if __name__ == '__main__':
    unittest.main()
