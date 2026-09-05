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
package io.ddd4j.quarkus.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus CDI 实现的 Subject 提供者
 * <p>
 * 通过 CDI {@code Instance<Subject>} 查找已注册的 Subject Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@DefaultBean
@ApplicationScoped
public class CdiSubjectProvider implements SubjectProvider {

    /**
     * CDI Subject 实例
     */
    @Inject
    Instance<Subject> subjectInstance;

    @Override
    public Subject getSubject() {
        if (subjectInstance.isUnsatisfied() || subjectInstance.isAmbiguous()) {
            log.debug("No unique Subject bean found, returning null");
            return null;
        }
        return subjectInstance.get();
    }
}
