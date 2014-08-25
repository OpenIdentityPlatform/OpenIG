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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.forgerock.http.Form;
import org.forgerock.http.URIUtil;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class URIUtilTest {

    @Test
    public void toURIandBack() throws Exception {
        URI u1 = URIUtil.create("a", "b", "c", 4, "/e%3D", "x=%3D", "g%3D");
        URI u2 = URIUtil.create(u1.getScheme(), u1.getRawUserInfo(), u1.getHost(),
                u1.getPort(), u1.getRawPath(), u1.getRawQuery(), u1.getRawFragment());
        assertThat(u1).isEqualTo(u2);
    }

    @Test
    public void rawParams() throws Exception {
        URI uri = URIUtil.create("http", "user", "example.com", 80, "/raw%3Dpath",
                "x=%3D", "frag%3Dment");
        assertThat(uri.toString()).isEqualTo("http://user@example.com:80/raw%3Dpath?x=%3D#frag%3Dment");
    }

    @Test
    public void rebase() throws Exception {
        URI uri = new URI("https://doot.doot.doo.org/all/good/things?come=to&those=who#breakdance");
        URI base = new URI("http://www.example.com/");
        URI rebased = URIUtil.rebase(uri, base);
        assertThat(rebased.toString()).isEqualTo("http://www.example.com/all/good/things?come=to&those=who#breakdance");
    }

    @Test
    public void testWithQuery() throws Exception {
        URI uri = new URI("https://doot.doot.doo.org/all/good/things?come=to&those=who#breakdance");
        Form form = new Form();
        form.add("goto", "http://some.url");
        form.add("state", "1234567890");
        URI withQuery = URIUtil.withQuery(uri, form);
        // Form uses LinkedHashMap so parameter order is guaranteed.
        assertThat(withQuery.toString()).isEqualTo(
                "https://doot.doot.doo.org/all/good/things?goto=http%3A%2F%2Fsome.url"
                        + "&state=1234567890#breakdance");
    }

    @Test
    public void testWithoutQueryAndFragment() throws Exception {
        URI uri = new URI("https://doot.doot.doo.org/all/good/things?come=to&those=who#breakdance");
        URI withoutQueryAndFragment = URIUtil.withoutQueryAndFragment(uri);
        assertThat(withoutQueryAndFragment.toString()).isEqualTo(
                "https://doot.doot.doo.org/all/good/things");
    }

}
