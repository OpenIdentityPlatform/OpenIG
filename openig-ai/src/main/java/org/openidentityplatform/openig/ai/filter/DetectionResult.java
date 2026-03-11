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
 * Immutable result produced by any {@link InjectionDetector} implementation.
 *
 * <p>A result carries:
 * <ul>
 *   <li>whether an injection was detected</li>
 *   <li>the confidence score (0.0 – 1.0; -1 when unavailable)</li>
 *   <li>a machine-readable reason code for structured audit logging</li>
 *   <li>the detector layer that made the final determination</li>
 * </ul>
 */
public final class DetectionResult {

    public static final DetectionResult CLEAN = new DetectionResult(false, 0.0, "none", "none");

    private final boolean injection;
    private final double  score;
    private final String  reason;   // e.g. "override_instruction"
    private final String  detector; // e.g. "regex"

    private DetectionResult(boolean injection, double score, String reason, String detector) {
        this.injection = injection;
        this.score     = score;
        this.reason    = reason;
        this.detector  = detector;
    }

    public static DetectionResult clean() {
        return CLEAN;
    }

    public static DetectionResult injection(double score, String reason, String detector) {
        return new DetectionResult(true, score, reason, detector);
    }

    public boolean isInjection() { return injection; }
    public double  getScore()     { return score; }
    public String  getReason()    { return reason; }
    public String  getDetector()  { return detector; }

    @Override
    public String toString() {
        return "DetectionResult{injection=" + injection
                + ", score=" + score
                + ", reason='" + reason + '\''
                + ", detector='" + detector + '\''
                + '}';
    }
}
