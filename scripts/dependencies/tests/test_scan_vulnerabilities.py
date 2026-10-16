import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).parents[1] / 'scan_vulnerabilities.py'
spec = importlib.util.spec_from_file_location('scanner', path)
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class ScannerTests(unittest.TestCase):
    def test_cvss_threshold_and_unknown_scores_fail_closed(self):
        self.assertEqual(scanner.vulnerability_score({'CVSS': {'nvd': {'V3Score': 8.1}}}), 8.1)
        self.assertEqual(scanner.vulnerability_score({'Severity': 'HIGH'}), 7.0)
        self.assertEqual(scanner.vulnerability_score({'CVSS': {'nvd': {'V3Score': 0}},
                                                     'Severity': 'HIGH'}), 7.0)
        self.assertEqual(scanner.vulnerability_score({}), 10.0)

    def test_reports_cover_pom_and_every_managed_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'project'
            (root / 'ddd4j-dependencies').mkdir(parents=True)
            output = Path(directory) / 'reports'
            components = [
                {'group': 'example', 'name': 'pinned', 'version': '1'},
                {'group': 'example', 'name': 'excluded', 'version': '2'},
            ]
            packages = [
                {'Name': row['name'], 'Identifier': {'PURL':
                 f"pkg:maven/{row['group']}/{row['name']}@{row['version']}"}}
                for row in components
            ]

            def execute(command, **kwargs):
                report = Path(command[command.index('--output') + 1])
                if command[1] == 'fs':
                    data = {'Results': [{'Target': 'module/pom.xml', 'Packages': [{'Name': 'test-scope'}]}]}
                else:
                    data = {'Results': [{'Target': 'managed-bom.cdx.json', 'Packages': packages}]}
                report.write_text(json.dumps(data))

            with patch.object(scanner, 'create_sbom', return_value={'components': components}), \
                    patch.object(scanner.subprocess, 'run', side_effect=execute) as run:
                self.assertEqual(scanner.scan(root, output, 'feature/2.0.x', 7), 0)
            self.assertEqual(run.call_count, 2)
            self.assertEqual(len(json.loads((output / 'managed-bom.cdx.json').read_text())['components']), 2)

    def test_missing_pom_coverage_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'project'
            (root / 'ddd4j-dependencies').mkdir(parents=True)
            output = Path(directory) / 'reports'

            def execute(command, **kwargs):
                report = Path(command[command.index('--output') + 1])
                report.write_text(json.dumps({'Results': [{'Target': 'pom.xml', 'Packages': []}]}))

            with patch.object(scanner, 'create_sbom', return_value={'components': [{'name': 'a'}]}), \
                    patch.object(scanner.subprocess, 'run', side_effect=execute):
                with self.assertRaises(ValueError):
                    scanner.scan(root, output, 'feature/2.0.x', 7)


if __name__ == '__main__':
    unittest.main()
