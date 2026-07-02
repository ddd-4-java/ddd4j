package io.ddd4j.extension.express.infrastructure.service;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import io.ddd4j.extension.express.application.service.RuleManagementService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态函数加载器
 * 基础设施层：负责从数据库动态加载自定义函数规则并注册到QLExpress
 *
 * <p>支持两种类型的函数规则：
 * <ul>
 *   <li>CLASS类型：通过反射加载类方法</li>
 *   <li>SCRIPT类型：脚本函数（未来扩展）</li>
 * </ul>
 *
 * <p>只加载 ruleType 为 FUNCTION 的规则
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Component
@Slf4j
public class DynamicFunctionLoader {

    private final Express4Runner expressRunner;
    private final RuleManagementService ruleManagementService;

    // 已加载的函数缓存，避免重复加载
    private final Map<String, CustomFunction> loadedFunctions = new ConcurrentHashMap<>();

    public DynamicFunctionLoader(Express4Runner expressRunner,
                                 RuleManagementService ruleManagementService) {
        this.expressRunner = expressRunner;
        this.ruleManagementService = ruleManagementService;
    }

    /**
     * 初始化时加载所有启用的自定义函数
     */
    @PostConstruct
    public void init() {
        try {
            loadAllEnabledFunctions();
            log.info("动态函数加载完成，共加载{}个函数", loadedFunctions.size());
        } catch (Exception e) {
            log.error("动态函数加载失败", e);
        }
    }

    /**
     * 加载所有启用的函数规则
     */
    public void loadAllEnabledFunctions() {
        List<RuleDefinition> functionRules = ruleManagementService.getAllFunctionRules();
        for (RuleDefinition rule : functionRules) {
            // 跳过本地环境函数规则（已经在QLExpressConfig中注册）
            if (Objects.nonNull(rule.getPriority()) && rule.getPriority() >= 1000) {
                continue;
            }
            loadFunction(rule);
        }
    }

    /**
     * 加载单个函数规则
     */
    public void loadFunction(RuleDefinition rule) {
        if (!rule.isAvailable() || !rule.isFunctionRule()) {
            log.warn("规则未启用或不是函数规则，跳过加载: {}", rule.getRuleCode());
            return;
        }

        try {
            if ("CLASS".equals(rule.getFunctionType())) {
                loadClassFunction(rule);
            } else if ("SCRIPT".equals(rule.getFunctionType())) {
                loadScriptFunction(rule);
            } else {
                log.warn("未知的函数类型: {}, 规则编码: {}", rule.getFunctionType(), rule.getRuleCode());
            }
        } catch (Exception e) {
            log.error("加载函数规则失败: {}", rule.getRuleCode(), e);
        }
    }

    /**
     * 加载类方法函数
     */
    private void loadClassFunction(RuleDefinition rule) throws Exception {
        String functionClass = rule.getFunctionClass();
        if (Objects.isNull(functionClass)
                || !org.springframework.util.StringUtils.hasText(functionClass)) {
            log.warn("函数类名为空: {}", rule.getRuleCode());
            return;
        }

        try {
            Class<?> clazz = Class.forName(functionClass);

            // 如果实现了CustomFunction接口，直接使用
            if (CustomFunction.class.isAssignableFrom(clazz)) {
                CustomFunction customFunction = (CustomFunction) clazz.getDeclaredConstructor().newInstance();
                expressRunner.addFunction(rule.getRuleCode(), customFunction);
                loadedFunctions.put(rule.getRuleCode(), customFunction);
                log.info("加载类函数成功: {} -> {}", rule.getRuleCode(), functionClass);
            } else {
                // 如果是静态方法，通过反射调用
                String methodName = rule.getFunctionMethod();
                if (Objects.nonNull(methodName)
                        && org.springframework.util.StringUtils.hasText(methodName)) {
                    Method method = clazz.getMethod(methodName, Object[].class);
                    // 创建包装函数
                    CustomFunction wrapper = createMethodWrapper(clazz, method);
                    expressRunner.addFunction(rule.getRuleCode(), wrapper);
                    loadedFunctions.put(rule.getRuleCode(), wrapper);
                    log.info("加载静态方法函数成功: {} -> {}.{}",
                            rule.getRuleCode(), functionClass, methodName);
                } else {
                    log.warn("函数方法名为空: {}", rule.getRuleCode());
                }
            }
        } catch (ClassNotFoundException e) {
            log.error("函数类不存在: {}", functionClass, e);
        } catch (Exception e) {
            log.error("加载函数类失败: {}", functionClass, e);
        }
    }

    /**
     * 创建方法包装器
     *
     * <p>将静态方法包装为 CustomFunction，用于动态加载函数规则。
     *
     * @param clazz  函数类
     * @param method 静态方法
     * @return 包装后的 CustomFunction
     */
    private CustomFunction createMethodWrapper(Class<?> clazz, Method method) {
        return (qContext, parameters) -> {
            try {
                // QLExpress 4.x 中 Parameters 可能是一个数组或者有 size() 方法
                // 使用反射方式获取参数值，兼容不同的 API 版本
                int paramCount = parameters.size();
                Object[] args = new Object[paramCount];
                for (int i = 0; i < paramCount; i++) {
                    args[i] = getParameterValue(parameters, i, qContext);
                }
                return method.invoke(null, (Object) args);
            } catch (Exception e) {
                throw new RuntimeException("执行函数方法失败", e);
            }
        };
    }

    /**
     * 获取参数值（兼容不同的 QLExpress 版本）
     */
    private Object getParameterValue(Parameters parameters, int index, QContext qContext) throws Throwable {
        try {
            if (Objects.nonNull(parameters.get(index))) {
                Object param = parameters.get(index);
                try {
                    java.lang.reflect.Method getObjectMethod = param.getClass().getMethod("getObject", QContext.class);
                    return getObjectMethod.invoke(param, qContext);
                } catch (NoSuchMethodException e) {
                    return param;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        throw new IllegalArgumentException("无法获取参数值，索引: " + index);
    }

    /**
     * 加载脚本函数（未来扩展）
     */
    private void loadScriptFunction(RuleDefinition rule) {
        // TODO: 实现脚本函数的加载逻辑
        log.warn("脚本函数暂未实现: {}", rule.getRuleCode());
    }

    /**
     * 重新加载函数规则（用于规则更新后）
     */
    public void reloadFunction(String ruleCode) {
        // 先移除旧函数
        loadedFunctions.remove(ruleCode);

        // 重新加载
        ruleManagementService.getRuleByCode(ruleCode).ifPresent(this::loadFunction);
    }

    /**
     * 卸载函数
     */
    public void unloadFunction(String ruleCode) {
        loadedFunctions.remove(ruleCode);
        log.info("卸载函数: {}", ruleCode);
    }
}
