/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证三线默认国际化提供者的资源与纯文本格式化契约。 */
class I18nProviderDefaultContractTest {

    @Test
    void blankKeyShouldReturnNull() {
        assertNull(I18nProvider.DEFAULT.getMessage("  "));
    }

    @Test
    void resourcePatternShouldUseMessageFormatArguments() {
        assertEquals("Hello, Alice!", I18nProvider.DEFAULT.getMessage("ddd4j.parity.greeting", "Alice"));
    }

    @Test
    void unknownKeyShouldUseSlfStyleArgumentsInOrder() {
        assertEquals("Order 42 belongs to tenant-a",
                I18nProvider.DEFAULT.getMessage("Order {} belongs to {}", 42, "tenant-a"));
    }

    @Test
    void unknownKeyShouldRenderNullAndLeaveUnusedPlaceholder() {
        assertEquals("Value null / {}", I18nProvider.DEFAULT.getMessage("Value {} / {}", (Object) null));
    }

    @Test
    void unknownKeyWithoutArgumentsShouldRemainUnchanged() {
        assertEquals("Missing {}", I18nProvider.DEFAULT.getMessage("Missing {}"));
    }
}
