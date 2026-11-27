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
package io.ddd4j.guice.annotation.ddd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>AnnotationFusionVerifier.main()</code>、<code>service.hello()</code>、<code>service.getClass()</code> 验证 <code>AnnotationFusionVerifier</code> 的运行行为。
 */
class AnnotationFusionVerifierTest {

    /**
     * 验证<code>AnnotationFusionVerifier.main(new String[0])</code> 正常返回。
     */
    @Test
    void mainVerificationPasses() {
        assertDoesNotThrow(() -> AnnotationFusionVerifier.main(new String[0]));
    }

    /**
     * 验证<code>service.hello()</code> 等于 <code>"hello"</code>，<code>service.getClass().getAnnotation(DomainService.class).annotationType()</code> 等于 <code>DomainService.class</code>。
     */
    @Test
    void businessDomainServiceIsAnnotatedAndUsable() {
        BusinessDomainService service = new BusinessDomainService();

        assertEquals("hello", service.hello());
        assertEquals(DomainService.class,
                service.getClass().getAnnotation(DomainService.class).annotationType());
    }
}
