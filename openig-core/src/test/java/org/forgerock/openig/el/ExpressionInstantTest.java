package org.forgerock.openig.el;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.el.Bindings.bindings;

public class ExpressionInstantTest {
    private Bindings bindings;

    @BeforeMethod
    public void beforeMethod() {
        bindings = bindings();
    }
    @Test
    public void testExpressionInstant() throws ExpressionException {
        ExpressionInstant instant = Expression.valueOf("${now}",
                ExpressionInstant.class).eval(bindings);
        assertThat(instant).isNotNull();
        assertThat(instant.getEpochMillis()).isGreaterThan(0);
    }

    @Test
    public void testExpressionInstantAddDays() throws ExpressionException {
        Long seconds = Expression.valueOf("${now.plusMinutes(30).epochSeconds}",
                Long.class).eval(bindings);
        assertThat(seconds).isGreaterThan(0);
    }
}