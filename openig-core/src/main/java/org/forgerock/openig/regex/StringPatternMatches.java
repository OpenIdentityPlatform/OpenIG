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
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.regex;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Iterates through multiple regular expression matches within a character sequence.
 */
public class StringPatternMatches {

    /** The patterns to match. */
    private final Pattern[] patterns;

    /** Matches found, with {@literal null} elements indicating no match for pattern. */
    private final Matcher[] matchers;

    /** The character sequence to search. */
    private final CharSequence input;

    /** Should patterns be discarded after they yield a match. */
    private boolean discard;

    /**
     * Constructs a new string pattern match iterator. If {@code discard} is {@code true},
     * then a pattern is discarded after it is first matched.
     *
     * @param input the character sequence to match regular expression patterns against.
     * @param patterns a collection of regular expression patterns to match.
     * @param discard indicates patterns be discarded after they yield a match.
     */
    public StringPatternMatches(final CharSequence input, final Collection<Pattern> patterns, final boolean discard) {
        this.input = input;
        this.patterns = patterns.toArray(new Pattern[patterns.size()]);
        this.matchers = new Matcher[this.patterns.length];
        for (int n = 0; n < this.patterns.length; n++) {
            // null pattern means already discarded; simply ignore
            if (this.patterns[n] != null) {
                Matcher matcher = this.patterns[n].matcher(input);
                // matchers without any matches are not used
                if (matcher.find()) {
                    matchers[n] = matcher;
                }
            }
        }
        this.discard = discard;
    }

    /**
     * Returns the next match from the character sequence. Matches are returned in the order
     * they are encountered in the character sequence, then by the order they are expressed in
     * the supplied patterns collection.
     *
     * @return the next {@link java.util.regex.Matcher} from the character sequence.
     * @throws NoSuchElementException if the reader has no more matches.
     */
    public Matcher next() {
        // index of matcher with smallest start index
        int matcherIndex = -1;
        // smallest start index encountered
        int charIndex = Integer.MAX_VALUE;
        // find first matcher with smallest start index
        for (int n = 0; n < matchers.length; n++) {
            if (matchers[n] != null) {
                int start = matchers[n].start();
                if (start < charIndex) {
                    charIndex = start;
                    matcherIndex = n;
                }
            }
        }
        if (matcherIndex == -1) {
            // no active matchers found
            throw new NoSuchElementException();
        }
        // save match to return
        Matcher next = matchers[matcherIndex];
        // reset matcher and set for next match (if applicable)
        matchers[matcherIndex] = (discard ? null : patterns[matcherIndex].matcher(input));
        if (matchers[matcherIndex] != null
                && (charIndex == input.length() - 1 || !matchers[matcherIndex].find(charIndex + 1))) {
            // matchers without any matches are not relevant
            matchers[matcherIndex] = null;
        }
        return next;
    }

    /**
     * Returns {@code true} if the character sequence has more matches.
     * @return {@code true} if the character sequence has more matches.
     */
    public boolean hasNext() {
        for (Matcher matcher : matchers) {
            if (matcher != null) {
                // any existing matcher means another match exists
                return true;
            }
        }
        return false;
    }
}
