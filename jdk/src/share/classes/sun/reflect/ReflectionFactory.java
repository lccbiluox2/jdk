/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect;

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Objects;

import sun.reflect.misc.ReflectUtil;


/** <P> The master factory for all reflective objects, both those in
    java.lang.reflect (Fields, Methods, Constructors) as well as their
    delegates (FieldAccessors, MethodAccessors, ConstructorAccessors).
    </P>

    <P> The methods in this class are extremely unsafe and can cause
    subversion of both the language and the verifier. For this reason,
    they are all instance methods, and access to the constructor of
    this factory is guarded by a security check, in similar style to
    {@link sun.misc.Unsafe}. </P>
*/
// 反射对象工厂
public class ReflectionFactory {

    // 是否检查过初始化
    private static boolean initted = false;
    private static final Permission reflectionFactoryAccessPerm
        = new RuntimePermission("reflectionFactoryAccess");
    private static final ReflectionFactory soleInstance = new ReflectionFactory();
    // Provides access to package-private mechanisms in java.lang.reflect
    // 反射工具类
    private static volatile LangReflectAccess langReflectAccess;

    /* Method for static class initializer <clinit>, or null */
    private static volatile Method hasStaticInitializerMethod;

    //
    // "Inflation" mechanism. Loading bytecodes to implement
    // Method.invoke() and Constructor.newInstance() currently costs
    // 3-4x more than an invocation via native code for the first
    // invocation (though subsequent invocations have been benchmarked
    // to be over 20x faster). Unfortunately this cost increases
    // startup time for certain applications that use reflection
    // intensively (but only once per class) to bootstrap themselves.
    // To avoid this penalty we reuse the existing JVM entry points
    // for the first few invocations of Methods and Constructors and
    // then switch to the bytecode-based implementations.
    //
    // Package-private to be accessible to NativeMethodAccessorImpl
    // and NativeConstructorAccessorImpl
    /*
     * 来自：https://blogs.oracle.com/buck/inflation-system-properties
     *
     * Java反射有两种方法来调用类的方法或构造器：JNI或纯Java。
     * JNI的执行速度很慢(主要是因为从Java到JNI以及从JNI到Java的过渡开销)，但是它的初始化成本为零，因为我们
     * 不需要生成任何东西，通用访问器的实现中已经内置。
     *
     * 纯Java的解决方案执行速度更快(没有JNI开销)，但是初始化成本很高，因为我们需要在运行前为每个需要
     * 调用的方法生成自定义字节码。
     *
     * (简单讲：JNI方案执行慢，初始化快；纯Java方案执行快，初始化慢)
     *
     * 因此，理想情况下，我们只希望为将被调用次数多的方法生成纯Java实现(因为这样可以分摊初始化成本)。
     * 而"Inflation"技术就是Java运行时尝试达到此目标的技术。
     *
     * 默认情况下，"Inflation"技术是开启的。
     * 这样一来，反射操作会在前期先使用JNI调用，但后续会为调用次数超过某个阈值的访问器生成纯Java版本。
     *
     * 如果关闭了"Inflation"技术，则跳过前面的JNI调用，在首次反射调用时即生成纯Java版本的访问器。
     */
    // 是否关闭了"Inflation"技术。
    private static boolean noInflation        = false;
    // JNI调用阈值，当某个方法反射调用超过这个阈值时，会为其生成纯Java的版本访问器。
    private static int     inflationThreshold = 15;

    private ReflectionFactory() {}

    /**
     * A convenience class for acquiring the capability to instantiate
     * reflective objects.  Use this instead of a raw call to {@link
     * #getReflectionFactory} in order to avoid being limited by the
     * permissions of your callers.
     *
     * <p>An instance of this class can be used as the argument of
     * <code>AccessController.doPrivileged</code>.
     */
    public static final class GetReflectionFactoryAction
        implements PrivilegedAction<ReflectionFactory> {
        public ReflectionFactory run() {
            return getReflectionFactory();
        }
    }

