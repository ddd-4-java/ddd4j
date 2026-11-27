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
package io.ddd4j.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CacheKit.LocalCacheType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CacheKitLocalCacheTypeTest {

    /**
     * 验证<code>types</code> 仅包含 <code>CacheKit.LocalCacheType.CAFFEINE</code>、<code>CacheKit.LocalCacheType.GUAVA</code>、<code>CacheKit.LocalCacheType.HUTOOL</code>。
     */
    @Test
    void values_shouldContainCaffeineGuavaHutool() {
        CacheKit.LocalCacheType[] types = CacheKit.LocalCacheType.values();

        assertThat(types).containsExactlyInAnyOrder(
                CacheKit.LocalCacheType.CAFFEINE,
                CacheKit.LocalCacheType.GUAVA,
                CacheKit.LocalCacheType.HUTOOL);
    }

    /**
     * 验证<code>CacheKit.LocalCacheType.valueOf("CAFFEINE")</code> 等于 <code>CacheKit.LocalCacheType.CAFFEINE</code>，<code>CacheKit.LocalCacheType.valueOf("GUAVA")</code> 等于 <code>CacheKit.LocalCacheType.GUAVA</code>，<code>CacheKit.LocalCacheType.valueOf("HUTOOL")</code> 等于 <code>CacheKit.LocalCacheType.HUTOOL</code>。
     */
    @Test
    void valueOf_shouldResolveByName() {
        assertThat(CacheKit.LocalCacheType.valueOf("CAFFEINE"))
                .isEqualTo(CacheKit.LocalCacheType.CAFFEINE);
        assertThat(CacheKit.LocalCacheType.valueOf("GUAVA"))
                .isEqualTo(CacheKit.LocalCacheType.GUAVA);
        assertThat(CacheKit.LocalCacheType.valueOf("HUTOOL"))
                .isEqualTo(CacheKit.LocalCacheType.HUTOOL);
    }

    /**
     * 验证<code>CacheKit.getDefaultType()</code> 等于 <code>CacheKit.LocalCacheType.CAFFEINE</code>。
     */
    @Test
    void getDefaultType_shouldBeCaffeineByDefault() {
        CacheKit.setDefaultType(CacheKit.LocalCacheType.CAFFEINE);

        assertThat(CacheKit.getDefaultType()).isEqualTo(CacheKit.LocalCacheType.CAFFEINE);
    }
}
