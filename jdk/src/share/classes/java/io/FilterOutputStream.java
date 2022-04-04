/*
 * Copyright (c) 1994, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class is the superclass of all classes that filter output
 * streams. These streams sit on top of an already existing output
 * stream (the <i>underlying</i> output stream) which it uses as its
 * basic sink of data, but possibly transforming the data along the
 * way or providing additional functionality.
 * <p>
 * The class <code>FilterOutputStream</code> itself simply overrides
 * all methods of <code>OutputStream</code> with versions that pass
 * all requests to the underlying output stream. Subclasses of
 * <code>FilterOutputStream</code> may further override some of these
 * methods as well as provide additional methods and fields.
 *
 * @author Jonathan Payne
 * @since 1.0
 */
// 对输出流的简单包装，由子类实现具体的包装行为
public class FilterOutputStream extends OutputStream {

    /**
     * Object used to prevent a race on the 'closed' instance variable.
     */
    private final Object closeLock = new Object();

    /**
     * The underlying output stream to be filtered.
     */
    protected OutputStream out;         // 包装的输出流

    /**
     * Whether the stream is closed; implicitly initialized to false.
     */
    private volatile boolean closed;    // 输入流是否已关闭



    /*▼ 构造器 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Creates an output stream filter built on top of the specified
     * underlying output stream.
     *
     * @param out the underlying output stream to be assigned to
     *            the field {@code this.out} for later use, or
     *            <code>null</code> if this instance is to be
     *            created without an underlying stream.
     */
    public FilterOutputStream(OutputStream out) {
        this.out = out;
    }

    /*▲ 构造器 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 写 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Writes the specified <code>byte</code> to this output stream.
     * <p>
     * The <code>write</code> method of <code>FilterOutputStream</code>
     * calls the <code>write</code> method of its underlying output stream,
     * that is, it performs {@code out.write(b)}.
     * <p>
     * Implements the abstract {@code write} method of {@code OutputStream}.
     *
     * @param b the <code>byte</code>.
     *
     * @throws IOException if an I/O error occurs.
     */
    // 将指定的字节写入到输出流
    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    /**
     * Writes <code>b.length</code> bytes to this output stream.
     * <p>
     * The <code>write</code> method of <code>FilterOutputStream</code>
     * calls its <code>write</code> method of three arguments with the
     * arguments <code>b</code>, <code>0</code>, and
     * <code>b.length</code>.
     * <p>
     * Note that this method does not call the one-argument
     * <code>write</code> method of its underlying output stream with
     * the single argument <code>b</code>.
     *
     * @param b the data to be written.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#write(byte[], int, int)
     */
    // 将字节数组b的内容写入到输出流
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes <code>len</code> bytes from the specified
     * <code>byte</code> array starting at offset <code>off</code> to
     * this output stream.
     * <p>
     * The <code>write</code> method of <code>FilterOutputStream</code>
     * calls the <code>write</code> method of one argument on each
     * <code>byte</code> to output.
     * <p>
     * Note that this method does not call the <code>write</code> method
     * of its underlying output stream with the same arguments. Subclasses
     * of <code>FilterOutputStream</code> should provide a more efficient
     * implementation of this method.
     *
     * @param b   the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#write(int)
     */
    // 将字节数组b中off处起的len个字节写入到输出流
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if((off | len | (b.length - (len + off)) | (off + len))<0)
            throw new IndexOutOfBoundsException();

        for(int i = 0; i<len; i++) {
            write(b[off + i]);
        }
    }

    /*▲ 写 ████████████████████████████████████████████████████████████████████████████████┛ */



    /*▼ 杂项 ████████████████████████████████████████████████████████████████████████████████┓ */

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out to the stream.
     * <p>
     * The <code>flush</code> method of <code>FilterOutputStream</code>
     * calls the <code>flush</code> method of its underlying output stream.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#out
     */
    // 将缓冲区中的字节刷新到输出流
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with the stream.
     * <p>
     * When not already closed, the {@code close} method of {@code
     * FilterOutputStream} calls its {@code flush} method, and then
     * calls the {@code close} method of its underlying output stream.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#flush()
     * @see java.io.FilterOutputStream#out
     */
    // 关闭输出流
    @Override
    public void close() throws IOException {
        if(closed) {
            return;
        }

        synchronized(closeLock) {
            if(closed) {
                return;
            }
            closed = true;
        }

        Throwable flushException = null;

        try {
            flush();
        } catch(Throwable e) {
            flushException = e;
            throw e;
        } finally {
            if(flushException == null) {
                out.close();
            } else {
                try {
                    out.close();
                } catch(Throwable closeException) {
                    // evaluate possible precedence of flushException over closeException
                    if((flushException instanceof ThreadDeath) && !(closeException instanceof ThreadDeath)) {
                        flushException.addSuppressed(closeException);
                        throw (ThreadDeath) flushException;
                    }

                    if(flushException != closeException) {
                        closeException.addSuppressed(flushException);
                    }

                    throw closeException;
                }
            }
        }
    }

    /*▲ 杂项 ████████████████████████████████████████████████████████████████████████████████┛ */

}
