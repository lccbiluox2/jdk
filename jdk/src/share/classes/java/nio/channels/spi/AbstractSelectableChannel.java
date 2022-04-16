/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.*;


/**
 * Base implementation class for selectable channels.
 *
 * <p> This class defines methods that handle the mechanics of channel
 * registration, deregistration, and closing.  It maintains the current
 * blocking mode of this channel as well as its current set of selection keys.
 * It performs all of the synchronization required to implement the {@link
 * java.nio.channels.SelectableChannel} specification.  Implementations of the
 * abstract protected methods defined in this class need not synchronize
 * against other threads that might be engaged in the same operations.  </p>
 *
 *
 * @author Mark Reinhold
 * @author Mike McCloskey
 * @author JSR-51 Expert Group
 * @since 1.4
 */
// 多路复用通道的抽象实现，常用于同步Socket通道
public abstract class AbstractSelectableChannel
    extends SelectableChannel
{

    // The provider that created this channel
    // 当前通道使用的选择器工厂
    private final SelectorProvider provider;

    // Keys that have been created by registering this channel with selectors.
    // They are saved because if this channel is closed the keys must be
    // deregistered.  Protected by keyLock.
    //
    /*
     * "选择键"集合，存储所有隶属于当前通道的"选择键"。
     *
     * 由当前通道发起的每次注册操作中，所有相关参数，包括通道(channel)、选择器(selector)、监听事件(ops)、附属对象(attachment)，
     * 都会被打包成一个"选择键"存储到该"选择键"集合中。
     */
    private SelectionKey[] keys = null;
    // "选择键"集合中已有的"选择键"数量
    private int keyCount = 0;

    // Lock for key set and count
    // 设置通道阻塞模式时使用的锁，参见configureBlocking(boolean)方法
    private final Object keyLock = new Object();

    // Lock for registration and configureBlocking operations
    // 更新已注册的SelectionKey集合时使用的锁
    private final Object regLock = new Object();

    // Blocking mode, protected by regLock
    boolean blocking = true;

    /**
     * Initializes a new instance of this class.
     *
     * @param  provider
     *         The provider that created this channel
     */
    protected AbstractSelectableChannel(SelectorProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     *
     * @return  The provider that created this channel
     */
    // 获取当前通道使用的选择器工厂
    public final SelectorProvider provider() {
        return provider;
    }


    // -- Utility methods for the key set --

    // 向当前通道的"选择键"集合中添加指定的"选择键"
    private void addKey(SelectionKey k) {
        assert Thread.holdsLock(keyLock);

        // 记录"选择键"集合中的找到的空槽下标
        int i = 0;

        // 如果"选择键"集合还不存在，则先新建一个"选择键"集合  // "选择键"集合未满，则找出空槽
        if ((keys != null) && (keyCount < keys.length)) {
            // Find empty element of key array
            // 找出空槽
            for (i = 0; i < keys.length; i++)
                // 找出空位置
                if (keys[i] == null)
                    break;
        } else if (keys == null) {
            // 如果"选择键"集合还不存在，则先新建一个"选择键"集合
            keys =  new SelectionKey[3];
        } else {
            // "选择键"集合已满，则扩容

            // Grow key array
            int n = keys.length * 2;
            SelectionKey[] ks =  new SelectionKey[n];
            for (i = 0; i < keys.length; i++)
                ks[i] = keys[i];
            keys = ks;
            i = keyCount;
        }

        // 添加新的"选择键"
        keys[i] = k;
        // 计数增一
        keyCount++;
    }

    // 搜索隶属于当前通道的所有"选择键"，查看当前通道是否在指定的selector上已经注册过
    private SelectionKey findKey(Selector sel) {
        synchronized (keyLock) {
            // 如果当前通道还未注册过监听事件，则keys为null，这里直接返回
            if (keys == null)
                return null;

            // 遍历隶属于当前通道的所有"选择键"
            for (int i = 0; i < keys.length; i++)
                // 如果某个key的选择器与参数中的选择器匹配，则返回该key
                if ((keys[i] != null) && (keys[i].selector() == sel))
                    return keys[i];
            return null;
        }
    }

    // 从当前通道的"选择键"集合中移除指定的"选择键"，并将该"选择键"标记为无效
    void removeKey(SelectionKey k) {                    // package-private
        synchronized (keyLock) {
            for (int i = 0; i < keys.length; i++)
                if (keys[i] == k) {
                    keys[i] = null;
                    keyCount--;
                }
            // 移除旧的"选择键"之后，将其标记为无效
            ((AbstractSelectionKey)k).invalidate();
        }
    }

    // 判断"选择键"集合中是否存在有效的"选择键"，这里所谓"有效"是指"选择键"未被取消
    private boolean haveValidKeys() {
        synchronized (keyLock) {
            if (keyCount == 0)
                return false;
            for (int i = 0; i < keys.length; i++) {
                if ((keys[i] != null) && keys[i].isValid())
                    return true;
            }
            return false;
        }
    }


    // -- Registration --

    // 判断当前通道是否注册到了某个选择器上（实现方式是判断当前通道上是否有注册的SelectionKey）
    public final boolean isRegistered() {
        synchronized (keyLock) {
            return keyCount != 0;
        }
    }

    // 搜索隶属于当前通道的所有"选择键"，查看当前通道是否在指定的selector上已经注册过（线程安全）
    public final SelectionKey keyFor(Selector sel) {
        return findKey(sel);
    }

    /**
     * Registers this channel with the given selector, returning a selection key.
     *
     * <p>  This method first verifies that this channel is open and that the
     * given initial interest set is valid.
     *
     * <p> If this channel is already registered with the given selector then
     * the selection key representing that registration is returned after
     * setting its interest set to the given value.
     *
     * <p> Otherwise this channel has not yet been registered with the given
     * selector, so the {@link AbstractSelector#register register} method of
     * the selector is invoked while holding the appropriate locks.  The
     * resulting key is added to this channel's key set before being returned.
     * </p>
     *
     * @throws  ClosedSelectorException {@inheritDoc}
     *
     * @throws  IllegalBlockingModeException {@inheritDoc}
     *
     * @throws  IllegalSelectorException {@inheritDoc}
     *
     * @throws  CancelledKeyException {@inheritDoc}
     *
     * @throws  IllegalArgumentException {@inheritDoc}
     */
    /*
     * 当前通道向指定的选择器selector发起注册操作，返回生成的"选择键"
     *
     * 具体的注册行为是：
     * 将通道(channel)、选择器(selector)、监听事件(ops)、附属对象(attachment)这四个属性打包成一个"选择键"对象，
     * 并将该对象分别存储到各个相关的"选择键"集合/队列中，涉及到的"选择键"集合/队列包括：
     *
     * AbstractSelectableChannel -> keys           "选择键"集合
     * SelectorImpl              -> keys           "新注册键集合"
     * WindowsSelectorImpl       -> newKeys        "新注册键临时队列"
     * WindowsSelectorImpl       -> updateKeys     "已更新键临时队列"
     * AbstractSelector          -> cancelledKeys  "已取消键临时集合"
     *
     * 注：需要确保当前通道为非阻塞通道
     */
    public final SelectionKey register(Selector sel, int ops,
                                       Object att)
        throws ClosedChannelException
    {
        synchronized (regLock) {
            // 如果通道未打开，则抛出异常
            if (!isOpen())
                throw new ClosedChannelException();

            // 校验待注册参数是否有效，如果无效则抛出异常
            if ((ops & ~validOps()) != 0)
                throw new IllegalArgumentException();

            // 处于阻塞模式下的通道无法注册到选择器
            if (blocking)
                throw new IllegalBlockingModeException();

            // 搜索隶属于当前通道的所有"选择键"，查看当前通道是否在指定的selector上已经注册过
            SelectionKey k = findKey(sel);

            // 如果当前通道已经在selector上注册过
            if (k != null) {
                /*
                 *【覆盖更新】当前选择键内的监听事件，如果新旧事件不同，则将当前"选择键"加入到选择器的"已更新键临时队列"(updateKeys)中。
                 * 事件注册完成后，返回当前"选择键"。
                 *
                 * 参见：WindowsSelectorImpl#updateKeys()
                 */
                k.interestOps(ops);
                // 向"选择键"对象设置附属对象(attachment)属性
                k.attach(att);
            }

            // 如果当前通道是首次在selector上注册
            if (k == null) {
                // New registration
                synchronized (keyLock) {
                    if (!isOpen())
                        throw new ClosedChannelException();

                    // 通道channel向当前选择器发起注册操作，返回生成的"选择键"
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    // 向当前通道的"选择键"集合中添加指定的"选择键"
                    addKey(k);
                }
            }
            return k;
        }
    }


    // -- Closing --

    /**
     * Closes this channel.
     *
     * <p> This method, which is specified in the {@link
     * AbstractInterruptibleChannel} class and is invoked by the {@link
     * java.nio.channels.Channel#close close} method, in turn invokes the
     * {@link #implCloseSelectableChannel implCloseSelectableChannel} method in
     * order to perform the actual work of closing this channel.  It then
     * cancels all of this channel's keys.  </p>
     */
    // 将通道标记为关闭状态后，再调用此方法执行资源清理操作
    protected final void implCloseChannel() throws IOException {
        // 实现对"可选择"通道的关闭操作
        implCloseSelectableChannel();
        synchronized (keyLock) {
            int count = (keys == null) ? 0 : keys.length;
            for (int i = 0; i < count; i++) {
                SelectionKey k = keys[i];
                if (k != null)
                    // 取消"选择键"selectionKey，取消之后其状态变为无效
                    k.cancel();
            }
        }
    }

    /**
     * Closes this selectable channel.
     *
     * <p> This method is invoked by the {@link java.nio.channels.Channel#close
     * close} method in order to perform the actual work of closing the
     * channel.  This method is only invoked if the channel has not yet been
     * closed, and it is never invoked more than once.
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in an I/O operation upon this channel to return
     * immediately, either by throwing an exception or by returning normally.
     * </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    // 实现对"可选择"通道的关闭操作
    protected abstract void implCloseSelectableChannel() throws IOException;


    // -- Blocking --

    // 当前通道是否为阻塞模式
    public final boolean isBlocking() {
        synchronized (regLock) {
            return blocking;
        }
    }

    // 获取设置通道阻塞模式时使用的锁，常用于Socket适配器中
    public final Object blockingLock() {
        return regLock;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> If the given blocking mode is different from the current blocking
     * mode then this method invokes the {@link #implConfigureBlocking
     * implConfigureBlocking} method, while holding the appropriate locks, in
     * order to change the mode.  </p>
     */
    // 是否设置当前通道为阻塞模式
    public final SelectableChannel configureBlocking(boolean block)
        throws IOException
    {
        synchronized (regLock) {
            if (!isOpen())
                throw new ClosedChannelException();

            // 获取通道当前的阻塞模式
            // 如果不需要改变阻塞模式，直接返回
            if (blocking == block)
                return this;

            // 如果需要更改通道的阻塞模式，但该通道已经是注册在某个选择器上的阻塞通道，则抛出异常
            if (block && haveValidKeys())
                throw new IllegalBlockingModeException();

            // 更新当前通道的阻塞模式
            implConfigureBlocking(block);
            blocking = block;
        }
        return this;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> This method is invoked by the {@link #configureBlocking
     * configureBlocking} method in order to perform the actual work of
     * changing the blocking mode.  This method is only invoked if the new mode
     * is different from the current mode.  </p>
     *
     * @param  block  If <tt>true</tt> then this channel will be placed in
     *                blocking mode; if <tt>false</tt> then it will be placed
     *                non-blocking mode
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    // 是否设置当前通道为阻塞模式
    protected abstract void implConfigureBlocking(boolean block)
        throws IOException;

}
