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

package javax.security.auth.spi;

import javax.security.auth.Subject;
import javax.security.auth.AuthPermission;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import java.util.Map;

/**
 * <p> {@code LoginModule} describes the interface
 * implemented by authentication technology providers.  LoginModules
 * are plugged in under applications to provide a particular type of
 * authentication.
 *
 * <p> While applications write to the {@code LoginContext} API,
 * authentication technology providers implement the
 * {@code LoginModule} interface.
 * A {@code Configuration} specifies the LoginModule(s)
 * to be used with a particular login application.  Therefore different
 * LoginModules can be plugged in under the application without
 * requiring any modifications to the application itself.
 *
 * <p> The {@code LoginContext} is responsible for reading the
 * {@code Configuration} and instantiating the appropriate
 * LoginModules.  Each {@code LoginModule} is initialized with
 * a {@code Subject}, a {@code CallbackHandler}, shared
 * {@code LoginModule} state, and LoginModule-specific options.
 *
 * The {@code Subject} represents the
 * {@code Subject} currently being authenticated and is updated
 * with relevant Credentials if authentication succeeds.
 * LoginModules use the {@code CallbackHandler} to
 * communicate with users.  The {@code CallbackHandler} may be
 * used to prompt for usernames and passwords, for example.
 * Note that the {@code CallbackHandler} may be null.  LoginModules
 * which absolutely require a {@code CallbackHandler} to authenticate
 * the {@code Subject} may throw a {@code LoginException}.
 * LoginModules optionally use the shared state to share information
 * or data among themselves.
 *
 * <p> The LoginModule-specific options represent the options
 * configured for this {@code LoginModule} by an administrator or user
 * in the login {@code Configuration}.
 * The options are defined by the {@code LoginModule} itself
 * and control the behavior within it.  For example, a
 * {@code LoginModule} may define options to support debugging/testing
 * capabilities.  Options are defined using a key-value syntax,
 * such as <i>debug=true</i>.  The {@code LoginModule}
 * stores the options as a {@code Map} so that the values may
 * be retrieved using the key.  Note that there is no limit to the number
 * of options a {@code LoginModule} chooses to define.
 *
 * <p> The calling application sees the authentication process as a single
 * operation.  However, the authentication process within the
 * {@code LoginModule} proceeds in two distinct phases.
 * In the first phase, the LoginModule's
 * {@code login} method gets invoked by the LoginContext's
 * {@code login} method.  The {@code login}
 * method for the {@code LoginModule} then performs
 * the actual authentication (prompt for and verify a password for example)
 * and saves its authentication status as private state
 * information.  Once finished, the LoginModule's {@code login}
 * method either returns {@code true} (if it succeeded) or
 * {@code false} (if it should be ignored), or throws a
 * {@code LoginException} to specify a failure.
 * In the failure case, the {@code LoginModule} must not retry the
 * authentication or introduce delays.  The responsibility of such tasks
 * belongs to the application.  If the application attempts to retry
 * the authentication, the LoginModule's {@code login} method will be
 * called again.
 *
 * <p> In the second phase, if the LoginContext's overall authentication
 * succeeded (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
 * LoginModules succeeded), then the {@code commit}
 * method for the {@code LoginModule} gets invoked.
 * The {@code commit} method for a {@code LoginModule} checks its
 * privately saved state to see if its own authentication succeeded.
 * If the overall {@code LoginContext} authentication succeeded
 * and the LoginModule's own authentication succeeded, then the
 * {@code commit} method associates the relevant
 * Principals (authenticated identities) and Credentials (authentication data
 * such as cryptographic keys) with the {@code Subject}
 * located within the {@code LoginModule}.
 *
 * <p> If the LoginContext's overall authentication failed (the relevant
 * REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules did not succeed),
 * then the {@code abort} method for each {@code LoginModule}
 * gets invoked.  In this case, the {@code LoginModule} removes/destroys
 * any authentication state originally saved.
 *
 * <p> Logging out a {@code Subject} involves only one phase.
 * The {@code LoginContext} invokes the LoginModule's {@code logout}
 * method.  The {@code logout} method for the {@code LoginModule}
 * then performs the logout procedures, such as removing Principals or
 * Credentials from the {@code Subject} or logging session information.
 *
 * <p> A {@code LoginModule} implementation must have a constructor with
 * no arguments.  This allows classes which load the {@code LoginModule}
 * to instantiate it.
 *
 * LoginModule描述了认证技术提供商实现的接口。loginmodule被插入到应用程序中，以提供特定类型的身份验证。
 *
 * 当应用程序写入LoginContext API时，身份验证技术提供商实现了LoginModule接口。一个Configuration
 * 指定用于特定登录应用程序的LoginModule。因此，可以在应用程序下插入不同的loginmodule，而不需要对
 * 应用程序本身进行任何修改。
 *
 *
 *
 * LoginContext负责读取Configuration并实例化相应的loginmodule。每个LoginModule都用一个Subject、
 * 一个CallbackHandler、共享的LoginModule状态和特定于LoginModule的选项来初始化。Subject表示当前
 * 正在验证的Subject，如果验证成功，则使用相关凭据更新。loginmodule使用CallbackHandler来与用户通信。
 * 例如，CallbackHandler可以用来提示输入用户名和密码。注意，CallbackHandler可能为空。绝对需要
 * CallbackHandler来验证Subject的LoginModules可能会抛出LoginException。LoginModules可以选择
 * 使用共享状态在它们之间共享信息或数据。
 *
 *
 *
 * 特定于LoginModule的选项表示管理员或用户在登录Configuration中为这个LoginModule配置的选项。
 * 这些选项是由LoginModule本身定义的，并控制其中的行为。例如，LoginModule可以定义支持调试/测试功能
 * 的选项。选项是使用键值语法定义的，比如debug=true。LoginModule将选项存储为{@code Map}，这样就
 * 可以使用键来检索值。注意，LoginModule没有限制选项的数量。
 *
 *
 *
 * 调用应用程序将身份验证过程视为单个操作。然而，LoginModule中的身份验证过程分为两个不同的阶段。
 * 在第一阶段，LoginModule的login方法被LoginContext的login方法调用。LoginModule的login方法
 * 然后执行实际的身份验证(例如提示并验证密码)，并将其身份验证状态保存为私有状态信息。一旦完成，
 * LoginModule的login方法要么返回{@code true}(如果它成功)，要么返回{@code false}(如果它应该被忽略)，
 * 要么抛出一个LoginException来指定失败。在失败的情况下，LoginModule不能重试身份验证或引入延迟。
 * 这些任务的责任属于应用程序。如果应用程序尝试重试身份验证，LoginModule的login方法将再次被调用。
 *
 *
 *
 * 在第二阶段，如果LoginContext的整体身份验证成功(相关的REQUIRED、REQUISITE、SUFFICIENT和
 * OPTIONAL LoginModule成功)，则调用LoginModule的commit方法。LoginModule的commit方法会
 * 检查它私有保存的状态，看看它自己的身份验证是否成功。如果整个LoginContext认证成功，并且LoginModule
 * 自己的认证成功，那么commit方法将相关的主体(已认证的身份)和凭证(认证数据，比如加密密钥)与位于LoginModule
 * 中的Subject关联。
 *
 *
 *
 * 如果LoginContext的整体身份验证失败(相关的REQUIRED、REQUISITE、SUFFICIENT和OPTIONAL LoginModule
 * 没有成功)，则调用每个LoginModule的abort方法。在这种情况下，LoginModule删除/销毁最初保存的任何
 * 身份验证状态。
 *
 *
 *
 * 注销Subject只涉及一个阶段。LoginContext调用LoginModule的logout方法。LoginModule的logout
 * 方法然后执行注销过程，例如从Subject或日志会话信息中删除主体或凭据。
 *
 *
 *
 * 一个LoginModule实现必须有一个不带参数的构造函数。这允许加载LoginModule的类实例化它。
 *
 * @see javax.security.auth.login.LoginContext
 * @see javax.security.auth.login.Configuration
 */
