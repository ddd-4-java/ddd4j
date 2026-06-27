
package io.ddd4j.extension.monitor.infras.config;

import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;

/**
 * 日志监控告警配置
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
// @ConfigurationProperties(prefix = "base-monitor")
public class BaseMonitorProperties {
    private Log log = new Log();

    @Data
    public static class Log {
        /**
         * 是否启用
         */
        private boolean enable = true;
        /**
         * 机器人发送速度限制 [每分钟最多20次] 1/3.5~= 0.2857
         */
        private Double rateLimiterPermitsPerSecond = 0.2857;

        /**
         * 钉钉机器人的配置
         */
        private DingTalkRobot dingding = new DingTalkRobot();

        /**
         * 企微
         */
        private QiWeiRobot qiwei = new QiWeiRobot();

        /**
         * 应用配置
         */
        private App app = new App();

        /**
         * 日志配置
         */
        private Config config = new Config();

        @Data
        public static class App {

            private String project = "";

            /**
             * 当前环境 not set is  Environment=>spring.profiles.active
             */
            private String env = "";

            /**
             * 应用名称  not set is  Environment=>spring.application.name
             */
            private String name = "";
        }

        @Data
        public static class Config {

            /**
             * 当前level 等级之上的都打印
             */
            private String logLevel = "ERROR";

            /**
             * 消息中有关键词才钉钉通知 逗号分割
             * 这里使用的表达式 kewWordExpression : return formattedMessage.contains("keyword1") || formattedMessage.contains("keyword2");
             */
            private List<String> includes;

            /**
             * 排除掉message 里面的关键字 不打印
             */
            private List<String> ignores;

            /**
             * 哪些日志名称不打印 包含关键字
             */
            private List<String> ignoreLogNames;

            /**
             * 消息中过滤 自己定义表达式 和 logKeyWords 冲突 {@see http://logback.qos.ch/manual/filters.html#EvaluatorFilter}
             * return  formattedMessage.contains("keyword1") ||  formattedMessage.contains("keyword12");
             */
            private String keywordExpression = "";

            /**
             * 添加到哪些 logger name
             */
            private List<String> appendLoggerNames = Lists.newArrayList("root");

            /**
             * mdc 里面哪些需要打印
             */
            private List<String> mdcList = Lists.newArrayList();

            /**
             * blockingQueue长度决定了队列能放多少信息，在默认的配置下，如果blockingQueue放满了，后续想要输出日志的线程会被阻塞，
             * 直到Worker线程处理掉队列中的信息为止。
             * 根据实际情况适当调整队列长度，可以防止线程被阻塞。
             */
            private Integer asyncAppenderQueueSize = 256;

            /**
             * 如果配置neverBlock=true，当队列满了之后，后面阻塞的线程想要输出的消息就直接被丢弃，从而线程不会阻塞。
             * 这个配置用于线程很重要，不能卡顿，而且日志又不是很重要的场景，因为很有可能会丢日志
             */
            private Boolean asyncAppenderNeverBlock = true;

            /**
             * 提取调用方数据可能相当昂贵。
             * 若要提高性能，默认情况下，当事件添加到事件队列时，不会提取与事件关联的调用方数据。
             * 默认情况下，只有“廉价”数据，如线程名和 都被复制了。
             */
            private Boolean asyncAppenderIncludeCallerData = true;

            /**
             * 最大告警长度，超过会以 …截断
             */
            private Integer maxLength = 800;
        }

        /**
         * 钉钉的配置
         */
        @Data
        public static class DingTalkRobot {
            /**
             * 钉钉机器人配置 webhook
             */
            private String token = "";
            /**
             * 钉钉机器人加签关键字
             */
            private String secret = "";
        }

        /**
         * 企微
         */
        @Data
        public static class QiWeiRobot {
            private String key = "";
        }
    }

}
