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

package java.security;

/**
 * Abstract class for representing access to a system resource.
 * All permissions have a name (whose interpretation depends on the subclass),
 * as well as abstract functions for defining the semantics of the
 * particular Permission subclass.
 *
 * <p>Most Permission objects also include an "actions" list that tells the actions
 * that are permitted for the object.  For example,
 * for a {@code java.io.FilePermission} object, the permission name is
 * the pathname of a file (or directory), and the actions list
 * (such as "read, write") specifies which actions are granted for the
 * specified file (or for files in the specified directory).
 * The actions list is optional for Permission objects, such as
 * {@code java.lang.RuntimePermission},
 * that don't need such a list; you either have the named permission (such
 * as "system.exit") or you don't.
 *
 * <p>An important method that must be implemented by each subclass is
 * the {@code implies} method to compare Permissions. Basically,
 * "permission p1 implies permission p2" means that
 * if one is granted permission p1, one is naturally granted permission p2.
 * Thus, this is not an equality test, but rather more of a
 * subset test.
 *
 * <P> Permission objects are similar to String objects in that they
 * are immutable once they have been created. Subclasses should not
 * provide methods that can change the state of a permission
 * once it has been created.
 *
 * @see Permissions
 * @see PermissionCollection
 *
 *
 * @author Marianne Mueller
 * @author Roland Schemers
 *
 *
 * 表示对系统资源的访问的抽象类。所有权限都有一个名称(其解释取决于子类)，以及用于
 * 定义特定Permission子类语义的抽象函数。
 *
 * 大多数Permission对象还包括一个“action”列表，它告诉对象允许哪些动作。例如，
 * 对于{@code java.io.FilePermission}对象，权限名是文件(或目录)的路径名，
 * 操作列表(如“读、写”)指定了对指定文件(或指定目录中的文件)授予哪些操作。操作列表
 * 对于Permission对象是可选的，例如{@code java.lang.RuntimePermission}，
 * 它不需要这样的列表;您要么拥有命名权限(如“system.exit”)，要么没有。
 *
 *
 * 每个子类必须实现的一个重要方法是用来比较Permissions的{@code implies}方法。
 * 基本上，“permission p1 implies permission p2”意味着如果一个人被授予了权限p1，
 * 那么他自然就被授予了权限p2。因此，这不是一个等式检验，而更像是一个子集检验。
 *
 * 权限对象类似于String对象，因为它们一旦被创建就不可变。一旦创建了权限，子类不应该
 * 提供可以更改权限状态的方法。
 */

public abstract class Permission implements Guard, java.io.Serializable {

    private static final long serialVersionUID = -5636570222231596674L;

    private String name;

    /**
     * Constructs a permission with the specified name.
     *
     * @param name name of the Permission object being created.
     *
     * 根据名称 构建一个指定的权限
     *
     */

    public Permission(String name) {
        this.name = name;
    }

