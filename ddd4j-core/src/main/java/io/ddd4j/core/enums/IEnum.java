package io.ddd4j.core.enums;

import java.io.Serializable;

/**
 * 双值枚举接口
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface IEnum<T extends Serializable> {
    /**
     * \u83b7\u53d6\u679a\u4e3e\u7f16\u7801\u3002
     * @return \u679a\u4e3e\u7f16\u7801
     */
    T getCode();

    /**
     * \u83b7\u53d6\u679a\u4e3e\u8bf4\u660e\u3002
     * @return \u679a\u4e3e\u8bf4\u660e
     */
    String getDesc();
}
