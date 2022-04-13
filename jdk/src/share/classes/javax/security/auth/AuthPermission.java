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

package javax.security.auth;

/**
 * This class is for authentication permissions.
 * An AuthPermission contains a name
 * (also referred to as a "target name")
 * but no actions list; you either have the named permission
 * or you don't.
 *
 * <p> The target name is the name of a security configuration parameter
 * (see below).  Currently the AuthPermission object is used to
 * guard access to the Policy, Subject, LoginContext,
 * and Configuration objects.
 *
 * 该类用于身份验证权限。AuthPermission包含一个名称(也称为“target name”)，
 * 但没有操作列表;你要么有命名权限，要么没有。
 *
 * target name 是安全配置参数的名称(参见下面)。目前，AuthPermission对象
 * 用于保护对Policy、Subject、LoginContext和Configuration对象的访问。
 *
 * <p> The possible target names for an Authentication Permission are:
 *
 * 身份验证权限可能的目标名称为:
 *
 * <pre>
 *      doAs -                  allow the caller to invoke the
 *                              {@code Subject.doAs} methods.
 *                              允许调用者调用{@code Subject.doAs}的方法。
 *
 *      doAsPrivileged -        allow the caller to invoke the
 *                              {@code Subject.doAsPrivileged} methods.
 *                              运行调用 Subject.doAsPrivileged 方法
 *
 *      getSubject -            allow for the retrieval of the
 *                              Subject(s) associated with the
 *                              current Thread.
 *
 *                              允许检索与当前线程相关的主题。
 *
 *      getSubjectFromDomainCombiner -  allow for the retrieval of the
 *                              Subject associated with the
 *                              a {@code SubjectDomainCombiner}.
 *
 *                              允许检索与一个{@code SubjectDomainCombiner相关的主题
 *
 *      setReadOnly -           allow the caller to set a Subject
 *                              to be read-only.
 *
 *                              允许调用者将Subject设置为只读。
 *
 *      modifyPrincipals -      allow the caller to modify the {@code Set}
 *                              of Principals associated with a
 *                              {@code Subject}
 *
 *                              运行修改 Principals
 *
 *      modifyPublicCredentials - allow the caller to modify the
 *                              {@code Set} of public credentials
 *                              associated with a {@code Subject}
 *
 *                              允许修改公共的 credentials
 *
 *      modifyPrivateCredentials - allow the caller to modify the
 *                              {@code Set} of private credentials
 *                              associated with a {@code Subject}
 *
 *                              允许修改私有的 credentials
 *
 *      refreshCredential -     allow code to invoke the {@code refresh}
 *                              method on a credential which implements
 *                              the {@code Refreshable} interface.
 *
 *                              允许调用 refresh 刷新 credential
 *
 *      destroyCredential -     allow code to invoke the {@code destroy}
 *                              method on a credential {@code object}
 *                              which implements the {@code Destroyable}
 *                              interface.
 *
 *                              允许调用 destroy 摧毁 credential
 *
 *      createLoginContext.{name} -  allow code to instantiate a
 *                              {@code LoginContext} with the
 *                              specified <i>name</i>.  <i>name</i>
 *                              is used as the index into the installed login
 *                              {@code Configuration}
 *                              (that returned by
 *                              {@code Configuration.getConfiguration()}).
 *                              <i>name</i> can be wildcarded (set to '*')
 *                              to allow for any name.
 *
 *                              允许代码用指定的name实例化一个{@code LoginContext}。
 *                              name被用作已安装登录{@code Configuration}(由{@code Configuration.getconfiguration()}返回)
 *                              的索引。name可以通配符(设置为'*')允许任何名称。
 *
 *      getLoginConfiguration - allow for the retrieval of the system-wide
 *                              login Configuration.
 *
 *                              允许检索系统范围的登录配置。
 *
 *      createLoginConfiguration.{type} - allow code to obtain a Configuration
 *                              object via
 *                              {@code Configuration.getInstance}.
 *
 *                              允许代码通过{@code Configuration. getinstance}获得一个Configuration对象。
 *
 *
 *      setLoginConfiguration - allow for the setting of the system-wide
 *                              login Configuration.
 *
 *                              允许设置一个系统之外的配置
 *
 *      refreshLoginConfiguration - allow for the refreshing of the system-wide
 *                              login Configuration.
 *
 *                              运行刷新系统之外的配置
 * </pre>
 *
 * 下面是一些废弃的参数
 *
 * <p> The following target name has been deprecated in favor of
 * {@code createLoginContext.{name}}.
 *
 *
 * <pre>
 *      createLoginContext -    allow code to instantiate a
 *                              {@code LoginContext}.
 * </pre>
 *
 * <p> {@code javax.security.auth.Policy} has been
 * deprecated in favor of {@code java.security.Policy}.
 * Therefore, the following target names have also been deprecated:
 *
 * <pre>
 *      getPolicy -             allow the caller to retrieve the system-wide
 *                              Subject-based access control policy.
 *
 *      setPolicy -             allow the caller to set the system-wide
 *                              Subject-based access control policy.
 *
 *      refreshPolicy -         allow the caller to refresh the system-wide
 *                              Subject-based access control policy.
 * </pre>
 *
 */
public final class AuthPermission extends
java.security.BasicPermission {

    private static final long serialVersionUID = 5806031445061587174L;

    /**
     * Creates a new AuthPermission with the specified name.
     * The name is the symbolic name of the AuthPermission.
     *
     * <p>
     *
     * @param name the name of the AuthPermission
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public AuthPermission(String name) {
        // for backwards compatibility --
        // createLoginContext is deprecated in favor of createLoginContext.*
        super("createLoginContext".equals(name) ?
                "createLoginContext.*" : name);
    }

    /**
     * Creates a new AuthPermission object with the specified name.
     * The name is the symbolic name of the AuthPermission, and the
     * actions String is currently unused and should be null.
     *
     * <p>
     *
     * @param name the name of the AuthPermission <p>
     *
     * @param actions should be null.
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public AuthPermission(String name, String actions) {
        // for backwards compatibility --
        // createLoginContext is deprecated in favor of createLoginContext.*
        super("createLoginContext".equals(name) ?
                "createLoginContext.*" : name, actions);
    }
}
