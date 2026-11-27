/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/**
 * MQ客户端启动状态。
 */
public enum MQStartupState {
    /** \u5c1a\u672a\u542f\u52a8\u3002 */
    NEW,
    /** \u6b63\u5728\u542f\u52a8\u3002 */
    STARTING,
    /** \u5df2\u5c31\u7eea\u3002 */
    READY,
    /** \u90e8\u5206\u76d1\u542c\u5668\u542f\u52a8\u5931\u8d25\u3002 */
    DEGRADED,
    /** \u542f\u52a8\u5931\u8d25\u3002 */
    FAILED,
    /** \u5df2\u505c\u6b62\u3002 */
    STOPPED
}
