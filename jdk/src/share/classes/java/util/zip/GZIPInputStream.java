/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

import java.io.SequenceInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;

/**
 * This class implements a stream filter for reading compressed data in
 * the GZIP file format.
 *
 * @see         InflaterInputStream
 * @author      David Connelly
 *
 */
// gzip输入流：读取gzip文件，将其解压为原始数据后填充到指定的内存(适用于对单个文件的解压)
public
class GZIPInputStream extends InflaterInputStream {
    /**
     * CRC-32 for uncompressed data.
     */
    // 数据校验
    protected CRC32 crc = new CRC32();

    /**
     * Indicates end of input stream.
     */
    // 是否已达输入流末尾
    protected boolean eos;

    // 输入流是否已关闭
    private boolean closed = false;

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Creates a new input stream with the specified buffer size.
     * @param in the input stream
     * @param size the input buffer size
     *
     * @exception ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @exception IOException if an I/O error has occurred
     * @exception IllegalArgumentException if {@code size <= 0}
     */
    // 用指定的源头输入流和缓冲区容量构造gzip输入流
    public GZIPInputStream(InputStream in, int size) throws IOException {
        super(in, new Inflater(true), size);
        // 使用了具有默认解压级别的压缩器
        usesDefaultInflater = true;
        // 解压前：读取gzip文件头信息
        readHeader(in);
    }

    /**
     * Creates a new input stream with a default buffer size.
     * @param in the input stream
     *
     * @exception ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @exception IOException if an I/O error has occurred
     */
    // 用指定的源头输入流构造gzip输入流
    public GZIPInputStream(InputStream in) throws IOException {
        this(in, 512);
    }

    /**
     * Reads uncompressed data into an array of bytes. If <code>len</code> is not
     * zero, the method will block until some input can be decompressed; otherwise,
     * no bytes are read and <code>0</code> is returned.
     * @param buf the buffer into which the data is read
     * @param off the start offset in the destination array <code>b</code>
     * @param len the maximum number of bytes read
     * @return  the actual number of bytes read, or -1 if the end of the
     *          compressed input stream is reached
     *
     * @exception  NullPointerException If <code>buf</code> is <code>null</code>.
     * @exception  IndexOutOfBoundsException If <code>off</code> is negative,
     * <code>len</code> is negative, or <code>len</code> is greater than
     * <code>buf.length - off</code>
     * @exception ZipException if the compressed input data is corrupt.
     * @exception IOException if an I/O error has occurred.
     *
     */
    /*
     * 从gzip输入流中读取(解压)出len个解压后的字节，并将其存入b的off处
     * 返回本次成功解压出的字节数(只统计实际的文件内容)
     *
     * 注：如果输入流中包含多个gzip文件，则会递归读取
     */
    public int read(byte[] buf, int off, int len) throws IOException {
        ensureOpen();
        // 如果已经读到文件尾了，则直接返回
        if (eos) {
            return -1;
        }
        // 从(解压)输入流中读取(解压)出len个解压后的字节，并将其存入b的off处
        int n = super.read(buf, off, len);
        // 如果解压完成
        if (n == -1) {
            // 解压后：读取文件尾信息
            if (readTrailer())
                // 标记输入流已经读到了尾部
                eos = true;
            else  // 如果解压未完成(后面仍然有链接的其他gzip文件块)
                // 递归读取后续的gzip文件块
                return this.read(buf, off, len);
        } else {
            // 用字节数组b中off处起的len个字节更新当前校验和
            crc.update(buf, off, n);
        }
        return n;
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     * @exception IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (!closed) {
            super.close();
            eos = true;
            closed = true;
        }
    }

    /**
     * GZIP header magic number.
     */
    // gzip文件的魔数
    public final static int GZIP_MAGIC = 0x8b1f;

    /*
     * File header flags.
     */
    private final static int FTEXT      = 1;    // Extra text
    private final static int FHCRC      = 2;    // Header CRC
    private final static int FEXTRA     = 4;    // Extra field
    private final static int FNAME      = 8;    // File name
    private final static int FCOMMENT   = 16;   // File comment

