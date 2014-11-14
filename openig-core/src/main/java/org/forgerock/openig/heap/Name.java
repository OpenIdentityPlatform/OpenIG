/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.heap;

import static java.lang.String.*;
import static org.forgerock.util.Reject.*;

import org.forgerock.util.Reject;

/**
 * A Name uniquely identify an object within a hierarchy.
 * It is composed of a (possible {@code null} parent Name) and a leaf name (never {@code null}).
 * <p>
 * Consumers of that API are free to do their own Name rendering (they have access to the whole Name's chain
 * with {@link #getParent()} method) or use the pre-defined {@link #getFullyQualifiedName()}
 * and {@link #getScopedName()} methods. Theses methods use the plus ({@literal +}) character as separator.
 * <p>
 * The Name instances are immutable.
 */
public final class Name {

    /**
     * Builds a new Name using the given name parts.
     * They are ordered in descending order (ancestors first, leaf last)
     *
     * @param parts
     *         ordered fragments of the name
     * @return a new Name using the given name parts.
     */
    public static Name of(final String... parts) {
        Reject.ifTrue(parts.length == 0);
        Name name = null;
        for (String part : parts) {
            name = new Name(name, part);
        }
        return name;
    }

    /**
     * Builds a new Name for the given type.
     * The generated name will use the given type's short name  as leaf and will have no parent.
     *
     * @param type
     *         typ used to generate a name
     * @return a new Name for the given type
     */
    public static Name of(final Class<?> type) {
        return Name.of(type.getSimpleName());
    }

    private final Name parent;
    private final String leaf;

    /**
     * Builds a new hierarchical Name with the given {@code parent} Name and the given {@code leaf} leaf name. Notice
     * that the parent name can be {@code null} while the leaf part cannot be {@code null} (this is verified and a
     * NullPointerException is thrown if it is).
     *
     * @param parent
     *         parent Name
     * @param leaf
     *         leaf name (cannot be {@code null})
     */
    private Name(final Name parent, final String leaf) {
        this.parent = parent;
        this.leaf = checkNotNull(leaf);
    }

    /**
     * Returns the parent Name (can be {@code null}).
     *
     * @return the parent Name (can be {@code null}).
     */
    public Name getParent() {
        return parent;
    }

    /**
     * Returns the leaf name (cannot be {@code null}).
     *
     * @return the leaf name.
     */
    public String getLeaf() {
        return leaf;
    }

    /**
     * Creates a new Name, relative to this Name with the given leaf name.
     *
     * @param name
     *         relative leaf name
     * @return a new Name, relative to this Name.
     */
    public Name child(final String name) {
        return new Name(this, name);
    }

    /**
     * Returns this name with the last segment adapted to include the decorator name.
     * The last segment is changed to follow this pattern: {@literal @decorator[last-segment]}.
     *
     * @param decorator
     *         decorator name.
     * @return a new decorated name based on this name
     */
    public Name decorated(final String decorator) {
        return new Name(parent, format("@%s[%s]", decorator, leaf));
    }

    /**
     * Returns a String representation of this Name that includes the full Name hierarchy.
     * <p>
     * The following format has to be expected:
     * <pre>
     *     {@code
     *     (parent '+')* leaf
     *     }
     * </pre>
     * <p>
     * Examples:
     * <ul>
     *     <li>{@code LocalNameOnly}</li>
     *     <li>{@code /openig/config/config.json+_Router}</li>
     *     <li>{@code /openig/config/config.json+_Router+/openig/config/routes/openid-connect.json+OAuth2Filter}</li>
     * </ul>
     *
     * @return a String representation of this Name that includes the full Name hierarchy.
     */
    public String getFullyQualifiedName() {
        StringBuilder sb = new StringBuilder();
        if (parent != null) {
            sb.append(parent.getFullyQualifiedName());
            sb.append("+");
        }
        sb.append(leaf);
        return sb.toString();
    }

    /**
     * Returns a String representation of this Name that includes only the first parent and the leaf name.
     * <p>
     * The following format has to be expected:
     * <pre>
     *     {@code
     *     (parent '+')? leaf
     *     }
     * </pre>
     * <p>
     * Examples:
     * <ul>
     *     <li>{@code LocalNameOnly}</li>
     *     <li>{@code /openig/config/config.json+_Router}</li>
     * </ul>
     *
     * @return a String representation of this Name that includes only the first parent and the leaf name.
     */
    public String getScopedName() {
        StringBuilder sb = new StringBuilder();
        if (parent != null) {
            sb.append(parent.getLeaf());
            sb.append("+");
        }
        sb.append(leaf);
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Name)) {
            return false;
        }

        Name name = (Name) o;

        if (!leaf.equals(name.leaf)) {
            return false;
        }
        if (parent == null) {
            return name.parent == null;
        }
        return parent.equals(name.parent);
    }

    @Override
    public int hashCode() {
        int result = parent != null ? parent.hashCode() : 0;
        result = 31 * result + leaf.hashCode();
        return result;
    }

    /**
     * Returns the fully qualified name of this Name (format: {@literal (parent '+')* leaf}).
     *
     * @return the fully qualified name of this Name.
     * @see #getFullyQualifiedName()
     */
    @Override
    public String toString() {
        return getFullyQualifiedName();
    }
}
