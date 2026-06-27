package io.ddd4j.kit.lang;

import cn.hutool.core.util.StrUtil;
import lombok.experimental.UtilityClass;

/**
 * 字符串工具类
 *
 * @author Loong Wan
 */
@UtilityClass
public class StrKit extends StrUtil {

	/**
	 * 判断字符串是否非空（与 isNotBlank 语义一致，兼容历史调用）
	 */
	public static boolean setIsNotBlank(CharSequence str) {
		return isNotBlank(str);
	}

	/**
	 * 判断字符串是否为空（与 isBlank 语义一致，兼容历史调用）
	 */
	public static boolean setIsBlank(CharSequence str) {
		return isBlank(str);
	}
}