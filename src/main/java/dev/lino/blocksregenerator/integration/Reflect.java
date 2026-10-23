package dev.lino.blocksregenerator.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.Plugin;

final class Reflect {
    private static final Map<Key, Method> METHODS = new ConcurrentHashMap<>();

    private Reflect() {
    }

    static Class<?> type(Plugin plugin, String name) throws ClassNotFoundException {
        return Class.forName(name, true, plugin.getClass().getClassLoader());
    }

    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        Key key = new Key(type, name, target instanceof Class<?>, Arrays.stream(args)
                .map(arg -> arg == null ? Void.class : arg.getClass()).toList());
        Method method = METHODS.get(key);
        if (method == null) {
            List<Method> candidates = Arrays.stream(type.getMethods()).filter(candidate -> candidate.getName().equals(name))
                    .filter(candidate -> key.staticCall() == Modifier.isStatic(candidate.getModifiers()))
                    .filter(candidate -> !candidate.isBridge())
                    .filter(candidate -> accepts(candidate.getParameterTypes(), args))
                    .sorted(Comparator.comparing(Method::toGenericString)).toList();
            List<Method> specific = candidates.stream().filter(candidate -> candidates.stream().noneMatch(other ->
                    other != candidate && moreSpecific(other.getParameterTypes(), candidate.getParameterTypes()))).toList();
            if (specific.isEmpty()) throw new NoSuchMethodException(type.getName() + "." + name);
            method = specific.getFirst();
            for (Method other : specific) {
                if (!Arrays.equals(method.getParameterTypes(), other.getParameterTypes())) {
                    throw new NoSuchMethodException("Ambiguous overload: " + type.getName() + "." + name);
                }
            }
            METHODS.put(key, method);
        }
        try {
            return method.invoke(target instanceof Class<?> ? null : target, args);
        } catch (InvocationTargetException error) {
            throw new ReflectiveOperationException(type.getName() + "." + name + " failed", error.getCause());
        }
    }

    static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        return type.getField(name).get(target instanceof Class<?> ? null : target);
    }

    private static boolean accepts(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) {
            return false;
        }
        for (int index = 0; index < args.length; index++) {
            if (args[index] == null ? parameters[index].isPrimitive() : !boxed(parameters[index]).isInstance(args[index])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static boolean moreSpecific(Class<?>[] left, Class<?>[] right) {
        boolean narrower = false;
        for (int index = 0; index < left.length; index++) {
            if (!boxed(right[index]).isAssignableFrom(boxed(left[index]))) return false;
            if (boxed(left[index]) == boxed(right[index])) {
                if (left[index].isPrimitive() && !right[index].isPrimitive()) return false;
                narrower |= !left[index].isPrimitive() && right[index].isPrimitive();
            } else narrower = true;
        }
        return narrower;
    }

    private record Key(Class<?> type, String method, boolean staticCall, java.util.List<Class<?>> arguments) {
    }
}
