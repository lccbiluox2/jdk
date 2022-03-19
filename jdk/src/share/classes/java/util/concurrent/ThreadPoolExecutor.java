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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * 一个{@link ExecutorService}，它使用可能的几个线程池来执行每个提交的任务，
 * 通常使用{@link Executors}工厂方法来配置。
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each {@code ThreadPoolExecutor} also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 * 线程池解决了两个不同的问题:它们通常在执行大量异步任务时提供改进的性能，因为减少了
 * 每个任务的调用开销，并且它们提供了一种方法来限制和管理执行任务集合时消耗的资源(包括线程)。
 * 每个{@code ThreadPoolExecutor}还维护一些基本的统计信息，比如完成任务的数量。
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * 为了在广泛的上下文中发挥作用，这个类提供了许多可调参数和可扩展性挂钩。然而，程序员
 * 被鼓励使用更方便的{@link Executors}工厂方法
 *    {@link Executors#newCachedThreadPool}(无界线程池，具有自动线程回收)，
 *    {@link Executors#newFixedThreadPool}(固定大小的线程池)和
 *    {@link Executors#newSingleThreadExecutor}(单个后台线程)，
 * 为最常见的使用场景预先配置设置。否则，在手动配置和调优该类时，请使用以下指南:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt> 核心线程数
 *
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * A {@code ThreadPoolExecutor}将根据corePoolSize(见{@link #getCorePoolSize})和
 * maximumPoolSize(见{@link #getMaximumPoolSize})设置的边界自动调整池的大小
 * (见{@link #getCorePoolSize})。
 *
 * When a new task is submitted in method {@link #execute(Runnable)},
 * and fewer than corePoolSize threads are running, a new thread is
 * created to handle the request, even if other worker threads are
 * idle.  If there are more than corePoolSize but less than
 * maximumPoolSize threads running, a new thread will be created only
 * if the queue is full.  By setting corePoolSize and maximumPoolSize
 * the same, you create a fixed-size thread pool. By setting
 * maximumPoolSize to an essentially unbounded value such as {@code
 * Integer.MAX_VALUE}, you allow the pool to accommodate an arbitrary
 * number of concurrent tasks. Most typically, core and maximum pool
 * sizes are set only upon construction, but they may also be changed
 * dynamically using {@link #setCorePoolSize} and {@link
 * #setMaximumPoolSize}. </dd>
 *
 * 当一个新任务以方法{@link #execute(Runnable)}提交，并且运行的线程少于corePoolSize时，
 * 一个新线程被创建来处理该请求，即使其他工作线程空闲。
 *
 * 如果有大于corePoolSize但小于maximumPoolSize的线程正在运行，则只有当队列已满时才会创建一个新的线程。
 *
 * 通过将corePoolSize和maximumPoolSize设置为相同，可以创建一个固定大小的线程池。
 *
 * 通过将maximumPoolSize设置为一个本质上无界的值，例如{@code Integer。MAX_VALUE}，允许池容纳
 * 任意数量的并发任务。最典型的情况是，核心池和最大池大小仅在构建时设置，但它们也可以使用
 * {@link #setCorePoolSize}和{@link #setMaximumPoolSize}动态更改。< / dd >
 *
 * <dt>On-demand construction</dt>  按需构建
 *
 * <dd>By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link
 * #prestartAllCoreThreads}.  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * 默认情况下，即使是核心线程也只有在新任务到达时才被初始创建和启动，但是可以使用
 * {@link #prestartCoreThread}或{@link #prestartAllCoreThreads}方法动态覆盖。
 * 如果使用非空队列构造池，您可能希望预先启动线程。
 *
 * <dt>Creating new threads</dt>
 * 创建新的线程
 *
 * <dd>New threads are created using a {@link ThreadFactory}.  If not
 * otherwise specified, a {@link Executors#defaultThreadFactory} is
 * used, that creates threads to all be in the same {@link
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a {@code ThreadFactory} fails to create a thread when asked
 * by returning null from {@code newThread}, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" {@code RuntimePermission}. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * 使用{@link ThreadFactory}创建新线程。如果没有指定，则使用一个{@link Executors#defaultThreadFactory}，
 * 它创建的线程都处于相同的{@link ThreadGroup}和相同的{@code NORM_PRIORITY}优先级和非守护状态。
 * 通过提供一个不同的ThreadFactory，你可以改变线程的名称、线程组、优先级、守护进程状态等。
 *
 * 如果{@code ThreadFactory}在{@code newThread}中返回null请求创建线程失败，执行器将继续执行，
 * 但可能无法执行任何任务。线程应该拥有“modifyThread”{@code RuntimePermission}。如果工作线程
 * 或者其他线程池使用不具备这种许可,服务可能退化:配置更改可能不会及时生效,和关闭池可能留在终止是
 * 可能的但未完成的状态。
 *
 * <dt>Keep-alive times</dt>
 *  存活时间
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime(TimeUnit)}).
 * This provides a means of reducing resource consumption when the
 * pool is not being actively used. If the pool becomes more active
 * later, new threads will be constructed. This parameter can also be
 * changed dynamically using method {@link #setKeepAliveTime(long,
 * TimeUnit)}.  Using a value of {@code Long.MAX_VALUE} {@link
 * TimeUnit#NANOSECONDS} effectively disables idle threads from ever
 * terminating prior to shut down. By default, the keep-alive policy
 * applies only when there are more than corePoolSize threads. But
 * method {@link #allowCoreThreadTimeOut(boolean)} can be used to
 * apply this time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * 如果当前池中有超过corePoolSize的线程，超过keepAliveTime(参见{@link #getKeepAliveTime(TimeUnit)})
 * 的空闲线程将被终止。这提供了一种方法，可以在池没有被积极使用时减少资源消耗。如果以后池变得更活跃，
 * 则会构造新的线程。这个参数也可以通过{@link #setKeepAliveTime(long, TimeUnit)}方法来动态修改。
 * 使用一个值{@code Long。MAX_VALUE} {@link TimeUnit#NANOSECONDS}有效地禁止空闲线程在关闭之前终止。
 * 缺省情况下，只有当corePoolSize以上的线程时，该策略才生效。但是，
 * 方法{@link #allowCoreThreadTimeOut(boolean)}也可以用于将这个超时策略应用到核心线程，
 * 只要keepAliveTime的值不为零。
 *
 * <dt>Queuing</dt>  队列
 *
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 * 任何{@link BlockingQueue}可以用来传输和保存提交的任务。这个队列的使用与池的大小调整相互作用:
 *
 * <ul>
 *
 * <li> If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.</li>
 *
 * 如果运行的线程少于corePoolSize, Executor总是倾向于添加一个新线程而不是排队
 *
 * <li> If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.</li>
 *
 * 如果corePoolSize或更多的线程正在运行，Executor总是倾向于让请求排队而不是添加一个新线程。
 *
 * <li> If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.</li>
 *
 * 如果一个请求不能被排队，一个新的线程被创建，除非它超过了maximumPoolSize，在这种情况下，任务将被拒绝
 *
 * </ul>
 *
 * There are three general strategies for queuing: 有三种一般的排队策略:
 * <ol>
 *
 * <li> <em> Direct handoffs.</em> A good default choice for a work
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.  </li>
 *
 * 直接交接。工作队列的一个很好的默认选择是{@link SynchronousQueue}，它将任务移交给线程而
 * 不持有它们。在这里，如果没有立即可用的线程来运行任务，尝试对任务进行排队将失败，因此将构造一个新线程。
 * 当处理可能具有内部依赖关系的请求集时，此策略可以避免锁定。直接切换通常需要无限制的maximumpoolsize，
 * 以避免拒绝新提交的任务。反过来，当命令的平均到达速度超过处理速度时，就有可能出现无限制的线程增长。
 *
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for
 * example a {@link LinkedBlockingQueue} without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.  </li>
 *
 * 无界队列。使用一个无界队列(例如一个没有预定义容量的{@link LinkedBlockingQueue})将导致在
 * 所有corePoolSize线程都繁忙时，新的任务在队列中等待。因此，只会创建corePoolSize线程。(因此，
 * maximumPoolSize的值没有任何影响。)当每个任务完全独立于其他任务时，这可能是合适的，因此任务不会
 * 影响其他任务的执行;
 *
 * 例如，在一个网页服务器中。虽然这种类型的排队在平滑短暂的请求突发方面很有用，但当命令的平均到达速度超过
 * 它们的处理速度时，它承认了无限工作队列增长的可能性。OOM
 *
 * <li><em>Bounded queues.</em> A bounded queue (for example, an
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.  </li>
 *
 * 有限队列。当使用有限的maximumPoolSizes时，有界队列(例如，{@link ArrayBlockingQueue})有助于防止
 * 资源耗尽，但可能更难调优和控制。队列大小和最大池大小可以相互权衡:使用大型队列和小型池可以最小化CPU使用、
 * OS资源和上下文切换开销，但可能会人为地降低吞吐量。如果任务经常阻塞(例如，如果它们受I/O限制)，
 * 系统可能会为比您所允许的更多的线程调度时间。使用小队列通常需要更大的池大小，这使得cpu更加繁忙，
 * 但可能会遇到不可接受的调度开销，这也会降低吞吐量。
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>  拒绝策略
 *
 * <dd>New tasks submitted in method {@link #execute(Runnable)} will be
 * <em>rejected</em> when the Executor has been shut down, and also when
 * the Executor uses finite bounds for both maximum threads and work queue
 * capacity, and is saturated.  In either case, the {@code execute} method
 * invokes the {@link
 * RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor)}
 * method of its {@link RejectedExecutionHandler}.  Four predefined handler
 * policies are provided:
 *
 * 在方法{@link #execute(Runnable)}中提交的新任务将被拒绝，当Executor已经关闭，
 * 以及当Executor使用了有限的最大线程和工作队列容量，并且饱和时也是如此。在这两种情况下，
 * {@code execute}方法都会调用其{@link RejectedExecutionHandler}的{@link
 * RejectedExecutionHandler# RejectedExecutionHandler (Runnable, ThreadPoolExecutor)}方法。
 * 提供了四个预定义的处理程序策略:
 *
 * <ol>
 *
 * <li> In the default {@link ThreadPoolExecutor.AbortPolicy}, the
 * handler throws a runtime {@link RejectedExecutionException} upon
 * rejection. </li>
 *
 * 默认{@link ThreadPoolExecutor。当被拒绝时，该处理程序抛出一个运行时
 * {@link RejectedExecutionException}。
 *
 * <li> In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted. </li>
 *
 * In {@link ThreadPoolExecutor. ini;callerrunsppolicy}，调用{@code execute}的线程本身运行该任务。
 * 这提供了一个简单的反馈控制机制，可以降低新任务提交的速度。
 *
 * <li> In {@link ThreadPoolExecutor.DiscardPolicy}, a task that
 * cannot be executed is simply dropped.  </li>
 *
 * In {@link ThreadPoolExecutor.DiscardPolicy}，不能执行的任务将被简单地删除。
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) </li>
 *
 * {@link ThreadPoolExecutor。DiscardOldestPolicy}，如果执行器没有关闭，工作队列前面的任务将被丢弃，
 * 然后重试执行(可能会再次失败，导致重复执行)
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * 可以定义和使用其他类型的{@link RejectedExecutionHandler}类。这样做需要一些注意，
 * 特别是当策略设计为仅在特定容量或排队策略下工作时。
 *
 * <dt>Hook methods</dt> hook方法
 *
 * <dd>This class provides {@code protected} overridable
 * {@link #beforeExecute(Thread, Runnable)} and
 * {@link #afterExecute(Runnable, Throwable)} methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log entries.
 * Additionally, method {@link #terminated} can be overridden to perform
 * any special processing that needs to be done once the Executor has
 * fully terminated.
 *
 * 这个类提供了{@code protected} override {@link #beforeExecute(Thread, Runnable)}
 * 和{@link #afterExecute(Runnable, Throwable)}方法，它们在每个任务执行之前和之后被调用。
 * 这些可以用来操纵执行环境;例如，重新初始化ThreadLocals、收集统计信息或添加日志条目。
 *
 * 此外，方法{@link #terminated}可以被重写，以执行任何在Executor完全终止后需要执行的特殊处理。
 *
 * <p>If hook or callback methods throw exceptions, internal worker
 * threads may in turn fail and abruptly terminate.</dd>
 *
 * 如果钩子或回调方法抛出异常，内部工作线程可能反过来失败并突然终止
 *
 * <dt>Queue maintenance</dt> 队列的维护
 *
 * <dd>Method {@link #getQueue()} allows access to the work queue
 * for purposes of monitoring and debugging.  Use of this method for
 * any other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove(Runnable)} and {@link #purge} are available to
 * assist in storage reclamation when large numbers of queued tasks
 * become cancelled.</dd>
 *
 * Method {@link #getQueue()}允许访问工作队列，用于监视和调试。强烈建议将此方法用于其他目的。
 * 提供的两个方法{@link #remove(Runnable)}和{@link #purge}可用于在大量排队任务被取消时协助存储回收
 *
 * <dt>Finalization</dt> Finalization
 *
 * <dd>A pool that is no longer referenced in a program <em>AND</em>
 * has no remaining threads will be {@code shutdown} automatically. If
 * you would like to ensure that unreferenced pools are reclaimed even
 * if users forget to call {@link #shutdown}, then you must arrange
 * that unused threads eventually die, by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>
 *
 * 在和程序中不再引用的池将自动被{@code shutdown}处理。如果你想确保即使用户忘记调用
 * {@link #shutdown}，未被引用的线程池也被回收，那么你必须通过设置适当的保持活动时间，
 * 使用0内核线程的下界和/或设置{@link #allowCoreThreadTimeOut(boolean)}来安排未
 * 被引用的线程最终死亡。
 *
 * </dl>
 *
 * <p><b>Extension example</b>. Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 * 扩展示例。这个类的大多数扩展会覆盖一个或多个受保护的钩子方法。例如，
 * 下面是一个子类，它添加了一个简单的暂停/恢复特性:
 *
 *  <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     *   workerCount, indicating the effective number of threads
     *   runState,    indicating whether running, shutting down etc
     *
     * 主池控制状态ctl是一个原子整数，包含两个概念字段
     *   workerCount，表示有效线程数
     *   runState，指示是否运行、关闭等
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * 为了将它们打包成一个int型，我们将workerCount限制为(2^29)-1(大约5亿)个线程，
     * 而不是(2^31)-1(20亿)个其他可表示的线程。如果这是将来的问题，变量可以更改为AtomicLong，
     * 下面的移位/掩码常数调整。但是在需要之前，使用int会更快、更简单一些。
     *
     * The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * workerCount是已允许启动而不允许停止的工人数。这个值可能与实际的活动线程数暂时不同，
     * 例如，当ThreadFactory请求创建线程失败时，以及退出的线程在终止前仍在执行簿记时。
     * 用户可见的池大小被报告为工人设置的当前大小。
     *
     * The runState provides the main lifecycle control, taking on values:
     * runState提供了主要的生命周期控制，其值为:
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks
     *   STOP:     Don't accept new tasks, don't process queued tasks,
     *             and interrupt in-progress tasks
     *   TIDYING:  All tasks have terminated, workerCount is zero,
     *             the thread transitioning to state TIDYING
     *             will run the terminated() hook method
     *   TERMINATED: terminated() has completed
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * 为了允许有序比较，这些值之间的数字顺序很重要。runState随时间单调增加，但不需要触及每个状态。转换:
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * 在waittermination()中等待的线程将在状态达到终止时返回。
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     *
     * 检测从关闭过渡到整理不如你想因为简单队列可能成为空后非空,反之亦然在关闭状态,但我们只能终止,
     * 如果在看到它是空的,我们看到workerCount 0(有时需要复核,见下文)。
     */
    /**
     *  这个是一个Int 类型的数字，表达了2个意思，1.声明当前线程池的状态 2.声明线程池的线程数
     *  这里是怎么用一个数字表达2个意思呢？
     *
     * 这个属性是用来存放 当前运行的worker数量以及线程池状态的
     * int是32位的，这里把int的高3位拿来充当线程池状态的标志位,后29位拿来充当当前运行worker的数量
     * rs=RUNNING值为：111-00000000000000000000000000000
     * wc的值为0：000-00000000000000000000000000000
     * rs | wc的结果为：111-00000000000000000000000000000
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    // Integer.SIZE 是32位  减去3 就是29  方便后面做位运算
    private static final int COUNT_BITS = Integer.SIZE - 3;
    // 最大容量 2 的 29次方 减去1
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    // -1的补码为：111-11111111111111111111111111111
    // 左移29位后：111-00000000000000000000000000000
    // 10进制值为：-536870912
    // 高3位111的值就是表示线程池正在处于运行状态
    private static final int RUNNING    = -1 << COUNT_BITS;// 把-1 往右边右移29位 代表线程池可以接受任务
    // 000 代表 我不接受新的任务，但是还会处理正在运行的任务和队列中的任务
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    // 001 代表线程池不接受新任务，也不去处理阻塞中的任务，会去中断你正在执行的任务
    private static final int STOP       =  1 << COUNT_BITS;
    // 010 代表线程池即将死亡 过渡状态 当你调用了shutdown 然后等到队列为空，工作线程为空就是这个状态
    private static final int TIDYING    =  2 << COUNT_BITS;
    // 011 代表线程池真的死掉了 凉凉了 需要执行 terminated 方法
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    // 通过ctl值获取线程池运行状态
    // 先把COUNT_MASK取反(~COUNT_MASK)，得到：111-00000000000000000000000000000
    // ctl位图特点是：xxx-yyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
    // 两者做一次与运算即可得到高3位xxx
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    // 通过ctl值获取工作线程数
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    // 通过运行状态和工作线程数计算ctl的值，或运算
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     *
     * 不需要解包ctl的位字段访问器。这些取决于位的布局和workerCount永远不为负。
     */

    // ctl和状态常量比较，判断是否小于s
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    // ctl和状态常量比较，判断是否大于等于s
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    // ctl和状态常量SHUTDOWN比较，判断是否处于RUNNING状态
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     * // CAS操作线程数增加1
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     * CAS操作线程数减少1
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     *
     * 线程数直接减少1
     */
    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     *
     * 用于保存任务并将其传递给工作线程的队列。我们不要求workQueue.poll()返回null就一定意味
     * 着workQueue.isEmpty()，所以只依赖于isEmpty来查看队列是否为空(例如，当我们决定是
     * 否从SHUTDOWN过渡到TIDYING时，我们必须这么做)。这容纳了特殊用途的队列，比如DelayQueues，
     * 允许poll()为其返回null，即使稍后在延迟过期时返回非null。
     *
     * 存放任务的阻塞队列
     *
     * 这里只有 Runnable 的？ 哪如果是 FutureTask呢？
     * 这里直接说了，在抽象类中，会直接将 FutureTask 封装成 Runnable对象
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     *
     * Lock持有对workers集和和related bookkeeping的访问。虽然我们可以使用某种类型的并发集合，
     * 但事实证明，使用锁通常更可取。其中一个原因是，这可以串行化interruptIdleWorkers，
     * 从而避免了不必要的中断风暴，特别是在关闭期间。否则，退出的线程将并发地中断那些尚未中断的线程。
     * 它还简化了largestPoolSize等一些相关的统计簿记。我们还在shutdown和shutdownNow上保持mainLock，
     * 以确保工人设置是稳定的，同时分别检查中断权限和实际中断。
     *
     * 全局锁
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     *
     * worker的集合,用set来存放
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination
     * awaitTermination方法使用的等待条件变量
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     * 历史达到的worker数最大值
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     *
     * 记录已经成功执行完毕的任务数
     */
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     *
     * 所有用户控制参数都声明为volatile，因此正在进行的操作都基于最新鲜的值，
     * 但不需要锁定，因为没有内部不变量依赖于它们相对于其他操作同步更改。
     */

    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread.start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     *
     * 新线程工厂。所有线程都是使用这个工厂创建的(通过addWorker方法)。所有的调用者都必须
     * 做好addWorker失败的准备，这可能反映了系统或用户限制线程数量的策略。即使它不被视为
     * 错误，创建线程失败可能会导致新的任务被拒绝或现有的任务滞留在队列中。
     *
     * 我们更进一步，即使在尝试创建线程时可能抛出OutOfMemoryError之类的错误时，也要保留池
     * 不变量。由于需要在Thread中分配本机堆栈，这样的错误是相当常见的。启动后，用户将希望执行
     * 清理池关闭来进行清理。很可能有足够的内存来完成清理代码，而不会遇到另一个OutOfMemoryError错误。
     *
     * 线程工厂，用于创建新的线程实例
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     *
     * 当队列满了并且worker的数量达到maxSize的时候,执行具体的拒绝策略
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     *
     * 等待工作的空闲线程的超时时间，以纳秒为单位。当存在多于corePoolSize或者
     * allowCoreThreadTimeOut时，线程使用这个超时。否则他们就永远等着新工作。
     *
     * 超出coreSize的worker的生存时间
     */
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     *
     * 是否允许核心线程超时，如果为true则keepAliveTime对核心线程也生效
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     *
     * 常驻worker的数量,内核池大小是保持活动的最小工作线程数(不允许超时等)，
     * 除非设置了allowCoreThreadTimeOut，在这种情况下最小值为0。
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.
     *
     * 最大worker的数量,一般当workQueue满了才会用到这个参数
     */
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     */
    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     *
     * shutdown和shutdownNow的调用需要获得许可。我们还要求(参见checkShutdownAccess)调用者
     * 拥有实际中断工作集中线程的权限(由Thread.interrupt控制，它依赖于ThreadGroup)。checkAccess，
     * 它反过来依赖于SecurityManager.checkAccess)。只有当这些检查通过时，才会尝试关闭。
     *
     * 所有对Thread.interrupt的实际调用(参见interruptIdleWorkers和interruptWorkers)都会忽略
     * securityexception，这意味着尝试中断的操作会以静默的方式失败。在关闭的情况下，它们应该不会失败，
     * 除非SecurityManager有不一致的策略，有时允许访问线程，有时不允许。在这种情况下，实际中断线程
     * 的失败可能会禁用或延迟完全终止。interruptIdleWorkers的其他用法是建议的，如果实际中断，将仅仅
     * 延迟对配置更改的响应，因此不会异常处理。
     */
    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    /* The context to be used when executing the finalizer, or null. */
    private final AccessControlContext acc;

    /**
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     *
     * Class Worker主要维护正在运行任务的线程的中断控制状态，以及其他次要的簿记工作。
     * 这个类机会性地扩展了AbstractQueuedSynchronizer，以简化获取和释放围绕每个任务执行的锁。
     * 这可以防止那些旨在唤醒等待任务的工作线程而不是中断正在运行的任务的中断。我们实现了一个简单的
     * 不可重入互斥锁，而不是使用ReentrantLock，因为我们不想让工作任务在调用池控制方法(如setCorePoolSize)
     * 时能够重新获得锁。此外，为了在线程真正开始运行任务之前抑制中断，我们将锁状态初始化为负值，
     * 并在启动时将其清除(在runWorker中)。
     *
     * 继承了AQS类，可以方便的实现工作线程的中止操作；
     * 实现了Runnable接口，可以将自身作为一个任务在工作线程中执行；
     * 当前提交的任务firstTask作为参数传入Worker的构造方法；
     */
    private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails.
         *
         * 运行的线程,前面addWorker方法中就是直接通过启动这个线程来启动这个worker
         * */
        final Thread thread;
        /** Initial task to run.  Possibly null.
         * 当一个worker刚创建的时候,就先尝试执行这个任务
         * */
        Runnable firstTask;
        /** Per-thread task counter 记录完成任务的数量
         *
         * */
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         */
        Worker(Runnable firstTask) {
            /**
             * todo: 这里为何将AQS的state设置为-1 ，而不设置为0 呢，是为了
             * 在初始化期间不接受中断信号，直到runWork开始运行
             */
            setState(-1); // inhibit interrupts until runWorker
            // 我们的任务
            this.firstTask = firstTask;
            //创建一个Thread,将自己设置给他,后面这个thread启动的时候,也就是执行worker的run方法
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker
         *
         * runWorker方法是线程池的核心:
         * 1. 线程启动之后，通过unlock方法释放锁，设置AQS的state为0，表示运行可中断；
         * 2. Worker执行firstTask或从workQueue中获取任务:
         *   1. 进行加锁操作，保证thread不被其他线程中断(除非线程池被中断)
         *   2. 检查线程池状态，倘若线程池处于中断状态，当前线程将中断。
         *   3. 执行beforeExecute
         *   4. 执行任务的run方法
         *   5. 执行afterExecute方法
         *   6. 解锁操作
         *
         * */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state  一些设置控制状态的方法
     */

    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * 将runState转换到给定的目标，如果至少已经是给定的目标，则不进行转换。
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     *
     * 如果(SHUTDOWN和池和队列为空)或(STOP和池为空)，则转换到终止状态。如果符合终止条件，
     * 但workerCount非零，则中断一个空闲的工作器，以确保关闭信号传播。这个方法必须在任何
     * 可能导致终止的操作之后被调用——减少工作人员的数量或在关机期间从队列中删除任务。该方法
     * 是非私有的，允许从ScheduledThreadPoolExecutor访问。
     */
    final void tryTerminate() {
        for (;;) {
            // 前面一坨 是判断方法能不能往下走

            // 获取线程状态
            int c = ctl.get();

            /**
             * isRunning(c) 如果线程还在运行直接退出
             * runStateAtLeast(c, TIDYING) 如果线程不在运行，那么尝试将状态改成 TIDYING ，如果修改成功，那么退出
             *
             * runStateOf(c) == SHUTDOWN 通过ctl值获取线程池运行状态，判断是不是SHUTDOWN
             * ! workQueue.isEmpty() 队列是不是为空
             * 这里如果队列不为空，代表有任务在等待，状态不是关闭，那么直接退出
             */
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;

            // 通过ctl值获取工作线程数 工作线程 不为0 代表y有任务在执行
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            // 如果执行到这里说明 线程池可以关闭了

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 尝试将线程池改成 TIDYING 状态
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        // 如果修改成功，尝试关闭
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     * 控制工作线程中断的方法。
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     *
     * 如果有安全管理器，请确保调用者通常具有关闭线程的权限(参见shutdownPerm)。如果通过，
     * 另外要确保允许调用者中断每个工作线程。如果SecurityManager对某些线程进行了特殊处理，
     * 那么即使通过了第一次检查，这也可能不是真的。
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     *
     * 中断所有线程，即使是活动的。忽略securityexception(在这种情况下，某些线程可能保持不中断)。
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //遍历所有worker，然后调用中断方法
            for (Worker w : workers)
                // 粗暴的设置中断
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * 中断可能正在等待任务的线程(如未被锁定所指示的)，以便它们可以检查是否终止或配置更改。
     * 忽略securityexception(在这种情况下，某些线程可能保持不中断)。
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     *
     * 如果为真，最多中断一个worker。这只会在tryTerminate被启用时调用，但仍然有其他worker。
     * 在这种情况下，在所有线程都在等待的情况下，最多有一个等待的worker被中断以传播关闭信号。
     * 中断任意线程可以确保关闭开始后新到达的工人最终也会退出。为了保证最终的终止，它只中断一个
     * 空闲的worker就足够了，但是shutdown()会中断所有空闲的工人，这样冗余的工人就会立即退出，
     * 而不是等待一个straggler的任务完成。
     *
     * 前面不是启动的有线程一直在等待队列中有任务，还没有清除，这里要清除
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //遍历所有的worker
            for (Worker w : workers) {
                Thread t = w.thread;
                //先尝试调用w.tryLock(),如果获取到锁,就说明worker是空闲的,就可以直接中断它
                //注意的是,worker自己本身实现了AQS同步框架,然后实现的类似锁的功能
                //它实现的锁是不可重入的,所以如果worker在执行任务的时候,会先进行加锁,这里tryLock()就会返回false
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     *
     * interrupt tidleworkers的常见形式，以避免必须记住布尔型参数的含义。
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     *
     * 杂项实用程序，其中大多数也导出到ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     *
     * 为给定命令调用被拒绝的执行处理程序。由ScheduledThreadPoolExecutor使用的包保护。
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     *
     * 在调用关机时执行运行状态转换之后的任何进一步清理。这里没有操作，
     * 但是被ScheduledThreadPoolExecutor用来取消延迟的任务。
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * 在关机期间，ScheduledThreadPoolExecutor需要进行状态检查以启用正在运行的任务。
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     *
     * 将任务队列排入一个新列表，通常使用drainTo。但如果队列是DelayQueue或任何其他类型的队列，
     * 其中poll或drainTo可能无法删除某些元素，则会逐个删除它们。
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            // 从队列中取出来，然后移除 清空队列呀
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     *
     * 创建、运行和清理workers的方法
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * 检查是否可以根据当前池状态和给定的边界(core或maximum)添加一个新的worker。
     * 如果是，则相应调整worker计数，如果可能，将创建并启动一个新worker，并将
     * firstTask作为其第一个任务运行。如果池已停止或有资格关闭，则此方法返回false。
     * 如果线程工厂在请求时未能创建线程，它也返回false。如果线程创建失败，要么是由于
     * 线程工厂返回null，要么是由于异常(通常是thread .start()中的OutOfMemoryError))，
     * 我们将彻底回滚。
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     *
     * 从方法execute的实现可以看出: addWorker主要负责创建新的线程并执行任务 线程池创建
     * 新线程执行任务时，需要 获取全局锁:
     *
     * 添加工作线程，如果返回false说明没有新创建工作线程，如果返回true说明创建和启动工作线程成功
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        // CAS更新线程池数量
        retry:
        // 注意这是一个死循环 - 最外层循环
        for (;;) {
            // 获取ctl 32位
            int c = ctl.get();
            // 获取线程池状态
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            // rs >= SHUTDOWN 那么状态是除了running状态的其他状态
            if (rs >= SHUTDOWN
               && ! (rs == SHUTDOWN && firstTask == null && ! workQueue.isEmpty())
                    // （rs == SHUTDOWN && 传入的任务是空  && 线程池不为空）返回fasle的时候 取反才是true
                 )
                /**
                 *  1. 如果不是 SHUTDOWN 那么就是更高的状态【stop等】，不需要添加任务
                 *  2. 任务为空，如果任务为空，并且线程池状态不是running.不需要处理
                 *  3. 阻塞队列不为null 如果阻塞队列为空，返回false,外侧取反 返回true，不需要处理
                 */
                return false;

            for (;;) {
                // 获取工作线程个数
                int wc = workerCountOf(c);
                /**
                 * wc >= CAPACITY  如果线程已经大于线程池最大容量，那么就不用创建了
                 * core 是否是核心线程
                 * core ? corePoolSize : maximumPoolSize 如果你传入true那么判断的是核心线程
                 * wc >= (core ? corePoolSize : maximumPoolSize) 工作线程是否大于核心线程或者最大线程
                 */
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    // 构建失败
                    return false;
                // 工作线程失败 CAS+1 操作，为什么会失败呢？因为这是并发操作，有可能其他线程已经修改了值
                if (compareAndIncrementWorkerCount(c))
                    // 成功就退出最外层循环
                    break retry;
                // 重新获取tl
                c = ctl.get();  // Re-read ctl
                // 重新判断线程池状态，如果发现状态发送变化，那么退出循环
                if (runStateOf(c) != rs)
                    // 结束这次外层循环，开始下次外部循环
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
                // 如果没有变化，那么走内测循环
            }
        }
        // 【上述执行完毕，成功的将线程数ctl增加1，就这么点事】

        // 是否worker开始
        boolean workerStarted = false;
        // 是否worker是否添加进去
        boolean workerAdded = false;
        Worker w = null; // 这个就是工作线程
        try {
            // Worker 是你的工作线程，现在这个工作线程放入了你的任务 firstTask
            w = new Worker(firstTask);
            // 从worker中获取线程
            final Thread t = w.thread;
            // 这里一般都不会为空
            if (t != null) {
                // 线程池重入锁，这里为什么要加锁呢？获取线程池的锁，避免我在添加任务的时候,其他线程
                // 干掉了线程池，因为干掉线程池需要获取这个锁，你获取了，别人就无法获取到
                // 【你在测试丢炸药包的时候，别人不能来拆除厕所】
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    // 重建获取线程池状态
                    int rs = runStateOf(ctl.get());

                    /**
                     * rs < SHUTDOWN 小于的只有running状态
                     *  (rs == SHUTDOWN && firstTask == null) 如果你是SHUTDOWN状态，传入的任务为空，可以多来几个线程加快处理阻塞队列中的任务
                     */
                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        // 如果我还没有把线程池放到集合中，你就运行了 这是不对的
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        // 将工作线程放入集合
                        workers.add(w);
                        int s = workers.size();// 工作线程的个数
                        // 如果你的线程数大于之前最大的线程数，那么更新一下这个值
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        // 已经成功将工作线程添加到集合中
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                // 添加成功后，才能执行任务
                if (workerAdded) {
                    // 线程启动，执行任务(Worker.thread(firstTask).start());
                    t.start();
                    // 标志启动线程成功
                    workerStarted = true;
                }
            }
        } finally {
            // 如果启动工作线程失败
            if (! workerStarted)
                addWorkerFailed(w);
        }
        // 最终返回的是线程是否启动
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     *   worker was holding up termination
     *
     * 回滚创建的工作线程。
     *
     * 1. 如果worker存在，从workers中移除worker
     * 2. 减少worker count计数
     * 3. 如果存在终止，则重新检查终止
     * 4. worker拒绝终止合同
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * 为dying 工worker 进行清理和记账。仅从工作线程调用。除非设置了completedsudden，
     * 否则假定workerCount已经被调整以考虑退出。这个方法从工作集中删除线程，如果它退出
     * 是因为用户任务异常，或者如果少于corePoolSize工作线程正在运行，或者队列非空但没有工作线程，
     * 则可能终止池或替换工作线程。
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     *
     *
     * 有以下情况
     *    1. 阻塞队列为空了，没任务可做了
     *    2. 比如调用了shutdown
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果是因为异常情况而退出，如果异常那么将线程数，减去1 ，正常结束的情况，会在运行会已经减去1了
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 完成的任务数+1
            completedTaskCount += w.completedTasks;
            // 从集合中移除
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 尝试关闭，g根据情况判断，比如你调用了shutdown
        tryTerminate();

        int c = ctl.get();
        //  ctl和状态常量比较，判断是否小于s 当前状态是否小于STOP，小于Stop状态的只有running
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                // 如果 allowCoreThreadTimeOut 这个为true 那么核心线程也能被回收
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                // 当前线程数 大于 最小线程 说明我还有线程，不用新建
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // todo: 这里可以看到就是线程复用的机制，前面不是有时候会释放线程吗？
            //     释放线程是因为线程大于任务数了，太多了，这里是那边一直减少减少
            //     这里又判断太少了 所以需要补一个线程
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     *    a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     *    workers are subject to termination (that is,
     *    {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     *    both before and after the timed wait, and if the queue is
     *    non-empty, this worker is not the last thread in the pool.
     *
     * 执行阻塞或定时等待任务，取决于当前的配置设置，或返回null，如果这个worker必须退出，因为以下任何原因:
     *
     * 1. 有超过maximumPoolSize的工作器(由于调用setMaximumPoolSize)。
     *
     * 2. 池已停止运行。
     *
     * 3.池已关闭，队列为空。
     *
     * 4. 这个worker在等待一个任务时超时了，超时的worker在等待之前和之后都会被终止
     *    (即{@code allowCoreThreadTimeOut || workerCount > corePoolSize})，如果
     *    队列不是空的，这个worker就不是池中的最后一个线程。
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     *
     * 通过getTask方法从阻塞队列中获取等待的任务，如果队列中没有任务，getTask方法会被
     * 阻塞并挂起，不会占用cpu资源；
     *
     * 这里面涉及到keepAliveTime的使用，从这个方法我们可以看出线程池是怎么让
     * 超过corePoolSize的那部分worker销毁的。
     */
    private Runnable getTask() {
        // 最后一次poll是否超时，只有没数据的时候 才可能超时吧
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            // 获取线程状态
            int c = ctl.get();
            // 通过ctl值获取线程池运行状态
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            /**
             * rs >= SHUTDOWN  线程池运行状态 如果不是运行状态
             * rs >= STOP 线程池不是 运行状态或者 SHUTDOWN 状态
             * workQueue.isEmpty() 队列为空
             *
             * 这些情况 都是不允许你从队列中获取任务了
             */
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            // 通过ctl值获取工作线程数
            int wc = workerCountOf(c);

            /**
             * 注意这里一段代码是keepAliveTime起作用的关键:
             * 1. allowCoreThreadTimeOut为false，线程即使空闲也不会被销毁；
             * 2. 倘若为ture，在keepAliveTime 内仍空闲则会被销毁。
             *
             * 如果线程允许空闲等待而不被销毁timed == false，workQueue.take任务:
             * 如果阻塞队列为空，当前线程会被挂起等待；
             *
             * 当队列中有任务加入时，线程被唤醒，take方法返回任务，并执行；
             * 如果线程不允许无休止空闲timed == true, workQueue.poll任务:
             *
             * 如果在keepAliveTime时间内，阻塞队列还是没有任务，则返回null；
             */
            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            /**
             * 回收线程的判断 将这个分成2个部分  A  & B
             *
             * (wc > maximumPoolSize || (timed && timedOut)
             *     wc > maximumPoolSize 工作线程数 大于 最大的线程数
             *     timedOut 最后一次poll是否超时，只有没数据的时候 才可能超时吧
             *     timed
             *
             * wc > 1 工作线程大于1 可以为1，前面不是说了 可以有一个工作线程等待任务，没有任务也要等待，这样数据来了，可以直接运行
             * workQueue.isEmpty()  对列为空
             *
             * 核心意思就是 你的线程多了，然后任务少了 需要减少线程数
             */
            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                // 减少线程个数
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // poll 这里就是阻塞
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                // 正常这里就能获取到数据，然后直接返回
                if (r != null)
                    return r;
                // 超时了
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * 主要工作循环运行。重复从队列中获取任务并执行它们，同时处理一些问题:
     *
     * 1. 我们可以从一个初始任务开始，在这种情况下，我们不需要得到第一个任务。否则，只要pool在运行，
     *    我们就从getTask获得任务。如果返回null，则由于池状态或配置参数的改变，worker退出。其他退出
     *    是由外部代码中的异常抛出导致的，在这种情况下completedsudden会保持，这通常会导致processWorkerExit
     *    替换该线程。
     *
     * 2. 在运行任何任务之前，需要获取锁，以防止在任务执行时发生其他池中断，然后我们确保，除非池停止，
     *    否则这个线程没有自己的中断集。
     *
     * 3.每个任务运行之前都有一个beforeExecute调用，它可能会抛出一个异常，在这种情况下，我们导致线程
     *   死亡(completedtrue中断循环)，而不处理该任务。
     *
     * 4. 假设beforeExecute正常完成，我们运行任务，收集其抛出的任何异常发送给afterExecute。我们分别处理
     *    RuntimeException、Error(规范保证我们会捕获这两种情况)和任意Throwables。因为我们不能在
     *    Runnable.run中重新抛出Throwables，所以我们在输出(到线程的UncaughtExceptionHandler)时将其
     *    包装在Errors中。任何抛出的异常也会保守地导致线程死亡。
     *
     * 5. 在task.run完成后，调用afterExecute，它也会抛出一个异常，也会导致线程死亡。根据JLS Sec 14.20，即使
     *    task.run抛出异常，这个异常也会生效。
     *
     * 异常机制的净效果是，afterExecute和线程的UncaughtExceptionHandler拥有我们所能提供的关于用户
     * 代码遇到的任何问题的准确信息。
     *
     * @param w the worker
     */
    final void runWorker(Worker w) {
        // 虎丘当前线程
        Thread wt = Thread.currentThread();
        // 拿到了要执行的任务
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            /**
             * task != null 如果任务不为空，那么执行任务
             * task = getTask() 如果任务为空，那么调用getTask()获取任务 这里就体现了线程的复用，如果是核心线程那么就会阻塞在这里
             * (task = getTask()) != null 只有为空就会一直循环，直到不循环才进去
             *
             * getTask() 为空的时候，就会执行线程 回收动作
             */
            while (task != null || (task = getTask()) != null) {
                // 加锁，避免你shutdown 我任务也不会中断，我正在工作呢，我拿了全局锁 你关不掉我
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                // runStateAtLeast 方法是 【ctl和状态常量比较，判断是否大于等于s】
                // runStateAtLeast(ctl.get(), STOP) 这里是判断当前状态是否小于等于stop状态，那么只能是 STOP TIDYING TERMINATED 状态
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted())
                    // 这里说明你的线程是处于 STOP TIDYING TERMINATED 那么直接中断线程
                    wt.interrupt();
                try {
                    // 执行任务前的操作 相当于AOP
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        // 直接调用了线程的run方法
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        // 执行任务后的操作 相当于AOP
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            // 线程池执行完毕的方法
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     *
     *
     * 为什么需要double check线程池的状态?
     *
     * 在多线程环境下，线程池的状态时刻在变化，而ctl.get()是非原子操作，很有可能刚获取
     * 了线程池状态后线程池状态就改变了。判断是否将command加入workque是线程池之前的状态。
     * 倘若没有double check，万一线程池处于非running状态(在多线程环境下很有可能发生)，
     * 那么command永远不会执行。
     *
     * 此处参考视频：https://www.bilibili.com/video/BV11A411V78m?from=search&seid=9894092365322891724&spm_id_from=333.337.0.0
     * 讲解比较清楚
     */
    public void execute(Runnable command) {
        if (command == null) // 判断命令（任务）对象非空
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         *
         * 按3个步骤进行:
         *
         * 1. 如果运行的线程少于corePoolSize，则尝试将给定的命令作为其第一个任务
         *   启动一个新线程。对addWorker的调用会自动检查runState和workerCount，
         *   从而通过返回false来防止误报，因为误报会在不应该添加线程的时候添加线程。
         *
         * 2. 如果一个任务可以成功排队，那么我们仍然需要再次检查是否应该添加一个线程
         *   (因为现有的线程在上次检查之后已经死亡)，或者池在进入这个方法之后已经关闭。
         *   因此，我们将重新检查状态，并在必要时在停止队列时回滚队列，或者在没有队列
         *   时启动一个新线程。
         *
         * 3.如果我们不能对任务进行排队，那么我们将尝试添加一个新线程。如果它失败了，
         *   我们知道我们已经关闭或饱和了，所以拒绝任务。
         */
        int c = ctl.get(); // 获取ctl的值 32位的int
        // workerCountOf(c) 正在工作的线程个数，判断如果当前工作线程数小于核心线程数，则创建新的核心线程并且执行传入的任务
        if (workerCountOf(c) < corePoolSize) {
            //【创建核心线程】workerCountOf获取线程池的当前线程数；小于corePoolSize，执行addWorker创建新线程执行command任务
            if (addWorker(command, true))
                return; // 如果创建新的核心线程成功则直接返回
            // 这里说明创建核心线程失败，需要更新ctl的临时变量c，为什么会失败呢？因为addWorker是没有加入任何锁的，所以上面是可能失败的
            // 比如一个线程先创建了核心线程，创建完毕后，另外一个创建的时候就会失败，会创建非核心线程
            // 但是ctl是原子类，如果上面的创建的成功了，此时要重新获取一下 ctl
            c = ctl.get();
        }
        // 线程池处于RUNNING状态，把提交的任务成功放入阻塞队列中？为什么要先判断呢？因为线程先shutdown了，那么是不能接受新的任务的
        // 走到这里说明创建新的核心线程失败，也就是当前工作线程数大于等于corePoolSize
        // 判断线程池是否处于运行中状态，同时尝试用非阻塞方法向任务队列放入任务（放入任务失败返回false）
        if (isRunning(c) && workQueue.offer(command)) {
            // 再次获取线程个数
            int recheck = ctl.get();
            // 【再次判断是否处于running状态】如果线程池没有RUNNING，成功从阻塞队列中删除任务，执行reject方法处理任务
            // 这里是向任务队列投放任务成功，对线程池的运行中状态做二次检查
            // 如果线程池二次检查状态是非运行中状态，则从任务队列移除当前的任务调用拒绝策略处理之（也就是移除前面成功入队的任务实例）
            if (! isRunning(recheck) && remove(command))
                // 调用拒绝策略处理任务 - 返回
                reject(command);
            // 走到下面的else if分支，说明有以下的前提：
            // 0、待执行的任务已经成功加入任务队列
            // 1、线程池可能是RUNNING状态
            // 2、传入的任务可能从任务队列中移除失败（移除失败的唯一可能就是任务已经被执行了）
            // 如果当前工作线程数量为0，则创建一个非核心线程并且传入的任务对象为null - 返回
            // 也就是创建的非核心线程不会马上运行，而是等待获取任务队列的任务去执行
            // 如果前工作线程数量不为0，原来应该是最后的else分支，但是可以什么也不做，因为任务已经成功入队列，总会有合适的时机分配其他空闲线程去执行它
            else if (workerCountOf(recheck) == 0) //线程池处于running状态，但是没有线程，则创建线程
                // 【阻塞队列中有任务排队，但是没有工作线程，添加一个【任务为空的线程】处理阻塞的任务】工作线程去等待任务，你提交后会直接从队列中获取任务
                addWorker(null, false);
        }
        // 走到这里说明有以下的前提：
        // 0、线程池中的工作线程总数已经大于等于corePoolSize（简单来说就是核心线程已经全部懒创建完毕）
        // 1、线程池可能不是RUNNING状态
        // 2、线程池可能是RUNNING状态同时任务队列已经满了
        // 如果向任务队列投放任务失败，则会尝试创建非核心线程传入任务执行
        // 创建非核心线程失败，此时需要拒绝执行任务
        else if (!addWorker(command, false)) // 往线程池中创建新的线程失败，则reject任务
            reject(command);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * shutdown方法会将线程池的状态设置为SHUTDOWN,线程池进入这个状态后,就拒绝再接受任务,
     * 然后会将剩余的任务全部执行完
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //检查是否可以关闭线程,权限的判断
            checkShutdownAccess();
            //设置线程池状态
            advanceRunState(SHUTDOWN);
            //尝试中断worker
            interruptIdleWorkers();
            //预留方法,留给子类实现
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        // 尝试结束
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     *
     * shutdownNow做的比较绝，它先将线程池状态设置为STOP，然后拒绝所有提交的任务。
     * 最后中断左右正在运行中的worker,然后清空任务队列。
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            //检测权限
            advanceRunState(STOP);
            //中断所有的worker
            interruptWorkers();
            //清空任务队列，拿出来对来中的所有任务，但是没有执行
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> { shutdown(); return null; };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
            addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     *
     * ensurePrestart是父类 ThreadPoolExecutor 的方法，用于启动一个新
     * 的工作线程等待执行任务，即使corePoolSize为0也会安排一个新线程。
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            // 如果核心线程没有满 那么创建核心线程
            addWorker(null, true);
        else if (wc == 0)
            // 这个说名 你设置的核心线程数为0，此时，你只能在非核心线程池
            //  至少要有一个任务的，为什么？因为如果没有一个任务，任务到了队列中
            //  就没有线程去从队列中拉取线程了，那么任务就死在任务队列中了
            addWorker(null, false);
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * 设置策略，控制如果在保持活动时间内没有任务到达，核心线程是否会超时并终止，如果需要，
     * 则在新任务到达时替换。当为false时，核心线程不会因为缺少传入的任务而终止。当为true时，
     * 适用于非核心线程的保持活动策略也适用于核心线程。为了避免持续的线程替换，在设置{@code true}时，
     * 保持活动时间必须大于零。通常应该在主动使用池之前调用此方法。
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     *
     * 尝试从工作队列中删除所有已取消的{@link Future}任务。这种方法可以用作存储回收操作
     * ，对功能没有其他影响。被取消的任务永远不会执行，但可能会在工作队列中累积，直到工作线程
     * 能够主动删除它们。现在调用此方法将尝试删除它们。然而，这种方法可能无法删除存在其他线程干扰的任务。
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * 返回计划执行的任务的大致总数。由于任务和线程的状态可能在计算过程中动态变化，所以返回值只是一个近似值。
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * 返回已完成执行的任务的大致总数。由于任务和线程的状态在计算过程中可能会动态变化，
     * 所以返回值只是一个近似值，但在连续调用中不会减少。
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                     (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                      "Shutting down"));
        return super.toString() +
            "[" + rs +
            ", pool size = " + nworkers +
            ", active threads = " + nactive +
            ", queued tasks = " + workQueue.size() +
            ", completed tasks = " + ncompleted +
            "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * 方法，在给定线程中执行给定的Runnable之前调用。这个方法由线程{@code t}调用，
     * 它将执行任务{@code r}，可以用来重新初始化ThreadLocals，或者执行日志记录。
     *
     * 这个实现什么都不做，但是可以在子类中自定义。注意:要正确嵌套多个覆盖，子类通常应该调用
     * {@code super。beforeExecute}在这个方法的末尾。
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * 方法在给定的Runnable执行完成时调用。此方法由执行任务的线程调用。如果非空，Throwable是
     * 导致执行突然终止的未捕获的{@code RuntimeException}或{@code Error}。
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * 这个实现什么都不做，但是可以在子类中自定义。注意:要正确嵌套多个覆盖，子类通常应该调用
     * {@code super。afterExecute}在这个方法的开头。
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     * 注意:当动作被显式地或通过方法(如{@link FutureTask})封装在任务中时，这些任务对象捕获并维护
     * 计算性异常，因此它们不会导致突然终止，内部异常是传递给该方法的而不是。如果你想在这个方法中
     * 捕获这两种失败，你可以进一步探测这种情况，比如在这个示例子类中，打印直接原因或任务被中止时的底层异常:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                // 从队列中取出来一个线程，但是没有用，取出来就是最老的
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
