import io.ddd4j.spring.cqrs.BasicSpringProcess;

/**
 * 从默认包的 main 启动独立 JVM 探针。
 */
public final class UnnamedBasicSpringProcess {
    /**
     * 调用 <code>BasicSpringProcess.verifyStartup()</code> 执行当前夹具操作。
     *
     * @param args 从命令行传入测试探针的参数
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    public static void main(String[] args) throws Exception {
        BasicSpringProcess.verifyStartup("");
    }
}
