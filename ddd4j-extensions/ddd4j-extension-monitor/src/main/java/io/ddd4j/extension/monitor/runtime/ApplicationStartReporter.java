/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.monitor.runtime;

import io.ddd4j.extension.monitor.Sender;
import io.ddd4j.extension.monitor.message.CodeVersion;
import io.ddd4j.kit.lang.StrKit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * 应用启动通告。
 *
 * <p>读取 classpath 下的 {@code git.properties}（由 maven-git-commit-id 插件生成），
 * 在应用就绪后通过 {@link Sender} 向机器人发送 markdown 格式的启动通知。
 *
 * <p>纯 Java，不依赖任何 Web 框架；上层框架在应用就绪后调用 {@link #init()} 即可。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### DDD4j-MONITOR : ApplicationStartReporter ###")
public class ApplicationStartReporter {

    /**
     * 当前代码版本，应用启动时由 {@link #init()} 写入。
     */
    public static CodeVersion CODE_VERSION = null;

    /** \u53d1\u5e03\u5e94\u7528\u542f\u52a8\u6d88\u606f\u7684\u53d1\u9001\u7ec4\u4ef6\u3002 */
    private final Sender sender;
    /** \u542f\u52a8\u901a\u77e5\u4e2d\u663e\u793a\u7684\u5e94\u7528\u540d\u79f0\u3002 */
    private final String appName;

    /**
     * \u521b\u5efa\u5e94\u7528\u542f\u52a8\u901a\u544a\u5668\u3002
     * @param sender \u6d88\u606f\u53d1\u9001\u5668
     * @param appName \u5e94\u7528\u540d\u79f0
     */
    public ApplicationStartReporter(Sender sender, String appName) {
        this.sender = sender;
        this.appName = appName;
    }

    /**
     * \u628a\u975e\u7a7a\u767d\u5143\u6570\u636e\u8ffd\u52a0\u4e3a\u901a\u77e5\u4e2d\u7684 Markdown \u5b57\u6bb5\u3002
     * @param sb \u901a\u77e5\u6587\u672c\u7f13\u51b2\u533a
     * @param key \u5b57\u6bb5\u663e\u793a\u540d\u79f0
     * @param value \u5f85\u5199\u5165\u7684\u5b57\u6bb5\u503c
     */
    private static void markdownAppend(StringBuilder sb, String key, String value) {
        if (Objects.nonNull(value) && StrKit.isNotBlank(value)) {
            sb.append("**").append(key).append(":** ").append(value).append("\n");
        }
    }

    /**
     * @return 注入的应用名称（用于启动通告文案）
     */
    public String appName() {
        return appName;
    }

    /**
     * 读取 {@code git.properties} 并发送启动通知。
     *
     * <p>{@code git.properties} 缺失时不抛异常（仅日志记录），保证非 maven-git-commit-id 环境也能跑。
     */
    @PostConstruct
    public void init() {
        Properties p = new Properties();
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (Objects.isNull(in)) {
                log.debug("未找到 git.properties，跳过启动通知");
                return;
            }
            p.load(in);
        } catch (IOException e) {
            log.debug("读取 git.properties 失败: {}", e.getMessage());
            return;
        }
        CODE_VERSION = new CodeVersion(
                p.getProperty("git.branch"),
                p.getProperty("git.build.time"),
                p.getProperty("git.build.version"),
                p.getProperty("git.commit.id"),
                p.getProperty("git.commit.message.full"),
                p.getProperty("git.commit.user.name"),
                p.getProperty("git.commit.time"));
        log.info("代码版本 => {}", CODE_VERSION);
        sender.send(buildNotice(CODE_VERSION));
    }

    /**
     * \u5c06\u5e94\u7528\u540d\u4e0e\u7248\u672c\u5143\u6570\u636e\u7ec4\u88c5\u4e3a\u542f\u52a8\u6210\u529f\u901a\u77e5\u3002
     * @param v \u5f53\u524d\u6784\u5efa\u7684\u7248\u672c\u548c\u63d0\u4ea4\u4fe1\u606f
     * @return \u53d1\u9001\u7ed9\u542f\u52a8\u901a\u77e5\u6e20\u9053\u7684 Markdown \u6587\u672c
     */
    private String buildNotice(CodeVersion v) {
        StringBuilder sb = new StringBuilder();
        sb.append(StrKit.format("应用 **{0}** 启动成功！", appName)).append("\n");
        markdownAppend(sb, "提交ID", v.getCommitId());
        markdownAppend(sb, "提交用户", v.getCommitUser());
        markdownAppend(sb, "提交信息", v.getCommitMessage());
        markdownAppend(sb, "提交时间", v.getCommitTime());
        return sb.toString();
    }
}
