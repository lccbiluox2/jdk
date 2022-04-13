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

package javax.security.auth.kerberos;

import java.util.*;
import java.security.Permission;
import java.security.PermissionCollection;
import java.io.ObjectStreamField;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 * This class is used to protect Kerberos services and the
 * credentials necessary to access those services. There is a one to
 * one mapping of a service principal and the credentials necessary
 * to access the service. Therefore granting access to a service
 * principal implicitly grants access to the credential necessary to
 * establish a security context with the service principal. This
 * applies regardless of whether the credentials are in a cache
 * or acquired via an exchange with the KDC. The credential can
 * be either a ticket granting ticket, a service ticket or a secret
 * key from a key table.
 *
 * 该类用于保护Kerberos服务和访问这些服务所需的凭证。服务主体和访问服务所必需的凭据之间
 * 存在一对一的映射。因此，授予对服务主体的访问权隐式地授予对与服务主体建立安全上下文
 * 所必需的凭证的访问权。无论凭证是在缓存中，还是通过与KDC交换获得，这都适用。该凭证
 * 可以是一个票据授予票据、一个服务票据或一个密钥表的秘密密钥。
 *
 *
 * <p>
 * A ServicePermission contains a service principal name and
 * a list of actions which specify the context the credential can be
 * used within.
 * <p>
 * The service principal name is the canonical name of the
 * {@code KerberosPrincipal} supplying the service, that is
 * the KerberosPrincipal represents a Kerberos service
 * principal. This name is treated in a case sensitive manner.
 * An asterisk may appear by itself, to signify any service principal.
 * <p>
 * Granting this permission implies that the caller can use a cached
 * credential (TGT, service ticket or secret key) within the context
 * designated by the action. In the case of the TGT, granting this
 * permission also implies that the TGT can be obtained by an
 * Authentication Service exchange.
 *
 *
 * ServicePermission包含一个服务主体名称和一个操作列表，这些操作指定了可以
 * 在其中使用凭据的上下文。
 *
 *
 * 服务主体名称是提供服务的{@code KerberosPrincipal}的规范名称，即KerberosPrincipal
 * 表示一个Kerberos服务主体。该名称以区分大小写的方式处理。星号可以单独出现，表示任何服务主体。

 * 授予此权限意味着调用者可以在操作指定的上下文中使用缓存的凭据(TGT、服务票据或密钥)。在TGT
 * 的情况下，授予此权限还意味着TGT可以通过身份验证服务交换获得。
 *
 * <p>
 * The possible actions are:
 *
 * <pre>
 *    initiate -              allow the caller to use the credential to
 *                            initiate a security context with a service
 *                            principal.
 *
 *    accept -                allow the caller to use the credential to
 *                            accept security context as a particular
 *                            principal.
 * </pre>
 *
 * For example, to specify the permission to access to the TGT to
 * initiate a security context the permission is constructed as follows:
 *
 * <pre>
 *     ServicePermission("krbtgt/EXAMPLE.COM@EXAMPLE.COM", "initiate");
 * </pre>
 * <p>
 * To obtain a service ticket to initiate a context with the "host"
 * service the permission is constructed as follows:
 * <pre>
 *     ServicePermission("host/foo.example.com@EXAMPLE.COM", "initiate");
 * </pre>
 * <p>
 * For a Kerberized server the action is "accept". For example, the permission
 * necessary to access and use the secret key of the  Kerberized "host"
 * service (telnet and the likes)  would be constructed as follows:
 *
 * <pre>
 *     ServicePermission("host/foo.example.com@EXAMPLE.COM", "accept");
 * </pre>
 *
 * 可能的actions是:
 *
 *    initiate —允许调用者使用凭据初始化一个带有服务主体的安全上下文。
 *    accept 允许调用者使用凭据接受安全上下文作为特定主体。
 *
 * 例如，要指定对TGT的访问权限以初始化一个安全上下文，该权限的构造如下
 *
 *     ServicePermission("krbtgt/EXAMPLE.COM@EXAMPLE.COM", "initiate"
 *
 * 为了获得一个服务票据来初始化一个带有“host”服务的上下文，权限构造如下:
 *
 *        ServicePermission("host/foo.example.com@EXAMPLE.COM", "initiat
 *
 * 对于Kerberized的服务器，操作是“accept”。例如，访问和使用kerberos的“hos”服务
 * (telnet等)的密钥所必需的p将被构造如下:
 *
 *    ServicePermission("host/foo.example.com@EXAMPLE.COM", "accept"
 *
 * @since 1.4
 */

public final class ServicePermission extends Permission
    implements java.io.Serializable {

    private static final long serialVersionUID = -1227585031618624935L;

    /**
     * Initiate a security context to the specified service
     *
     * 向指定的服务初始化一个安全上下文
     */
    private final static int INITIATE   = 0x1;

    /**
     * Accept a security context
     *
     * 接受安全上下文
     */
    private final static int ACCEPT     = 0x2;

    /**
     * All actions  所有权限
     */
    private final static int ALL        = INITIATE|ACCEPT;

    /**
     * No actions. 没有权限
     */
    private final static int NONE    = 0x0;

    // the actions mask  action的掩码
    private transient int mask;

    /**
     * the actions string.
     *
     * @serial
     */

    private String actions; // Left null as long as possible, then
                            // created and re-used in the getAction function.

    /**
     * Create a new {@code ServicePermission}
     * with the specified {@code servicePrincipal}
     * and {@code action}.
     *
     * 根据 servicePrincipal 和 action 构建一个 ServicePermission
     *
     * @param servicePrincipal the name of the service principal.
     * An asterisk may appear by itself, to signify any service principal.
     * <p>
     * @param action the action string
     */
    public ServicePermission(String servicePrincipal, String action) {
        // Note: servicePrincipal can be "@REALM" which means any principal in
        // this realm implies it. action can be "-" which means any
        // action implies it.
        //
        // 注意:servicePrincipal可以是“@REALM”，这意味着这个领域中的任何主体都会暗示它。
        // 动作可以是“-”，这意味着任何动作都暗示着它。
        super(servicePrincipal);
        init(servicePrincipal, getMask(action));
    }


    /**
     * Initialize the ServicePermission object.
     */
    private void init(String servicePrincipal, int mask) {

        if (servicePrincipal == null)
                throw new NullPointerException("service principal can't be null");

        if ((mask & ALL) != mask)
            throw new IllegalArgumentException("invalid actions mask");

        this.mask = mask;
    }


    /**
     * Checks if this Kerberos service permission object "implies" the
     * specified permission.
     * <P>
     * If none of the above are true, {@code implies} returns false.
     * @param p the permission to check against.
     *
     * @return true if the specified permission is implied by this object,
     * false if not.
     */
    public boolean implies(Permission p) {
        if (!(p instanceof ServicePermission))
            return false;

        ServicePermission that = (ServicePermission) p;

        return ((this.mask & that.mask) == that.mask) &&
            impliesIgnoreMask(that);
    }


    boolean impliesIgnoreMask(ServicePermission p) {
        return ((this.getName().equals("*")) ||
                this.getName().equals(p.getName()) ||
                (p.getName().startsWith("@") &&
                        this.getName().endsWith(p.getName())));
    }

    /**
     * Checks two ServicePermission objects for equality.
     * <P>
     * @param obj the object to test for equality with this object.
     *
     * @return true if <i>obj</i> is a ServicePermission, and has the
     *  same service principal, and actions as this
     * ServicePermission object.
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (! (obj instanceof ServicePermission))
            return false;

        ServicePermission that = (ServicePermission) obj;
        return ((this.mask & that.mask) == that.mask) &&
            this.getName().equals(that.getName());


    }

    /**
     * Returns the hash code value for this object.
     *
     * @return a hash code value for this object.
     */

    public int hashCode() {
        return (getName().hashCode() ^ mask);
    }


    /**
     * Returns the "canonical string representation" of the actions in the
     * specified mask.
     * Always returns present actions in the following order:
     * initiate, accept.
     *
     * @param mask a specific integer action mask to translate into a string
     * @return the canonical string representation of the actions
     */
    private static String getActions(int mask)
    {
        StringBuilder sb = new StringBuilder();
        boolean comma = false;

        if ((mask & INITIATE) == INITIATE) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("initiate");
        }

        if ((mask & ACCEPT) == ACCEPT) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("accept");
        }

        return sb.toString();
    }

    /**
     * Returns the canonical string representation of the actions.
     * Always returns present actions in the following order:
     * initiate, accept.
     */
    public String getActions() {
        if (actions == null)
            actions = getActions(this.mask);

        return actions;
    }


    /**
     * Returns a PermissionCollection object for storing
     * ServicePermission objects.
     * <br>
     * ServicePermission objects must be stored in a manner that
     * allows them to be inserted into the collection in any order, but
     * that also enables the PermissionCollection implies method to
     * be implemented in an efficient (and consistent) manner.
     *
     * @return a new PermissionCollection object suitable for storing
     * ServicePermissions.
     */
    public PermissionCollection newPermissionCollection() {
        return new KrbServicePermissionCollection();
    }

    /**
     * Return the current action mask.
     *
     * @return the actions mask.
     */
    int getMask() {
        return mask;
    }

    /**
     * Convert an action string to an integer actions mask.
     *
     * Note: if action is "-", action will be NONE, which means any
     * action implies it.
     *
     * @param action the action string.
     * @return the action mask
     */
    private static int getMask(String action) {

        if (action == null) {
            throw new NullPointerException("action can't be null");
        }

        if (action.equals("")) {
            throw new IllegalArgumentException("action can't be empty");
        }

        int mask = NONE;

        char[] a = action.toCharArray();

        if (a.length == 1 && a[0] == '-') {
            return mask;
        }

        int i = a.length - 1;

        while (i != -1) {
            char c;

            // skip whitespace
            while ((i!=-1) && ((c = a[i]) == ' ' ||
                               c == '\r' ||
                               c == '\n' ||
                               c == '\f' ||
                               c == '\t'))
                i--;

            // check for the known strings
            int matchlen;

            if (i >= 7 && (a[i-7] == 'i' || a[i-7] == 'I') &&
                          (a[i-6] == 'n' || a[i-6] == 'N') &&
                          (a[i-5] == 'i' || a[i-5] == 'I') &&
                          (a[i-4] == 't' || a[i-4] == 'T') &&
                          (a[i-3] == 'i' || a[i-3] == 'I') &&
                          (a[i-2] == 'a' || a[i-2] == 'A') &&
                          (a[i-1] == 't' || a[i-1] == 'T') &&
                          (a[i] == 'e' || a[i] == 'E'))
            {
                matchlen = 8;
                mask |= INITIATE;

            } else if (i >= 5 && (a[i-5] == 'a' || a[i-5] == 'A') &&
                                 (a[i-4] == 'c' || a[i-4] == 'C') &&
                                 (a[i-3] == 'c' || a[i-3] == 'C') &&
                                 (a[i-2] == 'e' || a[i-2] == 'E') &&
                                 (a[i-1] == 'p' || a[i-1] == 'P') &&
                                 (a[i] == 't' || a[i] == 'T'))
            {
                matchlen = 6;
                mask |= ACCEPT;

            } else {
                // parse error
                throw new IllegalArgumentException(
                        "invalid permission: " + action);
            }

            // make sure we didn't just match the tail of a word
            // like "ackbarfaccept".  Also, skip to the comma.
            boolean seencomma = false;
            while (i >= matchlen && !seencomma) {
                switch(a[i-matchlen]) {
                case ',':
                    seencomma = true;
                    break;
                case ' ': case '\r': case '\n':
                case '\f': case '\t':
                    break;
                default:
                    throw new IllegalArgumentException(
                            "invalid permission: " + action);
                }
                i--;
            }

            // point i at the location of the comma minus one (or -1).
            i -= matchlen;
        }

        return mask;
    }


    /**
     * WriteObject is called to save the state of the ServicePermission
     * to a stream. The actions are serialized, and the superclass
     * takes care of the name.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        // Write out the actions. The superclass takes care of the name
        // call getActions to make sure actions field is initialized
        if (actions == null)
            getActions();
        s.defaultWriteObject();
    }

    /**
     * readObject is called to restore the state of the
     * ServicePermission from a stream.
     */
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        // Read in the action, then initialize the rest
        s.defaultReadObject();
        init(getName(),getMask(actions));
    }


    /*
      public static void main(String args[]) throws Exception {
      ServicePermission this_ =
      new ServicePermission(args[0], "accept");
      ServicePermission that_ =
      new ServicePermission(args[1], "accept,initiate");
      System.out.println("-----\n");
      System.out.println("this.implies(that) = " + this_.implies(that_));
      System.out.println("-----\n");
      System.out.println("this = "+this_);
      System.out.println("-----\n");
      System.out.println("that = "+that_);
      System.out.println("-----\n");

      KrbServicePermissionCollection nps =
      new KrbServicePermissionCollection();
      nps.add(this_);
      nps.add(new ServicePermission("nfs/example.com@EXAMPLE.COM",
      "accept"));
      nps.add(new ServicePermission("host/example.com@EXAMPLE.COM",
      "initiate"));
      System.out.println("nps.implies(that) = " + nps.implies(that_));
      System.out.println("-----\n");

      Enumeration e = nps.elements();

      while (e.hasMoreElements()) {
      ServicePermission x =
      (ServicePermission) e.nextElement();
      System.out.println("nps.e = " + x);
      }

      }
    */

}


