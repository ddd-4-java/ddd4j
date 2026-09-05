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
 * 框架无关的上传文件抽象，由各 Web 框架适配自身上传类型。
 */
public interface ValidatableFile {

    /**
     * 获取客户端文件名。
     *
     * @return 文件名
     */
    String fileName();

    /**
     * 获取客户端声明的 Content-Type。
     *
     * @return Content-Type，可以为空
     */
    String contentType();

    /**
     * 获取文件字节数。
     *
     * @return 文件大小
     */
    long size();

    /**
     * 打开一个新的内容流。
     *
     * @return 新输入流
     * @throws IOException 文件无法读取时抛出
     */
    InputStream openStream() throws IOException;
}
