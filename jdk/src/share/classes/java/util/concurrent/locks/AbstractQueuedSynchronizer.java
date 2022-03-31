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
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h3>Usage</h3>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 *
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes
 * one of the instrumentation methods:
 *
 *  <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 *  <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 *
 * AQS是一个用来构建锁和同步器的框架，使用AQS能简单且高效地构造出应用广泛的大量的同步器，
 * 比如我们提到的ReentrantLock，Semaphore，其他的诸如ReentrantReadWriteLock，
 * SynchronousQueue，FutureTask等等皆是基于AQS的。
 *
 * 当然，我们自己也能利用AQS非常轻松容易地构造出符合我们自己需求的同步器。
 *
 * AQS核心思想是，如果被请求的共享资源空闲，则将当前请求资源的线程设置为有效的工作线程，
 * 并且将共享资源设置为锁定状态。如果被请求的共享资源被占用，那么就需要一套线程阻塞等待
 * 以及被唤醒时锁分配的机制，这个机制AQS是用CLH队列锁实现的，即将暂时获取不到锁的线程
 * 加入到队列中。
 *
 * CLH(Craig,Landin,and Hagersten)队列是一个虚拟的双向队列(虚拟的双向队列即不存在
 * 队列实例，仅存在结点之间的关联关系)。AQS是将每条请求共享资源的线程封装成一个CLH锁队列
 * 的一个结点(Node)来实现锁的分配。
 *
 * todo: 这个视频讲解的也不错 https://www.bilibili.com/video/BV14P4y177pw/?spm_id_from=333.788
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * Wait queue node class.
     *
     * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     *
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     *
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeuing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     *
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     *
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     *
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor, unless we can identify an uncancelled
     * predecessor who will carry this responsibility.
     *
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     *
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */
    static final class Node {
        // 模式，分为共享与独占
        // 共享模式
        /** Marker to indicate a node is waiting in shared mode */
        static final Node SHARED = new Node();
        /** Marker to indicate a node is waiting in exclusive mode */
        // 独占模式
        static final Node EXCLUSIVE = null;

        // 结点状态
        // CANCELLED，值为1，表示当前的线程被取消
        // SIGNAL，值为-1，表示当前节点的后继节点包含的线程需要运行，也就是unpark
        // CONDITION，值为-2，表示当前节点在等待condition，也就是在condition队列中
        // PROPAGATE，值为-3，表示当前场景下后续的acquireShared能够得以执行
        // 值为0，表示当前节点在sync队列中，等待着获取锁
        /** waitStatus value to indicate thread has cancelled */
        /**
         * 值为1，在同步队列中等待的线程等待超时或被中断，
         * 需要从同步队列中取消该Node的结点，
         * 其结点的waitStatus为CANCELLED，
         * 即结束状态，进入该状态后的结点将不会再变化。
         */
        static final int CANCELLED =  1;
        /** waitStatus value to indicate successor's thread needs unparking
         *  SIGNAL，值为-1，表示当前节点的后继节点包含的线程需要运行，也就是unpark

         * 值为-1，被标识为该等待唤醒状态的后继结点，
         * 当其前继结点的线程释放了同步锁或被取消，
         * 将会通知该后继结点的线程执行。
         * 说白了，就是处于唤醒状态，只要前继结点释放锁，
         * 就会通知标识为SIGNAL状态的后继结点的线程执行。
         * */
        static final int SIGNAL    = -1;
        /** waitStatus value to indicate thread is waiting on condition
         *
         * 值为-2，与Condition相关，该标识的结点处于等待队列中，
         * 结点的线程等待在Condition上，当其他线程调用了Condition的signal()方法后，
         * CONDITION状态的结点将从等待队列转移到同步队列中，等待获取同步锁。
         * */
        static final int CONDITION = -2;
        /**
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         *
         * 值为-3，与共享模式相关，
         * 在共享模式中，
         * 该状态标识结点的线程处于可运行状态。
         */
        static final int PROPAGATE = -3;

        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be)
         *               blocked (via park), so the current node must
         *               unpark its successor when it releases or
         *               cancels. To avoid races, acquire methods must
         *               first indicate they need a signal,
         *               then retry the atomic acquire, and then,
         *               on failure, block.
         *   CANCELLED:  This node is cancelled due to timeout or interrupt.
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  This node is currently on a condition queue.
         *               It will not be used as a sync queue node
         *               until transferred, at which time the status
         *               will be set to 0. (Use of this value here has
         *               nothing to do with the other uses of the
         *               field, but simplifies mechanics.)
         *   PROPAGATE:  A releaseShared should be propagated to other
         *               nodes. This is set (for head node only) in
         *               doReleaseShared to ensure propagation
         *               continues, even if other operations have
         *               since intervened.
         *   0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         *
         * 状态字段，仅取以下值:
         *
         * 以下状态通常是由next节点来设置前一个节点，比如队列中的node2设置node1的状态
         *   node3设置node2状态。。。
         *   【举个栗子，因为自己睡在了，自己是没法给自己挂个牌照说自己睡着了，只能
         *     后面的节点看到你睡着了，给你挂个牌照休息中。】
         *
         * SIGNAL:
         *    等待状态是等待唤醒。当前线程释放的锁，或者当前线程取消后，需要唤醒后续的线程，
         *    通常是由next节点来设置前一个节点，【举个栗子，因为自己睡在了，自己是没法给自己挂个拍照说
         *    自己睡着了，只能后面的节点看到你睡着了，给你挂个拍照休息中。】
         *
         *    这个节点的后继节点被(或即将被)阻塞(通过park)，所以当前节点在释放或取消它的
         *    后继节点时必须解除park。为了避免竞争，acquire方法必须首先表明它们需要一个
         *    信号，然后重试原子获取，然后，在失败时阻塞。
         *
         * CANCELLED:
         *     该节点由于超时或中断而被取消。节点永远不会离开这个状态。特别是，带有被取消节点
         *     的线程不会再阻塞。
         *
         * CONDITION:
         *      这个表示等待状态为条件等待，表示这个节点在Condition队列中。节点在等待Condition通知。
         *
         *      该节点当前在条件队列中。它将不会被用作一个同步队列节点，直到传输，此时状态
         *      将被设置为0。(这里使用这个值与字段的其他用途无关，只是简化了机制。)
         *
         * PROPAGATE:
         *    这个表示等待状态为传播状态，这个主要是将唤醒后续线程的能力传递下去。主要是用在共享模式下。
         *    比如队列如下 都是共享的  node1 <- node2 <- node3....
         *    当node1被唤醒的时候，如果状态是PROPAGATE，那么就会唤醒node2，如果Node2也是PROPAGATE
         *    那么node2也会唤醒node3，如果node3不是PROPAGATE状态，那么就不会唤醒node4
         *
         *    原先是没有这个状态的，但是在共享模式下，有个bug会导致共享模式的线程一直挂在那儿了，所以
         *    添加这个状态，为了解决这个问题。
         *
         *    一个releasshared应该被传播到其他节点。在doreleasshared中设置(仅用于头节点)，
         *    以确保传播继续进行，即使其他操作已经介入。
         *
         *  0:          None of the above
         *
         *  这些值按数字排列以简化使用。非负值意味着节点不需要发出信号。因此，大多数代码不需要
         *  检查特定的值，只需要检查符号。
         *
         * 对于普通同步节点，该字段被初始化为0，对于条件节点，该字段被初始化为CONDITION。
         * 使用CAS(或者在可能的情况下，使用无条件volatile写操作)修改它。
         *
         * 结点状态，等待状态 默认值是0  代表没有状态
         */
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         *
         *  // 前驱结点
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         *
         *  // 后继结点
         */
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         *
         * 进入该节点队列的线程。在构造时初始化，使用后为空。
         * 结点所对应的线程
         */
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         *
         *  下一个等待者
         *
         *  链接到下一个等待条件的节点，或者特殊值SHARED。因为只有在独占模式下才会访问
         *  条件队列，所以我们只需要一个简单的链接队列来在节点等待条件时保存节点。然后它们
         *  被转移到队列中重新获取。由于条件只能是排他的，所以我们通过使用特殊值来表示共享
         *  模式来保存字段。
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         *
         * 结点是否在共享模式下等待
         * /
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         *
         * // 获取前驱结点，若前驱结点为空，抛出异常
         */
        final Node predecessor() throws NullPointerException {
            // 保存前驱结点
            Node p = prev;
            if (p == null)
                // 前驱结点为空，抛出异常
                throw new NullPointerException();
            else
                // 前驱结点不为空，返回
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     *
     * AQS使用一个int成员变量来表示同步状态，通过内置的FIFO队列来完成获取资源线程的排队工作。
     * AQS使用CAS对该同步状态进行原子操作实现对其值的修改。
     *
     * 共享变量state,使用volatile进行修饰。通过state来实现ReentrantLock的重入锁性质
     * 在AQS中维护了一个private volatile int state来计数重入次数，
     * 避免了频繁的持有释放操作，这样既提升了效率，又避免了死锁
     */
    private volatile int state;//共享变量，使用volatile修饰保证线程可见性

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * //原子地(CAS操作)将同步状态值设置为给定值update如果当前同步状态的值等于expect(期望值)
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     *
     * 自旋的超时时间  比使用计时park的纳秒数。粗略的估计足以在很短的时间内提高响应能力。
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor
     *
     * 自旋转for循环 + CAS 入队列。
     * 当队列为空时，则会新创建一个节点，把尾节点指向头节点，然后继续循环。
     * 第二次循环时，则会把当前线程的节点添加到队尾。head 节是一个无用节点，这和我们做CLH实现时类似
     *
     * 注意，从尾节点逆向遍历
     *
     * 将节点 node 加入队列
     * 这里有个注意点
     * 情况:
     *      1. 首先 queue是空的
     *      2. 初始化一个 dummy 节点
     *      3. 这时再在tail后面添加节点(这一步可能失败, 可能发生竞争被其他的线程抢占)
     *  这里为什么要加入一个 dummy 节点呢?
     *      这里的 Sync Queue 是CLH lock的一个变种, 线程节点 node 能否获取lock的判断通过其前继节点
     *      而且这里在当前节点想获取lock时通常给前继节点 打上 signal 的标识(表示当前继节点释放lock需要通知我来获取lock)
     *      若这里不清楚的同学, 请先看看 CLH lock的资料 (这是理解 AQS 的基础)
     *
     * 首先这里的节点连接操作并不是原子，也就是说在多线程并发的情况下，可能会出现个别节点并没有设置 next 值，就失败了。
     * 但这些节点的 prev 是有值的，所以需要逆向遍历，让 prev 属性重新指向新的尾节点，直至全部自旋入队列。
     *
     * todo: 记住队首所对应的那个node中的thread时刻都是空的，无论什么情况下
     */
    private Node enq(final Node node) {
        // 无限循环，确保结点能够成功入队列
        for (;;) {
            Node t = tail;  // 保存尾结点
            // 尾结点为空，即还没被初始化
            if (t == null) { // Must initialize
                // 头节点为空，并设置头节点为新生成的结点
                if (compareAndSetHead(new Node()))
                    tail = head;  // 头节点与尾结点都指向同一个新生结点
            } else {
                // 尾结点不为空，即已经被初始化过【这里就是入链表】
                node.prev = t;  // 将node结点的prev域连接到尾结点
                // 比较结点t是否为尾结点，若是则将尾结点设置为node
                if (compareAndSetTail(t, node)) {
                    // 设置尾结点的next域为node
                    t.next = node;
                    return t;// 返回尾结点
                }
            }
        }
    }

    /**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     *
     * 当执行方法 addWaiter，那么就是 !tryAcquire = true，也就是 tryAcquire 获取锁失败了。
     * 接下来就是把当前线程封装到 Node 节点中，加入到 FIFO 队列中。因为先进先出，所以后来的队列加入到队尾
     * compareAndSetTail 不一定一定成功，因为在并发场景下，可能会出现操作失败。那么失败后，则需要调用 enq 方法，
     * 该方法会自旋操作，把节点入队列。
     *
     * 参考：https://www.bilibili.com/video/BV19J411Q7R5?p=18
     */
    private Node addWaiter(Node mode) {
        // 新生成一个结点，默认为独占模式
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail; // 保存尾结点
        // 如果队列不为空, 使用 CAS 方式将当前节点设为尾节点
        if (pred != null) { // 尾结点不为空，即已经被初始化
            // 设置尾结点的next域为node
            // 将入队的当前线程节点的prev指针指向尾节点
            // TODO 【QUESTION49】 为何在if (compareAndSetTail(pred, node))条件前面就先将当前节点的前指针指向尾节点呢？难道避免在CPU切换的时候还能从后往前遍历？
            // 下面的文字来自：http://www.tianxiaobo.com/2018/05/01/AbstractQueuedSynchronizer-%E5%8E%9F%E7%90%86%E5%88%86%E6%9E%90-%E7%8B%AC%E5%8D%A0-%E5%85%B1%E4%BA%AB%E6%A8%A1%E5%BC%8F/
            /*
             * 将节点插入队列尾部。这里是先将新节点的前驱设为尾节点，之后在尝试将新节点设为尾节
             * 点，最后再将原尾节点的后继节点指向新的尾节点。除了这种方式，我们还先设置尾节点，
             * 之后再设置前驱和后继，即：
             *
             *    if (compareAndSetTail(t, node)) {
             *        node.prev = t;
             *        t.next = node;
             *    }
             *
             * 但但如果是这样做，会导致一个问题，即短时内，队列结构会遭到破坏。考虑这种情况，
             * 某个线程在调用 compareAndSetTail(t, node)成功后，该线程被 CPU 切换了。此时
             * 设置前驱和后继的代码还没带的及执行，但尾节点指针却设置成功，导致队列结构短时内会
             * 出现如下情况：
             *
             *      +------+  prev +-----+       +-----+
             * head |      | <---- |     |       |     |  tail
             *      |      | ----> |     |       |     |
             *      +------+ next  +-----+       +-----+
             *
             * tail 节点完全脱离了队列，这样导致一些队列遍历代码出错。如果先设置
             * 前驱，在设置尾节点。及时线程被切换，队列结构短时可能如下：
             *
             *      +------+  prev +-----+ prev  +-----+
             * head |      | <---- |     | <---- |     |  tail
             *      |      | ----> |     |       |     |
             *      +------+ next  +-----+       +-----+
             *
             * 这样并不会影响从后向前遍历，不会导致遍历逻辑出错。
             *
             * 参考：
             *    https://www.cnblogs.com/micrari/p/6937995.html
             */
            node.prev = pred;  // 将node结点的prev域连接到尾结点
            // 比较pred是否为尾结点，是则将尾结点设置为node
            // 将入队的当前线程节点赋值给尾节点即若CAS成功的话，入队的当前线程节点将成为尾节点；若失败，那么继续调用下面的enq(node)方法继续这样的逻辑
            if (compareAndSetTail(pred, node)) {
                // 执行到这里，说明CAS成功了，此时将原来尾节点的next指针指向当前入队的线程节点并返回当前节点
                pred.next = node;
                return node;// 返回新生成的结点
            }
        }
        // 队列为空、CAS失败，将节点插入队列
        // 尾结点为空(即还没有被初始化过)，或者是compareAndSetTail操作失败，则入队列

        // 执行到这里，说明有两种情况：1）同步队列还未初始化为空；2）前面的CAS操作即if (compareAndSetTail(pred, node))操作失败
        // 此时继续将当前节点入队
        enq(node);
        return node;
    }

    /**
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        // 将当前节点设置为头结点
        head = node;
        // 当前节点持有的线程置为null
        node.thread = null;
        // 当前节点的prev指针置为null,这里也为isOnSynchronizeQueue方法里的if判断node.prev = null埋下了伏笔。
        node.prev = null;
        // 因为当前节点的nextWaiter指向的是Node.EXCLUDE，而Node.EXCLUDE实质就是null，因此不需要做node.nextWaiter=null的操作
        // 因为当前节点的next指针的节点可能仍需要等待，此时也不需要做node.next = null的操作
    }

    /**
     * Wakes up node's successor, if one exists.
     *
     * // 释放后继结点
     *
     *  // TODO 【Question2】 唤醒头结点的后一个节点后，不用将这个节点移除吗？
     *         //      【Answer2】 答案就在acquireQueued方法，因为唤醒park的节点后，又会进入for循环，
     *         //                 然后进入if (p == head && tryAcquire(arg)) 分支，重新设置当前被唤醒的节点为head节点
     *         //                 和将之前Head节点的指向next置空帮助GC
     *
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        // 获取node结点的等待状态
        int ws = node.waitStatus;
        // 状态值小于0，为SIGNAL -1 或 CONDITION -2 或 PROPAGATE -3
        // CANCELLED，值为1，表示当前的线程被取消
        // SIGNAL，值为-1，表示当前节点的后继节点包含的线程需要运行，也就是unpark
        // CONDITION，值为-2，表示当前节点在等待condition，也就是在condition队列中
        // PROPAGATE，值为-3，表示当前场景下后续的acquireShared能够得以执行
        // 值为0，表示当前节点在sync队列中，等待着获取锁
        // TODO 【QUESTION51】 这里为何要将当前节点（一般是头节点）的ws置为0？仅仅是因为release方法中有个h.waitStatus != 0的if判断？
        if (ws < 0)
            // 比较并且设置结点等待状态，设置为0
            // TODO 【QUESTION52】 这里为何要CAS，而不能直接操作相应的域（node.waitStatus = 0）呢
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         *
         *  获取node节点的下一个结点
         *
         *  head                                    tail
         *  node1  ->  node2 ->  node3 ->  node4 ->  node5
         *         <-
         *  运行中      取消       队列       取消      队列
         *  循环运行结束就是找到了 node1后面，第一个不是取消状态的节点
         *  t=node2
         *  s=node3
         */
        Node s = node.next;
        // 下一个结点为空或者下一个节点的等待状态大于0，即为CANCELLED
        // 若下一个节点被取消了
        if (s == null || s.waitStatus > 0) {
            s = null;// s赋值为空
            // 从尾结点开始从后往前开始遍历
            // TODO 【QUESTION50】为何这里要从尾结点开始即从后往前遍历找到第一个未被取消的线程节点？虽然下面的答案有一定的道理，但是还未解决我的疑问，因为假如不是下面这种情况呢？
            //                   如果从尾节点向前遍历，会不会存在前面很多未被取消的线程不能被唤醒？
            // 下面的文字来自：http://www.tianxiaobo.com/2018/05/01/AbstractQueuedSynchronizer-%E5%8E%9F%E7%90%86%E5%88%86%E6%9E%90-%E7%8B%AC%E5%8D%A0-%E5%85%B1%E4%BA%AB%E6%A8%A1%E5%BC%8F/
            /*
             * 这里如果 s == null 处理，是不是表明 node 是尾节点？答案是不一定。原因之前在分析
             * enq 方法时说过。这里再啰嗦一遍，新节点入队时，队列瞬时结构可能如下：
             *                      node1         node2
             *      +------+  prev +-----+ prev  +-----+
             * head |      | <---- |     | <---- |     |  tail
             *      |      | ----> |     |       |     |
             *      +------+ next  +-----+       +-----+
             *
             * node2 节点为新入队节点，此时 tail 已经指向了它，但 node1 后继引用还未设置。
             * 这里 node1 就是 node 参数，s = node1.next = null，但此时 node1 并不是尾
             * 节点。所以这里不能从前向后遍历同步队列，应该从后向前。
             */
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0) // 找到等待状态小于等于0的结点，找到最前的状态小于等于0的结点
                    s = t;// 保存结点
        }
        // 解除传入节点的后继节点的阻塞状态，唤醒后继节点所存放的线程
        // 将当前节点的下一个线程节点的线程唤醒
        if (s != null) // 该结点不为为空，释放许可
            LockSupport.unpark(s.thread);
    }

    /**
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     *
     * 共享模式的释放动作——信号后继，保证传播。(注:对于独占模式，如果需要信号，
     * 释放相当于调用head的unpark继任者。)
     *
     * 【注意】这是一个并发调用的方法，在releaseShared、acquireShared都会调用该方法。
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         *
         * 确保一个发布得到传播，即使有其他正在进行的获取/发布。如果需要信号，
         * 这将以通常的方式试图解除头部的继任者。但是如果没有，状态被设置为PROPAGATE，
         * 以确保在发布时继续传播。此外，如果在执行此操作时添加了新节点，则必须进行循环。
         * 另外，与unpark继任的其他用途不同，我们需要知道CAS重置状态是否失败，如果失败，
         * 则重新检查。
         */
        /**
         * 这里采用了自旋的方式获取锁
         *
         * 原理：
         * 如果持有锁的线程能在很短时间内释放锁资源,那么那些等待竞争锁
         * 的线程就不需要做内核态和用户态之间的切换进入阻塞挂起状态,它们只需要等一等(自旋),
         * 等持有锁的线程释放锁后即可立即获取锁,这样就避免用户线程和内核的切换的消耗。
         */
        for (;;) {
            // 保存头节点
            Node h = head;
            // 头节点不为空并且头节点不为尾结点
            if (h != null && h != tail) {
                // 获取头节点的等待状态
                int ws = h.waitStatus;
                // 状态为SIGNAL，SIGNAL，值为-1，表示当前节点的后继节点包含的线程需要运行，也就是unpark
                // 需要让还在等待的线程开始运行
                // 如果头节点ws为SIGNAL，说明需要唤醒后继节点
                if (ws == Node.SIGNAL) {
                    // 不成功就继续，设置线程处于可运行状态
                    // 将头节点的ws置为0，说明后继节点正在运行了（没有阻塞了）
                    // 【重要知识点】前继节点ws=0表示当前线程正在运行，没有被阻塞住，此时当前线程有以下两种情况：【1】刚进入同步队列，前继节点ws还未来得及置为SIGNAL；【2】当前线程节点刚被唤醒，此时也需要将前继节点ws设置为0，即这里的情况。
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    // 上面设置可运行，如果不成功会一直重试，不会走到下面

                    // 【重要】释放后继结点
                    // 【反正最终调用 LockSupport.unpark(s.thread) 使所有的线程可运行】
                    unparkSuccessor(h);
                }

                // 如果此时头结点ws为0，说明其后继节点要么是刚入队的第一个节点还没来得及将头结点的ws置为SIGNAL，要么就是同步队列中的第一个线程节点刚被唤醒，此时ws也为0
                // 因此，在这种情况下，需要设置头节点的ws为PROPAGATE，以便在同步队列中的第一个线程被唤醒且该线程（或该线程是刚闯进来的线程但头节点的ws为0的情况）
                // 获取到共享的同步状态后进入setHeadAndPropagate时，此时在if分支有 head.ws < 0的判断，此时即可以保证唤醒头节点的下下个线程，否则可能无法唤醒唤醒头节点的下下个线程
                // 及其该节点后的同步队列的其他线程。对于这种情况，我再举例说明下：在并发下，有两个线程，一个是获取到共享同步状态的线程，假如此时共享的同步状态为0了，
                // 另一个是刚闯入同步队列的第一个线程，此时头节点的ws还未来的及设置为SIGNAL即头结点的ws为0.与此同时，手中拥有共享同步状态的线程此时突然调用release来释放手中的
                // 同步状态，此时恰好执行到这里，判断头结点的ws为0后什么都不做而是直接退出的话，假如此时那个刚闯进来的线程获取到共享的同步状态，此时共享的同步状态已为0，
                // 当其再调用setHeadAndPropagate方法判断if条件时，此时同步状态为0不满足propagate > 0的条件，此时ws=0又不满足ws < 0的条件，此时该闯进来的线程就无法唤醒其后继的
                // 线程节点了（假如同时又有其他并发的线程入队！）。
                // TODO 【QUESTION57】 如果这里将头结点的ws置为SIGNAL是不是也可以？因为SIGNAL也是小于0，符合setHeadAndPropagate方法判断if条件的ws < 0的条件，如果不可以会有什么后果？有空再分析这种情况
                // TODO 【QUESTION60】 这里JDK曾经有个bug，设置 Node.PROPAGATE是好像是后面才添加的，
                //                    参考：https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6801020
                // 【重要】节点的的PROPAGATE在这里设置，目的是为了解决并发释放的时候后继节点无法被唤醒的问题。注意，共享状态下的前继节点ws正常情况下也是SIGNAL哈。

                else if (ws == 0 &&
                        // 状态为0并且不成功，继续
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            // 若头节点改变，继续循环
            // 若头节点未变，说明还是当年的那个头结点，在唤醒后继线程后未发生改变，此时负责release的线程使命已经完成，直接退出for循环即可
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * 设置队列的头，并检查继任者是否可以在共享模式下等待，如果是，如果设置了 propagate > 0
     * 或propagate状态，则传播。
     *
     * tryAcquire() ,tryRelease(), tryAcquireShared, tryReleaseShared
     * 前两者是独占模式, 后两者是共享模式
     *
     * 这里实际上都没有具体的方法实现, 而是直接抛出异常, 其实现交给子类进行实现;
     * 之所以没有将这些方法定义成abstract抽象方法的是因为:
     *
     * 如果将这几个方法定义成抽象的, 那么继承该类的子类就必须实现所有方法,
     * 但是独占模式是没有必要去实现共享模式, 所以这样做是尽量减少不必要的工作量
     *
     * 独占方式。尝试获取资源，成功则返回true，失败则返回false。
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     *                  propagate 传播的意思
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        // 获取头节点
        Node h = head; // Record old head for check below
        // 将当前线程节点设置为头结点。
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         *
         * 如果符合下面的条件尝试向下一个排队节点发送信号:
         *   Propagation由调用者指示，或者由之前的操作记录(例如h.waitStatus在setHead之前
         *    或之后)(注意:这使用了waitStatus的符号检查，因为PROPAGATE状态可能会转换为SIGNAL)。
         * 而且
         *  下一个节点在共享模式下等待，或者我们不知道，因为它看起来是空的
         *
         *
         * 进行判断 这里注意
         * h == null || h.waitStatus < 0 ||
         * (h = head) == null || h.waitStatus < 0
         * 这里猛一看是不是有点懵逼，为啥这里判断条件写了2次。注意这里是不一样的
         *
         * 第一个h 是头节点
         * 第二个h 是方法的传参，然后被设置成头节点了
         */
        // 首先这里需要明确两点：
        // 【1】在共享的同步状态仍大于0的情况下，唤醒同步队列的非第一个节点线程不是由已获取到同步状态的线程release时来唤醒的，而是由其前一节点，而已获取到同步状态的线程release时来仅仅唤醒同步队列中的第一个线程哈；
        // 【2】某个已获取到同步状态的线程release的最终结果并非唤醒所有同步队列中的线程，而是根据目前已有的同步状态数量来决定唤醒多少个，这样就减少了竞争，否则动不动就将所有同步队列的线程唤醒，此时只有一个同步状态呢？
        //     此时岂不是大量线程竞争这个仅有的一个同步状态，而导致大量无畏的竞争呢？
        // 这个propagate参数即之前tryAcquireShared方法返回的参数，此时propagate又有三种情况：
        // 1）propagate>0；2）propagate=0；3）propagate<0
        // 【1】对于1）propagate>0的情况：举个例子比如Semaphore只要获取完后还有信号量或CountDownLatch已经countDown完的情况，此时propagate>0，
        //     对于Semaphore，此时信号量大于0说明地主家还有余粮，此时需要唤醒更多同步队列中的线程去获取信号量；
        //     对于CountDownLatch，此时count已经为0了，说明所有同步队列的线程都符合条件了，自然要唤醒更多的同步队列中的线程了
        //     ==》因为这里的功能本身就是根据同步状态由多少，就唤醒多少个同步队列中的线程。

        //     或许这里大家有个疑问，这里除了判断propagate > 0外，为啥还要判断h.waitStatus < 0小于0的情况呢？
        //     这个答案已经在doReleaseShared方法中的注释中解答。
        // TODO 【QUESTION58】 (h = head) == null || h.waitStatus < 0)又是属于哪种情况呢？

        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            // 获取节点的后继
            // 若当前线程是同步队列中的第一个线程，被release的线程唤醒了，此时当前线程获取到同步状态，因此调用了setHeadAndPropagate方法将当前线程节点设置为头结点后，
            // 判断共享的同步状态仍大于0即propagate > 0（说明同时有其他线程release了同步状态）或waitStatus < 0 ，此时继续唤醒当前节点的下一个线程。

            Node s = node.next;
            // 后继为空或者为共享模式
            // TODO 【QUESTION59】s == null又是属于哪种情况？
            if (s == null || s.isShared())
                // 以共享模式进行释放
                // 【注意】执行到这里，说明当前节点已经变成了头节点，然后调用doReleaseShared方法中的unparkSuccessor(h)时拿到头结点的下一个节点即当前节点的下一个节点哈
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node
     *
     * // 取消继续获取(资源)
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null) // node为空，返回
            return;

        node.thread = null;  // 设置node结点的thread为空

        // Skip cancelled predecessors
        Node pred = node.prev;  // 保存node的前驱结点
        // 如果节点的前置节点也被取消了，反正就是从这个节点往前找，一直到找到不是取消的节点
        /**
         * head -> node1 -> node2 -> node3  -> node4
         *         未取消    取消      取消      节点
         * 会变成如下
         * head -> node1  -> node4
         *         未取消    节点
         *
         * 可以看到一次可能越过多个节点
         */
        while (pred.waitStatus > 0)
            // 找到node前驱结点中第一个状态小于0的结点，即不为CANCELLED状态的结点
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        // 获取pred结点的下一个结点
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        // 设置node结点的状态为CANCELLED
        // 将当前节点的ws设置为CANCELLED，注意这里并不是将取消的节点的前驱节点的ws置为CANCELLED哈，只有SIGNAL是设置在前驱节点，作用域当前节点的，而PROPAGATE只会在头节点设置。
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        // node == tail node结点为尾结点，则设置尾结点为pred结点
        // 如果当前抛出异常的线程对应节点是尾节点，那么将之前从后往前遍历找到的第一个未取消的pred线程节点设为尾节点即将当前线程节点移除
        if (node == tail && compareAndSetTail(node, pred)) {
            // 比较并设置pred结点的next节点为null
            // 同时将之前从后往前遍历找到的第一个未取消的pred线程节点的next指针置为null
            compareAndSetNext(pred, predNext, null);
            // 如果当前抛出异常的线程对应节点不是尾节点，则说明其后的正在等待的线程节点需要被唤醒，此时又分两种情况：
            // 【1】如果从后往前遍历第一个未被取消的pred线程节点不是头节点，且其ws为SIGNAL，此时将pred的next指针指向被取消的当前线程的下一个节点，此时不用唤醒，
            //     因为前面还有正在阻塞的线程节点，还轮不到，而这个被取消的线程节点很可能是被中断唤醒的
            // 【2】如果从后往前遍历第一个未被取消的pred线程节点是头节点，那么此时需要唤醒后一个线程节点，否则后一个线程节点可能会永远阻塞等待？

        } else {
            // node结点不为尾结点，或者比较设置不成功
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            // 如果从后往前遍历第一个未被取消的pred线程节点不是头节点，且其ws为SIGNAL，此时将pred的next指针指向被取消的当前线程的下一个节点
            // （此时这个节点是尾节点那么ws=0，如果不是，那么ws=-1）
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                pred.thread != null) {
                // (pred结点不为头节点，并且pred结点的状态为SIGNAL)或者
                // pred结点状态小于等于0，并且比较并设置等待状态为SIGNAL成功，并且pred结点所封装的线程不为空

                Node next = node.next; // 保存结点的后继
                if (next != null && next.waitStatus <= 0) // 后继不为空并且后继的状态小于等于0
                    compareAndSetNext(pred, predNext, next); // 比较并设置pred.next = next;
                // 如果从后往前遍历第一个未被取消的pred线程节点是头节点，那么此时需要唤醒后一个线程节点，这个要唤醒的后一个线程节点即当前被取消的线程节点的下一个节点
            } else {
                // 唤醒node节点的后续节点，个人感觉这里是因为本来要被唤醒的当前节点被取消，此时所以需要唤醒下一个未被取消的节点，因为此时锁很可能被释放，再不唤醒同步队列阻塞的线程可能就没有其他线程来唤醒了
                // 下面文字来源：http://www.tianxiaobo.com/2018/05/01/AbstractQueuedSynchronizer-%E5%8E%9F%E7%90%86%E5%88%86%E6%9E%90-%E7%8B%AC%E5%8D%A0-%E5%85%B1%E4%BA%AB%E6%A8%A1%E5%BC%8F/
                /* TODO 【QUESTION54】感觉不存在下面这种情况，因为node2入队调用shouldParkAfterFailedAcquire方法时，会将ws>0的前一节点移除，直接插入到head节点后面，然后再自旋设置head节点的ws为-1.
                 * 唤醒后继节点对应的线程。这里简单讲一下为什么要唤醒后继线程，考虑下面一种情况：
                 *        head          node1         node2         tail
                 *        ws=0          ws=1          ws=-1         ws=0
                 *      +------+  prev +-----+  prev +-----+  prev +-----+
                 *      |      | <---- |     | <---- |     | <---- |     |
                 *      |      | ----> |     | ----> |     | ----> |     |
                 *      +------+  next +-----+  next +-----+  next +-----+
                 *
                 * 头结点初始状态为 0，node1、node2 和 tail 节点依次入队。node1 自旋过程中调用
                 * tryAcquire 出现异常，进入 cancelAcquire。head 节点此时等待状态仍然是 0，它
                 * 会认为后继节点还在运行中，所它在释放同步状态后，不会去唤醒后继等待状态为非取消的
                 * 节点 node2。如果 node1 再不唤醒 node2 的线程，该线程面临无法被唤醒的情况。此
                 * 时，整个同步队列就回全部阻塞住。
                 */
                unparkSuccessor(node);  // 释放node的前一个结点
            }

            // TODO 【QUESTION53】help gc 为何不是将node置为null呢？
            node.next = node; // help GC
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     *
     * 你是否还CANCELLED、SIGNAL、CONDITION 、PROPAGATE ，这四种状态，在这个方法中用到了两种如下：
     *
     * CANCELLED，取消排队，放弃获取锁。
     * SIGNAL，标识当前节点的下一个节点状态已经被挂起，意思就是大家一起排队上厕所，队伍太长了，后面的谢飞机说，我去买个油条哈，一会到我了，你微信我哈。其实就是当前线程执行完毕后，需要额外执行唤醒后继节点操作。
     * 那么，以上这段代码主要的执行内容包括：
     *
     * 如果前一个节点状态是 SIGNAL，则返回 true。安心睡觉😪等着被叫醒
     * 如果前一个节点状态是 CANCELLED，就是它放弃了，则继续向前寻找其他节点。
     * 最后如果什么都没找到，就给前一个节点设置个闹钟 SIGNAL，等着被通知。
     *
     * todo: 这里讲解的不错的是 https://www.bilibili.com/video/BV19J411Q7R5?p=18
     *      入队的时候 将上一个节点的状态设置为休眠
     *      比如 A 入队后，可能 队列如下：A      此时A随时可能运行
     *          B 入队后，可能 队列如下：A->B   此时A随时可能运行,如果B看到A在休眠，那么将A改成休眠，否则就是在运行
     *          C 入队后，可能 队列如下：A->B->C   此时A随时可能运行,因为C看到B在休眠，所以C一定不能直接运行【排队的前面还有人呢】，那么将A改成休眠
     *
     * 1. 如果前驱节点pred的waitStatus为SIGNAL，返回true，表示node节点应该park，等待它的前驱节点来唤醒。
     * 2. 如果前驱节点pred的waitStatus>0，代表该节点为CANCELLED（取消）状态，需要跳过该节点。从pred节点
     *    开始向前寻找，直到找到等待状态不为CANCELLED的，将其设置为node的前驱节点。
     * 3. 否则，使用CAS尝试将pred节点的waitStatus修改为SIGNAL，然后返回false，这里直接返回false是为了
     *    再执行一次acquireQueued方法for循环的“if (p == head && tryAcquire(arg))”代码，因为如果
     *    能tryAcquire成功，则避免了当前线程阻塞，也就减少了上下文切换的开销（
     *
     *
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        // 获取前驱结点的状态,前驱节点的等待状态
        // 执行到这里，有三种情况：
        // 【1】如果同步队列初始化时进入的第一个节点线程经过再次的“投胎”机会仍未获取到同步状态,此时pred即head节点，所以刚开始pred.waitStatus=0,后面才被置为SIGNAL(-1)
        // 【2】如果当前线程节点的前一个节点不是head节点，说明前面还有等待的线程，所以pred即前面等待的线程节点，此时刚开始pred.waitStatus=0，后面才被置为SIGNAL(-1)
        // 【总结】尾节点的ws一开始总是0，后面才被置为-1，然后for循环再次进入该方法返回true将该线程节点阻塞
        // 【3】已经处在同步队列中的第一个节点线程被唤醒后，会经过自旋，会再次获取锁，但仍未获取到同步状态，此时pred即head节点，所以刚开始pred.waitStatus=-1即SIGNAL

        int ws = pred.waitStatus;
        // 【1.2】在【1.1】步设置head节点pred的ws为SIGNAL后，退出shouldParkAfterFailedAcquire并返回false，
        //        然后该线程节点仍未获取到同步状态此时再次进入该方法时，此时head节点的ws就为SIGNAL,因此返回true，让该线程节点park阻塞即可

        if (ws == Node.SIGNAL) // 如果前驱节点节点等待状态为SIGNA，为-1
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             *
             *  返回true，表示node节点应该park，等待它的前驱节点来唤醒
             *
             *  shouldParkAfterFailedAcquire: 校验node是否需要park(park：会将node的线程阻塞)
              *  只有当前驱节点等待状态为SIGNAL，才能将node进行park，因为当前驱节点为SIGNAL时，
              *  会保证来唤醒自己，因此可以安心park

             */
            return true; // 可以进行park操作
        // 如果前驱节点的等待状态>0，代表该前驱节点为CANCELLED（取消）状态，需要跳过该节点
        // 2) pred.waitStatus > 0 即 pred.waitStatus == Node.CANCELLED(1)
        // 若pred线程节点被取消，此时从pred节点开始向前遍历找到未取消的节点并把当前节点查到该未取消的节点后面
        if (ws > 0) { // 表示状态为CANCELLED，为1
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             *
             * // 从pred节点开始向前寻找，直到找到等待状态不为CANCELLED的，
             */
            do {
                // 将其设置为node的前驱节点
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0); // 找到pred结点前面最近的一个状态不为CANCELLED的结点
            // 赋值pred结点的next域
            pred.next = node;
        } else {
            // 为PROPAGATE -3 或者是0 表示无状态,(为CONDITION -2时，表示此节点在condition queue中)
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             *
             *   pred节点使用CAS尝试将等待状态修改为SIGNAL（ws必须为PROPAGATE或0），
             *   然后返回false（即再尝试一次能否不park直接acquire成功）
             */
            // 比较并设置前驱结点的状态为SIGNAL，把上一个节点改成-1
            // 【1.1】这个步骤接前面【1】的情况，此时pred是head节点，此时将head节点pred的ws置为SIGNAL状态，说明下一个节点需要阻塞等待被SIGNAL
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        // 不能进行park操作
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     *
     * // 线程挂起等待被唤醒
     * // 进行park操作并且返回该线程是否被中断
     *
     * todo: 此处参考视频 ：https://www.bilibili.com/video/BV19J411Q7R5?p=21
     *
     *  测试 interrupted 方法会导致状态变更，两次获取的不一致，这个和
     *     AQS内部相关的源码有关，里面有部分很奇怪的无法理解的在这里可以解释
     *  打印如下：
     *   true
     *   false
     *
     *  public void interrupt(){
     *         Thread.currentThread().interrupt();
     *         System.out.println(Thread.interrupted());
     *         System.out.println(Thread.interrupted());
     *     }
     *
     * lockInterruptibly --->  parkAndCheckInterrupt（改变了用户行为） -- 响应 -- 怎么才能响应呢？ 选择调用 Thread.interrupted()
     * lock --->  parkAndCheckInterrupt1（如果这里不复用，直接返回void，专门写个方法，代码就没有这么复杂）-- Thread.interrupted()
     *
     */
    private final boolean parkAndCheckInterrupt() {
        // 在许可可用之前禁用当前线程，并且设置了blocker
        // 最终将未能获取到同步状态的线程阻塞住
        LockSupport.park(this);
        // 当前线程是否已被中断，并清除中断标记位
        // 执行到这里，说明正在park阻塞的线程被unpark（正常release唤醒）或被中断了
        // 因此调用Thread.interrupted()区分该parking的线程是被正常唤醒还是被中断，若被中断，中断标识将被清除
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting
     *
     * 负责把 addWaiter 返回的 Node 节点添加到队列结尾，并会执行获取锁操作以及判断
     * 是否把当前线程挂起。
     *
     * sync队列中的结点在独占且忽略中断的模式下获取(资源)
     */
    final boolean acquireQueued(final Node node, int arg) {
        // 能进入到该方法，说明该线程节点已经进入同步队列了，但是还未park阻塞
        // failed失败标志：若抛出异常，那么该值保持不变为true
        boolean failed = true;
        try {
            boolean interrupted = false; // 中断标志,用于判断是否被中断过
            // 自旋
            // 【重要知识点】只要进入acquireQueued方法的线程，在正常执行没异常的情况下，该线程是必定要获取到同步状态（锁）才能退出for循环即该方法结束，除非在tryAcquire时抛出异常。
            for (;;) { // 无限循环
                // 获取node节点的前驱结点
                final Node p = node.predecessor();
                // 当前节点的前驱就是head节点时, 再次尝试获取锁【这里再次获取锁体现了并发编程，如果没有拿到锁，需要加入到队列，加入之后可能别的线程已经将锁释放了，然后这里尝试获取锁，可能就获取到了，这里就提现了自旋】
                // 如果当前线程节点的前一个节点是head节点，那么在此调用tryAcquire方法看能否再次获取到同步状态，基于下面两种情况考虑：
                // 【1】因为在当前线程在进入同步队列的时候说不定同步状态(锁)已经被别的线程释放，这里再作一次努力，
                //      而不是简单粗暴的将该线程park阻塞住哈，因此再给当前线程节点（即同步队列第一个线程节点）一次“投胎”的机会；
                //      其实是两次，还有一次就是调用shouldParkAfterFailedAcquire方法将头节点的ws设置为SIGNAL后返回false，此时自旋再次执行if分支
                // 【2】如果正在同步队列park阻塞的第一个线程被唤醒后，也会进入该if分支去尝试获取锁
                // 这里判断当前线程的前一节点是否为头结点还有一个好处就是加入同步队列中的非第一个线程节点被中断唤醒后，判断到其前一线程节点不是头节点而是同样正在等待的线程节点，
                // 此时该线程会继续进入等待状态，否则会破坏同步队列的链表结构导致该线程节点前面正在等待的线程永远无法被唤醒了。
                if (p == head && tryAcquire(arg)) {  // 前驱为头节点并且成功获得锁
                    // 很幸运，当前线程虽然已经进入同步队列了，但由于同时别的线程释放了同步状态（锁），因此当前线程又再一次获得了同步状态
                    // 此时将当前节点设置为头结点
                    setHead(node); // 前驱为头节点并且成功获得锁
                    // 此处参考：https://www.bilibili.com/video/BV19J411Q7R5?p=19
                    // 原先head头结点p的next指针指向了当前节点，因此node节点被唤醒后，这里肯定要将next指针置空，从而让原先的头结点点让其无任何引用可达从而GC TODO 【QUESTION63】若头结点没有任何其他引用了，但头结点还持有其他引用，此时头节点可以被gc吗？
                    // 【注意】此时是当前节点变成了头结点，原来的头节点要被GC
                    p.next = null; // help GC
                    // 设置标志  // 标志failed为false即获取同步状态成功
                    failed = false;
                    // 返回中断标志，默认是false
                    return interrupted;
                }
                // 获取锁失败后, 判断是否把当前线程挂起
                // shouldParkAfterFailedAcquire 是在我自旋一次之后判断是否需要阻塞
                // 执行到这里，有三种情况：
                // 【1】如果同步队列初始化时进入的第一个节点线程经过再次的“投胎”机会仍未获取到同步状态；
                // 【2】如果当前线程节点的前一个节点不是head节点，说明前面还有等待的线程，此时当前线程节点要么不是第一个进入同步队列的要么就是处在同步队列中的线程节点被中断醒来的// 【3】已经处在同步队列中的第一个节点线程被唤醒后0，会经过自旋，会再次获取锁，但仍未获取到同步状态。
                //  基于以上三种情况，此时获取同步状态失败后的线程节点需要将自己park阻塞住
                // 【QUESTION1】假如park的线程被唤醒后，acquireQueued的执行逻辑是怎样的？
                // 【ANSWER1】1）如果唤醒的是同步队列中的第一个线程节点，此时其前节点就是head头节点，因此再次进入for循环去获取同步状态；
                //           2）如果唤醒的不是同步队列中的第一个线程节点（可能是被中断唤醒的），此时会继续park阻塞。

                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    // 执行到这里，说明正在parking阻塞的线程被中断从而被唤醒，因此设置interrupted为true，因为parkAndCheckInterrupt将中断标识清除了，所以后续该线程需要自我interrupt一下
                    interrupted = true;
            }
        } finally {
            // 若failed仍为true的话，说明前面try代码段抛出了一个异常,这里很可能是tryAcquire方法抛出异常，因为tryAcquire方法是留给子类实现的难免有异常
            if (failed)
                // 抛出异常后，需要取消获取同步状态同时将已经入队的当前线程节点移除，根据情况看是否需要唤醒下一个线程节点，当然，异常会继续向上抛出
                // 假如前面是tryAcquire抛出异常，此时有以下两种情况：
                // 1)获取到了同步状态（锁）后抛出异常，在业务代码的finally块中执行释放同步状态，此时无异常；
                // 2）还未获取到同步状态就抛出异常，在业务代码的finally块中执行释放同步状态，此时释放同步状态的方法tryReleae的结果会为负值，同时本异常继续向上抛出；
                // 此时也不会唤醒后继节点，若又不符合cancelAcquire方法唤醒后继节点的条件，难道此时后继节点就只有等到有其他新来的线程再次获取同步状态后release来唤醒么？假如没有新来的线程呢？ TODO 待确认是不是存在这种情况？
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    // 这个与acquire的区别是这个会抛出异常
                    throw new InterruptedException();
            }
        } finally {
            // 能执行到这里，说明前面try代码段抛出了一个异常，比如InterruptedException
            if (failed)
                // 此时取消当前节点，满足条件则唤醒后续节点
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        // 系统时间 + 超时时间 = 未来的一个时间
        final long deadline = System.nanoTime() + nanosTimeout;
        // 加入队列
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                // 拿出节点
                final Node p = node.predecessor();
                // 如果是头结点 那么尝试去获取锁，如果获取到了，那么返回true
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                // 未来时间 - 当前时间 = 距离超时结束还剩下的时间
                nanosTimeout = deadline - System.nanoTime();
                // 小于0 证明已经超时了
                if (nanosTimeout <= 0L)
                    return false;
                /**
                 * 如果前面的shouldParkAfterFailedAcquire(p, node)返回true
                 * 那么判断 你剩余的超时时间 nanosTimeout 是否大于 spinForTimeoutThreshold
                 *
                 * 这里假设你调用 tryAcquireNanos(int arg, long nanosTimeout) 传参超时时间是 20秒
                 * 然后现在剩余时间是 nanosTimeout = 18秒，因为你一次没获取到，可能下次还获取不到，这里如果
                 * 不执行 LockSupport.parkNanos（this,18） 秒，你就会执行这个for循环，在这里耗费cpu
                 * 使劲的转圈。 所以这里让你先暂停一段时间。
                 *
                 * 这里我有个疑问？nanosTimeout = 18秒 的时候 打印 spinForTimeoutThreshold 我就暂停
                 * 18秒，然后这不就直接醒来后超时了，下轮循环就结束了？而且假设我过了一秒后，就能获取了，我是不是
                 * 傻傻的停止了 18秒？ 有疑问呀
                 */
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared uninterruptible mode.
     * @param arg the acquire argument
     *
     * 这个doAcquireShared方法的逻辑跟acquireQueued方法逻辑差不多，最大的区别就是唤醒的
     *            后继节点在满足条件的情况下，需要继续唤醒其后的节点
     */
    private void doAcquireShared(int arg) {
        // 加入等待队列         // 将当前线程以共享节点类型入同步队列，Node.SHARED保存在Node节点的nextWaiter这个属性里
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                // 拿出前驱节点,获取到当前节点的前继节点
                final Node p = node.predecessor();
                // 如果前驱节点是头节点
                // 若前继节点是头节点，说明自己是同步队列中的第一个线程节点，不管是新入队的还是被唤醒的；
                // 【1】如果当前节点是新入队的第一个线程节点，若还没调用shouldParkAfterFailedAcquire方法，此时head头节点的ws为0；若已经调用shouldParkAfterFailedAcquire方法，那么此时head头节点的ws为-1；
                // 【2】如果当前节点是被唤醒的同步队列的第一个线程节点，此时head头节点的ws为-1。
                if (p == head) {
                    // 尝试拿到共享锁
                    // 那么此时再次尝试调用tryAcquireShared看能否获取共享的同步状态，若成功，则返回的r>=0；否则r<0；
                    int r = tryAcquireShared(arg);
                    // 若获取共享状态成功，此时需要重新设置头节点，并保证传播
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
        // 添加节点至等待队列
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            // 无限循环
            for (;;) {
                // 获取node的前驱节点
                final Node p = node.predecessor();
                // 前驱节点为头节点
                if (p == head) {


                    // 并且尝试获取资源成功，也就是每一轮循环都会调用tryAcquireShared尝试获取资源
                    // （r >= 0意味获取成功），除非阻塞或者跳出循环
                    // 由前文可知，CountDownLatch中只有当state = 0的情况下，r才会大于等于0
                    int r = tryAcquireShared(arg);
                    // 获取成功
                    if (r >= 0) {
                        // 设置头结点，并且传播获取资源成功的状态，这个方法的作用是确保唤醒状态传播到所有的后继节点
                        // 然后任意一个节点晋升为头节点都会唤醒其第一个有效的后继节点，起到一个链式释放和解除阻塞的动作
                        setHeadAndPropagate(node, r);
                        // 由于节点晋升，原来的位置需要断开，置为NULL便于GC
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                // shouldParkAfterFailedAcquire ->  判断获取资源失败是否需要阻塞，这里会把前驱节点的等待状态CAS更新为Node.SIGNAL
                // parkAndCheckInterrupt -> 判断到获取资源失败并且需要阻塞，调用LockSupport.park()阻塞节点中的线程实例，
                // （解除阻塞后）清空中断状态位并且返回该中断状态
                if (shouldParkAfterFailedAcquire(p, node) &&
                        // 在获取失败后是否需要禁止线程并且进行中断检查
                        // 【这个方法会调用LockSupport.park(this) 进行阻塞线程】
                    parkAndCheckInterrupt())
                    // 抛出异常
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     *
     * 分别由继承 AQS 的公平锁（FairSync）、非公平锁（NonfairSync）实现。
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * 尝试以共享模式获取。这个方法应该查询对象的状态是否允许以共享模式获取它，如果允许，
     * 则获取它。
     *
     * 这个方法总是由执行acquire的线程调用。如果这个方法报告了失败，acquire方法可能
     * 会让还没有进入队列的线程进入队列，直到其他线程发出释放的信号。
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     *
     *        获取参数。这个值总是传递给acquire方法的值，或者是条件等待入口时保存的值。
     *        否则，该值是未解释的，可以表示任何您喜欢的值。
     *
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     *
     *         获取失败返回负值;
     *         1. 如果在共享模式下获取成功，但后续的共享模式获取不能成功，则为0;
     *         2. 如果共享模式下的获取成功，并且随后的共享模式获取也可能成功，则为正值，
     *            在这种情况下，随后的等待线程必须检查可用性。
     *
     *         (对三种不同返回值的支持使此方法可以用于acquire有时仅起排他作用的上下文中。)
     *         一旦成功，这个目标就被获得了。
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * //该线程是否正在独占资源。只有用到condition才需要去实现它。
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * //独占方式。尝试获取资源，成功则返回true，失败则返回false。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     *
     * acquire 是用于获取锁的最常用的模式
     * 步骤
     *      1. 调用 tryAcquire 尝试性的获取锁(一般都是由子类实现), 成功的话直接返回
     *      2. tryAcquire 调用获取失败, 将当前的线程封装成 Node 加入到 Sync Queue 里面(调用addWaiter), 等待获取 signal 信号
     *      3. 调用 acquireQueued 进行自旋的方式获取锁(有可能会 repeatedly blocking and unblocking)
     *      4. 根据acquireQueued的返回值判断在获取lock的过程中是否被中断, 若被中断, 则自己再中断一下(selfInterrupt)
     *
     */
    public final void acquire(int arg) {
        // 这里有个反
        // 【1】首先调用子类重写的tryAcquire方法看能否获取到同步状态（可以理解为锁），若获取到同步状态则马上返回；否则该线程被封装为Node节点进入同步队列
        if (!tryAcquire(arg) &&
            acquireQueued(
                    // 【2】执行到这里，说明该线程没能获取到同步状态，此时现将该线程进入同步队列，此时只是单纯的进入了同步队列的链表，还未park阻塞哈
                    addWaiter(Node.EXCLUSIVE), arg))
            // 是 AQS 中的 Thread.currentThread().interrupt() 方法调用，它的主要作用是在执行完 acquire
            // 之前自己执行中断操作。
            // 执行到这里，说明当前线程在同步队列中曾经被interrupt了，此时需要自我interrupt下，搞个中断标识
            selfInterrupt();
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        // 判断线程是否被终止
        if (Thread.interrupted())
            throw new InterruptedException();
        // 尝试性的获取锁
        if (!tryAcquire(arg))
            //获取锁不成功,直接加入到Sync
            doAcquireInterruptibly(arg);
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        // 线程是否中断了，如果中断了 那么就抛出异常
        if (Thread.interrupted())
            throw new InterruptedException();
        // 先尝试获取锁tryAcquire(arg)  如果获取到了 直接返回
        // 否则才会调用 doAcquireNanos
        return tryAcquire(arg) ||
            doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * 独占方式。尝试释放资源，成功则返回true，失败则返回false。
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     *
     * 独占锁的释放
     */
    public final boolean release(int arg) {
        // 调用子类, 若完全释放好, 则返回true(这里有lock重复获取)
        if (tryRelease(arg)) {
            // 【2】若成功释放同步状态，那么将唤醒head头节点的下一个节点
            Node h = head;//找到头结点
            // 头节点不为空并且头节点状态不为0
            // h.waitStatus !=0 其实就是 h.waitStatus < 0 后继节点需要唤醒
            // 下面的文字来自：http://www.tianxiaobo.com/2018/05/01/AbstractQueuedSynchronizer-%E5%8E%9F%E7%90%86%E5%88%86%E6%9E%90-%E7%8B%AC%E5%8D%A0-%E5%85%B1%E4%BA%AB%E6%A8%A1%E5%BC%8F/
            /*
             * 这里简单列举条件分支的可能性，如下：
             * 1. head = null
             *     head 还未初始化。初始情况下，head = null，当第一个节点入队后，head 会被初始
             *     为一个虚拟（dummy）节点。这里，如果还没节点入队就调用 release 释放同步状态，
             *     就会出现 h = null 的情况。
             *
             * 2. head != null && waitStatus = 0
             *     表明后继节点对应的线程仍在运行中，不需要唤醒
             *
             * 3. head != null && waitStatus < 0
             *     后继节点对应的线程可能被阻塞了，需要唤醒
             */
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h); // 唤醒后继节点
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * 共享方式。尝试获取资源。
     * 负数表示失败；0表示成功，但没有剩余可用资源；
     * 正数表示成功，且有剩余资源。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        // 小于0 就是没有获取到锁
        // 这里tryAcquireShared将返回一个int值，有三种情况：
        // 【1】如果返回值小于0，说明获取到共享的同步状态失败；
        // 【2】如果返回值等于0，说明获取共享的同步状态成功，但后续的线程将获取共享的同步状态失败；
        // 【3】如果返回值大于0，说明获取共享的同步状态成功，后续的线程依然能获取到共享的同步状态直到返回值为0
        if (tryAcquireShared(arg) < 0)
            // 执行到这里，说明当前线程获取到共享的同步状态失败，此时可能需要进入同步队列park阻塞等待
            doAcquireShared(arg);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        // 如果线程已经处于中断状态，则清空中断状态位，抛出InterruptedException
        if (Thread.interrupted())
            throw new InterruptedException();
        /** 尝试去拿共享锁，这里是判断所有的线程是否到达可执行的结尾了
         *
         * state是 你创建 CountDownLatch(int count)传递的值，代表你要等几个线程完成
         * 这里 getState() == 0 开始假设是12 然后各个线程调用 countDown() 的时候，会对这个值
         * 进行-1操作，如果等于0 那么证明，所有的线程都可以唤醒了会返回1，否则就是还要继续碎觉，返回-1
         *
         * 如果返回-1，那么就是小于0的，需要继续睡觉的
         */
        if (tryAcquireShared(arg) < 0)
            // 这里正常就需要阻塞线程了
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * //共享方式。尝试释放资源，成功则返回true，失败则返回false。
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        // 如果state=0了，证明需要唤醒所有的线程了
        // 尝试释放手中的共享同步状态，对于CountDownLatch的话，当count减为0的时候返回true，对于Semaphore的话，只要能将手中的信号量释放即返回true。
        if (tryReleaseShared(arg)) {
            // 成功释放了手中的共享同步状态，此时需要唤醒后继节点，然后再由唤醒的后继线程节点负责唤醒其后继节点，而不是由成功释放共享同步状态的
            // 当前线程负责唤醒同步队列中的所有线程节点后再返回【这是值得注意的地方】
            doReleaseShared();
            // 唤醒同步队列中的第一个线程节点后，当前线程立即返回true给调用方
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     *
     * 如果明显的第一个队列线程(如果存在)正在排他模式下等待，则返回{@code true}。
     * 如果这个方法返回{@code true}，并且当前线程正在尝试以共享模式获取(也就是说，
     * 这个方法是从{@link #tryAcquireShared}调用的)，那么可以保证当前线程不是
     * 队列中的第一个线程。仅在reentrtreadwritelock中用作启发式。
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        /**
         *  (h = head) != null 先把排队的head取出来 看看是不是Null
         *  如果不为空，那么取出来头结点的后继节点 判断是不是不为空
         *  !s.isShared()  判断s后继节点是不是shared如果是 返回false
         *     如果不是,那么说明s是独占锁，返回true，然后继续判断s.thread != null s的线程是不是为空
         *     为空返回false 不为空 返回true
         */
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     *  <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread() &&
     * hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     *  <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     *         current thread, and {@code false} if the current thread
     *         is at the head of the queue or the queue is empty
     * @since 1.7
     *
     * 整个方法是判断我要不要去排队。
     * 视频：https://www.bilibili.com/video/BV19J411Q7R5?p=20
     * 一句话就是等待队列中有没有节点
     *
     *
     * 下面提到的所有不需要排队，并不是字面意义，我实在想不出什么词语来描述这个“不需要排队”；不需要排队有两种情况
     * 一：队列没有初始化，不需要排队，不需要排队，不需要排队；直接去加锁，但是可能会失败；为什么会失败呢？
     * 假设两个线程同时来lock，都看到队列没有初始化，都认为不需要排队，都去进行CAS修改计数器；有一个必然失败
     * 比如t1先拿到锁，那么另外一个t2则会CAS失败，这个时候t2就会去初始化队列，并排队
     *
     * 二：队列被初始化了，但是tc过来加锁，发觉队列当中第一个排队的就是自己；比如重入；
     * 那么什么叫做第一个排队的呢？下面解释了，很重要往下看；
     * 这个时候他也不需要排队，不需要排队，不需要排队；为什么不需要排对？
     * 因为队列当中第一个排队的线程他会去尝试获取一下锁，因为有可能这个时候持有锁锁的那个线程可能释放了锁；
     * 如果释放了就直接获取锁执行。但是如果没有释放他就会去排队，
     * 所以这里的不需要排队，不是真的不需要排队
     *
     * h != t 判断首不等于尾这里要分三种情况
     * 1、队列没有初始化，也就是第一个线程tf来加锁的时候那么这个时候队列没有初始化，
     * h和t都是null，那么这个时候判断不等于则不成立（false）那么由于是&&运算后面的就不会走了，
     * 直接返回false表示不需要排队，而前面又是取反（if (!hasQueuedPredecessors()），所以会直接去cas加锁。
     * ----------第一种情况总结：队列没有初始化没人排队，那么我直接不排队，直接上锁；合情合理、有理有据令人信服；
     * 好比你去火车站买票，服务员都闲的蛋疼，整个队列都没有形成；没人排队，你直接过去交钱拿票
     *
     * 2、队列被初始化了，后面会分析队列初始化的流程，如果队列被初始化那么h!=t则成立；（不绝对，还有第3中情况）
     * h != t 返回true；但是由于是&&运算，故而代码还需要进行后续的判断
     * （有人可能会疑问，比如队列初始化了；里面只有一个数据，那么头和尾都是同一个怎么会成立呢？
     * 其实这是第3种情况--对头等于对尾；但是这里先不考虑，我们假设现在队列里面有大于1个数据）
     * 大于1个数据则成立;继续判断把h.next赋值给s；s有是对头的下一个Node，
     * 这个时候s则表示他是队列当中参与排队的线程而且是排在最前面的；
     * 为什么是s最前面不是h嘛？诚然h是队列里面的第一个，但是不是排队的第一个；下文有详细解释
     * 因为h也就是对头对应的Node对象或者线程他是持有锁的，但是不参与排队；
     * 这个很好理解，比如你去买车票，你如果是第一个这个时候售票员已经在给你服务了，你不算排队，你后面的才算排队；
     * 队列里面的h是不参与排队的这点一定要明白；参考下面关于队列初始化的解释；
     * 因为h要么是虚拟出来的节点，要么是持有锁的节点；什么时候是虚拟的呢？什么时候是持有锁的节点呢？下文分析
     * 然后判断s是否等于空，其实就是判断队列里面是否只有一个数据；
     * 假设队列大于1个，那么肯定不成立（s==null---->false），因为大于一个Node的时候h.next肯定不为空；
     * 由于是||运算如果返回false，还要判断s.thread != Thread.currentThread()；这里又分为两种情况
     *        2.1 s.thread != Thread.currentThread() 返回true，就是当前线程不等于在排队的第一个线程s；
     *              那么这个时候整体结果就是h!=t：true; （s==null false || s.thread != Thread.currentThread() true  最后true）
     *              结果： true && true 方法最终放回true，所以需要去排队
     *              其实这样符合情理，试想一下买火车票，队列不为空，有人在排队；
     *              而且第一个排队的人和现在来参与竞争的人不是同一个，那么你就乖乖去排队
     *        2.2 s.thread != Thread.currentThread() 返回false 表示当前来参与竞争锁的线程和第一个排队的线程是同一个线程
     *             这个时候整体结果就是h!=t---->true; （s==null false || s.thread != Thread.currentThread() false-----> 最后false）
     *            结果：true && false 方法最终放回false，所以不需要去排队
     *            不需要排队则调用 compareAndSetState(0, acquires) 去改变计数器尝试上锁；
     *            这里又分为两种情况（日了狗了这一行代码；有同学课后反应说子路老师老师老是说这个AQS难，
     *            你现在仔细看看这一行代码的意义，真的不简单的）
     *             2.2.1  第一种情况加锁成功？有人会问为什么会成功啊，如这个时候h也就是持有锁的那个线程执行完了
     *                      释放锁了，那么肯定成功啊；成功则执行 setExclusiveOwnerThread(current); 然后返回true 自己看代码
     *             2.2.2  第二种情况加锁失败？有人会问为什么会失败啊。假如这个时候h也就是持有锁的那个线程没执行完
     *                       没释放锁，那么肯定失败啊；失败则直接返回false，不会进else if（else if是相对于 if (c == 0)的）
     *                      那么如果失败怎么办呢？后面分析；
     *
     *----------第二种情况总结，如果队列被初始化了，而且至少有一个人在排队那么自己也去排队；但是有个插曲；
     * ----------他会去看看那个第一个排队的人是不是自己，如果是自己那么他就去尝试加锁；尝试看看锁有没有释放
     *----------也合情合理，好比你去买票，如果有人排队，那么你乖乖排队，但是你会去看第一个排队的人是不是你女朋友；
     *----------如果是你女朋友就相当于是你自己（这里实在想不出现实世界关于重入的例子，只能用男女朋友来替代）；
     * --------- 你就叫你女朋友看看售票员有没有搞完，有没有轮到你女朋友，因为你女朋友是第一个排队的
     * 疑问：比如如果在在排队，那么他是park状态，如果是park状态，自己怎么还可能重入啊。
     * 希望有同学可以想出来为什么和我讨论一下，作为一个菜逼，希望有人教教我
     *
     *
     * 3、队列被初始化了，但是里面只有一个数据；什么情况下才会出现这种情况呢？ts加锁的时候里面就只有一个数据？
     * 其实不是，因为队列初始化的时候会虚拟一个h作为头结点，tc=ts作为第一个排队的节点；tf为持有锁的节点
     * 为什么这么做呢？因为AQS认为h永远是不排队的，假设你不虚拟节点出来那么ts就是h，
     *  而ts其实需要排队的，因为这个时候tf可能没有执行完，还持有着锁，ts得不到锁，故而他需要排队；
     * 那么为什么要虚拟为什么ts不直接排在tf之后呢，上面已经时说明白了，tf来上锁的时候队列都没有，他不进队列，
     * 故而ts无法排在tf之后，只能虚拟一个thread=null的节点出来（Node对象当中的thread为null）；
     * 那么问题来了；究竟什么时候会出现队列当中只有一个数据呢？假设原队列里面有5个人在排队，当前面4个都执行完了
     * 轮到第五个线程得到锁的时候；他会把自己设置成为头部，而尾部又没有，故而队列当中只有一个h就是第五个
     * 至于为什么需要把自己设置成头部；其实已经解释了，因为这个时候五个线程已经不排队了，他拿到锁了；
     * 所以他不参与排队，故而需要设置成为h；即头部；所以这个时间内，队列当中只有一个节点
     * 关于加锁成功后把自己设置成为头部的源码，后面会解析到；继续第三种情况的代码分析
     * 记得这个时候队列已经初始化了，但是只有一个数据，并且这个数据所代表的线程是持有锁
     * h != t false 由于后面是&&运算，故而返回false可以不参与运算，整个方法返回false；不需要排队
     *
     *
     *-------------第三种情况总结：如果队列当中只有一个节点，而这种情况我们分析了，
     *-------------这个节点就是当前持有锁的那个节点，故而我不需要排队，进行cas；尝试加锁
     *-------------这是AQS的设计原理，他会判断你入队之前，队列里面有没有人排队；
     *-------------有没有人排队分两种情况；队列没有初始化，不需要排队
     *--------------队列初始化了，按时只有一个节点，也是没人排队，自己先也不排队
     *--------------只要认定自己不需要排队，则先尝试加锁；加锁失败之后再排队；
     *--------------再一次解释了不需要排队这个词的歧义性
     *-------------如果加锁失败了，在去park，下文有详细解释这样设计源码和原因
     *-------------如果持有锁的线程释放了锁，那么我能成功上锁
     */
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        /**
         * h != t 队首和队尾是不是不相等，相等返回false 不相等返回true,都是空也返回false
         */
        return h != t &&
            ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() +
            "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     *
     * 如果一个节点(总是最初放置在条件队列中的节点)现在正在等待同步队列中重新获取，则返回true。
     *
     * @param node the node
     * @return true if is reacquiring
     *
     * 判断节点是不是在队列中
     */
    final boolean isOnSyncQueue(Node node) {
        //如果当前线程node的状态是CONDITION或者node.prev为null时说明已经在Condition队列中了，所以返回false；
        //如果node添加到AQS阻塞队列中，那么他的waitstats会被初始化为0，或者被修改为-1，-3，肯定不是condition（-2）
        //如果node添加到AQS阻塞队列中，那么他的prev肯定不为空，至少也是head节点

        // 【1】若新入条件队列的节点此时ws为CONDITION，因此此时该节点不在同步队列了
        // 【2】如果同步队列中的一个线程获取到同步状态退出同步队列的时候在调用setHead方法时，会将node.prev置为null，因此node.prev == null也标志着该节点已经不在同步队列了

        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;

        //如果node有后继节点，那么他肯定在队列中。因为前面分析了，condition队列是不会设置next字段值的

        // 【QUESTION64】这里不是很理解？为何这里只要node.next!=null就可以断定其一定在同步队列呢？因为如果同步队列中的一个线程获取到同步状态退出同步队列的时候在调用setHead方法后，
        //              此时node.next依旧不为null，但其已经出了同步队列了。
        // 【ANSWER64】 这里为何可以这么断定，玄机就在前面的if判断node.prev == null，因为如果属于QUESTION64的这种情况，就不会执行到这里了。因此执行到这里的话，该节点一定在同步队列中，大写的妙啊！
        // 【重要】节点的next和prev指针是同步队列专属的，而节点的nextWaiter指针是条件队列专属的哈，注意区分。

        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         *
         *
         * 执行到这里，说明node的waitStatus 不是CONDITION ，prev肯定也不是null。并且next肯定为null。
         * 第一次看这个代码，肯定会蒙圈，怎么可能会出现这种不一致的情况呢
         * 这种情况是因为在将node添加到AQS阻塞队列时，采用的CAS策略。CAS就有可能失败，所以会出现这种临时
         * 的不一致行为。
         * 在下面分析signal时会看到，signal会调用AbstractQueuedSynchronizer#enq方法，这个方法会先
         * 设置prev，然后再CAS设置tail和next。
         *
         * // 执行到这里，说明此时node.next == null，说明在enq方法设置尾节点时肯能CAS失败且同时又遇到了CPU切换，因此符合这种情况，但此时node.prev是不为null的哈
        // 如果前面的if判断都不满足条件，此时执行到这里，从尾部开始寻找当前节点是否在同步队列中，至于为什么从尾部开始，因为其prev指针一定不为null，更多详情解释详见enq方法。

         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     *
     * 新添加的节点都是加在队尾，所以从后向前找效率更高
     *
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     *
     * 将节点从条件队列传输到同步队列。如果成功返回true。
     *
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         *
         * 这个一般都会更改成功
         *
         *   // 1）正常情况下，条件队列中的节点ws为CONDITION，因此能成功将该节点的ws置为0,因此CAS成功，继续往下执行
        // 2）否则，若条件队列的节点的ws不为CONDITION，说明该节点被取消，因此CAS失败，返回false

         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         *
         * // 执行到这里，说明前面CAS成功将当前节点的ws置为0
        // 此时将从条件队列出来的节点插入到同步队列尾部并返回当前节点的前驱节点（该前驱节点是该节点插入到同步队列前的尾节点）

         */
        Node p = enq(node);
        //  拿到该前驱节点的ws
        int ws = p.waitStatus;
        // 1）若前驱节点ws>0，说明前驱节点被取消了，需要唤醒当前节点的线程；
        // 2）若前驱节点ws<=0，此时前驱节点ws为0，因为前驱节点原先是当前节点入同步队列前的尾节点，同步队列尾节点ws=0，此时CAS前驱节点ws为SIGNAL即表示当前节点会被等待唤醒，此时直接返回true；若CAS失败，则马上唤醒当前节点 TODO 【QUESTION64】 为何这里会CAS失败呢？
        // TODO 【QUESTION65】为何前驱节点被取消或compareAndSetWaitStatus(p, ws, Node.SIGNAL)失败需要马上唤醒当前节点？

        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            // 注意这里被唤醒的线程和从条件队列的节点转移到同步队列的节点被唤醒后，会在原来await方法的park那句代码醒过来，然后调用下面的acquireQueue方法
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     *
     * 如果该线程节点在signal前被中断，此时返回true，因为await方法是不可中断的？
     */
    final boolean transferAfterCancelledWait(Node node) {
        // 若能成功更新状态为CONDITION的当前线程节点为0，说明该线程在条件队列中被中断醒来
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            // TODO 待分析，这里为啥要将该节点放入同步队列呢？
            enq(node);
            return true;
        }

        // 代码执行到这里，说明前面CAS节点的ws失败，说明该线程是在被signal过程中或被signal后（被unpark后）被中断的
        // 为什么呢？因为若该线程还没被signal即在signal前，该节点ws为CONDITION

        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         *
         *  若该线程节点还没能成功从条件队列转移到同步队列，说明是正在转移的过程中，此时什么
         *  也不用做，只是自旋并让出cpu时间片段直到该节点被转移到同步队列后退出自旋并返回false

         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     *
     * 使用当前状态值调用release;返回保存的状态。取消节点并在失败时抛出异常。
     *
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            // 拿到当前状态
            // 拿到当前节点的同步状态，可以看到这里对于可重入锁的情况，不管持有的同步状态是是多少，此时统一释放
            int savedState = getState();
            // 释放 独占锁的释放 【可以知道condition只有独占锁】
            // 调用release方法释放同步状态并唤醒后继的线程节点，若成功，则返回当前节点的同步状态，另有他用
            if (release(savedState)) {
                // 释放成功 就会出队列

                failed = false;
                return savedState;
            } else {
                // 若释放同步状态失败了，那么抛出IllegalMonitorStateException
                throw new IllegalMonitorStateException();
            }
        } finally {
            // 若此时failed仍为true，那么大可能是调用release方法中的tryRelease抛出了异常，此时需要将当前节点的ws置为CANCELLED以便该节点后续能被移除掉（因为已经废了 TODO 【QUESTION61】这种情况该线程抛出异常就退出了，但同步状态（锁）仍不为0即线程异常退出，但锁未释放，此时该怎样处理？），同时异常继续往上抛出。
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     *
     * 作为{@link Lock}实现的基础的{@link AbstractQueuedSynchronizer}的条件实现。
     *
     * 该类的方法文档描述了机制，而不是从Lock和Condition用户的角度来描述行为规范。
     * 这个类的导出版本通常需要附带文档描述条件语义，这些语义依赖于相关的{@code
     * AbstractQueuedSynchronizer}。
     *
     * 这个类是可序列化的，但是所有的字段都是瞬态的，所以反序列化的条件没有等待器。
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /** First node of condition queue. */
        // condition队列的头节点
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        // condition队列的尾结点
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         * @return its new wait node
         *
         * 添加新的waiter到wait队列
         */
        private Node addConditionWaiter() {
            // 保存尾结点
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            // 尾结点不为空，并且尾结点的状态不为CONDITION,
            // 如果 lastWaiter 是取消状态（waitStatus != Node.CONDITION），那么将其清除
            if (t != null && t.waitStatus != Node.CONDITION) {
                // 清除状态为CONDITION的结点,删除condition队列中所有被取消的节点
                unlinkCancelledWaiters();
                // 将最后一个结点重新赋值给t
                t = lastWaiter;
            }
            // 新建一个结点 节点参数当前线程,新建一个waitStatus是Node.CONDITION的节点，
            // 表示当前节点（线程）的等待状态是condition。
            // 将当前线程封装为一个Node节点，此时当前节点的ws状态为CONDITION（-2）
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            // 若尾节点为null，说明还未初始化过，此时将尾节点指针指向当前节点即可
            if (t == null)
                // 尾结点为空
                // 设置condition队列的头节点
                firstWaiter = node;
                // 若已经初始化过，此时条件队列已经有等待的节点的了，此时将当前节点加入到条件队列尾部即可
            else// 尾结点不为空
                t.nextWaiter = node;// 设置为节点的nextWaiter域为node结点
            // 并将尾节点指针移动到当前节点
            lastWaiter = node;// 更新condition队列的尾结点
            // 返回新加入条件队列的当前节点
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         *
         * 等待队列如下       first
         *                  node1        node2          node3
         *  firstWaiter ->  thread1 ->  thread2   ->  thread3
         *                  挂起         挂起            挂起
         *                                               |
         *  lastWaiter -----------------------------------
         *
         *  (firstWaiter = first.nextWaiter) 这一步操作如下
         *
         *   firstWaiter ---------------->
         *                  first
         *                  node1        node2          node3
         *                  thread1 ->  thread2   ->  thread3
         *                  挂起         挂起            挂起
         *                                               |
         *  lastWaiter -----------------------------------
         *
         *  第二步如下   first.nextWaiter = null;
         *
         *   firstWaiter ---------------->
         *                  first
         *                  node1        node2          node3
         *                  thread1 断开 thread2   ->  thread3
         *                  挂起         挂起            挂起
         *                                               |
         *  lastWaiter -----------------------------------
         *
         * 第三步骤  transferForSignal()
         *
         *
         */
        private void doSignal(Node first) {
            // 循环
            do {
                // 先把第一个节点的下一个节点赋值给firstWaiter，
                // 一般情况(firstWaiter = first.nextWaiter) == null都不为空 只有最后一个节点为空，
                // 然后将nextWaiter为空

                // 拿到条件队列中第一个节点的下一个节点，若下一个节点为null，说明条件队列除了这个节点外没有其他节点，此时将lastWaiter指针指向null，
                // 因为该节点即将要从条件队列出队转移到同步队列中
                if ( (firstWaiter = first.nextWaiter) == null)
                    // 设置尾结点为空
                    // 将当前节点的nextWaiter置为null即完成了将该节点从条件队列中移除的动作，同时也为await方法中的if (node.nextWaiter != null) 判断埋下了伏笔
                    lastWaiter = null;
                // 设置first结点的nextWaiter域
                first.nextWaiter = null;
                // 1）若transferForSignal返回true，说明要么条件队列出队的当前节点被成功转移到了同步队列，此时直接退出while循环；
                // 2）若transferForSignal返回false,说明条件队列中的该节点被取消，此时继续遍历条件队列的下一个节点，若下一个节点不为null，那么继续将下一个节点转移到同步队列中
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            // condition队列的头节点尾结点都设置为空
            lastWaiter = firstWaiter = null;
            // 循环
            do {
                // 获取first结点的nextWaiter域结点
                Node next = first.nextWaiter;
                // 设置first结点的nextWaiter域为空
                first.nextWaiter = null;
                // 将first结点从condition队列转移到sync队列
                transferForSignal(first);
                // 重新设置first
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         *
         * 从condition队列中清除状态为CANCEL的结点
         */
        private void unlinkCancelledWaiters() {
            // 保存condition队列头节点
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {  // t不为空
                Node next = t.nextWaiter; // 下一个结点
                // t结点的状态不为CONDTION状态
                if (t.waitStatus != Node.CONDITION) {
                    // 设置t节点的额nextWaiter域为空
                    t.nextWaiter = null;
                    if (trail == null) // trail为空
                        firstWaiter = next; // 重新设置condition队列的头节点
                    else // trail不为空
                        // 设置trail结点的nextWaiter域为next结点
                        trail.nextWaiter = next;
                    if (next == null) // next结点为空
                        // 设置condition队列的尾结点
                        lastWaiter = trail;
                }
                else // t结点的状态为CONDTION状态
                    trail = t;  // 设置trail结点
                t = next;   // 设置t结点
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         *
         * 唤醒一个等待线程。如果所有的线程都在等待此条件，则选择其中的一个唤醒。
         * 在从 await 返回之前，该线程必须重新获取锁。
         */
        public final void signal() {
            // condition使用的是独占锁 如果不是独占锁  那么直接报错
            // 对于Lock类实现来说，如果持有同步状态（锁）的线程不是当前线程，则抛出IllegalMonitorStateException
            if (!isHeldExclusively()) // 不被当前线程独占，抛出异常
                throw new IllegalMonitorStateException();
            // 保存condition队列头节点 // 拿到条件队列的第一个节点
            Node first = firstWaiter;
            if (first != null) // 头节点不为空
                doSignal(first);  // 唤醒一个等待线程
            // signal完后，该线程会释放同步状态（锁），然后从条件队列中被转移到同步队列的节点将被唤醒来竞争同步状态（锁），前提是此时该节点已经是同步队列的第一个节点哈
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         *  唤醒所有等待线程。如果所有的线程都在等待此条件，则唤醒所有线程。
         *  在从 await 返回之前，每个线程都必须重新获取锁。
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            // 不被当前线程独占，抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 保存condition队列头节点
            Node first = firstWaiter;
            if (first != null)// 头节点不为空
                doSignalAll(first); // 唤醒所有等待线程
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         *
         * // 等待，当前线程在接到信号之前一直处于等待状态，不响应中断
         */
        public final void awaitUninterruptibly() {
            // 添加一个结点到等待队列，将waiter加入到condition等待队列（condition queue）
            Node node = addConditionWaiter();
            // 获取释放的状态，将当前线程占用的state锁资源全部释放。目的是为了将该线程从AQS队列中移除。
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            // isOnSyncQueue：在AQS队列中返回true，否则返回false
            while (!isOnSyncQueue(node)) {
                // 阻塞当前线程,执行这里，说明当期线程不在AQS队列中，则需要被park挂起。
                LockSupport.park(this);
                // 当前线程被中断
                if (Thread.interrupted())
                    // 设置interrupted状态
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /** Mode meaning to reinterrupt on exit from wait */
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        private static final int THROW_IE    = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            // 若被中断了，此时调用transferAfterCancelledWait方法来确定下该线程中断是在被signal前还是被signal后中断，
            // 【1】若该线程是在被signal前中断了，说明该线程还处于条件队列中就被中断了，此时transferAfterCancelledWait方法返回，需要将该异常抛出去
            // 【2】若该线程是在被signal中或后被中断了，说明这个线程是正常唤醒的线程，又因为前面调用了Thread.interrupted() 清除了中断标识，此时返回REINTERRUPT
            //     即意味着返回await方法后需要自我interrupt下，搞个中断标志供用户识别，用户怎么处理是用户的事情了
            return Thread.interrupted() ?
                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         *
         * 等待，当前线程在接到信号或被中断之前一直处于等待状态
         */
        public final void await() throws InterruptedException {
            // 能执行到这里，说明当前线程已经持有了锁，当前线程是不在同步队列的哈
            // 首先检查当前线程是否被中断，如果是，那么抛出InterruptedException

            // 当前线程被中断，抛出异常
            if (Thread.interrupted())
                throw new InterruptedException();
            // 在wait队列上添加一个结点
            // 将当前线程加入到条件队列中，此时还未park阻塞且还未放弃锁,注意条件队列是一个单链表结构
            Node node = addConditionWaiter();
            // 释放同步状态并唤醒后继的线程节点，若成功，则返回当前节点的同步状态;若抛出异常，下面的逻辑不用执行了，此时同步状态未被释放？
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            // 判断节点是不是在队列中
            // 如果当前节点不在同步队列中，则直接阻塞当前已经释放同步状态（释放了锁）的线程
            // 不在同步队列中有以下情形：【1】当一个线程获取到同步状态（锁）后，马上调用了await方法，此时当前线程会被封装为一个CONDITION节点并进入条件队列，此时ws = CONDITION；
            //                       【2】当一个在条件队列阻塞的线程节点被唤醒或被中断后，若此时未能成功转移到同步队列？
            //                           TODO 【QUESTION66】 确认下有没有这种可能？感觉没有，因为如果是该线程在parking被中断唤醒，会调用checkInterruptWhileWaiting方法里的enq将该节点入队，之后再到while循环条件；
            //                                              如果该线程被正常唤醒的话，也是先将该线程节点先入同步队列，最后再唤醒该parking的线程，然后再到while循环条件
            while (!isOnSyncQueue(node)) {
                // 【阻塞当前线程】条件队列中挂起
                // 这里首先需要明确两点：1）被正常signal唤醒的线程需要从条件队列进入同步队列；2）正在条件队列中阻塞的线程被中断的话，最终也是需要进入同步队列；
                // 因为基于上面两种情况，只要醒过来的线程都要去重新竞争同步状态（锁），而竞争同步状态（锁）的正常步骤都是先将该线程节点入同步队列，然后再再调用
                // acquireQueued方法自旋获取锁，这也从侧面解释了为啥条件队列被中断的线程节点也需要进入同步队列。此外，基于2）的情形，如果不进入同步队列即不调用acquireQueued（调用了acquireQueued
                // 方法意味着正常情况下该线程必须先获取到同步状态（锁）才能返回即正常情况下，只要调用了acquireQueued就意味着最终能获取到锁）
                // 而最后在业务代码块中释放了同步状态（锁），此时是没获取到同步状态（锁）的，此时肯定会有另一个异常（没获取同步状态却释放同步状态的异常）会抛出从而覆盖中断异常

                // 1）若是条件队列中阻塞（还未被signal）被中断的线程醒来后，此时是THROW_IE的情形，同时调用acquireQueued方法又获取到了同步状态（锁），此时该线程节点会退出同步队列，
                //    最后在reportInterruptAfterWait方法中抛出InterruptedException，如果该线程抛出异常后，是不是刚获取的同步状态（锁）没有释放，所以要求我们在finally块中执行释放同步状态（锁）的操作来确保异常也能成功释放同步状态。
                // 2）若是条件队列中阻塞（还未被signal）被中断的线程醒来后，此时是THROW_IE的情形，同时调用acquireQueued方法没能获取到同步状态（锁），此时该线程节点会继续留在同步队列，并且再次
                //    进入parking阻塞状态。若在同步队列中parking没有被中断，当被唤醒后，若能获取到同步状态，此时acquireQueued方法返回false，继续执行代码时，因为interruptMode=-1，此时继续执行
                //    reportInterruptAfterWait方法；当若在同步队列中parking中被中断，当被唤醒后，若能获取到同步状态，此时acquireQueued方法返回true。
                // 【总结】正在条件队列中parking的线程不管是被正常signal唤醒还是异常中断唤醒，此时都需要入同步队列去竞争锁，以避免业务代码的finally块释放同步状态出错。
                LockSupport.park(this);
                // 检查结点等待时的中断类型，这里如果没有中断返回false
                // 检查下该线程在等待过程中有无被中断，若是被中断唤醒，此时直接退出while循环；若是正常被signal唤醒，此时继续while循环，此时若被signal唤醒，执行到这里正常情况下该节点已经被转移到同步队列了
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            // 如果当前线程已经在同步队列中，可能是当前线程被唤醒且被转移到同步队列了，此时需要再次去获取同步状态（锁），因为另一个调用signal唤醒的线程之后会释放锁。
            // 【注意】从条件队列转移到同步队列的阻塞节点被唤醒后，将执行这里的逻辑即调用acquireQueued去竞争锁哈！
            // 这里的savedState保存的是之前fullyRelease的返回值，考虑可重入锁的情况，之前释放了多少同步状态，此时再次获取同步状态时就同样要再次获取同等量级的同步状态。
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                // 这里表示条件队列的线程节点被signal后中断，此时这里是REINTERRUPT类型，意味着需要自我interrupt下，搞个中断标志供用户识别，用户怎么处理是用户的事情了
                interruptMode = REINTERRUPT;
            // 代码执行到这里，说明该线程醒来后（不管中断还是正常唤醒），再次获取到了锁
            // 【QUESTION63】为啥node.nextWaiter != null说明该节点就被CANCEL了呢？
            // 【ANSWER63】首先这里得先明确节点何时会被CANCEL,一般正在同步队列或条件队列parking阻塞的节点若被中断的话，此时意味着该节点被CANCEL了 TODO 总结下还有无其他节点被CANCEL的情况。
            //            其次，线程能执行到这里，说明要么正在条件队列的线程节点要么被signal正常唤醒，要么被中断，下面就这两种情况展开分析：
            //            1)被signal正常唤醒，那么在doSignal方法中会将node.nextWaiter置为null，然后将该节点转移到同步队列最后再唤醒该节点，因此该节点被唤醒后执行到这里，不满足node.nextWaiter != null条件
            //            2）被中断唤醒，前面会通过break跳出while循环，此时满足ode.nextWaiter != null的条件，说明此时该节点被CANCEL，

            if (node.nextWaiter != null) // clean up if cancelled
                // 从condition队列中清除状态为CANCEL的结点
                unlinkCancelledWaiters();
            // nterruptMode != 0，说明正在条件队列parking的线程被中断了；否则就是被正常唤醒
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         *
         * 等待，当前线程在接到信号、被中断或到达指定等待时间之前一直处于等待状态
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         *
         * 等待，当前线程在接到信号、被中断或到达指定最后期限之前一直处于等待状态
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         *
         * 等待，当前线程在接到信号、被中断或到达指定等待时间之前一直处于等待状态。此方法在行为上等效于: awaitNanos(unit.toNanos(time)) > 0
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         *
         *  返回正在等待此条件的线程数估计值
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         *
         *  返回包含那些可能正在等待此条件的线程集合
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                        expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
