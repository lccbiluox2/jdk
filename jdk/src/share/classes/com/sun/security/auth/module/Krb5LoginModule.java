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


package com.sun.security.auth.module;

import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.*;

import javax.security.auth.*;
import javax.security.auth.kerberos.*;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.*;

import sun.security.krb5.*;
import sun.security.jgss.krb5.Krb5Util;
import sun.security.krb5.Credentials;
import sun.misc.HexDumpEncoder;

/**
 * <p> This <code>LoginModule</code> authenticates users using
 * Kerberos protocols.
 *
 * <p> The configuration entry for <code>Krb5LoginModule</code> has
 * several options that control the authentication process and
 * additions to the <code>Subject</code>'s private credential
 * set. Irrespective of these options, the <code>Subject</code>'s
 * principal set and private credentials set are updated only when
 * <code>commit</code> is called.
 * When <code>commit</code> is called, the <code>KerberosPrincipal</code>
 * is added to the <code>Subject</code>'s principal set (unless the
 * <code>principal</code> is specified as "*"). If <code>isInitiator</code>
 * is true, the <code>KerberosTicket</code> is
 * added to the <code>Subject</code>'s private credentials.
 *
 * LoginModule使用Kerberos协议对用户进行身份验证。
 *
 * Krb5LoginModule的配置条目有几个选项，用于控制身份验证过程和向Subject的私有凭据集添加内容。
 * 不管这些选项如何，Subject的主体集和私有凭据集只在调用提交时更新。当调用commit时，
 * KerberosPrincipal被添加到Subject的主体集(除非主体被指定为“*”)。如果isInitiator为true，
 * 则KerberosTicket被添加到Subject的私有凭证中。
 *
 * <p> If the configuration entry for <code>KerberosLoginModule</code>
 * has the option <code>storeKey</code> set to true, then
 * <code>KerberosKey</code> or <code>KeyTab</code> will also be added to the
 * subject's private credentials. <code>KerberosKey</code>, the principal's
 * key(s) will be derived from user's password, and <code>KeyTab</code> is
 * the keytab used when <code>useKeyTab</code> is set to true. The
 * <code>KeyTab</code> object is restricted to be used by the specified
 * principal unless the principal value is "*".
 *
 * <p> This <code>LoginModule</code> recognizes the <code>doNotPrompt</code>
 * option. If set to true the user will not be prompted for the password.
 *
 * <p> The user can  specify the location of the ticket cache by using
 * the option <code>ticketCache</code> in the configuration entry.
 *
 * <p>The user can specify the keytab location by using
 * the option <code>keyTab</code>
 * in the configuration entry.
 *
 * 如果KerberosLoginModule的配置条目将选项storeKey设置为true，那么KerberosKey或KeyTab
 * 也将被添加到主体的私有凭证中。KerberosKey，主体的密钥将从用户的密码派生而来，KeyTab是当
 * useKeyTab设置为true时使用的KeyTab。KeyTab对象被限制由指定的主体使用，除非主体的值是“*”。
 *
 * 这个LoginModule识别doNotPrompt选项。如果设置为true，则不会提示用户输入密码。
 *
 * 用户可以使用配置条目中的选项ticketCache指定票据缓存的位置。
 *
 * 用户可以使用配置条目中的选项keytab指定keytab位置。
 *
 * <p> The principal name can be specified in the configuration entry
 * by using the option <code>principal</code>. The principal name
 * can either be a simple user name, a service name such as
 * <code>host/mission.eng.sun.com</code>, or "*". The principal can also
 * be set using the system property <code>sun.security.krb5.principal</code>.
 * This property is checked during login. If this property is not set, then
 * the principal name from the configuration is used. In the
 * case where the principal property is not set and the principal
 * entry also does not exist, the user is prompted for the name.
 * When this property of entry is set, and <code>useTicketCache</code>
 * is set to true, only TGT belonging to this principal is used.
 *
 * 通过使用选项principal，可以在配置条目中指定主体名称。主体名称可以是简单的用户名、服务名
 * ，如host/mission.eng.sun.com或“*”。还可以使用系统属性sun.security.krb5.principal
 * 来设置主体。在登录期间检查此属性。如果没有设置此属性，则使用配置中的主体名称。在没有设置主体
 * 属性且主体条目也不存在的情况下，会提示用户输入名称。当设置了条目的这个属性，并且useTicketCache
 * 设置为true时，只使用属于这个主体的TGT。
 *
 * <p> The following is a list of configuration options supported
 * for <code>Krb5LoginModule</code>:
 * <blockquote><dl>
 * <dt><b><code>refreshKrb5Config</code></b>:</dt>
 * <dd> Set this to true, if you want the configuration
 * to be refreshed before the <code>login</code> method is called.</dd>
 * <dt><b><code>useTicketCache</code></b>:</dt>
 * <dd>Set this to true, if you want the
 * TGT to be obtained
 * from the ticket cache. Set this option
 * to false if you do not want this module to use the ticket cache.
 * (Default is False).
 * This module will
 * search for the ticket
 * cache in the following locations:
 * On Solaris and Linux
 * it will look for the ticket cache in /tmp/krb5cc_<code>uid</code>
 * where the uid is numeric user
 * identifier. If the ticket cache is
 * not available in the above location, or if we are on a
 * Windows platform, it will look for the cache as
 * {user.home}{file.separator}krb5cc_{user.name}.
 * You can override the ticket cache location by using
 * <code>ticketCache</code>.
 * For Windows, if a ticket cannot be retrieved from the file ticket cache,
 * it will use Local Security Authority (LSA) API to get the TGT.
 * <dt><b><code>ticketCache</code></b>:</dt>
 * <dd>Set this to the name of the ticket
 * cache that  contains user's TGT.
 * If this is set,  <code>useTicketCache</code>
 * must also be set to true; Otherwise a configuration error will
 * be returned.</dd>
 * <dt><b><code>renewTGT</code></b>:</dt>
 * <dd>Set this to true, if you want to renew
 * the TGT. If this is set, <code>useTicketCache</code> must also be
 * set to true; otherwise a configuration error will be returned.</dd>
 * <dt><b><code>doNotPrompt</code></b>:</dt>
 * <dd>Set this to true if you do not want to be
 * prompted for the password
 * if credentials can not be obtained from the cache, the keytab,
 * or through shared state.(Default is false)
 * If set to true, credential must be obtained through cache, keytab,
 * or shared state. Otherwise, authentication will fail.</dd>
 * <dt><b><code>useKeyTab</code></b>:</dt>
 * <dd>Set this to true if you
 * want the module to get the principal's key from the
 * the keytab.(default value is False)
 * If <code>keytab</code>
 * is not set then
 * the module will locate the keytab from the
 * Kerberos configuration file.
 * If it is not specified in the Kerberos configuration file
 * then it will look for the file
 * <code>{user.home}{file.separator}</code>krb5.keytab.</dd>
 * <dt><b><code>keyTab</code></b>:</dt>
 * <dd>Set this to the file name of the
 * keytab to get principal's secret key.</dd>
 * <dt><b><code>storeKey</code></b>:</dt>
 * <dd>Set this to true to if you want the keytab or the
 * principal's key to be stored in the Subject's private credentials.
 * For <code>isInitiator</code> being false, if <code>principal</code>
 * is "*", the {@link KeyTab} stored can be used by anyone, otherwise,
 * it's restricted to be used by the specified principal only.</dd>
 * <dt><b><code>principal</code></b>:</dt>
 * <dd>The name of the principal that should
 * be used. The principal can be a simple username such as
 * "<code>testuser</code>" or a service name such as
 * "<code>host/testhost.eng.sun.com</code>". You can use the
 * <code>principal</code>  option to set the principal when there are
 * credentials for multiple principals in the
 * <code>keyTab</code> or when you want a specific ticket cache only.
 * The principal can also be set using the system property
 * <code>sun.security.krb5.principal</code>. In addition, if this
 * system property is defined, then it will be used. If this property
 * is not set, then the principal name from the configuration will be
 * used.
 * The principal name can be set to "*" when <code>isInitiator</code> is false.
 * In this case, the acceptor is not bound to a single principal. It can
 * act as any principal an initiator requests if keys for that principal
 * can be found. When <code>isInitiator</code> is true, the principal name
 * cannot be set to "*".
 * </dd>
 * <dt><b><code>isInitiator</code></b>:</dt>
 * <dd>Set this to true, if initiator. Set this to false, if acceptor only.
 * (Default is true).
 * Note: Do not set this value to false for initiators.</dd>
 * </dl></blockquote>
 *
 * 以下是Krb5LoginModule支持的配置选项列表:
 *
 * refreshKrb5Config:
 *
 *    如果您希望在调用登录方法之前刷新配置，则将此设置为true。
 *
 * useTicketCache:
 *
 *    如果希望从票证缓存获得TGT，则将此设置为true。如果您不希望此模块使用票据缓存，则将此选项
 *    设置为false。(缺省是false)。这个模块将在以下位置搜索票据缓存:在Solaris和Linux上，
 *    它将在/tmp/krb5cc_uid中查找票据缓存，其中uid是数字用户标识符。如果票据缓存在上面的
 *    位置不可用，或者如果我们在Windows平台上，它将查找{user.home}{file.separator}krb5cc_{user.name}
 *    的缓存。可以使用ticketCache覆盖票据缓存位置。对于Windows，如果票据不能从文件票据缓存中检索，
 *    它将使用本地安全机构(LSA) API来获取TGT。
 *
 * ticketCache:
 *
 *    将其设置为包含用户TGT的票据缓存的名称。如果设置了，useTicketCache也必须设置为true;
 *    否则将返回一个配置错误。
 *
 * renewTGT:
 *
 *   如果您想更新TGT，请将此设置为true。如果设置了，useTicketCache也必须设置为true;
 *   否则将返回一个配置错误。
 *
 * doNotPrompt:
 *
 *   如果您不希望在无法从缓存、keytab或通过共享状态获得凭据时提示输入密码，则将此设置为true。
 *   (默认为false)如果设置为true，则凭据必须通过缓存、keytab或共享状态获取。否则，认证将失败。
 *
 * useKeyTab:
 *
 *   如果你想让模块从keytab中获取主体的密钥，将此设置为true。(默认值为False)如果没有设置keytab，
 *   则模块将从Kerberos配置文件中找到keytab。如果在Kerberos配置文件中没有指定，那么它将查找文件
 *   {user.home}{file.separator}krb5.keytab。
 *
 * keyTab:
 *
 *   将此设置为keytab的文件名，以获得主体的密钥。
 *
 * storeKey:
 *
 *   如果您希望keytab或主体的密钥存储在Subject的私有凭据中，则将此设置为true。如果isInitiator为false，
 *   如果主体为“*”，则存储的{@link KeyTab}可以被任何人使用，否则，它被限制仅被指定的主体使用。
 *
 * principal:
 *
 *    应该使用的主体的名称。主体可以是简单的用户名，如“testuser”或服务名，如"host/testhost.eng.sun.com"。
 *    当keyTab中存在多个主体的凭据时，或者仅需要特定的票据缓存时，可以使用主体选项设置主体。还可以使用系统
 *    属性sun.security.krb5.principal来设置主体。此外，如果定义了这个系统属性，那么就会使用它。如果没有
 *    设置此属性，则将使用配置中的主体名称。当isInitiator为false时，主体名称可以设置为“*”。在这种情况下，
 *    acceptor不绑定到单个主体。如果可以找到该主体的键，则它可以充当发起者请求的任何主体。当isInitiator为
 *    true时，主体名称不能设置为“*”。
 *
 * isInitiator:
 *
 *   如果isInitiator设置为true。如果仅为acceptor，则设置为false。(默认是正确的)。注意:对于isInitiator，
 *   此值不能设置为false。
 *
 * <p> This <code>LoginModule</code> also recognizes the following additional
 * <code>Configuration</code>
 * options that enable you to share username and passwords across different
 * authentication modules:
 * <blockquote><dl>
 *
 *    <dt><b><code>useFirstPass</code></b>:</dt>
 *                   <dd>if, true, this LoginModule retrieves the
 *                   username and password from the module's shared state,
 *                   using "javax.security.auth.login.name" and
 *                   "javax.security.auth.login.password" as the respective
 *                   keys. The retrieved values are used for authentication.
 *                   If authentication fails, no attempt for a retry
 *                   is made, and the failure is reported back to the
 *                   calling application.</dd>
 *
 *    <dt><b><code>tryFirstPass</code></b>:</dt>
 *                   <dd>if, true, this LoginModule retrieves the
 *                   the username and password from the module's shared
 *                   state using "javax.security.auth.login.name" and
 *                   "javax.security.auth.login.password" as the respective
 *                   keys.  The retrieved values are used for
 *                   authentication.
 *                   If authentication fails, the module uses the
 *                   CallbackHandler to retrieve a new username
 *                   and password, and another attempt to authenticate
 *                   is made. If the authentication fails,
 *                   the failure is reported back to the calling application</dd>
 *
 *    <dt><b><code>storePass</code></b>:</dt>
 *                   <dd>if, true, this LoginModule stores the username and
 *                   password obtained from the CallbackHandler in the
 *                   modules shared state, using
 *                   "javax.security.auth.login.name" and
 *                   "javax.security.auth.login.password" as the respective
 *                   keys.  This is not performed if existing values already
 *                   exist for the username and password in the shared
 *                   state, or if authentication fails.</dd>
 *
 *    <dt><b><code>clearPass</code></b>:</dt>
 *                   <dd>if, true, this LoginModule clears the
 *                   username and password stored in the module's shared
 *                   state  after both phases of authentication
 *                   (login and commit) have completed.</dd>
 * </dl></blockquote>
 * <p>If the principal system property or key is already provided, the value of
 * "javax.security.auth.login.name" in the shared state is ignored.
 * <p>When multiple mechanisms to retrieve a ticket or key is provided, the
 * preference order is:
 * <ol>
 * <li>ticket cache
 * <li>keytab
 * <li>shared state
 * <li>user prompt
 * </ol>
 * <p>Note that if any step fails, it will fallback to the next step.
 * There's only one exception, if the shared state step fails and
 * <code>useFirstPass</code>=true, no user prompt is made.
 * <p>Examples of some configuration values for Krb5LoginModule in
 * JAAS config file and the results are:
 * <ul>
 * <p> <code>doNotPrompt</code>=true;
 * </ul>
 * <p> This is an illegal combination since none of <code>useTicketCache</code>,
 * <code>useKeyTab</code>, <code>useFirstPass</code> and <code>tryFirstPass</code>
 * is set and the user can not be prompted for the password.
 *<ul>
 * <p> <code>ticketCache</code> = &lt;filename&gt;;
 *</ul>
 * <p> This is an illegal combination since <code>useTicketCache</code>
 * is not set to true and the ticketCache is set. A configuration error
 * will occur.
 * <ul>
 * <p> <code>renewTGT</code>=true;
 *</ul>
 * <p> This is an illegal combination since <code>useTicketCache</code> is
 * not set to true and renewTGT is set. A configuration error will occur.
 * <ul>
 * <p> <code>storeKey</code>=true
 * <code>useTicketCache</code> = true
 * <code>doNotPrompt</code>=true;;
 *</ul>
 * <p> This is an illegal combination since  <code>storeKey</code> is set to
 * true but the key can not be obtained either by prompting the user or from
 * the keytab, or from the shared state. A configuration error will occur.
 * <ul>
 * <p>  <code>keyTab</code> = &lt;filename&gt; <code>doNotPrompt</code>=true ;
 * </ul>
 * <p>This is an illegal combination since useKeyTab is not set to true and
 * the keyTab is set. A configuration error will occur.
 * <ul>
 * <p> <code>debug=true </code>
 *</ul>
 * <p> Prompt the user for the principal name and the password.
 * Use the authentication exchange to get TGT from the KDC and
 * populate the <code>Subject</code> with the principal and TGT.
 * Output debug messages.
 * <ul>
 * <p> <code>useTicketCache</code> = true <code>doNotPrompt</code>=true;
 *</ul>
 * <p>Check the default cache for TGT and populate the <code>Subject</code>
 * with the principal and TGT. If the TGT is not available,
 * do not prompt the user, instead fail the authentication.
 * <ul>
 * <p><code>principal</code>=&lt;name&gt;<code>useTicketCache</code> = true
 * <code>doNotPrompt</code>=true;
 *</ul>
 * <p> Get the TGT from the default cache for the principal and populate the
 * Subject's principal and private creds set. If ticket cache is
 * not available or does not contain the principal's TGT
 * authentication will fail.
 * <ul>
 * <p> <code>useTicketCache</code> = true
 * <code>ticketCache</code>=&lt;file name&gt;<code>useKeyTab</code> = true
 * <code> keyTab</code>=&lt;keytab filename&gt;
 * <code>principal</code> = &lt;principal name&gt;
 * <code>doNotPrompt</code>=true;
 *</ul>
 * <p>  Search the cache for the principal's TGT. If it is not available
 * use the key in the keytab to perform authentication exchange with the
 * KDC and acquire the TGT.
 * The Subject will be populated with the principal and the TGT.
 * If the key is not available or valid then authentication will fail.
 * <ul>
 * <p><code>useTicketCache</code> = true
 * <code>ticketCache</code>=&lt;file name&gt;
 *</ul>
 * <p> The TGT will be obtained from the cache specified.
 * The Kerberos principal name used will be the principal name in
 * the Ticket cache. If the TGT is not available in the
 * ticket cache the user will be prompted for the principal name
 * and the password. The TGT will be obtained using the authentication
 * exchange with the KDC.
 * The Subject will be populated with the TGT.
 *<ul>
 * <p> <code>useKeyTab</code> = true
 * <code>keyTab</code>=&lt;keytab filename&gt;
 * <code>principal</code>= &lt;principal name&gt;
 * <code>storeKey</code>=true;
 *</ul>
 * <p>  The key for the principal will be retrieved from the keytab.
 * If the key is not available in the keytab the user will be prompted
 * for the principal's password. The Subject will be populated
 * with the principal's key either from the keytab or derived from the
 * password entered.
 * <ul>
 * <p> <code>useKeyTab</code> = true
 * <code>keyTab</code>=&lt;keytabname&gt;
 * <code>storeKey</code>=true
 * <code>doNotPrompt</code>=false;
 *</ul>
 * <p>The user will be prompted for the service principal name.
 * If the principal's
 * longterm key is available in the keytab , it will be added to the
 * Subject's private credentials. An authentication exchange will be
 * attempted with the principal name and the key from the Keytab.
 * If successful the TGT will be added to the
 * Subject's private credentials set. Otherwise the authentication will
 * fail.
 * <ul>
 * <p> <code>isInitiator</code> = false <code>useKeyTab</code> = true
 * <code>keyTab</code>=&lt;keytabname&gt;
 * <code>storeKey</code>=true
 * <code>principal</code>=*;
 *</ul>
 * <p>The acceptor will be an unbound acceptor and it can act as any principal
 * as long that principal has keys in the keytab.
 *<ul>
 * <p>
 * <code>useTicketCache</code>=true
 * <code>ticketCache</code>=&lt;file name&gt;;
 * <code>useKeyTab</code> = true
 * <code>keyTab</code>=&lt;file name&gt; <code>storeKey</code>=true
 * <code>principal</code>= &lt;principal name&gt;
 *</ul>
 * <p>
 * The client's TGT will be retrieved from the ticket cache and added to the
 * <code>Subject</code>'s private credentials. If the TGT is not available
 * in the ticket cache, or the TGT's client name does not match the principal
 * name, Java will use a secret key to obtain the TGT using the authentication
 * exchange and added to the Subject's private credentials.
 * This secret key will be first retrieved from the keytab. If the key
 * is not available, the user will be prompted for the password. In either
 * case, the key derived from the password will be added to the
 * Subject's private credentials set.
 * <ul>
 * <p><code>isInitiator</code> = false
 *</ul>
 * <p>Configured to act as acceptor only, credentials are not acquired
 * via AS exchange. For acceptors only, set this value to false.
 * For initiators, do not set this value to false.
 * <ul>
 * <p><code>isInitiator</code> = true
 *</ul>
 * <p>Configured to act as initiator, credentials are acquired
 * via AS exchange. For initiators, set this value to true, or leave this
 * option unset, in which case default value (true) will be used.
 *
 *
 * 这个LoginModule还识别以下额外的配置选项，使您能够跨不同的认证模块共享用户名和密码:
 *
 * useFirstPass:
 *
 *    如果useFirstPass=true，这个LoginModule从模块的共享状态中检索用户名和密码，使用
 *    “javax.security.auth.login.name”和"javax.security.auth.login.password"。
 *    作为相应的密钥。检索到的值用于身份验证。如果身份验证失败，则不会尝试重试，并将失败报告
 *    给调用应用程序。
 *
 * tryFirstPass:
 *
 *    如果tryFirstPass=true，这个LoginModule使用“javax.security.auth.login.name”和
 *    "javax.security.auth.login.password" 从模块的共享状态中检索用户名和密码。作为相应的密钥。
 *    检索到的值用于身份验证。如果身份验证失败，则模块使用CallbackHandler检索新的用户名和密码，
 *    并再次尝试身份验证。如果身份验证失败，则向调用 application storePass的应用程序报告失败:
 *
 *    如果tryFirstPass=true，这个LoginModule存储在模块共享状态中从CallbackHandler获得的用户名
 *    和密码，使用“javax.security.auth.login.name”和"javax.security.auth.login.password"
 *    作为相应的密钥。如果共享状态下的用户名和密码已有值，或者身份验证失败，则不会执行此操作。
 *
 * clearPass:
 *
 *   如果 clearPass=true，这个LoginModule在两个身份验证阶段(登录和提交)完成后清空存储在模块
 *   共享状态中的用户名和密码。
 *
 *
 * 如果已经提供了主体系统属性或密钥，则共享状态下的“javax.security.auth.login.name”的值将被忽略。
 *
 * 当提供了多个获取票据或密钥的机制时，优先顺序是:
 *
 *  1. ticket cache  票缓存
 *  2. keytab        keytab
 *  3. shared state  共享状态
 *  4. user prompt   用户提示
 *
 * 请注意，如果任何步骤失败，它将退回到下一个步骤。只有一个例外，如果共享状态步骤失败并且useFirstPass=true，
 * 则不会发出用户提示。
 *
 *
 * JAAS配置文件中关于Krb5LoginModule的一些配置值的例子和结果如下:
 *
 * doNotPrompt = true;
 *
 *
 * 这是一个非法的组合，因为没有useTicketCache useKeyTab, useFirstPass和tryFirstPass
 *
 * ticketCache = <文件名>;
 *
 * 这是一个非法的组合，因为useTicketCache没有设置为true, ticketCache也被设置了。会出现配置错误。
 *
 * renewTGT = true;
 *
 * 这是一个非法的组合，因为useTicketCache没有设置为true, renewTGT被设置。会出现配置错误。
 *
 * storeKey=true useTicketCache =true doNotPrompt=true;;
 *
 * 这是一个非法的组合，因为storeKey被设置为true，但是密钥不能通过提示用户或从keytab或从共享状态获得。
 * 会出现配置错误。
 *
 * keyTab = doNotPrompt=true;
 *
 * 这是一个非法的组合，因为useKeyTab没有设置为true，而keyTab被设置。会出现配置错误。
 *
 * debug = true
 *
 * 提示用户输入主体名和密码。使用身份验证交换从KDC获得TGT，并使用主体和TGT填充Subject。输出调试信息。
 *
 * useTicketCache =true doNotPrompt=true;
 *
 * 检查TGT的默认缓存，并用主体和TGT填充Subject。如果TGT不可用，则不要提示用户，而是让身份验证失败。
 *
 * principal=<name> useTicketCache = true doNotPrompt=true;
 *

 * 从主体的默认缓存获取TGT，并填充Subject的主体和私有信用集。如果票据缓存不可用或不包含主体的
 * TGT身份验证将失败。
 *
 * useTicketCache = true ticketCache=<file name> useKeyTab = true
 * keyTab=<keytab filename> principal = <principal name> doNotPrompt=true;
 *
 *
 * 在缓存中搜索主体的TGT。如果它不可用，请使用keytab中的密钥与KDC执行身份验证交换并获取TGT。
 * Subject将被主体和TGT填充。如果密钥不可用或无效，则身份验证将失败。
 *
 * useTicketCache = true ticketCache=<文件名>

 *
 * TGT将从指定的缓存中获得。所使用的Kerberos主体名称将是Ticket缓存中的主体名称。如果TGT在票据缓存
 * 中不可用，则会提示用户输入主体名和密码。TGT将通过与KDC的身份验证交换获得。Subject将被TGT填充。
 *
 *
 *
 * useKeyTab =true keyTab=< keyTab filename> principal= <主体名称> storeKey=true;
 *
 *
 *
 * 主体的键将从keytab中检索。如果密钥在keytab中不可用，则会提示用户输入主体的密码。Subject将使用
 * 来自keytab或从输入的密码派生的主体的密钥来填充。
 *
 *
 *
 * useKeyTab =true keyTab= storeKey=true doNotPrompt=false;
 *
 *
 *
 * 系统会提示用户输入服务原则
 *
 * @author Ram Marti
 */

