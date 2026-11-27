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

/**
 * Framework-neutral port for resolving logos and frame images.
 */
public interface QrCodeResourceResolver {

    /**
     * \u89e3\u6790\u5916\u6846\u56fe\u50cf\u6216 Logo \u8d44\u6e90\u3002
     * @param location \u8d44\u6e90\u4f4d\u7f6e
     * @return \u5df2\u89e3\u6790\u56fe\u50cf
     * @throws IOException \u8d44\u6e90\u8bfb\u53d6\u5931\u8d25\u65f6
     */
    BufferedImage resolve(String location) throws IOException;
}
