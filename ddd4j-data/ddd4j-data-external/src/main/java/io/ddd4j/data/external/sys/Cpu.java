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
 * CPU相关信息
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Cpu {
    /**
     * 核心数
     */
    private int cpuNum;

    /**
     * CPU总的使用率
     */
    private double total;

    /**
     * CPU系统使用率
     */
    private double sys;

    /**
     * CPU用户使用率
     */
    private double used;

    /**
     * CPU当前等待率
     */
    private double wait;

    /**
     * CPU当前空闲率
     */
    private double free;

    public double getTotal() {
        return ArithKit.round(ArithKit.mul(total, 100), 2);
    }

    public double getSys() {
        return ArithKit.round(ArithKit.mul(sys / total, 100), 2);
    }

    public double getUsed() {
        return ArithKit.round(ArithKit.mul(used / total, 100), 2);
    }

    public double getWait() {
        return ArithKit.round(ArithKit.mul(wait / total, 100), 2);
    }

    public double getFree() {
        return ArithKit.round(ArithKit.mul(free / total, 100), 2);
    }

}
