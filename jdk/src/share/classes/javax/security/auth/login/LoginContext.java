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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.text.MessageFormat;
import javax.security.auth.Subject;
import javax.security.auth.AuthPermission;
import javax.security.auth.callback.*;
import java.security.AccessController;
import java.security.AccessControlContext;
import sun.security.util.PendingException;
import sun.security.util.ResourcesMgr;

/**
 * <p> The {@code LoginContext} class describes the basic methods used
 * to authenticate Subjects and provides a way to develop an
 * application independent of the underlying authentication technology.
 * A {@code Configuration} specifies the authentication technology, or
 * {@code LoginModule}, to be used with a particular application.
 * Different LoginModules can be plugged in under an application
 * without requiring any modifications to the application itself.
 *
 * {@code LoginContext}类描述了用于验证subject的基本方法，并提供了一种开发独立于
 * 底层验证技术的应用程序的方法。{@code Configuration}指定用于特定应用程序的身份验证技术，
 * 或{@code LoginModule}。不同的loginmodule可以被插入到应用程序中，而不需要对应用程序本身
 * 进行任何修改。
 *
 * <p> In addition to supporting <i>pluggable</i> authentication, this class
 * also supports the notion of <i>stacked</i> authentication.
 * Applications may be configured to use more than one
 * LoginModule.  For example, one could
 * configure both a Kerberos LoginModule and a smart card
 * LoginModule under an application.
 *
 * 除了支持pluggable身份验证之外，这个类还支持stacked身份验证的概念。应用程序可以配置为
 * 使用多个LoginModule。例如，可以在应用程序下配置Kerberos LoginModule和smart card LoginModule。
 *
 * <p> A typical caller instantiates a LoginContext with
 * a <i>name</i> and a {@code CallbackHandler}.
 * LoginContext uses the <i>name</i> as the index into a
 * Configuration to determine which LoginModules should be used,
 * and which ones must succeed in order for the overall authentication to
 * succeed.  The {@code CallbackHandler} is passed to the underlying
 * LoginModules so they may communicate and interact with users
 * (prompting for a username and password via a graphical user interface,
 * for example).
 *
 * 典型的调用者用名称和{@code CallbackHandler}实例化一个LoginContext。LoginContext使用
 * 名称作为配置的索引，以确定应该使用哪些loginmodule，以及为了使整个身份验证成功，哪些loginmodule
 * 必须成功。{@code CallbackHandler}被传递给底层的loginmodule，这样它们就可以与用户进行
 * 通信和交互(例如，通过图形用户界面提示用户名和密码)。
 *
 * <p> Once the caller has instantiated a LoginContext,
 * it invokes the {@code login} method to authenticate
 * a {@code Subject}.  The {@code login} method invokes
 * the configured modules to perform their respective types of authentication
 * (username/password, smart card pin verification, etc.).
 * Note that the LoginModules will not attempt authentication retries nor
 * introduce delays if the authentication fails.
 * Such tasks belong to the LoginContext caller.
 *
 * 一旦调用者实例化了一个LoginContext，它就调用{@code login}方法来验证一个{@code Subject}。
 * {@code login}方法调用配置的模块来执行它们各自类型的身份验证(用户名/密码、智能卡pin验证等)。
 * 请注意，如果身份验证失败，LoginModules将不会尝试重试身份验证，也不会引入延迟。这些任务属于
 * LoginContext调用者。
 *
 * <p> If the {@code login} method returns without
 * throwing an exception, then the overall authentication succeeded.
 * The caller can then retrieve
 * the newly authenticated Subject by invoking the
 * {@code getSubject} method.  Principals and Credentials associated
 * with the Subject may be retrieved by invoking the Subject's
 * respective {@code getPrincipals}, {@code getPublicCredentials},
 * and {@code getPrivateCredentials} methods.
 *
 *
 * 如果{@code login}方法返回时没有抛出异常，则整个身份验证成功。调用者可以通过调用
 * {@code getSubject}方法来检索新认证的Subject。与Subject相关的主体和凭据可以
 * 通过调用Subject各自的{@code getPrincipals}、{@code getPublicCredentials}
 * 和{@code getPrivateCredentials}方法来检索。
 *
 * <p> To logout the Subject, the caller calls
 * the {@code logout} method.  As with the {@code login}
 * method, this {@code logout} method invokes the {@code logout}
 * method for the configured modules.
 *
 * 要退出Subject，调用者调用{@code logout}方法。和{@code login}方法一样，
 * 这个{@code logout}方法会为配置好的模块调用{@code logout}方法。
 *
 * <p> A LoginContext should not be used to authenticate
 * more than one Subject.  A separate LoginContext
 * should be used to authenticate each different Subject.
 *
 * LoginContext不应该用于验证多个Subject。应该使用单独的LoginContext对每个
 * 不同的Subject进行身份验证。
 *
 * <p> The following documentation applies to all LoginContext constructors:
 * <ol>
 *     以下文档适用于所有的LoginContext构造函数:
 *
 * <li> {@code Subject}
 * <ul>
 * <li> If the constructor has a Subject
 * input parameter, the LoginContext uses the caller-specified
 * Subject object.
 *
 * <li> If the caller specifies a {@code null} Subject
 * and a {@code null} value is permitted,
 * the LoginContext instantiates a new Subject.
 *
 * <li> If the constructor does <b>not</b> have a Subject
 * input parameter, the LoginContext instantiates a new Subject.
 * <p>
 * </ul>
 *
 * <li> {@code Configuration}
 * <ul>
 * <li> If the constructor has a Configuration
 * input parameter and the caller specifies a non-null Configuration,
 * the LoginContext uses the caller-specified Configuration.
 * <p>
 * If the constructor does <b>not</b> have a Configuration
 * input parameter, or if the caller specifies a {@code null}
 * Configuration object, the constructor uses the following call to
 * get the installed Configuration:
 * <pre>
 *      config = Configuration.getConfiguration();
 * </pre>
 * For both cases,
 * the <i>name</i> argument given to the constructor is passed to the
 * {@code Configuration.getAppConfigurationEntry} method.
 * If the Configuration has no entries for the specified <i>name</i>,
 * then the {@code LoginContext} calls
 * {@code getAppConfigurationEntry} with the name, "<i>other</i>"
 * (the default entry name).  If there is no entry for "<i>other</i>",
 * then a {@code LoginException} is thrown.
 *
 * <li> When LoginContext uses the installed Configuration, the caller
 * requires the createLoginContext.<em>name</em> and possibly
 * createLoginContext.other AuthPermissions. Furthermore, the
 * LoginContext will invoke configured modules from within an
 * {@code AccessController.doPrivileged} call so that modules that
 * perform security-sensitive tasks (such as connecting to remote hosts,
 * and updating the Subject) will require the respective permissions, but
 * the callers of the LoginContext will not require those permissions.
 *
 * <li> When LoginContext uses a caller-specified Configuration, the caller
 * does not require any createLoginContext AuthPermission.  The LoginContext
 * saves the {@code AccessControlContext} for the caller,
 * and invokes the configured modules from within an
 * {@code AccessController.doPrivileged} call constrained by that context.
 * This means the caller context (stored when the LoginContext was created)
 * must have sufficient permissions to perform any security-sensitive tasks
 * that the modules may perform.
 * <p>
 * </ul>
 *
 * <li> {@code CallbackHandler}
 * <ul>
 * <li> If the constructor has a CallbackHandler
 * input parameter, the LoginContext uses the caller-specified
 * CallbackHandler object.
 *
 * <li> If the constructor does <b>not</b> have a CallbackHandler
 * input parameter, or if the caller specifies a {@code null}
 * CallbackHandler object (and a {@code null} value is permitted),
 * the LoginContext queries the
 * {@code auth.login.defaultCallbackHandler} security property for the
 * fully qualified class name of a default handler
 * implementation. If the security property is not set,
 * then the underlying modules will not have a
 * CallbackHandler for use in communicating
 * with users.  The caller thus assumes that the configured
 * modules have alternative means for authenticating the user.
 *
 *
 * <li> When the LoginContext uses the installed Configuration (instead of
 * a caller-specified Configuration, see above),
 * then this LoginContext must wrap any
 * caller-specified or default CallbackHandler implementation
 * in a new CallbackHandler implementation
 * whose {@code handle} method implementation invokes the
 * specified CallbackHandler's {@code handle} method in a
 * {@code java.security.AccessController.doPrivileged} call
 * constrained by the caller's current {@code AccessControlContext}.
 * </ul>
 * </ol>
 *
 * 1. {@code Subject}
 *     1. 如果构造函数有一个Subject输入参数，LoginContext使用调用者指定的Subject对象。
 *     2. 如果调用者指定了一个{@code null} Subject并且允许一个{@code null}值，LoginContext
 *        实例化一个新的Subject。
 *     3. 如果构造函数没有Subject输入参数，LoginContext将实例化一个新的Subject。
 *
 * 2. {@code Configuration}
 *    1. 如果构造函数有一个Configuration输入参数，而调用者指定了一个非空的Configuration,
 *       LoginContext使用调用者指定的Configuration。
 *
 *       如果构造函数没有Configuration输入参数，或者调用者指定了一个{@code null} Configuration对象，
 *       构造函数就会使用下面的调用来获取安装的Configuration:
 *
 *       config = Configuration.getConfiguration();
 *
 *       对于这两种情况，给构造函数的name参数都会被传递给{@code Configuration.getAppConfigurationEntry}
 *       方法。如果Configuration没有指定名称的条目，那么{@code LoginContext}调用{@code getAppConfigurationEntry}
 *       带有名称“other”(默认的条目名)。如果没有“other”条目，则抛出{@code LoginException}。
 *
 *    2. 当LoginContext使用安装的Configuration时，调用者需要createLoginContext.name，可能还需要
 *      createLoginContext。其他AuthPermissions。此外，LoginContext将从{@code AccessController中
 *      调用已配置的模块。doPrivileged}调用，这样执行安全敏感任务(例如连接到远程主机和更新Subject)的模块
 *      将需要相应的权限，但LoginContext的调用者将不需要这些权限。
 *
 *    3. 当LoginContext使用调用者指定的配置时，调用者不需要任何createLoginContext AuthPermission。
 *       LoginContext为调用者保存{@code AccessControlContext}，并从{@code AccessController中
 *       调用配置的模块。被上下文限制的特权}调用。这意味着调用者上下文(在创建LoginContext时存储)必须具
 *       有足够的权限来执行模块可能执行的任何安全敏感任务。
 *
 * 3. {@code CallbackHandler}
 *    1. 如果构造函数有一个CallbackHandler输入参数，LoginContext使用调用者指定的CallbackHandler对象。
 *    2. 如果构造函数没有CallbackHandler输入参数,或者调用者指定零}{@code CallbackHandler对象(零}
 *       {@code值是允许的),LoginContext查询{@code auth.login.defaultCallbackHandler}的安全属性
 *       默认处理程序实现的完全限定类名。如果未设置security属性，则底层模块将没有用于与用户通信的
 *       CallbackHandler。因此，调用者假定配置的模块有其他方法来验证用户。
 *    3. 当LoginContext使用安装的Configuration(而不是调用者指定的Configuration，参见上面的内容)时，
 *       那么LoginContext必须将任何调用者指定的或默认的CallbackHandler实现包装到一个新的CallbackHandler
 *       实现中，该实现的{@code handle}方法实现将调用{@code java.security.AccessController中指定的
 *       CallbackHandler的{@code handle}方法。被调用者当前{@code AccessControlContext}约束的调用。
 *
 * @see java.security.Security
 * @see javax.security.auth.AuthPermission
 * @see javax.security.auth.Subject
 * @see javax.security.auth.callback.CallbackHandler
 * @see javax.security.auth.login.Configuration
 * @see javax.security.auth.spi.LoginModule
 * @see java.security.Security security properties
 *
 * todo:  【java】java的Jaas授权与鉴权
 *         https://blog.csdn.net/qq_21383435/article/details/85340239
 *
 * 1. LoginContext是javax.security.auth.login包里的一个类，它描述了用于验证对象(subjects)的方法。
 * 2. Subject就是在某个你想去认证和分配访问权限的系统里的一个标识。一个主体(subject)可能是一个
 *    用户、一个进程或者是一台机器，它用javax.security.auth.Subject类表示。由于一个Subject
 *    可能涉及多个授权（一个网上银行密码和另一个电子邮件系统），
 * 3. java.security.Principal就被用作在那些关联里的标识。也就是说，该Principal接口是一个能
 *    够被用作代表某个实体、公司或者登陆ID的抽象概念。一个Subject可能包含多个Principles.
 * 4. Principal ：当一个Subject认证成功，Principal将会关联到这个Subject。Principal表示
 *    Subject的身份表示，必须要实现 java.security.Principal 和 java.io.Serializable 接口。
 *    Subject section 描述了更新Principal关联到subject的方法。
 * 5. Credential ：并不是主要的Jaas代码，任何类可以表示为Credential，并需要实现Credential的
 *    两个接口Refreshable和Destroyable。
 *
 *
 */
