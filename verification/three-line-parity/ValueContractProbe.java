import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 跨版本真实值对象契约探针：只使用 JDK，不替换业务实现。 */
public final class ValueContractProbe {
    private static int failures;
    public static void main(String[] args) throws Exception {
        check("io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo",
                new Class<?>[]{Instant.class, int.class, String.class},
                new Object[]{Instant.EPOCH, 3, null}, new Object[]{null, 0, null},
                new String[][]{{"lastRunAt", "getLastRunAt"}, {"lastEventCount", "getLastEventCount"}, {"lastError", "getLastError"}});
        check("io.ddd4j.data.mybatis.adapter.SqlObservation",
                new Class<?>[]{String.class, String.class, List.class, long.class, Throwable.class},
                new Object[]{"statement", "select 1", Collections.emptyList(), 1000000L, null},
                new Object[]{null, null, null, 0L, null},
                new String[][]{{"statementId", "getStatementId"}, {"sql", "getSql"}, {"sortedParams", "getSortedParams"}, {"elapsedNanos", "getElapsedNanos"}, {"error", "getError"}});
        check("io.ddd4j.mq.redisstream.RedisStreamRecord",
                new Class<?>[]{String.class, String.class, Map.class, Object.class},
                new Object[]{"stream", "1-0", Collections.emptyMap(), "native-a"},
                new Object[]{null, null, null, null},
                new String[][]{{"stream", "getStream"}, {"id", "getId"}, {"fields", "getFields"}, {"nativeMessage", "getNativeMessage"}});
        check("io.ddd4j.extension.qlexpress.model.QLExpressValidationResult",
                new Class<?>[]{boolean.class, String.class},
                new Object[]{true, "valid"}, new Object[]{false, null},
                new String[][]{{"valid", "isValid"}, {"message", "getMessage"}});
        Class<?> type = Class.forName("io.ddd4j.mq.redisstream.RedisStreamRecord");
        Constructor<?> ctor = type.getConstructor(String.class, String.class, Map.class, Object.class);
        Object a = ctor.newInstance("stream", "1-0", Collections.emptyMap(), "native-a");
        Object b = ctor.newInstance("stream", "1-0", Collections.emptyMap(), "native-b");
        require(!a.equals(b), "nativeMessage must participate in equality");
        if (failures > 0) {
            throw new AssertionError(failures + " contract checks failed");
        }
    }
    private static void check(String name, Class<?>[] types, Object[] values,
                              Object[] nullValues, String[][] getters) throws Exception {
        Class<?> type = Class.forName(name);
        Constructor<?> ctor = type.getDeclaredConstructor(types);
        require(Modifier.isPublic(ctor.getModifiers()), name + " constructor must be public");
        ctor.setAccessible(true);
        Object a = ctor.newInstance(values), b = ctor.newInstance(values);
        require(a.equals(b), name + " equal fields must compare equal");
        require(new HashSet<Object>(Arrays.asList(a, b)).size() == 1, name + " must deduplicate by value");
        require(a.hashCode() == b.hashCode(), name + " equal values must hash equally");
        for (String[] pair : getters) {
            try {
                Object component = type.getMethod(pair[0]).invoke(a);
                Object bean = type.getMethod(pair[1]).invoke(a);
                require(Objects.equals(component, bean), name + " accessor aliases must agree: " + pair[0]);
            } catch (NoSuchMethodException ex) {
                require(false, name + " missing public accessor " + ex.getMessage());
            }
        }
        try {
            Object n1 = ctor.newInstance(nullValues), n2 = ctor.newInstance(nullValues);
            require(n1.equals(n2), name + " nullable fields must compare by value");
            require(n1.hashCode() == n2.hashCode(), name + " nullable fields must hash safely");
            System.out.println("NULL_HASH " + name + "=" + n1.hashCode());
            System.out.println("NULL_TEXT " + name + "=" + n1.toString());
        } catch (RuntimeException ex) {
            require(false, name + " nullable values failed: " + ex.getClass().getName());
        }
        List<String> api = new ArrayList<String>();
        for (Constructor<?> c : type.getConstructors()) {
            api.add("constructor" + Arrays.toString(c.getParameterTypes()));
        }
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                api.add(m.getName() + Arrays.toString(m.getParameterTypes()) + ":" + m.getReturnType().getName());
            }
        }
        Collections.sort(api);
        System.out.println("API " + name + "=" + api);
        System.out.println("HASH " + name + "=" + a.hashCode());
        System.out.println("TEXT " + name + "=" + a.toString());
    }
    private static void require(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.err.println("FAIL " + message);
        }
    }
}

