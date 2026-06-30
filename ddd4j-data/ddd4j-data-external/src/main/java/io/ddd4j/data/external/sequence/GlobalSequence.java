package io.ddd4j.data.external.sequence;

import cn.hutool.core.lang.Snowflake;
import io.ddd4j.kit.lang.IdKit;

/**
 * Framework-independent global sequence generator.
 */
public class GlobalSequence {

    private final Snowflake snowflake;

    public GlobalSequence(long workerId, long dataCenterId, boolean useSystemClock,
                          long timeOffset, long randomSequenceLimit) {
        this.snowflake = IdKit.getSnowflake(workerId, dataCenterId, useSystemClock, timeOffset, randomSequenceLimit);
    }

    public long nextId() {
        return snowflake.nextId();
    }

    public String nextIdStr() {
        return snowflake.nextIdStr();
    }

    public Snowflake getSnowflake() {
        return snowflake;
    }

    public void shutdown() {
        // Reserved for implementations that allocate external resources.
    }
}
