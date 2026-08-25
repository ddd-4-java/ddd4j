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
package io.ddd4j.data.jpa;

/**
 * 数据库自增 ID 策略：生成时返回 {@code null}，由数据库在 insert 时填充主键。
 *
 * <p>对标 cloud-das 的 {@code IdGenerator}（数据库自增场景）。
 * 使用此策略时，实体主键应标注：
 * <pre>
 *   @Id
 *   @GeneratedValue(strategy = GenerationType.IDENTITY)
 *   public Long id;
 * </pre>
 *
 * <p>本策略主要用于编程式生成场景下的占位（表示"由 DB 决定"），
 * 实际持久化由 ORM 的 {@code IDENTITY} 策略完成。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.3.x
 */
public class AutoIncrementIdStrategy implements IdGenerationStrategy<Long> {

    @Override
    public Long generate() {
        // 返回 null 表示由数据库自增列填充
        return null;
    }
}
