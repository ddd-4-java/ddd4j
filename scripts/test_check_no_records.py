import unittest
import subprocess
import sys
import tempfile
from pathlib import Path

from check_no_records import declarations


class NoRecordsTest(unittest.TestCase):
    def test_detects_public_nested_and_local_records(self):
        source = 'public record One(int x) {}\nclass A { record Two() {} void f(){ record Three() {} } }'
        self.assertEqual(list(declarations(source)), [(1, 'One'), (2, 'Two'), (2, 'Three')])

    def test_detects_generic_multiline_record(self):
        self.assertEqual(list(declarations('record\nBox<T extends Map<String, List<X>>>\n(T value) {}')), [(1, 'Box')])

    def test_ignores_comments_strings_and_text_blocks(self):
        source = '''// record One() {}
/* record Two() {} */
String a = "record Three() {}";
String b = """
record Four() {}
""";
class A { void record() {} int record = 1; }
'''
        self.assertEqual(list(declarations(source)), [])

    def test_detects_unicode_escaped_keyword(self):
        self.assertEqual(list(declarations(r'public \u0072ecord One(int x) {}')), [(1, 'One')])

    def test_reports_line_after_comment(self):
        self.assertEqual(list(declarations('/* one\ntwo */\nrecord One() {}')), [(3, 'One')])


class DefaultDiscoveryTest(unittest.TestCase):
    def run_checker(self, root):
        return subprocess.run([sys.executable, str(Path(__file__).with_name('check_no_records.py').resolve())],
                              cwd=root, capture_output=True, text=True)

    def test_deleted_tracked_java_does_not_crash(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            source = root / 'Deleted.java'
            source.write_text('class Deleted {}')
            subprocess.run(['git', '-C', directory, 'add', 'Deleted.java'], check=True)
            source.unlink()
            result = self.run_checker(root)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_new_untracked_java_is_checked_but_ignored_output_is_not(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            (root / '.gitignore').write_text('target/\n')
            (root / 'target').mkdir()
            (root / 'target' / 'Ignored.java').write_text('record Ignored() {}')
            self.assertEqual(self.run_checker(root).returncode, 0)
            (root / 'New.java').write_text('record New() {}')
            result = self.run_checker(root)
            self.assertEqual(result.returncode, 1, result.stdout)
            self.assertIn('New.java:1:', result.stdout)
            self.assertNotIn('Ignored.java', result.stdout)


if __name__ == '__main__':
    unittest.main()
