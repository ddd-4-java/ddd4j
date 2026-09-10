/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/** MQ客户端启动状态。 */
public enum MQStartupState {
    NEW,
    STARTING,
    READY,
    DEGRADED,
    FAILED,
    STOPPED
}
