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

/**
 * Strategy interface for prompt-injection detection.
 *
 * <p>Implementations must be <strong>thread-safe</strong>: a single detector
 * instance is shared across all concurrent requests.
 *
 * <p>Known implementations:
 * <ul>
 *   <li>{@link RegexDetector}          – fast, deterministic regex pre-filter</li>
 *   <li>{@link TypoglycemiaDetector}   – fast, catches injection keywords whose interior
 *      letters have been transposed to evade exactmatching</li>
 *   <li>{@link CompositeDetector}      – chains the above with short-circuit logic</li>
 * </ul>
 */
public interface InjectionDetector {

    /**
     * Scan {@code prompt} for injection signals.
     *
     * @param prompt         the normalized prompt text extracted from the LLM request body
     * @return               a {@link DetectionResult}; never {@code null}
     */
    DetectionResult scan(String prompt);

    default void destroy() {}
}
