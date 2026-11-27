/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证三线默认国际化提供者的资源与纯文本格式化契约。
 */
class I18nProviderDefaultContractTest {

    /**
     * 验证<code>I18nProvider.DEFAULT.getMessage(" ")</code>为空。
     */
    @Test
    void blankKeyShouldReturnNull() {
        assertNull(I18nProvider.DEFAULT.getMessage("  "));
    }

    /**
     * 验证<code>I18nProvider.DEFAULT.getMessage("ddd4j.parity.greeting", "Alice")</code> 等于 <code>"Hello, Alice!"</code>。
     */
    @Test
    void resourcePatternShouldUseMessageFormatArguments() {
        assertEquals("Hello, Alice!", I18nProvider.DEFAULT.getMessage("ddd4j.parity.greeting", "Alice"));
    }

    /**
     * 验证<code>I18nProvider.DEFAULT.getMessage("Order {} belongs to {}", 42, "tenant-a")</code> 等于 <code>"Order 42 belongs to tenant-a"</code>。
     */
    @Test
    void unknownKeyShouldUseSlfStyleArgumentsInOrder() {
        assertEquals("Order 42 belongs to tenant-a",
                I18nProvider.DEFAULT.getMessage("Order {} belongs to {}", 42, "tenant-a"));
    }


    /**
     * 在测试框架中执行 <code>I18nProvider.DEFAULT.getMessage()</code>，验证该场景。
     */
    @Test
    void unknownKeyShouldNotTreatBracesInArgumentsAsPlaceholders() {
        assertEquals("Repository {} belongs to inventory",
                I18nProvider.DEFAULT.getMessage("Repository {} belongs to {}", "{}", "inventory"));
        assertEquals("{} plus {} and {}",
                I18nProvider.DEFAULT.getMessage("{} plus {} and {}", "{}", "{}"));
    }

    /**
     * 验证<code>I18nProvider.DEFAULT.getMessage("Value {} / {}", (Object) null)</code> 等于 <code>"Value null / {}"</code>。
     */
    @Test
    void unknownKeyShouldRenderNullAndLeaveUnusedPlaceholder() {
        assertEquals("Value null / {}", I18nProvider.DEFAULT.getMessage("Value {} / {}", (Object) null));
    }

    /**
     * 验证<code>I18nProvider.DEFAULT.getMessage("Missing {}")</code> 等于 <code>"Missing {}"</code>。
     */
    @Test
    void unknownKeyWithoutArgumentsShouldRemainUnchanged() {
        assertEquals("Missing {}", I18nProvider.DEFAULT.getMessage("Missing {}"));
    }
}
