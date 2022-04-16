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

/*
 * ===========================================================================
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 * ===========================================================================
 *
 */
package sun.security.krb5.internal.ccache;

import sun.security.krb5.*;
import sun.security.krb5.internal.*;
import java.util.StringTokenizer;
import java.util.Vector;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.*;

/**
 * CredentialsCache stores credentials(tickets, session keys, etc) in a
 * semi-permanent store
 * for later use by different program.
 *
 * CredentialsCache将凭据(票据、会话密钥等)存储在半永久存储中，供不同的程序使用。
 *
 * @author Yanni Zhang
 * @author Ram Marti
 */

public class FileCredentialsCache extends CredentialsCache
    implements FileCCacheConstants {
    public int version;
    public Tag tag; // optional
    public PrincipalName primaryPrincipal;
    private Vector<Credentials> credentialsList;
    private static String dir;
    private static boolean DEBUG = Krb5.DEBUG;

    /**
     * var2 = {FileCredentialsCache@2071}
     *  version = 1283
     *  tag = null
     *  primaryPrincipal = {PrincipalName@2070} "mr/zdh2@ZDH.COM"
     *   nameType = 1
     *   nameStrings = {String[2]@2099}
     *    0 = "mr"
     *    1 = "zdh2"
     *   nameRealm = {Realm@2100} "ZDH.COM"
     *    realm = "ZDH.COM"
     *   realmDeduced = false
     *   salt = null
     *  credentialsList = {Vector@2080}  size = 1
     *   0 = {Credentials@2083}
     *    cname = {PrincipalName@2084} "mr/zdh2@ZDH.COM"
     *    sname = {PrincipalName@2085} "krbtgt/ZDH.COM@ZDH.COM"
     *    key = {EncryptionKey@2086} "EncryptionKey: keyType=17 kvno=1283 keyValue (hex dump)=\n0000: 79 7B 0A 1E EE B9 97 C5   88 8A 4A 00 78 D0 10 15  y.........J.x...\n\n"
     *     keyType = 17
     *     keyValue = {byte[16]@2113}
     *     kvno = {Integer@2114} 1283
     *    authtime = {KerberosTime@2087} "20220415232327Z"
     *    starttime = null
     *    endtime = {KerberosTime@2088} "20220416232329Z"
     *    renewTill = {KerberosTime@2089} "20220416085929Z"
     *    caddr = null
     *    authorizationData = null
     *    isEncInSKey = false
     *    flags = {TicketFlags@2090} "FORWARDABLE;PROXIABLE;RENEWABLE;INITIAL;PRE-AUTHENT"
     *    ticket = {Ticket@2091}
     *     tkt_vno = 5
     *     sname = {PrincipalName@2104} "krbtgt/ZDH.COM@ZDH.COM"
     *      nameType = 1
     *      nameStrings = {String[2]@2107}
     *       0 = "krbtgt"
     *       1 = "ZDH.COM"
     *      nameRealm = {Realm@2108} "ZDH.COM"
     *       realm = "ZDH.COM"
     *        value = {char[7]@2112}
     *        hash = 0
     *      realmDeduced = false
     *      salt = null
     *     encPart = {EncryptedData@2105}
     *    secondTicket = null
     *    DEBUG = true
     * @param principal
     * @param cache
     * @return
     */
    public static synchronized FileCredentialsCache acquireInstance(
                PrincipalName principal, String cache) {
        try {
            FileCredentialsCache fcc = new FileCredentialsCache();
            if (cache == null) {
                // linux下获取默认的 cat /tmp/krb5cc_0
                cacheName = FileCredentialsCache.getDefaultCacheName();
            } else {
                cacheName = FileCredentialsCache.checkValidation(cache);
            }
            if ((cacheName == null) || !(new File(cacheName)).exists()) {
                // invalid cache name or the file doesn't exist
                return null;
            }
            if (principal != null) {
                fcc.primaryPrincipal = principal;
            }
            fcc.load(cacheName);
            return fcc;
        } catch (IOException e) {
            // we don't handle it now, instead we return a null at the end.
            if (DEBUG) {
                e.printStackTrace();
            }
        } catch (KrbException e) {
            // we don't handle it now, instead we return a null at the end.
            if (DEBUG) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static FileCredentialsCache acquireInstance() {
        return acquireInstance(null, null);
    }

    static synchronized FileCredentialsCache New(PrincipalName principal,
                                                String name) {
        try {
            FileCredentialsCache fcc = new FileCredentialsCache();
            cacheName = FileCredentialsCache.checkValidation(name);
            if (cacheName == null) {
                // invalid cache name or the file doesn't exist
                return null;
            }
            fcc.init(principal, cacheName);
            return fcc;
        }
        catch (IOException e) {
        }
        catch (KrbException e) {
        }
        return null;
    }

    static synchronized FileCredentialsCache New(PrincipalName principal) {
        try {
            FileCredentialsCache fcc = new FileCredentialsCache();
            cacheName = FileCredentialsCache.getDefaultCacheName();
            fcc.init(principal, cacheName);
            return fcc;
        }
        catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        } catch (KrbException e) {
            if (DEBUG) {
                e.printStackTrace();
            }

        }
        return null;
    }

    private FileCredentialsCache() {
    }

    boolean exists(String cache) {
        File file = new File(cache);
        if (file.exists()) {
            return true;
        } else return false;
    }

    synchronized void init(PrincipalName principal, String name)
        throws IOException, KrbException {
        primaryPrincipal = principal;
        try (FileOutputStream fos = new FileOutputStream(name);
             CCacheOutputStream cos = new CCacheOutputStream(fos)) {
            version = KRB5_FCC_FVNO_3;
            cos.writeHeader(primaryPrincipal, version);
        }
        load(name);
    }

    synchronized void load(String name) throws IOException, KrbException {
        PrincipalName p;
        try (FileInputStream fis = new FileInputStream(name);
             CCacheInputStream cis = new CCacheInputStream(fis)) {
            version = cis.readVersion();
            if (version == KRB5_FCC_FVNO_4) {
                tag = cis.readTag();
            } else {
                tag = null;
                if (version == KRB5_FCC_FVNO_1 || version == KRB5_FCC_FVNO_2) {
                    cis.setNativeByteOrder();
                }
            }
            p = cis.readPrincipal(version);

            if (primaryPrincipal != null) {
                if (!(primaryPrincipal.match(p))) {
                    throw new IOException("Primary principals don't match.");
                }
            } else
                primaryPrincipal = p;
            credentialsList = new Vector<Credentials>();
            while (cis.available() > 0) {
                Credentials cred = cis.readCred(version);
                if (cred != null) {
                    credentialsList.addElement(cred);
                }
            }
        }
    }


    /**
     * Updates the credentials list. If the specified credentials for the
     * service is new, add it to the list. If there is an entry in the list,
     * replace the old credentials with the new one.
     * @param c the credentials.
     */

    public synchronized void update(Credentials c) {
        if (credentialsList != null) {
            if (credentialsList.isEmpty()) {
                credentialsList.addElement(c);
            } else {
                Credentials tmp = null;
                boolean matched = false;

                for (int i = 0; i < credentialsList.size(); i++) {
                    tmp = credentialsList.elementAt(i);
                    if (match(c.sname.getNameStrings(),
                              tmp.sname.getNameStrings()) &&
                        ((c.sname.getRealmString()).equalsIgnoreCase(
                                     tmp.sname.getRealmString()))) {
                        matched = true;
                        if (c.endtime.getTime() >= tmp.endtime.getTime()) {
                            if (DEBUG) {
                                System.out.println(" >>> FileCredentialsCache "
                                         +  "Ticket matched, overwrite "
                                         +  "the old one.");
                            }
                            credentialsList.removeElementAt(i);
                            credentialsList.addElement(c);
                        }
                    }
                }
                if (matched == false) {
                    if (DEBUG) {
                        System.out.println(" >>> FileCredentialsCache Ticket "
                                        +   "not exactly matched, "
                                        +   "add new one into cache.");
                    }

                    credentialsList.addElement(c);
                }
            }
        }
    }

    public synchronized PrincipalName getPrimaryPrincipal() {
        return primaryPrincipal;
    }


    /**
     * Saves the credentials cache file to the disk.
     */
    public synchronized void save() throws IOException, Asn1Exception {
        try (FileOutputStream fos = new FileOutputStream(cacheName);
             CCacheOutputStream cos = new CCacheOutputStream(fos)) {
            cos.writeHeader(primaryPrincipal, version);
            Credentials[] tmp = null;
            if ((tmp = getCredsList()) != null) {
                for (int i = 0; i < tmp.length; i++) {
                    cos.addCreds(tmp[i]);
                }
            }
        }
    }

    boolean match(String[] s1, String[] s2) {
        if (s1.length != s2.length) {
            return false;
        } else {
            for (int i = 0; i < s1.length; i++) {
                if (!(s1[i].equalsIgnoreCase(s2[i]))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the list of credentials entries in the cache file.
     */
    public synchronized Credentials[] getCredsList() {
        if ((credentialsList == null) || (credentialsList.isEmpty())) {
            return null;
        } else {
            Credentials[] tmp = new Credentials[credentialsList.size()];
            for (int i = 0; i < credentialsList.size(); i++) {
                tmp[i] = credentialsList.elementAt(i);
            }
            return tmp;
        }

    }

    public Credentials getCreds(LoginOptions options, PrincipalName sname) {
        if (options == null) {
            return getCreds(sname);
        } else {
            Credentials[] list = getCredsList();
            if (list == null) {
                return null;
            } else {
                for (int i = 0; i < list.length; i++) {
                    if (sname.match(list[i].sname)) {
                        if (list[i].flags.match(options)) {
                            return list[i];
                        }
                    }
                }
            }
            return null;
        }
    }


    /**
     * Gets a credentials for a specified service.
     * @param sname service principal name.
     */
    public Credentials getCreds(PrincipalName sname) {
        Credentials[] list = getCredsList();
        if (list == null) {
            return null;
        } else {
            for (int i = 0; i < list.length; i++) {
                if (sname.match(list[i].sname)) {
                    return list[i];
                }
            }
        }
        return null;
    }

    public Credentials getDefaultCreds() {
        Credentials[] list = getCredsList();
        if (list == null) {
            return null;
        } else {
            for (int i = list.length-1; i >= 0; i--) {
                if (list[i].sname.toString().startsWith("krbtgt")) {
                    String[] nameStrings = list[i].sname.getNameStrings();
                    // find the TGT for the current realm krbtgt/realm@realm
                    if (nameStrings[1].equals(list[i].sname.getRealm().toString())) {
                       return list[i];
                    }
                }
            }
        }
        return null;
    }

    /*
     * Returns path name of the credentials cache file.
     * The path name is searched in the following order:
     *
     * 1. KRB5CCNAME (bare file name without FILE:)
     * 2. /tmp/krb5cc_<uid> on unix systems
     * 3. <user.home>/krb5cc_<user.name>
     * 4. <user.home>/krb5cc (if can't get <user.name>)
     *
     * 返回凭据缓存文件的路径名。搜索路径名的顺序如下:
     *
     * 1. KRB5CCNAME (bare file name without FILE:)
     * 2. /tmp/krb5cc_<uid> on unix systems
     * 3. <user.home>/krb5cc_<user.name>
     * 4. <user.home>/krb5cc (if can't get <user.name>)
     */

    public static String getDefaultCacheName() {

        String stdCacheNameComponent = "krb5cc";
        String name;

        // The env var can start with TYPE:, we only support FILE: here.
        // https://docs.oracle.com/cd/E19082-01/819-2252/6n4i8rtr3/index.html
        name = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<String>() {
            @Override
            public String run() {
                // 测试的时候 这个一直为空 打包放到Linux中测试kerberos认证的，发现这个一直为空
                String cache = System.getenv("KRB5CCNAME");
                if (cache != null &&
                        (cache.length() >= 5) &&
                        cache.regionMatches(true, 0, "FILE:", 0, 5)) {
                    cache = cache.substring(5);
                }
                return cache;
            }
        });
        if (name != null) {
            if (DEBUG) {
                //  没有打印这个日志
                System.out.println(">>>KinitOptions cache name is " + name);
            }
            return name;
        }

        // get cache name from system.property
        String osname =
            java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("os.name"));

        /*
         * For Unix platforms we use the default cache name to be
         * /tmp/krbcc_uid ; for all other platforms  we use
         * {user_home}/krb5_cc{user_name}
         * Please note that for Windows 2K we will use LSA to get
         * the TGT from the the default cache even before we come here;
         * however when we create cache we will create a cache under
         * {user_home}/krb5_cc{user_name} for non-Unix platforms including
         * Windows 2K.
         *
         * 对于Unix平台，我们使用默认的缓存名/tmp/krbcc_uid;请注意，对于Windows 2K，
         * 我们将使用LSA从默认缓存中获取TGT，甚至在我们来这里之前;然而，当我们创建缓存时，
         * 我们将在{user_home}/krb5_cc{user_name}下为非unix平台(包括Windows 2K)创建一个缓存。
         */

        if (osname != null) {
            String cmd = null;
            String uidStr = null;
            long uid = 0;

            if (!osname.startsWith("Windows")) {
                try {
                    /**
                     * 在linux系统下 打印如下 >>>KinitOptions cache name is /tmp/krb5cc_0
                     * [root@zdh2 ~]# cat /tmp/krb5cc_0
                     * ZDH.COMmrzdh2ZDH.COMmrzdh2ZDH.COMkrbtgtZDH.COMy{
                     * Җ�9��H� �\룷��l���F�,��:�ȑ�n0{2��#��Z�5�£�10krbtgtZDH.COM���0�������������s
                     * �6�1    �␌�����␉��R:�│��J��_┬\�≥���U�;�U\��o�h�'��3��)�����0�����jn�5�#D��Y��Mg�        �_dQ��1┴Q�#���▒9��ԩ��U[⎼⎺⎺├@≥␍␤2 ·]#
                     * [⎼⎺⎺├@≥␍␤2 ·]#
                     */
                    Class<?> c = Class.forName
                        ("com.sun.security.auth.module.UnixSystem");
                    Constructor<?> constructor = c.getConstructor();
                    Object obj = constructor.newInstance();
                    Method method = c.getMethod("getUid");
                    uid =  ((Long)method.invoke(obj)).longValue();
                    name = File.separator + "tmp" +
                        File.separator + stdCacheNameComponent + "_" + uid;
                    if (DEBUG) {
                        System.out.println(">>>KinitOptions cache name is " +
                                           name);
                    }
                    return name;
                } catch (Exception e) {
                    if (DEBUG) {
                        System.out.println("Exception in obtaining uid " +
                                            "for Unix platforms " +
                                            "Using user's home directory");


                        e.printStackTrace();
                    }
                }
            }
        }

        // we did not get the uid;


        String user_name =
            java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("user.name"));

        String user_home =
            java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("user.home"));

        if (user_home == null) {
            user_home =
                java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("user.dir"));
        }

        if (user_name != null) {
            name = user_home + File.separator  +
                stdCacheNameComponent + "_" + user_name;
        } else {
            name = user_home + File.separator + stdCacheNameComponent;
        }

        if (DEBUG) {
            System.out.println(">>>KinitOptions cache name is " + name);
        }

        return name;
    }

    public static String checkValidation(String name) {
        String fullname = null;
        if (name == null) {
            return null;
        }
        try {
            // get full path name
            fullname = (new File(name)).getCanonicalPath();
            File fCheck = new File(fullname);
            if (!(fCheck.exists())) {
                // get absolute directory
                File temp = new File(fCheck.getParent());
                // test if the directory exists
                if (!(temp.isDirectory()))
                    fullname = null;
                temp = null;
            }
            fCheck = null;

        } catch (IOException e) {
            fullname = null; // invalid name
        }
        return fullname;
    }


    private static String exec(String c) {
        StringTokenizer st = new StringTokenizer(c);
        Vector<String> v = new Vector<>();
        while (st.hasMoreTokens()) {
            v.addElement(st.nextToken());
        }
        final String[] command = new String[v.size()];
        v.copyInto(command);
        try {

            Process p =
                java.security.AccessController.doPrivileged
                (new java.security.PrivilegedAction<Process> () {
                        public Process run() {
                            try {
                                return (Runtime.getRuntime().exec(command));
                            } catch (java.io.IOException e) {
                                if (DEBUG) {
                                    e.printStackTrace();
                                }
                                return null;
                            }
                        }
                    });
            if (p == null) {
                // exception occurred during executing the command
                return null;
            }

            BufferedReader commandResult =
                new BufferedReader
                    (new InputStreamReader(p.getInputStream(), "8859_1"));
            String s1 = null;
            if ((command.length == 1) &&
                (command[0].equals("/usr/bin/env"))) {
                while ((s1 = commandResult.readLine()) != null) {
                    if (s1.length() >= 11) {
                        if ((s1.substring(0, 11)).equalsIgnoreCase
                            ("KRB5CCNAME=")) {
                            s1 = s1.substring(11);
                            break;
                        }
                    }
                }
            } else     s1 = commandResult.readLine();
            commandResult.close();
            return s1;
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
