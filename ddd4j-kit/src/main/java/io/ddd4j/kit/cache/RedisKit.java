package io.ddd4j.kit.cache;

import io.ddd4j.core.context.SpringContext;
import io.ddd4j.core.utils.JsonKit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@UtilityClass
@Slf4j(topic = "### BASE-KIT : RedisKit ###")
public class RedisKit {

    // 使用 ThreadLocal 来管理每个线程的 Jedis 实例
    private final ThreadLocal<Jedis> jedisThreadLocal = ThreadLocal.withInitial(RedisKit::createJedis);

    private Jedis createJedis() {
        String host = SpringContext.getEnv().getProperty("spring.redis.host");
        String port = SpringContext.getEnv().getProperty("spring.redis.port", "6379");
        String username = SpringContext.getEnv().getProperty("spring.redis.username");
        String password = SpringContext.getEnv().getProperty("spring.redis.password");
        String database = SpringContext.getEnv().getProperty("spring.redis.database");

        Jedis jedis = new Jedis(host, Integer.parseInt(port));
        if (database != null && !database.isEmpty()) {
            jedis.select(Integer.parseInt(database));
        }
        if (password != null && !password.isEmpty()) {
            if (username != null && !username.isEmpty()) {
                jedis.auth(username, password);
            } else {
                jedis.auth(password);
            }
        }
        return jedis;
    }

    private Jedis jedis() {
        Jedis jedis = jedisThreadLocal.get();
        if (jedis == null || !jedis.isConnected()) {
            jedisThreadLocal.set(createJedis());
        }
        return jedisThreadLocal.get();
    }

    public <T> Boolean set(String key, T value, long expiredSeconds) {
        try (Jedis jedis = jedis()) {
            String result = jedis.set(key, value instanceof String ? (String) value : JsonKit.toJson(value), SetParams.setParams().ex(expiredSeconds));
            return "OK".equals(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set key: " + key, e);
        }
    }

    public <T> Boolean setIfAbsent(String key, T value, long expiredSeconds) {
        try (Jedis jedis = jedis()) {
            String result = jedis.set(key, value instanceof String ? (String) value : JsonKit.toJson(value), SetParams.setParams().ex(expiredSeconds).nx());
            return "OK".equals(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setIfAbsent key: " + key, e);
        }
    }

    public <T> Boolean set(String key, T value) {
        try (Jedis jedis = jedis()) {
            String result = jedis.set(key, value instanceof String ? (String) value : JsonKit.toJson(value));
            return "OK".equals(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set key: " + key, e);
        }
    }

    public <T> Boolean setIfAbsent(String key, T value) {
        try (Jedis jedis = jedis()) {
            Long result = jedis.setnx(key, value instanceof String ? (String) value : JsonKit.toJson(value));
            return result != null && result == 1L;
        } catch (Exception e) {
            throw new RuntimeException("Failed to setIfAbsent key: " + key, e);
        }
    }

    public void increment(String key) {
        try (Jedis jedis = jedis()) {
            jedis.incr(key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to increment key: " + key, e);
        }
    }

    public String get(String key) {
        try (Jedis jedis = jedis()) {
            return jedis.get(key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get key: " + key, e);
        }
    }

    public <T> T get(String key, Class<T> clazz) {
        String value = get(key);
        if (value != null && value.contains("{") && value.endsWith("}")) {
            value = value.substring(value.indexOf("{"), value.lastIndexOf("}") + 1);
        }
        return JsonKit.toObject(value, clazz);
    }


    public List<String> get(List<String> keys) {
        try (Jedis jedis = jedis()) {
            List<String> values = new ArrayList<>();
            for (String key : keys) {
                values.add(jedis.get(key));
            }
            return values;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get keys: " + keys, e);
        }
    }

    public <T> List<T> get(List<String> keys, Class<T> clazz) {
        return JsonKit.toList(get(keys), clazz);
    }

    public Boolean expire(String key, long expiredSeconds) {
        try (Jedis jedis = jedis()) {
            Long result = jedis.expire(key, expiredSeconds);
            return result != null && result == 1L;
        } catch (Exception e) {
            throw new RuntimeException("Failed to expire key: " + key, e);
        }
    }

    public List<String> keys(String pattern) {
        try (Jedis jedis = jedis()) {
            Set<String> keys = jedis.keys(pattern);
            return new ArrayList<>(keys);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get keys with pattern: " + pattern, e);
        }
    }

    public Long delete(String... keys) {
        try (Jedis jedis = jedis()) {
            return jedis.del(keys);
        } catch (Exception e) {
            throw new RuntimeException("Failed to del keys: " + keys, e);
        }
    }

}