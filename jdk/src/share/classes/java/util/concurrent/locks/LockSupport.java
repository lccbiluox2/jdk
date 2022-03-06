/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import sun.misc.Unsafe;


/**
 * Basic thread blocking primitives for creating locks and other
 * synchronization classes.
 *
 * <p>This class associates, with each thread that uses it, a permit
 * (in the sense of the {@link java.util.concurrent.Semaphore
 * Semaphore} class). A call to {@code park} will return immediately
 * if the permit is available, consuming it in the process; otherwise
 * it <em>may</em> block.  A call to {@code unpark} makes the permit
 * available, if it was not already available. (Unlike with Semaphores
 * though, permits do not accumulate. There is at most one.)
 *
 * <p>Methods {@code park} and {@code unpark} provide efficient
 * means of blocking and unblocking threads that do not encounter the
 * problems that cause the deprecated methods {@code Thread.suspend}
 * and {@code Thread.resume} to be unusable for such purposes: Races
 * between one thread invoking {@code park} and another thread trying
 * to {@code unpark} it will preserve liveness, due to the
 * permit. Additionally, {@code park} will return if the caller's
 * thread was interrupted, and timeout versions are supported. The
 * {@code park} method may also return at any other time, for "no
 * reason", so in general must be invoked within a loop that rechecks
 * conditions upon return. In this sense {@code park} serves as an
 * optimization of a "busy wait" that does not waste as much time
 * spinning, but must be paired with an {@code unpark} to be
 * effective.
 *
 * <p>The three forms of {@code park} each also support a
 * {@code blocker} object parameter. This object is recorded while
 * the thread is blocked to permit monitoring and diagnostic tools to
 * identify the reasons that threads are blocked. (Such tools may
 * access blockers using method {@link #getBlocker}.) The use of these
 * forms rather than the original forms without this parameter is
 * strongly encouraged. The normal argument to supply as a
 * {@code blocker} within a lock implementation is {@code this}.
 *
 * <p>These methods are designed to be used as tools for creating
 * higher-level synchronization utilities, and are not in themselves
 * useful for most concurrency control applications.  The {@code park}
 * method is designed for use only in constructions of the form:
 * <pre>while (!canProceed()) { ... LockSupport.park(this); }</pre>
 * where neither {@code canProceed} nor any other actions prior to the
 * call to {@code park} entail locking or blocking.  Because only one
 * permit is associated with each thread, any intermediary uses of
 * {@code park} could interfere with its intended effects.
 *
 * <p><b>Sample Usage.</b> Here is a sketch of a first-in-first-out
 * non-reentrant lock class:
 * <pre>{@code
 * class FIFOMutex {
 *   private final AtomicBoolean locked = new AtomicBoolean(false);
 *   private final Queue<Thread> waiters
 *     = new ConcurrentLinkedQueue<Thread>();
 *
 *   public void lock() {
 *     boolean wasInterrupted = false;
 *     Thread current = Thread.currentThread();
 *     waiters.add(current);
 *
 *     // Block while not first in queue or cannot acquire lock
 *     while (waiters.peek() != current ||
 *            !locked.compareAndSet(false, true)) {
 *        LockSupport.park(this);
 *        if (Thread.interrupted()) // ignore interrupts while waiting
 *          wasInterrupted = true;
 *     }
 *
 *     waiters.remove();
 *     if (wasInterrupted)          // reassert interrupt status on exit
 *        current.interrupt();
 *   }
 *
 *   public void unlock() {
 *     locked.set(false);
 *     LockSupport.unpark(waiters.peek());
 *   }
 * }}</pre>
 */

public class LockSupport {
    // 私有构造函数，无法被实例化
    private LockSupport() {} // Cannot be instantiated.

    // Hotspot implementation via intrinsics API
    // 获取Unsafe实例
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    // 表示内存偏移地址
    private static final long parkBlockerOffset;

