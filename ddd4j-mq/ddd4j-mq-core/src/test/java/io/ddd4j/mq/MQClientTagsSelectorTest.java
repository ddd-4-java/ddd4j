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
package io.ddd4j.mq;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MQClient#tagsToSelector(String)} 表达式转换单测。
 *
 * <p>验证 {@code MQEventListener.tags} 字符串表达式 → JMS Message Selector 的转换正确性，
 * 这是 broker 端精确 tag 过滤的核心。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQClientTagsSelectorTest {

    /** \u6d4b\u8bd5\u8c03\u7528\u7684\u547d\u4ee4\u5ba2\u6237\u7aef\u6216\u547d\u4ee4\u884c\u5165\u53e3\u3002 */
    private final MQClient cli = new MQClient() {
        @Override
        public String impl() {
            return "test";
        }

        @Override
        public Consumer<MQEvent> initProducer(MQProperties properties) {
            return null;
        }

        @Override
        public boolean initConsumer(MQListener listener, MQProperties properties) {
            return false;
        }
    };

    /** \u9a8c\u8bc1<code>cli.tagsToSelector("*")</code>\u4e3a\u7a7a\uff0c<code>cli.tagsToSelector("")</code>\u4e3a\u7a7a\uff0c<code>cli.tagsToSelector(" * ")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void wildcardShouldReturnNull() {
        assertNull(cli.tagsToSelector("*"));
        assertNull(cli.tagsToSelector(""));
        assertNull(cli.tagsToSelector(null));
        assertNull(cli.tagsToSelector("  *  "));
    }

    /** \u9a8c\u8bc1<code>cli.tagsToSelector("paid")</code> \u7b49\u4e8e <code>"(ddd4jTag = 'paid' OR ddd4jTag IS NULL)"</code>\u3002 */
    @Test
    void singleTagShouldEqualMatch() {
        assertEquals("(ddd4jTag = 'paid' OR ddd4jTag IS NULL)", cli.tagsToSelector("paid"));
    }

    /** \u9a8c\u8bc1<code>cli.tagsToSelector("paid || shipped")</code> \u7b49\u4e8e <code>"(ddd4jTag = 'paid' OR ddd4jTag = 'shipped' OR ddd4jTag IS NULL)"</code>\u3002 */
    @Test
    void orExpressionShouldGenerateOrChain() {
        assertEquals("(ddd4jTag = 'paid' OR ddd4jTag = 'shipped' OR ddd4jTag IS NULL)",
                cli.tagsToSelector("paid || shipped"));
    }

    /** \u9a8c\u8bc1<code>cli.tagsToSelector("* -cancelled")</code> \u7b49\u4e8e <code>"(1=1 AND (ddd4jTag &lt;&gt; 'cancelled' OR ddd4jTag IS NULL))"</code>\u3002 */
    @Test
    void wildcardExcludeShouldGenerateNotEqual() {
        assertEquals("(1=1 AND (ddd4jTag <> 'cancelled' OR ddd4jTag IS NULL))",
                cli.tagsToSelector("* -cancelled"));
    }

    /** \u9a8c\u8bc1<code>cli.tagsToSelector("paid -cancelled")</code> \u7b49\u4e8e <code>"((ddd4jTag = 'paid' OR ddd4jTag IS NULL) AND (ddd4jTag &lt;&gt; 'cancelled' OR ddd4jTag IS NULL))"</code>\u3002 */
    @Test
    void singleExcludeShouldEqualMatchAndExclude() {
        assertEquals(
                "((ddd4jTag = 'paid' OR ddd4jTag IS NULL) AND (ddd4jTag <> 'cancelled' OR ddd4jTag IS NULL))",
                cli.tagsToSelector("paid -cancelled"));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>cli.tagsToSelector()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void orExpressionWithExclude() {
        assertEquals(
                "((ddd4jTag = 'paid' OR ddd4jTag = 'shipped' OR ddd4jTag IS NULL) "
                        + "AND (ddd4jTag <> 'cancelled' OR ddd4jTag IS NULL))",
                cli.tagsToSelector("paid || shipped -cancelled"));
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"(ddd4jTag = 'a''b' OR ddd4jTag IS NULL)"</code>\u3002 */
    @Test
    void singleQuoteShouldBeEscaped() {
        // 防止 tags 内单引号破坏 selector 解析
        String result = cli.tagsToSelector("a'b");
        // a'b 应转义为 a''b，并包在 IN (...) 结构里
        assertEquals("(ddd4jTag = 'a''b' OR ddd4jTag IS NULL)", result);
    }

    /** \u9a8c\u8bc1<code>cli.tagHeaderKey().contains(".")</code>\u4e3a\u5047\u3002 */
    @Test
    void tagHeaderKeyShouldDefaultToNoDot() {
        // 必须无 .，否则 JMS selector 当 SQL identifier 会拒绝
        org.junit.jupiter.api.Assertions.assertFalse(
                cli.tagHeaderKey().contains("."),
                "tagHeaderKey() 必须不含 . ，返回: " + cli.tagHeaderKey());
    }
}
