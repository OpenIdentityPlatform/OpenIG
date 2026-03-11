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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Layer-1 injection detector: deterministic, sub-millisecond regex matching.
 *
 * <h2>Detection pipeline</h2>
 * <ol>
 *   <li><strong>Unicode normalization</strong> – collapses homoglyphs and
 *       strips invisible/zero-width characters (U+200B, U+FEFF, RTL overrides).</li>
 *   <li><strong>Base64 decode-then-scan</strong> – detects obfuscated injection
 *       payloads embedded as Base64 strings.</li>
 *   <li><strong>Pattern matching</strong> – applies a compiled set of
 *       case-insensitive patterns covering all categories from the architecture:
 *       override instructions, role-play bypass, prompt exfiltration, etc.</li>
 * </ol>
 *
 * <p>Patterns are compiled once at construction time and are immutable,
 * making this class fully thread-safe without synchronization.
 */
public final class RegexDetector implements InjectionDetector {

    private static final Logger logger = LoggerFactory.getLogger(RegexDetector.class);

    /**
     * Regex to strip invisible / zero-width Unicode characters that attackers
     * insert to break pattern matching while leaving text visually unchanged.
     * Covers:
     *   U+00AD SOFT HYPHEN
     *   U+200B..U+200D ZERO WIDTH SPACE / NON-JOINER / JOINER
     *   U+200E..U+200F LEFT-TO-RIGHT / RIGHT-TO-LEFT MARK
     *   U+202A..U+202E directional formatting overrides
     *   U+2060 WORD JOINER
     *   U+FEFF ZERO WIDTH NO-BREAK SPACE (BOM)
     */
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
            "[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]");

    /** Loose Base64 block detector — captures plausible encoded payloads. */
    private static final Pattern BASE64_BLOCK = Pattern.compile(
            "(?:[A-Za-z0-9+/]{4}){4,}(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?");


    private final List<CompiledPattern> compiledPatterns;

    public RegexDetector(List<PatternEntry> patterns) {
        this.compiledPatterns = patterns.stream()
                .map(p -> new CompiledPattern(p.reason,
                        Pattern.compile(p.regex, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE)))
                .collect(Collectors.toList());
        logger.info("RegexDetector initialized with {} patterns", compiledPatterns.size());
    }
    @Override
    public DetectionResult scan(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return DetectionResult.clean();
        }

        String normalized = normalize(prompt);

        DetectionResult directResult = scanText(normalized);
        if (directResult.isInjection()) {
            logger.debug("Regex injection detected (direct): reason={}", directResult.getReason());
            return directResult;
        }

        DetectionResult b64Result = scanBase64Segments(normalized);
        if (b64Result.isInjection()) {
            logger.debug("Regex injection detected (base64): reason={}", b64Result.getReason());
            return b64Result;
        }

        return DetectionResult.clean();
    }

    static String normalize(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);

        String stripped = INVISIBLE_CHARS.matcher(nfd).replaceAll("");

        return stripped.replaceAll("\\s{2,}", " ").trim();
    }

    private DetectionResult scanText(String text) {
        for (CompiledPattern cp : compiledPatterns) {
            if (cp.pattern.matcher(text).find()) {
                return DetectionResult.injection(1.0, cp.reason, "regex");
            }
        }
        return DetectionResult.clean();
    }

    /**
     * Find all Base64-looking segments in the prompt, decode them, and scan
     * the decoded text. Ignore segments that fail to decode (not valid Base64).
     */
    private DetectionResult scanBase64Segments(String text) {
        var matcher = BASE64_BLOCK.matcher(text);
        while (matcher.find()) {
            String segment = matcher.group();
            try {
                byte[] decoded = Base64.getDecoder().decode(segment);
                String decodedText = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                // Only scan printable-looking decoded content
                if (isProbablyText(decodedText)) {
                    DetectionResult inner = scanText(normalize(decodedText));
                    if (inner.isInjection()) {
                        return DetectionResult.injection(1.0, "encoding_obfuscation:base64+" + inner.getReason(), "regex");
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Not valid Base64 or not valid UTF-8 — skip
            }
        }
        return DetectionResult.clean();
    }

    /**
     * Heuristic: decoded bytes are "probably text" if >80% are printable ASCII.
     * Avoids treating binary blobs as text.
     */
    private static boolean isProbablyText(String s) {
        if (s.length() < 8) return false;
        long printable = s.chars().filter(c -> c >= 0x20 && c < 0x7F).count();
        return (printable * 100L / s.length()) > 80;
    }

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /** Raw pattern entry before compilation (maps to injection-patterns.json). */
    public static class PatternEntry {
        final String reason;

        final String regex;

        @JsonCreator
        public PatternEntry(@JsonProperty("reason") String reason, @JsonProperty("regex") String regex) {
            this.reason = reason;
            this.regex = regex;
        }
    }

    private static class CompiledPattern {
        final String reason;

        final Pattern pattern;

        private CompiledPattern(String reason, Pattern pattern) {
            this.reason = reason;
            this.pattern = pattern;
        }

    }
}
