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
package io.ddd4j.web.quarkus;

import io.ddd4j.core.api.Page;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 租户资源分页转换的三线行为契约。
 */
class TenantAwareResourceTransferContractTest {

    /**
     * 验证<code>result.getTotal()</code> 等于 <code>3L</code>，<code>result.getCurrent()</code> 等于 <code>2L</code>，<code>result.getRecords()</code> 等于 <code>Arrays.asList("1", "2")</code>。
     */
    @Test
    void transferPreservesMetadataAndMutableRecords() {
        Page<Integer> source = new Page<>();
        source.setRecords(Arrays.asList(1));
        source.setTotal(3L);
        source.setCurrent(2L);
        source.setSize(1L);

        Page<String> result = new ExposedResource().convert(source);
        assertEquals(3L, result.getTotal());
        assertEquals(2L, result.getCurrent());
        assertEquals(1L, result.getSize());
        assertEquals(Arrays.asList("1"), result.getRecords());
        result.getRecords().add("2");
        assertEquals(Arrays.asList("1", "2"), result.getRecords());
    }

    /**
     * 实现 <code>TenantAwareResource</code>，供测试调用 <code>convert()</code> 并观察结果。
     */
    private static final class ExposedResource extends TenantAwareResource {
        /**
         * 通过 <code>transfer()</code> 取得当前测试需要的结果。
         *
         * @param source 测试中待转换、读取或传播的数据来源
         * @return 当前测试使用的结果，取自 <code>transfer(source, String::valueOf)</code>
         */
        Page<String> convert(Page<Integer> source) {
            return transfer(source, String::valueOf);
        }
    }
}
