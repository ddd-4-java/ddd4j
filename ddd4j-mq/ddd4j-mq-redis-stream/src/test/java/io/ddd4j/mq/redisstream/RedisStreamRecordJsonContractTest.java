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
package io.ddd4j.mq.redisstream;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Redis Stream 记录在各线的 JSON 往返契约。 */
class RedisStreamRecordJsonContractTest {

    @Test
    void jsonRoundTripPreservesPublicFields() throws Exception {
        RedisStreamRecord original = new RedisStreamRecord("orders", "1-0",
                Collections.singletonMap("event", "created"), "native");
        String json = JsonKit.toJson(original);
        RedisStreamRecord restored = JsonKit.toObject(json, RedisStreamRecord.class);
        assertNotNull(restored);
        assertEquals(original.getStream(), restored.getStream());
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getFields(), restored.getFields());
        assertEquals(original.getNativeMessage(), restored.getNativeMessage());
    }
}
