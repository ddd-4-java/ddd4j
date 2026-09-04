package io.ddd4j.extension.monitor.message;

import lombok.Data;

import java.util.List;

/**
 * @ 通知配置。was {@code AtVO}。
 *
 * <p>传入机器人消息体时使用，控制消息是否 @ 一些用户或 @ 全体。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class At {

    /**
     * 被 @ 的手机号列表
     */
    private List<String> atMobiles;
    /**
     * 是否 @ 所有人
     */
    private Boolean isAtAll = false;

    /**
     * 创建一个空 @（不 @ 任何用户）。
     */
    public static At none() {
        return new At();
    }

    /**
     * 创建一个 @ 全体。
     */
    public static At all() {
        At at = new At();
        at.setIsAtAll(true);
        return at;
    }

    /**
     * 创建一个针对指定手机号列表的 @。
     *
     * @param mobiles 手机号列表
     */
    public static At ofMobiles(List<String> mobiles) {
        At at = new At();
        at.setAtMobiles(mobiles);
        return at;
    }
}
