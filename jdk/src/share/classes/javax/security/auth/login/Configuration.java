/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.security.auth.AuthPermission;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.util.Objects;

import sun.security.jca.GetInstance;

/**
 * A Configuration object is responsible for specifying which LoginModules
 * should be used for a particular application, and in what order the
 * LoginModules should be invoked.
 *
 * <p> A login configuration contains the following information.
 * Note that this example only represents the default syntax for the
 * {@code Configuration}.  Subclass implementations of this class
 * may implement alternative syntaxes and may retrieve the
 * {@code Configuration} from any source such as files, databases,
 * or servers.
 *
 * Configuration对象负责指定应该为特定的应用程序使用哪些loginmodule，以及应该以什么
 * 顺序调用loginmodule。
 *
 * 登录配置包含如下信息。注意，这个例子只代表了{@code Configuration}的默认语法。这个类
 * 的子类实现可以实现其他语法，可以从任何源(如文件、数据库或服务器)检索{@code Configuration}。
 *
 * <pre>
 *      Name {
 *            ModuleClass  Flag    ModuleOptions;
 *            ModuleClass  Flag    ModuleOptions;
 *            ModuleClass  Flag    ModuleOptions;
 *      };
 *      Name {
 *            ModuleClass  Flag    ModuleOptions;
 *            ModuleClass  Flag    ModuleOptions;
 *      };
 *      other {
 *            ModuleClass  Flag    ModuleOptions;
 *            ModuleClass  Flag    ModuleOptions;
 *      };
 * </pre>
 *
 * <p> Each entry in the {@code Configuration} is indexed via an
 * application name, <i>Name</i>, and contains a list of
 * LoginModules configured for that application.  Each {@code LoginModule}
 * is specified via its fully qualified class name.
 * Authentication proceeds down the module list in the exact order specified.
 * If an application does not have a specific entry,
 * it defaults to the specific entry for "<i>other</i>".
 *
 * {@code Configuration}中的每个条目都是通过一个应用程序名称(name)建立索引的，并包含一个
 * 为该应用程序配置的LoginModules列表。每个{@code LoginModule}都是通过它的全限定类名指定的。
 * 身份验证按照指定的准确顺序在模块列表中进行。如果应用程序没有特定的条目，它默认为“other”的
 * 特定条目。
 *
 * <p> The <i>Flag</i> value controls the overall behavior as authentication
 * proceeds down the stack.  The following represents a description of the
 * valid values for <i>Flag</i> and their respective semantics:
 *
 * 当身份验证沿着堆栈向下进行时，Flag值控制整个行为。下面描述了Flag的有效值及其各自的语义:
 *
 * <pre>
 *      1) Required     - The {@code LoginModule} is required to succeed.
 *                      If it succeeds or fails, authentication still continues
 *                      to proceed down the {@code LoginModule} list.
 *                      —{@code LoginModule} 需要成功。无论验证成功或失败，身份验证仍然
 *                      会沿着{@code LoginModule}列表继续进行。
 *
 *      2) Requisite    - The {@code LoginModule} is required to succeed.
 *                      If it succeeds, authentication continues down the
 *                      {@code LoginModule} list.  If it fails,
 *                      control immediately returns to the application
 *                      (authentication does not proceed down the
 *                      {@code LoginModule} list).
 *
 *                      —{@code LoginModule} 需要成功。如果成功，身份验证会沿着
 *                      {@code LoginModule}列表继续。如果失败，控制立即返回到
 *                      应用程序(身份验证不会沿着{@code LoginModule}列表进行)。
 *
 *      3) Sufficient   - The {@code LoginModule} is not required to
 *                      succeed.  If it does succeed, control immediately
 *                      returns to the application (authentication does not
 *                      proceed down the {@code LoginModule} list).
 *                      If it fails, authentication continues down the
 *                      {@code LoginModule} list.
 *
 *                      LoginModule并不强制要求成功。如果它成功了，控制立即返回到
 *                      应用程序(身份验证不会沿着{@code LoginModule}列表进行)。
 *                      如果失败，身份验证将继续在{@code LoginModule}列表中进行。
 *
 *      4) Optional     - The {@code LoginModule} is not required to
 *                      succeed.  If it succeeds or fails,
 *                      authentication still continues to proceed down the
 *                      {@code LoginModule} list.
 *
 *                      LoginModule并不强制要求成功。无论验证成功或失败，身份验证仍然
 *                      会沿着{@code LoginModule}列表继续进行。
 * </pre>
 *
 * <p> The overall authentication succeeds only if all <i>Required</i> and
 * <i>Requisite</i> LoginModules succeed.  If a <i>Sufficient</i>
 * {@code LoginModule} is configured and succeeds,
 * then only the <i>Required</i> and <i>Requisite</i> LoginModules prior to
 * that <i>Sufficient</i> {@code LoginModule} need to have succeeded for
 * the overall authentication to succeed. If no <i>Required</i> or
 * <i>Requisite</i> LoginModules are configured for an application,
 * then at least one <i>Sufficient</i> or <i>Optional</i>
 * {@code LoginModule} must succeed.
 *
 * 只有当所有必需的和必需的loginmodule都成功时，整个身份验证才会成功。如果配置了足够的
 * {@code LoginModule}并成功了，那么只有在此之前的Required和Requisite LoginModule
 * 才需要成功，整个身份验证才会成功。如果没有为应用程序配置必需或必需的LoginModule，
 * 则必须至少有一个充分或可选{@code LoginModule}成功。
 *
 * <p> <i>ModuleOptions</i> is a space separated list of
 * {@code LoginModule}-specific values which are passed directly to
 * the underlying LoginModules.  Options are defined by the
 * {@code LoginModule} itself, and control the behavior within it.
 * For example, a {@code LoginModule} may define options to support
 * debugging/testing capabilities.  The correct way to specify options in the
 * {@code Configuration} is by using the following key-value pairing:
 * <i>debug="true"</i>.  The key and value should be separated by an
 * 'equals' symbol, and the value should be surrounded by double quotes.
 * If a String in the form, ${system.property}, occurs in the value,
 * it will be expanded to the value of the system property.
 * Note that there is no limit to the number of
 * options a {@code LoginModule} may define.
 *
 * ModuleOptions是一个用空格分隔的列表，包含了特定于{@code LoginModule}的值，
 * 这些值直接被传递给底层的LoginModule。选项是由{@code LoginModule}本身定义的，
 * 并控制其中的行为。例如，{@code LoginModule}可以定义支持调试/测试功能的选项。
 * 在{@code Configuration}中指定选项的正确方法是使用以下键值配对:debug="true"。
 * 键和值应该用“等于”符号分隔，值应该用双引号包围。如果一个字符串的形式，${system.property}
 * 中出现的值，则将其扩展为系统属性的值。注意，{@code LoginModule}可以定义的
 * 选项的数量是没有限制的。
 *
 * <p> The following represents an example {@code Configuration} entry
 * based on the syntax above:
 *
 * 根据上面的语法，下面是一个示例{@code Configuration}条目:
 *
 * <pre>
 * Login {
 *   com.sun.security.auth.module.UnixLoginModule required;
 *   com.sun.security.auth.module.Krb5LoginModule optional
 *                   useTicketCache="true"
 *                   ticketCache="${user.home}${/}tickets";
 * };
 * </pre>
 *
 * <p> This {@code Configuration} specifies that an application named,
 * "Login", requires users to first authenticate to the
 * <i>com.sun.security.auth.module.UnixLoginModule</i>, which is
 * required to succeed.  Even if the <i>UnixLoginModule</i>
 * authentication fails, the
 * <i>com.sun.security.auth.module.Krb5LoginModule</i>
 * still gets invoked.  This helps hide the source of failure.
 * Since the <i>Krb5LoginModule</i> is <i>Optional</i>, the overall
 * authentication succeeds only if the <i>UnixLoginModule</i>
 * (<i>Required</i>) succeeds.
 *
 * <p> Also note that the LoginModule-specific options,
 * <i>useTicketCache="true"</i> and
 * <i>ticketCache=${user.home}${/}tickets"</i>,
 * are passed to the <i>Krb5LoginModule</i>.
 * These options instruct the <i>Krb5LoginModule</i> to
 * use the ticket cache at the specified location.
 * The system properties, <i>user.home</i> and <i>/</i>
 * (file.separator), are expanded to their respective values.
 *
 * 这个{@code Configuration}指定了一个名为“Login”的应用程序，要求用户首先验证到
 * com.sun.security.auth.module.UnixLoginModule，这是成功所必需的。
 * 即使UnixLoginModule的身份验证失败，com.sun.security.auth.module.Krb5LoginModule
 * 仍然会被调用。这有助于隐藏失败的来源。因为Krb5LoginModule是可选的，
 * 所以只有当UnixLoginModule (Required)成功时，整个身份验证才会成功。
 *
 *
 *
 * 还要注意loginmodule特定的选项，useTicketCache="true"和ticketCache=${user.home}${/}tickets"，
 * 被传递给Krb5LoginModule。这些选项指示Krb5LoginModule在指定位置使用票据缓存。
 * 系统属性，user.home and / (file.separator), 展开为它们各自的值。
 *
 * <p> There is only one Configuration object installed in the runtime at any
 * given time.  A Configuration object can be installed by calling the
 * {@code setConfiguration} method.  The installed Configuration object
 * can be obtained by calling the {@code getConfiguration} method.
 *
 * 在任何给定的时间，运行时中只安装一个Configuration对象。一个Configuration对象
 * 可以通过调用{@code setConfiguration}方法来安装。安装的Configuration对象
 * 可以通过调用{@code getConfiguration}方法获得。
 *
 * <p> If no Configuration object has been installed in the runtime, a call to
 * {@code getConfiguration} installs an instance of the default
 * Configuration implementation (a default subclass implementation of this
 * abstract class).
 * The default Configuration implementation can be changed by setting the value
 * of the {@code login.configuration.provider} security property to the fully
 * qualified name of the desired Configuration subclass implementation.
 *
 * 如果运行时中没有安装Configuration对象，调用{@code getConfiguration}会安装一个默认
 * Configuration实现的实例(这个抽象类的默认子类实现)。默认的Configuration实现可以通过
 * 设置{@code login.configuration.provider}安全属性设置为所需Configuration子类
 * 实现的完全限定名。
 *
 * <p> Application code can directly subclass Configuration to provide a custom
 * implementation.  In addition, an instance of a Configuration object can be
 * constructed by invoking one of the {@code getInstance} factory methods
 * with a standard type.  The default policy type is "JavaLoginConfig".
 * See the Configuration section in the <a href=
 * "{@docRoot}/../technotes/guides/security/StandardNames.html#Configuration">
 * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
 * for a list of standard Configuration types.
 *
 * 应用程序代码可以直接子类化Configuration以提供自定义实现。另外，一个Configuration对象
 * 的实例可以通过调用一个标准类型的{@code getInstance}工厂方法来构造。默认的策略类型是
 * “JavaLoginConfig”。有关标准配置类型的列表，请参阅Java加密体系结构标准算法名称文档
 * 中的配置部分。
 *
 * @see javax.security.auth.login.LoginContext
 * @see java.security.Security security properties
 */
