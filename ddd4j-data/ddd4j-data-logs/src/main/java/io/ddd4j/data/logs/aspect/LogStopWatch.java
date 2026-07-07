package io.ddd4j.data.logs.aspect;

/**
 * 纯 Java 计时器，替代 {@code org.springframework.util.StopWatch}。
 * <p>
 * 仅记录单段任务耗时，满足 API 操作日志切面的计时需求，避免在数据层引入 Spring 依赖。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LogStopWatch {

    private final String id;
    private long startTime;
    private long elapsedTime;
    private boolean running;
    private String taskName;

    /**
     * 构造计时器。
     *
     * @param id 计时器标识（通常为 requestId）
     */
    public LogStopWatch(String id) {
        this.id = id;
    }

    /**
     * 启动计时并记录任务名。
     *
     * @param taskName 任务名
     */
    public void start(String taskName) {
        this.taskName = taskName;
        this.startTime = System.nanoTime();
        this.running = true;
    }

    /**
     * 停止计时。
     */
    public void stop() {
        if (this.running) {
            this.elapsedTime = System.nanoTime() - this.startTime;
            this.running = false;
        }
    }

    /**
     * 是否正在计时。
     *
     * @return true 表示正在计时
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * 获取计时器标识。
     *
     * @return 标识
     */
    public String getId() {
        return this.id;
    }

    /**
     * 获取任务名。
     *
     * @return 任务名
     */
    public String getTaskName() {
        return this.taskName;
    }

    /**
     * 获取总耗时（毫秒）。
     *
     * @return 总耗时毫秒数
     */
    public long getTotalTimeMillis() {
        return this.elapsedTime / 1_000_000;
    }

    /**
     * 格式化输出计时信息。
     *
     * @return 可读的计时描述
     */
    public String prettyPrint() {
        return "StopWatch '" + this.id + "': "
                + (this.taskName != null ? this.taskName + " " : "")
                + (this.elapsedTime / 1_000_000) + "ms";
    }

}
