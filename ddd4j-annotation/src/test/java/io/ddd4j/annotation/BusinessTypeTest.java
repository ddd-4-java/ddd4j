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
package io.ddd4j.annotation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BusinessType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BusinessTypeTest {

    /**
     * 验证业务操作枚举含登录、退出、增删改、导入导出、强制退出、生成和清理等预期项目。
     */
    @Test
    void values_shouldContainExpectedEntries() {
        assertThat(BusinessType.values())
                .contains(BusinessType.LOGIN, BusinessType.LOGOUT, BusinessType.INSERT,
                        BusinessType.DELETE, BusinessType.UPDATE, BusinessType.SELECT);
    }

    /**
     * 验证<code>BusinessType.LOGIN.getKey()</code> 等于 <code>"login"</code>，<code>BusinessType.INSERT.getKey()</code> 等于 <code>"insert"</code>。
     */
    @Test
    void getKey_shouldReturnIdentifier() {
        assertThat(BusinessType.LOGIN.getKey()).isEqualTo("login");
        assertThat(BusinessType.INSERT.getKey()).isEqualTo("insert");
    }

    /**
     * 验证<code>BusinessType.LOGIN.getDesc()</code> 等于 <code>"用户登录"</code>，<code>BusinessType.UPLOAD.getDesc()</code> 等于 <code>"文件上传"</code>。
     */
    @Test
    void getDesc_shouldReturnDescription() {
        assertThat(BusinessType.LOGIN.getDesc()).isEqualTo("用户登录");
        assertThat(BusinessType.UPLOAD.getDesc()).isEqualTo("文件上传");
    }

    /**
     * 验证<code>map</code> 含对应条目 <code>"key"</code>、<code>"login"</code>，<code>map</code> 含对应条目 <code>"desc"</code>、<code>"用户登录"</code>。
     */
    @Test
    void toMap_shouldContainKeyAndDesc() {
        Map<String, String> map = BusinessType.LOGIN.toMap();

        assertThat(map).containsEntry("key", "login");
        assertThat(map).containsEntry("desc", "用户登录");
    }

    /**
     * 验证<code>list</code> 的大小为 <code>BusinessType.values().length</code>。
     */
    @Test
    void toList_shouldContainAllEntries() {
        List<Map<String, String>> list = BusinessType.toList();

        assertThat(list).hasSize(BusinessType.values().length);
        assertThat(list.get(0)).containsKey("key");
        assertThat(list.get(0)).containsKey("desc");
    }

    /**
     * 验证<code>BusinessType.LOGIN.equals(BusinessType.LOGIN)</code>为真，<code>BusinessType.LOGIN.equals(BusinessType.LOGOUT)</code>为假。
     */
    @Test
    void equals_shouldReturnTrueForSameType() {
        assertThat(BusinessType.LOGIN.equals(BusinessType.LOGIN)).isTrue();
        assertThat(BusinessType.LOGIN.equals(BusinessType.LOGOUT)).isFalse();
    }

    /**
     * 验证<code>type.getKey()</code> 等于 <code>"custom-key"</code>，<code>type.getDesc()</code> 等于 <code>"custom-desc"</code>。
     */
    @Test
    void setKeyAndSetDesc_shouldRoundTrip() {
        BusinessType type = BusinessType.LOGIN;
        type.setKey("custom-key");
        type.setDesc("custom-desc");

        assertThat(type.getKey()).isEqualTo("custom-key");
        assertThat(type.getDesc()).isEqualTo("custom-desc");

        // restore to avoid leaking state into other tests (enum is singleton)
        type.setKey("login");
        type.setDesc("用户登录");
    }
}
