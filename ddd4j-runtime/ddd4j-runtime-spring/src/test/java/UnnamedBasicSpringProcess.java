import io.ddd4j.spring.cqrs.BasicSpringProcess;

/**
 * 从默认包的 main 启动独立 JVM 探针。
 */
public final class UnnamedBasicSpringProcess {
    /**
     * \u8c03\u7528 <code>BasicSpringProcess.verifyStartup()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param args \u4ece\u547d\u4ee4\u884c\u4f20\u5165\u6d4b\u8bd5\u63a2\u9488\u7684\u53c2\u6570
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    public static void main(String[] args) throws Exception {
        BasicSpringProcess.verifyStartup("");
    }
}
