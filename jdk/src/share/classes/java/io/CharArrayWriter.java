/*
 * Copyright (c) 1996, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.util.Arrays;

/**
 * This class implements a character buffer that can be used as an Writer.
 * The buffer automatically grows when data is written to the stream.  The data
 * can be retrieved using toCharArray() and toString().
 * <P>
 * Note: Invoking close() on this class has no effect, and methods
 * of this class can be called after the stream has closed
 * without generating an IOException.
 *
 * @author Herb Jellinek
 * @since 1.1
 */
// 字符数组输出流：将给定的字符写入到内部的字符数组缓冲区
public class CharArrayWriter extends Writer {

    /**
     * The buffer where data is stored.
     */
    protected char[] buf;   // 内部缓冲区，存储写入当前流的字符，会自动扩容

    /**
     * The number of chars in the buffer.
     */
    protected int count;    // 内部缓冲区中的字符数量



    /*▼ 构造器 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Creates a new CharArrayWriter.
     */
    public CharArrayWriter() {
        this(32);
    }

    /**
     * Creates a new CharArrayWriter with the specified initial size.
     *
     * @param initialSize an int specifying the initial buffer size.
     *
     * @throws IllegalArgumentException if initialSize is negative
     */
    public CharArrayWriter(int initialSize) {
        if(initialSize<0) {
            throw new IllegalArgumentException("Negative initial size: " + initialSize);
        }

        buf = new char[initialSize];
    }

    /*▲ 构造器 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 写 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Writes a character to the buffer.
     */
    // 将指定的字符写入到输出流
    public void write(int c) {
        synchronized(lock) {
            int newcount = count + 1;

            // 扩容
            if(newcount>buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
            }

            buf[count] = (char) c;

            count = newcount;
        }
    }

    /**
     * Writes characters to the buffer.
     *
     * @param c   the data to be written
     * @param off the start offset in the data
     * @param len the number of chars that are written
     *
     * @throws IndexOutOfBoundsException If {@code off} is negative, or {@code len} is negative,
     *                                   or {@code off + len} is negative or greater than the length
     *                                   of the given array
     */
    // 将字符数组cbuf中off处起的len个字符写入到输出流
    public void write(char[] c, int off, int len) {
        if((off<0) || (off>c.length) || (len<0) || ((off + len)>c.length) || ((off + len)<0)) {
            throw new IndexOutOfBoundsException();
        } else if(len == 0) {
            return;
        }

        synchronized(lock) {
            int newcount = count + len;

            // 扩容
            if(newcount>buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
            }

            System.arraycopy(c, off, buf, count, len);
            count = newcount;
        }
    }


    /**
     * Write a portion of a string to the buffer.
     *
     * @param str String to be written from
     * @param off Offset from which to start reading characters
     * @param len Number of characters to be written
     *
     * @throws IndexOutOfBoundsException If {@code off} is negative, or {@code len} is negative,
     *                                   or {@code off + len} is negative or greater than the length
     *                                   of the given string
     */
    // 将字符串str中off处起的len个字符写入到输出流
    public void write(String str, int off, int len) {
        synchronized(lock) {
            int newcount = count + len;

            // 扩容
            if(newcount>buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
            }

            // 将String内部的字节批量转换为char后存入buf
            str.getChars(off, off + len, buf, count);

            count = newcount;
        }
    }


    /**
     * Appends the specified character to this writer.
     *
     * <p> An invocation of this method of the form {@code out.append(c)}
     * behaves in exactly the same way as the invocation
     *
     * <pre>
     *     out.write(c) </pre>
     *
     * @param c The 16-bit character to append
     *
     * @return This writer
     *
     * @since 1.5
     */
    // 将指定的字符写入到输出流
    public CharArrayWriter append(char c) {
        write(c);
        return this;
    }

    /**
     * Appends the specified character sequence to this writer.
     *
     * <p> An invocation of this method of the form {@code out.append(csq)}
     * behaves in exactly the same way as the invocation
     *
     * <pre>
     *     out.write(csq.toString()) </pre>
     *
     * <p> Depending on the specification of {@code toString} for the
     * character sequence {@code csq}, the entire sequence may not be
     * appended. For instance, invoking the {@code toString} method of a
     * character buffer will return a subsequence whose content depends upon
     * the buffer's position and limit.
     *
     * @param csq The character sequence to append.  If {@code csq} is
     *            {@code null}, then the four characters {@code "null"} are
     *            appended to this writer.
     *
     * @return This writer
     *
     * @since 1.5
     */
    // 将字符序列csq的字符写入到输出流
    public CharArrayWriter append(CharSequence csq) {
        String s = String.valueOf(csq);
        write(s, 0, s.length());
        return this;
    }

    /**
     * Appends a subsequence of the specified character sequence to this writer.
     *
     * <p> An invocation of this method of the form
     * {@code out.append(csq, start, end)} when
     * {@code csq} is not {@code null}, behaves in
     * exactly the same way as the invocation
     *
     * <pre>
     *     out.write(csq.subSequence(start, end).toString()) </pre>
     *
     * @param csq   The character sequence from which a subsequence will be
     *              appended.  If {@code csq} is {@code null}, then characters
     *              will be appended as if {@code csq} contained the four
     *              characters {@code "null"}.
     * @param start The index of the first character in the subsequence
     * @param end   The index of the character following the last character in the
     *              subsequence
     *
     * @return This writer
     *
     * @throws IndexOutOfBoundsException If {@code start} or {@code end} are negative, {@code start}
     *                                   is greater than {@code end}, or {@code end} is greater than
     *                                   {@code csq.length()}
     * @since 1.5
     */
    // 将字符序列csq[start, end)范围的字符写入到输出流
    public CharArrayWriter append(CharSequence csq, int start, int end) {
        if(csq == null) {
            csq = "null";
        }

        return append(csq.subSequence(start, end));
    }


    /**
     * Writes the contents of the buffer to another character stream.
     *
     * @param out the output stream to write to
     *
     * @throws IOException If an I/O error occurs.
     */
    // 将内部缓冲区中的字符写入输出流out
    public void writeTo(Writer out) throws IOException {
        synchronized(lock) {
            out.write(buf, 0, count);
        }
    }

    /*▲ 写 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 杂项 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Flush the stream.
     */
    // 将内部缓冲区中的字符写入到输出流
    public void flush() {
    }

    /**
     * Close the stream.  This method does not release the buffer, since its
     * contents might still be required. Note: Invoking this method in this class
     * will have no effect.
     */
    // 关闭输出流
    public void close() {
    }


    /**
     * Returns the current size of the buffer.
     *
     * @return an int representing the current size of the buffer.
     */
    // 返回写入当前输出流的字符数
    public int size() {
        return count;
    }

    /**
     * Resets the buffer so that you can use it again without
     * throwing away the already allocated buffer.
     */
    // 重置(清空)当前输出流的字符缓冲区
    public void reset() {
        count = 0;
    }


    /**
     * Returns a copy of the input data.
     *
     * @return an array of chars copied from the input data.
     */
    // 返回当前输出流中的字符序列
    public char[] toCharArray() {
        synchronized(lock) {
            return Arrays.copyOf(buf, count);
        }
    }

    /*▲ 杂项 ████████████████████████████████████████████████████████████████████████████████┛ */



    /**
     * Converts input data to a string.
     *
     * @return the string.
     */
    // 将当前输出流中的字符以String形式返回
    public String toString() {
        synchronized(lock) {
            return new String(buf, 0, count);
        }
    }

}