public class LoginContext {

    private static final String INIT_METHOD             = "initialize";
    private static final String LOGIN_METHOD            = "login";
    private static final String COMMIT_METHOD           = "commit";
    private static final String ABORT_METHOD            = "abort";
    private static final String LOGOUT_METHOD           = "logout";
    private static final String OTHER                   = "other";
    private static final String DEFAULT_HANDLER         =
                                "auth.login.defaultCallbackHandler";
    private Subject subject = null;
    private boolean subjectProvided = false;
    private boolean loginSucceeded = false;
    private CallbackHandler callbackHandler;
    private Map<String,?> state = new HashMap<String,Object>();

    private Configuration config;
    private AccessControlContext creatorAcc = null;  // customized config only
    private ModuleInfo[] moduleStack;
    private ClassLoader contextClassLoader = null;
    private static final Class<?>[] PARAMS = { };

    // state saved in the event a user-specified asynchronous exception
    // was specified and thrown

    private int moduleIndex = 0;
    private LoginException firstError = null;
    private LoginException firstRequiredError = null;
    private boolean success = false;

    private static final sun.security.util.Debug debug =
        sun.security.util.Debug.getInstance("logincontext", "\t[LoginContext]");

    private void init(String name) throws LoginException {

        // 获取安全管理器
        SecurityManager sm = System.getSecurityManager();
        if (sm != null && creatorAcc == null) {
            // 检测权限
            sm.checkPermission(new AuthPermission
                                ("createLoginContext." + name));
        }

        if (name == null)
            throw new LoginException
                (ResourcesMgr.getString("Invalid.null.input.name"));

        // get the Configuration
        if (config == null) {
            config = java.security.AccessController.doPrivileged
                (new java.security.PrivilegedAction<Configuration>() {
                public Configuration run() {
                    /**
                     * 此处获取系统配置 你运行时候 填写的-D 参数 都能获取到
                     * -Djavax.security.auth.login.config=xx
                     * -Djava.security.krb5.conf=xx
                     */
                    return Configuration.getConfiguration();
                }
            });
        }

        // get the LoginModules configured for this application
        // 获取为这个应用程序配置的LoginModules 比如 com.sun.security.auth.module.Krb5LoginModule
        /**
         * Client {
         *   com.sun.security.auth.module.Krb5LoginModule required
         *   useKeyTab=true
         *   keyTab="/home/mr/mr.keytab"
         *   storeKey=true
         *   useTicketCache=false
         *   principal="mr/zdh2@ZDH.COM";
         * };
         * 比如这里传入的name 是 Client 那么 返回的就是括号的一串
         */
        AppConfigurationEntry[] entries = config.getAppConfigurationEntry(name);
        if (entries == null) {

            if (sm != null && creatorAcc == null) {
                sm.checkPermission(new AuthPermission
                                ("createLoginContext." + OTHER));
            }

            entries = config.getAppConfigurationEntry(OTHER);
            if (entries == null) {
                MessageFormat form = new MessageFormat(ResourcesMgr.getString
                        ("No.LoginModules.configured.for.name"));
                Object[] source = {name};
                throw new LoginException(form.format(source));
            }
        }
        // 生成 moduleStack
        moduleStack = new ModuleInfo[entries.length];
        for (int i = 0; i < entries.length; i++) {
            // clone returned array
            moduleStack[i] = new ModuleInfo
                                (new AppConfigurationEntry
                                        // 这里获取的就是 com.sun.security.auth.module.Krb5LoginModule
                                        (entries[i].getLoginModuleName(),
                                        entries[i].getControlFlag(),
                                        entries[i].getOptions()),
                                null);
        }

        contextClassLoader = java.security.AccessController.doPrivileged
                (new java.security.PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    ClassLoader loader =
                            Thread.currentThread().getContextClassLoader();
                    if (loader == null) {
                        // Don't use bootstrap class loader directly to ensure
                        // proper package access control!
                        loader = ClassLoader.getSystemClassLoader();
                    }

                    return loader;
                }
        });
    }

    private void loadDefaultCallbackHandler() throws LoginException {

        // get the default handler class
        try {

            final ClassLoader finalLoader = contextClassLoader;

            this.callbackHandler = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedExceptionAction<CallbackHandler>() {
                public CallbackHandler run() throws Exception {
                    String defaultHandler = java.security.Security.getProperty
                        (DEFAULT_HANDLER);
                    if (defaultHandler == null || defaultHandler.length() == 0)
                        return null;
                    Class<? extends CallbackHandler> c = Class.forName(
                            defaultHandler, true,
                            finalLoader).asSubclass(CallbackHandler.class);
                    return c.newInstance();
                }
            });
        } catch (java.security.PrivilegedActionException pae) {
            throw new LoginException(pae.getException().toString());
        }

        // secure it with the caller's ACC
        if (this.callbackHandler != null && creatorAcc == null) {
            this.callbackHandler = new SecureCallbackHandler
                                (java.security.AccessController.getContext(),
                                this.callbackHandler);
        }
    }

    /**
     * Instantiate a new {@code LoginContext} object with a name.
     *
     * @param name the name used as the index into the
     *          {@code Configuration}.
     *
     * @exception LoginException if the caller-specified {@code name}
     *          does not appear in the {@code Configuration}
     *          and there is no {@code Configuration} entry
     *          for "<i>other</i>", or if the
     *          <i>auth.login.defaultCallbackHandler</i>
     *          security property was set, but the implementation
     *          class could not be loaded.
     *          <p>
     * @exception SecurityException if a SecurityManager is set and
     *          the caller does not have
     *          AuthPermission("createLoginContext.<i>name</i>"),
     *          or if a configuration entry for <i>name</i> does not exist and
     *          the caller does not additionally have
     *          AuthPermission("createLoginContext.other")
     */
    public LoginContext(String name) throws LoginException {
        init(name);
        loadDefaultCallbackHandler();
    }

    /**
     * Instantiate a new {@code LoginContext} object with a name
     * and a {@code Subject} object.
     *
     * <p>
     *
     * @param name the name used as the index into the
     *          {@code Configuration}. <p>
     *
     * @param subject the {@code Subject} to authenticate.
     *
     * @exception LoginException if the caller-specified {@code name}
     *          does not appear in the {@code Configuration}
     *          and there is no {@code Configuration} entry
     *          for "<i>other</i>", if the caller-specified {@code subject}
     *          is {@code null}, or if the
     *          <i>auth.login.defaultCallbackHandler</i>
     *          security property was set, but the implementation
     *          class could not be loaded.
     *          <p>
     * @exception SecurityException if a SecurityManager is set and
     *          the caller does not have
     *          AuthPermission("createLoginContext.<i>name</i>"),
     *          or if a configuration entry for <i>name</i> does not exist and
     *          the caller does not additionally have
     *          AuthPermission("createLoginContext.other")
     */
    public LoginContext(String name, Subject subject)
    throws LoginException {
        init(name);
        if (subject == null)
            throw new LoginException
                (ResourcesMgr.getString("invalid.null.Subject.provided"));
        this.subject = subject;
        subjectProvided = true;
        loadDefaultCallbackHandler();
    }

    /**
     * Instantiate a new {@code LoginContext} object with a name
     * and a {@code CallbackHandler} object.
     *
     * <p>
     *
     * @param name the name used as the index into the
     *          {@code Configuration}. <p>
     *
     * @param callbackHandler the {@code CallbackHandler} object used by
     *          LoginModules to communicate with the user.
     *
     * @exception LoginException if the caller-specified {@code name}
     *          does not appear in the {@code Configuration}
     *          and there is no {@code Configuration} entry
     *          for "<i>other</i>", or if the caller-specified
     *          {@code callbackHandler} is {@code null}.
     *          <p>
     * @exception SecurityException if a SecurityManager is set and
     *          the caller does not have
     *          AuthPermission("createLoginContext.<i>name</i>"),
     *          or if a configuration entry for <i>name</i> does not exist and
     *          the caller does not additionally have
     *          AuthPermission("createLoginContext.other")
     */
    public LoginContext(String name, CallbackHandler callbackHandler)
    throws LoginException {
        init(name);
        if (callbackHandler == null)
            throw new LoginException(ResourcesMgr.getString
                                ("invalid.null.CallbackHandler.provided"));
        this.callbackHandler = new SecureCallbackHandler
                                (java.security.AccessController.getContext(),
                                callbackHandler);
    }

    /**
     * Instantiate a new {@code LoginContext} object with a name,
     * a {@code Subject} to be authenticated, and a
     * {@code CallbackHandler} object.
     *
     * <p>
     *
     * @param name the name used as the index into the
     *          {@code Configuration}. <p>
     *
     * @param subject the {@code Subject} to authenticate. <p>
     *
     * @param callbackHandler the {@code CallbackHandler} object used by
     *          LoginModules to communicate with the user.
     *
     * @exception LoginException if the caller-specified {@code name}
     *          does not appear in the {@code Configuration}
     *          and there is no {@code Configuration} entry
     *          for "<i>other</i>", or if the caller-specified
     *          {@code subject} is {@code null},
     *          or if the caller-specified
     *          {@code callbackHandler} is {@code null}.
     *          <p>
     * @exception SecurityException if a SecurityManager is set and
     *          the caller does not have
     *          AuthPermission("createLoginContext.<i>name</i>"),
     *          or if a configuration entry for <i>name</i> does not exist and
     *          the caller does not additionally have
     *          AuthPermission("createLoginContext.other")
     */
    public LoginContext(String name, Subject subject,
                        CallbackHandler callbackHandler) throws LoginException {
        this(name, subject);
        if (callbackHandler == null)
            throw new LoginException(ResourcesMgr.getString
                                ("invalid.null.CallbackHandler.provided"));
        this.callbackHandler = new SecureCallbackHandler
                                (java.security.AccessController.getContext(),
                                callbackHandler);
    }

    /**
     * Instantiate a new {@code LoginContext} object with a name,
     * a {@code Subject} to be authenticated,
     * a {@code CallbackHandler} object, and a login
     * {@code Configuration}.
     *
     * <p>
     *
     * @param name the name used as the index into the caller-specified
     *          {@code Configuration}. <p>
     *
     * @param subject the {@code Subject} to authenticate,
     *          or {@code null}. <p>
     *
     * @param callbackHandler the {@code CallbackHandler} object used by
     *          LoginModules to communicate with the user, or {@code null}.
     *          <p>
     *
     * @param config the {@code Configuration} that lists the
     *          login modules to be called to perform the authentication,
     *          or {@code null}.
     *
     * @exception LoginException if the caller-specified {@code name}
     *          does not appear in the {@code Configuration}
     *          and there is no {@code Configuration} entry
     *          for "<i>other</i>".
     *          <p>
     * @exception SecurityException if a SecurityManager is set,
     *          <i>config</i> is {@code null},
     *          and either the caller does not have
     *          AuthPermission("createLoginContext.<i>name</i>"),
     *          or if a configuration entry for <i>name</i> does not exist and
     *          the caller does not additionally have
     *          AuthPermission("createLoginContext.other")
     *
     * @since 1.5
     */
    public LoginContext(String name, Subject subject,
                        CallbackHandler callbackHandler,
                        Configuration config) throws LoginException {
        this.config = config;
        if (config != null) {
            creatorAcc = java.security.AccessController.getContext();
        }

        init(name);
        if (subject != null) {
            this.subject = subject;
            subjectProvided = true;
        }
        if (callbackHandler == null) {
            loadDefaultCallbackHandler();
        } else if (creatorAcc == null) {
            this.callbackHandler = new SecureCallbackHandler
                                (java.security.AccessController.getContext(),
                                callbackHandler);
        } else {
            this.callbackHandler = callbackHandler;
        }
    }

    /**
     * Perform the authentication.
     *
     * <p> This method invokes the {@code login} method for each
     * LoginModule configured for the <i>name</i> specified to the
     * {@code LoginContext} constructor, as determined by the login
     * {@code Configuration}.  Each {@code LoginModule}
     * then performs its respective type of authentication
     * (username/password, smart card pin verification, etc.).
     *
     * <p> This method completes a 2-phase authentication process by
     * calling each configured LoginModule's {@code commit} method
     * if the overall authentication succeeded (the relevant REQUIRED,
     * REQUISITE, SUFFICIENT, and OPTIONAL LoginModules succeeded),
     * or by calling each configured LoginModule's {@code abort} method
     * if the overall authentication failed.  If authentication succeeded,
     * each successful LoginModule's {@code commit} method associates
     * the relevant Principals and Credentials with the {@code Subject}.
     * If authentication failed, each LoginModule's {@code abort} method
     * removes/destroys any previously stored state.
     *
     * <p> If the {@code commit} phase of the authentication process
     * fails, then the overall authentication fails and this method
     * invokes the {@code abort} method for each configured
     * {@code LoginModule}.
     *
     * <p> If the {@code abort} phase
     * fails for any reason, then this method propagates the
     * original exception thrown either during the {@code login} phase
     * or the {@code commit} phase.  In either case, the overall
     * authentication fails.
     *
     * <p> In the case where multiple LoginModules fail,
     * this method propagates the exception raised by the first
     * {@code LoginModule} which failed.
     *
     * <p> Note that if this method enters the {@code abort} phase
     * (either the {@code login} or {@code commit} phase failed),
     * this method invokes all LoginModules configured for the
     * application regardless of their respective {@code Configuration}
     * flag parameters.  Essentially this means that {@code Requisite}
     * and {@code Sufficient} semantics are ignored during the
     * {@code abort} phase.  This guarantees that proper cleanup
     * and state restoration can take place.
     *
     * <p>
     *
     * @exception LoginException if the authentication fails.
     *
     *
     * 执行身份验证。
     *
     * 这个方法调用每个LoginModule的{@code login}方法，每个LoginModule的名称都被指定
     * 给{@code LoginContext}构造函数，这是由登录{@code Configuration}决定的。然后
     * 每个{@code LoginModule}执行其各自类型的身份验证(用户名/密码，智能卡pin验证，等等)。
     *
     *
     *
     * 如果整个身份验证成功(相关的REQUIRED、REQUISITE、SUFFICIENT和OPTIONAL LoginModule成功)，
     * 则调用每个配置的LoginModule的{@code commit}方法;如果整个身份验证失败，则调用每个配置的
     * LoginModule的{@code abort}方法，完成一个两阶段的身份验证过程。如果认证成功，每个成功的
     * LoginModule的{@code commit}方法将相关主体和凭据与{@code Subject}关联起来。如果身份验证
     * 失败，每个LoginModule的{@code abort}方法会删除/销毁之前存储的任何状态。
     *
     *
     *
     * 如果身份验证过程的{@code commit}阶段失败，那么整个身份验证都会失败，这个方法会为每个
     * 配置的{@code LoginModule}调用{@code abort}方法。
     *
     *
     *
     * 如果由于任何原因{@code abort}阶段失败，那么该方法将传播在{@code login}阶段或{@code commit}
     * 阶段抛出的原始异常。在这两种情况下，整个身份验证都失败。
     *
     *
     *
     * 在多个LoginModule失败的情况下，该方法传播由第一个失败的{@code LoginModule}引发的异常。
     *
     *
     *
     * 注意，如果这个方法进入{@code abort}阶段({@code login}或{@code commit}阶段失败)，
     * 这个方法会调用所有为应用程序配置的loginmodule，而不管它们各自的{@code Configuration}
     * 标志参数。本质上，这意味着{@code Requisite}和{@code Sufficient}语义在{@code abort}
     * 阶段被忽略。这可以保证进行适当的清理和状态恢复。
     */
    public void login() throws LoginException {

        loginSucceeded = false;

        // 可以看到你传入的为空，那么就新建一个，如果传入的不为空，那么就新建一个
        if (subject == null) {
            subject = new Subject();
        }

        try {
            // module invoked in doPrivileged
            // 调用 LoginModule 的 login 方法  此处转到 Krb5LoginModule 的 login 方法
            invokePriv(LOGIN_METHOD);
            // 调用 LoginModule 的 commit 方法   此处转到 Krb5LoginModule 的 commit 方法
            invokePriv(COMMIT_METHOD);
            loginSucceeded = true;
        } catch (LoginException le) {
            try {
                // 出现任何一异常调用  abort 方法  此处转到 Krb5LoginModule 的 abort 方法
                invokePriv(ABORT_METHOD);
            } catch (LoginException le2) {
                throw le;
            }
            throw le;
        }
    }

    /**
     * Logout the {@code Subject}.
     *
     * <p> This method invokes the {@code logout} method for each
     * {@code LoginModule} configured for this {@code LoginContext}.
     * Each {@code LoginModule} performs its respective logout procedure
     * which may include removing/destroying
     * {@code Principal} and {@code Credential} information
     * from the {@code Subject} and state cleanup.
     *
     * <p> Note that this method invokes all LoginModules configured for the
     * application regardless of their respective
     * {@code Configuration} flag parameters.  Essentially this means
     * that {@code Requisite} and {@code Sufficient} semantics are
     * ignored for this method.  This guarantees that proper cleanup
     * and state restoration can take place.
     *
     * <p>
     *
     * @exception LoginException if the logout fails.
     */
    public void logout() throws LoginException {
        if (subject == null) {
            throw new LoginException(ResourcesMgr.getString
                ("null.subject.logout.called.before.login"));
        }

        // module invoked in doPrivileged
        invokePriv(LOGOUT_METHOD);
    }

    /**
     * Return the authenticated Subject.
     *
     * <p>
     *
     * @return the authenticated Subject.  If the caller specified a
     *          Subject to this LoginContext's constructor,
     *          this method returns the caller-specified Subject.
     *          If a Subject was not specified and authentication succeeds,
     *          this method returns the Subject instantiated and used for
     *          authentication by this LoginContext.
     *          If a Subject was not specified, and authentication fails or
     *          has not been attempted, this method returns null.
     */
    public Subject getSubject() {
        if (!loginSucceeded && !subjectProvided)
            return null;
        return subject;
    }

    private void clearState() {
        moduleIndex = 0;
        firstError = null;
        firstRequiredError = null;
        success = false;
    }

    private void throwException(LoginException originalError, LoginException le)
    throws LoginException {

        // first clear state
        clearState();

        // throw the exception
        LoginException error = (originalError != null) ? originalError : le;
        throw error;
    }

    /**
     * Invokes the login, commit, and logout methods
     * from a LoginModule inside a doPrivileged block restricted
     * by creatorAcc (may be null).
     *
     * This version is called if the caller did not instantiate
     * the LoginContext with a Configuration object.
     */
    private void invokePriv(final String methodName) throws LoginException {
        try {
            java.security.AccessController.doPrivileged
                (new java.security.PrivilegedExceptionAction<Void>() {
                public Void run() throws LoginException {
                    invoke(methodName);
                    return null;
                }
            }, creatorAcc);
        } catch (java.security.PrivilegedActionException pae) {
            throw (LoginException)pae.getException();
        }
    }

    /**
     * 获取 LoginModule 信息后 开始调用
     *
     * @param methodName
     * @throws LoginException
     */
    private void invoke(String methodName) throws LoginException {

        // start at moduleIndex
        // - this can only be non-zero if methodName is LOGIN_METHOD
        //
        // 从moduleIndex开始——如果methodName是LOGIN_METHOD，这个值只能是非零

        for (int i = moduleIndex; i < moduleStack.length; i++, moduleIndex++) {
            try {

                int mIndex = 0;
                Method[] methods = null;

                if (moduleStack[i].module != null) {
                    methods = moduleStack[i].module.getClass().getMethods();
                } else {

                    // instantiate the LoginModule
                    //
                    // Allow any object to be a LoginModule as long as it
                    // conforms to the interface.
                    // 反射加载相应的类  getLoginModuleName 就是类全名
                    Class<?> c = Class.forName(
                                moduleStack[i].entry.getLoginModuleName(),
                                true,
                                contextClassLoader);

                    // 获取构造函数
                    Constructor<?> constructor = c.getConstructor(PARAMS);
                    Object[] args = { };
                    // 创建实例
                    moduleStack[i].module = constructor.newInstance(args);

                    // call the LoginModule's initialize method
                    // 获取 initialize 方法
                    methods = moduleStack[i].module.getClass().getMethods();
                    for (mIndex = 0; mIndex < methods.length; mIndex++) {
                        if (methods[mIndex].getName().equals(INIT_METHOD)) {
                            break;
                        }
                    }

                    Object[] initArgs = {subject,
                                        callbackHandler,
                                        state,
                                        moduleStack[i].entry.getOptions() };
                    // invoke the LoginModule initialize method
                    //
                    // Throws ArrayIndexOutOfBoundsException if no such
                    // method defined.  May improve to use LoginException in
                    // the future.
                    // 初始化
                    methods[mIndex].invoke(moduleStack[i].module, initArgs);
                }

                // find the requested method in the LoginModule
                // 在 LoginModule 中 查询需要的方法
                for (mIndex = 0; mIndex < methods.length; mIndex++) {
                    if (methods[mIndex].getName().equals(methodName)) {
                        break;
                    }
                }

                // set up the arguments to be passed to the LoginModule method
                // 设置要传递给LoginModule方法的参数
                Object[] args = { };

                // invoke the LoginModule method
                //
                // Throws ArrayIndexOutOfBoundsException if no such
                // method defined.  May improve to use LoginException in
                // the future.
                //
                // 如果没有定义这样的方法，则抛出ArrayIndexOutOfBoundsException异常。将来可能会改进
                // 使用LoginException。
                boolean status = ((Boolean)methods[mIndex].invoke
                                (moduleStack[i].module, args)).booleanValue();

                if (status == true) {

                    // if SUFFICIENT, return if no prior REQUIRED errors
                    //
                    // 如果足够，如果没有之前的REQUIRED错误返回
                    if (!methodName.equals(ABORT_METHOD) &&
                        !methodName.equals(LOGOUT_METHOD) &&
                        moduleStack[i].entry.getControlFlag() ==
                    AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT &&
                        firstRequiredError == null) {

                        // clear state
                        clearState();

                        if (debug != null)
                            debug.println(methodName + " SUFFICIENT success");
                        return;
                    }

                    if (debug != null)
                        debug.println(methodName + " success");
                    success = true;
                } else {
                    if (debug != null)
                        debug.println(methodName + " ignored");
                }

            } catch (NoSuchMethodException nsme) {
                MessageFormat form = new MessageFormat(ResourcesMgr.getString
                        ("unable.to.instantiate.LoginModule.module.because.it.does.not.provide.a.no.argument.constructor"));
                Object[] source = {moduleStack[i].entry.getLoginModuleName()};
                throwException(null, new LoginException(form.format(source)));
            } catch (InstantiationException ie) {
                throwException(null, new LoginException(ResourcesMgr.getString
                        ("unable.to.instantiate.LoginModule.") +
                        ie.getMessage()));
            } catch (ClassNotFoundException cnfe) {
                throwException(null, new LoginException(ResourcesMgr.getString
                        ("unable.to.find.LoginModule.class.") +
                        cnfe.getMessage()));
            } catch (IllegalAccessException iae) {
                throwException(null, new LoginException(ResourcesMgr.getString
                        ("unable.to.access.LoginModule.") +
                        iae.getMessage()));
            } catch (InvocationTargetException ite) {

                // failure cases

                LoginException le;

                if (ite.getCause() instanceof PendingException &&
                    methodName.equals(LOGIN_METHOD)) {

                    // XXX
                    //
                    // if a module's LOGIN_METHOD threw a PendingException
                    // then immediately throw it.
                    //
                    // when LoginContext is called again,
                    // the module that threw the exception is invoked first
                    // (the module list is not invoked from the start).
                    // previously thrown exception state is still present.
                    //
                    // it is assumed that the module which threw
                    // the exception can have its
                    // LOGIN_METHOD invoked twice in a row
                    // without any commit/abort in between.
                    //
                    // in all cases when LoginContext returns
                    // (either via natural return or by throwing an exception)
                    // we need to call clearState before returning.
                    // the only time that is not true is in this case -
                    // do not call throwException here.

                    throw (PendingException)ite.getCause();

                } else if (ite.getCause() instanceof LoginException) {

                    le = (LoginException)ite.getCause();

                } else if (ite.getCause() instanceof SecurityException) {

                    // do not want privacy leak
                    // (e.g., sensitive file path in exception msg)

                    le = new LoginException("Security Exception");
                    le.initCause(new SecurityException());
                    if (debug != null) {
                        debug.println
                            ("original security exception with detail msg " +
                            "replaced by new exception with empty detail msg");
                        debug.println("original security exception: " +
                                ite.getCause().toString());
                    }
                } else {

                    // capture an unexpected LoginModule exception
                    java.io.StringWriter sw = new java.io.StringWriter();
                    ite.getCause().printStackTrace
                                                (new java.io.PrintWriter(sw));
                    sw.flush();
                    le = new LoginException(sw.toString());
                }

                if (moduleStack[i].entry.getControlFlag() ==
                    AppConfigurationEntry.LoginModuleControlFlag.REQUISITE) {

                    if (debug != null)
                        debug.println(methodName + " REQUISITE failure");

                    // if REQUISITE, then immediately throw an exception
                    if (methodName.equals(ABORT_METHOD) ||
                        methodName.equals(LOGOUT_METHOD)) {
                        if (firstRequiredError == null)
                            firstRequiredError = le;
                    } else {
                        throwException(firstRequiredError, le);
                    }

                } else if (moduleStack[i].entry.getControlFlag() ==
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED) {

                    if (debug != null)
                        debug.println(methodName + " REQUIRED failure");

                    // mark down that a REQUIRED module failed
                    if (firstRequiredError == null)
                        firstRequiredError = le;

                } else {

                    if (debug != null)
                        debug.println(methodName + " OPTIONAL failure");

                    // mark down that an OPTIONAL module failed
                    if (firstError == null)
                        firstError = le;
                }
            }
        }

        // we went thru all the LoginModules.
        if (firstRequiredError != null) {
            // a REQUIRED module failed -- return the error
            throwException(firstRequiredError, null);
        } else if (success == false && firstError != null) {
            // no module succeeded -- return the first error
            throwException(firstError, null);
        } else if (success == false) {
            // no module succeeded -- all modules were IGNORED
            throwException(new LoginException
                (ResourcesMgr.getString("Login.Failure.all.modules.ignored")),
                null);
        } else {
            // success

            clearState();
            return;
        }
    }

    /**
     * Wrap the caller-specified CallbackHandler in our own
     * and invoke it within a privileged block, constrained by
     * the caller's AccessControlContext.
     */
    private static class SecureCallbackHandler implements CallbackHandler {

        private final java.security.AccessControlContext acc;
        private final CallbackHandler ch;

        SecureCallbackHandler(java.security.AccessControlContext acc,
                        CallbackHandler ch) {
            this.acc = acc;
            this.ch = ch;
        }

        public void handle(final Callback[] callbacks)
                throws java.io.IOException, UnsupportedCallbackException {
            try {
                java.security.AccessController.doPrivileged
                    (new java.security.PrivilegedExceptionAction<Void>() {
                    public Void run() throws java.io.IOException,
                                        UnsupportedCallbackException {
                        ch.handle(callbacks);
                        return null;
                    }
                }, acc);
            } catch (java.security.PrivilegedActionException pae) {
                if (pae.getException() instanceof java.io.IOException) {
                    throw (java.io.IOException)pae.getException();
                } else {
                    throw (UnsupportedCallbackException)pae.getException();
                }
            }
        }
    }

    /**
     * LoginModule information -
     *          incapsulates Configuration info and actual module instances
     */
    private static class ModuleInfo {
        AppConfigurationEntry entry;
        Object module;

        ModuleInfo(AppConfigurationEntry newEntry, Object newModule) {
            this.entry = newEntry;
            this.module = newModule;
        }
    }
}
