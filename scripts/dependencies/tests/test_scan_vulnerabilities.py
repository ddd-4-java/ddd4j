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

    def test_reports_cover_every_reactor_module_and_managed_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'project'
            (root / 'ddd4j-dependencies').mkdir(parents=True)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><packaging>pom</packaging></project>')
            module = root / 'module'
            module.mkdir()
            (module / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><packaging>jar</packaging></project>')
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
                if 'makeBom' in ' '.join(command):
                    bom = module / 'target' / 'weekly-deps.json'
                    bom.parent.mkdir()
                    bom.write_text(json.dumps({'bomFormat': 'CycloneDX', 'specVersion': '1.6',
                                               'components': [{'group': 'example', 'name': 'resolved',
                                                               'version': '3', 'purl': 'pkg:maven/example/resolved@3'}]}))
                    return
                report = Path(command[command.index('--output') + 1])
                if 'reactor-bom.cdx.json' in command[-1]:
                    data = {'Results': [{'Target': 'reactor-bom.cdx.json',
                                         'Packages': [{'Identifier': {'PURL': 'pkg:maven/example/resolved@3'}}]}]}
                else:
                    data = {'Results': [{'Target': 'managed-bom.cdx.json', 'Packages': packages}]}
                report.write_text(json.dumps(data))

            with patch.object(scanner, 'create_sbom', return_value={'components': components}), \
                    patch.object(scanner.subprocess, 'run', side_effect=execute) as run:
                self.assertEqual(scanner.scan(root, output, 'feature/2.0.x', 7), 0)
            self.assertEqual(run.call_count, 3)
            self.assertEqual(len(json.loads((output / 'managed-bom.cdx.json').read_text())['components']), 2)
            self.assertTrue((output / 'reactor-bom.cdx.json').is_file())
            self.assertFalse(any(call.args[0][1] == 'fs' for call in run.call_args_list))

    def test_missing_reactor_module_sbom_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'project'
            (root / 'ddd4j-dependencies').mkdir(parents=True)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><packaging>pom</packaging></project>')
            module = root / 'module'
            module.mkdir()
            (module / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><packaging>jar</packaging></project>')
            stale = module / 'target' / 'weekly-deps.json'
            stale.parent.mkdir()
            stale.write_text(json.dumps({'bomFormat': 'CycloneDX', 'components': [
                {'group': 'example', 'name': 'stale', 'version': '1',
                 'purl': 'pkg:maven/example/stale@1'}]}))
            output = Path(directory) / 'reports'

            def execute(command, **kwargs):
                report = Path(command[command.index('--output') + 1])
                report.write_text(json.dumps({'Results': [{'Target': 'pom.xml', 'Packages': []}]}))

            with patch.object(scanner, 'create_sbom', return_value={'components': [{'name': 'a'}]}), \
                    patch.object(scanner.subprocess, 'run', side_effect=execute) as run:
                with self.assertRaises(ValueError):
                    scanner.scan(root, output, 'feature/2.0.x', 7)
            self.assertFalse(stale.exists())
            self.assertEqual(run.call_count, 1)


if __name__ == '__main__':
    unittest.main()
