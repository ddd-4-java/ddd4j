package io.ddd4j.boot.mq.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * MQ过滤器
 */
public class MQFilter {
    /**
     * 匹配表达式
     * 支持的表达式: * || -，如：A || B -D
     */
    public static boolean matchExps(String input, String exps) {
        //处理通配符*
        if (exps == null || Objects.equals(exps, "*")) {
            return true;
        }
        // 验证输入
        if (input == null) {
            return false;
        }
        Set<String> includes = new HashSet<>();
        Set<String> excludes = new HashSet<>();
        String[] expArr = exps.split(" ");
        // A || B -D
        for (String exp : expArr) {
            if (exp.startsWith("-")) {
                excludes.add(exp.substring(1));
            } else if (!exp.equals("||")) {
                // 包含特定的输入
                includes.add(exp);
            }
        }
        // 如果存在排除列表，则检查是否排除此输入
        if (!excludes.isEmpty() && excludes.contains(input)) {
            return false;
        }
        // 如果存在包含列表，则检查是否包含此输入
        return includes.isEmpty() || includes.contains(input);
    }

    public static Set<String> findIncludes(String exps) {
        Set<String> includes = new HashSet<>();
        //处理通配符*
        if (exps == null || Objects.equals(exps, "*")) {
            return includes;
        }
        String[] expArr = exps.split(" ");
        // A || B -D
        for (String exp : expArr) {
            if (exp.startsWith("-")) {
            } else if (!exp.equals("||")) {
                // 包含特定的输入
                includes.add(exp);
            }
        }
        return includes;
    }

}