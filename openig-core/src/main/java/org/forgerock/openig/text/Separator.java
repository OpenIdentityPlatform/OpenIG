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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.text;

/**
 * A field separator specification, used to parse delimiter-separated values.
 */
public class Separator {

    /** The character used to separate values. */
    private final char character;

    /** The character used to quote string literals, or {@code -1} if none. */
    private final int quote;

    /** The character used to escape character literals, or {@code -1} if none. */
    private final int escape;

    /**
     * Constructs a new field separator specification.
     *
     * @param character the character used to separate values.
     * @param quote the character used to quote string literals, or {@code -1} if none.
     * @param escape the character used to escape character literals, or {@code -1} if none.
     */
    public Separator(char character, int quote, int escape) {
        this.character = character;
        this.quote = quote;
        this.escape = escape;
    }

    /**
     * Returns the character used to separate values.
     * @return the character used to separate values.
     */
    public char getCharacter() {
        return character;
    }

    /**
     * Returns the character used to quote string literals, or {@code -1} if none.
     * @return the character used to quote string literals, or {@code -1} if none.
     */
    public int getQuote() {
        return quote;
    }

    /**
     * Returns the character used to escape character literals, or {@code -1} if none.
     * @return the character used to escape character literals, or {@code -1} if none.
     */
    public int getEscape() {
        return escape;
    }

    /**
     * Indicates whether some other object is equal to this one.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the {@code obj} argument.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Separator)) {
            return false;
        }
        if (this == o) {
            return true;
        }
        return (this.character == ((Separator) o).character
                && this.quote == ((Separator) o).quote
                && this.escape == ((Separator) o).escape);
    }

    @Override
    public int hashCode() {
        return character ^ quote ^ escape;
    }
}
