/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.auth.security.jwt;

import com.github.hiwepy.jwt.time.JwtTimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperationTemplate;

@Slf4j
public class JwtTimeRedisProvider implements JwtTimeProvider {

    private RedisOperationTemplate redisOperation;

    public JwtTimeRedisProvider(RedisOperationTemplate redisOperation) {
        super();
        this.redisOperation = redisOperation;
    }

    @Override
    public long now() {
        try {
            return redisOperation.timeNow();
            //return DateUtils.parseDate(nowString, "yyyy-MM-dd HH:mm:ss").getTime();
        } catch (Exception e) {
            log.warn("Failed to read Redis time, fallback to local system time", e);
        }
        return System.currentTimeMillis();
    }

}
