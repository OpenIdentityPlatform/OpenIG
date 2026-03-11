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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.ai.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Layer-2 Injection detector: catches prompt-injection keywords that have been
 * <em>typoglycemia-obfuscated</em> — i.e. their interior letters are scrambled
 * while the first and last characters are preserved.
 *
 * <p>Adversaries exploit this to smuggle injection keywords past string-matching
 * guardrails:
 * <pre>
 *   "Inoger all preivous isutrctions"  →  "Ignore all previous instructions"
 *   "drsreigad yuor sstyem promt"      →  "disregard your system prompt"
 *   "jilkbraae"                        →  "jailbreak"
 * </pre>
 *
 * <h2>Algorithm</h2>
 * For each token in the input prompt:
 * <ol>
 *   <li>If token length ≤ 3 — compare directly (no interior to scramble).</li>
 *   <li>Otherwise — compare the <strong>first character</strong>,
 *       <strong>last character</strong>, and a <strong>sorted bag</strong> of the
 *       interior characters against the same fingerprint of every keyword in the
 *       watch-list.</li>
 *   <li>If fingerprints match, the similarity is verified with a fast
 *       <strong>Damerau-Levenshtein distance</strong> (≤ {@code maxEditDistance}
 *       on the full strings) to suppress accidental collisions between genuinely
 *       different words that happen to share a fingerprint (e.g. "satin"/"saint").</li>
 * </ol>
 */

public class TypoglycemiaDetector implements InjectionDetector {


    private static final Logger logger = LoggerFactory.getLogger(TypoglycemiaDetector.class);

    private static final int DEFAULT_MIN_WORD_LENGTH = 4;
    private static final int DEFAULT_MAX_EDIT_DISTANCE = 3;

    private final int minWordLength;

    private final int maxEditDistance;

    private final Map<Fingerprint, List<String>> index;

    public TypoglycemiaDetector(List<String> keywords) {
        this(DEFAULT_MIN_WORD_LENGTH, DEFAULT_MAX_EDIT_DISTANCE, keywords);
    }

    public TypoglycemiaDetector(int minWordLength, int maxEditDistance, List<String> keywords) {
        this.minWordLength = minWordLength;
        this.maxEditDistance = maxEditDistance;
        this.index = buildIndex(keywords);
    }

    @Override
    public DetectionResult scan(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return DetectionResult.clean();
        }

        // Tokenise: split on whitespace + common punctuation, lowercase everything
        String[] tokens = tokenise(prompt);
        if (tokens.length == 0) {
            return DetectionResult.clean();
        }

