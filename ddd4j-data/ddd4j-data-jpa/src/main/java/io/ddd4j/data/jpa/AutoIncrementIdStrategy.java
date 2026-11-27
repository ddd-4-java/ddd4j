package io.ddd4j.data.jpa;

/**
 * \u6570\u636e\u5e93\u81ea\u589e ID \u7b56\u7565\uff1a\u751f\u6210\u65f6\u8fd4\u56de {@code null}\uff0c\u7531\u6570\u636e\u5e93\u5728 insert \u65f6\u586b\u5145\u4e3b\u952e\u3002
 *
 *  <p>\u5bf9\u6807 cloud-das \u7684 {@code IdGenerator}\uff08\u6570\u636e\u5e93\u81ea\u589e\u573a\u666f\uff09\u3002
 *  \u4f7f\u7528\u6b64\u7b56\u7565\u65f6\uff0c\u5b9e\u4f53\u4e3b\u952e\u5e94\u6807\u6ce8\uff1a
 *  <pre>
 * @Id
 * @GeneratedValue (strategy = GenerationType.IDENTITY)
 *    public Long id;
 *  </pre>
 *
 *  <p>\u672c\u7b56\u7565\u4e3b\u8981\u7528\u4e8e\u7f16\u7a0b\u5f0f\u751f\u6210\u573a\u666f\u4e0b\u7684\u5360\u4f4d\uff08\u8868\u793a"\u7531 DB \u51b3\u5b9a"\uff09\uff0c
 *  \u5b9e\u9645\u6301\u4e45\u5316\u7531 ORM \u7684 {@code IDENTITY} \u7b56\u7565\u5b8c\u6210\u3002
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.3.x
 */
public class AutoIncrementIdStrategy implements IdGenerationStrategy<Long> {

    /**
     * 生成调用所需的标识或结果。
     *
     * @return 此实现返回<code>null</code>
     */
    @Override
    public Long generate() {
        // 返回 null 表示由数据库自增列填充
        return null;
    }
}
