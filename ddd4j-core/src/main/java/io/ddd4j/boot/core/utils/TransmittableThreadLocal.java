package io.ddd4j.boot.core.utils;

import java.util.WeakHashMap;

/**
 * 跨线程传递线程局部变量类
 * 从com.alibaba:transmittable-thread-local移植精简
 *
 * @param <T>
 */
public class TransmittableThreadLocal<T> extends InheritableThreadLocal<T> {

    private final boolean disableIgnoreNullValueSemantics;

    public TransmittableThreadLocal() {
        this.disableIgnoreNullValueSemantics = false;
    }

    @Override
    public final T get() {
        T value = super.get();
        if (disableIgnoreNullValueSemantics || null != value) addThisToHolder();
        return value;
    }

    @Override
    public final void set(T value) {
        if (!disableIgnoreNullValueSemantics && null == value) {
            // may set null to remove value
            remove();
        } else {
            super.set(value);
            addThisToHolder();
        }
    }

    // see {@link InheritableThreadLocal#remove()}
    @Override
    public final void remove() {
        removeThisFromHolder();
        super.remove();
    }

    private static final InheritableThreadLocal<WeakHashMap<TransmittableThreadLocal<Object>, ?>> holder =
            new InheritableThreadLocal<WeakHashMap<TransmittableThreadLocal<Object>, ?>>() {
                @Override
                protected WeakHashMap<TransmittableThreadLocal<Object>, ?> initialValue() {
                    return new WeakHashMap<>();
                }

                @Override
                protected WeakHashMap<TransmittableThreadLocal<Object>, ?> childValue(WeakHashMap<TransmittableThreadLocal<Object>, ?> parentValue) {
                    return new WeakHashMap<TransmittableThreadLocal<Object>, Object>(parentValue);
                }
            };

    @SuppressWarnings("unchecked")
    private void addThisToHolder() {
        if (!holder.get().containsKey(this)) {
            holder.get().put((TransmittableThreadLocal<Object>) this, null); // WeakHashMap supports null value.
        }
    }

    private void removeThisFromHolder() {
        holder.get().remove(this);
    }

}