final class KrbServicePermissionCollection extends PermissionCollection
    implements java.io.Serializable {

    // Not serialized; see serialization section at end of class
    private transient List<Permission> perms;

    public KrbServicePermissionCollection() {
        perms = new ArrayList<Permission>();
    }

    /**
     * Check and see if this collection of permissions implies the permissions
     * expressed in "permission".
     *
     * @param permission the Permission object to compare
     *
     * @return true if "permission" is a proper subset of a permission in
     * the collection, false if not.
     */
    public boolean implies(Permission permission) {
        if (! (permission instanceof ServicePermission))
                return false;

        ServicePermission np = (ServicePermission) permission;
        int desired = np.getMask();

        if (desired == 0) {
            for (Permission p: perms) {
                ServicePermission sp = (ServicePermission)p;
                if (sp.impliesIgnoreMask(np)) {
                    return true;
                }
            }
            return false;
        }

        int effective = 0;
        int needed = desired;

        synchronized (this) {
            int len = perms.size();

            // need to deal with the case where the needed permission has
            // more than one action and the collection has individual permissions
            // that sum up to the needed.

            for (int i = 0; i < len; i++) {
                ServicePermission x = (ServicePermission) perms.get(i);

                //System.out.println("  trying "+x);
                if (((needed & x.getMask()) != 0) && x.impliesIgnoreMask(np)) {
                    effective |=  x.getMask();
                    if ((effective & desired) == desired)
                        return true;
                    needed = (desired ^ effective);
                }
            }
        }
        return false;
    }

    /**
     * Adds a permission to the ServicePermissions. The key for
     * the hash is the name.
     *
     * @param permission the Permission object to add.
     *
     * @exception IllegalArgumentException - if the permission is not a
     *                                       ServicePermission
     *
     * @exception SecurityException - if this PermissionCollection object
     *                                has been marked readonly
     */
    public void add(Permission permission) {
        if (! (permission instanceof ServicePermission))
            throw new IllegalArgumentException("invalid permission: "+
                                               permission);
        if (isReadOnly())
            throw new SecurityException("attempt to add a Permission to a readonly PermissionCollection");

        synchronized (this) {
            perms.add(0, permission);
        }
    }

    /**
     * Returns an enumeration of all the ServicePermission objects
     * in the container.
     *
     * @return an enumeration of all the ServicePermission objects.
     */

    public Enumeration<Permission> elements() {
        // Convert Iterator into Enumeration
        synchronized (this) {
            return Collections.enumeration(perms);
        }
    }

    private static final long serialVersionUID = -4118834211490102011L;

    // Need to maintain serialization interoperability with earlier releases,
    // which had the serializable field:
    // private Vector permissions;

    /**
     * @serialField permissions java.util.Vector
     *     A list of ServicePermission objects.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("permissions", Vector.class),
    };

    /**
     * @serialData "permissions" field (a Vector containing the ServicePermissions).
     */
    /*
     * Writes the contents of the perms field out as a Vector for
     * serialization compatibility with earlier releases.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        // Don't call out.defaultWriteObject()

        // Write out Vector
        Vector<Permission> permissions = new Vector<>(perms.size());

        synchronized (this) {
            permissions.addAll(perms);
        }

        ObjectOutputStream.PutField pfields = out.putFields();
        pfields.put("permissions", permissions);
        out.writeFields();
    }

    /*
     * Reads in a Vector of ServicePermissions and saves them in the perms field.
     */
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        // Don't call defaultReadObject()

        // Read in serialized fields
        ObjectInputStream.GetField gfields = in.readFields();

        // Get the one we want
        Vector<Permission> permissions =
                (Vector<Permission>)gfields.get("permissions", null);
        perms = new ArrayList<Permission>(permissions.size());
        perms.addAll(permissions);
    }
}
