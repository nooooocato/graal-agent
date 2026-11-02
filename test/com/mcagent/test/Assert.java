package com.mcagent.test;

import java.util.Arrays;
import java.util.Objects;

public class Assert {
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected [" + expected + "] but got [" + actual + "]");
        }
    }

    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("Expected array " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    public static void assertNotNull(Object obj) {
        if (obj == null) {
            throw new AssertionError("Expected non-null object but was null");
        }
    }

    public static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    public static void assertNull(Object obj) {
        if (obj != null) {
            throw new AssertionError("Expected null object but was: " + obj);
        }
    }

    public static void assertNull(Object obj, String message) {
        if (obj != null) {
            throw new AssertionError("Assertion failed: " + message + " (was: " + obj + ")");
        }
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return expectedType.cast(t);
            }
            throw new AssertionError("Expected exception " + expectedType.getName() + " but caught " + t.getClass().getName(), t);
        }
        throw new AssertionError("Expected exception " + expectedType.getName() + " was not thrown");
    }

    @FunctionalInterface
    public interface Executable {
        void execute() throws Throwable;
    }
}