@jdk.Exported
public class Krb5LoginModule implements LoginModule {

    // initial state
    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, Object> sharedState;
    private Map<String, ?> options;

    // configurable option
    private boolean debug = false;
    private boolean storeKey = false;
    private boolean doNotPrompt = false;
    private boolean useTicketCache = false;
    private boolean useKeyTab = false;
    private String ticketCacheName = null;
    private String keyTabName = null;
    private String princName = null;

    private boolean useFirstPass = false;
    private boolean tryFirstPass = false;
    private boolean storePass = false;
    private boolean clearPass = false;
    private boolean refreshKrb5Config = false;
    private boolean renewTGT = false;

    // specify if initiator.
    // perform authentication exchange if initiator
    private boolean isInitiator = true;

    // the authentication status
    private boolean succeeded = false;
    private boolean commitSucceeded = false;
    private String username;

    // Encryption keys calculated from password. Assigned when storekey == true
    // and useKeyTab == false (or true but not found)
    private EncryptionKey[] encKeys = null;

    KeyTab ktab = null;

    private Credentials cred = null;

    private PrincipalName principal = null;
    private KerberosPrincipal kerbClientPrinc = null;
    private KerberosTicket kerbTicket = null;
    private KerberosKey[] kerbKeys = null;
    private StringBuffer krb5PrincName = null;
    private boolean unboundServer = false;
    private char[] password = null;

