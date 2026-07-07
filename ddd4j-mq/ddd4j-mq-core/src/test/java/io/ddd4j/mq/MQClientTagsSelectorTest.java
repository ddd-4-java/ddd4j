package io.ddd4j.mq;

import io.ddd4j.mq.MQProperties;
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

    @Test
    void wildcardShouldReturnNull() {
        assertNull(cli.tagsToSelector("*"));
        assertNull(cli.tagsToSelector(""));
        assertNull(cli.tagsToSelector(null));
        assertNull(cli.tagsToSelector("  *  "));
    }

    @Test
    void singleTagShouldEqualMatch() {
        assertEquals("(ddd4jTag = 'paid' OR ddd4jTag IS NULL)", cli.tagsToSelector("paid"));
    }

    @Test
    void orExpressionShouldGenerateOrChain() {
        assertEquals("(ddd4jTag = 'paid' OR ddd4jTag = 'shipped' OR ddd4jTag IS NULL)",
                cli.tagsToSelector("paid || shipped"));
    }

    @Test
    void wildcardExcludeShouldGenerateNotEqual() {
        assertEquals("(1=1 AND (ddd4jTag <> 'cancelled' OR ddd4jTag IS NULL))",
                cli.tagsToSelector("* -cancelled"));
    }

    @Test
    void singleExcludeShouldEqualMatchAndExclude() {
        assertEquals(
                "((ddd4jTag = 'paid' OR ddd4jTag IS NULL) AND (ddd4jTag <> 'cancelled' OR ddd4jTag IS NULL))",
                cli.tagsToSelector("paid -cancelled"));
    }

    @Test
    void orExpressionWithExclude() {
        assertEquals(
                "((ddd4jTag = 'paid' OR ddd4jTag = 'shipped' OR ddd4jTag IS NULL) "
                        + "AND (ddd4jTag <> 'cancelled' OR ddd4jTag IS NULL))",
                cli.tagsToSelector("paid || shipped -cancelled"));
    }

    @Test
    void singleQuoteShouldBeEscaped() {
        // 防止 tags 内单引号破坏 selector 解析
        String result = cli.tagsToSelector("a'b");
        // a'b 应转义为 a''b，并包在 IN (...) 结构里
        assertEquals("(ddd4jTag = 'a''b' OR ddd4jTag IS NULL)", result);
    }

    @Test
    void tagHeaderKeyShouldDefaultToNoDot() {
        // 必须无 .，否则 JMS selector 当 SQL identifier 会拒绝
        org.junit.jupiter.api.Assertions.assertFalse(
                cli.tagHeaderKey().contains("."),
                "tagHeaderKey() 必须不含 . ，返回: " + cli.tagHeaderKey());
    }
}
