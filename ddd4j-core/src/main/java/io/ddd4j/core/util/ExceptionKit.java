package io.ddd4j.core.util;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * 异常处理工具类
 *
 * @author Loong Wan
 * @since 2.0.x
 */
@UtilityClass
public class ExceptionKit {

    public String getProjectStackTraces(Throwable e) {
        List<StackTraceElement> stackTraceElements = new ArrayList<>(e.getStackTrace().length);
        Collections.addAll(stackTraceElements, e.getStackTrace());
        String projectStackTraces = getProjectStackTraces(stackTraceElements);
        if (projectStackTraces != null) {
            return projectStackTraces;
        }
        return e.getLocalizedMessage();
    }

    public String getProjectStackTraces(List<StackTraceElement> stackTraceElements) {
        if (stackTraceElements == null) return null;
        String projectPackage = BaseContext.get(ContextConstants.PROJECT_PACKAGE);
        if (projectPackage != null) {
            StringJoiner stringJoiner = new StringJoiner("; ", "", "");
            for (int i = 0; i < stackTraceElements.size(); i++) {
                StackTraceElement s = stackTraceElements.get(i);
                String fileName = s.getFileName();
                int lineNumber = s.getLineNumber();
                // 忽略条件
                if (fileName == null || lineNumber == -1) continue;
                if (i == 0) {
                    if (s.getClassName().startsWith(projectPackage)) {
                        stringJoiner.add(fileName + "(" + lineNumber + ")");
                    } else {
                        stringJoiner.add(fileName + "(" + lineNumber + ") ...");
                    }
                } else if (s.getClassName().startsWith(projectPackage)) {
                    stringJoiner.add(fileName + "(" + lineNumber + ")");
                }
            }
            return stringJoiner.toString().replace("...;", "...");
        }
        return null;
    }
}