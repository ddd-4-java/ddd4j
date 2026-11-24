"""独立 BOM 版本归属门禁回归；由 CI 执行，不依赖网络或 Maven。"""
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import dependency_version_owners as owners
from test_dependency_family_alignment import FAMILY, model, owner, tree


class VersionOwnershipTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'ddd4j-dependencies').mkdir()
        (self.root / 'pom.xml').write_text(
            '<project><properties><java.version>17</java.version></properties></project>')
        self.policy = {
            'jdk': 17, 'spring': 6,
            'boms': [{'bom': 'org.slf4j:slf4j-bom', 'property': 'slf4j.version',
                      'priority': 0, 'includes': ['org.slf4j:*']}],
        }
        self.write_source()

    def write_source(self, binding='', props='', extra_import=''):
        (self.root / 'ddd4j-dependencies/pom.xml').write_text(
            '<project><properties><slf4j.version>2.0.18</slf4j.version>' + props +
            '</properties><dependencyManagement><dependencies>'
            '<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-bom</artifactId>'
            '<version>${slf4j.version}</version><type>pom</type><scope>import</scope>'
            '</dependency>' + extra_import + binding + '</dependencies></dependencyManagement></project>')

    @staticmethod
    def reference(deps):
        return {'owner-000': model('owner-000', deps)}

    def test_clean_bom_detects_corruption_in_central_table_itself(self):
        contracts = owners.contracts(self.root, self.reference([
            ('org.slf4j', 'jul-to-slf4j', '2.0.18')]), self.policy)
        base = owner()
        deps = base.find('./dependencyManagement/dependencies')
        deps.append(ET.fromstring('<dependency><groupId>org.slf4j</groupId>'
                                 '<artifactId>jul-to-slf4j</artifactId><version>1.7.36</version></dependency>'))
        result = FAMILY.audit({'ddd4j-dependencies': base}, {}, set(), contracts, self.policy)
        self.assertTrue(any(e.get('coordinate') == 'org.slf4j:jul-to-slf4j' and
                            e.get('expected') == '2.0.18' for e in result['errors']))

    def test_parent_third_party_exports_do_not_steal_another_family(self):
        contracts = owners.contracts(self.root, self.reference([
            ('org.slf4j', 'slf4j-api', '2.0.18'),
            ('io.micrometer', 'micrometer-core', '1.10.4')]), self.policy)
        self.assertNotIn(('io.micrometer', 'micrometer-core', 'jar', ''), contracts)

    def test_rejects_central_binding_contradicting_clean_bom(self):
        self.write_source('<dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId>'
                          '<version>1.7.36</version></dependency>')
        with self.assertRaisesRegex(ValueError, 'contradicts upstream'):
            owners.contracts(self.root, self.reference([
                ('org.slf4j', 'jul-to-slf4j', '2.0.18')]), self.policy)

    def test_missing_or_empty_reference_cannot_report_success(self):
        for models in ({}, self.reference([])):
            with self.subTest(models=models), self.assertRaises(ValueError):
                owners.contracts(self.root, models, self.policy)

    def test_reference_preserves_components_with_independent_versions(self):
        self.policy['boms'][0]['includes'] = ['io.micrometer:*']
        contracts = owners.contracts(self.root, self.reference([
            ('io.micrometer', 'micrometer-core', '1.17.2'),
            ('io.micrometer', 'context-propagation', '1.2.1')]), self.policy)
        self.assertEqual('1.2.1', contracts[('io.micrometer', 'context-propagation', 'jar', '')]['version'])

    def test_conflicting_upstream_owners_fail_instead_of_first_one_winning(self):
        self.policy['boms'].append({'bom': 'demo:platform', 'property': 'platform.version',
                                   'priority': 1, 'includes': ['org.slf4j:*']})
        models = self.reference([('org.slf4j', 'slf4j-api', '2.0.18')])
        models['owner-001'] = model('owner-001', [('org.slf4j', 'slf4j-api', '1.7.36')])
        with self.assertRaisesRegex(ValueError, 'ambiguous component ownership'):
            owners.contracts(self.root, models, self.policy)

    def test_platform_cannot_move_ahead_of_component_owner(self):
        self.write_source(extra_import='<dependency><groupId>demo</groupId><artifactId>platform</artifactId>'
                          '<version>${platform.version}</version><type>pom</type><scope>import</scope></dependency>',
                          props='<platform.version>1</platform.version>')
        self.policy['boms'][0]['priority'] = 200
        self.policy['boms'].append({'bom': 'demo:platform', 'property': 'platform.version',
                                   'priority': 1, 'includes': ['demo:*']})
        errors = owners.source_errors(self.root, {}, self.policy)
        self.assertTrue(any(e['code'] == 'bom-owner-priority' for e in errors))

    def test_cycles_and_undefined_properties_fail_closed(self):
        for props in ({'a': '${b}', 'b': '${a}'}, {'a': '${missing}'}):
            with self.subTest(props=props), self.assertRaises(ValueError):
                owners.resolve('${a}', props)

    def test_new_bom_requires_registered_owner(self):
        self.write_source(extra_import='<dependency><groupId>demo</groupId><artifactId>demo-bom</artifactId>'
                          '<version>1</version><type>pom</type><scope>import</scope></dependency>')
        errors = owners.source_errors(self.root, {}, self.policy)
        self.assertTrue(any(e['code'] == 'bom-owner-coverage' for e in errors))

    def test_property_alias_and_independent_release_are_checked(self):
        self.policy['required_aliases'] = {'grpc-bom.version': '${grpc.version}'}
        self.policy['independent_properties'] = ['selenium-htmlunit.version']
        self.write_source(props='<grpc-bom.version>1.56.1</grpc-bom.version>'
                          '<selenium-htmlunit.version>${slf4j.version}</selenium-htmlunit.version>')
        errors = owners.source_errors(self.root, {}, self.policy)
        self.assertEqual({'family-property-alias', 'independent-version-property'},
                         {e['code'] for e in errors})

    def test_updater_policy_cannot_disagree_with_current_source(self):
        (self.root / '.github').mkdir()
        (self.root / '.github/dependency-policy.json').write_text(json.dumps({
            'fixed_properties': {'slf4j.version': '2.0.19'}, 'branches': {'feature/2.0.x': {}}}))
        self.policy['update_policy'] = {'file': '.github/dependency-policy.json', 'branch': 'feature/2.0.x'}
        errors = owners.source_errors(self.root, {}, self.policy)
        self.assertTrue(any(e['code'] == 'update-policy-version-drift' for e in errors))

    def test_module_property_cannot_shadow_central_owner(self):
        (self.root / 'demo').mkdir()
        (self.root / 'demo/pom.xml').write_text('<project><properties>'
            '<slf4j.version>1.7.36</slf4j.version></properties></project>')
        errors = owners.source_errors(self.root, {'demo': 'demo'}, self.policy)
        self.assertTrue(any(e['code'] == 'module-version-property-shadow' for e in errors))

    def test_cxf_wss4j_cohort_rejects_older_signature_confirmation_implementation(self):
        base = owner()
        ET.SubElement(base.find('properties'), 'wss4j.version').text = '4.0.3'
        module = 'ddd4j-web-webmvc'
        actual = tree(module, [('org.apache.wss4j', 'wss4j-ws-security-dom', '4.0.2')])
        policy = {'family_properties': {'org.apache.wss4j': 'wss4j.version'}}
        result = FAMILY.audit({'ddd4j-dependencies': base, module: model(module)},
                              {module: actual}, {module}, {}, policy)
        self.assertTrue(any(e['code'] == 'resolved-version' and e['expected'] == '4.0.3'
                            for e in result['errors']))

    def test_retirement_distinguishes_classic_http_and_current_xml_apis(self):
        policy = owners.load_policy(Path(__file__).parents[1])
        for group, artifact in [('org.apache.httpcomponents', 'httpclient'),
                                ('org.apache.httpcomponents', 'httpcore'),
                                ('org.apache.xmlbeans', 'xmlbeans'),
                                ('javax.cache', 'cache-api'), ('javax.money', 'money-api')]:
            with self.subTest(group=group, artifact=artifact):
                self.assertIsNone(owners.retired_rule(policy, group, artifact))
        for artifact in ('httpasyncclient', 'httpcore-nio', 'httpcore-osgi'):
            self.assertIsNotNone(owners.retired_rule(policy, 'org.apache.httpcomponents', artifact))

    def retired_policy(self):
        return {**self.policy, 'retired_dependencies': [{
            'coordinates': ['org.apache.cxf:cxf-rt-databinding-xmlbeans', 'org.bouncycastle:*-jdk15on'],
            'reason': 'retired legacy dependency', 'replacement': 'maintained family'}]}

    def test_retired_dependency_in_profile_is_rejected_before_maven(self):
        self.write_source()
        p = self.root / 'ddd4j-dependencies/pom.xml'
        p.write_text(p.read_text().replace('</project>', '<profiles><profile><id>legacy</id>'
            '<dependencies><dependency><groupId>org.apache.cxf</groupId>'
            '<artifactId>cxf-rt-databinding-xmlbeans</artifactId><version>3.1.18</version>'
            '</dependency></dependencies></profile></profiles></project>'))
        errors = owners.source_errors(self.root, {}, self.retired_policy())
        self.assertTrue(any(e['code'] == 'retired-source-dependency' for e in errors))

    def test_retired_exclusion_is_allowed_and_modern_xmlbeans_is_not_cxf_binding(self):
        self.write_source(binding='<dependency><groupId>org.apache.xmlbeans</groupId>'
            '<artifactId>xmlbeans</artifactId><version>5.3.0</version><exclusions><exclusion>'
            '<groupId>org.apache.cxf</groupId><artifactId>cxf-rt-databinding-xmlbeans</artifactId>'
            '</exclusion></exclusions></dependency>')
        self.assertEqual([], owners.source_errors(self.root, {}, self.retired_policy()))

    def test_transitive_retired_dependency_reports_introducing_path(self):
        module = 'ddd4j-data-crypto'
        actual = tree(module, [('vendor', 'sdk', '1')])
        actual['children'][0]['children'] = [dict(groupId='org.bouncycastle', artifactId='bcprov-jdk15on',
            version='1.70', scope='test', classifier='tests', children=[])]
        result = FAMILY.audit({'ddd4j-dependencies': owner(), module: model(module)},
                              {module: actual}, {module}, {}, self.retired_policy())
        error = next(e for e in result['errors'] if e['code'] == 'retired-dependency')
        self.assertEqual('vendor:sdk:1', error['path'][-2])
        self.assertEqual('org.bouncycastle:bcprov-jdk15on:1.70', error['path'][-1])
        self.assertEqual('tests', error['classifier'])

    def test_inherited_retired_management_is_reported_without_hiding_from_scan(self):
        base = owner()
        base.find('./dependencyManagement/dependencies').append(ET.fromstring('<dependency>'
            '<groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk15on</artifactId>'
            '<version>1.70</version></dependency>'))
        result = FAMILY.audit({'ddd4j-dependencies': base}, {}, set(), {}, self.retired_policy())
        self.assertEqual([], result['errors'])
        self.assertEqual('org.bouncycastle:bcprov-jdk15on', result['retired_managed_entries'][0]['coordinate'])
        inventory = FAMILY.inventories({'ddd4j-dependencies': base}, {})
        self.assertTrue(any(c['name'] == 'bcprov-jdk15on' for c in inventory['managed']['components']))

    def test_compatibility_override_does_not_reversion_independent_vertx_tools(self):
        policy = {'runtime_overrides': [{'modules': ['*quarkus*'], 'includes': ['io.vertx:*'],
                   'property': 'compat', 'default_prefixes': ['4.', '5.']}]}
        props = {'compat': '4.5.36'}
        self.assertEqual('4.5.36', owners.runtime_expected(policy, props, 'runtime-quarkus',
                         'io.vertx', 'vertx-core', '5.2.3'))
        self.assertIsNone(owners.runtime_expected(policy, props, 'runtime-quarkus',
                          'io.vertx', 'vertx-docgen-processor', '0.9.9'))
        self.assertIsNone(owners.runtime_expected(policy, props, 'runtime-vertx',
                          'io.vertx', 'vertx-core', '5.2.3'))

    def test_helidon_runtime_and_sample_use_platform_inject_version(self):
        policy = owners.load_policy(Path(__file__).resolve().parents[1])
        props = {'jakarta-inject.version': '2.0.1', 'helidon-jakarta-inject.version': '2.0.0'}
        scenarios = {'ddd4j-runtime-helidon': ('2.0.0', '2.0.1'),
                     'ddd4j-sample-helidon-cqrs': ('2.0.0', '2.0.1')}
        for module, (expected, rejected) in scenarios.items():
            for version in (expected, rejected):
                with self.subTest(module=module, version=version):
                    baseline = owner()
                    for name, value in props.items():
                        ET.SubElement(baseline.find('properties'), name).text = value
                    consumers = model(module)
                    direct = ET.SubElement(ET.SubElement(consumers, 'dependencies'), 'dependency')
                    ET.SubElement(direct, 'groupId').text = 'jakarta.inject'
                    ET.SubElement(direct, 'artifactId').text = 'jakarta.inject-api'
                    ET.SubElement(direct, 'version').text = version
                    actual = tree(module, [('jakarta.inject', 'jakarta.inject-api', version)])
                    result = FAMILY.audit({'ddd4j-dependencies': baseline, module: consumers},
                                          {module: actual}, {module}, {}, policy)
                    errors = [error for error in result['errors']
                              if error.get('coordinate') == 'jakarta.inject:jakarta.inject-api']
                    self.assertEqual(set() if version == expected else {'declared', 'resolved'},
                                     {error['scope'] for error in errors})
                    self.assertTrue(all(error['expected'] == expected for error in errors))

    def test_compatibility_test_classifier_does_not_change_default_runtime_contract(self):
        self.write_source(binding='<dependency><groupId>io.vertx</groupId><artifactId>vertx-core</artifactId>'
                          '<version>${compat}</version><type>jar</type><classifier>tests</classifier></dependency>',
                          props='<compat>4.5.36</compat>')
        self.policy['boms'][0]['includes'] = ['io.vertx:*']
        self.policy['central_overrides'] = {'io.vertx:vertx-core:jar:tests': '${compat}'}
        contracts = owners.contracts(self.root, self.reference([
            ('io.vertx', 'vertx-core', '5.2.3')]), self.policy)
        classified = contracts[('io.vertx', 'vertx-core', 'jar', 'tests')]
        self.assertEqual('5.2.3', classified['version'])
        self.assertEqual('4.5.36', classified['managed_version'])
        module = 'ddd4j-runtime-vertx'
        actual = tree(module, [('io.vertx', 'vertx-core', '4.5.36')])
        actual['children'][0]['classifier'] = 'tests'
        result = FAMILY.audit({'ddd4j-dependencies': owner(), module: model(module)},
                              {module: actual}, {module}, contracts, self.policy)
        self.assertTrue(any(e['code'] == 'resolved-version' and e['expected'] == '5.2.3'
                            for e in result['errors']))

    def test_exact_classifier_keeps_its_exporting_bom_version(self):
        coordinate = 'org.slf4j:slf4j-api:jar:classes'
        self.policy['boms'].append({'bom': 'demo:platform', 'property': 'platform.version',
                                   'priority': 1, 'includes': ['demo:*', coordinate]})
        models = self.reference([('org.slf4j', 'slf4j-api', '2.0.18')])
        models['owner-001'] = model('owner-001', [('demo', 'common', '1')])
        deps = models['owner-001'].find('./dependencyManagement/dependencies')
        deps.append(ET.fromstring('<dependency><groupId>org.slf4j</groupId>'
                                 '<artifactId>slf4j-api</artifactId><classifier>classes</classifier>'
                                 '<version>1.7.36</version></dependency>'))
        contracts = owners.contracts(self.root, models, self.policy)
        self.assertEqual({'version': '1.7.36', 'owner': 'demo:platform'},
                         contracts[tuple(coordinate.split(':'))])
        self.assertEqual('2.0.18', contracts[('org.slf4j', 'slf4j-api', 'jar', '')]['version'])
        actual = tree('demo', [('org.slf4j', 'slf4j-api', '1.7.36')])
        item = actual['children'][0]
        item['classifier'] = 'classes'
        models = {'ddd4j-dependencies': owner(), 'demo': model('demo')}
        result = FAMILY.audit(models, {'demo': actual}, {'demo'}, contracts, self.policy)
        self.assertEqual([], result['errors'])
        for version, classifier, expected in [('2.0.18', 'classes', '1.7.36'),
                                              ('1.7.36', 'sources', '2.0.18')]:
            with self.subTest(version=version, classifier=classifier):
                item.update(version=version, classifier=classifier)
                result = FAMILY.audit(models, {'demo': actual}, {'demo'}, contracts, self.policy)
                self.assertTrue(any(error['code'] == 'resolved-version' and error['expected'] == expected
                                    for error in result['errors']))

    def test_exact_classifier_must_be_exported_by_declared_bom(self):
        self.policy['boms'][0]['includes'].append('org.slf4j:slf4j-api:jar:classes')
        with self.assertRaisesRegex(ValueError, 'registered classified artifact'):
            owners.contracts(self.root, self.reference([('org.slf4j', 'slf4j-api', '2.0.18')]), self.policy)

    def test_classified_artifact_obeys_the_same_owner_version_filter(self):
        self.policy['boms'][0]['includes'].append('demo:annotations:jar:sources')
        self.policy['boms'][0]['version_suffix'] = '.Final'
        models = self.reference([('org.slf4j', 'slf4j-api', '2.0.18.Final')])
        deps = models['owner-000'].find('./dependencyManagement/dependencies')
        deps.append(ET.fromstring('<dependency><groupId>demo</groupId><artifactId>annotations</artifactId>'
                                 '<classifier>sources</classifier><version>2.0.18.Preview</version></dependency>'))
        with self.assertRaisesRegex(ValueError, 'registered classified artifact'):
            owners.contracts(self.root, models, self.policy)

    def test_classifier_uses_owner_contract_and_module_specific_line(self):
        module = 'ddd4j-runtime-quarkus'
        actual = tree(module, [('io.netty', 'netty-transport-native-epoll', '4.2.21.Final')])
        actual['children'][0]['classifier'] = 'linux-x86_64'
        policy = {'runtime_overrides': [{'modules': ['*quarkus*'], 'includes': ['io.netty:*'],
                                        'property': 'netty-4_1.version'}]}
        contracts = {('io.netty', 'netty-transport-native-epoll', 'jar', ''):
                     {'version': '4.2.21.Final', 'owner': 'io.netty:netty-bom'}}
        result = FAMILY.audit({'ddd4j-dependencies': owner(), module: model(module)},
                              {module: actual}, {module}, contracts, policy)
        self.assertTrue(any(e['code'] == 'resolved-version' and e['expected'] == '4.1.141.Final'
                            for e in result['errors']))


if __name__ == '__main__':
    unittest.main()
