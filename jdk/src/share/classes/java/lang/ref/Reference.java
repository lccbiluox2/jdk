/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.ref;

import sun.misc.Cleaner;
import sun.misc.JavaLangRefAccess;
import sun.misc.SharedSecrets;

/**
 * Abstract base class for reference objects.  This class defines the
 * operations common to all reference objects.  Because reference objects are
 * implemented in close cooperation with the garbage collector, this class may
 * not be subclassed directly.
 *
 * @author   Mark Reinhold
 * @since    1.2
 */
/*
 * 常见的Referenc子类是SoftReference、WeakReference、PhantomReference、FinalReference。
 * 这些子类相当于给了JVM一个信号，告诉JVM它们在内存中存留的时间。
 *
 * 引用简介：
 * Strong Reference：强引用，普通的的引用类型，new一个对象默认得到的引用就是强引用，只要对象存在强引用，就不会被GC。
 * SoftReference：软引用，当一个对象只剩软引用，且堆内存不足时，垃圾回收器才会回收对应引用
 * WeakReference：弱引用，当一个对象只剩弱引用，垃圾回收器每次运行都会回收其引用
 * PhantomReference：虚引用，对引用无影响，只用于获取对象被回收的通知
 * FinalReference：Java用于实现finalization的一个内部类
 *
 * 引用类型	取得目标对象方式	 垃圾回收条件	是否可能内存泄漏
 * 强引用	直接调用	         不回收	        可能
 * 软引用	通过get()方法	 视内存情况回收	不可能
 * 弱引用	通过get()方法	 永远回收	    不可能
 * 虚引用	无法取得	         不回收/回收	    可能
 * 注：虚引用在JDK9之前不回收，JDK9之后回收
 *
 * 值得注意的是，GC只对追踪的referent对象做特殊处理
 * 对于软/弱/虚引用本身，以及子类中的其他引用，按普通的垃圾回收机制处理
 *
 * 所以，如果自定义的引用继承了弱引用或虚引用，且自主增加了额外的引用变量，
 * 那么如果没有及时释放这些引用，还是可能发生内存泄露的
 */
public abstract class Reference<T> {

    /* A Reference instance is in one of four possible internal states:
     *
     *     Active: Subject to special treatment by the garbage collector.  Some
     *     time after the collector detects that the reachability of the
     *     referent has changed to the appropriate state, it changes the
     *     instance's state to either Pending or Inactive, depending upon
     *     whether or not the instance was registered with a queue when it was
     *     created.  In the former case it also adds the instance to the
     *     pending-Reference list.  Newly-created instances are Active.
     *
     *     Pending: An element of the pending-Reference list, waiting to be
     *     enqueued by the Reference-handler thread.  Unregistered instances
     *     are never in this state.
     *
     *     Enqueued: An element of the queue with which the instance was
     *     registered when it was created.  When an instance is removed from
     *     its ReferenceQueue, it is made Inactive.  Unregistered instances are
     *     never in this state.
     *
     *     Inactive: Nothing more to do.  Once an instance becomes Inactive its
     *     state will never change again.
     *
     * The state is encoded in the queue and next fields as follows:
     *
     *     Active: queue = ReferenceQueue with which instance is registered, or
     *     ReferenceQueue.NULL if it was not registered with a queue; next =
     *     null.
     *
     *     Pending: queue = ReferenceQueue with which instance is registered;
     *     next = this
     *
     *     Enqueued: queue = ReferenceQueue.ENQUEUED; next = Following instance
     *     in queue, or this if at end of list.
     *
     *     Inactive: queue = ReferenceQueue.NULL; next = this.
     *
     * With this scheme the collector need only examine the next field in order
     * to determine whether a Reference instance requires special treatment: If
     * the next field is null then the instance is active; if it is non-null,
     * then the collector should treat the instance normally.
     *
     * To ensure that a concurrent collector can discover active Reference
     * objects without interfering with application threads that may apply
     * the enqueue() method to those objects, collectors should link
     * discovered objects through the discovered field. The discovered
     * field is also used for linking Reference objects in the pending list.
     */

    private T referent;         /* Treated specially by GC */

    // "报废引用"队列，当前引用被垃圾回收之后会存于此；多个引用可以共享一个引用队列
    volatile ReferenceQueue<? super T> queue;

    /* When active:   NULL
     *     pending:   this
     *    Enqueued:   next reference in queue (or this if last)
     *    Inactive:   this
     */
    @SuppressWarnings("rawtypes")
    Reference next;

    /* When active:   next element in a discovered reference list maintained by GC (or this if last)
     *     pending:   next element in the pending list (or null if last)
     *   otherwise:   NULL
     */
    // 由虚拟机设置的"报废引用"列表，什么类型的引用都有
    transient private Reference<T> discovered;  /* used by VM */


    /* Object used to synchronize with the garbage collector.  The collector
     * must acquire this lock at the beginning of each collection cycle.  It is
     * therefore critical that any code holding this lock complete as quickly
     * as possible, allocate no new objects, and avoid calling user code.
     */
    static private class Lock { }
    private static Lock lock = new Lock();


    /* List of References waiting to be enqueued.  The collector adds
     * References to this list, while the Reference-handler thread removes
     * them.  This list is protected by the above lock object. The
     * list uses the discovered field to link its elements.
     */
    private static Reference<Object> pending = null;

    /* High-priority thread to enqueue pending References
     */
    /*
     * “报废Reference”处理线程，用来监测被虚拟机清理的引用，并决定是否将其加入ReferenceQueue以便回收利用，在单独的后台线程中启动
     *
     * 注：站在报废引用队列的角度观察，该线程可以被视为是生产者，不断向报废引用队列填充已经报废的引用以待后续处理
     */
    private static class ReferenceHandler extends Thread {

