import io.ddd4j.spring.cqrs.BasicSpringProcess;

/**
 * 从默认包的 main 启动独立 JVM 探针。
 */
public final class UnnamedBasicSpringProcess {
    public static void main(String[] args) throws Exception {
        BasicSpringProcess.verifyStartup("");
    }
}
