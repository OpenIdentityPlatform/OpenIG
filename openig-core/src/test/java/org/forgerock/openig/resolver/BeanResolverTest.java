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

package org.forgerock.openig.resolver;

import static java.lang.Boolean.*;
import static org.assertj.core.api.Assertions.*;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BeanResolverTest {

    @Test
    public void shouldReturnValueOfProperty() throws Exception {
        BeanResolver resolver = new BeanResolver();
        final JavaBean bean = new JavaBean("OpenIG", true, 42);

        assertThat(resolver.get(bean, "name")).isEqualTo("OpenIG");
        assertThat(resolver.get(bean, "bool")).isEqualTo(TRUE);
        assertThat(resolver.get(bean, "number")).isEqualTo(42);
    }

    @Test
    public void shouldReturnUnresolvedForMissingProperty() throws Exception {
        BeanResolver resolver = new BeanResolver();
        final JavaBean bean = new JavaBean("OpenIG", true, 42);

        assertThat(resolver.get(bean, "missing")).isEqualTo(Resolver.UNRESOLVED);
    }

    @Test
    public void shouldSetValueOfProperty() throws Exception {
        BeanResolver resolver = new BeanResolver();
        final JavaBean bean = new JavaBean("OpenIG", true, 42);

        resolver.put(bean, "name", "ForgeRock");
        assertThat(bean.getName()).isEqualTo("ForgeRock");

        resolver.put(bean, "bool", FALSE);
        assertThat(bean.isBool()).isFalse();
    }

    @Test
    public void shouldNotSetValueOfPropertyBecauseNotWritable() throws Exception {
        BeanResolver resolver = new BeanResolver();
        final JavaBean bean = new JavaBean("OpenIG", true, 42);

        resolver.put(bean, "number", 404);
        assertThat(bean.getNumber()).isEqualTo(42);
    }

    private static class JavaBean {
        private String name;
        private boolean bool;
        private final int number;

        public JavaBean(final String name, final boolean bool, final int number) {
            this.name = name;
            this.bool = bool;
            this.number = number;
        }

        public String getName() {
            return name;
        }

        @SuppressWarnings("unused")
        public void setName(final String name) {
            this.name = name;
        }

        public boolean isBool() {
            return bool;
        }

        @SuppressWarnings("unused")
        public void setBool(final boolean bool) {
            this.bool = bool;
        }

        public int getNumber() {
            return number;
        }
    }

}