    private static final String NAME = "javax.security.auth.login.name";
    private static final String PWD = "javax.security.auth.login.password";
    private static final ResourceBundle rb = AccessController.doPrivileged(
            new PrivilegedAction<ResourceBundle>() {
                public ResourceBundle run() {
                    return ResourceBundle.getBundle(
                            "sun.security.util.AuthResources");
                }
            }
    );

    /**
     * Initialize this <code>LoginModule</code>.
     *
     * <p>
     * @param subject the <code>Subject</code> to be authenticated. <p>
     *
     * @param callbackHandler a <code>CallbackHandler</code> for
     *                  communication with the end user (prompting for
     *                  usernames and passwords, for example). <p>
     *
     * @param sharedState shared <code>LoginModule</code> state. <p>
     *
     * @param options options specified in the login
     *                  <code>Configuration</code> for this particular
     *                  <code>LoginModule</code>.
     */
    // Unchecked warning from (Map<String, Object>)sharedState is safe
    // since javax.security.auth.login.LoginContext passes a raw HashMap.
    // Unchecked warnings from options.get(String) are safe since we are
    // passing known keys.
    //
    // (Map)sharedState的未检查警告是安全的，因为javax.security.auth.login.LoginContext传递了
    // 一个原始HashMap。来自options.get(String)的未检查的警告是安全的，因为我们传递的是已知的键。
    @SuppressWarnings("unchecked")
    public void initialize(Subject subject,
                           CallbackHandler callbackHandler,
                           Map<String, ?> sharedState,
                           Map<String, ?> options) {

        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = (Map<String, Object>)sharedState;
        this.options = options;

        // initialize any configured options

        // 初始化一堆的参数
        debug = "true".equalsIgnoreCase((String)options.get("debug"));
        storeKey = "true".equalsIgnoreCase((String)options.get("storeKey"));
        doNotPrompt = "true".equalsIgnoreCase((String)options.get
                                              ("doNotPrompt"));
        useTicketCache = "true".equalsIgnoreCase((String)options.get
                                                 ("useTicketCache"));
        useKeyTab = "true".equalsIgnoreCase((String)options.get("useKeyTab"));
        ticketCacheName = (String)options.get("ticketCache");
        keyTabName = (String)options.get("keyTab");
        if (keyTabName != null) {
            keyTabName = sun.security.krb5.internal.ktab.KeyTab.normalize(
                         keyTabName);
        }
        princName = (String)options.get("principal");
        refreshKrb5Config =
            "true".equalsIgnoreCase((String)options.get("refreshKrb5Config"));
        renewTGT =
            "true".equalsIgnoreCase((String)options.get("renewTGT"));

        // check isInitiator value
        String isInitiatorValue = ((String)options.get("isInitiator"));
        if (isInitiatorValue == null) {
            // use default, if value not set
        } else {
            isInitiator = "true".equalsIgnoreCase(isInitiatorValue);
        }

        tryFirstPass =
            "true".equalsIgnoreCase
            ((String)options.get("tryFirstPass"));
        useFirstPass =
            "true".equalsIgnoreCase
            ((String)options.get("useFirstPass"));
        storePass =
            "true".equalsIgnoreCase((String)options.get("storePass"));
        clearPass =
            "true".equalsIgnoreCase((String)options.get("clearPass"));

        // todo: 如果是debug模式下回打印这些参数
        if (debug) {
            System.out.print("Debug is  " + debug
                             + " storeKey " + storeKey
                             + " useTicketCache " + useTicketCache
                             + " useKeyTab " + useKeyTab
                             + " doNotPrompt " + doNotPrompt
                             + " ticketCache is " + ticketCacheName
                             + " isInitiator " + isInitiator
                             + " KeyTab is " + keyTabName
                             + " refreshKrb5Config is " + refreshKrb5Config
                             + " principal is " + princName
                             + " tryFirstPass is " + tryFirstPass
                             + " useFirstPass is " + useFirstPass
                             + " storePass is " + storePass
                             + " clearPass is " + clearPass + "\n");
        }
    }


