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
import java.util.Collection;

/**
 * An implementation of {@link ReadWriteLock} supporting similar
 * semantics to {@link ReentrantLock}.
 * <p>This class has the following properties:
 *
 * {@link ReadWriteLock}的实现支持与{@link ReentrantLock}类似的语义。
 *
 * 这个类有以下属性:
 *
 * <ul>
 * <li><b>Acquisition order</b>
 *
 * <p>This class does not impose a reader or writer preference
 * ordering for lock access.  However, it does support an optional
 * <em>fairness</em> policy.
 *
 * 这个类不强制锁访问的reader或writer偏好排序。但是，它确实支持一个可选的公平性策略。
 *
 * <dl>
 * <dt><b><i>Non-fair mode (default)</i></b>  非公平模式 默认
 *
 * <dd>When constructed as non-fair (the default), the order of entry
 * to the read and write lock is unspecified, subject to reentrancy
 * constraints.  A nonfair lock that is continuously contended may
 * indefinitely postpone one or more reader or writer threads, but
 * will normally have higher throughput than a fair lock.
 *
 * 当构造为非公平锁(默认值)时，读和写锁的入口顺序是不指定的，受重入约束。持续争用
 * 的非公平锁可以无限期地推迟一个或多个读线程或写线程，但通常会比公平锁具有更高的吞吐量。
 *
 * <dt><b><i>Fair mode</i></b> 公平模式
 *
 * <dd>When constructed as fair, threads contend for entry using an
 * approximately arrival-order policy. When the currently held lock
 * is released, either the longest-waiting single writer thread will
 * be assigned the write lock, or if there is a group of reader threads
 * waiting longer than all waiting writer threads, that group will be
 * assigned the read lock.
 *
 * 当被构造为公平时，线程使用近似到达顺序策略来争夺入口。当当前持有的锁被释放时，
 * 要么为等待时间最长的单个写线程分配写锁，要么为一组等待时间超过所有等待写线程的
 * 读线程分配读锁。
 *
 * <p>A thread that tries to acquire a fair read lock (non-reentrantly)
 * will block if either the write lock is held, or there is a waiting
 * writer thread. The thread will not acquire the read lock until
 * after the oldest currently waiting writer thread has acquired and
 * released the write lock. Of course, if a waiting writer abandons
 * its wait, leaving one or more reader threads as the longest waiters
 * in the queue with the write lock free, then those readers will be
 * assigned the read lock.
 *
 * 试图获得公平读锁(不可重入)的线程，如果持有写锁，或者有一个正在等待的写线程，则会阻塞。
 * 在当前等待的最老的写线程获得并释放写锁之后，线程才会获得读锁。当然，如果一个正在等待
 * 的写线程放弃了它的等待，留下一个或多个读线程作为队列中最长的等待线程，并且没有写锁，
 * 那么这些读线程将被分配读锁。
 *
 * <p>A thread that tries to acquire a fair write lock (non-reentrantly)
 * will block unless both the read lock and write lock are free (which
 * implies there are no waiting threads).  (Note that the non-blocking
 * {@link ReadLock#tryLock()} and {@link WriteLock#tryLock()} methods
 * do not honor this fair setting and will immediately acquire the lock
 * if it is possible, regardless of waiting threads.)
 * <p>
 * </dl>
 *
 * 试图获得公平写锁(非重入)的线程将阻塞，除非读锁和写锁都是空闲的(这意味着没有等待线程)。
 * (注意，非阻塞的{@link ReadLock#tryLock()}和{@link WriteLock#tryLock()}方法
 * 不尊重这种公平设置，如果可能，将立即获取锁，无论等待线程。)
 *
 * <li><b>Reentrancy</b>
 *
 * <p>This lock allows both readers and writers to reacquire read or
 * write locks in the style of a {@link ReentrantLock}. Non-reentrant
 * readers are not allowed until all write locks held by the writing
 * thread have been released.
 *
 * 这个锁允许读取器和写入器重新获得{@link ReentrantLock}风格的读或写锁。在写
 * 线程持有的所有写锁被释放之前，不允许不可重入的读取器。
 *
 * <p>Additionally, a writer can acquire the read lock, but not
 * vice-versa.  Among other applications, reentrancy can be useful
 * when write locks are held during calls or callbacks to methods that
 * perform reads under read locks.  If a reader tries to acquire the
 * write lock it will never succeed.
 *
 * 此外，一个写入器可以获得读锁，但反之则不行。在其他应用程序中，当在调用或回调在读锁
 * 下执行读操作的方法期间持有写锁时，可重入性可能很有用。如果读取器试图获取写锁，它将
 * 永远不会成功。
 *
 * <li><b>Lock downgrading</b>  锁降级
 *
 * <p>Reentrancy also allows downgrading from the write lock to a read lock,
 * by acquiring the write lock, then the read lock and then releasing the
 * write lock. However, upgrading from a read lock to the write lock is
 * <b>not</b> possible.
 *
 * 重入性还允许将写锁降级为读锁，方法是先获取写锁，然后获取读锁，然后释放写锁。但是，
 * 从读锁升级到写锁是而不是可能。
 *
 * <li><b>Interruption of lock acquisition</b>  锁获取中断
 *
 * <p>The read lock and write lock both support interruption during lock
 * acquisition.
 *
 * 读锁和写锁在获取锁时都支持中断。
 *
 * <li><b>{@link Condition} support</b> Condition 支持
 *
 * <p>The write lock provides a {@link Condition} implementation that
 * behaves in the same way, with respect to the write lock, as the
 * {@link Condition} implementation provided by
 * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
 * This {@link Condition} can, of course, only be used with the write lock.
 *
 * 写锁提供了一个{@link Condition}的实现，它的行为与{@link ReentrantLock#newCondition}
 * 提供的{@link Condition}实现的{@link ReentrantLock}的行为相同。当然，这个{@link
 * Condition}只能用于写锁。
 *
 * <p>The read lock does not support a {@link Condition} and
 * {@code readLock().newCondition()} throws
 * {@code UnsupportedOperationException}.
 *
 * 读锁不支持{@link Condition}和{@code readLock().newcondition()}
 * throws {@code UnsupportedOperationException}。
 *
 * <li><b>Instrumentation</b> 仪表
 *
 * <p>This class supports methods to determine whether locks
 * are held or contended. These methods are designed for monitoring
 * system state, not for synchronization control.
 * </ul>
 *
 * 该类支持确定锁是被持有还是被争用的方法。这些方法是为了监控系统状态而设计的，
 * 而不是为了同步控制。
 *
 * <p>Serialization of this class behaves in the same way as built-in
 * locks: a deserialized lock is in the unlocked state, regardless of
 * its state when serialized.
 *
 * 该类的序列化行为与内置锁相同:反序列化的锁处于解锁状态，而不管它在序列化时的状态如何。
 *
 * <p><b>Sample usages</b>. Here is a code sketch showing how to perform
 * lock downgrading after updating a cache (exception handling is
 * particularly tricky when handling multiple locks in a non-nested
 * fashion):
 *
 * 示例用法。下面是一个代码草图，展示了如何在更新缓存后执行锁降级(异常处理在非嵌套方式
 * 处理多个锁时特别棘手):
 *
 * <pre> {@code
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *       // Must release read lock before acquiring write lock
 *       rwl.readLock().unlock();
 *       rwl.writeLock().lock();
 *       try {
 *         // Recheck state because another thread might have
 *         // acquired write lock and changed state before we did.
 *         if (!cacheValid) {
 *           data = ...
 *           cacheValid = true;
 *         }
 *         // Downgrade by acquiring read lock before releasing write lock
 *         rwl.readLock().lock();
 *       } finally {
 *         rwl.writeLock().unlock(); // Unlock write, still hold read
 *       }
 *     }
 *
 *     try {
 *       use(data);
 *     } finally {
 *       rwl.readLock().unlock();
 *     }
 *   }
 * }}</pre>
 *
 * ReentrantReadWriteLocks can be used to improve concurrency in some
 * uses of some kinds of Collections. This is typically worthwhile
 * only when the collections are expected to be large, accessed by
 * more reader threads than writer threads, and entail operations with
 * overhead that outweighs synchronization overhead. For example, here
 * is a class using a TreeMap that is expected to be large and
 * concurrently accessed.
 *
 * reentrtreadwritelocks可以用于在某些类型的集合的某些使用中提高并发性。这通常只在以下
 * 情况下是值得的:期望集合很大，由更多的读线程访问而不是写线程，并且需要操作的开销大于同步
 * 开销。例如，这里有一个使用TreeMap的类，它被认为是大的并且可以并发访问的。
 *
 *  <pre> {@code
 * class RWDictionary {
 *   private final Map<String, Data> m = new TreeMap<String, Data>();
 *   private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *   private final Lock r = rwl.readLock();
 *   private final Lock w = rwl.writeLock();
 *
 *   public Data get(String key) {
 *     r.lock();
 *     try { return m.get(key); }
 *     finally { r.unlock(); }
 *   }
 *   public String[] allKeys() {
 *     r.lock();
 *     try { return m.keySet().toArray(); }
 *     finally { r.unlock(); }
 *   }
 *   public Data put(String key, Data value) {
 *     w.lock();
 *     try { return m.put(key, value); }
 *     finally { w.unlock(); }
 *   }
 *   public void clear() {
 *     w.lock();
 *     try { m.clear(); }
 *     finally { w.unlock(); }
 *   }
 * }}</pre>
 *
 * <h3>Implementation Notes</h3>
 *
 * <p>This lock supports a maximum of 65535 recursive write locks
 * and 65535 read locks. Attempts to exceed these limits result in
 * {@link Error} throws from locking methods.
 *
 * 最大支持65535个递归写锁和65535个读锁。试图超过这些限制会导致锁方法抛出{@link Error}。
 *
 * @since 1.5
 * @author Doug Lea
 *
 * 说明: 可以看到，ReentrantReadWriteLock实现了ReadWriteLock接口，ReadWriteLock
 * 接口定义了获取读锁和写锁的规范，具体需要实现类去实现；同时其还实现了Serializable接口，
 * 表示可以进行序列化，在源代码中可以看到ReentrantReadWriteLock实现了自己的序列化逻辑。
 *
 *
 * 写锁的降级，降级成为 读锁
 * 1. 如果一个线程持有写锁，在不释放写锁的情况下，它还可以继续持有读锁，
 *    这种情况就是写锁的降级，降级成为读锁
 * 2. 规则遵循:先获取写锁，然后获取读锁，再释放写锁的次序
 * 3. 如果释放了写锁，那么就完全转换为读锁
 *
 * todo: 不支持锁的升级
 *
 * 读锁、与锁的互斥规则:
 * 1:读-读共享，意味着他们都可以拿到锁，也就是共享锁。
 * 2:读-写互斥
 * 3:写-读互斥
 * 4:写-写互斥
 */
