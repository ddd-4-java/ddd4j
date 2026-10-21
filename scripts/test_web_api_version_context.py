"""API 版本值对象与旧/新请求上下文构造器的兼容行为。"""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ddd4j-web/ddd4j-web-core/src/main/java/io/ddd4j/web/core"
def jackson_annotations_jar():
    pom = ET.parse(ROOT / "ddd4j-dependencies/pom.xml").getroot()
    namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
    properties = pom.find(namespace + "properties")
    version = properties.findtext(namespace + "jackson-annotations.version")
    if not version:
        jackson_version = properties.findtext(namespace + "jackson.version")
        bom = Path.home() / ".m2/repository/com/fasterxml/jackson/jackson-bom" / jackson_version / ("jackson-bom-" + jackson_version + ".pom")
        bom_root = ET.parse(bom).getroot()
        bom_namespace = bom_root.tag.partition("}")[0] + "}" if "}" in bom_root.tag else ""
        version = bom_root.findtext("./" + bom_namespace + "properties/" + bom_namespace + "jackson.version.annotations")
    if not version:
        raise ValueError("Jackson annotations version is missing from the line BOM")
    return Path.home() / ".m2/repository/com/fasterxml/jackson/core/jackson-annotations" / version / ("jackson-annotations-" + version + ".jar")


JACKSON = jackson_annotations_jar()


def current_jdk():
    configured = os.environ.get("JAVA_HOME")
    if configured:
        return Path(configured)
    pom = ET.parse(ROOT / "pom.xml").getroot()
    namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
    version = pom.findtext("./" + namespace + "properties/" + namespace + "java.version")
    return Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip())


class WebApiVersionContextTest(unittest.TestCase):
    def test_parse_and_eight_nine_argument_compatibility(self):
        probe = '''import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.version.ApiVersion;
import java.util.Locale;
public class WebApiVersionProbe {
    public static void main(String[] args) {
        ApiVersion version = ApiVersion.parse("v2.1");
        if (!version.equals(ApiVersion.parse("2.1.0"))) throw new AssertionError("parse");
        WebRequestContext old = new WebRequestContext("r", "t", "tenant", "Bearer token", Locale.CHINA, "127.0.0.1", "get", "/orders");
        WebRequestContext same = new WebRequestContext("r", "t", "tenant", "Bearer token", Locale.CHINA, "127.0.0.1", "get", "/orders", null);
        WebRequestContext current = new WebRequestContext("r", "t", "tenant", "Bearer token", Locale.CHINA, "127.0.0.1", "get", "/orders", version);
        if (old.apiVersion() != null || old.getApiVersion() != null) throw new AssertionError("legacy");
        if (!old.equals(same) || old.hashCode() != same.hashCode()) throw new AssertionError("eight/nine");
        if (old.equals(current) || !version.equals(current.apiVersion())) throw new AssertionError("version equality");
        if (!"GET".equals(current.getMethod()) || !current.toString().contains("apiVersion=2.1.0")) throw new AssertionError("normalization");
        System.out.println("web api version passed");
    }
}'''
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stub = root / "io/ddd4j/kit/lang/StrKit.java"
            stub.parent.mkdir(parents=True)
            stub.write_text('package io.ddd4j.kit.lang; public final class StrKit { public static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); } }')
            main = root / "WebApiVersionProbe.java"
            main.write_text(probe)
            classes = root / "classes"
            classes.mkdir()
            jdk = current_jdk()
            compiled = subprocess.run([str(jdk / "bin/javac"), "-classpath", str(JACKSON), "-d", str(classes),
                                       str(stub), str(SOURCE / "version/ApiVersion.java"),
                                       str(SOURCE / "context/WebRequestContext.java"), str(main)],
                                      capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            execution = subprocess.run([str(jdk / "bin/java"), "-classpath", str(classes), "WebApiVersionProbe"],
                                       capture_output=True, text=True)
            self.assertEqual(0, execution.returncode, execution.stderr)
            self.assertIn("web api version passed", execution.stdout)


if __name__ == "__main__":
    unittest.main()
