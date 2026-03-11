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
