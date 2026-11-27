package io.ddd4j.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CacheKit.LocalCacheType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CacheKitLocalCacheTypeTest {

    /** \u9a8c\u8bc1<code>types</code> \u4ec5\u5305\u542b <code>CacheKit.LocalCacheType.CAFFEINE</code>\u3001<code>CacheKit.LocalCacheType.GUAVA</code>\u3001<code>CacheKit.LocalCacheType.HUTOOL</code>\u3002 */
    @Test
    void values_shouldContainCaffeineGuavaHutool() {
        CacheKit.LocalCacheType[] types = CacheKit.LocalCacheType.values();

        assertThat(types).containsExactlyInAnyOrder(
                CacheKit.LocalCacheType.CAFFEINE,
                CacheKit.LocalCacheType.GUAVA,
                CacheKit.LocalCacheType.HUTOOL);
    }

    /** \u9a8c\u8bc1<code>CacheKit.LocalCacheType.valueOf("CAFFEINE")</code> \u7b49\u4e8e <code>CacheKit.LocalCacheType.CAFFEINE</code>\uff0c<code>CacheKit.LocalCacheType.valueOf("GUAVA")</code> \u7b49\u4e8e <code>CacheKit.LocalCacheType.GUAVA</code>\uff0c<code>CacheKit.LocalCacheType.valueOf("HUTOOL")</code> \u7b49\u4e8e <code>CacheKit.LocalCacheType.HUTOOL</code>\u3002 */
    @Test
    void valueOf_shouldResolveByName() {
        assertThat(CacheKit.LocalCacheType.valueOf("CAFFEINE"))
                .isEqualTo(CacheKit.LocalCacheType.CAFFEINE);
        assertThat(CacheKit.LocalCacheType.valueOf("GUAVA"))
                .isEqualTo(CacheKit.LocalCacheType.GUAVA);
        assertThat(CacheKit.LocalCacheType.valueOf("HUTOOL"))
                .isEqualTo(CacheKit.LocalCacheType.HUTOOL);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getDefaultType()</code> \u7b49\u4e8e <code>CacheKit.LocalCacheType.CAFFEINE</code>\u3002 */
    @Test
    void getDefaultType_shouldBeCaffeineByDefault() {
        CacheKit.setDefaultType(CacheKit.LocalCacheType.CAFFEINE);

        assertThat(CacheKit.getDefaultType()).isEqualTo(CacheKit.LocalCacheType.CAFFEINE);
    }
}
