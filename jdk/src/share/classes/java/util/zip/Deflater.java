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

/**
 * This class provides support for general purpose compression using the
 * popular ZLIB compression library. The ZLIB compression library was
 * initially developed as part of the PNG graphics standard and is not
 * protected by patents. It is fully described in the specifications at
 * the <a href="package-summary.html#package_description">java.util.zip
 * package description</a>.
 *
 * <p>The following code fragment demonstrates a trivial compression
 * and decompression of a string using <tt>Deflater</tt> and
 * <tt>Inflater</tt>.
 *
 * <blockquote><pre>
 * try {
 *     // Encode a String into bytes
 *     String inputString = "blahblahblah";
 *     byte[] input = inputString.getBytes("UTF-8");
 *
 *     // Compress the bytes
 *     byte[] output = new byte[100];
 *     Deflater compresser = new Deflater();
 *     compresser.setInput(input);
 *     compresser.finish();
 *     int compressedDataLength = compresser.deflate(output);
 *     compresser.end();
 *
 *     // Decompress the bytes
 *     Inflater decompresser = new Inflater();
 *     decompresser.setInput(output, 0, compressedDataLength);
 *     byte[] result = new byte[100];
 *     int resultLength = decompresser.inflate(result);
 *     decompresser.end();
 *
 *     // Decode the bytes into a String
 *     String outputString = new String(result, 0, resultLength, "UTF-8");
 * } catch(java.io.UnsupportedEncodingException ex) {
 *     // handle
 * } catch (java.util.zip.DataFormatException ex) {
 *     // handle
 * }
 * </pre></blockquote>
 *
 * @see         Inflater
 * @author      David Connelly
 */
// 压缩器
public
class Deflater {

    // 资源清理器
    private final ZStreamRef zsRef;
    private byte[] buf = new byte[0];
    private int off, len;
    // 压缩级别
    // 压缩策略
    private int level, strategy;
    // 是否需要设置level和strategy参数
    private boolean setParams;
    // 是否使用刷新模式：FINISH
    // 是否使用刷新模式：FINISH
    private boolean finish, finished;
    // 累计读取了多少压缩前的字节
    private long bytesRead;
    // 累计写入了多少压缩后的字节
    private long bytesWritten;

    /**
     * Compression method for the deflate algorithm (the only one currently
     * supported).
     */
    // 压缩算法：目前仅支持deflate算法
    public static final int DEFLATED = 8;

    /**
     * Compression level for no compression.
     */
    // 压缩级别：不压缩
    public static final int NO_COMPRESSION = 0;

    /**
     * Compression level for fastest compression.
     */
    // 压缩级别：速度最快
    public static final int BEST_SPEED = 1;

    /**
     * Compression level for best compression.
     */
    // 压缩级别：文件最小
    public static final int BEST_COMPRESSION = 9;

    /**
     * Default compression level.
     */
    // 压缩级别：默认
    public static final int DEFAULT_COMPRESSION = -1;

    /**
     * Compression strategy best used for data consisting mostly of small
     * values with a somewhat random distribution. Forces more Huffman coding
     * and less string matching.
     */
    // 压缩策略：压缩数据时对数据进行过滤，以达到更好的压缩效果
    public static final int FILTERED = 1;

    /**
     * Compression strategy for Huffman coding only.
     */
    // 压缩策略：哈夫曼压缩
    public static final int HUFFMAN_ONLY = 2;

    /**
     * Default compression strategy.
     */
    // 压缩策略：默认
    public static final int DEFAULT_STRATEGY = 0;

    /**
     * Compression flush mode used to achieve best compression result.
     *
     * @see Deflater#deflate(byte[], int, int, int)
     * @since 1.7
     */
    // 刷新模式：数据被输出之前，预先决定每次压缩多少数据，以实现最大化压缩
    public static final int NO_FLUSH = 0;

    /**
     * Compression flush mode used to flush out all pending output; may
     * degrade compression for some compression algorithms.
     *
     * @see Deflater#deflate(byte[], int, int, int)
     * @since 1.7
     */
    // 刷新模式：将压缩数据分为若干个压缩快，每次将一个压缩块中的数据写入输出缓存区，然后在数据块后面写入一个10位长度的空白数据块.
    public static final int SYNC_FLUSH = 2;

