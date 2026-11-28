/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/**
 * MQ客户端启动状态。
 */
public enum MQStartupState {
    /** 已创建尚未启动。 */
    NEW,
    /** 正在启动中。 */
    STARTING,
    /** 启动完成且全部监听器正常。 */
    READY,
    /** 启动完成但存在监听器初始化失败，处于降级可用状态。 */
    DEGRADED,
    /** 启动失败。 */
    FAILED,
    /** 已停止。 */
    STOPPED
}
