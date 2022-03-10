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

package java.util.concurrent;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A synchronization aid that allows one or more threads to wait until
 * a set of operations being performed in other threads completes.
 *
 * <p>A {@code CountDownLatch} is initialized with a given <em>count</em>.
 * The {@link #await await} methods block until the current count reaches
 * zero due to invocations of the {@link #countDown} method, after which
 * all waiting threads are released and any subsequent invocations of
 * {@link #await await} return immediately.  This is a one-shot phenomenon
 * -- the count cannot be reset.  If you need a version that resets the
 * count, consider using a {@link CyclicBarrier}.
 *
 * <p>A {@code CountDownLatch} is a versatile synchronization tool
 * and can be used for a number of purposes.  A
 * {@code CountDownLatch} initialized with a count of one serves as a
 * simple on/off latch, or gate: all threads invoking {@link #await await}
 * wait at the gate until it is opened by a thread invoking {@link
 * #countDown}.  A {@code CountDownLatch} initialized to <em>N</em>
 * can be used to make one thread wait until <em>N</em> threads have
 * completed some action, or some action has been completed N times.
 *
 * <p>A useful property of a {@code CountDownLatch} is that it
 * doesn't require that threads calling {@code countDown} wait for
 * the count to reach zero before proceeding, it simply prevents any
 * thread from proceeding past an {@link #await await} until all
 * threads could pass.
 *
 * <p><b>Sample usage:</b> Here is a pair of classes in which a group
 * of worker threads use two countdown latches:
 * <ul>
 * <li>The first is a start signal that prevents any worker from proceeding
 * until the driver is ready for them to proceed;
 * <li>The second is a completion signal that allows the driver to wait
 * until all workers have completed.
 * </ul>
 *
 *  <pre> {@code
 * class Driver { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch startSignal = new CountDownLatch(1);
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       new Thread(new Worker(startSignal, doneSignal)).start();
 *
 *     doSomethingElse();            // don't let run yet
 *     startSignal.countDown();      // let all threads proceed
 *     doSomethingElse();
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class Worker implements Runnable {
 *   private final CountDownLatch startSignal;
 *   private final CountDownLatch doneSignal;
 *   Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
 *     this.startSignal = startSignal;
 *     this.doneSignal = doneSignal;
 *   }
 *   public void run() {
 *     try {
 *       startSignal.await();
 *       doWork();
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Another typical usage would be to divide a problem into N parts,
 * describe each part with a Runnable that executes that portion and
 * counts down on the latch, and queue all the Runnables to an
 * Executor.  When all sub-parts are complete, the coordinating thread
 * will be able to pass through await. (When threads must repeatedly
 * count down in this way, instead use a {@link CyclicBarrier}.)
 *
 *  <pre> {@code
 * class Driver2 { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *     Executor e = ...
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       e.execute(new WorkerRunnable(doneSignal, i));
 *
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class WorkerRunnable implements Runnable {
 *   private final CountDownLatch doneSignal;
 *   private final int i;
 *   WorkerRunnable(CountDownLatch doneSignal, int i) {
 *     this.doneSignal = doneSignal;
 *     this.i = i;
 *   }
 *   public void run() {
 *     try {
 *       doWork(i);
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Memory consistency effects: Until the count reaches
 * zero, actions in a thread prior to calling
 * {@code countDown()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions following a successful return from a corresponding
 * {@code await()} in another thread.
 *
 * @since 1.5
 * @author Doug Lea
 *
 * todo：参考 【高并发】JUC中等待多线程完成的工具类CountDownLatch
 *     https://blog.csdn.net/qq_21383435/article/details/110248728
 */
public class CountDownLatch {
    /**
     * Synchronization control For CountDownLatch.
     * Uses AQS state to represent count.
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        // 返回当前计数
        int getCount() {
            return getState();
        }

        /**
         *  试图在共享模式下获取对象状态，该函数只是简单的判断AQS的state是否为0，
         *  为0则返回1，不为0则返回-1。
         *
         *  共享模式下获取资源，这里无视共享模式下需要获取的资源数，只判断当前的state值是否为0，为0的时候，
         *  意味资源获取成功，闭锁已经释放，所有等待线程需要解除阻塞
         *
         *  如果state当前已经为0，那么线程完全不会加入AQS同步队列中等待，表现为直接运行
         *
         * state 是什么？我开始居然搞混了 我搞成 ThreadPoolExecutor 中 AtomicInteger ctl
         *
         * state是 你创建 CountDownLatch(int count)传递的值，代表你要等几个线程完成
         * 这里 getState() == 0 开始假设是12 然后各个线程调用 countDown() 的时候，会对这个值
         * 进行-1操作，如果等于0 那么证明，所有的线程都可以唤醒了，否则就是还要继续碎觉
         *
         */
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        /**
         * 共享模式下释放资源，这里也无视共享模式下需要释放的资源数，每次让状态值通过CAS减少1，
         * 当减少到0的时候，返回true
         *
         * 试图设置状态来反映共享模式下的一个释放
         *
         * state是 你创建 CountDownLatch(int count)传递的值，代表你要等几个线程完成
         * 每个线程运行完毕，都会调用 countDown 方法，导致 state -1
         * 当 state = 0 的时候，就需要唤醒所有的线程，内部肯定有这样的方法
         *
         * 所以这里一个线程运行完毕后，会先看看状态是不是为0了，为0，那么就要唤醒所有线程
         * 不为0，需要将state - 1 操作，然后再次判断 state是不是为0了
         *
         * state 为 0 返回 true  否则 返回 false
         */
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            // 无限循环
            for (;;) {
                int c = getState(); // 获取状态
                // 没有被线程占有
                if (c == 0)
                    // 这里为啥返回 false呢？与下面的不符合，估计是 线程调用countDown就应该-1操作。
                    // 但是我还没操作呢。你就返回-1了。很奇怪，除非是初始化的时候，还没调用 CountDownLatch(int count)
                    // 这个操作的时候

                    // 这种情况下说明了当前state为0，从tryAcquireShared方法来看，线程不会加入AQS同步
                    // 队列进行阻塞，所以也无须释放
                    return false;

                // state的快照值减少1，并且通过CAS设置快照值更新为state，如果state减少为0
                // 则返回true，意味着需要唤醒阻塞线程
                int nextc = c-1;
                // 比较并且设置成功
                if (compareAndSetState(c, nextc))
                    // sate == 0的时候 返回 true
                    return nextc == 0;
            }
        }
    }

    private final Sync sync;

    /**
     * Constructs a {@code CountDownLatch} initialized with the given count.
     *
     * 说明: 该构造函数可以构造一个用给定计数初始化的CountDownLatch，并且构造函数内完成了
     * sync的初始化，并设置了状态数。
     *
     * @param count the number of times {@link #countDown} must be invoked
     *        before threads can pass through {@link #await}
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        // 初始化状态数
        this.sync = new Sync(count);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>If the current count is zero then this method returns immediately.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of two things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     *
     * 此函数将会使当前线程在锁存器倒计数至零之前一直等待，除非线程被中断。
     */
    public void await() throws InterruptedException {
        // 转发到sync对象上,因为你是多个线程调用await方法，所以拿到的是共享锁
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted},
     * or the specified waiting time elapses.
     *
     * <p>If the current count is zero then this method returns immediately
     * with the value {@code true}.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of three things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If the count reaches zero then the method returns with the
     * value {@code true}.
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero and {@code false}
     *         if the waiting time elapsed before the count reached zero
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the new count is zero then all waiting threads are re-enabled for
     * thread scheduling purposes.
     *
     * <p>If the current count equals zero then nothing happens.
     *
     * 减少闩锁的计数，如果计数为零，则释放所有等待线程。
     *
     * 如果当前计数大于0，则该计数将递减。如果新计数为零，那么为了线程调度目的，
     * 将重新启用所有等待的线程。
     *
     * 如果当前计数等于0，那么什么也不会发生。
     */
    public void countDown() {
        /**
         * state是 你创建 CountDownLatch(int count)传递的值，代表你要等几个线程完成
         * 每个线程运行完毕，都会调用 countDown 方法，导致 state -1
         * 当 state = 0 的时候，就需要唤醒所有的线程，内部肯定有这样的方法
         */
        sync.releaseShared(1);
    }

    /**
     * Returns the current count.
     *
     * <p>This method is typically used for debugging and testing purposes.
     *
     * @return the current count
     */
    public long getCount() {
        return sync.getCount();
    }

    /**
     * Returns a string identifying this latch, as well as its state.
     * The state, in brackets, includes the String {@code "Count ="}
     * followed by the current count.
     *
     * @return a string identifying this latch, as well as its state
     */
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