    /**
     * Authenticate the user
     *
     * 用户认证
     *
     * <p>
     *
     * @return true in all cases since this <code>LoginModule</code>
     *          should not be ignored.
     *
     * @exception FailedLoginException if the authentication fails. <p>
     *
     * @exception LoginException if this <code>LoginModule</code>
     *          is unable to perform the authentication.
     */
    public boolean login() throws LoginException {

        // 是否刷新 Krb5配置
        if (refreshKrb5Config) {
            try {
                if (debug) {
                    System.out.println("Refreshing Kerberos configuration");
                }
                // 刷新配置
                sun.security.krb5.Config.refresh();
            } catch (KrbException ke) {
                LoginException le = new LoginException(ke.getMessage());
                le.initCause(ke);
                throw le;
            }
        }
        // 获取 principal
        String principalProperty = System.getProperty
            ("sun.security.krb5.principal");
        if (principalProperty != null) {
            krb5PrincName = new StringBuffer(principalProperty);
        } else {
            if (princName != null) {
                krb5PrincName = new StringBuffer(princName);
            }
        }

        // todo: 验证
        validateConfiguration();

        // 如果配置*号 代表接收所有
        if (krb5PrincName != null && krb5PrincName.toString().equals("*")) {
            unboundServer = true;
        }

        if (tryFirstPass) {
            try {
                attemptAuthentication(true);
                if (debug)
                    System.out.println("\t\t[Krb5LoginModule] " +
                                       "authentication succeeded");
                succeeded = true;
                cleanState();
                return true;
            } catch (LoginException le) {
                // authentication failed -- try again below by prompting
                cleanState();
                if (debug) {
                    System.out.println("\t\t[Krb5LoginModule] " +
                                       "tryFirstPass failed with:" +
                                       le.getMessage());
                }
            }
        } else if (useFirstPass) {
            try {
                attemptAuthentication(true);
                succeeded = true;
                cleanState();
                return true;
            } catch (LoginException e) {
                // authentication failed -- clean out state
                if (debug) {
                    System.out.println("\t\t[Krb5LoginModule] " +
                                       "authentication failed \n" +
                                       e.getMessage());
                }
                succeeded = false;
                cleanState();
                throw e;
            }
        }

        // attempt the authentication by getting the username and pwd
        // by prompting or configuration i.e. not from shared state

        // 通过提示或配置获取用户名和PWD来尝试身份验证，即不从共享状态

        try {
            attemptAuthentication(false);
            succeeded = true;
            cleanState();
            return true;
        } catch (LoginException e) {
            // authentication failed -- clean out state
            if (debug) {
                System.out.println("\t\t[Krb5LoginModule] " +
                                   "authentication failed \n" +
                                   e.getMessage());
            }
            succeeded = false;
            cleanState();
            throw e;
        }
    }
    /**
     * process the configuration options
     * Get the TGT either out of
     * cache or from the KDC using the password entered
     * Check the  permission before getting the TGT
     *
     * 处理配置选项使用输入的密码将TGT从缓存中取出或从KDC中取出
     */

