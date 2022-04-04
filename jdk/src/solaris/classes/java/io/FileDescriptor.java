/*
 * Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Instances of the file descriptor class serve as an opaque handle
 * to the underlying machine-specific structure representing an open
 * file, an open socket, or another source or sink of bytes. The
 * main practical use for a file descriptor is to create a
 * <code>FileInputStream</code> or <code>FileOutputStream</code> to
 * contain it.
 * <p>
 * Applications should not create their own file descriptors.
 *
 * @author  Pavani Diwanji
 * @see     java.io.FileInputStream
 * @see     java.io.FileOutputStream
 * @since   JDK1.0
 */
/*
 * 文件描述符用来保存操作系统中的标准流id和对文件的引用handle
 *
 * FileDescriptor实例会被FileInputStream/FileOutputStream/RandomAccessFile持有，
 * 这三个类在打开文件时，在JNI代码中使用open执行系统调用打开文件，得到文件描述符在JNI代码中设置到FileDescriptor的fd成员变量上
 *
 * 关闭FileInputStream/FileOutputStream/RandomAccessFile时，会关闭底层对应的文件描述符。
 * 关闭是文件底层是通过使用close执行系统调用实现的。
 *
 * 简单理解：在Java中，文件描述符是"文件"的抽象表示。文件可以是：file、socket或其他IO连接
 *
 * 注：应用程序不应创建自己的文件描述符。
 */
public final class FileDescriptor {

    /*
     * 当前文件描述符在本地(native层)的引用
     *
     * 每个打开的文件都会为它分配一个fd值作为其唯一编号，通过该值可以找到对应的文件并进行相关操作。
     *
     * 在windows上，文件描述符是文件句柄结构中的一个字段。
     */
    private int fd;
//    /*
//     * 文件句柄(windows下的概念)
//     *
//     * 句柄是Windows下各种对象的标识符，比如文件、资源、菜单、光标等等。
//     * 文件句柄和文件描述符类似，它也是一个非负整数，也用于定位文件数据在内存中的位置。
//     *
//     * 在Windows中，文件句柄可以看做是FILE*，即文件指针。
//     */
//    private long handle;

    // 存放文件描述符关联的唯一流对象（绝大多数情况）
    private Closeable parent;
    // 存放关联了文件描述符的所有文件(流)对象（使用流的带有文件描述符参数的构造方法会用到此字段）
    private List<Closeable> otherParents;
    // 判断fd是否被释放
    private boolean closed;

//    // 判断当前文件是否处于追加(append)模式
//    private boolean append;
//        // 在为明确关闭FileDescriptor的情况下对其进行清理
//        private PhantomCleanable<FileDescriptor> cleanup;


    /**
     * Constructs an (invalid) FileDescriptor
     * object.
     */
    // 构造一个无效的文件描述符，后续设置其fd和handle
    public /**/ FileDescriptor() {
        fd = -1;
    }

    // 构造标准流（输入流、输出流、错误流）的文件描述符
    private /* */ FileDescriptor(int fd) {
        this.fd = fd;
    }

    /**
     * A handle to the standard input stream. Usually, this file
     * descriptor is not used directly, but rather via the input stream
     * known as <code>System.in</code>.
     *
     * @see     java.lang.System#in
     */
    // 标准输入流，被封装为System.in
    public static final FileDescriptor in = new FileDescriptor(0);

    /**
     * A handle to the standard output stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as <code>System.out</code>.
     * @see     java.lang.System#out
     */
    // 标准输出流，被封装为System.out
    public static final FileDescriptor out = new FileDescriptor(1);

    /**
     * A handle to the standard error stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as <code>System.err</code>.
     *
     * @see     java.lang.System#err
     */
    // 标准错误流，被封装为System.err
    public static final FileDescriptor err = new FileDescriptor(2);

    /**
     * Tests if this file descriptor object is valid.
     *
     * @return  <code>true</code> if the file descriptor object represents a
     *          valid, open file, socket, or other active I/O connection;
     *          <code>false</code> otherwise.
     */
    // 判断文件描述符是否有效，判断依据是handle或fd
    public boolean valid() {
        return fd != -1;
    }

    /**
     * Force all system buffers to synchronize with the underlying
     * device.  This method returns after all modified data and
     * attributes of this FileDescriptor have been written to the
     * relevant device(s).  In particular, if this FileDescriptor
     * refers to a physical storage medium, such as a file in a file
     * system, sync will not return until all in-memory modified copies
     * of buffers associated with this FileDescriptor have been
     * written to the physical medium.
     *
     * sync is meant to be used by code that requires physical
     * storage (such as a file) to be in a known state  For
     * example, a class that provided a simple transaction facility
     * might use sync to ensure that all changes to a file caused
     * by a given transaction were recorded on a storage medium.
     *
     * sync only affects buffers downstream of this FileDescriptor.  If
     * any in-memory buffering is being done by the application (for
     * example, by a BufferedOutputStream object), those buffers must
     * be flushed into the FileDescriptor (for example, by invoking
     * OutputStream.flush) before that data will be affected by sync.
     *
     * @exception SyncFailedException
     *        Thrown when the buffers cannot be flushed,
     *        or because the system cannot guarantee that all the
     *        buffers have been synchronized with physical media.
     * @since     JDK1.1
     */
    public native void sync() throws SyncFailedException;

    /* This routine initializes JNI field offsets for the class */
    private static native void initIDs();

    static {
        initIDs();
    }

    // Set up JavaIOFileDescriptorAccess in SharedSecrets
    static {
        sun.misc.SharedSecrets.setJavaIOFileDescriptorAccess(
            new sun.misc.JavaIOFileDescriptorAccess() {
                public void set(FileDescriptor obj, int fd) {
                    obj.fd = fd;
                }

                public int get(FileDescriptor obj) {
                    return obj.fd;
                }

                public void setHandle(FileDescriptor obj, long handle) {
                    throw new UnsupportedOperationException();
                }

                public long getHandle(FileDescriptor obj) {
                    throw new UnsupportedOperationException();
                }
            }
        );
    }

    /*
     * Package private methods to track referents.
     * If multiple streams point to the same FileDescriptor, we cycle
     * through the list of all referents and call close()
     */

    /**
     * Attach a Closeable to this FD for tracking.
     * parent reference is added to otherParents when
     * needed to make closeAll simpler.
     */
    synchronized void attach(Closeable c) {
        if (parent == null) {
            // first caller gets to do this
            parent = c;
        } else if (otherParents == null) {
            otherParents = new ArrayList<>();
            otherParents.add(parent);
            otherParents.add(c);
        } else {
            otherParents.add(c);
        }
    }

    /**
     * Cycle through all Closeables sharing this FD and call
     * close() on each one.
     *
     * The caller closeable gets to call close0().
     */
    @SuppressWarnings("try")
    synchronized void closeAll(Closeable releaser) throws IOException {
        if (!closed) {
            closed = true;
            IOException ioe = null;
            try (Closeable c = releaser) {
                if (otherParents != null) {
                    for (Closeable referent : otherParents) {
                        try {
                            referent.close();
                        } catch(IOException x) {
                            if (ioe == null) {
                                ioe = x;
                            } else {
                                ioe.addSuppressed(x);
                            }
                        }
                    }
                }
            } catch(IOException ex) {
                /*
                 * If releaser close() throws IOException
                 * add other exceptions as suppressed.
                 */
                if (ioe != null)
                    ex.addSuppressed(ioe);
                ioe = ex;
            } finally {
                if (ioe != null)
                    throw ioe;
            }
        }
    }
}
