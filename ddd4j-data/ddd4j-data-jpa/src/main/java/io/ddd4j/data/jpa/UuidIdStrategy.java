package io.ddd4j.data.jpa;

import io.ddd4j.kit.lang.IdKit;

/**
 * UUID \u5b57\u7b26\u4e32 ID \u7b56\u7565\uff1a\u751f\u6210 32 \u4f4d\u65e0\u6a2a\u7ebf UUID\u3002
 *
 *  <p>\u59d4\u6258 ddd4j \u6807\u51c6\u5de5\u5177 {@link IdKit#simpleUUID()}\uff08Hutool \u5b9e\u73b0\uff0c\u6bd4 JDK UUID \u9ad8\u6027\u80fd\uff09\u3002
 *  \u9002\u7528\u4e8e\u9700\u8981\u5168\u5c40\u552f\u4e00\u3001\u4e0d\u4f9d\u8d56\u6570\u636e\u5e93\u81ea\u589e\u3001\u65e0\u5b89\u5168\u987a\u5e8f\u6cc4\u9732\u8981\u6c42\u7684\u573a\u666f\u3002
 *  \u4f7f\u7528\u6b64\u7b56\u7565\u65f6\uff0c\u5b9e\u4f53\u4e3b\u952e\u5e94\u4e3a {@code String} \u7c7b\u578b\uff1a
 *  <pre>
 * @Id
 * @GeneratedValue (generator = "ddd4j-uuid")
 * @GenericGenerator (name = "ddd4j-uuid", strategy = "org.hibernate.id.UUIDGenerator")
 *    public String id;
 *  </pre>
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see IdKit
 * @since 3.3.x
 */
public class UuidIdStrategy implements IdGenerationStrategy<String> {

    /**
     * 生成调用所需的标识或结果。
     *
     * <p>此实现调用 <code>IdKit.simpleUUID()</code>。</p>
     *
     * @return 实际返回<code>IdKit.simpleUUID()</code>
     */
    @Override
    public String generate() {
        return IdKit.simpleUUID();
    }
}