    /**
     * Compression flush mode used to flush out all pending output and
     * reset the deflater. Using this mode too often can seriously degrade
     * compression.
     *
     * @see Deflater#deflate(byte[], int, int, int)
     * @since 1.7
     */
    // 刷新模式：写入方式与SYNC_FLUSH相同，但在数据块写完后复位压缩状态
    public static final int FULL_FLUSH = 3;

    static {
        /* Zip library is loaded from System.initializeSystemClass */
        initIDs();
    }

    /**
     * Creates a new compressor using the specified compression level.
     * If 'nowrap' is true then the ZLIB header and checksum fields will
     * not be used in order to support the compression format used in
     * both GZIP and PKZIP.
     * @param level the compression level (0-9)
     * @param nowrap if true then use GZIP compatible compression
     */
    // 构造具有默认压缩级别的压缩器(不兼容GZIP)
    public Deflater(int level, boolean nowrap) {
        this.level = level;
        this.strategy = DEFAULT_STRATEGY;
        // 初始化一块本地缓存存放压缩过程中用到的一些附加信息，如字典信息、压缩策略、压缩级别等
        this.zsRef = new ZStreamRef(init(level, DEFAULT_STRATEGY, nowrap));
    }

    /**
     * Creates a new compressor using the specified compression level.
     * Compressed data will be generated in ZLIB format.
     * @param level the compression level (0-9)
     */
    // 构造具有指定压缩级别的压缩器(不兼容GZIP)
    public Deflater(int level) {
        this(level, false);
    }

    /**
     * Creates a new compressor with the default compression level.
     * Compressed data will be generated in ZLIB format.
     */
    // 构造具有默认压缩级别的压缩器，nowrap指示是否兼容GZIP
    public Deflater() {
        this(DEFAULT_COMPRESSION, false);
    }