    static {
        try {
            // 获取Thread的parkBlocker字段的内存偏移地址
            parkBlockerOffset = unsafe.objectFieldOffset
                (java.lang.Thread.class.getDeclaredField("parkBlocker"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private static void setBlocker(Thread t, Object arg) {
        // Even though volatile, hotspot doesn't need a write barrier here.
        // 设置线程t的parkBlocker字段的值为arg
        unsafe.putObject(t, parkBlockerOffset, arg);
    }

    /**
     * Makes available the permit for the given thread, if it
     * was not already available.  If the thread was blocked on
     * {@code park} then it will unblock.  Otherwise, its next call
     * to {@code park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * thread has not been started.
     *
     * unpark函数，释放线程的许可，即激活调用park后阻塞的线程。这个函数不是安全的，
     * 调用这个函数时要确保线程依旧存活。
     *
     * 此函数表示如果给定线程的许可尚不可用，则使其可用。如果线程在 park 上受阻塞，
     * 则它将解除其阻塞状态。否则，保证下一次调用 park 不会受阻塞。如果给定线程尚未启动，
     * 则无法保证此操作有任何效果。
     *
     * @param thread the thread to unpark, or {@code null}, in which case
     *        this operation has no effect
     */
    public static void unpark(Thread thread) {
        // 线程为不空
        if (thread != null)
            // 释放该线程许可
            unsafe.unpark(thread);
    }

    /**
     * Disables the current thread for thread scheduling purposes unless the
     * permit is available.
     *
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     *
     * <ul>
     * <li>Some other thread invokes {@link #unpark unpark} with the
     * current thread as the target; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread upon return.
     *
     * 著作权归https://pdai.tech所有。
     * 链接：https://www.pdai.tech/md/java/thread/java-thread-x-lock-LockSupport.html
     *
     * park函数，阻塞线程，并且该线程在下列情况发生之前都会被阻塞:
     *
     * ① 调用unpark函数，释放该线程的许可。
     * ② 该线程被中断。
     * ③ 设置的时间到了。
     *
     * 并且，当time为绝对时间时，isAbsolute为true，否则，isAbsolute为false。
     * 当time为0时，表示无限等待，直到unpark发生。
     *
     * 著作权归https://pdai.tech所有。
     * 链接：https://www.pdai.tech/md/java/thread/java-thread-x-lock-LockSupport.html
     *
     * 说明: 调用park函数时，首先获取当前线程，然后设置当前线程的parkBlocker字段，即
     * 调用setBlocker函数，之后调用Unsafe类的park函数，之后再调用setBlocker函数。
     * 那么问题来了，为什么要在此park函数中要调用两次setBlocker函数呢?
     *
     * 原因其实很简单，调用park函数时，当前线程首先设置好parkBlocker字段，然后再调用
     * Unsafe的park函数，此后，当前线程就已经阻塞了，等待该线程的unpark函数被调用，
     * 所以后面的一个setBlocker函数无法运行，unpark函数被调用，该线程获得许可后，
     * 就可以继续运行了，也就运行第二个setBlocker，把该线程的parkBlocker字段设置为null，
     * 这样就完成了整个park函数的逻辑。如果没有第二个setBlocker，那么之后没有调用
     * park(Object blocker)，而直接调用getBlocker函数，得到的还是前一个
     * park(Object blocker)设置的blocker，显然是不符合逻辑的。
     *
     * 总之，必须要保证在park(Object blocker)整个函数执行完后，该线程的parkBlocker
     * 字段又恢复为null。所以，park(Object)型函数里必须要调用setBlocker函数两次。
     *
     * @param blocker the synchronization object responsible for this
     *        thread parking
     * @since 1.6
     */
    public static void park(Object blocker) {
        // 获取当前线程
        Thread t = Thread.currentThread();
        // 设置Blocker
        setBlocker(t, blocker);
        // 获取许可
        unsafe.park(false, 0L);
        // 重新可运行后再此设置Blocker
        setBlocker(t, null);
    }

    /**
     * Disables the current thread for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other thread invokes {@link #unpark unpark} with the
     * current thread as the target; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     *
     * <li>The specified waiting time elapses; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the elapsed time
     * upon return.
     *
     * @param blocker the synchronization object responsible for this
     *        thread parking
     * @param nanos the maximum number of nanoseconds to wait
     * @since 1.6
     */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            unsafe.park(false, nanos);
            setBlocker(t, null);
        }
    }

    /**
     * Disables the current thread for thread scheduling purposes, until
     * the specified deadline, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other thread invokes {@link #unpark unpark} with the
     * current thread as the target; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts} the
     * current thread; or
     *
     * <li>The specified deadline passes; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the current time
     * upon return.
     *
     * @param blocker the synchronization object responsible for this
     *        thread parking
     * @param deadline the absolute time, in milliseconds from the Epoch,
     *        to wait until
     * @since 1.6
     */
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        unsafe.park(true, deadline);
        setBlocker(t, null);
    }

    /**
     * Returns the blocker object supplied to the most recent
     * invocation of a park method that has not yet unblocked, or null
     * if not blocked.  The value returned is just a momentary
     * snapshot -- the thread may have since unblocked or blocked on a
     * different blocker object.
     *
     * @param t the thread
     * @return the blocker
     * @throws NullPointerException if argument is null
     * @since 1.6
     */
    public static Object getBlocker(Thread t) {
        if (t == null)
            throw new NullPointerException();
        return unsafe.getObjectVolatile(t, parkBlockerOffset);
    }

    /**
     * Disables the current thread for thread scheduling purposes unless the
     * permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of three
     * things happens:
     *
     * <ul>
     *
     * <li>Some other thread invokes {@link #unpark unpark} with the
     * current thread as the target; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread upon return.
     *
     * 说明: 调用了park函数后，会禁用当前线程，除非许可可用。在以下三种情况之一发生之前，
     * 当前线程都将处于休眠状态，即下列情况发生时，当前线程会获取许可，可以继续运行。
     * 1. 其他某个线程将当前线程作为目标调用 unpark。
     * 2. 其他某个线程中断当前线程。
     * 3. 该调用不合逻辑地(即毫无理由地)返回。
     */
    public static void park() {
        // 获取许可，设置时间为无限长，直到可以获取许可
        unsafe.park(false, 0L);
    }

    /**
     * Disables the current thread for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other thread invokes {@link #unpark unpark} with the
     * current thread as the target; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     *
     * <li>The specified waiting time elapses; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the elapsed time
     * upon return.
     *
     * 此函数表示在许可可用前禁用当前线程，并最多等待指定的等待时间
     *
     * @param nanos the maximum number of nanoseconds to wait
     */
    public static void parkNanos(long nanos) {
        // 时间大于0
        if (nanos > 0)
            unsafe.park(false, nanos);
    }

    /**
     * Disables the current thread for thread scheduling purposes, until
     * the specified deadline, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other thread invokes {@link #unpark unpark} with the
     * current thread as the target; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     *
     * <li>The specified deadline passes; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the current time
     * upon return.
     *
     * @param deadline the absolute time, in milliseconds from the Epoch,
     *        to wait until
     */
    public static void parkUntil(long deadline) {
        unsafe.park(true, deadline);
    }
}
