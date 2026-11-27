package io.ddd4j.kit.lang;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-KIT : PinyinKit ###")
@UtilityClass
public class PinyinKit {
    /** \u5f53\u524d\u8f6c\u6362\u64cd\u4f5c\u4f7f\u7528\u7684\u6c49\u8bed\u62fc\u97f3\u8f93\u51fa\u683c\u5f0f\u3002 */
    HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

    static {
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /**
     * \u5c06\u6c49\u5b57\u8f6c\u6362\u4e3a\u62fc\u97f3\u3002
     * @param hanyu \u5f85\u8f6c\u6362\u7684\u6c49\u5b57
     * @return \u62fc\u97f3\u5b57\u7b26\u4e32
     */
    public String toPinyin(String hanyu) {
        try {
            return PinyinHelper.toHanYuPinyinString(hanyu, format, "", true);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换拼音失败", e);
            return hanyu;
        }
    }


    /**
     * \u5c06\u6c49\u5b57\u8f6c\u6362\u4e3a\u5927\u5199\u62fc\u97f3\u3002
     * @param hanyu \u5f85\u8f6c\u6362\u7684\u6c49\u5b57
     * @return \u5927\u5199\u62fc\u97f3\u5b57\u7b26\u4e32
     */
    public String toPINYIN(String hanyu) {
        try {
            return PinyinHelper.toHanYuPinyinString(hanyu, format, "", true).toUpperCase();
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换拼音失败", e);
            return hanyu;
        }
    }
}
