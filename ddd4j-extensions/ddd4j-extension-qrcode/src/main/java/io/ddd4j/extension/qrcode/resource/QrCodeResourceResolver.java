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
package io.ddd4j.extension.qrcode.resource;

import java.awt.image.BufferedImage;
import java.io.IOException;

/** Framework-neutral port for resolving logos and frame images. */
public interface QrCodeResourceResolver {

    /**
     * 按资源定位符解析出图片。
     *
     * @param location 资源定位符（路径、URL 或 classpath 位置）
     * @return 解析得到的图片
     * @throws IOException 资源不存在或读取失败时抛出
     */
    BufferedImage resolve(String location) throws IOException;
}
