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
import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     *
     * 修订说明:这与该类以前依赖于AbstractQueuedSynchronizer的版本不同，主要是
     * 为了避免在取消竞赛期间保持中断状态的问题让用户感到意外。当前设计中的同步控制
     * 依赖于通过CAS更新的“状态”字段来跟踪完成，以及一个简单的Treiber堆栈来保持等
     * 待线程。
     *
     * 样式注释:像往常一样，我们绕过使用AtomicXFieldUpdaters的开销，而直接使用
     * 不安全的intrinsic。
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     *
     * 此任务的运行状态，最初为NEW。只有在方法set、setException和cancel中，
     * 运行状态才会转换为终端状态。在完成过程中，状态可能呈现完成(当设置结果时)或
     * 中断(仅当中断运行程序以满足取消时(true))的瞬态值。从这些中间状态到最终状态
     * 的转换使用更便宜的有序/惰性写，因为值是唯一的，不能进一步修改。
     */
    // 状态
    private volatile int state;
    /**
     * 初始化状态，FutureTask实例创建时候在构造函数中标记为此状态
     */
    private static final int NEW          = 0; // 初始化状态
    /**
     * 完成中状态，这个是中间状态，执行完成后设置outcome之前标记为此状态
     */
    private static final int COMPLETING   = 1; // 完成中状态
    /**
     * 正常执行完成，通过调用get()方法能够获取正确的计算结果
     */
    private static final int NORMAL       = 2; // 正常情况下的完成状态
    /**
     * 异常执行完成，通过调用get()方法会抛出包装后的ExecutionException异常
     */
    private static final int EXCEPTIONAL  = 3; // 异常情况下的完成状态
    /**
     * 表示任务还没执行就被取消了，非中断方式 属于最终状态
     */
    private static final int CANCELLED    = 4; // 取消状态
    /**
     * 表示任务还没执行就被取消了，中断中状态，执行线程实例Thread#interrupt()之前会标记为此状态
     */
    private static final int INTERRUPTING = 5; // 中断中状态
    /**
     * 表示任务还没执行就被取消了，已中断状态
     */
    private static final int INTERRUPTED  = 6; // 已中断状态

    /** The underlying callable; nulled out after running */
    // 底层的Callable实现，执行完毕后需要置为null
    private Callable<V> callable;
    /** The result to return or exception to throw from get()
     *
     * 输出结果，如果是正常执行完成，get()方法会返回此结果，如果是异常执行完成，get()方法会抛出
     * outcome包装为ExecutionException的异常
     *
     * */
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run()
     *
     *  真正的执行Callable对象的线程实例，运行期间通过CAS操作此线程实例
     *
     * */
    private volatile Thread runner;
    /** Treiber stack of waiting threads
     *
     * 等待线程集合，Treiber Stack实现
     *
     * Treiber Stack，中文翻译是驱动栈，听起来比较怪。实际上，Treiber Stack算法是R. Kent Treiber在其
     * 1986年的论文Systems Programming: Coping with Parallelism中首次提出，这种算法提供了一种可扩展
     * 的无锁栈，基于细粒度的并发原语CAS(Compare And Swap)实现。笔者并没有花时间去研读Treiber的论文，因为
     * 在Doug Lea大神参与编写的《Java Concurrency in Practice（Java并发编程实战）》中的第15.4.1小节中
     * 有简单分析非阻塞算法中的非阻塞栈。
     *
     *
     * 作者: Throwable
     * 链接: https://www.throwx.cn/2019/07/27/java-concurrency-executor-service/
     *
     * 在实现相同功能的前提下，非阻塞算法通常比基于锁的算法更加复杂。创建非阻塞算法的关键在于，找出如何将原子
     * 修改的范围缩小到单个变量上，同时还要维护数据的一致性。下面的ConcurrentStack是基于Java语言实现的
     * Treiber算法：
     *
     * public class ConcurrentStack<E> {
     *
     *     private AtomicReference<Node<E>> top = new AtomicReference<>();
     *
     *     public void push(E item) {
     *         Node<E> newHead = new Node<>(item);
     *         Node<E> oldHead;
     *         do {
     *             oldHead = top.get();
     *             newHead.next = oldHead;
     *         } while (!top.compareAndSet(oldHead, newHead));
     *     }
     *
     *     public E pop() {
     *         Node<E> oldHead;
     *         Node<E> newHead;
     *         do {
     *             oldHead = top.get();
     *             if (null == oldHead) {
     *                 return null;
     *             }
     *             newHead = oldHead.next;
     *         } while (!top.compareAndSet(oldHead, newHead));
     *         return oldHead.item;
     *     }
     *
     *     private static class Node<E> {
     *
     *         final E item;
     *         Node<E> next;
     *
     *         Node(E item) {
     *             this.item = item;
     *         }
     *     }
     * }
     *
     *
     * ConcurrentStack是一个栈，它是由Node元素构成的一个链表，其中栈顶作为根节点，并且每个元素都
     * 包含了一个值以及指向下一个元素的链接。push()方法创建一个新的节点，该节点的next域指向了当前的
     * 栈顶，然后通过CAS把这个新节点放入栈顶。如果在开始插入节点时，位于栈顶的节点没有发生变化，那么
     * CAS就会成功，如果栈顶节点发生变化（例如由于其他线程在当前线程开始之前插入或者移除了元素），那么
     * CAS就会失败，而push()方法会根据栈的当前状态来更新节点（其实就是while循环会进入下一轮），并且
     * 再次尝试。无论哪种情况，在CAS执行完成之后，栈仍然回处于一致的状态。
     *
     *
     *
     * */
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * 报告结果的方法，入参是awaitDone()方法返回的状态值
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        // 将异步任务执行结果赋值给x，此时FutureTask的成员变量outcome要么保存着
        // 异步任务正常执行的结果，要么保存着异步任务执行过程中抛出的异常
        Object x = outcome;
        // 【1】若异步任务正常执行结束，此时返回异步任务执行结果即可
        if (s == NORMAL)
            return (V)x;
        // 如果状态值大于等于CANCELLED(4)，则抛出CancellationException异常
        // 【2】若异步任务执行过程中，其他线程执行过cancel方法，此时抛出CancellationException异常
        if (s >= CANCELLED)
            throw new CancellationException();
        // 其他情况，实际上只剩下状态值为EXCEPTIONAL(3)，则基于outcome强转为Throwable类型，则包装
        // 成ExecutionException抛出
        // 【3】若异步任务执行过程中，抛出异常，此时将该异常转换成ExecutionException后，重新抛出。
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * 适配使用Callable类型任务的场景
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     *
     * 适配使用Runnable类型任务和已经提供了最终计算结果的场景
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * 判断是否取消状态，包括CANCELLED(4)、INTERRUPTING(5)、INTERRUPTED(6)三种状态
     *
     */
    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    /**
     * 判断是否已经完成，这里只是简单判断状态值不为NEW(0)，原因是所有的中间状态都是十分短暂的
     */
    public boolean isDone() {
        return state != NEW;
    }

    /**
     * cancel()方法只能够中断状态为NEW(0)的线程，并且由于线程只在某些特殊情况下（例如阻塞
     * 在同步代码块或者同步方法中阻塞在Object#wait()方法、主动判断线程的中断状态等等）才能
     * 响应中断，所以需要思考这个方法是否可以达到预想的目的
     *
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this
     * task should be interrupted; otherwise, in-progress tasks are allowed
     * to complete
     * @return
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 状态必须为NEW(0)
        // 如果mayInterruptIfRunning为true，则把状态通过CAS更新为INTERRUPTING(5)
        // 如果mayInterruptIfRunning为false，则把状态通过CAS更新为CANCELLED(4)
        // 如果状态不为NEW(0)或者CAS更新失败，直接返回false，说明任务已经执行到set()或
        // setException()，无法取消

        // 【1】判断当前任务状态，若state == NEW时根据mayInterruptIfRunning参数值给当前任务状态赋值为INTERRUPTING或CANCELLED
        // a）当任务状态不为NEW时，说明异步任务已经完成，或抛出异常，或已经被取消，此时直接返回false。
        // TODO 【问题】此时若state = COMPLETING呢？此时为何也直接返回false，而不能发出中断异步任务线程的中断信号呢？？
        // TODO 仅仅因为COMPLETING是一个瞬时态吗？？？
        // b）当前仅当任务状态为NEW时，此时若mayInterruptIfRunning为true，此时任务状态赋值为INTERRUPTING；否则赋值为CANCELLED。

        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;

        // 如果线程能取消

        try {    // in case call to interrupt throws exception
            // mayInterruptIfRunning为true，调用执行任务的线程实例的Thread#interrupt()进行中断，
            // 更新最终状态为INTERRUPTED(6)
            // 【2】如果mayInterruptIfRunning为true，此时中断执行异步任务的线程runner（还记得执行异步任务时就把执行异步任务的线程就赋值给了runner成员变量吗）

            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        // 中断执行异步任务的线程runner，进行中断，这里说一下，你调用了中断，但是线程不一定响应你的中断
                        t.interrupt();
                } finally { // final state
                    // 最后任务状态赋值为INTERRUPTED
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            // 完成后的通知方法
            // 【3】不管mayInterruptIfRunning为true还是false，此时都要调用finishCompletion方法唤醒阻塞的获取异步任务结果的线程并移除线程等待链表节点
            finishCompletion();
        }
        return true;
    }

    /**
     * @throws CancellationException {@inheritDoc}
     * 获取执行结果
     *
     * 说明：FutureTask 通过get()方法获取任务执行结果。如果任务处于未完成的状态(state <= COMPLETING)，
     * 就调用awaitDone方法(后面单独讲解)等待任务完成。任务完成后，通过report方法获取
     * 执行结果或抛出执行期间的异常。
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        // 如果状态小于等于COMPLETING(1)，也就是COMPLETING(1)和NEW(0)，那么就需要等待任务完成
        // 【1】若任务状态<=COMPLETING，说明任务正在执行过程中，此时可能正常结束，也可能遇到异常
        if (s <= COMPLETING)
            // 注意这里调用awaitDone方法的参数为永久阻塞参数，也就是没有超时期限，返回最新的状态值
            s = awaitDone(false, 0L);
        // 根据状态值报告结果
        // 【2】最后根据任务状态来返回任务执行结果，此时有三种情况：1）任务正常执行；2）任务执行异常；3）任务被取消
        return report(s);
    }

    /**
     * 获取执行结果 - 带超时的阻塞
     *
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        // 如果状态小于等于COMPLETING(1)，也就是COMPLETING(1)和NEW(0)，那么就需要等待任务完成
        // 注意这里调用awaitDone方法的参数为带超时上限的阻塞参数
        // 如果超过了指定的等待期限（注意会把时间转化为纳秒），返回的最新状态依然为COMPLETING(1)或者NEW(0)，
        // 那么抛出TimeoutException异常
        if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        // 根据状态值报告结果
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     *
     *钩子方法，可以通过子类扩展此方法，方法回调的时机是任务已经执行完毕，阻塞获取结果的线程被唤醒之后
     *
     */
    protected void done() { }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * 运行任务，如果任务状态为NEW状态，则利用CAS修改为当前线程。执行完毕调用set(result)方法
     * 设置执行结果。
     *
     * 正常执行完毕的情况下设置执行结果
     *
     * @param v the value
     */
    protected void set(V v) {
        // CAS更新状态state，由NEW(0)更新为COMPLETING(1)
        // 【1】调用UNSAFE的CAS方法判断任务当前状态是否为NEW，若为NEW，则设置任务状态为COMPLETING
        // 【思考】此时任务不能被多线程并发执行，什么情况下会导致任务状态不为NEW？
        // 答案是只有在调用了cancel方法的时候，此时任务状态不为NEW，此时什么都不需要做，
        // 因此需要调用CAS方法来做判断任务状态是否为NEW
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            // 最终执行结果值更新到outcome中 【2】将任务执行结果赋值给成员变量outcome
            outcome = v;
            // 设置最终状态state = NORMAL(2)，意味着任务最终正常执行完毕
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            // 完成后的通知方法
            // 【4】调用任务执行完成方法，此时会唤醒阻塞的线程，调用done()方法和清空等待线程链表等
            finishCompletion(); //执行完毕，唤醒等待线程
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        // CAS更新状态state，由NEW(0)更新为COMPLETING(1)
        // 【1】调用UNSAFE的CAS方法判断任务当前状态是否为NEW，若为NEW，则设置任务状态为COMPLETING
        // 【思考】此时任务不能被多线程并发执行，什么情况下会导致任务状态不为NEW？
        // 答案是只有在调用了cancel方法的时候，此时任务状态不为NEW，此时什么都不需要做，
        // 因此需要调用CAS方法来做判断任务状态是否为NEW
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            // 设置异常实例到outcome属性中
            outcome = t;
            // 设置最终状态state = EXCEPTIONAL(3)，意味着任务最终异常执行完毕
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            // 完成后的通知方法
            // 【4】调用任务执行完成方法，此时会唤醒阻塞的线程，调用done()方法和清空等待线程链表等
            finishCompletion();
        }
    }

    public void run() {
        //新建任务，CAS替换runner为当前线程
        // 如果状态不为NEW(0)或者CAS(null,当前线程实例)更新runner-真正的执行
        // Callable对象的线程实例失败，那么直接返回，不执行任务

        // 【1】,为了防止多线程并发执行异步任务，这里需要判断线程满不满足执行异步任务的条件，有以下三种情况：
        // 1）若任务状态state为NEW且runner为null，说明还未有线程执行过异步任务，此时满足执行异步任务的条件，
        // 此时同时调用CAS方法为成员变量runner设置当前线程的值；
        // 2）若任务状态state为NEW且runner不为null，任务状态虽为NEW但runner不为null，说明有线程正在执行异步任务，
        // 此时不满足执行异步任务的条件，直接返回；
        // 1）若任务状态state不为NEW，此时不管runner是否为null，说明已经有线程执行过异步任务，此时没必要再重新
        // 执行一次异步任务，此时不满足执行异步任务的条件；
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            // 将用户的线程赋值给局部变量
            // 拿到之前构造函数传进来的callable实现类对象，其call方法封装了异步任务执行的逻辑
            Callable<V> c = callable;
            // 判断任务不能为空，二次校验状态必须为NEW(0)    // 若任务还是新建状态的话，那么就调用异步任务
            if (c != null && state == NEW) {
                V result; // 异步任务执行结果
                boolean ran;   // 异步任务执行成功还是始遍标志
                try {
                    // 执行用户的call方法，这个方法会有返回值是用户的返回值
                    // 调用任务实例Callable#call()方法，正常情况下的执行完毕，没有抛出异常，则记录执行结果
                    result = c.call();
                    // 如果调用用户代码没有出错，那么设置标志位为true
                    ran = true;
                } catch (Throwable ex) {
                    // 异常情况下的执行完毕，执行结果记录为null
                    result = null;
                    // 记录异常执行完毕
                    ran = false;
                    // 设置异常实例
                    setException(ex);
                }
                // 如果调用用户代码没有出错，标志位为true，那么将结果保存起来
                // 既然都设置了，那么一定能get 这就能获取到现场返回值了呀
                if (ran)
                    set(result);//设置执行结果
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            // runner更新为null，防止并发执行run()方法
            // 异步任务正在执行过程中，runner一直是非空的，防止并发调用run方法，前面有调用cas方法做判断的
            // 在异步任务执行完后，不管是正常结束还是异常结束，此时设置runner为null
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            // 记录新的状态值，因为run()方法执行的时候，状态值有可能被其他方法更新了
            // 线程执行异步任务后的任务状态
            int s = state;
            // 如果状态大于中断状态
            if (s >= INTERRUPTING)
                // 处理run()方法执行期间调用了cancel(true)方法的情况
                // 【4】如果执行了cancel(true)方法，此时满足条件，
                // 此时调用handlePossibleCancellationInterrupt方法处理中断
                handlePossibleCancellationInterrupt(s); //处理中断逻辑
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * 执行任务并且重置状态
     *
     * 由于没有执行set()方法设置执行结果，这个方法除了执行过程中抛出异常或者主动取消
     * 会到导致state由NEW更变为其他值，正常执行完毕一个任务之后，state是保持为NEW不变
     *
     * runAndReset()方法保证了在任务正常执行完成之后返回true，此时FutureTask的状态state
     * 保持为NEW，由于没有调用set()方法，也就是没有调用finishCompletion()方法，它内部持有
     * 的Callable任务引用不会置为null，等待获取结果的线程集合也不会解除阻塞。这种设计方案专门
     * 针对可以周期性重复执行的任务。异常执行情况和取消的情况导致的最终结果和run()方法是一致的
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        // 如果状态不为NEW(0)或者CAS(null,当前线程实例)更新runner-真正的执行
        // Callable对象的线程实例失败，那么直接返回false，不执行任务
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    // 这里会忽略执行结果，只记录是否正常执行
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    // 记录执行异常结果
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        // 正常情况下的执行完毕，ran会更新为true，state此时也保持为NEW，这个时候方法返回true
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     *
     * // 处理run()方法执行期间调用了cancel(true)方法的情况
     * // 这里还没分析cancel()方法，但是可以提前告知：它会先把状态更新为INTERRUPTING，再进行
     *    线程中断，最后更新状态为INTERRUPTED
     * // 所以如果发现当前状态为INTERRUPTING，当前线程需要让出CPU控制权等待到状态更变为INTERRUPTED
     *    即可，这个时间应该十分短暂
     *
     *
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        //在中断者中断线程之前可能会延迟，所以我们只需要让出CPU时间片自旋等待
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     *
     * 等待获取结果的线程节点（集合），实际上是一个单链表，实现了一个非阻塞栈
     */
    static final class WaitNode {
        // 记录等待线程实例
        volatile Thread thread;
        // 指向下一个节点的引用
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     *
     * 著作权归https://pdai.tech所有。
     * 链接：https://www.pdai.tech/md/java/thread/java-thread-x-juc-executor-FutureTask.html
     *
     * 首先利用cas修改state状态为COMPLETING，设置返回结果，然后使用 lazySet(UNSAFE.putOrderedInt)的方式设置state状态为NORMAL。
     * 结果设置完毕后，调用finishCompletion()方法唤醒等待线程
     *
     * 完成任务后的通知方法，最要作用是移除和唤醒所有的等待结果线程，调用钩子方法
     * done()和设置任务实例callable为null
     *
     * https://www.javanorth.cn/2021/06/20/FutureTask/
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        // 遍历栈，终止条件是下一个元素为null
        // 取出等待线程链表头节点，判断头节点是否为null
        // 1）若线程链表头节点不为空，此时以“后进先出”的顺序（栈）移除等待的线程WaitNode节点
        // 2）若线程链表头节点为空，说明还没有线程调用Future.get()方法来获取任务执行结果，固然不用移除

        for (WaitNode q; (q = waiters) != null;) {
            //移除等待线程   // 调用UNSAFE的CAS方法将成员变量waiters设置为空
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                // 遍历栈中的所有节点，唤醒节点中的线程，这是一个十分常规的遍历单链表的方法，注意几点：
                // 1. 使用LockSupport.unpark()唤醒线程，因为后面会分析，线程阻塞等待的时候使用的是LockSupport.park()方法
                // 2. 断开链表节点的时候后继节点需要置为null，这样游离节点才能更容易被JVM回收
                for (;;) {//自旋遍历等待线程
                    // 取出WaitNode节点的线程
                    Thread t = q.thread;
                    // 若取出的线程不为null，则将该WaitNode节点线程置空，且唤醒正在阻塞的该线程
                    if (t != null) {
                        q.thread = null;
                        //【重要】唤醒正在阻塞的该线程
                        LockSupport.unpark(t);//唤醒等待线程
                    }
                    // 继续取得下一个WaitNode线程节点
                    WaitNode next = q.next;
                    // 若没有下一个WaitNode线程节点，说明已经将所有等待的线程唤醒，此时跳出for循环
                    if (next == null)
                        break;
                    // 将已经移除的线程WaitNode节点的next指针置空，此时好被垃圾回收
                    q.next = null; // unlink to help gc
                    // 再把下一个WaitNode线程节点置为当前线程WaitNode头节点
                    q = next;
                }
                break;
            }
        }

        //任务完成后调用函数，自定义扩展
        // 不管任务正常执行还是抛出异常，都会调用done方法
        done();

        // 置任务实例callable为null，从而减少JVM memory footprint（这个东西有兴趣可以自行扩展阅读）
        // 因为异步任务已经执行完且结果已经保存到outcome中，因此此时可以将callable对象置空了
        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * 等待任务完成，区分永久阻塞等待和带超时上限的阻塞等待两种场景
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        // 计算超时结束时间
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        // 线程链表头节点
        WaitNode q = null;
        boolean queued = false;// 是否入队
        for (;;) { //自旋
            // 如果当前获取任务执行结果的线程被中断，此时移除该线程WaitNode链表节点，并抛出InterruptedException
            if (Thread.interrupted()) { //获取并清除中断状态
                removeWaiter(q); //移除等待WaitNode
                throw new InterruptedException();
            }

            int s = state;
            // 如果状态值已经大于COMPLETING(1)，说明任务已经执行完毕，可以直接返回，如果
            // 等待节点已经初始化，则置空其线程实例引用，便于GC回收
            // 【5】如果任务状态>COMPLETING，此时返回任务执行结果，其中此时任务可能正常结束（NORMAL）,可能抛出异常（EXCEPTIONAL）
            // 或任务被取消（CANCELLED，INTERRUPTING或INTERRUPTED状态的一种）
            if (s > COMPLETING) {
                // 【问】此时将当前WaitNode节点的线程置空，其中在任务结束时也会调用finishCompletion将WaitNode节点的thread置空，
                // 这里为什么又要再调用一次q.thread = null;呢？
                // 【答】因为若很多线程来获取任务执行结果，在任务执行完的那一刻，此时获取任务的线程要么已经在线程等待链表中，要么
                // 此时还是一个孤立的WaitNode节点。在线程等待链表中的的所有WaitNode节点将由finishCompletion来移除（同时唤醒）所有
                // 等待的WaitNode节点，以便垃圾回收；而孤立的线程WaitNode节点此时还未阻塞，因此不需要被唤醒，此时只要把其属性置为
                // null，然后其有没有被谁引用，因此可以被GC。
                if (q != null)
                    q.thread = null; //置空等待节点的线程
                // 【重要】返回任务执行结果
                return s;
            }
            // 【4】若任务状态为COMPLETING，此时说明任务正在执行过程中，此时获取任务结果的线程需让出CPU执行时间片段
            else if (s == COMPLETING) // cannot time out yet
                // 状态值等于COMPLETING(1)，说明任务执行到达尾声，在执行set()或者setException()，
                // 只需让出CPU控制权等待完成即可等待下一轮循环重试即可
                Thread.yield();
                // 【1】若当前线程还没有进入线程等待链表的WaitNode节点，此时新建一个WaitNode节点，并把当前线程赋值给WaitNode节点的thread属性
            else if (q == null)
                // 等待节点尚未初始化，如果设置了超时期限并且超时时间小于等于0，则直接返回状态
                // 并且终止等待，说明已经超时了
                // 这里的逻辑属于先行校验，如果命中了就不用进行超时阻塞
                q = new WaitNode();  // 初始化等待节点
                // 【2】若当前线程等待节点还未入线程等待队列，此时加入到该线程等待队列的头部
            else if (!queued)
                //CAS修改waiter
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
                // 若有超时设置，那么处理超时获取任务结果的逻辑

            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    //超时，移除等待节点
                    removeWaiter(q);
                    return state;
                }
                // [核心] todo:如果状态为NEW(0)，则进行超时阻塞，阻塞的是当前的线程
                LockSupport.parkNanos(this, nanos);
            }
            // 【3】若没有超时设置，此时直接阻塞当前线程
            else
                // [核心] todo:这种就是最后一个if分支，就是不命中任何条件的永久阻塞，阻塞的是当前的线程
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     *
     * 移除等待节点，这个方法有两次使用的地方：
     * 1. 获取结果的线程进行阻塞等待的时候被中断的场景（处理中断）
     * 2. 获取结果的线程采用带超时的阻塞等待并且在进行阻塞之前已经判断到超时时间已经到期（处理不小心进栈的无效节点）
     *   实际上，这个方法就是Treiber Stack算法的出栈操作
     *
     */
    private void removeWaiter(WaitNode node) {
        // 只有目标等待节点不空时候才处理
        if (node != null) {
            // 目标等待节点的线程引用置为空
            node.thread = null;
            // 这里循环标记用于因为此方法执行的竞态条件需要重试的起点
            retry:
            for (;;) {          // restart on removeWaiter race
                // 遍历的终止条件：q != null，由于变化条件是q = s，并且每轮循环s = q.next，
                // 因此终止条件是栈节点的后继节点next为null
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    // 第一轮循环，q其实就是栈顶节点，栈顶节点的后继节点为s，获取说q是当前需要
                    // 处理的节点，s是其后继节点
                    s = q.next;
                    // 如果当前节点时有效（持有的线程引用非空）的节点，则前驱节点pred更新为当前
                    // 节点，进行下一轮遍历
                    if (q.thread != null)
                        pred = q;
                    // 如果当前节点已经无效，并且它存在前驱节点，那么前驱节点pred的后继节点引用连接
                        // 到当前节点的后继节点s，实现当前节点的删除
                    // 这个是单链表删除中间某一个节点的常规操作
                    else if (pred != null) {
                        pred.next = s;
                        // 如果在当前节点已经无效，并且它存在前驱节点，但是前驱节点二次判断为无效，
                        // 说明出现了竞态，需要重新进行栈waiters的遍历
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    // 当前节点已经无效，它不存在前驱节点，则直接把当前节点的后继节点s通过CAS更新栈顶节点
                    // 类比前面分析过的ConcurrentStack的pop()方法，这里的q就是oldHead，s就是newHead。
                    // CAS更新失败说明存在竞态，则需要重新进行栈waiters的遍历
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                          q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    // 对应成员变量state的偏移地址
    private static final long stateOffset;
    // 对应成员变量runner的偏移地址
    private static final long runnerOffset;
    // 对应成员变量waiters的偏移地址
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
