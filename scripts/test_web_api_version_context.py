"""API 版本值对象与旧/新请求上下文构造器的兼容行为。"""

from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ddd4j-web/ddd4j-web-core/src/main/java/io/ddd4j/web/core"
JACKSON = Path.home() / ".m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar"


def current_jdk():
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