    /*
     * Reads GZIP member header and returns the total byte number
     * of this member header.
     */
    // 解压前：读取文件头信息(以及其他附加信息)
    private int readHeader(InputStream this_in) throws IOException {
        // 记录当前校验和
        CheckedInputStream in = new CheckedInputStream(this_in, crc);
        // 重置当前校验和
        crc.reset();
        // Check header magic
        // 读取魔数
        if (readUShort(in) != GZIP_MAGIC) {
            throw new ZipException("Not in GZIP format");
        }
        // Check compression method 读取解压方法
        if (readUByte(in) != 8) {
            throw new ZipException("Unsupported compression method");
        }
        // Read flags  读取通用标识
        int flg = readUByte(in);
        // Skip MTIME, XFL, and OS fields
        // 跳过file modification time、extra flags、OS type
        skipBytes(in, 6);
        int n = 2 + 2 + 6;
        // Skip optional extra field
        if ((flg & FEXTRA) == FEXTRA) {
            int m = readUShort(in);
            skipBytes(in, m);
            n += m + 2;
        }
        // Skip optional file name
        if ((flg & FNAME) == FNAME) {
            do {
                n++;
            } while (readUByte(in) != 0);
        }
        // Skip optional file comment
        if ((flg & FCOMMENT) == FCOMMENT) {
            do {
                n++;
            } while (readUByte(in) != 0);
        }
        // Check optional header CRC
        if ((flg & FHCRC) == FHCRC) {
            int v = (int)crc.getValue() & 0xffff;
            if (readUShort(in) != v) {
                throw new ZipException("Corrupt GZIP header");
            }
            n += 2;
        }
        crc.reset();
        return n;
    }

    /*
     * Reads GZIP member trailer and returns true if the eos
     * reached, false if there are more (concatenated gzip
     * data set)
     */
    /*
     * 解压后：读取文件尾信息，主要是读取数据校验和，以及累计读取了多少解压后的字节。
     * 如果返回true，表示解压操作可以结束了。
     * 如果返回false，表示后续还有gzip片段，解压操作后续还得继续。
     */
    private boolean readTrailer() throws IOException {
        InputStream in = this.in;

        // 返回解压器内部缓冲区中剩余未处理的字节数量
        int n = inf.getRemaining();
        if (n > 0) {
            // 构建输入流序列(集合)，稍后需要先读解压器中的残余字节，再读源头输入流
            in = new SequenceInputStream(
                        new ByteArrayInputStream(buf, len - n, n),
                        new FilterInputStream(in) {
                            public void close() throws IOException {}
                        });
        }
        // Uses left-to-right evaluation order
        if ((readUInt(in) != crc.getValue()) ||
            // rfc1952; ISIZE is the input size modulo 2^32
            (readUInt(in) != (inf.getBytesWritten() & 0xffffffffL)))
            throw new ZipException("Corrupt GZIP trailer");

        // If there are more bytes available in "in" or
        // the leftover in the "inf" is > 26 bytes:
        // this.trailer(8) + next.header.min(10) + next.trailer(8)
        // try concatenated case
        // 如果输入流中还有更多数据，或者解压器的内部缓冲区中有足量(>26)剩余未处理的字节
        if (this.in.available() > 0 || n > 26) {
            int m = 8;                  // this.trailer
            try {
                m += readHeader(in);    // next.header
            } catch (IOException ze) {
                return true;  // ignore any malformed, do nothing
            }
            // 重置解压器，以便解压器接收新数据进行解压
            inf.reset();
            // 如果解压器中包含了下一段gzip文件的信息
            if (n > m)
                inf.setInput(buf, len - n + m, n - m);
            return false;
        }
        return true;
    }

    /*
     * Reads unsigned integer in Intel byte order.
     */
    private long readUInt(InputStream in) throws IOException {
        long s = readUShort(in);
        return ((long)readUShort(in) << 16) | s;
    }

    /*
     * Reads unsigned short in Intel byte order.
     */
    private int readUShort(InputStream in) throws IOException {
        int b = readUByte(in);
        return (readUByte(in) << 8) | b;
    }

    /*
     * Reads unsigned byte.
     */
    private int readUByte(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new EOFException();
        }
        if (b < -1 || b > 255) {
            // Report on this.in, not argument in; see read{Header, Trailer}.
            throw new IOException(this.in.getClass().getName()
                + ".read() returned value out of range -1..255: " + b);
        }
        return b;
    }

    // 临时存储一些字节信息
    private byte[] tmpbuf = new byte[128];

    /*
     * Skips bytes of input data blocking until all bytes are skipped.
     * Does not assume that the input stream is capable of seeking.
     */
    private void skipBytes(InputStream in, int n) throws IOException {
        while (n > 0) {
            int len = in.read(tmpbuf, 0, n < tmpbuf.length ? n : tmpbuf.length);
            if (len == -1) {
                throw new EOFException();
            }
            n -= len;
        }
    }
}
