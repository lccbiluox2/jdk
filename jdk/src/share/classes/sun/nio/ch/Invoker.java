/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.*;
import java.util.concurrent.*;
import java.security.AccessController;
import sun.security.action.GetIntegerAction;

/**
 * Defines static methods to invoke a completion handler or arbitrary task.
 */
/*
 * 回调句柄处理器。
 *
 * 如果某个异步IO操作显式设置了回调句柄，那么在任务执行结束后，会通过此类来处理回调句柄。
 *
 * 参见：CompletionHandler
 */
class Invoker {
    private Invoker() { }

    // maximum number of completion handlers that may be invoked on the current
    // thread before it re-directs invocations to the thread pool. This helps
    // avoid stack overflow and lessens the risk of starvation.
    /*
     * 当前线程上允许递归处理回调句柄的深度。
     * 设置此限制的作用是避免其他工作线程饥饿，而且避免在递归调用中堆栈溢出。
     */
    private static final int maxHandlerInvokeCount = AccessController.doPrivileged(
        new GetIntegerAction("sun.nio.ch.maxCompletionHandlersOnStack", 16));

    // Per-thread object with reference to channel group and a counter for
    // the number of completion handlers invoked. This should be reset to 0
    // when all completion handlers have completed.
    // (递归)调用计数器：每个工作线程(不包括保底线程)都保存一个GroupAndInvokeCount对象
    static class GroupAndInvokeCount {
        // 记录异步通道组引用
        private final AsynchronousChannelGroupImpl group;
        // 记录当前线程的递归调用深度
        private int handlerInvokeCount;
        GroupAndInvokeCount(AsynchronousChannelGroupImpl group) {
            this.group = group;
        }
        AsynchronousChannelGroupImpl group() {
            return group;
        }
        int invokeCount() {
            return handlerInvokeCount;
        }
        void setInvokeCount(int value) {
            handlerInvokeCount = value;
        }
        void resetInvokeCount() {
            handlerInvokeCount = 0;
        }
        void incrementInvokeCount() {
            handlerInvokeCount++;
        }
    }

    /*
     * 除保底线程外，每个工作线程内都持有的局部缓存
     *
     * 参见：AsynchronousChannelGroupImpl#internalThreadCount
     * 　　　AsynchronousChannelGroupImpl#startThreads()
     */
    private static final ThreadLocal<GroupAndInvokeCount> myGroupAndInvokeCount =
        new ThreadLocal<GroupAndInvokeCount>() {
            @Override protected GroupAndInvokeCount initialValue() {
                return null;
            }
        };

    /**
     * Binds this thread to the given group
     */
    // 为当前线程设置一个(递归)调用计数器
    static void bindToGroup(AsynchronousChannelGroupImpl group) {
        // 为当前线程设置一个(递归)调用计数器
        myGroupAndInvokeCount.set(
                // 构造一个调用计数器
                new GroupAndInvokeCount(group));
    }

    /**
     * Returns the GroupAndInvokeCount object for this thread.
     */
    // 返回为当前线程设置的(递归)调用计数器
    static GroupAndInvokeCount getGroupAndInvokeCount() {
        return myGroupAndInvokeCount.get();
    }

    /**
     * Returns true if the current thread is in a channel group's thread pool
     */
    // 判断当前线程是否设置了(递归)调用计数器
    static boolean isBoundToAnyGroup() {
        return myGroupAndInvokeCount.get() != null;
    }

    /**
     * Returns true if the current thread is in the given channel's thread
     * pool and we haven't exceeded the maximum number of handler frames on
     * the stack.
     */
    // 是否允许直接在当前线程中处理回调句柄
    static boolean mayInvokeDirect(GroupAndInvokeCount myGroupAndInvokeCount,
                                   AsynchronousChannelGroupImpl group)
    {
        if ((myGroupAndInvokeCount != null) &&
            (myGroupAndInvokeCount.group() == group) &&
            (myGroupAndInvokeCount.invokeCount() < maxHandlerInvokeCount))
        {
            return true;
        }
        return false;
    }

