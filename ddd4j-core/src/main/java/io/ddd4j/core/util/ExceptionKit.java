package io.ddd4j.core.util;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.constant.ContextConstants;
import lombok.experimental.UtilityClass;

import java.util.*;

/**
 * 异常处理工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class ExceptionKit {

    public String getProjectStackTraces(Throwable e) {
        List<StackTraceElement> stackTraceElements = new ArrayList<>(e.getStackTrace().length);
        Collections.addAll(stackTraceElements, e.getStackTrace());
        String projectStackTraces = getProjectStackTraces(stackTraceElements);
        if (Objects.nonNull(projectStackTraces)) {
            return projectStackTraces;
        }
        return e.getLocalizedMessage();
    }

    public String getProjectStackTraces(List<StackTraceElement> stackTraceElements) {
        if (Objects.isNull(stackTraceElements)) {
            return null;
        }
        String projectPackage = BaseContext.get(ContextConstants.PROJECT_PACKAGE);
        if (Objects.nonNull(projectPackage)) {
            StringJoiner stringJoiner = new StringJoiner("; ", "", "");
            for (int i = 0; i < stackTraceElements.size(); i++) {
                StackTraceElement s = stackTraceElements.get(i);
                String fileName = s.getFileName();
                int lineNumber = s.getLineNumber();
                // 忽略条件
                if (Objects.isNull(fileName) || lineNumber == -1) {
                    continue;
                }
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