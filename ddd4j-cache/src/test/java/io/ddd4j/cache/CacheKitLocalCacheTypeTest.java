package io.ddd4j.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CacheKit.LocalCacheType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CacheKitLocalCacheTypeTest {

    @Test
    void values_shouldContainCaffeineGuavaHutool() {
        CacheKit.LocalCacheType[] types = CacheKit.LocalCacheType.values();

        assertThat(types).containsExactlyInAnyOrder(
                CacheKit.LocalCacheType.CAFFEINE,
                CacheKit.LocalCacheType.GUAVA,
                CacheKit.LocalCacheType.HUTOOL);
    }

    @Test
    void valueOf_shouldResolveByName() {
        assertThat(CacheKit.LocalCacheType.valueOf("CAFFEINE"))
                .isEqualTo(CacheKit.LocalCacheType.CAFFEINE);
        assertThat(CacheKit.LocalCacheType.valueOf("GUAVA"))
                .isEqualTo(CacheKit.LocalCacheType.GUAVA);
        assertThat(CacheKit.LocalCacheType.valueOf("HUTOOL"))
                .isEqualTo(CacheKit.LocalCacheType.HUTOOL);
    }

    @Test
    void getDefaultType_shouldBeCaffeineByDefault() {
        CacheKit.setDefaultType(CacheKit.LocalCacheType.CAFFEINE);

        assertThat(CacheKit.getDefaultType()).isEqualTo(CacheKit.LocalCacheType.CAFFEINE);
    }
}
