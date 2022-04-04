/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;

import java.io.Serializable;
import jdk.internal.HotSpotIntrinsicCandidate;

/**
 * The Boolean class wraps a value of the primitive type
 * {@code boolean} in an object. An object of type
 * {@code Boolean} contains a single field whose type is
 * {@code boolean}.
 * <p>
 * In addition, this class provides many methods for
 * converting a {@code boolean} to a {@code String} and a
 * {@code String} to a {@code boolean}, as well as other
 * constants and methods useful when dealing with a
 * {@code boolean}.
 *
 * @author Arthur van Hoff
 * @since 1.0
 */
// boolean的包装类
public final class Boolean implements Serializable, Comparable<Boolean> {

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = -3665804199014368530L;

    /**
     * The Class object representing the primitive type boolean.
     *
     * @since 1.1
     */
    // 相当于boolean.class
    @SuppressWarnings("unchecked")
    public static final Class<Boolean> TYPE = (Class<Boolean>) Class.getPrimitiveClass("boolean");

    /**
     * The {@code Boolean} object corresponding to the primitive
     * value {@code true}.
     */
    public static final Boolean TRUE = new Boolean(true);

    /**
     * The {@code Boolean} object corresponding to the primitive
     * value {@code false}.
     */
    public static final Boolean FALSE = new Boolean(false);

    /**
     * The value of the Boolean.
     *
     * @serial
     */
    private final boolean value; // 当前类包装的值



    /*▼ 构造方法 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Allocates a {@code Boolean} object representing the
     * {@code value} argument.
     *
     * @param value the value of the {@code Boolean}.
     *
     * @deprecated It is rarely appropriate to use this constructor. The static factory
     * {@link #valueOf(boolean)} is generally a better choice, as it is
     * likely to yield significantly better space and time performance.
     * Also consider using the final fields {@link #TRUE} and {@link #FALSE}
     * if possible.
     */
    @Deprecated(since = "9")
    public Boolean(boolean value) {
        this.value = value;
    }

    /**
     * Allocates a {@code Boolean} object representing the value
     * {@code true} if the string argument is not {@code null}
     * and is equal, ignoring case, to the string {@code "true"}.
     * Otherwise, allocates a {@code Boolean} object representing the
     * value {@code false}.
     *
     * @param s the string to be converted to a {@code Boolean}.
     *
     * @deprecated It is rarely appropriate to use this constructor.
     * Use {@link #parseBoolean(String)} to convert a string to a
     * {@code boolean} primitive, or use {@link #valueOf(String)}
     * to convert a string to a {@code Boolean} object.
     */
    @Deprecated(since = "9")
    public Boolean(String s) {
        this(parseBoolean(s));
    }

    /*▲ 构造方法 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ [装箱] ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Returns a {@code Boolean} instance representing the specified
     * {@code boolean} value.  If the specified {@code boolean} value
     * is {@code true}, this method returns {@code Boolean.TRUE};
     * if it is {@code false}, this method returns {@code Boolean.FALSE}.
     * If a new {@code Boolean} instance is not required, this method
     * should generally be used in preference to the constructor
     * {@link #Boolean(boolean)}, as this method is likely to yield
     * significantly better space and time performance.
     *
     * @param b a boolean value.
     *
     * @return a {@code Boolean} instance representing {@code b}.
     *
     * @since 1.4
     */
    // boolean-->Boolean 默认的装箱行为
    @HotSpotIntrinsicCandidate
    public static Boolean valueOf(boolean b) {
        return (b ? TRUE : FALSE);
    }

    /**
     * Returns a {@code Boolean} with a value represented by the
     * specified string.  The {@code Boolean} returned represents a
     * true value if the string argument is not {@code null}
     * and is equal, ignoring case, to the string {@code "true"}.
     * Otherwise, a false value is returned, including for a null
     * argument.
     *
     * @param s a string.
     *
     * @return the {@code Boolean} value represented by the string.
     */
    // 将字符串s解析为boolean值，随后再装箱
    public static Boolean valueOf(String s) {
        return parseBoolean(s) ? TRUE : FALSE;
    }

    /*▲ [装箱] ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ [拆箱] ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Returns the value of this {@code Boolean} object as a boolean
     * primitive.
     *
     * @return the primitive {@code boolean} value of this object.
     */
    // Boolean-->boolean 默认的拆箱行为
    @HotSpotIntrinsicCandidate
    public boolean booleanValue() {
        return value;
    }

    /*▲ [拆箱] ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 从属性中解析值 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Returns {@code true} if and only if the system property named
     * by the argument exists and is equal to, ignoring case, the
     * string {@code "true"}.
     * A system property is accessible through {@code getProperty}, a
     * method defined by the {@code System} class.  <p> If there is no
     * property with the specified name, or if the specified name is
     * empty or null, then {@code false} is returned.
     *
     * @param name the system property name.
     *
     * @return the {@code boolean} value of the system property.
     *
     * @throws SecurityException for the same reasons as
     *                           {@link System#getProperty(String) System.getProperty}
     * @see java.lang.System#getProperty(java.lang.String)
     * @see java.lang.System#getProperty(java.lang.String, java.lang.String)
     */
    /*
     * 从系统属性中获取值，其中，nm为某个系统属性
     *
     * 比如：
     * System.setProperty("isVIP", "true");
     * Boolean boo = Boolean.getBoolean("isVIP");
     * 如果属性isVIP存在，返回设置的值。否则，返回false。
     */
    public static boolean getBoolean(String name) {
        boolean result = false;
        try {
            result = parseBoolean(System.getProperty(name));
        } catch(IllegalArgumentException | NullPointerException e) {
        }
        return result;
    }

