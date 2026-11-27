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

/** 租户资源分页转换的三线行为契约。 */
class TenantAwareResourceTransferContractTest {

    /** \u9a8c\u8bc1<code>result.getTotal()</code> \u7b49\u4e8e <code>3L</code>\uff0c<code>result.getCurrent()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>result.getRecords()</code> \u7b49\u4e8e <code>Arrays.asList("1", "2")</code>\u3002 */
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

    /** \u5b9e\u73b0 <code>TenantAwareResource</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>convert()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class ExposedResource extends TenantAwareResource {
        /**
         * \u901a\u8fc7 <code>transfer()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param source \u6d4b\u8bd5\u4e2d\u5f85\u8f6c\u6362\u3001\u8bfb\u53d6\u6216\u4f20\u64ad\u7684\u6570\u636e\u6765\u6e90
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>transfer(source, String::valueOf)</code>
         */
        Page<String> convert(Page<Integer> source) {
            return transfer(source, String::valueOf);
        }
    }
}
