package com.fuck.bili.ads.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ReflectTest {

    public static class Overloads {
        public static String id(String value) {
            return "string:" + value;
        }

        public static String id(Object value) {
            return "object:" + value;
        }

        public static String id(CharSequence value) {
            return "charsequence:" + value;
        }

        public static int sum(int a, int b) {
            return a + b;
        }
    }

    static class Parent {
        protected String hidden() {
            return "parent";
        }
    }

    static class Child extends Parent {
        @SuppressWarnings("unused")
        private int unusedField;
    }

    public interface Greeter {
        default String greet() {
            return "hi";
        }
    }

    @Test
    public void interfaceDefaultMethodIsFound() throws Throwable {
        Object result = Reflect.callMethod(new Greeter() { }, "greet");
        assertEquals("hi", result);
    }

    @Test
    public void findClassExistsAndMissing() throws Exception {
        assertEquals(String.class, Reflect.findClass("java.lang.String", ClassLoader.getSystemClassLoader()));
        assertNull(Reflect.findClassIfExists("not.a.RealClass", ClassLoader.getSystemClassLoader()));
    }

    @Test
    public void nullArgumentMatchesReferenceParameters() throws Throwable {
        Object result = Reflect.callStaticMethod(Overloads.class, "id", new Object[]{null});
        // String is more specific than Object/CharSequence when the argument is null.
        assertEquals("string:null", result);
    }

    @Test
    public void boxAndUnboxPrimitiveParameters() throws Throwable {
        assertEquals(3, Reflect.callStaticMethod(Overloads.class, "sum", 1, 2));
    }

    @Test
    public void superclassMethodIsFound() throws Throwable {
        assertEquals("parent", Reflect.callMethod(new Child(), "hidden"));
    }

    @Test
    public void mostSpecificOverloadWins() throws Throwable {
        Object value = Reflect.callStaticMethod(Overloads.class, "id", "x");
        assertEquals("string:x", value);
        Object generic = Reflect.callStaticMethod(Overloads.class, "id", new Object());
        assertTrue(String.valueOf(generic).startsWith("object:"));
    }

    @Test
    public void fieldLookupTraversesSuperclass() throws Throwable {
        Child child = new Child();
        Reflect.setIntField(child, "unusedField", 8);
        assertEquals(8, child.unusedField);
    }

    public static class Throwing {
        public Throwing(String message) {
            throw new IllegalStateException(message);
        }
    }

    @Test
    public void constructorMatchingAndInvocationTargetException() {
        try {
            Reflect.newInstance(Throwing.class, "boom");
            fail("expected the constructor's own exception");
        } catch (Throwable throwable) {
            assertTrue(String.valueOf(throwable), throwable instanceof IllegalStateException);
            assertEquals("boom", throwable.getMessage());
        }
    }

    @Test
    public void methodLookupHelper() {
        Method method = Reflect.findMethod(Overloads.class, "id", new Object[]{"x"}, true);
        assertNotNull(method);
        assertEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test
    public void instanceMethodIsRejectedForStaticLookup() {
        List<Method> unused = new ArrayList<>();
        assertNull(Reflect.findMethod(String.class, "length", new Object[0], true));
    }
}