    private void attemptAuthentication(boolean getPasswdFromSharedState)
        throws LoginException {

        /*
         * Check the creds cache to see whether
         * we have TGT for this client principal
         *
         * 检查creds缓存，看看我们是否有这个客户端主体的TGT
         */
        if (krb5PrincName != null) {
            try {
                principal = new PrincipalName
                    (krb5PrincName.toString(),
                     PrincipalName.KRB_NT_PRINCIPAL);
            } catch (KrbException e) {
                LoginException le = new LoginException(e.getMessage());
                le.initCause(e);
                throw le;
            }
        }

        try {
            // 一般我们都设置false
            if (useTicketCache) {
                // ticketCacheName == null implies the default cache
                if (debug)
                    System.out.println("Acquire TGT from Cache");
                cred  = Credentials.acquireTGTFromCache
                    (principal, ticketCacheName);

                if (cred != null) {
                    // check to renew credentials
                    if (!isCurrent(cred)) {
                        if (renewTGT) {
                            cred = renewCredentials(cred);
                        } else {
                            // credentials have expired
                            cred = null;
                            if (debug)
                                System.out.println("Credentials are" +
                                                " no longer valid");
                        }
                    }
                }

                if (cred != null) {
                   // get the principal name from the ticket cache
                   if (principal == null) {
                        principal = cred.getClient();
                   }
                }
                if (debug) {
                    System.out.println("Principal is " + principal);
                    if (cred == null) {
                        System.out.println
                            ("null credentials from Ticket Cache");
                    }
                }
            }

            // cred = null indicates that we didn't get the creds
            // from the cache or useTicketCache was false
            // cred = null表示我们没有从缓存中获取cred，或者useTicketCache为false

            if (cred == null) {
                // We need the principal name whether we use keytab
                // or AS Exchange  无论使用keytab还是AS Exchange，我们都需要主体名称
                if (principal == null) {
                    promptForName(getPasswdFromSharedState);
                    principal = new PrincipalName
                        (krb5PrincName.toString(),
                         PrincipalName.KRB_NT_PRINCIPAL);
                }

                /*
                 * Before dynamic KeyTab support (6894072), here we check if
                 * the keytab contains keys for the principal. If no, keytab
                 * will not be used and password is prompted for.
                 *
                 * After 6894072, we normally don't check it, and expect the
                 * keys can be populated until a real connection is made. The
                 * check is still done when isInitiator == true, where the keys
                 * will be used right now.
                 *
                 * Probably tricky relations:
                 *
                 * useKeyTab is config flag, but when it's true but the ktab
                 * does not contains keys for principal, we would use password
                 * and keep the flag unchanged (for reuse?). In this method,
                 * we use (ktab != null) to check whether keytab is used.
                 * After this method (and when storeKey == true), we use
                 * (encKeys == null) to check.
                 *
                 * 在动态KeyTab支持(6894072)之前，这里我们检查KeyTab是否包含主体的密钥。
                 * 如果不使用，则不使用keytab，并提示输入密码。
                 *
                 * 在6894072之后，我们通常不检查它，并期望能够填充键，直到建立真正的连接。
                 * 当isInitiator == true时，检查仍然进行，此时键将被立即使用。可能有一些
                 *
                 * 微妙的关系:
                 *
                 * useKeyTab是config标志，但是当它为真，但ktab不包含主体的键时，
                 * 我们将使用密码并保持标志不变(为了重用?)在这个方法中，我们使用(ktab != null)
                 * 来检查是否使用了keytab。在这个方法之后(当storeKey == true时)，我们使用
                 * (encKeys == null)进行检查。
                 *
                 */
                if (useKeyTab) {
                    // 如果不是支持所有 * 号
                    if (!unboundServer) {
                        KerberosPrincipal kp =
                                new KerberosPrincipal(principal.getName());
                        ktab = (keyTabName == null)
                                ? KeyTab.getInstance(kp)
                                : KeyTab.getInstance(kp, new File(keyTabName));
                    } else {
                        ktab = (keyTabName == null)
                                ? KeyTab.getUnboundInstance()
                                : KeyTab.getUnboundInstance(new File(keyTabName));
                    }
                    if (isInitiator) {
                        if (Krb5Util.keysFromJavaxKeyTab(ktab, principal).length
                                == 0) {
                            ktab = null;
                            if (debug) {
                                System.out.println
                                    ("Key for the principal " +
                                     principal  +
                                     " not available in " +
                                     ((keyTabName == null) ?
                                      "default key tab" : keyTabName));
                            }
                        }
                    }
                }

                KrbAsReqBuilder builder;

                if (ktab == null) {
                    promptForPass(getPasswdFromSharedState);
                    builder = new KrbAsReqBuilder(principal, password);
                    if (isInitiator) {
                        // XXX Even if isInitiator=false, it might be
                        // better to do an AS-REQ so that keys can be
                        // updated with PA info
                        cred = builder.action().getCreds();
                    }
                    if (storeKey) {
                        encKeys = builder.getKeys(isInitiator);
                        // When encKeys is empty, the login actually fails.
                        // For compatibility, exception is thrown in commit().
                    }
                } else {
                    builder = new KrbAsReqBuilder(principal, ktab);
                    if (isInitiator) {
                        cred = builder.action().getCreds();
                    }
                }
                builder.destroy();

                if (debug) {
                    System.out.println("principal is " + principal);
                    HexDumpEncoder hd = new HexDumpEncoder();
                    if (ktab != null) {
                        System.out.println("Will use keytab");
                    } else if (storeKey) {
                        for (int i = 0; i < encKeys.length; i++) {
                            System.out.println("EncryptionKey: keyType=" +
                                encKeys[i].getEType() +
                                " keyBytes (hex dump)=" +
                                hd.encodeBuffer(encKeys[i].getBytes()));
                        }
                    }
                }

                // we should hava a non-null cred
                if (isInitiator && (cred == null)) {
                    throw new LoginException
                        ("TGT Can not be obtained from the KDC ");
                }

            }
        } catch (KrbException e) {
            LoginException le = new LoginException(e.getMessage());
            le.initCause(e);
            throw le;
        } catch (IOException ioe) {
            LoginException ie = new LoginException(ioe.getMessage());
            ie.initCause(ioe);
            throw ie;
        }
    }

