package io.ddd4j.extension.qlexpress;

import com.alibaba.qlexpress4.Express4Runner;
import io.ddd4j.extension.qlexpress.function.NamedQLFunction;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionOptions;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionResult;
import io.ddd4j.extension.qlexpress.model.QLExpressValidationResult;

import java.util.Map;
import java.util.Set;

/**
 * QLExpress 执行、校验和函数管理的公共抽象。
 */
public interface QLExpressEngine {

    /**
     * \u6267\u884c\u8868\u8fbe\u5f0f\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @param context \u8f93\u5165\u4e0a\u4e0b\u6587
     * @return \u8868\u8fbe\u5f0f\u7ed3\u679c
     */
    Object execute(String expression, Map<String, Object> context);

    /**
     * \u4f7f\u7528\u6307\u5b9a\u9009\u9879\u6267\u884c\u8868\u8fbe\u5f0f\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @param context \u8f93\u5165\u4e0a\u4e0b\u6587
     * @param options \u6267\u884c\u9009\u9879
     * @return \u8868\u8fbe\u5f0f\u7ed3\u679c
     */
    Object execute(String expression, Map<String, Object> context, QLExpressExecutionOptions options);

    /**
     * \u6267\u884c\u8868\u8fbe\u5f0f\u5e76\u8f6c\u6362\u7ed3\u679c\u7c7b\u578b\u3002
     * @param <T> \u7ed3\u679c\u7c7b\u578b
     * @param expression \u8868\u8fbe\u5f0f
     * @param context \u8f93\u5165\u4e0a\u4e0b\u6587
     * @param resultType \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u7ed3\u679c
     */
    <T> T execute(String expression, Map<String, Object> context, Class<T> resultType);

    /**
     * \u4f7f\u7528\u6307\u5b9a\u9009\u9879\u6267\u884c\u8868\u8fbe\u5f0f\u5e76\u8f6c\u6362\u7ed3\u679c\u7c7b\u578b\u3002
     * @param <T> \u7ed3\u679c\u7c7b\u578b
     * @param expression \u8868\u8fbe\u5f0f
     * @param context \u8f93\u5165\u4e0a\u4e0b\u6587
     * @param options \u6267\u884c\u9009\u9879
     * @param resultType \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u7ed3\u679c
     */
    <T> T execute(String expression, Map<String, Object> context,
                  QLExpressExecutionOptions options, Class<T> resultType);

    /**
     * \u6267\u884c\u8868\u8fbe\u5f0f\u5e76\u5c06\u5f02\u5e38\u5c01\u88c5\u4e3a\u7ed3\u679c\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @param context \u8f93\u5165\u4e0a\u4e0b\u6587
     * @return \u5305\u542b\u6210\u529f\u6216\u5931\u8d25\u4fe1\u606f\u7684\u6267\u884c\u7ed3\u679c
     */
    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context);

    /**
     * \u4f7f\u7528\u6307\u5b9a\u9009\u9879\u6267\u884c\u8868\u8fbe\u5f0f\u5e76\u5c01\u88c5\u5f02\u5e38\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @param context \u8f93\u5165\u4e0a\u4e0b\u6587
     * @param options \u6267\u884c\u9009\u9879
     * @return \u5305\u542b\u6210\u529f\u6216\u5931\u8d25\u4fe1\u606f\u7684\u6267\u884c\u7ed3\u679c
     */
    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context,
                                                   QLExpressExecutionOptions options);

    /**
     * \u6821\u9a8c\u8868\u8fbe\u5f0f\u8bed\u6cd5\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @return \u8bed\u6cd5\u6821\u9a8c\u7ed3\u679c
     */
    QLExpressValidationResult validate(String expression);

    /**
     * \u83b7\u53d6\u8868\u8fbe\u5f0f\u5f15\u7528\u7684\u5916\u90e8\u53d8\u91cf\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @return \u5916\u90e8\u53d8\u91cf\u540d\u79f0
     */
    Set<String> getExternalVariables(String expression);

    /**
     * \u83b7\u53d6\u8868\u8fbe\u5f0f\u5f15\u7528\u7684\u5916\u90e8\u51fd\u6570\u3002
     * @param expression \u8868\u8fbe\u5f0f
     * @return \u5916\u90e8\u51fd\u6570\u540d\u79f0
     */
    Set<String> getExternalFunctions(String expression);

    /**
     * \u6ce8\u518c\u81ea\u5b9a\u4e49\u51fd\u6570\u3002
     * @param function \u81ea\u5b9a\u4e49\u51fd\u6570
     * @return \u6ce8\u518c\u6210\u529f\u65f6\u4e3a true
     */
    boolean registerFunction(NamedQLFunction function);

    /**
     * \u6ce8\u518c\u6216\u66ff\u6362\u81ea\u5b9a\u4e49\u51fd\u6570\u3002
     * @param function \u81ea\u5b9a\u4e49\u51fd\u6570
     */
    void registerOrReplaceFunction(NamedQLFunction function);

    /**
     * \u79fb\u9664\u81ea\u5b9a\u4e49\u51fd\u6570\u3002
     * @param functionName \u51fd\u6570\u540d\u79f0
     * @return \u79fb\u9664\u6210\u529f\u65f6\u4e3a true
     */
    boolean removeFunction(String functionName);

    /**
     * \u83b7\u53d6\u5e95\u5c42 QLExpress Runner\u3002
     * @return \u5e95\u5c42 Runner
     */
    Express4Runner unwrap();
}
