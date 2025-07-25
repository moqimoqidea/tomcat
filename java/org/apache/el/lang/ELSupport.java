/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.lang;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.LambdaExpression;

import org.apache.el.util.ExceptionUtils;
import org.apache.el.util.MessageFactory;


/**
 * A helper class that implements the EL Specification
 *
 * @author Jacob Hookom [jacob@hookom.net]
 */
public class ELSupport {

    private static final Long ZERO = Long.valueOf(0L);

    protected static final boolean COERCE_TO_ZERO = Boolean.getBoolean("org.apache.el.parser.COERCE_TO_ZERO");


    /**
     * Compare two objects, after coercing to the same type if appropriate.
     * <p>
     * If the objects are identical, or they are equal according to {@link #equals(ELContext, Object, Object)} then
     * return 0.
     * <p>
     * If either object is null, error
     * <p>
     * If either object is a BigDecimal, then coerce both to BigDecimal first. Similarly for Double(Float), BigInteger,
     * and Long(Integer, Char, Short, Byte).
     * <p>
     * If either object is TemporalAccessor, Clock, java.util.Date or java.sql.Timestamp, coerce both to Instant and
     * then compare.
     * <p>
     * If the first object is an instance of Comparable, return the result of comparing against the second object.
     * <p>
     * If the second object is an instance of Comparable, return -1 * the result of comparing against the first object.
     * <p>
     * Otherwise, error.
     *
     * @param ctx  the context in which this comparison is taking place
     * @param obj0 first object
     * @param obj1 second object
     *
     * @return -1, 0, or 1 if this object is less than, equal to, or greater than val.
     *
     * @throws ELException        if neither object is Comparable
     * @throws ClassCastException if the objects are not mutually comparable
     */
    public static int compare(final ELContext ctx, final Object obj0, final Object obj1) throws ELException {
        if (obj0 == obj1 || equals(ctx, obj0, obj1)) {
            return 0;
        }
        Objects.requireNonNull(obj0, MessageFactory.get("error.compare.null"));
        Objects.requireNonNull(obj1, MessageFactory.get("error.compare.null"));

        if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(ctx, obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(ctx, obj1, BigDecimal.class);
            return bd0.compareTo(bd1);
        }
        if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(ctx, obj0, Double.class);
            Double d1 = (Double) coerceToNumber(ctx, obj1, Double.class);
            return d0.compareTo(d1);
        }
        if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(ctx, obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(ctx, obj1, BigInteger.class);
            return bi0.compareTo(bi1);
        }
        if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(ctx, obj0, Long.class);
            Long l1 = (Long) coerceToNumber(ctx, obj1, Long.class);
            return l0.compareTo(l1);
        }
        if (isDateOp(obj0, obj1)) {
            Instant i0 = coerceToInstant(ctx, obj0);
            Instant i1 = coerceToInstant(ctx, obj1);
            return i0.compareTo(i1);
        }
        if (obj0 instanceof String || obj1 instanceof String) {
            return coerceToString(ctx, obj0).compareTo(coerceToString(ctx, obj1));
        }
        if (obj0 instanceof Comparable<?>) {
            @SuppressWarnings("unchecked") // checked above
            final Comparable<Object> comparable = (Comparable<Object>) obj0;
            return comparable.compareTo(obj1);
        }
        if (obj1 instanceof Comparable<?>) {
            @SuppressWarnings("unchecked") // checked above
            final Comparable<Object> comparable = (Comparable<Object>) obj1;
            return -comparable.compareTo(obj0);
        }
        throw new ELException(MessageFactory.get("error.compare", obj0, obj1));
    }

    /**
     * Compare two objects for equality, after coercing to the same type if appropriate.
     * <p>
     * If the objects are identical (including both null) return true.
     * <p>
     * If either object is null, return false.
     * <p>
     * If either object is Boolean, coerce both to Boolean and check equality.
     * <p>
     * Similarly for Enum, String, BigDecimal, Double(Float), Long(Integer, Short, Byte, Character)
     * <p>
     * Otherwise default to using Object.equals().
     *
     * @param ctx  the context in which this equality test is taking place
     * @param obj0 the first object
     * @param obj1 the second object
     *
     * @return true if the objects are equal
     *
     * @throws ELException if one of the coercion fails
     */
    public static boolean equals(final ELContext ctx, final Object obj0, final Object obj1) throws ELException {
        if (obj0 == obj1) {
            return true;
        } else if (obj0 == null || obj1 == null) {
            return false;
        } else if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(ctx, obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(ctx, obj1, BigDecimal.class);
            return bd0.equals(bd1);
        } else if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(ctx, obj0, Double.class);
            Double d1 = (Double) coerceToNumber(ctx, obj1, Double.class);
            return d0.equals(d1);
        } else if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(ctx, obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(ctx, obj1, BigInteger.class);
            return bi0.equals(bi1);
        } else if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(ctx, obj0, Long.class);
            Long l1 = (Long) coerceToNumber(ctx, obj1, Long.class);
            return l0.equals(l1);
        } else if (obj0 instanceof Boolean || obj1 instanceof Boolean) {
            return coerceToBoolean(ctx, obj0, false).equals(coerceToBoolean(ctx, obj1, false));
        } else if (obj0.getClass().isEnum()) {
            return obj0.equals(coerceToEnum(ctx, obj1, obj0.getClass()));
        } else if (obj1.getClass().isEnum()) {
            return obj1.equals(coerceToEnum(ctx, obj0, obj1.getClass()));
        } else if (isDateOp(obj0, obj1)) {
            Instant i0 = coerceToInstant(ctx, obj0);
            Instant i1 = coerceToInstant(ctx, obj1);
            return i0.equals(i1);
        } else if (obj0 instanceof String || obj1 instanceof String) {
            int lexCompare = coerceToString(ctx, obj0).compareTo(coerceToString(ctx, obj1));
            return lexCompare == 0;
        } else {
            return obj0.equals(obj1);
        }
    }

    /*
     * Going to have some casts /raw types somewhere so doing it here keeps them all in one place. There might
     * be a neater / better solution, but I couldn't find it.
     */
    @SuppressWarnings("unchecked")
    public static Enum<?> coerceToEnum(final ELContext ctx, final Object obj,
            @SuppressWarnings("rawtypes") Class type) {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, type);
                if (ctx.isPropertyResolved()) {
                    return (Enum<?>) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (obj == null || "".equals(obj)) {
            return null;
        }
        if (type.isAssignableFrom(obj.getClass())) {
            return (Enum<?>) obj;
        }

        if (!(obj instanceof String)) {
            throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
        }

        Enum<?> result;
        try {
            result = Enum.valueOf(type, (String) obj);
        } catch (IllegalArgumentException iae) {
            throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
        }
        return result;
    }

    /**
     * Convert an object to Boolean. Null and empty string are false.
     *
     * @param ctx       the context in which this conversion is taking place
     * @param obj       the object to convert
     * @param primitive is the target a primitive in which case coercion to null is not permitted
     *
     * @return the Boolean value of the object
     *
     * @throws ELException if object is not Boolean or String
     */
    public static Boolean coerceToBoolean(final ELContext ctx, final Object obj, boolean primitive)
            throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Boolean result = ctx.getELResolver().convertToType(ctx, obj, Boolean.class);
                if (ctx.isPropertyResolved()) {
                    return result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (!COERCE_TO_ZERO && !primitive) {
            if (obj == null) {
                return null;
            }
        }

        if (obj == null || "".equals(obj)) {
            return Boolean.FALSE;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.valueOf((String) obj);
        }

        throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), Boolean.class));
    }

    private static Character coerceToCharacter(final ELContext ctx, final Object obj) throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Character result = ctx.getELResolver().convertToType(ctx, obj, Character.class);
                if (ctx.isPropertyResolved()) {
                    return result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (obj == null || "".equals(obj)) {
            return Character.valueOf((char) 0);
        }
        if (obj instanceof String) {
            return Character.valueOf(((String) obj).charAt(0));
        }
        if (ELArithmetic.isNumber(obj)) {
            return Character.valueOf((char) ((Number) obj).shortValue());
        }
        Class<?> objType = obj.getClass();
        if (obj instanceof Character) {
            return (Character) obj;
        }

        throw new ELException(MessageFactory.get("error.convert", obj, objType, Character.class));
    }

    protected static Number coerceToNumber(final Number number, final Class<?> type) throws ELException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            return Long.valueOf(number.longValue());
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            return Double.valueOf(number.doubleValue());
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            return Integer.valueOf(number.intValue());
        }
        if (BigInteger.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return ((BigDecimal) number).toBigInteger();
            }
            if (number instanceof BigInteger) {
                return number;
            }
            return BigInteger.valueOf(number.longValue());
        }
        if (BigDecimal.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return number;
            }
            if (number instanceof BigInteger) {
                return new BigDecimal((BigInteger) number);
            }
            return new BigDecimal(number.doubleValue());
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            return Byte.valueOf(number.byteValue());
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            return Short.valueOf(number.shortValue());
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            return Float.valueOf(number.floatValue());
        }
        if (Number.class.equals(type)) {
            return number;
        }

        throw new ELException(MessageFactory.get("error.convert", number, number.getClass(), type));
    }

    public static Number coerceToNumber(final ELContext ctx, final Object obj, final Class<?> type)
            throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, type);
                if (ctx.isPropertyResolved()) {
                    return (Number) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (!COERCE_TO_ZERO) {
            if (obj == null && !type.isPrimitive()) {
                return null;
            }
        }

        if (obj == null || "".equals(obj)) {
            return coerceToNumber(ZERO, type);
        }
        if (obj instanceof String) {
            return coerceToNumber((String) obj, type);
        }
        if (ELArithmetic.isNumber(obj)) {
            return coerceToNumber((Number) obj, type);
        }

        if (obj instanceof Character) {
            return coerceToNumber(Short.valueOf((short) ((Character) obj).charValue()), type);
        }

        throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
    }

    protected static Number coerceToNumber(final String val, final Class<?> type) throws ELException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            try {
                return Long.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            try {
                return Integer.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            try {
                return Double.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (BigInteger.class.equals(type)) {
            try {
                return new BigInteger(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (BigDecimal.class.equals(type)) {
            try {
                return new BigDecimal(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            try {
                return Byte.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            try {
                return Short.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            try {
                return Float.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
            }
        }

        throw new ELException(MessageFactory.get("error.convert", val, String.class, type));
    }

    /**
     * Coerce an object to a string.
     *
     * @param ctx the context in which this conversion is taking place
     * @param obj the object to convert
     *
     * @return the String value of the object
     */
    public static String coerceToString(final ELContext ctx, final Object obj) {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                String result = ctx.getELResolver().convertToType(ctx, obj, String.class);
                if (ctx.isPropertyResolved()) {
                    return result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        return switch (obj) {
            case null -> "";
            case String s -> s;
            case Enum<?> anEnum -> anEnum.name();
            default -> {
                try {
                    yield obj.toString();
                } catch (ELException e) {
                    // Unlikely but you never know
                    throw e;
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    throw new ELException(t);
                }
            }
        };
    }


    private static Instant coerceToInstant(final ELContext ctx, final Object obj) {
        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Instant result = ctx.getELResolver().convertToType(ctx, obj, Instant.class);
                if (ctx.isPropertyResolved()) {
                    return result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        return switch (obj) {
            case null -> null;
            case TemporalAccessor t -> Instant.from(t);
            case Clock c -> c.instant();
            case Date d -> d.toInstant();
            case String s -> Instant.parse(s);
            default -> {
                throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass().getName(), Instant.class));
            }
        };
    }

    public static <T> T coerceToType(final ELContext ctx, final Object obj, final Class<T> type)
            throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                T result = ctx.getELResolver().convertToType(ctx, obj, type);
                if (ctx.isPropertyResolved()) {
                    return result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (type == null || Object.class.equals(type) || (obj != null && type.isAssignableFrom(obj.getClass()))) {
            @SuppressWarnings("unchecked")
            T result = (T) obj;
            return result;
        }

        if (!COERCE_TO_ZERO) {
            if (obj == null && !type.isPrimitive() && !String.class.isAssignableFrom(type)) {
                return null;
            }
        }

        if (String.class.equals(type)) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToString(ctx, obj);
            return result;
        }
        if (ELArithmetic.isNumberType(type)) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToNumber(ctx, obj, type);
            return result;
        }
        if (Character.class.equals(type) || Character.TYPE == type) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToCharacter(ctx, obj);
            return result;
        }
        if (Boolean.class.equals(type) || Boolean.TYPE == type) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToBoolean(ctx, obj, Boolean.TYPE == type);
            return result;
        }
        if (type.isEnum()) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToEnum(ctx, obj, type);
            return result;
        }

        // new to spec
        if (obj == null) {
            return null;
        }

        if (Instant.class.equals(type)) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToInstant(ctx, obj);
            return result;
        }
        if (Date.class.equals(type)) {
            @SuppressWarnings("unchecked")
            T result = (T) Date.from(coerceToInstant(ctx, obj));
            return result;
        }

        if (obj instanceof String str) {
            PropertyEditor editor = PropertyEditorManager.findEditor(type);
            if (editor == null) {
                if (str.isEmpty()) {
                    return null;
                }
                throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
            } else {
                try {
                    editor.setAsText(str);
                    @SuppressWarnings("unchecked")
                    T result = (T) editor.getValue();
                    return result;
                } catch (RuntimeException e) {
                    if (str.isEmpty()) {
                        return null;
                    }
                    throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), type), e);
                }
            }
        }

        // Handle special case because the syntax for the empty set is the same
        // for an empty map. The parser will always parse {} as an empty set.
        if (obj instanceof Set && type == Map.class && ((Set<?>) obj).isEmpty()) {
            @SuppressWarnings("unchecked")
            T result = (T) Collections.EMPTY_MAP;
            return result;
        }

        // Handle arrays
        if (type.isArray() && obj.getClass().isArray()) {
            @SuppressWarnings("unchecked")
            T result = (T) coerceToArray(ctx, obj, type);
            return result;
        }

        if (obj instanceof LambdaExpression && isFunctionalInterface(type)) {
            return coerceToFunctionalInterface(ctx, (LambdaExpression) obj, type);
        }

        throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), type));
    }

    private static Object coerceToArray(final ELContext ctx, final Object obj, final Class<?> type) {
        // Note: Nested arrays will result in nested calls to this method.

        // Note: Calling method has checked the obj is an array.

        int size = Array.getLength(obj);
        // Cast the input object to an array (calling method has checked it is
        // an array)
        // Get the target type for the array elements
        Class<?> componentType = type.getComponentType();
        // Create a new array of the correct type
        Object result = Array.newInstance(componentType, size);
        // Coerce each element in turn.
        for (int i = 0; i < size; i++) {
            Array.set(result, i, coerceToType(ctx, Array.get(obj, i), componentType));
        }

        return result;
    }


    private static <T> T coerceToFunctionalInterface(final ELContext ctx, final LambdaExpression lambdaExpression,
            final Class<T> type) {
        Supplier<T> proxy = () -> {
            // Create a dynamic proxy for the functional interface
            @SuppressWarnings("unchecked")
            T result = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[] { type },
                    (Object obj, Method method, Object[] args) -> {
                        // Functional interfaces have a single, abstract method
                        if (!Modifier.isAbstract(method.getModifiers())) {
                            throw new ELException(MessageFactory.get("elSupport.coerce.nonAbstract", type, method));
                        }
                        if (ctx == null) {
                            return lambdaExpression.invoke(args);
                        } else {
                            return lambdaExpression.invoke(ctx, args);
                        }
                    });
            return result;
        };
        return proxy.get();
    }


    public static boolean isBigDecimalOp(final Object obj0, final Object obj1) {
        return obj0 instanceof BigDecimal || obj1 instanceof BigDecimal;
    }

    public static boolean isBigIntegerOp(final Object obj0, final Object obj1) {
        return obj0 instanceof BigInteger || obj1 instanceof BigInteger;
    }

    public static boolean isDoubleOp(final Object obj0, final Object obj1) {
        return obj0 instanceof Double || obj1 instanceof Double || obj0 instanceof Float || obj1 instanceof Float;
    }

    public static boolean isLongOp(final Object obj0, final Object obj1) {
        return obj0 instanceof Long || obj1 instanceof Long || obj0 instanceof Integer || obj1 instanceof Integer ||
                obj0 instanceof Character || obj1 instanceof Character || obj0 instanceof Short ||
                obj1 instanceof Short || obj0 instanceof Byte || obj1 instanceof Byte;
    }

    public static boolean isDateOp(final Object obj0, Object obj1) {
        return obj0 instanceof TemporalAccessor || obj1 instanceof TemporalAccessor || obj0 instanceof Clock ||
                obj1 instanceof Clock ||obj0 instanceof Date || obj1 instanceof Date ||obj0 instanceof Timestamp ||
                obj1 instanceof Timestamp;
    }

    public static boolean isStringFloat(final String str) {
        int len = str.length();
        if (len > 1) {
            for (int i = 0; i < len; i++) {
                switch (str.charAt(i)) {
                    case 'E':
                        return true;
                    case 'e':
                        return true;
                    case '.':
                        return true;
                }
            }
        }
        return false;
    }


    /*
     * Copied to jakarta.el.ELContext - keep in sync
     */
    static boolean isFunctionalInterface(Class<?> type) {

        if (!type.isInterface()) {
            return false;
        }

        boolean foundAbstractMethod = false;
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                // Abstract methods that override one of the public methods
                // of Object don't count
                if (overridesObjectMethod(method)) {
                    continue;
                }
                if (foundAbstractMethod) {
                    // Found more than one
                    return false;
                } else {
                    foundAbstractMethod = true;
                }
            }
        }
        return foundAbstractMethod;
    }


    /*
     * Copied to jakarta.el.ELContext - keep in sync
     */
    private static boolean overridesObjectMethod(Method method) {
        // There are three methods that can be overridden
        switch (method.getName()) {
            case "equals" -> {
                if (method.getReturnType().equals(boolean.class)) {
                    if (method.getParameterCount() == 1) {
                        return method.getParameterTypes()[0].equals(Object.class);
                    }
                }
            }
            case "hashCode" -> {
                if (method.getReturnType().equals(int.class)) {
                    return method.getParameterCount() == 0;
                }
            }
            case "toString" -> {
                if (method.getReturnType().equals(String.class)) {
                    return method.getParameterCount() == 0;
                }
            }
        }

        return false;
    }


    private ELSupport() {
        // Utility class - hide default constructor;
    }
}
