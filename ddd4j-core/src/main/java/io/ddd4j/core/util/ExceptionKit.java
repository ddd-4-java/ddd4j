/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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