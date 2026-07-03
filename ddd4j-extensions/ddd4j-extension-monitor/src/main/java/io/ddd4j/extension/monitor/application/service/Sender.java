package io.ddd4j.extension.monitor.application.service;

/**
 * 消息发送器接口
 *
 * <p>定义消息发送的抽象接口，支持不同渠道的消息推送（如钉钉、企微）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface Sender {

    void send(String msg);
}
