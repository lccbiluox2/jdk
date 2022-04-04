/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/*
 * A useful utility class that will enumerate over an array of
 * enumerations.
 */
/*
 * 复合枚举类型
 *
 * 其内部定义了Enumeration数组字段，其自身又是Enumeration的子类
 * 这意味着该类型可以嵌套存储Enumeration类型
 */
public class CompoundEnumeration<E> implements Enumeration<E> {
    private Enumeration<E>[] enums;
    // 游标，用于遍历元素
    private int index = 0;

    public CompoundEnumeration(Enumeration<E>[] enums) {
        this.enums = enums;
    }

    private boolean next() {
        while (index < enums.length) {
            if (enums[index] != null && enums[index].hasMoreElements()) {
                return true;
            }
            index++;
        }
        return false;
    }

    // 是否存在未遍历的元素
    public boolean hasMoreElements() {
        return next();
    }

    // 返回下一个元素
    public E nextElement() {
        if (!next()) {
            throw new NoSuchElementException();
        }
        return enums[index].nextElement();
    }
}
