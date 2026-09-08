"""依赖属性布局门禁的回归测试。"""

import tempfile
import unittest
from pathlib import Path

from verify_dependency_property_layout import natural_key, verify
from format_dependency_properties import format_properties, rename_easy4j_properties


def pom(body):
    return f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><properties>
        <!-- Global Properties -->
{body[0]}
        <!-- Third-Party Dependencies -->
{body[1]}
        <!-- Maven Dependencies -->
{body[2]}
    </properties></project>'''


class DependencyPropertyLayoutTest(unittest.TestCase):
    def check(self, content):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "pom.xml"
            path.write_text(content)
            return verify(path)

    def test_valid_three_section_layout_passes(self):
        errors = self.check(pom((
            "        <skipTests>false</skipTests>",
            "        <artifact2.version>1</artifact2.version>\n        <artifact10.version>1</artifact10.version>",
            "        <maven-clean-plugin.version>1</maven-clean-plugin.version>",
        )))
        self.assertEqual(errors, [])

    def test_easy4j_prefix_fails(self):
        errors = self.check(pom(("", "        <easy4j-hitool.version>2</easy4j-hitool.version>", "")))
        self.assertTrue(any("easy4j-prefixed" in error for error in errors))

    def test_hiwepy_conflict_prefix_fails(self):
        errors = self.check(pom(("", "        <hiwepy-hitool.version>1</hiwepy-hitool.version>", "")))
        self.assertTrue(any("conflicting hiwepy" in error for error in errors))

    def test_alignment_prefix_fails(self):
        errors = self.check(pom(("", "        <alignment.hitool.version>1</alignment.hitool.version>", "")))
        self.assertTrue(any("internal alignment" in error for error in errors))

    def test_collision_removes_old_coordinate_and_keeps_easy4j(self):
        source = '''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <properties>
        <component.version>1</component.version>
        <easy4j-component.version>2</easy4j-component.version>
    </properties>
    <dependencyManagement><dependencies>
        <!-- old component -->
        <dependency>
            <groupId>com.github.hiwepy</groupId>
            <artifactId>component</artifactId>
            <version>${component.version}</version>
        </dependency>
        <!-- current component -->
        <dependency>
            <groupId>io.github.easy4j</groupId>
            <artifactId>component</artifactId>
            <version>${easy4j-component.version}</version>
        </dependency>
    </dependencies></dependencyManagement>
</project>'''
        changed, _ = rename_easy4j_properties(source)
        self.assertNotIn("com.github.hiwepy", changed)
        self.assertNotIn("easy4j-component.version", changed)
        self.assertIn("io.github.easy4j", changed)
        self.assertIn("${component.version}", changed)

    def test_existing_hiwepy_property_removes_old_dependency(self):
        source = '''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <properties>
        <component.version>2</component.version>
        <hiwepy-component.version>1</hiwepy-component.version>
    </properties>
    <dependencyManagement><dependencies>
        <dependency>
            <groupId>com.github.hiwepy</groupId>
            <artifactId>component</artifactId>
            <version>${hiwepy-component.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.easy4j</groupId>
            <artifactId>component</artifactId>
            <version>${component.version}</version>
        </dependency>
    </dependencies></dependencyManagement>
</project>'''
        changed, _ = rename_easy4j_properties(source)
        self.assertNotIn("hiwepy-component.version", changed)
        self.assertNotIn("com.github.hiwepy", changed)
        self.assertIn("io.github.easy4j", changed)

    def test_wrong_section_and_order_fail(self):
        errors = self.check(pom((
            "",
            "        <z.version>1</z.version>\n        <a.version>1</a.version>\n"
            "        <maven-clean-plugin.version>1</maven-clean-plugin.version>",
            "",
        )))
        self.assertTrue(any("misplaced" in error for error in errors))
        self.assertTrue(any("not naturally sorted" in error for error in errors))

    def test_natural_order_handles_numbers_and_case(self):
        values = ["item10.version", "item2.version", "Item1.version"]
        self.assertEqual(sorted(values, key=natural_key),
                         ["Item1.version", "item2.version", "item10.version"])

    def test_formatter_keeps_inline_property_comment(self):
        source = '''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <properties>
        <z.version>1</z.version>
        <a.version>1</a.version> <!-- compatibility reason -->
    </properties>
</project>'''
        formatted = format_properties(source)
        self.assertIn("<a.version>1</a.version> <!-- compatibility reason -->", formatted)
        self.assertLess(formatted.index("<a.version>"), formatted.index("<z.version>"))

    def test_formatter_keeps_following_comment_with_described_property(self):
        source = '''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <properties>
        <z.version>1</z.version>
        <artemis.version>2.19</artemis.version>
        <!-- Artemis 2.19 is the Java 8 line. -->
        <akka.version>2.8</akka.version>
    </properties>
</project>'''
        formatted = format_properties(source)
        artemis = formatted.index("<artemis.version>")
        comment = formatted.index("Artemis 2.19 is the Java 8 line")
        akka = formatted.index("<akka.version>")
        self.assertLess(akka, artemis)
        self.assertLess(artemis, comment)

    def test_formatter_is_idempotent_with_multiline_comment(self):
        source = '''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <properties>
        <library.version>1</library.version>
        <!-- Library compatibility:
             Java 8 baseline. -->
    </properties>
</project>'''
        once = format_properties(source)
        self.assertEqual(format_properties(once), once)


if __name__ == "__main__":
    unittest.main()
