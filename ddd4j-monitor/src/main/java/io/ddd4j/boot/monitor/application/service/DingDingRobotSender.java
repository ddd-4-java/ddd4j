package io.ddd4j.boot.monitor.application.service;

import io.ddd4j.boot.monitor.domain.dingding.service.DingDingService;

/**
 * 钉钉机器人
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public class DingDingRobotSender implements Sender {

    private final String token;
    private final String secret;

    public DingDingRobotSender(String token, String secret) {
        this.token = token;
        this.secret = secret;
    }

    @Override
    public void send(String msg) {
        DingDingService.send(token, secret, msg);
    }
}
