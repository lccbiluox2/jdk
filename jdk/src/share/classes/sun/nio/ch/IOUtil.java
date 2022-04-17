/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * File-descriptor based I/O utilities that are shared by NIO classes.
 */

public class IOUtil {

    /**
     * Max number of iovec structures that readv/writev supports
     */
    static final int IOV_MAX;

    private IOUtil() { }                // No instantiation

    static int write(FileDescriptor fd, ByteBuffer src, long position,
                     NativeDispatcher nd)
        throws IOException
    {
        if (src instanceof DirectBuffer)
            return writeFromNativeBuffer(fd, src, position, nd);

        // Substitute a native buffer
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        ByteBuffer bb = Util.getTemporaryDirectBuffer(rem);
        try {
            bb.put(src);
            bb.flip();
            // Do not update src until we see how many bytes were written
            src.position(pos);

            int n = writeFromNativeBuffer(fd, bb, position, nd);
            if (n > 0) {
                // now update src
                src.position(pos + n);
            }
            return n;
        } finally {
            Util.offerFirstTemporaryDirectBuffer(bb);
        }
    }

    private static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                             long position, NativeDispatcher nd)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        int written = 0;
        if (rem == 0)
            return 0;
        if (position != -1) {
            written = nd.pwrite(fd,
                                ((DirectBuffer)bb).address() + pos,
                                rem, position);
        } else {
            written = nd.write(fd, ((DirectBuffer)bb).address() + pos, rem);
        }
        if (written > 0)
            bb.position(pos + written);
        return written;
    }

    static long write(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
        throws IOException
    {
        return write(fd, bufs, 0, bufs.length, nd);
    }

    static long write(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                      NativeDispatcher nd)
        throws IOException
    {
        IOVecWrapper vec = IOVecWrapper.get(length);

        boolean completed = false;
        int iov_len = 0;
        try {

            // Iterate over buffers to populate native iovec array.
            int count = offset + length;
            int i = offset;
            while (i < count && iov_len < IOV_MAX) {
                ByteBuffer buf = bufs[i];
                int pos = buf.position();
                int lim = buf.limit();
                assert (pos <= lim);
                int rem = (pos <= lim ? lim - pos : 0);
                if (rem > 0) {
                    vec.setBuffer(iov_len, buf, pos, rem);

                    // allocate shadow buffer to ensure I/O is done with direct buffer
                    if (!(buf instanceof DirectBuffer)) {
                        ByteBuffer shadow = Util.getTemporaryDirectBuffer(rem);
                        shadow.put(buf);
                        shadow.flip();
                        vec.setShadow(iov_len, shadow);
                        buf.position(pos);  // temporarily restore position in user buffer
                        buf = shadow;
                        pos = shadow.position();
                    }

                    vec.putBase(iov_len, ((DirectBuffer)buf).address() + pos);
                    vec.putLen(iov_len, rem);
                    iov_len++;
                }
                i++;
            }
            if (iov_len == 0)
                return 0L;

            long bytesWritten = nd.writev(fd, vec.address, iov_len);

            // Notify the buffers how many bytes were taken
            long left = bytesWritten;
            for (int j=0; j<iov_len; j++) {
                if (left > 0) {
                    ByteBuffer buf = vec.getBuffer(j);
                    int pos = vec.getPosition(j);
                    int rem = vec.getRemaining(j);
                    int n = (left > rem) ? rem : (int)left;
                    buf.position(pos + n);
                    left -= n;
                }
                // return shadow buffers to buffer pool
                ByteBuffer shadow = vec.getShadow(j);
                if (shadow != null)
                    Util.offerLastTemporaryDirectBuffer(shadow);
                vec.clearRefs(j);
            }

            completed = true;
            return bytesWritten;

        } finally {
            // if an error occurred then clear refs to buffers and return any shadow
            // buffers to cache
            if (!completed) {
                for (int j=0; j<iov_len; j++) {
                    ByteBuffer shadow = vec.getShadow(j);
                    if (shadow != null)
                        Util.offerLastTemporaryDirectBuffer(shadow);
                    vec.clearRefs(j);
                }
            }
        }
    }

    /*
     * 从文件描述符fd（关联的文件/socket）中position位置处开始读取，读到的内容写入dst后，返回读到的字节数量
     * 当position==-1时，该方法是一次性地，即已经读完的流不可以重复读取（不支持随机读取）
     * 当position>=0时，该方法可重复调用，读取的位置是position指定的位置（支持随机读取）
     */
    static int read(FileDescriptor fd, ByteBuffer dst, long position,
                    NativeDispatcher nd)
        throws IOException
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");

        // 如果待写缓冲区已经是直接缓冲区
        if (dst instanceof DirectBuffer)
            // 直接向目标缓冲区写入从fd中读到的数据
            return readIntoNativeBuffer(fd, dst, position, nd);

        // Substitute a native buffer
        // 如果目标缓冲区不是直接缓冲区，则需要准备一个直接缓冲区作为中转
        ByteBuffer bb = Util.getTemporaryDirectBuffer(
                // 获取dst中剩余可写空间
                dst.remaining());
        try {
            // 从fd读取，向临时创建的直接缓冲bb区写入
            int n = readIntoNativeBuffer(fd, bb, position, nd);
            // 从写模式切换为读模式
            bb.flip();
            if (n > 0)
                // 从临时创建的直接缓冲区bb中读取，向dst缓冲区写入
                dst.put(bb);
            return n;
        } finally {
            // 采用FILO的形式(入栈模式)将bb放入Buffer缓存池以待复用
            Util.offerFirstTemporaryDirectBuffer(bb);
        }
    }

    /*
     * 从文件描述符fd（关联的文件/socket）中position位置处读取，读到的内容写入直接缓冲区bb后，返回读到的字节数量
     * 当position==-1时，该方法是一次性地，即已经读完的流不可以重复读取（不支持随机读取）
     * 当position>=0时，该方法可重复调用，读取的位置是position指定的位置（支持随机读取）
     */
    private static int readIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                            long position, NativeDispatcher nd)
        throws IOException
    {
        int pos = bb.position(); // 待写缓冲区游标
        int lim = bb.limit();// 待写缓冲区上界
        assert (pos <= lim);

        // 获取bb中剩余可写空间
        int rem = (pos <= lim ? lim - pos : 0);

        if (rem == 0)
            return 0;
        int n = 0;
        if (position != -1) {
            // 从文件描述符fd读取数据，并填充address指向的本地内存中的前rem个字节
            n = nd.pread(fd, ((DirectBuffer)bb).address() + pos,
                         rem, position);
        } else {
            // 从文件描述符fd读取数据，并从address指向的本地内存中的position位置开始，填充前rem个字节
            n = nd.read(fd, ((DirectBuffer)bb).address() + pos, rem);
        }

        // 设置新的游标position
        if (n > 0)
            bb.position(pos + n);
        return n;
    }

    static long read(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
        throws IOException
    {
        return read(fd, bufs, 0, bufs.length, nd);
    }


    /*
     * 从文件描述符fd（关联的文件/socket）中读取，读到的内容依次写入dsts中offset处起的length个缓冲区后，返回读到的字节数量
     * 该方法是一次性地，即已经读完的流不可以重复读取（不支持随机读取）
     * 是否使用内存分页对齐与DirectIO，取决于alignment和directIO参数
     */
    static long read(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                     NativeDispatcher nd)
        throws IOException
    {
        // 创建长度为size的结构体length的数组
        IOVecWrapper vec = IOVecWrapper.get(length);

        boolean completed = false;
        int iov_len = 0;
        try {

            // Iterate over buffers to populate native iovec array.
            int count = offset + length;
            int i = offset;
            // 遍历缓冲区数组，创建底层结构体iovec的数组，以便向其中写入数据
            while (i < count && iov_len < IOV_MAX) {
                ByteBuffer buf = bufs[i];
                // 无法向只读Buffer写入数据
                if (buf.isReadOnly())
                    throw new IllegalArgumentException("Read-only buffer");
                int pos = buf.position();
                int lim = buf.limit();
                assert (pos <= lim);

                // 获取buf中剩余可写空间
                int rem = (pos <= lim ? lim - pos : 0);

                if (rem > 0) {
                    vec.setBuffer(iov_len, buf, pos, rem);

                    // allocate shadow buffer to ensure I/O is done with direct buffer
                    if (!(buf instanceof DirectBuffer)) {
                        ByteBuffer shadow = Util.getTemporaryDirectBuffer(rem);
                        vec.setShadow(iov_len, shadow);
                        buf = shadow;
                        pos = shadow.position();
                    }

                    vec.putBase(iov_len, ((DirectBuffer)buf).address() + pos);
                    vec.putLen(iov_len, rem);
                    iov_len++;
                }
                i++;
            }
            if (iov_len == 0)
                return 0L;

            long bytesRead = nd.readv(fd, vec.address, iov_len);

            // Notify the buffers how many bytes were read
            long left = bytesRead;
            for (int j=0; j<iov_len; j++) {
                ByteBuffer shadow = vec.getShadow(j);
                if (left > 0) {
                    ByteBuffer buf = vec.getBuffer(j);
                    int rem = vec.getRemaining(j);
                    int n = (left > rem) ? rem : (int)left;
                    if (shadow == null) {
                        int pos = vec.getPosition(j);
                        buf.position(pos + n);
                    } else {
                        shadow.limit(shadow.position() + n);
                        buf.put(shadow);
                    }
                    left -= n;
                }
                if (shadow != null)
                    Util.offerLastTemporaryDirectBuffer(shadow);
                vec.clearRefs(j);
            }

            completed = true;
            return bytesRead;

        } finally {
            // if an error occurred then clear refs to buffers and return any shadow
            // buffers to cache
            if (!completed) {
                for (int j=0; j<iov_len; j++) {
                    ByteBuffer shadow = vec.getShadow(j);
                    if (shadow != null)
                        Util.offerLastTemporaryDirectBuffer(shadow);
                    vec.clearRefs(j);
                }
            }
        }
    }

    public static FileDescriptor newFD(int i) {
        FileDescriptor fd = new FileDescriptor();
        setfdVal(fd, i);
        return fd;
    }

    static native boolean randomBytes(byte[] someBytes);

    /**
     * Returns two file descriptors for a pipe encoded in a long.
     * The read end of the pipe is returned in the high 32 bits,
     * while the write end is returned in the low 32 bits.
     */
    static native long makePipe(boolean blocking);

    static native boolean drain(int fd) throws IOException;

    public static native void configureBlocking(FileDescriptor fd,
                                                boolean blocking)
        throws IOException;

    public static native int fdVal(FileDescriptor fd);

    static native void setfdVal(FileDescriptor fd, int value);

    static native int fdLimit();

    static native int iovMax();

    static native void initIDs();

    /**
     * Used to trigger loading of native libraries
     */
    public static void load() { }

    static {
        java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<Void>() {
                    public Void run() {
                        // 加载本地库
                        System.loadLibrary("net");
                        System.loadLibrary("nio");
                        return null;
                    }
                });

        initIDs();

        IOV_MAX = iovMax();
    }

}
