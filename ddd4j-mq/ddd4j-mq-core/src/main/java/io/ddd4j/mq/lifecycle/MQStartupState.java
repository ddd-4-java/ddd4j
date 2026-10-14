/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/**
 * MQ客户端启动状态。
 */
public enum MQStartupState {
    /** 尚未启动。 */
    NEW,
    /** 正在启动。 */
    STARTING,
    /** 已就绪。 */
    READY,
    /** 部分监听器启动失败。 */
    DEGRADED,
    /** 启动失败。 */
    FAILED,
    /** 已停止。 */
    STOPPED
}