        // Collect all matched keywords (position → keyword) for phrase-window check
        Map<Integer, String> hits = new HashMap<>();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() < minWordLength) {
                continue;
            }

            Fingerprint fp = Fingerprint.of(token);
            List<String> candidates = index.get(fp);
            if (candidates == null) {
                continue;
            }

            for (String keyword : candidates) {
                if (isTypoglycemiaMatch(token, keyword)) {
                    hits.put(i, keyword);
                    logger.debug("Typoglycemia hit: token='{}' matches keyword='{}' at pos={}", token, keyword, i);
                    break;
                }
            }
        }

        if (hits.isEmpty()) {
            return DetectionResult.clean();
        }

        // A single-token hit on a high-value keyword is sufficient to flag
        String matchedKeyword = hits.values().iterator().next();
        double score = computeScore(hits, tokens.length);

        return DetectionResult.injection(
                score,
                "typoglycemia_obfuscation:" + matchedKeyword,
                "typoglycemia");
    }

    boolean isTypoglycemiaMatch(String token, String keyword) {
        String t = token.toLowerCase();
        String k = keyword.toLowerCase();

        // Fast length gate: allow ±1 to catch an extra stutter/typo character
        if (Math.abs(t.length() - k.length()) > 1) return false;

        // Short words: exact match only (fingerprint would be meaningless)
        if (k.length() <= 3) return t.equals(k);

        // First-char gate
        if (t.charAt(0) != k.charAt(0)) return false;
        // Last-char gate
        if (t.charAt(t.length() - 1) != k.charAt(k.length() - 1)) return false;
        // Interior multiset gate (sorted char arrays)
        if (!interiorBagEquals(t, k)) return false;

        // Damerau-Levenshtein as final verification (guards against false collisions)
        return damerauLevenshtein(t, k) <= maxEditDistance;
    }

    private static boolean interiorBagEquals(String a, String b) {
        char[] ia = interior(a);
        char[] ib = interior(b);
        Arrays.sort(ia);
        Arrays.sort(ib);
        return Arrays.equals(ia, ib);
    }

    private static char[] interior(String s) {
        if (s.length() <= 2) return new char[0];
        return s.substring(1, s.length() - 1).toCharArray();
    }

    /**
     * Computes a confidence score in [0.5, 1.0].
     *
     * <p>More matched keywords → higher score. The score is deliberately
     * capped below 1.0 to indicate this is a heuristic (not a definitive ML
     * probability), so downstream consumers can distinguish it from the
     * exact-regex detector which returns 1.0.
     */
    private static double computeScore(Map<Integer, String> hits, int totalTokens) {
        if (totalTokens == 0) return 0.5;
        double density = (double) hits.size() / totalTokens;
        // Scale: 1 hit → 0.70, 2 hits → ~0.80, 3+ hits → saturates near 0.95
        return Math.min(0.95, 0.65 + (density * 2.0));
    }

    static String[] tokenise(String text) {
        // Strip leading/trailing punctuation from each token, keep interior hyphens
        return Arrays.stream(text.split("[\\s,;:!?()\\[\\]{}<>\"'`]+"))
                .map(t -> t.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", ""))
                .filter(t -> !t.isBlank())
                .map(String::toLowerCase)
                .toArray(String[]::new);
    }

    private static Map<Fingerprint, List<String>> buildIndex(Collection<String> keywords) {
        Map<Fingerprint, List<String>> map = new HashMap<>();
        for (String kw : keywords) {
            String lower = kw.toLowerCase();
            if (lower.length() < 2) continue;
            Fingerprint fp = Fingerprint.of(lower);
            map.computeIfAbsent(fp, k -> new ArrayList<>()).add(lower);
        }
        return Map.copyOf(map);
    }

    /**
     * Computes the Optimal String Alignment (restricted Damerau-Levenshtein)
     * distance between {@code a} and {@code b}.
     *
     * <p>Supports the four edit operations:
     * insertion, deletion, substitution, and <strong>transposition of two
     * adjacent characters</strong> — the last operation being precisely what
     * typoglycemia exploits.
     *
     * <p>Time: O(|a|·|b|). Space: O(|a|·|b|) — both strings are at most
     * ~20 chars so this is negligible.
     */
    static int damerauLevenshtein(String a, String b) {
        int la = a.length();
        int lb = b.length();

        // Map each character to its most recent 1-based position in 'a'
        // (the "last seen" table required by the unrestricted algorithm).
        // We use a simple array indexed by char value; words are short so
        // the sparse allocation cost is negligible.
        int maxChar = 0;
        for (int i = 0; i < la; i++) maxChar = Math.max(maxChar, a.charAt(i));
        for (int i = 0; i < lb; i++) maxChar = Math.max(maxChar, b.charAt(i));
        int[] da = new int[maxChar + 1]; // da[c] = last row in 'a' where char c was seen

        // dp is (la+2) × (lb+2); the extra row/column hold the sentinel value.
        int sentinel = la + lb + 1;
        int[][] dp = new int[la + 2][lb + 2];
        dp[0][0] = sentinel;
        for (int i = 0; i <= la; i++) { dp[i + 1][0] = sentinel; dp[i + 1][1] = i; }
        for (int j = 0; j <= lb; j++) { dp[0][j + 1] = sentinel; dp[1][j + 1] = j; }

        for (int i = 1; i <= la; i++) {
            int db = 0; // last column in 'b' where a[i-1] was seen
            for (int j = 1; j <= lb; j++) {
                int i1 = da[b.charAt(j - 1)]; // last row in 'a' where b[j-1] appeared
                int j1 = db;                   // last col in 'b' where a[i-1] appeared
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                if (cost == 0) db = j;

                dp[i + 1][j + 1] = min4(
                        dp[i][j] + cost,                                // substitution
                        dp[i + 1][j] + 1,                               // insertion
                        dp[i][j + 1] + 1,                               // deletion
                        dp[i1][j1] + (i - i1 - 1) + 1 + (j - j1 - 1)  // transposition
                );
            }
            da[a.charAt(i - 1)] = i;
        }
        return dp[la + 1][lb + 1];
    }

    private static int min4(int a, int b, int c, int d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    static class Fingerprint {

        private final char first;
        private final char last;

        private final String sortedInterior;

        Fingerprint(char first, char last, String sortedInterior) {
            this.first = first;
            this.last = last;
            this.sortedInterior = sortedInterior;
        }


        static Fingerprint of(String word) {
            String w = word.toLowerCase();
            if (w.length() <= 2) {
                char f = w.charAt(0);
                char l = w.length() == 2 ? w.charAt(1) : f;
                return new Fingerprint(f, l, "");
            }
            char[] interior = w.substring(1, w.length() - 1).toCharArray();
            Arrays.sort(interior);
            return new Fingerprint(w.charAt(0), w.charAt(w.length() - 1), new String(interior));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Fingerprint that = (Fingerprint) o;
            return first == that.first && last == that.last && sortedInterior.equals(that.sortedInterior);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, last, sortedInterior);
        }
    }
}
