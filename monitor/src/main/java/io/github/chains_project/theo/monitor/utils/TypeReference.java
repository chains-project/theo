package io.github.chains_project.theo.monitor.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * This class is a copy of the original class from the jackson databind library.
 */
public abstract class TypeReference<T> implements Comparable<TypeReference<T>> {
    protected final Type _type;

    protected TypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof Class<?>) { // sanity check, should never happen
            throw new IllegalArgumentException("Internal error: TypeReference constructed without actual type information");
        }

        _type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() {
        return _type;
    }

    /**
     * The only reason we define this method (and require implementation
     */
    @Override
    public int compareTo(TypeReference<T> o) {
        return 0;
    }
    // just need an implementation, not a good one... hence ^^^
}