    /*▲ 从属性中解析值 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 逆字符串化 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Parses the string argument as a boolean.  The {@code boolean}
     * returned represents the value {@code true} if the string argument
     * is not {@code null} and is equal, ignoring case, to the string
     * {@code "true"}.
     * Otherwise, a false value is returned, including for a null
     * argument.<p>
     * Example: {@code Boolean.parseBoolean("True")} returns {@code true}.<br>
     * Example: {@code Boolean.parseBoolean("yes")} returns {@code false}.
     *
     * @param s the {@code String} containing the boolean
     *          representation to be parsed
     *
     * @return the boolean represented by the string argument
     *
     * @since 1.5
     */
    // 将字符串s解析为boolean值
    public static boolean parseBoolean(String s) {
        return "true".equalsIgnoreCase(s);
    }

    /*▲ 逆字符串化 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 字符串化 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Returns a {@code String} object representing this Boolean's
     * value.  If this object represents the value {@code true},
     * a string equal to {@code "true"} is returned. Otherwise, a
     * string equal to {@code "false"} is returned.
     *
     * @return a string representation of this object.
     */
    public String toString() {
        return value ? "true" : "false";
    }

    /**
     * Returns a {@code String} object representing the specified
     * boolean.  If the specified boolean is {@code true}, then
     * the string {@code "true"} will be returned, otherwise the
     * string {@code "false"} will be returned.
     *
     * @param b the boolean to be converted
     *
     * @return the string representation of the specified {@code boolean}
     *
     * @since 1.4
     */
    public static String toString(boolean b) {
        return b ? "true" : "false";
    }

    /*▲ 字符串化 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 比较 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Compares two {@code boolean} values.
     * The value returned is identical to what would be returned by:
     * <pre>
     *    Boolean.valueOf(x).compareTo(Boolean.valueOf(y))
     * </pre>
     *
     * @param x the first {@code boolean} to compare
     * @param y the second {@code boolean} to compare
     *
     * @return the value {@code 0} if {@code x == y};
     * a value less than {@code 0} if {@code !x && y}; and
     * a value greater than {@code 0} if {@code x && !y}
     *
     * @since 1.7
     */
    // 比较两个boolean（可以把true看做1，false看做0，然后再比较）
    public static int compare(boolean x, boolean y) {
        return (x == y) ? 0 : (x ? 1 : -1);
    }

    /**
     * Compares this {@code Boolean} instance with another.
     *
     * @param b the {@code Boolean} instance to be compared
     *
     * @return zero if this object represents the same boolean value as the
     * argument; a positive value if this object represents true
     * and the argument represents false; and a negative value if
     * this object represents false and the argument represents true
     *
     * @throws NullPointerException if the argument is {@code null}
     * @see Comparable
     * @since 1.5
     */
    // 比较两个Boolean（可以把true看做1，false看做0，然后再比较）
    public int compareTo(Boolean b) {
        return compare(this.value, b.value);
    }

    /*▲ 比较 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 简单运算 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Returns the result of applying the logical AND operator to the
     * specified {@code boolean} operands.
     *
     * @param a the first operand
     * @param b the second operand
     *
     * @return the logical AND of {@code a} and {@code b}
     *
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    // 逻辑与，一假为假
    public static boolean logicalAnd(boolean a, boolean b) {
        return a && b;
    }

    /**
     * Returns the result of applying the logical OR operator to the
     * specified {@code boolean} operands.
     *
     * @param a the first operand
     * @param b the second operand
     *
     * @return the logical OR of {@code a} and {@code b}
     *
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    // 逻辑或，一真为真
    public static boolean logicalOr(boolean a, boolean b) {
        return a || b;
    }

    /**
     * Returns the result of applying the logical XOR operator to the
     * specified {@code boolean} operands.
     *
     * @param a the first operand
     * @param b the second operand
     *
     * @return the logical XOR of {@code a} and {@code b}
     *
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    // 逻辑异或，相同为假，不同为真
    public static boolean logicalXor(boolean a, boolean b) {
        return a ^ b;
    }

    /*▲ 简单运算 ████████████████████████████████████████████████████████████████████████████████┛ */



    /**
     * Returns a hash code for this {@code Boolean} object.
     *
     * @return the integer {@code 1231} if this object represents
     * {@code true}; returns the integer {@code 1237} if this
     * object represents {@code false}.
     */
    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }

    /**
     * Returns a hash code for a {@code boolean} value; compatible with
     * {@code Boolean.hashCode()}.
     *
     * @param value the value to hash
     *
     * @return a hash code value for a {@code boolean} value.
     *
     * @since 1.8
     */
    public static int hashCode(boolean value) {
        return value ? 1231 : 1237;
    }

    /**
     * Returns {@code true} if and only if the argument is not
     * {@code null} and is a {@code Boolean} object that
     * represents the same {@code boolean} value as this object.
     *
     * @param obj the object to compare with.
     *
     * @return {@code true} if the Boolean objects represent the
     * same value; {@code false} otherwise.
     */
    public boolean equals(Object obj) {
        if(obj instanceof Boolean) {
            return value == ((Boolean) obj).booleanValue();
        }
        return false;
    }
}