    /**
     * Sets input data for compression. This should be called whenever
     * needsInput() returns true indicating that more input data is required.
     * @param b the input data bytes
     * @param off the start offset of the data
     * @param len the length of the data
     * @see Deflater#needsInput
     */
    public void setInput(byte[] b, int off, int len) {
        if (b== null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (zsRef) {
            this.buf = b;
            this.off = off;
            this.len = len;
        }
    }

    /**
     * Sets input data for compression. This should be called whenever
     * needsInput() returns true indicating that more input data is required.
     * @param b the input data bytes
     * @see Deflater#needsInput
     */
    public void setInput(byte[] b) {
        setInput(b, 0, b.length);
    }

    /**
     * Sets preset dictionary for compression. A preset dictionary is used
     * when the history buffer can be predetermined. When the data is later
     * uncompressed with Inflater.inflate(), Inflater.getAdler() can be called
     * in order to get the Adler-32 value of the dictionary required for
     * decompression.
     * @param b the dictionary data bytes
     * @param off the start offset of the data
     * @param len the length of the data
     * @see Inflater#inflate
     * @see Inflater#getAdler
     */
    public void setDictionary(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (zsRef) {
            ensureOpen();
            setDictionary(zsRef.address(), b, off, len);
        }
    }

    /**
     * Sets preset dictionary for compression. A preset dictionary is used
     * when the history buffer can be predetermined. When the data is later
     * uncompressed with Inflater.inflate(), Inflater.getAdler() can be called
     * in order to get the Adler-32 value of the dictionary required for
     * decompression.
     * @param b the dictionary data bytes
     * @see Inflater#inflate
     * @see Inflater#getAdler
     */
    public void setDictionary(byte[] b) {
        setDictionary(b, 0, b.length);
    }

    /**
     * Sets the compression strategy to the specified value.
     *
     * <p> If the compression strategy is changed, the next invocation
     * of {@code deflate} will compress the input available so far with
     * the old strategy (and may be flushed); the new strategy will take
     * effect only after that invocation.
     *
     * @param strategy the new compression strategy
     * @exception IllegalArgumentException if the compression strategy is
     *                                     invalid
     */
    public void setStrategy(int strategy) {
        switch (strategy) {
          case DEFAULT_STRATEGY:
          case FILTERED:
          case HUFFMAN_ONLY:
            break;
          default:
            throw new IllegalArgumentException();
        }
        synchronized (zsRef) {
            if (this.strategy != strategy) {
                this.strategy = strategy;
                setParams = true;
            }
        }
    }

    /**
     * Sets the compression level to the specified value.
     *
     * <p> If the compression level is changed, the next invocation
     * of {@code deflate} will compress the input available so far
     * with the old level (and may be flushed); the new level will
     * take effect only after that invocation.
     *
     * @param level the new compression level (0-9)
     * @exception IllegalArgumentException if the compression level is invalid
     */
    public void setLevel(int level) {
        if ((level < 0 || level > 9) && level != DEFAULT_COMPRESSION) {
            throw new IllegalArgumentException("invalid compression level");
        }
        synchronized (zsRef) {
            if (this.level != level) {
                this.level = level;
                setParams = true;
            }
        }
    }

    /**
     * Returns true if the input data buffer is empty and setInput()
     * should be called in order to provide more input.
     * @return true if the input data buffer is empty and setInput()
     * should be called in order to provide more input
     */
    /*
     * 判断是否需要输入更多数据
     *
     * 如果内部缓冲区中没有数据，则返回true，表示需要更多数据。
     * 如果内部缓冲区中已有数据，则返回false，表示不需要更多数据。
     * 这可用于确定是否应调用setInput()方法以提供更多输入。
     */
    public boolean needsInput() {
        synchronized (zsRef) {
            return len <= 0;
        }
    }

    /**
     * When called, indicates that compression should end with the current
     * contents of the input buffer.
     */
    public void finish() {
        synchronized (zsRef) {
            finish = true;
        }
    }

    /**
     * Returns true if the end of the compressed data output stream has
     * been reached.
     * @return true if the end of the compressed data output stream has
     * been reached
     */
    public boolean finished() {
        synchronized (zsRef) {
            return finished;
        }
    }

    /**
     * Compresses the input data and fills specified buffer with compressed
     * data. Returns actual number of bytes of compressed data. A return value
     * of 0 indicates that {@link #needsInput() needsInput} should be called
     * in order to determine if more input data is required.
     *
     * <p>This method uses {@link #NO_FLUSH} as its compression flush mode.
     * An invocation of this method of the form {@code deflater.deflate(b, off, len)}
     * yields the same result as the invocation of
     * {@code deflater.deflate(b, off, len, Deflater.NO_FLUSH)}.
     *
     * @param b the buffer for the compressed data
     * @param off the start offset of the data
     * @param len the maximum number of bytes of compressed data
     * @return the actual number of bytes of compressed data written to the
     *         output buffer
     */
    public int deflate(byte[] b, int off, int len) {
        return deflate(b, off, len, NO_FLUSH);
    }

    /**
     * Compresses the input data and fills specified buffer with compressed
     * data. Returns actual number of bytes of compressed data. A return value
     * of 0 indicates that {@link #needsInput() needsInput} should be called
     * in order to determine if more input data is required.
     *
     * <p>This method uses {@link #NO_FLUSH} as its compression flush mode.
     * An invocation of this method of the form {@code deflater.deflate(b)}
     * yields the same result as the invocation of
     * {@code deflater.deflate(b, 0, b.length, Deflater.NO_FLUSH)}.
     *
     * @param b the buffer for the compressed data
     * @return the actual number of bytes of compressed data written to the
     *         output buffer
     */
    public int deflate(byte[] b) {
        return deflate(b, 0, b.length, NO_FLUSH);
    }

    /**
     * Compresses the input data and fills the specified buffer with compressed
     * data. Returns actual number of bytes of data compressed.
     *
     * <p>Compression flush mode is one of the following three modes:
     *
     * <ul>
     * <li>{@link #NO_FLUSH}: allows the deflater to decide how much data
     * to accumulate, before producing output, in order to achieve the best
     * compression (should be used in normal use scenario). A return value
     * of 0 in this flush mode indicates that {@link #needsInput()} should
     * be called in order to determine if more input data is required.
     *
     * <li>{@link #SYNC_FLUSH}: all pending output in the deflater is flushed,
     * to the specified output buffer, so that an inflater that works on
     * compressed data can get all input data available so far (In particular
     * the {@link #needsInput()} returns {@code true} after this invocation
     * if enough output space is provided). Flushing with {@link #SYNC_FLUSH}
     * may degrade compression for some compression algorithms and so it
     * should be used only when necessary.
     *
     * <li>{@link #FULL_FLUSH}: all pending output is flushed out as with
     * {@link #SYNC_FLUSH}. The compression state is reset so that the inflater
     * that works on the compressed output data can restart from this point
     * if previous compressed data has been damaged or if random access is
     * desired. Using {@link #FULL_FLUSH} too often can seriously degrade
     * compression.
     * </ul>
     *
     * <p>In the case of {@link #FULL_FLUSH} or {@link #SYNC_FLUSH}, if
     * the return value is {@code len}, the space available in output
     * buffer {@code b}, this method should be invoked again with the same
     * {@code flush} parameter and more output space.
     *
     * @param b the buffer for the compressed data
     * @param off the start offset of the data
     * @param len the maximum number of bytes of compressed data
     * @param flush the compression flush mode
     * @return the actual number of bytes of compressed data written to
     *         the output buffer
     *
     * @throws IllegalArgumentException if the flush mode is invalid
     * @since 1.7
     */
    // 向压缩缓冲区output的指定范围填充压缩后的数据，刷新模式由flush指定，返回实际填充的字节数
    public int deflate(byte[] b, int off, int len, int flush) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (zsRef) {
            ensureOpen();
            if (flush == NO_FLUSH || flush == SYNC_FLUSH ||
                flush == FULL_FLUSH) {
                int thisLen = this.len;
                int n = deflateBytes(zsRef.address(), b, off, len, flush);
                bytesWritten += n;
                bytesRead += (thisLen - this.len);
                return n;
            }
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the ADLER-32 value of the uncompressed data.
     * @return the ADLER-32 value of the uncompressed data
     */
    public int getAdler() {
        synchronized (zsRef) {
            ensureOpen();
            return getAdler(zsRef.address());
        }
    }

    /**
     * Returns the total number of uncompressed bytes input so far.
     *
     * <p>Since the number of bytes may be greater than
     * Integer.MAX_VALUE, the {@link #getBytesRead()} method is now
     * the preferred means of obtaining this information.</p>
     *
     * @return the total number of uncompressed bytes input so far
     */
    public int getTotalIn() {
        return (int) getBytesRead();
    }

    /**
     * Returns the total number of uncompressed bytes input so far.
     *
     * @return the total (non-negative) number of uncompressed bytes input so far
     * @since 1.5
     */
    public long getBytesRead() {
        synchronized (zsRef) {
            ensureOpen();
            return bytesRead;
        }
    }

    /**
     * Returns the total number of compressed bytes output so far.
     *
     * <p>Since the number of bytes may be greater than
     * Integer.MAX_VALUE, the {@link #getBytesWritten()} method is now
     * the preferred means of obtaining this information.</p>
     *
     * @return the total number of compressed bytes output so far
     */
    public int getTotalOut() {
        return (int) getBytesWritten();
    }

    /**
     * Returns the total number of compressed bytes output so far.
     *
     * @return the total (non-negative) number of compressed bytes output so far
     * @since 1.5
     */
    public long getBytesWritten() {
        synchronized (zsRef) {
            ensureOpen();
            return bytesWritten;
        }
    }

    /**
     * Resets deflater so that a new set of input data can be processed.
     * Keeps current compression level and strategy settings.
     */
    public void reset() {
        synchronized (zsRef) {
            ensureOpen();
            reset(zsRef.address());
            finish = false;
            finished = false;
            off = len = 0;
            bytesRead = bytesWritten = 0;
        }
    }

    /**
     * Closes the compressor and discards any unprocessed input.
     * This method should be called when the compressor is no longer
     * being used, but will also be called automatically by the
     * finalize() method. Once this method is called, the behavior
     * of the Deflater object is undefined.
     */
    /*
     * 关闭压缩器(本地)并丢弃所有未处理的输入。
     * 当不再使用压缩器时，应调用此方法。
     * 调用此方法后，Deflater对象的行为处于未定义状态。
     */
    public void end() {
        synchronized (zsRef) {
            long addr = zsRef.address();
            zsRef.clear();
            if (addr != 0) {
                end(addr);
                buf = null;
            }
        }
    }

    /**
     * Closes the compressor when garbage is collected.
     */
    protected void finalize() {
        end();
    }

    private void ensureOpen() {
        assert Thread.holdsLock(zsRef);
        if (zsRef.address() == 0)
            throw new NullPointerException("Deflater has been closed");
    }

    private static native void initIDs();
    private native static long init(int level, int strategy, boolean nowrap);
    private native static void setDictionary(long addr, byte[] b, int off, int len);
    private native int deflateBytes(long addr, byte[] b, int off, int len,
                                    int flush);
    private native static int getAdler(long addr);
    private native static void reset(long addr);
    private native static void end(long addr);
}
