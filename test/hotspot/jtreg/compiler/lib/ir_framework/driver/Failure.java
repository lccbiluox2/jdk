/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.ir_framework.driver;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of a failure found when applying IR matching of an IR rule. This class represents an IR matching failure
 * of a regex of an attribute of an IR rule.
 *
 * @see IRRuleMatchResult
 * @see IRRule
 */
abstract class Failure {
    // Dedicated empty list to use to indicate that there was no failure.
    public static final List<FailOnFailure> NO_FAILURE = new ArrayList<>();

    protected final String nodeRegex;
    protected final int nodeId;
    protected final List<String> matches;

    public Failure(String nodeRegex, int nodeId, List<String> matches) {
        this.nodeRegex = nodeRegex;
        this.nodeId = nodeId;
        this.matches = matches;
    }

    public int getMatchesCount() {
        return matches.size();
    }

    abstract public String getFormattedFailureMessage();

    protected String getRegexLine() {
        return "       * Regex " + nodeId + ": " + nodeRegex + System.lineSeparator();
    }

    protected String getMatchedNodesBlock() {
        return getMatchedNodesHeader() + getMatchesNodeLines();
    }

    protected String getMatchedNodesHeader() {
        int matchCount = matches.size();
        return "" + getMatchedNodesWhiteSpace() + "- " + getMatchedPrefix() + " node"
               + (matchCount != 1 ? "s (" + matchCount + ")" : "") + ":" + System.lineSeparator();
    }

    abstract protected String getMatchedPrefix();

    protected String getMatchedNodesWhiteSpace() {
        return "         ";
    }

    protected String getMatchesNodeLines() {
        StringBuilder builder = new StringBuilder();
        matches.forEach(match -> builder.append("             ").append(match).append(System.lineSeparator()));
        return builder.toString();
    }


}