public interface LoginModule {

    /**
     * Initialize this LoginModule.
     *
     * <p> This method is called by the {@code LoginContext}
     * after this {@code LoginModule} has been instantiated.
     * The purpose of this method is to initialize this
     * {@code LoginModule} with the relevant information.
     * If this {@code LoginModule} does not understand
     * any of the data stored in {@code sharedState} or
     * {@code options} parameters, they can be ignored.
     *
     * <p>
     *
     * @param subject the {@code Subject} to be authenticated. <p>
     *
     * @param callbackHandler a {@code CallbackHandler} for communicating
     *                  with the end user (prompting for usernames and
     *                  passwords, for example). <p>
     *
     * @param sharedState state shared with other configured LoginModules. <p>
     *
     * @param options options specified in the login
     *                  {@code Configuration} for this particular
     *                  {@code LoginModule}.
     */
    void initialize(Subject subject, CallbackHandler callbackHandler,
                    Map<String,?> sharedState,
                    Map<String,?> options);

    /**
     * Method to authenticate a {@code Subject} (phase 1).
     *
     * <p> The implementation of this method authenticates
     * a {@code Subject}.  For example, it may prompt for
     * {@code Subject} information such
     * as a username and password and then attempt to verify the password.
     * This method saves the result of the authentication attempt
     * as private state within the LoginModule.
     *
     * <p>
     *
     * @exception LoginException if the authentication fails
     *
     * @return true if the authentication succeeded, or false if this
     *                  {@code LoginModule} should be ignored.
     */
    boolean login() throws LoginException;

    /**
     * Method to commit the authentication process (phase 2).
     *
     * <p> This method is called if the LoginContext's
     * overall authentication succeeded
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules
     * succeeded).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (checked by retrieving the private state saved by the
     * {@code login} method), then this method associates relevant
     * Principals and Credentials with the {@code Subject} located in the
     * {@code LoginModule}.  If this LoginModule's own
     * authentication attempted failed, then this method removes/destroys
     * any state that was originally saved.
     *
     * <p>
     *
     * @exception LoginException if the commit fails
     *
     * @return true if this method succeeded, or false if this
     *                  {@code LoginModule} should be ignored.
     */
    boolean commit() throws LoginException;

    /**
     * Method to abort the authentication process (phase 2).
     *
     * <p> This method is called if the LoginContext's
     * overall authentication failed.
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules
     * did not succeed).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (checked by retrieving the private state saved by the
     * {@code login} method), then this method cleans up any state
     * that was originally saved.
     *
     * <p>
     *
     * @exception LoginException if the abort fails
     *
     * @return true if this method succeeded, or false if this
     *                  {@code LoginModule} should be ignored.
     */
    boolean abort() throws LoginException;

    /**
     * Method which logs out a {@code Subject}.
     *
     * <p>An implementation of this method might remove/destroy a Subject's
     * Principals and Credentials.
     *
     * <p>
     *
     * @exception LoginException if the logout fails
     *
     * @return true if this method succeeded, or false if this
     *                  {@code LoginModule} should be ignored.
     */
    boolean logout() throws LoginException;
}