public abstract class Configuration {

    private static Configuration configuration;

    private final java.security.AccessControlContext acc =
            java.security.AccessController.getContext();

    private static void checkPermission(String type) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new AuthPermission
                                ("createLoginConfiguration." + type));
        }
    }

    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected Configuration() { }

    /**
     * Get the installed login Configuration.
     *
     * <p>
     *
     * @return the login Configuration.  If a Configuration object was set
     *          via the {@code Configuration.setConfiguration} method,
     *          then that object is returned.  Otherwise, a default
     *          Configuration object is returned.
     *
     * @exception SecurityException if the caller does not have permission
     *                          to retrieve the Configuration.
     *
     * @see #setConfiguration
     */
    public static Configuration getConfiguration() {

        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(new AuthPermission("getLoginConfiguration"));

        synchronized (Configuration.class) {
            if (configuration == null) {
                String config_class = null;
                config_class = AccessController.doPrivileged
                    (new PrivilegedAction<String>() {
                    public String run() {
                        // 第一步：sun.security.provider.ConfigFile 获取参数
                        return java.security.Security.getProperty
                                    ("login.configuration.provider");
                    }
                });
                if (config_class == null) {
                    config_class = "sun.security.provider.ConfigFile";
                }

                try {
                    final String finalClass = config_class;
                    Configuration untrustedImpl = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<Configuration>() {
                                public Configuration run() throws ClassNotFoundException,
                                        InstantiationException,
                                        IllegalAccessException {
                                    // 第二步 实例化类 sun.security.provider.ConfigFile
                                    Class<? extends Configuration> implClass = Class.forName(
                                            finalClass, false,
                                            Thread.currentThread().getContextClassLoader()
                                    ).asSubclass(Configuration.class);
                                    /**
                                     * 实例化类
                                     * 1. 会调用 sun.security.provider.ConfigFile#ConfigFile() 空的构造方法
                                     * 2. 然后调用 sun.security.provider.ConfigFile.Spi#Spi()
                                     * 3. 然后调用 sun.security.provider.ConfigFile.Spi#init()
                                     */
                                    return implClass.newInstance();
                                }
                            });
                    AccessController.doPrivileged(
                            new PrivilegedExceptionAction<Void>() {
                                public Void run() {
                                    setConfiguration(untrustedImpl);
                                    return null;
                                }
                            }, Objects.requireNonNull(untrustedImpl.acc)
                    );
                } catch (PrivilegedActionException e) {
                    Exception ee = e.getException();
                    if (ee instanceof InstantiationException) {
                        throw (SecurityException) new
                            SecurityException
                                    ("Configuration error:" +
                                     ee.getCause().getMessage() +
                                     "\n").initCause(ee.getCause());
                    } else {
                        throw (SecurityException) new
                            SecurityException
                                    ("Configuration error: " +
                                     ee.toString() +
                                     "\n").initCause(ee);
                    }
                }
            }
            return configuration;
        }
    }

    /**
     * Set the login {@code Configuration}.
     *
     * <p>
     *
     * @param configuration the new {@code Configuration}
     *
     * @exception SecurityException if the current thread does not have
     *                  Permission to set the {@code Configuration}.
     *
     * @see #getConfiguration
     */
    public static void setConfiguration(Configuration configuration) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(new AuthPermission("setLoginConfiguration"));
        Configuration.configuration = configuration;
    }

    /**
     * Returns a Configuration object of the specified type.
     *
     * 返回指定类型的Configuration对象。
     *
     * <p> This method traverses the list of registered security providers,
     * starting with the most preferred Provider.
     * A new Configuration object encapsulating the
     * ConfigurationSpi implementation from the first
     * Provider that supports the specified type is returned.
     *
     * 此方法遍历已注册的安全提供程序列表，从最首选的提供程序开始。返回一个新的Configuration
     * 对象，该对象封装了来自支持指定类型的第一个提供程序的ConfigurationSpi实现。
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * 注意，已经注册的提供商列表可以通过{@link Security#getProviders()
     * Security.getProviders()}方法来检索。
     *
     * @param type the specified Configuration type.  See the Configuration
     *    section in the <a href=
     *    "{@docRoot}/../technotes/guides/security/StandardNames.html#Configuration">
     *    Java Cryptography Architecture Standard Algorithm Name
     *    Documentation</a> for a list of standard Configuration types.
     *
     * @param params parameters for the Configuration, which may be null.
     *
     * @return the new Configuration object.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get a Configuration instance for the specified type.
     *
     * @exception NullPointerException if the specified type is null.
     *
     * @exception IllegalArgumentException if the specified parameters
     *          are not understood by the ConfigurationSpi implementation
     *          from the selected Provider.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a
     *          ConfigurationSpi implementation for the specified type.
     *
     * @see Provider
     * @since 1.6
     */
    public static Configuration getInstance(String type,
                                Configuration.Parameters params)
                throws NoSuchAlgorithmException {

        checkPermission(type);
        try {
            GetInstance.Instance instance = GetInstance.getInstance
                                                        ("Configuration",
                                                        ConfigurationSpi.class,
                                                        type,
                                                        params);
            return new ConfigDelegate((ConfigurationSpi)instance.impl,
                                                        instance.provider,
                                                        type,
                                                        params);
        } catch (NoSuchAlgorithmException nsae) {
            return handleException (nsae);
        }
    }

    /**
     * Returns a Configuration object of the specified type.
     *
     * <p> A new Configuration object encapsulating the
     * ConfigurationSpi implementation from the specified provider
     * is returned.   The specified provider must be registered
     * in the provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param type the specified Configuration type.  See the Configuration
     *    section in the <a href=
     *    "{@docRoot}/../technotes/guides/security/StandardNames.html#Configuration">
     *    Java Cryptography Architecture Standard Algorithm Name
     *    Documentation</a> for a list of standard Configuration types.
     *
     * @param params parameters for the Configuration, which may be null.
     *
     * @param provider the provider.
     *
     * @return the new Configuration object.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get a Configuration instance for the specified type.
     *
     * @exception NullPointerException if the specified type is null.
     *
     * @exception IllegalArgumentException if the specified provider
     *          is null or empty,
     *          or if the specified parameters are not understood by
     *          the ConfigurationSpi implementation from the specified provider.
     *
     * @exception NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception NoSuchAlgorithmException if the specified provider does not
     *          support a ConfigurationSpi implementation for the specified
     *          type.
     *
     * @see Provider
     * @since 1.6
     */
    public static Configuration getInstance(String type,
                                Configuration.Parameters params,
                                String provider)
                throws NoSuchProviderException, NoSuchAlgorithmException {

        if (provider == null || provider.length() == 0) {
            throw new IllegalArgumentException("missing provider");
        }

        checkPermission(type);
        try {
            GetInstance.Instance instance = GetInstance.getInstance
                                                        ("Configuration",
                                                        ConfigurationSpi.class,
                                                        type,
                                                        params,
                                                        provider);
            return new ConfigDelegate((ConfigurationSpi)instance.impl,
                                                        instance.provider,
                                                        type,
                                                        params);
        } catch (NoSuchAlgorithmException nsae) {
            return handleException (nsae);
        }
    }

    /**
     * Returns a Configuration object of the specified type.
     *
     * <p> A new Configuration object encapsulating the
     * ConfigurationSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param type the specified Configuration type.  See the Configuration
     *    section in the <a href=
     *    "{@docRoot}/../technotes/guides/security/StandardNames.html#Configuration">
     *    Java Cryptography Architecture Standard Algorithm Name
     *    Documentation</a> for a list of standard Configuration types.
     *
     * @param params parameters for the Configuration, which may be null.
     *
     * @param provider the Provider.
     *
     * @return the new Configuration object.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get a Configuration instance for the specified type.
     *
     * @exception NullPointerException if the specified type is null.
     *
     * @exception IllegalArgumentException if the specified Provider is null,
     *          or if the specified parameters are not understood by
     *          the ConfigurationSpi implementation from the specified Provider.
     *
     * @exception NoSuchAlgorithmException if the specified Provider does not
     *          support a ConfigurationSpi implementation for the specified
     *          type.
     *
     * @see Provider
     * @since 1.6
     */
    public static Configuration getInstance(String type,
                                Configuration.Parameters params,
                                Provider provider)
                throws NoSuchAlgorithmException {

        if (provider == null) {
            throw new IllegalArgumentException("missing provider");
        }

        checkPermission(type);
        try {
            GetInstance.Instance instance = GetInstance.getInstance
                                                        ("Configuration",
                                                        ConfigurationSpi.class,
                                                        type,
                                                        params,
                                                        provider);
            return new ConfigDelegate((ConfigurationSpi)instance.impl,
                                                        instance.provider,
                                                        type,
                                                        params);
        } catch (NoSuchAlgorithmException nsae) {
            return handleException (nsae);
        }
    }

    private static Configuration handleException(NoSuchAlgorithmException nsae)
                throws NoSuchAlgorithmException {
        Throwable cause = nsae.getCause();
        if (cause instanceof IllegalArgumentException) {
            throw (IllegalArgumentException)cause;
        }
        throw nsae;
    }

    /**
     * Return the Provider of this Configuration.
     *
     * <p> This Configuration instance will only have a Provider if it
     * was obtained via a call to {@code Configuration.getInstance}.
     * Otherwise this method returns null.
     *
     * @return the Provider of this Configuration, or null.
     *
     * @since 1.6
     */
    public Provider getProvider() {
        return null;
    }

    /**
     * Return the type of this Configuration.
     *
     * <p> This Configuration instance will only have a type if it
     * was obtained via a call to {@code Configuration.getInstance}.
     * Otherwise this method returns null.
     *
     * @return the type of this Configuration, or null.
     *
     * @since 1.6
     */
    public String getType() {
        return null;
    }

    /**
     * Return Configuration parameters.
     *
     * <p> This Configuration instance will only have parameters if it
     * was obtained via a call to {@code Configuration.getInstance}.
     * Otherwise this method returns null.
     *
     * @return Configuration parameters, or null.
     *
     * @since 1.6
     */
    public Configuration.Parameters getParameters() {
        return null;
    }

    /**
     * Retrieve the AppConfigurationEntries for the specified <i>name</i>
     * from this Configuration.
     *
     * <p>
     *
     * @param name the name used to index the Configuration.
     *
     * @return an array of AppConfigurationEntries for the specified <i>name</i>
     *          from this Configuration, or null if there are no entries
     *          for the specified <i>name</i>
     */
    public abstract AppConfigurationEntry[] getAppConfigurationEntry
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
     *                          to refresh its Configuration.
     */
    public void refresh() { }

    /**
     * This subclass is returned by the getInstance calls.  All Configuration
     * calls are delegated to the underlying ConfigurationSpi.
     */
    private static class ConfigDelegate extends Configuration {

        private ConfigurationSpi spi;
        private Provider p;
        private String type;
        private Configuration.Parameters params;

        private ConfigDelegate(ConfigurationSpi spi, Provider p,
                        String type, Configuration.Parameters params) {
            this.spi = spi;
            this.p = p;
            this.type = type;
            this.params = params;
        }

        public String getType() { return type; }

        public Configuration.Parameters getParameters() { return params; }

        public Provider getProvider() { return p; }

        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            return spi.engineGetAppConfigurationEntry(name);
        }

        public void refresh() {
            spi.engineRefresh();
        }
    }

    /**
     * This represents a marker interface for Configuration parameters.
     *
     * @since 1.6
     */
    public static interface Parameters { }
}
