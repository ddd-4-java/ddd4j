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
import java.util.Optional;

/**
 * 文件内容类型检测策略。
 */
@FunctionalInterface
public interface FileTypeDetector {

    /**
     * 根据内容识别文件类型。
     *
     * @param file 文件
     * @return 检测结果；无法识别时为空
     * @throws IOException 文件无法读取时抛出
     */
    Optional<DetectedFileType> detect(ValidatableFile file) throws IOException;
}
