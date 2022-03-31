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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronization aid that allows a set of threads to all wait for
 * each other to reach a common barrier point.  CyclicBarriers are
 * useful in programs involving a fixed sized party of threads that
 * must occasionally wait for each other. The barrier is called
 * <em>cyclic</em> because it can be re-used after the waiting threads
 * are released.
 *
 * 一种同步辅助，允许一组线程互相等待到达一个共同的障碍点。CyclicBarriers在涉及
 * 固定大小的线程群的程序中很有用，这些线程群必须偶尔相互等待。这个屏障被称为循环，
 * 因为它可以在等待的线程被释放后被重用。
 *
 * <p>A {@code CyclicBarrier} supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released.
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 *
 * A {@code CyclicBarrier}支持一个可选的{@link Runnable}命令，该命令在
 * 每个barrier点上运行一次，在队列中的最后一个线程到达之后，但在任何线程被释放
 * 之前。这个barrier动作用于在任何一方继续之前更新共享状态。
 *
 * <p><b>Sample usage:</b> Here is an example of using a barrier in a
 * parallel decomposition design:
 *
 * 下面是一个在并行分解设计中使用barrier的例子:
 *
 *  <pre> {@code
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *
 *   class Worker implements Runnable {
 *     int myRow;
 *     Worker(int row) { myRow = row; }
 *     public void run() {
 *       while (!done()) {
 *         processRow(myRow);
 *
 *         try {
 *           barrier.await();
 *         } catch (InterruptedException ex) {
 *           return;
 *         } catch (BrokenBarrierException ex) {
 *           return;
 *         }
 *       }
 *     }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     Runnable barrierAction =
 *       new Runnable() { public void run() { mergeRows(...); }};
 *     barrier = new CyclicBarrier(N, barrierAction);
 *
 *     List<Thread> threads = new ArrayList<Thread>(N);
 *     for (int i = 0; i < N; i++) {
 *       Thread thread = new Thread(new Worker(i));
 *       threads.add(thread);
 *       thread.start();
 *     }
 *
 *     // wait until done
 *     for (Thread thread : threads)
 *       thread.join();
 *   }
 * }}</pre>
 *
 * Here, each worker thread processes a row of the matrix then waits at the
 * barrier until all rows have been processed. When all rows are processed
 * the supplied {@link Runnable} barrier action is executed and merges the
 * rows. If the merger
 * determines that a solution has been found then {@code done()} will return
 * {@code true} and each worker will terminate.
 *
 * 在这里，每个工作线程处理矩阵的一行，然后在barrier处等待，直到所有的行都被处理完毕。
 * 当所有的行都被处理后，{@link Runnable} barrier动作被执行并合并行。如果合并确定
 * 找到了解决方案，{@code done()}将返回{@code true}，每个worker将终止。
 *
 * <p>If the barrier action does not rely on the parties being suspended when
 * it is executed, then any of the threads in the party could execute that
 * action when it is released. To facilitate this, each invocation of
 * {@link #await} returns the arrival index of that thread at the barrier.
 * You can then choose which thread should execute the barrier action, for
 * example:
 *
 * 如果barrier操作在执行时不依赖于被挂起的线程，那么该操作被释放时，该线程中的任何线程
 * 都可以执行该操作。为了方便这一点，每次调用{@link #await}都会返回该线程在barrier的
 * 到达索引。然后你可以选择哪个线程应该执行barrier动作，
 *
 *  <pre> {@code
 * if (barrier.await() == 0) {
 *   // log the completion of this iteration
 * }}</pre>
 *
 * <p>The {@code CyclicBarrier} uses an all-or-none breakage model
 * for failed synchronization attempts: If a thread leaves a barrier
 * point prematurely because of interruption, failure, or timeout, all
 * other threads waiting at that barrier point will also leave
 * abnormally via {@link BrokenBarrierException} (or
 * {@link InterruptedException} if they too were interrupted at about
 * the same time).
 *
 * {@code CyclicBarrier}对失败的同步尝试使用全或全中断模型:如果一个线程因为中断、
 * 失败或超时而提前离开一个barrier点，所有在该barrier点等待的其他线程也会通过
 * {@link BrokenBarrierException}(或者{@link InterruptedException}，如果
 * 它们也在同一时间被中断的话)异常离开。
 *
 * <p>Memory consistency effects: Actions in a thread prior to calling
 * {@code await()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions that are part of the barrier action, which in turn
 * <i>happen-before</i> actions following a successful return from the
 * corresponding {@code await()} in other threads.
 *
 * 内存一致性影响:线程中调用{@code await()} happen-before之前的动作是barrier动作的一部分，
 * 在其他线程中相应的{@code await()}成功返回后 happens -before动作。
 *
 * @since 1.5
 * @see CountDownLatch
 *
 * @author Doug Lea
 */
public class CyclicBarrier {
    /**
     * Each use of the barrier is represented as a generation instance.
     * The generation changes whenever the barrier is tripped, or
     * is reset. There can be many generations associated with threads
     * using the barrier - due to the non-deterministic way the lock
     * may be allocated to waiting threads - but only one of these
     * can be active at a time (the one to which {@code count} applies)
     * and all the rest are either broken or tripped.
     * There need not be an active generation if there has been a break
     * but no subsequent reset.
     *
     * barrier的每次使用都表示为一个代实例。每当障碍被触发或重置时，生成都会发生变化。
     * 可以有许多代与线程相关的使用障碍,由于不确定的方式锁可能被分配到等待线程,但一次
     * 只能激活一个(一个{@code数}适用)和所有其他的 broken or tripped.
     *
     * 这个是代，为什么叫代呢?
     *
     * 因为这个是可循环重复执行的。比如 10个士兵完成了集结，然后可以开启下一轮的循环。
     * broken 代表是否 完成一个批次
     */
    private static class Generation {
        // Generation类有一个属性broken，用来表示当前屏障是否被损坏
        boolean broken = false;
    }

    /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /** The number of parties */
    // 参与的线程数量
    private final int parties;
    /* The command to run when tripped */
    // 由最后一个进入 barrier 的线程执行的操作
    private final Runnable barrierCommand;
    /** The current generation */
    private Generation generation = new Generation();

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each generation.  It is reset to parties on each new
     * generation or when broken.
     *
     * 还有很多人在等着。Counts down from parties to 0 on each generation。
     * 它会在每一代新成员或被破坏时重新设定。
     *
     * 还有多少线程还没有来到,正在等待进入屏障的线程数量
     */
    private int count;

    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    private void nextGeneration() {
        // signal completion of last generation
        // 将等待队列中的线程全部唤醒
        trip.signalAll();
        // set up next generation
        // 重新设置 总共要等待的线程格式
        count = parties;
        // 重新生成下一代
        generation = new Generation();
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     */
    private void breakBarrier() {
        // 设置栅栏坏掉的标示为true
        generation.broken = true;
        // 把限制的人数给count
        count = parties;
        // 等待的条件队列中的线程 全部唤醒
        trip.signalAll();
    }

    /**
     * Main barrier code, covering the various policies.
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            // 如果 栅栏坏了 抛出 BrokenBarrierException
            // 线程一进来，如果这一代Generation已经broken，此时抛出BrokenBarrierException
            if (g.broken)
                throw new BrokenBarrierException();

            // 有没有中断呀
            if (Thread.interrupted()) {
                // 如果中断了，那么先去破坏栅栏，然后抛出中断异常
                breakBarrier();
                throw new InterruptedException();
            }

            // 还剩下多少线程，每次调用await 就减去一个
            int index = --count;
            if (index == 0) {  // tripped  最后一个线程到达
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        // 方法级别调用run方法
                        command.run();
                    // 设置运行成功
                    ranAction = true;
                    // 启动下一轮barrier
                    nextGeneration();
                    // 介绍
                    return 0;
                } finally {
                    // 如果出错了，那么就那么先去破坏栅栏
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // 运行到这里，就说明等待一切正常，而且不是最后的线程

            // loop until tripped, broken, interrupted, or timed out
            // 为了防止被意外的唤醒，这里做了一个自旋
            // 要么是tripped满足栅栏开启条件了
            // 要么是 中断了
            // 要么是超时了
            for (;;) {
                try {
                    // 要不要做超时
                    if (!timed)
                        // 不做超时，那么就一直阻塞
                        trip.await();
                    else if (nanos > 0L)
                        // 如果有时间限制，那么就调用超时等待
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    // 如果在等待的时候，有线程被中断了

                    // 如果栅栏已经破坏了，那么就破坏栅栏 然后抛出异常
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // 否则就是抛出异常

                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }

                // 如果栅栏坏了 那么抛出异常
                if (g.broken)
                    throw new BrokenBarrierException();

                // 如果不相等，那么重置index
                if (g != generation)
                    return index;

                // 如果超时，那么抛出超时异常
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @return the arrival index of the current thread, where index
     *         {@code getParties() - 1} indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     *
     *         如果当前线程在等待时被中断
     *
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was
     *         broken when {@code await} was called, or the barrier
     *         action (if present) failed due to an exception
     *
     *         如果另一个线程在当前线程等待时被中断或超时，或者barrier被重置，
     *         或者在调用{@code await}时barrier被打破，或者barrier动作(如果存在)
     *         由于异常而失败
     *
     *  这里为什么抛出2个异常？
     *   因为你执行等待的时候，可能被中断了，如果线程被中断了，那么最后总的线程等待数，就达不到
     *   栅栏的要求，那么改怎么办呢？
     *
     *   当抛出 BrokenBarrierException 这个异常的时候，就表示这个栅栏已经损坏了。有可能永远
     *   都达不到打开栅栏的要求了。 所以当抛出这个异常的时候所有等待的线程都不会在等待了。因为
     *   达不到等待的要求了。这点要特别的注意。
     *
     *         TODO 【QUESTION24】这里列出了说明情况下会抛出BrokenBarrierException。假如有n个线程正在await，此时有以下几种情况：
     *                           1，await的其中一个线程timeout，此时其他线程会抛出BrokenBarrierException吗？
     *                           2，await的其中一个线程被interrupt，此时其他线程会抛出BrokenBarrierException吗？
     *                           3，如果调用了reset，此时其他线程会抛出BrokenBarrierException吗？
     *
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier, or the specified waiting time elapses.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>The specified timeout elapses; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown. If the time is less than or equal to zero, the
     * method will not wait at all.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while
     * waiting, then all other waiting threads will throw {@link
     * BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @param timeout the time to wait for the barrier
     * @param unit the time unit of the timeout parameter
     * @return the arrival index of the current thread, where index
     *         {@code getParties() - 1} indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws TimeoutException if the specified timeout elapses.
     *         In this case the barrier will be broken.
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was broken
     *         when {@code await} was called, or the barrier action (if
     *         present) failed due to an exception
     */
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
               BrokenBarrierException,
               TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     *         barrier due to interruption or timeout since
     *         construction or the last reset, or a barrier action
     *         failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