    private void promptForName(boolean getPasswdFromSharedState)
        throws LoginException {
        krb5PrincName = new StringBuffer("");
        if (getPasswdFromSharedState) {
            // use the name saved by the first module in the stack
            username = (String)sharedState.get(NAME);
            if (debug) {
                System.out.println
                    ("username from shared state is " + username + "\n");
            }
            if (username == null) {
                System.out.println
                    ("username from shared state is null\n");
                throw new LoginException
                    ("Username can not be obtained from sharedstate ");
            }
            if (debug) {
                System.out.println
                    ("username from shared state is " + username + "\n");
            }
            if (username != null && username.length() > 0) {
                krb5PrincName.insert(0, username);
                return;
            }
        }

        if (doNotPrompt) {
            throw new LoginException
                ("Unable to obtain Principal Name for authentication ");
        } else {
            if (callbackHandler == null)
                throw new LoginException("No CallbackHandler "
                                         + "available "
                                         + "to garner authentication "
                                         + "information from the user");
            try {
                String defUsername = System.getProperty("user.name");

                Callback[] callbacks = new Callback[1];
                MessageFormat form = new MessageFormat(
                                       rb.getString(
                                       "Kerberos.username.defUsername."));
                Object[] source =  {defUsername};
                callbacks[0] = new NameCallback(form.format(source));
                callbackHandler.handle(callbacks);
                username = ((NameCallback)callbacks[0]).getName();
                if (username == null || username.length() == 0)
                    username = defUsername;
                krb5PrincName.insert(0, username);

            } catch (java.io.IOException ioe) {
                throw new LoginException(ioe.getMessage());
            } catch (UnsupportedCallbackException uce) {
                throw new LoginException
                    (uce.getMessage()
                     +" not available to garner "
                     +" authentication information "
                     +" from the user");
            }
        }
    }