        // 确保指定的类已经初始化
        private static void ensureClassInitialized(Class<?> clazz) {
            try {
                Class.forName(clazz.getName(), true, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw (Error) new NoClassDefFoundError(e.getMessage()).initCause(e);
            }
        }

        static {
            // pre-load and initialize InterruptedException and Cleaner classes
            // so that we don't get into trouble later in the run loop if there's
            // memory shortage while loading/initializing them lazily.
            ensureClassInitialized(InterruptedException.class);
            // 确保jdk.internal.ref.Cleaner已经初始化
            ensureClassInitialized(Cleaner.class);
        }

        ReferenceHandler(ThreadGroup g, String name) {
            super(g, name);
        }

        // 常驻后台运行
        public void run() {
            // 死循环
            while (true) {
                // 处理报废的引用
                tryHandlePending(true);
            }
        }
    }

    /**
     * Try handle pending {@link Reference} if there is one.<p>
     * Return {@code true} as a hint that there might be another
     * {@link Reference} pending or {@code false} when there are no more pending
     * {@link Reference}s at the moment and the program can do some other
     * useful work instead of looping.
     *
     * @param waitForNotify if {@code true} and there was no pending
     *                      {@link Reference}, wait until notified from VM
     *                      or interrupted; if {@code false}, return immediately
     *                      when there is no pending {@link Reference}.
     * @return {@code true} if there was a {@link Reference} pending and it
     *         was processed, or we waited for notification and either got it
     *         or thread was interrupted before being notified;
     *         {@code false} otherwise.
     */
    static boolean tryHandlePending(boolean waitForNotify) {
        Reference<Object> r;
        Cleaner c;
        try {
            synchronized (lock) {
                if (pending != null) {
                    r = pending;
                    // 'instanceof' might throw OutOfMemoryError sometimes
                    // so do this before un-linking 'r' from the 'pending' chain...
                    c = r instanceof Cleaner ? (Cleaner) r : null;
                    // unlink 'r' from 'pending' chain
                    pending = r.discovered;
                    r.discovered = null;
                } else {
                    // The waiting on the lock may cause an OutOfMemoryError
                    // because it may try to allocate exception objects.
                    if (waitForNotify) {
                        lock.wait();
                    }
                    // retry if waited
                    return waitForNotify;
                }
            }
        } catch (OutOfMemoryError x) {
            // Give other threads CPU time so they hopefully drop some live references
            // and GC reclaims some space.
            // Also prevent CPU intensive spinning in case 'r instanceof Cleaner' above
            // persistently throws OOME for some time...
            Thread.yield();
            // retry
            return true;
        } catch (InterruptedException x) {
            // retry
            return true;
        }

        // Fast path for cleaners
        if (c != null) {
            c.clean();
            return true;
        }

        ReferenceQueue<? super Object> q = r.queue;
        if (q != ReferenceQueue.NULL) q.enqueue(r);
        return true;
    }

    // 在根线程组启动一个名为Reference Handler的守护线程来处理被回收掉的引用
    static {
        // 获取当前线程所在的线程组
        ThreadGroup tg = Thread.currentThread().getThreadGroup();

        // 顺着当前线程组往上遍历，找到根线程组system
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());

        // 构造名称为"Reference Handler"的线程
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        /* If there were a special system-only priority greater than
         * MAX_PRIORITY, it would be used here
         */
        // 设置为最高优先级的守护线程
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();

        // provide access in SharedSecrets
        SharedSecrets.setJavaLangRefAccess(new JavaLangRefAccess() {
            @Override
            public boolean tryHandlePendingReference() {
                return tryHandlePending(false);
            }
        });
    }

    /* -- Referent accessor and setters -- */

    /**
     * Returns this reference object's referent.  If this reference object has
     * been cleared, either by the program or by the garbage collector, then
     * this method returns <code>null</code>.
     *
     * @return   The object to which this reference refers, or
     *           <code>null</code> if this reference object has been cleared
     */
    // 返回此Reference包裹的自定义引用对象，如果该对象已被回收，则返回null
    public T get() {
        return this.referent;
    }

    /**
     * Clears this reference object.  Invoking this method will not cause this
     * object to be enqueued.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * clears references it does so directly, without invoking this method.
     */
    // 取消对目标对象的追踪
    public void clear() {
        this.referent = null;
    }


    /* -- Queue operations -- */

    /**
     * Tells whether or not this reference object has been enqueued, either by
     * the program or by the garbage collector.  If this reference object was
     * not registered with a queue when it was created, then this method will
     * always return <code>false</code>.
     *
     * @return   <code>true</code> if and only if this reference object has
     *           been enqueued
     */
    // 判断当前Reference是否在ReferenceQueue中
    public boolean isEnqueued() {
        return (this.queue == ReferenceQueue.ENQUEUED);
    }

    /**
     * Adds this reference object to the queue with which it is registered,
     * if any.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * enqueues references it does so directly, without invoking this method.
     *
     * @return   <code>true</code> if this reference object was successfully
     *           enqueued; <code>false</code> if it was already enqueued or if
     *           it was not registered with a queue when it was created
     */
    // 取消对目标对象的追踪，并将当前报废的Reference入队，在这个过程中，会回收目标对象
    public boolean enqueue() {
        return this.queue.enqueue(this);
    }


    /* -- Constructors -- */

    // 没有关联ReferenceQueue，意味着用户只需要特殊的引用类型，不关心对象何时被GC
    Reference(T referent) {
        this(referent, null);
    }

    // 传入自定义引用referent和ReferenceQueue，当reference被回收后，会添加到queue中
    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
    }

}
