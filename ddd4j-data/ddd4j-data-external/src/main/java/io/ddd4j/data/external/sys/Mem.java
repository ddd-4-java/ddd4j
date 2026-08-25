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
package io.ddd4j.data.external.sys;

import io.ddd4j.kit.lang.ArithKit;
import lombok.Data;

/**
 * 內存相关信息
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Mem {
    /**
     * 内存总量
     */
    private double total;

    /**
     * 已用内存
     */
    private double used;

    /**
     * 剩余内存
     */
    private double free;

    public double getTotal() {
        return ArithKit.div(total, (1024 * 1024 * 1024), 2);
    }

    public double getUsed() {
        return ArithKit.div(used, (1024 * 1024 * 1024), 2);
    }

    public double getFree() {
        return ArithKit.div(free, (1024 * 1024 * 1024), 2);
    }

    public double getUsage() {
        return ArithKit.mul(ArithKit.div(used, total, 4), 100);
    }
}