public class ReentrantReadWriteLock
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    /** Inner class providing readlock */
    // 读锁
    private final ReentrantReadWriteLock.ReadLock readerLock;
    /** Inner class providing writelock */
    // 写锁
    private final ReentrantReadWriteLock.WriteLock writerLock;
    /** Performs all synchronization mechanics */
    // 同步队列
    final Sync sync;

    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * default (nonfair) ordering properties.
     */
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * the given fairness policy.
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        // 公平策略或者是非公平策略
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);// 读锁
        writerLock = new WriteLock(this); // 写锁
    }

    public ReentrantReadWriteLock.WriteLock writeLock() { return writerLock; }
    public ReentrantReadWriteLock.ReadLock  readLock()  { return readerLock; }

    /**
     * Synchronization implementation for ReentrantReadWriteLock.
     * Subclassed into fair and nonfair versions.
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;

        /*
         * Read vs write count extraction constants and functions.
         * Lock state is logically divided into two unsigned shorts:
         * The lower one representing the exclusive (writer) lock hold count,
         * and the upper the shared (reader) hold count.
         *
         * 读vs写计数提取常量和函数。锁状态在逻辑上分为两个unsigned short:
         * 下一个代表独占(写)锁保持计数，上一个代表共享(读)锁保持计数。
         */

        /***
         * 先看看这个类需要保存哪些状态？
         * 1 :写锁的重入次数
         * 2 :读锁的个数
         * 3 :每个读锁重入的次数
         *
         * 这看起来要有三个值呀，但是AQS框架是只有一个值来表达状态，这矛盾就来了呀。
         *
         * 这里为啥做的这么麻烦？为毛要分为2个部分，1部分不行吗？
         * 原因就是AQS框架是只有一个值来表达状态。volatile int state  这个值是int类型的。
         * 然后如果不使用这个的话，自己需要自己实现一套AQS，但是相当于代码复写了一遍，没有什么
         * 特别有用的，然后Doug Lea老爷子就想到了把int分开来使用。而这里恰好发现可以分为高低16位来表示。
         * 这里不得不说 Doug Lea老爷子 牛逼  牛逼  牛逼  牛逼 。。。九师兄  牛逼
         *
         *
         * Int state 32位，
         * 表示成二进制，前面16位表示读锁的同步状态【读锁个数+重入次数】,后面16位表示写锁的同步状态【写锁个数+重入次数】
         *
         * 1     ---      0000 0000 0000 0000 0000 0000 0000 0001
         *
         * 65536 2的16次方
         * 1 <<16     --- 0000 0000 0000 0001 0000 0000 0000 0000
         * 655354
         * (1<<16) -1 --- 0000 0000 0000 0000 1111 1111 1111 1111
         */
        // 高16位为读锁，低16位为写锁
        static final int SHARED_SHIFT   = 16;
        // 读锁单位                        0000 0000 0000 0001   0000 0000 0000 0000
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        // 读锁最大数量                     0000 0000 0000 0000   1111 1111 1111 1111  65535
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
        // 写锁最大数量                     0000 0000 0000 0000   1111 1111 1111 1111  65535
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /** Returns the number of shared holds represented in count
         *
         * 表示占有读锁的线程数量
         *
         * 说明: 直接将state右移16位，就可以得到读锁的线程数量，因为state的高16位表示读锁，
         * 对应的低十六位表示写锁数量。
         *
         */
        static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
        /** Returns the number of exclusive holds represented in count
         *
         * 表示占有写锁的线程数量
         *
         * 说明: 直接将状态state和(2^16 - 1)做与运算，其等效于将state模上2^16。
         * 写锁数量由state的低十六位表示。
         * */
        static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

        /**
         * A counter for per-thread read hold counts.
         * Maintained as a ThreadLocal; cached in cachedHoldCounter
         *
         * Sync类内部存在两个内部类，分别为HoldCounter和ThreadLocalHoldCounter，其中
         * HoldCounter主要与读锁配套使用
         *
         * 计数器,说明: HoldCounter主要有两个属性，count和tid，
         * 其中count表示某个读线程重入的次数，
         * tid表示该线程的tid字段的值，该字段可以用来唯一标识一个线程。
         */
        static final class HoldCounter {
            // 计数
            int count = 0;
            // Use id, not reference, to avoid garbage retention
            // 获取当前线程的TID属性的值
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * ThreadLocal subclass. Easiest to explicitly define for sake
         * of deserialization mechanics.
         *
         * 本地线程计数器
         *
         * 说明: ThreadLocalHoldCounter重写了ThreadLocal的initialValue方法，
         * ThreadLocal类可以将线程与对象相关联。在没有进行set的情况下，get到的均
         * 是initialValue方法里面生成的那个HolderCounter对象。
         */
        static final class ThreadLocalHoldCounter
            extends ThreadLocal<HoldCounter> {
            // 重写初始化方法，在没有进行set的情况下，获取的都是该HoldCounter值
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         * The number of reentrant read locks held by current thread.
         * Initialized only in constructor and readObject.
         * Removed whenever a thread's read hold count drops to 0.
         *
         * 本地线程计数器
         */
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * The hold count of the last thread to successfully acquire
         * readLock. This saves ThreadLocal lookup in the common case
         * where the next thread to release is the last one to
         * acquire. This is non-volatile since it is just used
         * as a heuristic, and would be great for threads to cache.
         *
         * <p>Can outlive the Thread for which it is caching the read
         * hold count, but avoids garbage retention by not retaining a
         * reference to the Thread.
         *
         * <p>Accessed via a benign data race; relies on the memory
         * model's final field and out-of-thin-air guarantees.
         *
         * 缓存的计数器
         */
        private transient HoldCounter cachedHoldCounter;

        /**
         * firstReader is the first thread to have acquired the read lock.
         * firstReaderHoldCount is firstReader's hold count.
         *
         * <p>More precisely, firstReader is the unique thread that last
         * changed the shared count from 0 to 1, and has not released the
         * read lock since then; null if there is no such thread.
         *
         * <p>Cannot cause garbage retention unless the thread terminated
         * without relinquishing its read locks, since tryReleaseShared
         * sets it to null.
         *
         * <p>Accessed via a benign data race; relies on the memory
         * model's out-of-thin-air guarantees for references.
         *
         * <p>This allows tracking of read holds for uncontended read
         * locks to be very cheap.
         *
         * 第一个读线程,firstReader是第一个获得读锁的线程。firstReaderHoldCount是firstReader的保持计数。
         *
         * 更准确地说，firstReader是最后一次将共享计数从0更改为1的惟一线程，并且从那以后
         * 一直没有释放读锁;如果没有这样的线程，则为空。
         *
         * 不能导致垃圾保留，除非线程终止而不放弃其读锁，因为tryreleasshared将其设置为null。
         *
         * 通过良性数据竞争访问;依赖于内存模型对引用的out- thin-air保证。
         *
         * 这使得跟踪非争用读锁的读持有非常便宜。
         */
        private transient Thread firstReader = null;
        // 第一个读线程的计数
        private transient int firstReaderHoldCount;

        /**
         * 说明: 在Sync的构造函数中设置了本地线程计数器和AQS的状态state。
         */
        Sync() {
            // 本地线程计数器
            readHolds = new ThreadLocalHoldCounter();
            // 设置AQS的状态
            setState(getState()); // ensures visibility of readHolds
        }

        /*
         * Acquires and releases use the same code for fair and
         * nonfair locks, but differ in whether/how they allow barging
         * when queues are non-empty.
         */

        /**
         * Returns true if the current thread, when trying to acquire
         * the read lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean readerShouldBlock();

        /**
         * Returns true if the current thread, when trying to acquire
         * the write lock, and otherwise eligible to do so, should block
         * because of policy for overtaking other waiting threads.
         */
        abstract boolean writerShouldBlock();

        /*
         * Note that tryRelease and tryAcquire can be called by
         * Conditions. So it is possible that their arguments contain
         * both read and write holds that are all released during a
         * condition wait and re-established in tryAcquire.
         */

        /**
         * 明: 此函数用于释放写锁资源，首先会判断该线程是否为独占线程，若不为独占线程，
         * 则抛出异常，否则，计算释放资源后的写锁的数量，若为0，表示成功释放，资源不将
         * 被占用，否则，表示资源还被占用。
         *
         * 参考图：doc/images/lock/java-thread-x-readwritelock-2.png
         * 参考：https://www.pdai.tech/md/java/thread/java-thread-x-lock-ReentrantReadWriteLock.html
         */
        protected final boolean tryRelease(int releases) {
            // 判断是否伪独占线程,判断当前线程是否为独占线程
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 计算释放资源后的写锁的数量  = 当前状态 - 你这次要释放的状态
            int nextc = getState() - releases;
            // 是否释放成功,如果等于0 代码写锁减完了 意味着没有写锁了
            boolean free = exclusiveCount(nextc) == 0;
            if (free)
                setExclusiveOwnerThread(null);// 设置独占线程为空
            setState(nextc); // 设置状态
            return free;
        }

        /**
         * 链接：https://www.pdai.tech/md/java/thread/java-thread-x-lock-ReentrantReadWriteLock.html
         *
         * 说明: 此函数用于获取写锁，首先会获取state，判断是否为0，若为0，表示此时没有读锁线程，
         * 再判断写线程是否应该被阻塞，而在非公平策略下总是不会被阻塞，在公平策略下会进行判断(判断
         * 同步队列中是否有等待时间更长的线程，若存在，则需要被阻塞，否则，无需阻塞)，之后在设置
         * 状态state，然后返回true。
         *  若state不为0，则表示此时存在读锁或写锁线程
         *  若写锁线程数量为0或者当前线程为独占锁线程，则返回false，表示不成功，
         *  否则，判断写锁线程的重入次数是否大于了最大值，
         *  若是，则抛出异常，
         *  否则，设置状态state，返回true，表示成功。
         *
         * 参考：doc/images/lock/java-thread-x-readwritelock-3.png
         * @param acquires
         * @return
         */
        protected final boolean tryAcquire(int acquires) {
            /*
             * Walkthrough:
             * 1. If read count nonzero or write count nonzero
             *    and owner is a different thread, fail.
             * 2. If count would saturate, fail. (This can only
             *    happen if count is already nonzero.)
             * 3. Otherwise, this thread is eligible for lock if
             *    it is either a reentrant acquire or
             *    queue policy allows it. If so, update state
             *    and set owner.
             *
             * 预排：
             * 1. 如果读计数非零或写计数非零且所有者是不同的线程，则失败。
             * 2. 如果计数饱和，则失败。(这只会发生在count已经是非零的情况下。)
             * 3. 否则，如果该线程是可重入获取或队列策略允许，则该线程有资格获得锁。
             *    如果是，更新state并设置owner。
             */
            // 获取当前线程
            Thread current = Thread.currentThread();
            int c = getState();// 获取写锁当前的同步状态
            int w = exclusiveCount(c);// 写线程数量 互斥线程的个数
            if (c != 0) { // 状态不为0 代表有写锁竞争
                // (Note: if c != 0 and w == 0 then shared count != 0)
                /**
                 * 写线程数量为0或者当前线程没有占有独占资源，怎么感觉和If条件冲突了
                 * 上面 c != 0 说明有读锁或者写锁
                 * 那么 w == 0 代表有读锁，有读锁的时候，是不允许获取写锁的，所以直接返回
                 * 如果 w != 0 那么就意味着有写锁，就会判断 current != getExclusiveOwnerThread() 这
                 *     个说当前线程还不是当前持有锁的线程，说明不是持有锁的线程重入这个锁，说明是其他的线程
                 *     因为是写锁，是独占锁，直接返回、【你厕所拉粑粑的时候是不希望别人进来的】
                  */
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                /**
                 * 如果上面的那个if过了，说明线程持有写锁，并且是重入
                 */

                // 判断是否超过最高写线程数量
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // Reentrant acquire
                // c + acquires 一般都是 c+1 重入次数加1
                setState(c + acquires); // 设置AQS状态
                return true;
            }

            // 走到这里说明 c == 0  代表没有锁竞争，读锁和写锁都是空的

            /**
             * writerShouldBlock() 先判读是不是需要阻塞
             * 非公平锁 writerShouldBlock 一直是false 一定会执行后面的
             */
            if (writerShouldBlock() ||
                !compareAndSetState(c, c + acquires)) // 写线程是否应该被阻塞
                return false;
            setExclusiveOwnerThread(current);  // 设置独占线程
            return true;
        }

        /**
         * 链接：https://www.pdai.tech/md/java/thread/java-thread-x-lock-ReentrantReadWriteLock.html
         *
         * 说明: 此函数表示读锁线程释放锁。
         *  首先判断当前线程是否为第一个读线程firstReader，
         *   若是， 则判断第一个读线程占有的资源数firstReaderHoldCount是否为1，
         *      若是，则设置第一个读线程firstReader为空，
         *      否则，将第一个读线程占有的资源数firstReaderHoldCount减1；
         *   若当前线程不是第一个读线程，那么首先会获取缓存计数器(上一个读锁线程对应的计数器 )，
         *   若计数器为空或者tid不等于当前线程的tid值，则获取当前线程的计数器，
         *   如果计数器的计数count小于等于1，则移除当前线程对应的计数器，
         *   如果计数器的计数count小于等于0，则抛出异常，之后再减少计数即可。
         *
         *   无论何种情况，都会进入无限循环，该循环可以确保成功设置状态state。其流程图如下
         *
         * unused ： 注意这个参数  意思是这个参数是有没有使用的
         */
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread(); // 获取当前线程
            if (firstReader == current) { // 当前线程为第一个读线程
                // assert firstReaderHoldCount > 0;
                if (firstReaderHoldCount == 1) // 读线程占用的资源数为1
                    firstReader = null;
                else
                    firstReaderHoldCount--; // 减少占用的资源
            } else {
                // 当前线程不为第一个读线程

                HoldCounter rh = cachedHoldCounter;  // 获取缓存的计数器
                // 计数器为空或者计数器的tid不为当前正在运行的线程的tid
                if (rh == null || rh.tid != current.getId())
                    rh = readHolds.get();  // 获取当前线程对应的计数器
                int count = rh.count; // 获取计数
                if (count <= 1) {  // 计数小于等于1
                    readHolds.remove(); // 移除
                    if (count <= 0) // 计数小于等于0，抛出异常
                        throw unmatchedUnlockException();
                }
                --rh.count; // 减少计数
            }
            for (;;) { // 无限循环
                int c = getState(); // 获取状态
                int nextc = c - SHARED_UNIT;  // 获取状态
                if (compareAndSetState(c, nextc)) // 比较并进行设置
                    // Releasing the read lock has no effect on readers,
                    // but it may allow waiting writers to proceed if
                    // both read and write locks are now free.
                    return nextc == 0;
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                "attempt to unlock read lock, not locked by current thread");
        }

        /**
         * 共享模式下获取资源
         *
         * 链接：https://www.pdai.tech/md/java/thread/java-thread-x-lock-ReentrantReadWriteLock.html
         *
         * 说明: 此函数表示读锁线程获取读锁。
         * 首先判断写锁是否为0并且当前线程不占有独占锁，直接返回；
         * 否则，判断读线程是否需要被阻塞并且读锁数量是否小于最大值并且比较设置状态成功，
         *   若当前没有读锁，则设置第一个读线程firstReader和firstReaderHoldCount；
         *   若当前线程线程为第一个读线程，则增加firstReaderHoldCount；
         *   否则，将设置当前线程对应的HoldCounter对象的值。
         *
         * doc/images/lock/java-thread-x-readwritelock-5.png
         *
         * 视频：https://www.bilibili.com/video/BV1Zq4y1k7TH/?spm_id_from=pageDriver
         */
        protected final int tryAcquireShared(int unused) {
            /*
             * Walkthrough:
             * 1. If write lock held by another thread, fail.
             * 2. Otherwise, this thread is eligible for
             *    lock wrt state, so ask if it should block
             *    because of queue policy. If not, try
             *    to grant by CASing state and updating count.
             *    Note that step does not check for reentrant
             *    acquires, which is postponed to full version
             *    to avoid having to check hold count in
             *    the more typical non-reentrant case.
             * 3. If step 2 fails either because thread
             *    apparently not eligible or CAS fails or count
             *    saturated, chain to version with full retry loop.
             *
             * 预排
             * 1. 如果写锁被另一个线程持有，则失败。
             * 2. 否则，这个线程有资格获得锁wrt状态，所以询问它是否应该因为队列策略而阻塞。
             *    如果没有，尝试通过CASing state和更新计数。注意，step没有检查可重入获取，
             *    这被推迟到完整版本，以避免在更典型的不可重入情况下必须检查hold count。
             * 3.如果步骤2失败，因为线程显然不合格或CAS失败或计数饱和，则使用完整的重试循环链接到版本。
             */
            Thread current = Thread.currentThread();
            int c = getState(); // 当前状态
            if (exclusiveCount(c) != 0 && // 写锁的个数
                getExclusiveOwnerThread() != current) // 写线程数不为0并且占有资源的不是当前线程
                return -1;
            int r = sharedCount(c); // 读锁的个数
            if (!readerShouldBlock() &&
                r < MAX_COUNT &&
                compareAndSetState(c, c + SHARED_UNIT)) { // 读线程是否应该被阻塞、并且小于最大值、并且比较设置成功
                if (r == 0) { // 读锁数量为0
                    firstReader = current; // 设置第一个读线程
                    firstReaderHoldCount = 1; // 读线程占用的资源数为1
                } else if (firstReader == current) { // 当前线程为第一个读线程【判断是不是第一个线程的重入】
                    firstReaderHoldCount++; // 占用资源数加1
                } else {
                    // 读锁数量不为0并且不为当前线程

                    HoldCounter rh = cachedHoldCounter;  // 获取计数器
                    // 计数器为空或者计数器的tid不为当前正在运行的线程的tid
                    if (rh == null || rh.tid != current.getId())
                        // 获取当前线程对应的计数器
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)  // 计数为0
                        readHolds.set(rh); // 设置
                    rh.count++;
                }
                // 返回1 表示拿到锁
                return 1;
            }
            return fullTryAcquireShared(current);
        }

        /**
         * Full version of acquire for reads, that handles CAS misses
         * and reentrant reads not dealt with in tryAcquireShared.
         *
         * 说明: 在tryAcquireShared函数中，如果下列三个条件不满足(读线程是否应该被阻塞、
         * 小于最大值、比较设置成功)则会进行fullTryAcquireShared函数中，它用来保证相关
         * 操作可以成功。
         */
        final int fullTryAcquireShared(Thread current) {
            /*
             * This code is in part redundant with that in
             * tryAcquireShared but is simpler overall by not
             * complicating tryAcquireShared with interactions between
             * retries and lazily reading hold counts.
             */
            HoldCounter rh = null;
            for (;;) {// 无限循环
                int c = getState();// 获取状态
                if (exclusiveCount(c) != 0) { // 写线程数量不为0
                    // 不为当前线程
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    // else we hold the exclusive lock; blocking here
                    // would cause deadlock.
                } else if (readerShouldBlock()) {
                    /**
                     * 写线程数量为0并且读线程被阻塞
                     *
                     * 这一段的总体作用，就是如果是新的线程来请求读锁的话，那么对不起直接返回
                     * 但是老的线程来的话，可重入
                     */
                    // Make sure we're not acquiring read lock reentrantly
                    // 当前线程为第一个读线程 ，如果是第一个线程，无论是读锁还是写锁都可以重入
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        // 当前线程不为第一个读线程
                        if (rh == null) { // 计数器不为空
                            rh = cachedHoldCounter;
                            // 计数器为空或者计数器的tid不为当前正在运行的线程的tid
                            if (rh == null || rh.tid != current.getId()) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)
                            return -1;
                    }
                }
                // 读锁数量为最大值，抛出异常
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) { // 比较并且设置成功
                    // 表示是第一个线程
                    if (sharedCount(c) == 0) { // 读线程数量为0
                        // 设置第一个读线程
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) { // 表示重入
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;// 重入数量+1
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
         * Performs tryLock for write, enabling barging in both modes.
         * This is identical in effect to tryAcquire except for lack
         * of calls to writerShouldBlock.
         */
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c != 0) {
                int w = exclusiveCount(c);
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            if (!compareAndSetState(c, c + 1))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * Performs tryLock for read, enabling barging in both modes.
         * This is identical in effect to tryAcquireShared except for
         * lack of calls to readerShouldBlock.
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (;;) {
                int c = getState();
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        /**
         * 虽然通常我们必须在owner之前读取state，但是我们不需要这样做来检查当前线程是否为owner
         * @return
         */
        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            // Must read state before owner to ensure memory consistency
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }

        final int getReadLockCount() {
            return sharedCount(getState());
        }

        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() { return getState(); }
    }

    /**
     * Nonfair version of Sync
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
        final boolean writerShouldBlock() {
            return false; // writers can always barge
        }
        final boolean readerShouldBlock() {
            /* As a heuristic to avoid indefinite writer starvation,
             * block if the thread that momentarily appears to be head
             * of queue, if one exists, is a waiting writer.  This is
             * only a probabilistic effect since a new reader will not
             * block if there is a waiting writer behind other enabled
             * readers that have not yet drained from the queue.
             *
             * 作为一种避免无限期写入器饥饿的启发式方法，如果暂时出现在队列头的线程
             * (如果存在)是一个等待写入器，则阻塞该线程。这只是一个概率效应，因为如果
             * 在其他启用的读器后面有一个等待的写器，而这个读器还没有从队列中抽干，那么
             * 一个新的读器就不会阻塞。
             *
             * 大概是明显的看你第一个节点是独占节点
             */
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * Fair version of Sync
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;
        final boolean writerShouldBlock() {
            // 一句话就是等待队列中有没有节点 有的话就去排队去
            return hasQueuedPredecessors();
        }
        final boolean readerShouldBlock() {
            // 一句话就是等待队列中有没有节点 有的话就去排队去
            return hasQueuedPredecessors();
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#readLock}.
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * Acquires the read lock.
         *
         * <p>Acquires the read lock if the write lock is not held by
         * another thread and returns immediately.
         *
         * <p>If the write lock is held by another thread then
         * the current thread becomes disabled for thread scheduling
         * purposes and lies dormant until the read lock has been acquired.
         */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         * Acquires the read lock unless the current thread is
         * {@linkplain Thread#interrupt interrupted}.
         *
         * <p>Acquires the read lock if the write lock is not held
         * by another thread and returns immediately.
         *
         * <p>If the write lock is held by another thread then the
         * current thread becomes disabled for thread scheduling
         * purposes and lies dormant until one of two things happens:
         *
         * <ul>
         *
         * <li>The read lock is acquired by the current thread; or
         *
         * <li>Some other thread {@linkplain Thread#interrupt interrupts}
         * the current thread.
         *
         * </ul>
         *
         * <p>If the current thread:
         *
         * <ul>
         *
         * <li>has its interrupted status set on entry to this method; or
         *
         * <li>is {@linkplain Thread#interrupt interrupted} while
         * acquiring the read lock,
         *
         * </ul>
         *
         * then {@link InterruptedException} is thrown and the current
         * thread's interrupted status is cleared.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock.
         *
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * Acquires the read lock only if the write lock is not held by
         * another thread at the time of invocation.
         *
         * <p>Acquires the read lock if the write lock is not held by
         * another thread and returns immediately with the value
         * {@code true}. Even when this lock has been set to use a
         * fair ordering policy, a call to {@code tryLock()}
         * <em>will</em> immediately acquire the read lock if it is
         * available, whether or not other threads are currently
         * waiting for the read lock.  This &quot;barging&quot; behavior
         * can be useful in certain circumstances, even though it
         * breaks fairness. If you want to honor the fairness setting
         * for this lock, then use {@link #tryLock(long, TimeUnit)
         * tryLock(0, TimeUnit.SECONDS) } which is almost equivalent
         * (it also detects interruption).
         *
         * <p>If the write lock is held by another thread then
         * this method will return immediately with the value
         * {@code false}.
         *
         * @return {@code true} if the read lock was acquired
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * Acquires the read lock if the write lock is not held by
         * another thread within the given waiting time and the
         * current thread has not been {@linkplain Thread#interrupt
         * interrupted}.
         *
         * <p>Acquires the read lock if the write lock is not held by
         * another thread and returns immediately with the value
         * {@code true}. If this lock has been set to use a fair
         * ordering policy then an available lock <em>will not</em> be
         * acquired if any other threads are waiting for the
         * lock. This is in contrast to the {@link #tryLock()}
         * method. If you want a timed {@code tryLock} that does
         * permit barging on a fair lock then combine the timed and
         * un-timed forms together:
         *
         *  <pre> {@code
         * if (lock.tryLock() ||
         *     lock.tryLock(timeout, unit)) {
         *   ...
         * }}</pre>
         *
         * <p>If the write lock is held by another thread then the
         * current thread becomes disabled for thread scheduling
         * purposes and lies dormant until one of three things happens:
         *
         * <ul>
         *
         * <li>The read lock is acquired by the current thread; or
         *
         * <li>Some other thread {@linkplain Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified waiting time elapses.
         *
         * </ul>
         *
         * <p>If the read lock is acquired then the value {@code true} is
         * returned.
         *
         * <p>If the current thread:
         *
         * <ul>
         *
         * <li>has its interrupted status set on entry to this method; or
         *
         * <li>is {@linkplain Thread#interrupt interrupted} while
         * acquiring the read lock,
         *
         * </ul> then {@link InterruptedException} is thrown and the
         * current thread's interrupted status is cleared.
         *
         * <p>If the specified waiting time elapses then the value
         * {@code false} is returned.  If the time is less than or
         * equal to zero, the method will not wait at all.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock, and over reporting the elapse of the waiting time.
         *
         * @param timeout the time to wait for the read lock
         * @param unit the time unit of the timeout argument
         * @return {@code true} if the read lock was acquired
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if the time unit is null
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * Attempts to release this lock.
         *
         * <p>If the number of readers is now zero then the lock
         * is made available for write lock attempts.
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         * Throws {@code UnsupportedOperationException} because
         * {@code ReadLocks} do not support conditions.
         *
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a string identifying this lock, as well as its lock state.
         * The state, in brackets, includes the String {@code "Read locks ="}
         * followed by the number of held read locks.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#writeLock}.
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * Acquires the write lock.
         *
         * <p>Acquires the write lock if neither the read nor write lock
         * are held by another thread
         * and returns immediately, setting the write lock hold count to
         * one.
         *
         * <p>If the current thread already holds the write lock then the
         * hold count is incremented by one and the method returns
         * immediately.
         *
         * <p>If the lock is held by another thread then the current
         * thread becomes disabled for thread scheduling purposes and
         * lies dormant until the write lock has been acquired, at which
         * time the write lock hold count is set to one.
         */
        public void lock() {
            sync.acquire(1);
        }

        /**
         * Acquires the write lock unless the current thread is
         * {@linkplain Thread#interrupt interrupted}.
         *
         * <p>Acquires the write lock if neither the read nor write lock
         * are held by another thread
         * and returns immediately, setting the write lock hold count to
         * one.
         *
         * <p>If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * immediately.
         *
         * <p>If the lock is held by another thread then the current
         * thread becomes disabled for thread scheduling purposes and
         * lies dormant until one of two things happens:
         *
         * <ul>
         *
         * <li>The write lock is acquired by the current thread; or
         *
         * <li>Some other thread {@linkplain Thread#interrupt interrupts}
         * the current thread.
         *
         * </ul>
         *
         * <p>If the write lock is acquired by the current thread then the
         * lock hold count is set to one.
         *
         * <p>If the current thread:
         *
         * <ul>
         *
         * <li>has its interrupted status set on entry to this method;
         * or
         *
         * <li>is {@linkplain Thread#interrupt interrupted} while
         * acquiring the write lock,
         *
         * </ul>
         *
         * then {@link InterruptedException} is thrown and the current
         * thread's interrupted status is cleared.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock.
         *
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
         * Acquires the write lock only if it is not held by another thread
         * at the time of invocation.
         *
         * <p>Acquires the write lock if neither the read nor write lock
         * are held by another thread
         * and returns immediately with the value {@code true},
         * setting the write lock hold count to one. Even when this lock has
         * been set to use a fair ordering policy, a call to
         * {@code tryLock()} <em>will</em> immediately acquire the
         * lock if it is available, whether or not other threads are
         * currently waiting for the write lock.  This &quot;barging&quot;
         * behavior can be useful in certain circumstances, even
         * though it breaks fairness. If you want to honor the
         * fairness setting for this lock, then use {@link
         * #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS) }
         * which is almost equivalent (it also detects interruption).
         *
         * <p>If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * {@code true}.
         *
         * <p>If the lock is held by another thread then this method
         * will return immediately with the value {@code false}.
         *
         * @return {@code true} if the lock was free and was acquired
         * by the current thread, or the write lock was already held
         * by the current thread; and {@code false} otherwise.
         */
        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        /**
         * Acquires the write lock if it is not held by another thread
         * within the given waiting time and the current thread has
         * not been {@linkplain Thread#interrupt interrupted}.
         *
         * <p>Acquires the write lock if neither the read nor write lock
         * are held by another thread
         * and returns immediately with the value {@code true},
         * setting the write lock hold count to one. If this lock has been
         * set to use a fair ordering policy then an available lock
         * <em>will not</em> be acquired if any other threads are
         * waiting for the write lock. This is in contrast to the {@link
         * #tryLock()} method. If you want a timed {@code tryLock}
         * that does permit barging on a fair lock then combine the
         * timed and un-timed forms together:
         *
         *  <pre> {@code
         * if (lock.tryLock() ||
         *     lock.tryLock(timeout, unit)) {
         *   ...
         * }}</pre>
         *
         * <p>If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * {@code true}.
         *
         * <p>If the lock is held by another thread then the current
         * thread becomes disabled for thread scheduling purposes and
         * lies dormant until one of three things happens:
         *
         * <ul>
         *
         * <li>The write lock is acquired by the current thread; or
         *
         * <li>Some other thread {@linkplain Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified waiting time elapses
         *
         * </ul>
         *
         * <p>If the write lock is acquired then the value {@code true} is
         * returned and the write lock hold count is set to one.
         *
         * <p>If the current thread:
         *
         * <ul>
         *
         * <li>has its interrupted status set on entry to this method;
         * or
         *
         * <li>is {@linkplain Thread#interrupt interrupted} while
         * acquiring the write lock,
         *
         * </ul>
         *
         * then {@link InterruptedException} is thrown and the current
         * thread's interrupted status is cleared.
         *
         * <p>If the specified waiting time elapses then the value
         * {@code false} is returned.  If the time is less than or
         * equal to zero, the method will not wait at all.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock, and over reporting the elapse of the waiting time.
         *
         * @param timeout the time to wait for the write lock
         * @param unit the time unit of the timeout argument
         *
         * @return {@code true} if the lock was free and was acquired
         * by the current thread, or the write lock was already held by the
         * current thread; and {@code false} if the waiting time
         * elapsed before the lock could be acquired.
         *
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if the time unit is null
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * Attempts to release this lock.
         *
         * <p>If the current thread is the holder of this lock then
         * the hold count is decremented. If the hold count is now
         * zero then the lock is released.  If the current thread is
         * not the holder of this lock then {@link
         * IllegalMonitorStateException} is thrown.
         *
         * @throws IllegalMonitorStateException if the current thread does not
         * hold this lock
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         * Returns a {@link Condition} instance for use with this
         * {@link Lock} instance.
         * <p>The returned {@link Condition} instance supports the same
         * usages as do the {@link Object} monitor methods ({@link
         * Object#wait() wait}, {@link Object#notify notify}, and {@link
         * Object#notifyAll notifyAll}) when used with the built-in
         * monitor lock.
         *
         * <ul>
         *
         * <li>If this write lock is not held when any {@link
         * Condition} method is called then an {@link
         * IllegalMonitorStateException} is thrown.  (Read locks are
         * held independently of write locks, so are not checked or
         * affected. However it is essentially always an error to
         * invoke a condition waiting method when the current thread
         * has also acquired read locks, since other threads that
         * could unblock it will not be able to acquire the write
         * lock.)
         *
         * <li>When the condition {@linkplain Condition#await() waiting}
         * methods are called the write lock is released and, before
         * they return, the write lock is reacquired and the lock hold
         * count restored to what it was when the method was called.
         *
         * <li>If a thread is {@linkplain Thread#interrupt interrupted} while
         * waiting then the wait will terminate, an {@link
         * InterruptedException} will be thrown, and the thread's
         * interrupted status will be cleared.
         *
         * <li> Waiting threads are signalled in FIFO order.
         *
         * <li>The ordering of lock reacquisition for threads returning
         * from waiting methods is the same as for threads initially
         * acquiring the lock, which is in the default case not specified,
         * but for <em>fair</em> locks favors those threads that have been
         * waiting the longest.
         *
         * </ul>
         *
         * @return the Condition object
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         * Returns a string identifying this lock, as well as its lock
         * state.  The state, in brackets includes either the String
         * {@code "Unlocked"} or the String {@code "Locked by"}
         * followed by the {@linkplain Thread#getName name} of the owning thread.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                                       "[Unlocked]" :
                                       "[Locked by thread " + o.getName() + "]");
        }

        /**
         * Queries if this write lock is held by the current thread.
         * Identical in effect to {@link
         * ReentrantReadWriteLock#isWriteLockedByCurrentThread}.
         *
         * @return {@code true} if the current thread holds this lock and
         *         {@code false} otherwise
         * @since 1.6
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * Queries the number of holds on this write lock by the current
         * thread.  A thread has a hold on a lock for each lock action
         * that is not matched by an unlock action.  Identical in effect
         * to {@link ReentrantReadWriteLock#getWriteHoldCount}.
         *
         * @return the number of holds on this lock by the current thread,
         *         or zero if this lock is not held by the current thread
         * @since 1.6
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return {@code true} if any thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the write lock by the current thread,
     *         or zero if the write lock is not held by the current thread
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Queries the number of reentrant read holds on this lock by the
     * current thread.  A reader thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the read lock by the current thread,
     *         or zero if the read lock is not held by the current thread
     * @since 1.6
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting to acquire the read or
     * write lock. Note that because cancellations may occur at any
     * time, a {@code true} return does not guarantee that any other
     * thread will ever acquire a lock.  This method is designed
     * primarily for use in monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire either
     * the read or write lock. Note that because cancellations may
     * occur at any time, a {@code true} return does not guarantee
     * that this thread will ever acquire a lock.  This method is
     * designed primarily for use in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * either the read or write lock.  The value is only an estimate
     * because the number of threads may change dynamically while this
     * method traverses internal data structures.  This method is
     * designed for use in monitoring of the system state, not for
     * synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire either the read or write lock.  Because the actual set
     * of threads may change dynamically while constructing this
     * result, the returned collection is only a best-effort estimate.
     * The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with the write lock. Note that because timeouts and
     * interrupts may occur at any time, a {@code true} return does
     * not guarantee that a future {@code signal} will awaken any
     * threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with the write lock. Note that because
     * timeouts and interrupts may occur at any time, the estimate
     * serves only as an upper bound on the actual number of waiters.
     * This method is designed for use in monitoring of the system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with the write lock.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a
     * best-effort estimate. The elements of the returned collection
     * are in no particular order.  This method is designed to
     * facilitate construction of subclasses that provide more
     * extensive condition monitoring facilities.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes the String {@code "Write locks ="}
     * followed by the number of reentrantly held write locks, and the
     * String {@code "Read locks ="} followed by the number of held
     * read locks.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
            "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * Returns the thread id for the given thread.  We must access
     * this directly rather than via method Thread.getId() because
     * getId() is not final, and has been known to be overridden in
     * ways that do not preserve unique mappings.
     */
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TID_OFFSET;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
