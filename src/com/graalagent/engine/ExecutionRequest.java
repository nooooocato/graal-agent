package com.graalagent.engine;

import java.time.Duration;

/**
 * 脚本求值请求不可变模型
 */
public record ExecutionRequest(
        String script,
        Object commandSource,
        boolean privileged,
        Duration timeout
) {}
