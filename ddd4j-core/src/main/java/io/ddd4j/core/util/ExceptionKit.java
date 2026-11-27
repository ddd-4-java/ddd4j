package io.ddd4j.core.util;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
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

    /**
     * \u63d0\u53d6\u5f02\u5e38\u4e2d\u5c5e\u4e8e\u5f53\u524d\u9879\u76ee\u7684\u5806\u6808\uff1b\u672a\u5339\u914d\u65f6\u8fd4\u56de\u5f02\u5e38\u6d88\u606f\u3002
     * @param e \u5f85\u5206\u6790\u5f02\u5e38
     * @return \u9879\u76ee\u5806\u6808\u6216\u5f02\u5e38\u6d88\u606f
     */
    public String getProjectStackTraces(Throwable e) {
        List<StackTraceElement> stackTraceElements = new ArrayList<>(e.getStackTrace().length);
        Collections.addAll(stackTraceElements, e.getStackTrace());
        String projectStackTraces = getProjectStackTraces(stackTraceElements);
        if (Objects.nonNull(projectStackTraces)) {
            return projectStackTraces;
        }
        return e.getLocalizedMessage();
    }

    /**
     * \u63d0\u53d6\u5806\u6808\u5217\u8868\u4e2d\u5c5e\u4e8e\u5f53\u524d\u9879\u76ee\u7684\u8c03\u7528\u5e27\u3002
     * @param stackTraceElements \u5f85\u7b5b\u9009\u8c03\u7528\u5e27
     * @return \u9879\u76ee\u8c03\u7528\u5e27\u63cf\u8ff0\uff0c\u672a\u5339\u914d\u65f6\u4e3a null
     */
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