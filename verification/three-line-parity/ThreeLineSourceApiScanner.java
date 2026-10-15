import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 从当前 Java 源码的语法树提取声明，供三线差异清单使用。 */
public final class ThreeLineSourceApiScanner {

    private ThreeLineSourceApiScanner() {
    }

    /**
     * 解析标准输入中的相对源码路径并输出公开及受保护方法声明。
     *
     * @param args 唯一参数是源码根目录
     * @throws Exception 文件读取或语法分析失败时抛出
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected exactly one source root");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (Objects.nonNull(line = input.readLine())) {
                if (!line.isEmpty()) {
                    files.add(root.resolve(line).normalize());
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
            for (CompilationUnitTree unit : task.parse()) {
                Path file = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                String relative = root.relativize(file).toString().replace('\\', '/');
                String packageName = Objects.isNull(unit.getPackageName()) ? "" : unit.getPackageName().toString();
                new ApiVisitor(relative, packageName).scan(unit, null);
            }
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalArgumentException("source parse failed: " + diagnostic);
            }
        }
    }

    private static final class ApiVisitor extends TreeScanner<Void, Void> {
        private final String path;
        private final String packageName;
        private final Deque<String> types = new ArrayDeque<>();
        private final Deque<Boolean> interfaces = new ArrayDeque<>();

        private ApiVisitor(String path, String packageName) {
            this.path = path;
            this.packageName = packageName;
        }

        @Override
        public Void visitClass(ClassTree type, Void ignored) {
            String name = type.getSimpleName().toString();
            if (name.isEmpty()) {
                return null;
            }
            Set<Modifier> flags = type.getModifiers().getFlags();
            boolean visible = flags.contains(Modifier.PUBLIC)
                    || (!types.isEmpty() && flags.contains(Modifier.PROTECTED))
                    || (!types.isEmpty() && Boolean.TRUE.equals(interfaces.peekLast())
                    && !flags.contains(Modifier.PRIVATE));
            if (!visible) {
                return null;
            }
            types.addLast(name);
            interfaces.addLast(type.getKind() == Tree.Kind.INTERFACE
                    || type.getKind() == Tree.Kind.ANNOTATION_TYPE);
            boolean hasConstructor = type.getMembers().stream()
                    .anyMatch(member -> member instanceof MethodTree
                            && Objects.isNull(((MethodTree) member).getReturnType()));
            boolean lombokNoArgs = hasAnnotation(type, "NoArgsConstructor");
            boolean lombokOtherConstructor = hasAnnotation(type, "Value")
                    || hasAnnotation(type, "Data")
                    || hasAnnotation(type, "AllArgsConstructor")
                    || hasAnnotation(type, "RequiredArgsConstructor")
                    || hasAnnotation(type, "Builder")
                    || hasAnnotation(type, "SuperBuilder");
            if (type.getKind() == Tree.Kind.CLASS
                    && (lombokNoArgs || (!hasConstructor && !lombokOtherConstructor))
                    && !hasExplicitNoArgs(type)) {
                StringBuilder owner = new StringBuilder(packageName);
                for (String part : types) {
                    if (owner.length() > 0) {
                        owner.append('.');
                    }
                    owner.append(part);
                }
                String visibility = flags.contains(Modifier.PROTECTED) ? "protected" : "public";
                System.out.println(path + "\t" + owner + "#<init>()\t<init>|" + visibility + "|");
            }
            super.visitClass(type, ignored);
            interfaces.removeLast();
            types.removeLast();
            return null;
        }

        private static boolean hasAnnotation(ClassTree type, String simpleName) {
            for (AnnotationTree annotation : type.getModifiers().getAnnotations()) {
                String name = annotation.getAnnotationType().toString();
                if (name.equals(simpleName) || name.endsWith("." + simpleName)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasExplicitNoArgs(ClassTree type) {
            return type.getMembers().stream()
                    .anyMatch(member -> member instanceof MethodTree
                            && Objects.isNull(((MethodTree) member).getReturnType())
                            && ((MethodTree) member).getParameters().isEmpty());
        }

        @Override
        public Void visitMethod(MethodTree method, Void ignored) {
            Set<Modifier> flags = method.getModifiers().getFlags();
            boolean visible = flags.contains(Modifier.PUBLIC) || flags.contains(Modifier.PROTECTED)
                    || (Boolean.TRUE.equals(interfaces.peekLast()) && !flags.contains(Modifier.PRIVATE));
            if (!visible || types.isEmpty()) {
                return null;
            }
            StringBuilder owner = new StringBuilder(packageName);
            Iterator<String> names = types.iterator();
            while (names.hasNext()) {
                if (owner.length() > 0) {
                    owner.append('.');
                }
                owner.append(names.next());
            }
            List<String> parameterTypes = new ArrayList<>();
            List<String> parameterNames = new ArrayList<>();
            method.getParameters().forEach(parameter -> {
                parameterTypes.add(compact(parameter.getType().toString()));
                parameterNames.add(parameter.getName().toString());
            });
            String methodName = Objects.isNull(method.getReturnType()) ? "<init>" : method.getName().toString();
            String returnType = Objects.isNull(method.getReturnType()) ? "<init>"
                    : compact(method.getReturnType().toString());
            String visibility = flags.contains(Modifier.PROTECTED) ? "protected" : "public";
            String key = owner + "#" + methodName + "(" + String.join(",", parameterTypes) + ")";
            String value = returnType + "|" + visibility + "|" + String.join(",", parameterNames);
            System.out.println(path + "\t" + key + "\t" + value);
            return null;
        }

        private static String compact(String value) {
            return value.replaceAll("\\s+", " ").trim();
        }
    }
}
