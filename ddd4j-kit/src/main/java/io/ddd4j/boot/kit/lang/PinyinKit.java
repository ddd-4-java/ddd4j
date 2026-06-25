package io.ddd4j.boot.kit.lang;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具类
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Slf4j(topic = "### BASE-KIT : PinyinKit ###")
@UtilityClass
public class PinyinKit {
    HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

    static {
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    public String toPinyin(String hanyu) {
        try {
            return PinyinHelper.toHanYuPinyinString(hanyu, format, "", true);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换拼音失败", e);
            return hanyu;
        }
    }


    public String toPINYIN(String hanyu) {
        try {
            return PinyinHelper.toHanYuPinyinString(hanyu, format, "", true).toUpperCase();
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换拼音失败", e);
            return hanyu;
        }
    }
}