    /**
     * Provides the caller with the capability to instantiate reflective
     * objects.
     *
     * <p> First, if there is a security manager, its
     * <code>checkPermission</code> method is called with a {@link
     * java.lang.RuntimePermission} with target
     * <code>"reflectionFactoryAccess"</code>.  This may result in a
     * security exception.
     *
     * <p> The returned <code>ReflectionFactory</code> object should be
     * carefully guarded by the caller, since it can be used to read and
     * write private data and invoke private methods, as well as to load
     * unverified bytecodes.  It must never be passed to untrusted code.
     *
     * @exception SecurityException if a security manager exists and its
     *             <code>checkPermission</code> method doesn't allow
     *             access to the RuntimePermission "reflectionFactoryAccess".  */
    // 返回ReflectionFactory实例
    public static ReflectionFactory getReflectionFactory() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            // TO DO: security.checkReflectionFactoryAccess();
            security.checkPermission(reflectionFactoryAccessPerm);
        }
        return soleInstance;
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by java.lang.reflect
    //
    //

    /** Called only by java.lang.reflect.Modifier's static initializer */
    // 设置指定的反射工具类，仅在Modifier静态初始化块中被初始化
    public void setLangReflectAccess(LangReflectAccess access) {
        langReflectAccess = access;
    }

    /**
     * Note: this routine can cause the declaring class for the field
     * be initialized and therefore must not be called until the
     * first get/set of this field.
     * @param field the field
     * @param override true if caller has overridden aaccessibility
     */
    // 构造并返回字段field的访问器，override指示字段field的访问安全检查是否被禁用
    public jdk.internal.reflect.FieldAccessor newFieldAccessor(Field field, boolean override) {
        // 检查初始化，只检查一次
        checkInitted();
        return UnsafeFieldAccessorFactory.newFieldAccessor(field, override);
    }

    // 返回指定方法的访问器
    public MethodAccessor newMethodAccessor(Method method) {
        // 检查初始化，只检查一次
        checkInitted();

        if (noInflation && !ReflectUtil.isVMAnonymousClass(method.getDeclaringClass())) {
            return new jdk.internal.reflect.MethodAccessorGenerator().
                generateMethod(method.getDeclaringClass(),
                               method.getName(),
                               method.getParameterTypes(),
                               method.getReturnType(),
                               method.getExceptionTypes(),
                               method.getModifiers());
        } else {
            // 构造基于JNI的方法调用器，先尝试用基于JNI的方式进行反射操作
            NativeMethodAccessorImpl acc =
                new NativeMethodAccessorImpl(method);
            DelegatingMethodAccessorImpl res =
                new DelegatingMethodAccessorImpl(acc);
            acc.setParent(res);
            return res;
        }
    }

    // 返回指定构造器的访问器
    public jdk.internal.reflect.ConstructorAccessor newConstructorAccessor(Constructor<?> c) {
        // 检查初始化，只检查一次
        checkInitted();

        // 返回构造器所在的类
        Class<?> declaringClass = c.getDeclaringClass();

        // 如果需要创建的是抽象类，则禁止通过反射实例化对象
        if (Modifier.isAbstract(declaringClass.getModifiers())) {
            // 适用于抽象类的构造器访问器：当尝试调用newInstance()方法构造对象时，会抛出异常
            return new InstantiationExceptionConstructorAccessorImpl(null);
        }

        // 如果需要创建的是Class类，则禁止通过反射实例化对象
        if (declaringClass == Class.class) {
            return new InstantiationExceptionConstructorAccessorImpl
                ("Can not instantiate java.lang.Class");
        }
        // Bootstrapping issue: since we use Class.newInstance() in
        // the ConstructorAccessor generation process, we have to
        // break the cycle here.
        // 如果declaringClass类是否与ConstructorAccessorImpl类相同，或为ConstructorAccessorImpl类的子类，则需要防止构造器产生无限递归调用
        if (Reflection.isSubclassOf(declaringClass,
                                    jdk.internal.reflect.ConstructorAccessorImpl.class)) {
            return new BootstrapConstructorAccessorImpl(c);
        }

        // 构造器可能已经发生了改变
        declaringClass = c.getDeclaringClass();


        // 如果关闭了"Inflation"技术，且declaringClass不是虚拟机匿名类
        if (noInflation && !ReflectUtil.isVMAnonymousClass(declaringClass)) {
            // 构造基于纯Java的构造器访问器，以便直接使用纯Java的方式进行反射操作
            return new jdk.internal.reflect.MethodAccessorGenerator().
                generateConstructor(c.getDeclaringClass(),
                                    c.getParameterTypes(),
                                    c.getExceptionTypes(),
                                    c.getModifiers());
        } else {
            // 如果开启"Inflation"技术

            // 构造基于JNI的构造器调用器，先尝试用基于JNI的方式进行反射操作
            jdk.internal.reflect.NativeConstructorAccessorImpl acc =
                new jdk.internal.reflect.NativeConstructorAccessorImpl(c);
            jdk.internal.reflect.DelegatingConstructorAccessorImpl res =
                new jdk.internal.reflect.DelegatingConstructorAccessorImpl(acc);
            acc.setParent(res);
            return res;
        }
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by java.lang
    //
    //

    /** Creates a new java.lang.reflect.Field. Access checks as per
        java.lang.reflect.AccessibleObject are not overridden. */
    // 构造并返回一个"字段"对象
    public Field newField(Class<?> declaringClass,
                          String name,
                          Class<?> type,
                          int modifiers,
                          int slot,
                          String signature,
                          byte[] annotations)
    {
        return langReflectAccess().newField(declaringClass,
                                            name,
                                            type,
                                            modifiers,
                                            slot,
                                            signature,
                                            annotations);
    }

    /** Creates a new java.lang.reflect.Method. Access checks as per
        java.lang.reflect.AccessibleObject are not overridden. */
    // 构造并返回一个"方法"对象
    public Method newMethod(Class<?> declaringClass,
                            String name,
                            Class<?>[] parameterTypes,
                            Class<?> returnType,
                            Class<?>[] checkedExceptions,
                            int modifiers,
                            int slot,
                            String signature,
                            byte[] annotations,
                            byte[] parameterAnnotations,
                            byte[] annotationDefault)
    {
        return langReflectAccess().newMethod(declaringClass,
                                             name,
                                             parameterTypes,
                                             returnType,
                                             checkedExceptions,
                                             modifiers,
                                             slot,
                                             signature,
                                             annotations,
                                             parameterAnnotations,
                                             annotationDefault);
    }

    /** Creates a new java.lang.reflect.Constructor. Access checks as
        per java.lang.reflect.AccessibleObject are not overridden. */
    public Constructor<?> newConstructor(Class<?> declaringClass,
                                         Class<?>[] parameterTypes,
                                         Class<?>[] checkedExceptions,
                                         int modifiers,
                                         int slot,
                                         String signature,
                                         byte[] annotations,
                                         byte[] parameterAnnotations)
    {
        return langReflectAccess().newConstructor(declaringClass,
                                                  parameterTypes,
                                                  checkedExceptions,
                                                  modifiers,
                                                  slot,
                                                  signature,
                                                  annotations,
                                                  parameterAnnotations);
    }

    /** Gets the MethodAccessor object for a java.lang.reflect.Method */
    // 返回指定方法的访问器
    public MethodAccessor getMethodAccessor(Method m) {
        return langReflectAccess().getMethodAccessor(m);
    }

    /** Sets the MethodAccessor object for a java.lang.reflect.Method */
    // 为指定的方法设置访问器
    public void setMethodAccessor(Method m, MethodAccessor accessor) {
        langReflectAccess().setMethodAccessor(m, accessor);
    }

    /** Gets the ConstructorAccessor object for a
        java.lang.reflect.Constructor */
    // 返回指定构造器的访问器
    public jdk.internal.reflect.ConstructorAccessor getConstructorAccessor(Constructor<?> c) {
        return langReflectAccess().getConstructorAccessor(c);
    }

    /** Sets the ConstructorAccessor object for a
        java.lang.reflect.Constructor */
    // 为指定的构造器设置访问器
    public void setConstructorAccessor(Constructor<?> c,
                                       jdk.internal.reflect.ConstructorAccessor accessor)
    {
        langReflectAccess().setConstructorAccessor(c, accessor);
    }

    /** Makes a copy of the passed method. The returned method is a
        "child" of the passed one; see the comments in Method.java for
        details. */
    // 方法拷贝，参数中的方法是返回值的复制源
    public Method copyMethod(Method arg) {
        return langReflectAccess().copyMethod(arg);
    }

    /** Makes a copy of the passed field. The returned field is a
        "child" of the passed one; see the comments in Field.java for
        details. */
    // 字段对象拷贝
    public Field copyField(Field arg) {
        return langReflectAccess().copyField(arg);
    }

    /** Makes a copy of the passed constructor. The returned
        constructor is a "child" of the passed one; see the comments
        in Constructor.java for details. */
    // 构造器拷贝
    public <T> Constructor<T> copyConstructor(Constructor<T> arg) {
        return langReflectAccess().copyConstructor(arg);
    }

    /** Gets the byte[] that encodes TypeAnnotations on an executable.
     */
    public byte[] getExecutableTypeAnnotationBytes(Executable ex) {
        return langReflectAccess().getExecutableTypeAnnotationBytes(ex);
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by serialization
    //
    //

    /**
     * Returns an accessible constructor capable of creating instances
     * of the given class, initialized by the given constructor.
     *
     * @param classToInstantiate the class to instantiate
     * @param constructorToCall the constructor to call
     * @return an accessible constructor
     */
    // 构造并返回一个"构造器"对象
    public Constructor<?> newConstructorForSerialization
        (Class<?> classToInstantiate, Constructor<?> constructorToCall)
    {
        // Fast path
        if (constructorToCall.getDeclaringClass() == classToInstantiate) {
            return constructorToCall;
        }
        return generateConstructor(classToInstantiate, constructorToCall);
    }

    /**
     * Returns an accessible no-arg constructor for a class.
     * The no-arg constructor is found searching the class and its supertypes.
     *
     * @param cl the class to instantiate
     * @return a no-arg constructor for the class or {@code null} if
     *     the class or supertypes do not have a suitable no-arg constructor
     */
    /*
     * 返回clazz的第一个不可序列化的父类的无参构造器，要求该无参构造器可被clazz访问。
     * 如果未找到该构造器，或该构造器子类无法访问，则返回null。
     */
    public final Constructor<?> newConstructorForSerialization(Class<?> cl) {
        Class<?> initCl = cl;
        // 如果initCl是Serializable的实现类，则向上查找其首个不是Serializable实现类的父类
        while (Serializable.class.isAssignableFrom(initCl)) {
            // 如果不存在父类(如接口)，直接返回null
            if ((initCl = initCl.getSuperclass()) == null) {
                return null;
            }
        }
        Constructor<?> constructorToCall;
        try {
            // 获取initCl中的无参构造器
            constructorToCall = initCl.getDeclaredConstructor();
            // 获取该构造器的修饰符
            int mods = constructorToCall.getModifiers();
            // 如果构造器私有，或者构造器为包访问权限，但cl和initCl不在同一个包，此时返回null，即无法获取到可用构造器
            if ((mods & Modifier.PRIVATE) != 0 ||
                    ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0 &&
                            !packageEquals(cl, initCl))) {
                return null;
            }
        } catch (NoSuchMethodException ex) {
            return null;
        }
        // 基于constructorToCall，生成一个可供clazz使用的构造器后返回
        return generateConstructor(cl, constructorToCall);
    }

    private final Constructor<?> generateConstructor(Class<?> classToInstantiate,
                                                     Constructor<?> constructorToCall) {


        jdk.internal.reflect.ConstructorAccessor acc = new jdk.internal.reflect.MethodAccessorGenerator().
            generateSerializationConstructor(classToInstantiate,
                                             constructorToCall.getParameterTypes(),
                                             constructorToCall.getExceptionTypes(),
                                             constructorToCall.getModifiers(),
                                             constructorToCall.getDeclaringClass());
        Constructor<?> c = newConstructor(constructorToCall.getDeclaringClass(),
                                          constructorToCall.getParameterTypes(),
                                          constructorToCall.getExceptionTypes(),
                                          constructorToCall.getModifiers(),
                                          langReflectAccess().
                                          getConstructorSlot(constructorToCall),
                                          langReflectAccess().
                                          getConstructorSignature(constructorToCall),
                                          langReflectAccess().
                                          getConstructorAnnotations(constructorToCall),
                                          langReflectAccess().
                                          getConstructorParameterAnnotations(constructorToCall));
        setConstructorAccessor(c, acc);
        c.setAccessible(true);
        return c;
    }

    /**
     * Returns an accessible no-arg constructor for an externalizable class to be
     * initialized using a public no-argument constructor.
     *
     * @param cl the class to instantiate
     * @return A no-arg constructor for the class; returns {@code null} if
     *     the class does not implement {@link java.io.Externalizable}
     */
    // 如果clazz是Externalizable类型，返回其构造器
    public final Constructor<?> newConstructorForExternalization(Class<?> cl) {
        if (!Externalizable.class.isAssignableFrom(cl)) {
            return null;
        }
        try {
            Constructor<?> cons = cl.getConstructor();
            cons.setAccessible(true);
            return cons;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * Returns a direct MethodHandle for the {@code readObject} method on
     * a Serializable class.
     * The first argument of {@link MethodHandle#invoke} is the serializable
     * object and the second argument is the {@code ObjectInputStream} passed to
     * {@code readObject}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the {@code readObject} method of the class or
     *          {@code null} if the class does not have a {@code readObject} method
     */
    public final MethodHandle readObjectForSerialization(Class<?> cl) {
        return findReadWriteObjectForSerialization(cl, "readObject", ObjectInputStream.class);
    }

    /**
     * Returns a direct MethodHandle for the {@code readObjectNoData} method on
     * a Serializable class.
     * The first argument of {@link MethodHandle#invoke} is the serializable
     * object and the second argument is the {@code ObjectInputStream} passed to
     * {@code readObjectNoData}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the {@code readObjectNoData} method
     *          of the class or {@code null} if the class does not have a
     *          {@code readObjectNoData} method
     */
    public final MethodHandle readObjectNoDataForSerialization(Class<?> cl) {
        return findReadWriteObjectForSerialization(cl, "readObjectNoData", ObjectInputStream.class);
    }

    /**
     * Returns a direct MethodHandle for the {@code writeObject} method on
     * a Serializable class.
     * The first argument of {@link MethodHandle#invoke} is the serializable
     * object and the second argument is the {@code ObjectOutputStream} passed to
     * {@code writeObject}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the {@code writeObject} method of the class or
     *          {@code null} if the class does not have a {@code writeObject} method
     */
    public final MethodHandle writeObjectForSerialization(Class<?> cl) {
        return findReadWriteObjectForSerialization(cl, "writeObject", ObjectOutputStream.class);
    }

    private final MethodHandle findReadWriteObjectForSerialization(Class<?> cl,
                                                                   String methodName,
                                                                   Class<?> streamClass) {
        if (!Serializable.class.isAssignableFrom(cl)) {
            return null;
        }

        try {
            Method meth = cl.getDeclaredMethod(methodName, streamClass);
            int mods = meth.getModifiers();
            if (meth.getReturnType() != Void.TYPE ||
                    Modifier.isStatic(mods) ||
                    !Modifier.isPrivate(mods)) {
                return null;
            }
            meth.setAccessible(true);
            return MethodHandles.lookup().unreflect(meth);
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (IllegalAccessException ex1) {
            throw new InternalError("Error", ex1);
        }
    }

    /**
     * Returns a direct MethodHandle for the {@code readResolve} method on
     * a serializable class.
     * The single argument of {@link MethodHandle#invoke} is the serializable
     * object.
     *
     * @param cl the Serializable class
     * @return  a direct MethodHandle for the {@code readResolve} method of the class or
     *          {@code null} if the class does not have a {@code readResolve} method
     */
    public final MethodHandle readResolveForSerialization(Class<?> cl) {
        return getReplaceResolveForSerialization(cl, "readResolve");
    }

    /**
     * Returns a direct MethodHandle for the {@code writeReplace} method on
     * a serializable class.
     * The single argument of {@link MethodHandle#invoke} is the serializable
     * object.
     *
     * @param cl the Serializable class
     * @return  a direct MethodHandle for the {@code writeReplace} method of the class or
     *          {@code null} if the class does not have a {@code writeReplace} method
     */
    public final MethodHandle writeReplaceForSerialization(Class<?> cl) {
        return getReplaceResolveForSerialization(cl, "writeReplace");
    }

    /**
     * Returns a direct MethodHandle for the {@code writeReplace} method on
     * a serializable class.
     * The single argument of {@link MethodHandle#invoke} is the serializable
     * object.
     *
     * @param cl the Serializable class
     * @return  a direct MethodHandle for the {@code writeReplace} method of the class or
     *          {@code null} if the class does not have a {@code writeReplace} method
     */
    private MethodHandle getReplaceResolveForSerialization(Class<?> cl,
                                                           String methodName) {
        if (!Serializable.class.isAssignableFrom(cl)) {
            return null;
        }

        Class<?> defCl = cl;
        while (defCl != null) {
            try {
                Method m = defCl.getDeclaredMethod(methodName);
                if (m.getReturnType() != Object.class) {
                    return null;
                }
                int mods = m.getModifiers();
                if (Modifier.isStatic(mods) | Modifier.isAbstract(mods)) {
                    return null;
                } else if (Modifier.isPublic(mods) | Modifier.isProtected(mods)) {
                    // fall through
                } else if (Modifier.isPrivate(mods) && (cl != defCl)) {
                    return null;
                } else if (!packageEquals(cl, defCl)) {
                    return null;
                }
                try {
                    // Normal return
                    m.setAccessible(true);
                    return MethodHandles.lookup().unreflect(m);
                } catch (IllegalAccessException ex0) {
                    // setAccessible should prevent IAE
                    throw new InternalError("Error", ex0);
                }
            } catch (NoSuchMethodException ex) {
                defCl = defCl.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Returns true if the class has a static initializer.
     * The presence of a static initializer is used to compute the serialVersionUID.
     * @param cl a serializable classLook
     * @return {@code true} if the class has a static initializer,
     *          otherwise {@code false}
     */
    public final boolean hasStaticInitializerForSerialization(Class<?> cl) {
        Method m = hasStaticInitializerMethod;
        if (m == null) {
            try {
                m = ObjectStreamClass.class.getDeclaredMethod("hasStaticInitializer",
                        new Class<?>[]{Class.class});
                m.setAccessible(true);
                hasStaticInitializerMethod = m;
            } catch (NoSuchMethodException ex) {
                throw new InternalError("No such method hasStaticInitializer on "
                        + ObjectStreamClass.class, ex);
            }
        }
        try {
            return (Boolean) m.invoke(null, cl);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new InternalError("Exception invoking hasStaticInitializer", ex);
        }
    }

    /**
     * Returns a new OptionalDataException with {@code eof} set to {@code true}
     * or {@code false}.
     * @param bool the value of {@code eof} in the created OptionalDataException
     * @return  a new OptionalDataException
     */
    public final OptionalDataException newOptionalDataExceptionForSerialization(boolean bool) {
        try {
            // 返回OptionalDataException类中指定形参的构造器，但不包括父类中的构造器
            Constructor<OptionalDataException> boolCtor =
                    OptionalDataException.class.getDeclaredConstructor(Boolean.TYPE);
            boolCtor.setAccessible(true);
            return boolCtor.newInstance(bool);
        } catch (NoSuchMethodException | InstantiationException|
                IllegalAccessException|InvocationTargetException ex) {
            throw new InternalError("unable to create OptionalDataException", ex);
        }
    }

    //--------------------------------------------------------------------------
    //
    // Internals only below this point
    //
    // 返回某个方法被基于JNI的反射调用的次数
    static int inflationThreshold() {
        return inflationThreshold;
    }

    /** We have to defer full initialization of this class until after
        the static initializer is run since java.lang.reflect.Method's
        static initializer (more properly, that for
        java.lang.reflect.AccessibleObject) causes this class's to be
        run, before the system properties are set up. */
    // 检查初始化，只检查一次
    private static void checkInitted() {
        // 如果已经检查过初始化，直接返回
        if (initted) return;
        AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                public Void run() {
                    // Tests to ensure the system properties table is fully
                    // initialized. This is needed because reflection code is
                    // called very early in the initialization process (before
                    // command-line arguments have been parsed and therefore
                    // these user-settable properties installed.) We assume that
                    // if System.out is non-null then the System class has been
                    // fully initialized and that the bulk of the startup code
                    // has been run.

                    if (System.out == null) {
                        // java.lang.System not yet fully initialized
                        return null;
                    }

                    // 判断是否关闭"Inflation"技术，默认是开启的，反射操作会有一个从JNI调用过渡到纯Java调用的过程
                    String val = System.getProperty("sun.reflect.noInflation");
                    if (val != null && val.equals("true")) {
                        noInflation = true;// 关闭"Inflation"技术
                    }

                    // 尝试更新JNI调用阈值
                    val = System.getProperty("sun.reflect.inflationThreshold");
                    if (val != null) {
                        try {
                            inflationThreshold = Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            throw new RuntimeException("Unable to parse property sun.reflect.inflationThreshold", e);
                        }
                    }

                    // 已进行初始化检查
                    initted = true;
                    return null;
                }
            });
    }

    // 返回反射对象访问工具
    private static LangReflectAccess langReflectAccess() {
        if (langReflectAccess == null) {
            // Call a static method to get class java.lang.reflect.Modifier
            // initialized. Its static initializer will cause
            // setLangReflectAccess() to be called from the context of the
            // java.lang.reflect package.
            // 加载Modifier方法内的静态初始化块，使langReflectAccess对象被初始化
            Modifier.isPublic(Modifier.PUBLIC);
        }
        return langReflectAccess;
    }

    /**
     * Returns true if classes are defined in the classloader and same package, false
     * otherwise.
     * @param cl1 a class
     * @param cl2 another class
     * @returns true if the two classes are in the same classloader and package
     */
    // 判断两个类是否位于同一个包
    private static boolean packageEquals(Class<?> cl1, Class<?> cl2) {
        // 类加载器一致，包名一致
        return cl1.getClassLoader() == cl2.getClassLoader() &&
                Objects.equals(cl1.getPackage(), cl2.getPackage());
    }

}
