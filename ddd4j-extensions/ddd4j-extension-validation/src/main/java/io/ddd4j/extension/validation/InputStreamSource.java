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
package io.ddd4j.extension.validation;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可重复打开文件内容流的来源。
 */
@FunctionalInterface
public interface InputStreamSource {

    /**
     * 打开一个新的输入流，调用方负责关闭。
     *
     * @return 新输入流
     * @throws IOException 文件无法读取时抛出
     */
    InputStream openStream() throws IOException;
}
