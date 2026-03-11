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

import java.util.List;
import java.util.Objects;

/**
 * Composite injection detector that chains multiple {@link InjectionDetector}
 * implementations in priority order, short-circuiting on the first positive.
 */
public final class CompositeDetector implements InjectionDetector {

    private static final Logger logger = LoggerFactory.getLogger(CompositeDetector.class);

    private final List<InjectionDetector> detectors;

    public CompositeDetector(InjectionDetector... detectors) {
        this(List.of(detectors));
    }

    public CompositeDetector(List<InjectionDetector> detectors) {
        Objects.requireNonNull(detectors, "detectors must not be null");
        if (detectors.isEmpty()) {
            throw new IllegalArgumentException("At least one detector is required");
        }
        this.detectors = List.copyOf(detectors);
    }
    @Override
    public DetectionResult scan(String prompt) {
        for (InjectionDetector detector : detectors) {
            DetectionResult result = detector.scan(prompt);
            if (result.isInjection()) {
                logger.info("Injection confirmed by detector={} reason={} score={}",
                        result.getDetector(), result.getReason(), result.getScore());
                return result;
            }
        }
        return DetectionResult.clean();
    }

    @Override
    public void destroy() {
        detectors.forEach(d -> {
            try {
                d.destroy();
            } catch (Exception e) {
                logger.warn("Error destroying detector {}: {}", d.getClass().getSimpleName(), e.getMessage());
            }
        });
    }
}