    /**
     * Invoke handler without checking the thread identity or number of handlers
     * on the thread stack.
     */
    // 直接处理future中记录的回调句柄，不会改变当前线程的递归调用深度
    static <V,A> void invokeUnchecked(CompletionHandler<V,? super A> handler,
                                      A attachment,
                                      V value,
                                      Throwable exc)
    {
        if (exc == null) {
            // 未发生异常
            handler.completed(value, attachment);
        } else {
            // 发生了异常
            handler.failed(exc, attachment);
        }

        // clear interrupt
        // 清除线程的中断状态
        Thread.interrupted();

        // clear thread locals when in default thread pool
        if (System.getSecurityManager() != null) {
            Thread me = Thread.currentThread();
            if (me instanceof sun.misc.InnocuousThread) {
                GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();
                ((sun.misc.InnocuousThread)me).eraseThreadLocals();
                if (thisGroupAndInvokeCount != null) {
                    myGroupAndInvokeCount.set(thisGroupAndInvokeCount);
                }
            }
        }
    }

    /**
     * Invoke handler assuming thread identity already checked
     */
    // 直接处理回调句柄，处理前会先将当前线程的递归调用深度增一
    static <V,A> void invokeDirect(GroupAndInvokeCount myGroupAndInvokeCount,
                                   CompletionHandler<V,? super A> handler,
                                   A attachment,
                                   V result,
                                   Throwable exc)
    {
        myGroupAndInvokeCount.incrementInvokeCount();
        Invoker.invokeUnchecked(handler, attachment, result, exc);
    }

    /**
     * Invokes the handler. If the current thread is in the channel group's
     * thread pool then the handler is invoked directly, otherwise it is
     * invoked indirectly.
     */
    // 处理回调句柄，会视情形进行直接处理或间接处理
    static <V,A> void invoke(AsynchronousChannel channel,
                             CompletionHandler<V,? super A> handler,
                             A attachment,
                             V result,
                             Throwable exc)
    {
        boolean invokeDirect = false; // 理论上可以直接处理回调句柄
        boolean identityOkay = false; // 实际上可以直接处理回调句柄

        // 获取当前线程内的线程局部缓存值
        GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();


        /*
         * 除保底线程外，每个工作线程内都持有的局部缓存。
         * 换句话说，myGroupAndInvokeCount不为null的话，说明当前线程不是保底线程。
         */
        if (thisGroupAndInvokeCount != null) {
            if ((thisGroupAndInvokeCount.group() == ((Groupable)channel).group()))
                identityOkay = true;
            if (identityOkay &&
                (thisGroupAndInvokeCount.invokeCount() < maxHandlerInvokeCount))
            {
                // group match
                invokeDirect = true;
            }
        }

        // 如果允许直接调用(往往是在工作线程中)，则直接处理回调句柄
        if (invokeDirect) {
            // 直接处理回调句柄，处理前会先将当前线程的递归调用深度增一
            invokeDirect(thisGroupAndInvokeCount, handler, attachment, result, exc);
        } else {  // 否则，间接处理回调回调句柄
            try {
                /*
                 * 间接处理回调句柄。
                 * 间接的含义不在当前线程中处理回调句柄，
                 * 而是将给定的回调句柄交给异步IO线程池去处理。
                 */
                invokeIndirectly(channel, handler, attachment, result, exc);
            } catch (RejectedExecutionException ree) {
                // channel group shutdown; fallback to invoking directly
                // if the current thread has the right identity.
                if (identityOkay) {
                    invokeDirect(thisGroupAndInvokeCount,
                                 handler, attachment, result, exc);
                } else {
                    throw new ShutdownChannelGroupException();
                }
            }
        }
    }

