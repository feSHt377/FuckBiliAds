package com.fuck.bili.ads.core;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection helper used by the native libxposed implementation.
 *
 * <p>It intentionally mimics the lookup behaviour of the removed legacy helpers
 * calls: assignable parameter matching, box/unbox conversion, null matching any
 * reference parameter, superclass/interface traversal and InvocationTarget
 * unwrapping. It never touches Xposed classes.
 */
public final class Reflect {

    private Reflect() {
    }

    // ------------------------------------------------------------------- classes

    public static Class<?> findClass(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ----------------------------------------------------------------- methods

    public static Object callMethod(Object target, String name, Object... args) throws Throwable {
        if (target == null) {
            throw new NullPointerException("target");
        }
        Method method = findMethod(target.getClass(), name, args, false);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }
        return invoke(method, target, args);
    }

    public static Object callStaticMethod(Class<?> type, String name, Object... args) throws Throwable {
        if (type == null) {
            throw new NullPointerException("type");
        }
        Method method = findMethod(type, name, args, true);
        if (method == null) {
            throw new NoSuchMethodException(type.getName() + "#" + name);
        }
        return invoke(method, null, args);
    }

    public static Object tryCallMethod(Object target, String name, Object... args) {
        try {
            return callMethod(target, name, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Finds a compatible method, following the legacy helper semantics. */
    public static Method findMethod(Class<?> start, String name, Object[] args, boolean staticOnly) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            collectMethods(type, name, args, staticOnly, candidates);
        }
        // The legacy helper also resolves interface methods (default methods,
        // callbacks implemented through an interface). Walk the interface graph.
        List<Class<?>> interfaces = new ArrayList<>();
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            collectInterfaces(type, interfaces);
        }
        for (Class<?> type : interfaces) {
            collectMethods(type, name, args, staticOnly, candidates);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Method best = null;
        for (Method candidate : candidates) {
            if (best == null || isMoreSpecific(candidate, best)) {
                best = candidate;
            }
        }
        if (best != null) {
            best.setAccessible(true);
        }
        return best;
    }

    private static void collectMethods(Class<?> type, String name, Object[] args,
                                       boolean staticOnly, List<Method> out) {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (staticOnly && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (isCompatible(method.getParameterTypes(), args)) {
                out.add(method);
            }
        }
    }

    private static void collectInterfaces(Class<?> type, List<Class<?>> out) {
        for (Class<?> iface : type.getInterfaces()) {
            if (!out.contains(iface)) {
                out.add(iface);
                collectInterfaces(iface, out);
            }
        }
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, prepareArgs(method.getParameterTypes(), args));
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    // ------------------------------------------------------------ constructors

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object newInstance(Class<?> type, Object... args) throws Throwable {
        Constructor best = null;
        for (Constructor constructor : type.getDeclaredConstructors()) {
            if (isCompatible(constructor.getParameterTypes(), args)) {
                if (best == null
                        || isMoreSpecific(constructor.getParameterTypes(), best.getParameterTypes())) {
                    best = constructor;
                }
            }
        }
        if (best == null) {
            throw new NoSuchMethodException(type.getName() + "#<init>");
        }
        best.setAccessible(true);
        try {
            return best.newInstance(prepareArgs(best.getParameterTypes(), args));
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new IllegalStateException("constructor invocation failed", e);
        }
    }

    // ------------------------------------------------------------------- fields

    public static Object getObjectField(Object target, String name) throws Throwable {
        Field field = findField(target.getClass(), name);
        return field == null ? null : field.get(target);
    }

    public static Object tryGetObjectField(Object target, String name) {
        try {
            return getObjectField(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void setObjectField(Object target, String name, Object value) throws Throwable {
        Field field = findField(target.getClass(), name);
        if (field != null) {
            field.set(target, value);
        }
    }

    public static int getIntField(Object target, String name) throws Throwable {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(target.getClass().getName() + "#" + name);
        }
        Object value = field.get(target);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static void setIntField(Object target, String name, int value) throws Throwable {
        Field field = findField(target.getClass(), name);
        if (field != null) {
            field.set(target, value);
        }
    }

    public static void setBooleanField(Object target, String name, boolean value) throws Throwable {
        Field field = findField(target.getClass(), name);
        if (field != null) {
            field.set(target, value);
        }
    }

    public static Object getStaticObjectField(Class<?> type, String name) throws Throwable {
        Field field = findField(type, name);
        return field == null ? null : field.get(null);
    }

    private static Field findField(Class<?> start, String name) {
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // continue through superclass chain
            }
        }
        return null;
    }

    // --------------------------------------------------------------- matching

    private static boolean isCompatible(Class<?>[] parameters, Object[] args) {
        int count = args == null ? 0 : args.length;
        if (parameters.length != count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (!isConvertible(parameters[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isConvertible(Class<?> parameter, Object value) {
        if (parameter.isPrimitive()) {
            return value != null && boxedType(parameter).isInstance(value);
        }
        return value == null || parameter.isInstance(value);
    }

    private static Class<?> boxedType(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == void.class) return Void.class;
        return type;
    }

    private static Object[] prepareArgs(Class<?>[] parameters, Object[] args) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] result = args;
        for (int i = 0; i < parameters.length; i++) {
            Object value = args[i];
            if (value != null && value.getClass() != parameters[i]) {
                if (result == args) {
                    result = args.clone();
                }
                result[i] = convertValue(parameters[i], value);
            }
        }
        return result;
    }

    private static Object convertValue(Class<?> parameter, Object value) {
        if (parameter.isPrimitive() && boxedType(parameter).isInstance(value)) {
            return value; // reflection performs the unbox automatically
        }
        return value;
    }

    private static boolean isMoreSpecific(Method candidate, Method current) {
        return isMoreSpecific(candidate.getParameterTypes(), current.getParameterTypes());
    }

    private static boolean isMoreSpecific(Class<?>[] candidate, Class<?>[] current) {
        boolean better = false;
        for (int i = 0; i < candidate.length; i++) {
            Class<?> from = candidate[i];
            Class<?> to = current[i];
            if (from == to) {
                continue;
            }
            // Prefer primitive parameters over boxed/reference alternatives.
            if (from.isPrimitive() && !to.isPrimitive()) {
                better = true;
                continue;
            }
            if (!from.isPrimitive() && to.isPrimitive()) {
                return false;
            }
            // Prefer concrete classes over interfaces when both accept the
            // argument (String over CharSequence, then Object last).
            if (from.isInterface() != to.isInterface()) {
                if (from.isInterface()) {
                    return false;
                }
                better = true;
                continue;
            }
            // Otherwise the candidate is more specific only when its parameter
            // is a strict subtype of the competing parameter.
            if (!to.isAssignableFrom(from)) {
                return false;
            }
            better = true;
        }
        return better;
    }

    /** Convenience helper used when replacing legacy bridge-style APIs. */
    public static boolean isAssignableFrom(Class<?> target, Object value) {
        return target.isInstance(value);
    }

    @SuppressWarnings("unused")
    private static void neverCalledForArrayApi() {
        Object unused = Array.class; // keep java.lang.reflect.Array import stable
    }
}
