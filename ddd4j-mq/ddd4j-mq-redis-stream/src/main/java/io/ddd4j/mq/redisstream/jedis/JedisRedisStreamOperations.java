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
package io.ddd4j.mq.redisstream.jedis;

import io.ddd4j.mq.redisstream.RedisStreamOperations;
import io.ddd4j.mq.redisstream.RedisStreamRecord;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.*;

/**
 * 基于 Jedis 客户端的 Redis Stream 操作实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class JedisRedisStreamOperations implements RedisStreamOperations {

    private final UnifiedJedis jedis;

    public JedisRedisStreamOperations(UnifiedJedis jedis) {
        this.jedis = Objects.requireNonNull(jedis, "jedis");
    }

    @Override
    public String add(String stream, Map<String, String> fields) {
        return jedis.xadd(stream, XAddParams.xAddParams().id(StreamEntryID.NEW_ENTRY), fields).toString();
    }

    @Override
    public void createGroup(String stream, String group) {
        try {
            jedis.xgroupCreate(stream, group, StreamEntryID.XGROUP_LAST_ENTRY, true);
        } catch (JedisDataException ex) {
            if (Objects.isNull(ex.getMessage()) || !ex.getMessage().contains("BUSYGROUP")) {
                throw ex;
            }
        }
    }

    @Override
    public Map<String, List<RedisStreamRecord>> readGroup(
            String group,
            String consumer,
            int count,
            int blockMillis,
            Set<String> streams) {
        XReadGroupParams params = XReadGroupParams.xReadGroupParams()
                .count(count)
                .block(blockMillis);
        Map<String, StreamEntryID> offsets = new HashMap<>();
        streams.forEach(stream -> offsets.put(stream, StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY));
        Map<String, List<StreamEntry>> records = jedis.xreadGroupAsMap(group, consumer, params, offsets);
        Map<String, List<RedisStreamRecord>> result = new HashMap<>();
        if (Objects.isNull(records) || records.isEmpty()) {
            return result;
        }
        records.forEach((stream, entries) -> {
            List<RedisStreamRecord> normalized = new ArrayList<>();
            for (StreamEntry entry : entries) {
                normalized.add(new RedisStreamRecord(stream, entry.getID().toString(), entry.getFields(), entry));
            }
            result.put(stream, normalized);
        });
        return result;
    }

    @Override
    public void ack(String stream, String group, String entryId) {
        jedis.xack(stream, group, new StreamEntryID(entryId));
    }

    @Override
    public Object nativeClient() {
        return jedis;
    }

    @Override
    public void close() {
        jedis.close();
    }
}
