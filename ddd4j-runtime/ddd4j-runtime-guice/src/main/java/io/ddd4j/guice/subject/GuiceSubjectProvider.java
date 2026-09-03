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
package io.ddd4j.guice.subject;

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice 实现的 Subject 提供者
 * <p>
 * 通过 Guice {@link Injector} 查找已绑定的 Subject 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuiceSubjectProvider implements SubjectProvider {

    private static final Logger log = LoggerFactory.getLogger(GuiceSubjectProvider.class);

    /**
     * Guice 注入器
     */
    @Inject
    private Injector injector;

    @Override
    public Subject getSubject() {
        Optional<Subject> subject = Optional.ofNullable(injector).map(inj -> {
            try {
                return inj.getInstance(Subject.class);
            } catch (Exception e) {
                log.debug("No Subject binding found: {}", e.getMessage());
                return null;
            }
        });
        return subject.orElse(null);
    }
}
