/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

/**
 * WeakCleanable subclasses efficiently encapsulate cleanup state and
 * the cleaning action.
 * Subclasses implement the abstract {@link #performCleanup()}  method
 * to provide the cleaning action.
 * When constructed, the object reference and the {@link Cleaner.Cleanable Cleanable}
 * are registered with the {@link Cleaner}.
 * The Cleaner invokes {@link Cleaner.Cleanable#clean() clean} after the
 * referent becomes weakly reachable.
 */

/*
 * Reference
 *     │
 * WeakReference   Cleanable
 *     └──────┬────────┘
 *         WeakCleanable
 *
 * 抽象基类：可清理的弱引用，此类的属性既是弱引用，又是清理器。
 */
public abstract class WeakCleanable<T> extends WeakReference<T> implements Cleaner.Cleanable {
    
    /**
     * The list of WeakCleanable; synchronizes insert and remove.
     */
    // 指向CleanerImpl内部维护的WeakCleanableList，在这里完成插入和删除操作
    private final WeakCleanable<?> list;
    
    /**
     * Links to previous and next in a doubly-linked list.
     */
    // 前驱、后继
    WeakCleanable<?> prev = this, next = this;
    
    /**
     * Constructs new {@code WeakCleanableReference} with
     * {@code non-null referent} and {@code non-null cleaner}.
     * The {@code cleaner} is not retained by this reference; it is only used
     * to register the newly constructed {@link Cleaner.Cleanable Cleanable}.
     *
     * @param referent the referent to track
     * @param cleaner  the {@code Cleaner} to register new reference with
     */
    // 向cleaner注册跟踪的对象referent
    public WeakCleanable(T referent, Cleaner cleaner) {
        super(Objects.requireNonNull(referent), CleanerImpl.getCleanerImpl(cleaner).queue);
        list = CleanerImpl.getCleanerImpl(cleaner).weakCleanableList;
        insert();
        
        // Ensure referent and cleaner remain accessible
        Reference.reachabilityFence(referent);
        Reference.reachabilityFence(cleaner);
        
    }
    
    /**
     * Construct a new root of the list; not inserted.
     */
    WeakCleanable() {
        super(null, null);
        this.list = this;
    }
    
    /**
     * The {@code performCleanup} abstract method is overridden
     * to implement the cleaning logic.
     * The {@code performCleanup} method should not be called except
     * by the {@link #clean} method which ensures at most once semantics.
     */
    // 弱引用清理器的清理操作，被clean()方法调用，由子类完善
    protected abstract void performCleanup();
    
    /**
     * Unregister this WeakCleanable reference and invoke {@link #performCleanup()},
     * ensuring at-most-once semantics.
     */
    /*
     * CleanerImpl.run()==>清理器clean()-->清理器performCleanup()-->action.run()
     * 可以等待清理清理服务自动调用，也可以手动执行清理操作
     */
    @Override
    public final void clean() {
        if(remove()) {
            super.clear();
            performCleanup();
        }
    }
    
    /**
     * This method always throws {@link UnsupportedOperationException}.
     * Enqueuing details of {@link Cleaner.Cleanable}
     * are a private implementation detail.
     *
     * @throws UnsupportedOperationException always
     */
    // 禁止在此处执行
    @Override
    public final boolean isEnqueued() {
        throw new UnsupportedOperationException("isEnqueued");
    }
    
    /**
     * This method always throws {@link UnsupportedOperationException}.
     * Enqueuing details of {@link Cleaner.Cleanable}
     * are a private implementation detail.
     *
     * @throws UnsupportedOperationException always
     */
    // 禁止在此处执行
    @Override
    public final boolean enqueue() {
        throw new UnsupportedOperationException("enqueue");
    }
    
    /**
     * Unregister this WeakCleanable and clear the reference.
     * Due to inherent concurrency, {@link #performCleanup()} may still be invoked.
     */
    // 清理弱引用追踪的对象的引用
    @Override
    public void clear() {
        if(remove()) {
            super.clear();
        }
    }
    
    /**
     * Returns true if the list's next reference refers to itself.
     *
     * @return true if the list is empty
     */
    // 判断WeakCleanableList是否为空
    boolean isListEmpty() {
        synchronized(list) {
            return list == list.next;
        }
    }
    
    /**
     * Insert this WeakCleanableReference after the list head.
     */
    // 将该弱引用（清理器）插入到WeakCleanableList中
    private void insert() {
        synchronized(list) {
            prev = list;
            next = list.next;
            next.prev = this;
            list.next = this;
        }
    }
    
    /**
     * Remove this WeakCleanableReference from the list.
     *
     * @return true if Cleanable was removed or false if not because
     * it had already been removed before
     */
    // 将该弱引用（清理器）从WeakCleanableList中移除。原因是ReferenceQueue中已记录到该引用属于"报废引用"了。
    private boolean remove() {
        synchronized(list) {
            if(next != this) {
                next.prev = prev;
                prev.next = next;
                prev = this;
                next = this;
                return true;
            }
            return false;
        }
    }
}