    /**
     * Implements the guard interface for a permission. The
     * {@code SecurityManager.checkPermission} method is called,
     * passing this permission object as the permission to check.
     * Returns silently if access is granted. Otherwise, throws
     * a SecurityException.
     *
     * 实现权限的保护接口。{@code SecurityManager。checkPermission}方法被调用，
     * 并将此权限对象作为检查的权限传递。如果允许访问，则以静默方式返回。否则，
     * 抛出一个SecurityException。
     *
     * @param object the object being guarded (currently ignored).
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        {@code checkPermission} method doesn't allow access.
     *
     * @see Guard
     * @see GuardedObject
     * @see SecurityManager#checkPermission
     *
     */
    public void checkGuard(Object object) throws SecurityException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(this);
    }

    /**
     * Checks if the specified permission's actions are "implied by"
     * this object's actions.
     * <P>
     * This must be implemented by subclasses of Permission, as they are the
     * only ones that can impose semantics on a Permission object.
     *
     * <p>The {@code implies} method is used by the AccessController to determine
     * whether or not a requested permission is implied by another permission that
     * is known to be valid in the current execution context.
     *
     * 检查指定权限的操作是否由该对象的操作“隐含”。
     *
     * 这必须由Permission的子类实现，因为它们是唯一可以将语义强加到Permission对象上的子类。
     *
     *
     *
     * AccessController使用{@code implies}方法来确定一个请求的权限是否由另一个已知
     * 在当前执行上下文中有效的权限隐含。
     *
     * @param permission the permission to check against.
     *
     * @return true if the specified permission is implied by this object,
     * false if not.
     */

    public abstract boolean implies(Permission permission);

    /**
     * Checks two Permission objects for equality.
     * <P>
     * Do not use the {@code equals} method for making access control
     * decisions; use the {@code implies} method.
     *
     *
     * 检查两个Permission对象是否相等。
     *
     * 不要使用{@code equals}方法来做访问控制决策;使用{@code implies}方法。
     *
     * @param obj the object we are testing for equality with this object.
     *
     * @return true if both Permission objects are equivalent.
     */

    public abstract boolean equals(Object obj);

    /**
     * Returns the hash code value for this Permission object.
     * <P>
     * The required {@code hashCode} behavior for Permission Objects is
     * the following:
     * <ul>
     * <li>Whenever it is invoked on the same Permission object more than
     *     once during an execution of a Java application, the
     *     {@code hashCode} method
     *     must consistently return the same integer. This integer need not
     *     remain consistent from one execution of an application to another
     *     execution of the same application.
     * <li>If two Permission objects are equal according to the
     *     {@code equals}
     *     method, then calling the {@code hashCode} method on each of the
     *     two Permission objects must produce the same integer result.
     * </ul>
     *
     * @return a hash code value for this object.
     *
     * 返回此Permission对象的哈希码值。
     *
     * 权限对象需要的{@code hashCode}行为如下:
     *
     * 1. 在Java应用程序的执行过程中，每当它在同一个Permission对象上被多次调用时，
     *   {@code hashCode}方法必须一致地返回相同的整数。该整数在应用程序的一次执行
     *   和同一应用程序的另一次执行之间不必保持一致。
     * 2. 如果两个Permission对象根据{@code equals}方法相等，那么在这两个Permission
     *   对象上调用{@code hashCode}方法必须产生相同的整数结果。
     */

    public abstract int hashCode();

    /**
     * Returns the name of this Permission.
     * For example, in the case of a {@code java.io.FilePermission},
     * the name will be a pathname.
     *
     * 返回此权限的名称。例如，在{@code java.io。FilePermission}，该名称将是一个路径名。
     *
     * @return the name of this Permission.
     *
     */

    public final String getName() {
        return name;
    }

    /**
     * Returns the actions as a String. This is abstract
     * so subclasses can defer creating a String representation until
     * one is needed. Subclasses should always return actions in what they
     * consider to be their
     * canonical form. For example, two FilePermission objects created via
     * the following:
     *
     * <pre>
     *   perm1 = new FilePermission(p1,"read,write");
     *   perm2 = new FilePermission(p2,"write,read");
     * </pre>
     *
     * both return
     * "read,write" when the {@code getActions} method is invoked.
     *
     * @return the actions of this Permission.
     *
     * 以字符串的形式返回动作。这是抽象的，所以子类可以延迟创建字符串表示，直到需要。
     * 子类应该始终以它们认为是规范形式的方式返回操作。例如，通过以下方式创建的两个
     * FilePermission对象:
     *
     * <pre>
     *   perm1 = new FilePermission(p1,"read,write");
     *   perm2 = new FilePermission(p2,"write,read");
     * </pre>
     *
     * 当调用{@code getActions}方法时，两者都返回“read,write”。
     */

    public abstract String getActions();

    /**
     * Returns an empty PermissionCollection for a given Permission object, or null if
     * one is not defined. Subclasses of class Permission should
     * override this if they need to store their permissions in a particular
     * PermissionCollection object in order to provide the correct semantics
     * when the {@code PermissionCollection.implies} method is called.
     * If null is returned,
     * then the caller of this method is free to store permissions of this
     * type in any PermissionCollection they choose (one that uses a Hashtable,
     * one that uses a Vector, etc).
     *
     * 为给定的Permission对象返回一个空的PermissionCollection，或者没有定义该对象。
     * 类的子类许可应覆盖这个如果他们需要存储在一个特定的权限PermissionCollection对象
     * 为了{@code PermissionCollection时提供正确的语义。调用Implies}方法。如果返回
     * null，则该方法的调用者可以自由地将该类型的权限存储在他们选择的任何PermissionCollection
     * 中(使用Hashtable的权限、使用Vector的权限等)。
     *
     * @return a new PermissionCollection object for this type of Permission, or
     * null if one is not defined.
     */

    public PermissionCollection newPermissionCollection() {
        return null;
    }

    /**
     * Returns a string describing this Permission.  The convention is to
     * specify the class name, the permission name, and the actions in
     * the following format: '("ClassName" "name" "actions")', or
     * '("ClassName" "name")' if actions list is null or empty.
     *
     * @return information about this Permission.
     */
    public String toString() {
        String actions = getActions();
        if ((actions == null) || (actions.length() == 0)) { // OPTIONAL
            return "(\"" + getClass().getName() + "\" \"" + name + "\")";
        } else {
            return "(\"" + getClass().getName() + "\" \"" + name +
                 "\" \"" + actions + "\")";
        }
    }
}