    private void promptForPass(boolean getPasswdFromSharedState)
        throws LoginException {

        if (getPasswdFromSharedState) {
            // use the password saved by the first module in the stack
            password = (char[])sharedState.get(PWD);
            if (password == null) {
                if (debug) {
                    System.out.println
                        ("Password from shared state is null");
                }
                throw new LoginException
                    ("Password can not be obtained from sharedstate ");
            }
            if (debug) {
                System.out.println
                    ("password is " + new String(password));
            }
            return;
        }
        /**
         * todo: 这里可能报错
         *
         *
         * javax.security.auth.login.LoginException: Unable to obtain password from user
         *
         * 当代码无法在keytab中找到匹配条目以获取密码时，通常会发生此错误。由于CDH中的服务不是交互式的，因此在此示例中，密码请求失败并导致显示消息。
         * 这可以表明无法读取keytab。
         * 如果keytab中的所有条目均不可用，例如，如果keytab仅具有aes256但未将无限强度的加密jar添加到群集中，则也会发生这种情况。
         *
         * 如果是flink 可能是配置错误
         *
         * # todo 重点配置
         * security.kerberos.login.use-ticket-cache: true
         * security.kerberos.login.keytab: /home/hdfs/hdfs.keytab
         * # todo: 这里需要hdfs的配置
         * security.kerberos.login.principal: hdfs/zdh2@ZDH.COM
         * security.kerberos.login.contexts: HdfsClient,Client,KafkaClient
         */
        if (doNotPrompt) {
            throw new LoginException
                ("Unable to obtain password from user\n");
        } else {
            if (callbackHandler == null)
                throw new LoginException("No CallbackHandler "
                                         + "available "
                                         + "to garner authentication "
                                         + "information from the user");
            try {
                Callback[] callbacks = new Callback[1];
                String userName = krb5PrincName.toString();
                MessageFormat form = new MessageFormat(
                                         rb.getString(
                                         "Kerberos.password.for.username."));
                Object[] source = {userName};
                callbacks[0] = new PasswordCallback(
                                                    form.format(source),
                                                    false);
                callbackHandler.handle(callbacks);
                char[] tmpPassword = ((PasswordCallback)
                                      callbacks[0]).getPassword();
                if (tmpPassword == null) {
                    throw new LoginException("No password provided");
                }
                password = new char[tmpPassword.length];
                System.arraycopy(tmpPassword, 0,
                                 password, 0, tmpPassword.length);
                ((PasswordCallback)callbacks[0]).clearPassword();


                // clear tmpPassword
                for (int i = 0; i < tmpPassword.length; i++)
                    tmpPassword[i] = ' ';
                tmpPassword = null;
                if (debug) {
                    System.out.println("\t\t[Krb5LoginModule] " +
                                       "user entered username: " +
                                       krb5PrincName);
                    System.out.println();
                }
            } catch (java.io.IOException ioe) {
                throw new LoginException(ioe.getMessage());
            } catch (UnsupportedCallbackException uce) {
                throw new LoginException(uce.getMessage()
                                         +" not available to garner "
                                         +" authentication information "
                                         + "from the user");
            }
        }
    }

    private void validateConfiguration() throws LoginException {
        if (doNotPrompt && !useTicketCache && !useKeyTab
                && !tryFirstPass && !useFirstPass)
            throw new LoginException
                ("Configuration Error"
                 + " - either doNotPrompt should be "
                 + " false or at least one of useTicketCache, "
                 + " useKeyTab, tryFirstPass and useFirstPass"
                 + " should be true");
        if (ticketCacheName != null && !useTicketCache)
            throw new LoginException
                ("Configuration Error "
                 + " - useTicketCache should be set "
                 + "to true to use the ticket cache"
                 + ticketCacheName);
        if (keyTabName != null & !useKeyTab)
            throw new LoginException
                ("Configuration Error - useKeyTab should be set to true "
                 + "to use the keytab" + keyTabName);
        if (storeKey && doNotPrompt && !useKeyTab
                && !tryFirstPass && !useFirstPass)
            throw new LoginException
                ("Configuration Error - either doNotPrompt should be set to "
                 + " false or at least one of tryFirstPass, useFirstPass "
                 + "or useKeyTab must be set to true for storeKey option");
        if (renewTGT && !useTicketCache)
            throw new LoginException
                ("Configuration Error"
                 + " - either useTicketCache should be "
                 + " true or renewTGT should be false");
        if (krb5PrincName != null && krb5PrincName.toString().equals("*")) {
            if (isInitiator) {
                throw new LoginException
                    ("Configuration Error"
                    + " - principal cannot be * when isInitiator is true");
            }
        }
    }

    private boolean isCurrent(Credentials creds)
    {
        Date endTime = creds.getEndTime();
        if (endTime != null) {
            return (System.currentTimeMillis() <= endTime.getTime());
        }
        return true;
    }

    private Credentials renewCredentials(Credentials creds)
    {
        Credentials lcreds;
        try {
            if (!creds.isRenewable())
                throw new RefreshFailedException("This ticket" +
                                " is not renewable");
            if (System.currentTimeMillis() > cred.getRenewTill().getTime())
                throw new RefreshFailedException("This ticket is past "
                                             + "its last renewal time.");
            lcreds = creds.renew();
            if (debug)
                System.out.println("Renewed Kerberos Ticket");
        } catch (Exception e) {
            lcreds = null;
            if (debug)
                System.out.println("Ticket could not be renewed : "
                                + e.getMessage());
        }
        return lcreds;
    }

    /**
     * <p> This method is called if the LoginContext's
     * overall authentication succeeded
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
     * LoginModules succeeded).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (checked by retrieving the private state saved by the
     * <code>login</code> method), then this method associates a
     * <code>Krb5Principal</code>
     * with the <code>Subject</code> located in the
     * <code>LoginModule</code>. It adds Kerberos Credentials to the
     *  the Subject's private credentials set. If this LoginModule's own
     * authentication attempted failed, then this method removes
     * any state that was originally saved.
     *
     * <p>
     *
     * 如果LoginContext的整体身份验证成功(相关的REQUIRED、REQUISITE、SUFFICIENT和OPTIONAL
     * LoginModules成功)，则调用此方法。
     *
     * 如果此LoginModule自己的身份验证尝试成功(通过检索登录方法保存的私有状态进行检查)，则此方法将位于
     * LoginModule中的Krb5Principal与Subject关联。它将Kerberos凭证添加到Subject的私有凭证集。
     * 如果这个LoginModule自己的身份验证尝试失败，那么这个方法将删除最初保存的任何状态。
     *
     * @exception LoginException if the commit fails.
     *
     * @return true if this LoginModule's own login and commit
     *          attempts succeeded, or false otherwise.
     */