    /**
     * Invokes the handler indirectly via the channel group's thread pool.
     */
    /*
     * 间接处理future中记录的回调句柄。
     * 间接的含义不在当前线程中处理回调句柄，
     * 而是将给定的回调句柄交给异步IO线程池去处理。
     */
    static <V,A> void invokeIndirectly(AsynchronousChannel channel,
                                       final CompletionHandler<V,? super A> handler,
                                       final A attachment,
                                       final V result,
                                       final Throwable exc)
    {
        try {
            ((Groupable)channel).group().executeOnPooledThread(new Runnable() {
                public void run() {
                    GroupAndInvokeCount thisGroupAndInvokeCount =
                        myGroupAndInvokeCount.get();
                    if (thisGroupAndInvokeCount != null)
                        thisGroupAndInvokeCount.setInvokeCount(1);
                    // 直接处理回调句柄，不会改变当前线程的递归调用深度
                    invokeUnchecked(handler, attachment, result, exc);
                }
            });
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }

    /**
     * Invokes the handler "indirectly" in the given Executor
     */
    /*
     * 间接处理回调句柄。
     * 间接的含义不在当前线程中处理回调句柄，
     * 而是将给定的回调句柄交给异步IO线程池去处理。
     */
    static <V,A> void invokeIndirectly(final CompletionHandler<V,? super A> handler,
                                       final A attachment,
                                       final V value,
                                       final Throwable exc,
                                       Executor executor)
    {
         try {
            executor.execute(new Runnable() {
                public void run() {
                    invokeUnchecked(handler, attachment, value, exc);
                }
            });
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }

    /**
     * Invokes the given task on the thread pool associated with the given
     * channel. If the current thread is in the thread pool then the task is
     * invoked directly.
     */
    // 处理回调句柄，会视情形进行直接处理或间接处理
    static void invokeOnThreadInThreadPool(Groupable channel,
                                           Runnable task)
    {
        boolean invokeDirect;
        GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();
        AsynchronousChannelGroupImpl targetGroup = channel.group();
        if (thisGroupAndInvokeCount == null) {
            invokeDirect = false;
        } else {
            invokeDirect = (thisGroupAndInvokeCount.group == targetGroup);
        }
        try {
            if (invokeDirect) {
                // 直接处理
                task.run();
            } else {
                /*
                 * 间接处理：让异步IO线程池处理指定的task。
                 *
                 * 如果线程池容量固定，则唤醒正在阻塞的工作线程处理task。
                 * 如果线程池容量不固定，则将task提交到线程池中以启动新的线程来处理task。
                 */
                targetGroup.executeOnPooledThread(task);
            }
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }

    /**
     * Invoke handler with completed result. This method does not check the
     * thread identity or the number of handlers on the thread stack.
     */
    /*
     * 当异步IO操作已有执行结果时，接下来处理future中记录的回调句柄
     *
     * future: "已挂起的结果"，当调用此方法时，future中往往已经填充好了任务结果
     */
    static <V,A> void invokeUnchecked(PendingFuture<V,A> future) {
        assert future.isDone();
        // 获取显式注册的回调句柄
        CompletionHandler<V,? super A> handler = future.handler();
        if (handler != null) {
            // 处理回调句柄，会视情形进行直接处理或间接处理
            invokeUnchecked(handler,
                            future.attachment(),
                            future.value(),
                            future.exception());
        }
    }

    /**
     * Invoke handler with completed result. If the current thread is in the
     * channel group's thread pool then the handler is invoked directly,
     * otherwise it is invoked indirectly.
     */
    static <V,A> void invoke(PendingFuture<V,A> future) {
        assert future.isDone();
        CompletionHandler<V,? super A> handler = future.handler();
        if (handler != null) {
            invoke(future.channel(),
                   handler,
                   future.attachment(),
                   future.value(),
                   future.exception());
        }
    }

    /**
     * Invoke handler with completed result. The handler is invoked indirectly,
     * via the channel group's thread pool.
     */
    static <V,A> void invokeIndirectly(PendingFuture<V,A> future) {
        assert future.isDone();
        CompletionHandler<V,? super A> handler = future.handler();
        if (handler != null) {
            invokeIndirectly(future.channel(),
                             handler,
                             future.attachment(),
                             future.value(),
                             future.exception());
        }
    }
}
