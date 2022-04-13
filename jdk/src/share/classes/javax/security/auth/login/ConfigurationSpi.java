/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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


package javax.security.auth.login;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the {@code Configuration} class.
 * All the abstract methods in this class must be implemented by each
 * service provider who wishes to supply a Configuration implementation.
 *
 * <p> Subclass implementations of this abstract class must provide
 * a public constructor that takes a {@code Configuration.Parameters}
 * object as an input parameter.  This constructor also must throw
 * an IllegalArgumentException if it does not understand the
 * {@code Configuration.Parameters} input.
 *
 * 这个类定义了{@code Configuration}类的服务提供者接口(Service Provider Interface, SPI)。
 * 该类中的所有抽象方法都必须由希望提供Configuration实现的每个服务提供者实现。
 *
 * 这个抽象类的子类实现必须提供一个公共构造函数，该构造函数接受一个{@code Configuration。
 * 对象作为输入参数。如果构造函数不理解输入参数{@code Configuration]，它也必须抛出一个
 * IllegalArgumentException。}。
 *
 *
 * @since 1.6
 */

public abstract class ConfigurationSpi {
    /**
     * Retrieve the AppConfigurationEntries for the specified <i>name</i>.
     *
     * 检索指定name的AppConfigurationEntries。
     *
     * <p>
     *
     * @param name the name used to index the Configuration.
     *
     * @return an array of AppConfigurationEntries for the specified
     *          <i>name</i>, or null if there are no entries.
     */
    protected abstract AppConfigurationEntry[] engineGetAppConfigurationEntry
                                                        (String name);

    /**
     * Refresh and reload the Configuration.
     *
     * <p> This method causes this Configuration object to refresh/reload its
     * contents in an implementation-dependent manner.
     * For example, if this Configuration object stores its entries in a file,
     * calling {@code refresh} may cause the file to be re-read.
     *
     * <p> The default implementation of this method does nothing.
     * This method should be overridden if a refresh operation is supported
     * by the implementation.
     *
     * @exception SecurityException if the caller does not have permission
     *          to refresh its Configuration.
     *
     * 刷新并重新加载配置。
     *
     * 此方法导致这个Configuration对象以一种依赖于实现的方式刷新/重新加载其内容。例如，
     * 如果这个Configuration对象将它的条目存储在一个文件中，调用{@code refresh}可能
     * 会导致该文件被重新读取。

     * 此方法的默认实现不执行任何操作。如果实现支持刷新操作，则应重写此方法。如果调用者
     * 没有刷新配置的权限，则SecurityException异常。
     */
    protected void engineRefresh() { }
}