    public boolean commit() throws LoginException {

        /*
         * Let us add the Krb5 Creds to the Subject's
         * private credentials. The credentials are of type
         * KerberosKey or KerberosTicket
         *
         * 让我们将Krb5 Creds添加到Subject的私有凭据中。凭证的类型为KerberosKey或KerberosTicket
         */
        if (succeeded == false) {
            return false;
        } else {
            // 如果 login成功了 那么走下面

            if (isInitiator && (cred == null)) {
                succeeded = false;
                throw new LoginException("Null Client Credential");
            }

            if (subject.isReadOnly()) {
                cleanKerberosCred();
                throw new LoginException("Subject is Readonly");
            }

            /*
             * Add the Principal (authenticated identity)
             * to the Subject's principal set and
             * add the credentials (TGT or Service key) to the
             * Subject's private credentials
             *
             * 将主体(经过身份验证的身份)添加到Subject的主体集，并将凭据(TGT或Service密钥)
             * 添加到Subject的私有凭据
             */

            Set<Object> privCredSet =  subject.getPrivateCredentials();
            Set<java.security.Principal> princSet  = subject.getPrincipals();
            kerbClientPrinc = new KerberosPrincipal(principal.getName());

            // create Kerberos Ticket  创建一个票据
            if (isInitiator) {
                // todo:  创建一个票据 Ticket
                kerbTicket = Krb5Util.credsToTicket(cred);
            }

            if (storeKey && encKeys != null) {
                if (encKeys.length == 0) {
                    succeeded = false;
                    throw new LoginException("Null Server Key ");
                }

                kerbKeys = new KerberosKey[encKeys.length];
                for (int i = 0; i < encKeys.length; i ++) {
                    Integer temp = encKeys[i].getKeyVersionNumber();
                    kerbKeys[i] = new KerberosKey(kerbClientPrinc,
                                          encKeys[i].getBytes(),
                                          encKeys[i].getEType(),
                                          (temp == null?
                                          0: temp.intValue()));
                }

            }
            // Let us add the kerbClientPrinc,kerbTicket and KeyTab/KerbKey (if
            // storeKey is true)

            // We won't add "*" as a KerberosPrincipal
            if (!unboundServer &&
                    !princSet.contains(kerbClientPrinc)) {
                princSet.add(kerbClientPrinc);
            }

            // add the TGT
            if (kerbTicket != null) {
                if (!privCredSet.contains(kerbTicket))
                    privCredSet.add(kerbTicket);
            }

            if (storeKey) {
                if (encKeys == null) {
                    if (ktab != null) {
                        if (!privCredSet.contains(ktab)) {
                            privCredSet.add(ktab);
                        }
                    } else {
                        succeeded = false;
                        throw new LoginException("No key to store");
                    }
                } else {
                    for (int i = 0; i < kerbKeys.length; i ++) {
                        if (!privCredSet.contains(kerbKeys[i])) {
                            privCredSet.add(kerbKeys[i]);
                        }
                        encKeys[i].destroy();
                        encKeys[i] = null;
                        if (debug) {
                            System.out.println("Added server's key"
                                            + kerbKeys[i]);
                            System.out.println("\t\t[Krb5LoginModule] " +
                                           "added Krb5Principal  " +
                                           kerbClientPrinc.toString()
                                           + " to Subject");
                        }
                    }
                }
            }
        }
        commitSucceeded = true;
        if (debug)
            System.out.println("Commit Succeeded \n");
        return true;
    }

    /**
     * <p> This method is called if the LoginContext's
     * overall authentication failed.
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
     * LoginModules did not succeed).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (checked by retrieving the private state saved by the
     * <code>login</code> and <code>commit</code> methods),
     * then this method cleans up any state that was originally saved.
     *
     * <p>
     *
     * 如果LoginContext的整体身份验证失败，则调用此方法。(相关的REQUIRED、REQUISITE、
     * SUFFICIENT和OPTIONAL LoginModules没有成功)。
     *
     *
     *
     * 如果这个LoginModule自己的身份验证尝试成功(通过检索登录和提交方法保存的私有状态
     * 进行检查)，那么这个方法将清除最初保存的任何状态。
     *
     * @exception LoginException if the abort fails.
     *
     * @return false if this LoginModule's own login and/or commit attempts
     *          failed, and true otherwise.
     */

    public boolean abort() throws LoginException {
        if (succeeded == false) {
            return false;
        } else if (succeeded == true && commitSucceeded == false) {
            // login succeeded but overall authentication failed
            succeeded = false;
            cleanKerberosCred();
        } else {
            // overall authentication succeeded and commit succeeded,
            // but someone else's commit failed
            //
            // 整体认证成功，提交成功，但其他人提交失败
            logout();
        }
        return true;
    }

    /**
     * Logout the user.
     *
     * <p> This method removes the <code>Krb5Principal</code>
     * that was added by the <code>commit</code> method.
     *
     * <p>
     *
     * 注销用户。
     *
     * 该方法移除Krb5Principal，该commit方法添加了该Krb5Principal。
     *
     * @exception LoginException if the logout fails.
     *
     * @return true in all cases since this <code>LoginModule</code>
     *          should not be ignored.
     */
    public boolean logout() throws LoginException {

        if (debug) {
            System.out.println("\t\t[Krb5LoginModule]: " +
                "Entering logout");
        }

        if (subject.isReadOnly()) {
            cleanKerberosCred();
            throw new LoginException("Subject is Readonly");
        }

        subject.getPrincipals().remove(kerbClientPrinc);
           // Let us remove all Kerberos credentials stored in the Subject
        Iterator<Object> it = subject.getPrivateCredentials().iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof KerberosTicket ||
                    o instanceof KerberosKey ||
                    o instanceof KeyTab) {
                it.remove();
            }
        }
        // clean the kerberos ticket and keys
        cleanKerberosCred();

        succeeded = false;
        commitSucceeded = false;
        if (debug) {
            System.out.println("\t\t[Krb5LoginModule]: " +
                               "logged out Subject");
        }
        return true;
    }

    /**
     * Clean Kerberos credentials
     */
    private void cleanKerberosCred() throws LoginException {
        // Clean the ticket and server key
        try {
            if (kerbTicket != null)
                // 摧毁票据
                kerbTicket.destroy();
            if (kerbKeys != null) {
                for (int i = 0; i < kerbKeys.length; i++) {
                    kerbKeys[i].destroy();
                }
            }
        } catch (DestroyFailedException e) {
            throw new LoginException
                ("Destroy Failed on Kerberos Private Credentials");
        }
        kerbTicket = null;
        kerbKeys = null;
        kerbClientPrinc = null;
    }

    /**
     * Clean out the state
     */
    private void cleanState() {

        // save input as shared state only if
        // authentication succeeded
        if (succeeded) {
            if (storePass &&
                !sharedState.containsKey(NAME) &&
                !sharedState.containsKey(PWD)) {
                sharedState.put(NAME, username);
                sharedState.put(PWD, password);
            }
        } else {
            // remove temp results for the next try
            encKeys = null;
            ktab = null;
            principal = null;
        }
        username = null;
        password = null;
        if (krb5PrincName != null && krb5PrincName.length() != 0)
            krb5PrincName.delete(0, krb5PrincName.length());
        krb5PrincName = null;
        if (clearPass) {
            sharedState.remove(NAME);
            sharedState.remove(PWD);
        }
    }
}
