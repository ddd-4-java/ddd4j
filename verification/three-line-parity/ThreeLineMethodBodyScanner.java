import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** 从当前 Java 源码语法树定位方法体，不读取 CodeGraph 索引。 */
public final class ThreeLineMethodBodyScanner {

    private ThreeLineMethodBodyScanner() {
    }

    /** 标准输入为相对源码路径，输出 path/key/start/end/base64-body 的 TSV。 */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected exactly one source root");
        }
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (Objects.nonNull(line = input.readLine())) {
                if (!line.isEmpty()) {
                    Path file = root.resolve(line).normalize();
                    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                        throw new IllegalArgumentException("invalid source path: " + line);
                    }
                    files.add(file);
                }
            }
        }
        if (files.isEmpty()) {
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (Objects.isNull(compiler)) {
            throw new IllegalStateException("JDK compiler is unavailable");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null,
                StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = manager.getJavaFileObjectsFromPaths(files);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("-proc:none", "-encoding", "UTF-8"), null, sources);
            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                Path file = Paths.get(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                String relative = root.relativize(file).toString().replace('\\', '/');
                String packageName = Objects.isNull(unit.getPackageName()) ? "" : unit.getPackageName().toString();
                String source = Files.readString(file, StandardCharsets.UTF_8);
                new BodyVisitor(unit, positions, relative, packageName, source).scan(unit, null);
            }
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalArgumentException("source parse failed: " + diagnostic);
            }
        }
    }

    private static final class BodyVisitor extends TreeScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String path;
        private final String packageName;
        private final String source;
        private final Deque<String> types = new ArrayDeque<>();

        private BodyVisitor(CompilationUnitTree unit, SourcePositions positions, String path,
                            String packageName, String source) {
            this.unit = unit;
            this.positions = positions;
            this.path = path;
            this.packageName = packageName;
            this.source = source;
        }

        @Override
        public Void visitClass(ClassTree type, Void ignored) {
            String name = type.getSimpleName().toString();
            if (name.isEmpty()) {
                return null;
            }
            types.addLast(name);
            super.visitClass(type, ignored);
            types.removeLast();
            return null;
        }

        @Override
        public Void visitMethod(MethodTree method, Void ignored) {
            if (!types.isEmpty() && Objects.nonNull(method.getBody())) {
                long start = positions.getStartPosition(unit, method.getBody());
                long end = positions.getEndPosition(unit, method.getBody());
                if (start < 0 || end < start || end > source.length()) {
                    throw new IllegalArgumentException("invalid method body position: " + path);
                }
                StringBuilder owner = new StringBuilder(packageName);
                for (String type : types) {
                    if (owner.length() > 0) {
                        owner.append('.');
                    }
                    owner.append(type);
                }
                List<String> parameters = new ArrayList<>();
                method.getParameters().forEach(parameter ->
                        parameters.add(parameter.getType().toString().replaceAll("\\s+", " ").trim()));
                String key = owner + "#" + method.getName() + "(" + String.join(",", parameters) + ")";
                long startLine = unit.getLineMap().getLineNumber(start);
                long endLine = unit.getLineMap().getLineNumber(end - 1);
                String body = source.substring((int) start, (int) end);
                String encoded = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
                System.out.println(path + "\t" + key + "\t" + startLine + "\t" + endLine + "\t" + encoded);
            }
            return super.visitMethod(method, ignored);
        }
    }
}
