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

    /** \u9a8c\u8bc1\u4e1a\u52a1\u64cd\u4f5c\u679a\u4e3e\u542b\u767b\u5f55\u3001\u9000\u51fa\u3001\u589e\u5220\u6539\u3001\u5bfc\u5165\u5bfc\u51fa\u3001\u5f3a\u5236\u9000\u51fa\u3001\u751f\u6210\u548c\u6e05\u7406\u7b49\u9884\u671f\u9879\u76ee\u3002 */
    @Test
    void values_shouldContainExpectedEntries() {
        assertThat(BusinessType.values())
                .contains(BusinessType.LOGIN, BusinessType.LOGOUT, BusinessType.INSERT,
                        BusinessType.DELETE, BusinessType.UPDATE, BusinessType.SELECT);
    }

    /** \u9a8c\u8bc1<code>BusinessType.LOGIN.getKey()</code> \u7b49\u4e8e <code>"login"</code>\uff0c<code>BusinessType.INSERT.getKey()</code> \u7b49\u4e8e <code>"insert"</code>\u3002 */
    @Test
    void getKey_shouldReturnIdentifier() {
        assertThat(BusinessType.LOGIN.getKey()).isEqualTo("login");
        assertThat(BusinessType.INSERT.getKey()).isEqualTo("insert");
    }

    /** \u9a8c\u8bc1<code>BusinessType.LOGIN.getDesc()</code> \u7b49\u4e8e <code>"\u7528\u6237\u767b\u5f55"</code>\uff0c<code>BusinessType.UPLOAD.getDesc()</code> \u7b49\u4e8e <code>"\u6587\u4ef6\u4e0a\u4f20"</code>\u3002 */
    @Test
    void getDesc_shouldReturnDescription() {
        assertThat(BusinessType.LOGIN.getDesc()).isEqualTo("用户登录");
        assertThat(BusinessType.UPLOAD.getDesc()).isEqualTo("文件上传");
    }

    /** \u9a8c\u8bc1<code>map</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"key"</code>\u3001<code>"login"</code>\uff0c<code>map</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"desc"</code>\u3001<code>"\u7528\u6237\u767b\u5f55"</code>\u3002 */
    @Test
    void toMap_shouldContainKeyAndDesc() {
        Map<String, String> map = BusinessType.LOGIN.toMap();

        assertThat(map).containsEntry("key", "login");
        assertThat(map).containsEntry("desc", "用户登录");
    }

    /** \u9a8c\u8bc1<code>list</code> \u7684\u5927\u5c0f\u4e3a <code>BusinessType.values().length</code>\u3002 */
    @Test
    void toList_shouldContainAllEntries() {
        List<Map<String, String>> list = BusinessType.toList();

        assertThat(list).hasSize(BusinessType.values().length);
        assertThat(list.get(0)).containsKey("key");
        assertThat(list.get(0)).containsKey("desc");
    }

    /** \u9a8c\u8bc1<code>BusinessType.LOGIN.equals(BusinessType.LOGIN)</code>\u4e3a\u771f\uff0c<code>BusinessType.LOGIN.equals(BusinessType.LOGOUT)</code>\u4e3a\u5047\u3002 */
    @Test
    void equals_shouldReturnTrueForSameType() {
        assertThat(BusinessType.LOGIN.equals(BusinessType.LOGIN)).isTrue();
        assertThat(BusinessType.LOGIN.equals(BusinessType.LOGOUT)).isFalse();
    }

    /** \u9a8c\u8bc1<code>type.getKey()</code> \u7b49\u4e8e <code>"custom-key"</code>\uff0c<code>type.getDesc()</code> \u7b49\u4e8e <code>"custom-desc"</code>\u3002 */
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
