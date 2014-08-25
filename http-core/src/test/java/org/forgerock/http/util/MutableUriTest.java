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

package org.forgerock.http.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.MutableUri.uri;

import java.net.URI;

import org.forgerock.http.MutableUri;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MutableUriTest {

    // Test for un-encoded values
    // ----------------------------------------------------------

    @Test
    public void shouldUpdateScheme() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setScheme("https");
        assertThat(uri).isEqualTo(uri("https://www.example.com"));
        assertThat(uri.getScheme()).isEqualTo("https");
    }

    @Test
    public void shouldUpdateHost() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setHost("openig.forgerock.org");
        assertThat(uri).isEqualTo(uri("http://openig.forgerock.org"));
        assertThat(uri.getHost()).isEqualTo("openig.forgerock.org");
    }

    @Test
    public void shouldUpdatePort() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setPort(8080);
        assertThat(uri).isEqualTo(uri("http://www.example.com:8080"));
        assertThat(uri.getPort()).isEqualTo(8080);
    }

    @Test
    public void shouldRemovePort() throws Exception {
        MutableUri uri = uri("http://www.example.com:8080");
        uri.setPort(-1);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
        assertThat(uri.getPort()).isEqualTo(-1);
    }

    @Test
    public void shouldAddUserInfo() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setUserInfo("bjensen:s3cr3t");
        assertThat(uri).isEqualTo(uri("http://bjensen:s3cr3t@www.example.com"));
        assertThat(uri.getUserInfo()).isEqualTo("bjensen:s3cr3t");
    }

    @Test
    public void shouldModifyUserInfo() throws Exception {
        MutableUri uri = uri("http://bjensen:s3cr3t@www.example.com");
        uri.setUserInfo("guillaume:password");
        assertThat(uri).isEqualTo(uri("http://guillaume:password@www.example.com"));
        assertThat(uri.getUserInfo()).isEqualTo("guillaume:password");
    }

    @Test
    public void shouldRemoveUserInfo() throws Exception {
        MutableUri uri = uri("http://bjensen:s3cr3t@www.example.com");
        uri.setUserInfo(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
        assertThat(uri.getUserInfo()).isNull();
    }

    @Test
    public void shouldAddPath() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setPath("/openig space/fr");
        assertThat(uri).isEqualTo(uri("http://www.example.com/openig%20space/fr"));
        assertThat(uri.getPath()).isEqualTo("/openig space/fr");
    }

    @Test
    public void shouldModifyPath() throws Exception {
        MutableUri uri = uri("http://www.example.com/openig");
        uri.setPath("/forgerock");
        assertThat(uri).isEqualTo(uri("http://www.example.com/forgerock"));
        assertThat(uri.getPath()).isEqualTo("/forgerock");
    }

    @Test
    public void shouldRemovePath() throws Exception {
        MutableUri uri = uri("http://www.example.com/openig");
        uri.setPath(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
        // Note: because we rebuild the full URL at each modification, it seems the underlying URI find an empty path
        // instead of a null pah
        assertThat(uri.getPath()).isNullOrEmpty();
    }

    @Test
    public void shouldAddQuery() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setQuery("one=two three");
        assertThat(uri).isEqualTo(uri("http://www.example.com?one=two%20three"));
        assertThat(uri.getQuery()).isEqualTo("one=two three");
    }

    @Test
    public void shouldModifyQuery() throws Exception {
        MutableUri uri = uri("http://www.example.com?one=two%20three");
        uri.setQuery("a=b");
        assertThat(uri).isEqualTo(uri("http://www.example.com?a=b"));
        assertThat(uri.getQuery()).isEqualTo("a=b");
    }

    @Test
    public void shouldRemoveQuery() throws Exception {
        MutableUri uri = uri("http://www.example.com?a=b");
        uri.setQuery(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
        assertThat(uri.getQuery()).isNull();
    }

    @Test
    public void shouldAddFragment() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setFragment("marker one");
        assertThat(uri).isEqualTo(uri("http://www.example.com#marker%20one"));
        assertThat(uri.getFragment()).isEqualTo("marker one");
    }

    @Test
    public void shouldModifyFragment() throws Exception {
        MutableUri uri = uri("http://www.example.com#other");
        uri.setFragment("marker one");
        assertThat(uri).isEqualTo(uri("http://www.example.com#marker%20one"));
        assertThat(uri.getFragment()).isEqualTo("marker one");
    }

    @Test
    public void shouldRemoveFragment() throws Exception {
        MutableUri uri = uri("http://www.example.com#marker");
        uri.setFragment(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
        assertThat(uri.getFragment()).isNull();
    }

    // Test for encoded values (URL encoded)
    // ----------------------------------------------------------

    @Test
    public void shouldAddRawUserInfo() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setRawUserInfo("bjen%20sen:s3c%3Dr3t");
        assertThat(uri).isEqualTo(uri("http://bjen%20sen:s3c%3Dr3t@www.example.com"));
    }

    @Test
    public void shouldRemoveRawUserInfo() throws Exception {
        MutableUri uri = uri("http://bjensen:s3cr3t@www.example.com");
        uri.setRawUserInfo(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
    }

    @Test
    public void shouldAddRawPath() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setRawPath("/openig%20space/fr");
        assertThat(uri).isEqualTo(uri("http://www.example.com/openig%20space/fr"));
    }

    @Test
    public void shouldRemoveRawPath() throws Exception {
        MutableUri uri = uri("http://www.example.com/openig");
        uri.setRawPath(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
    }

    @Test
    public void shouldAddRawQuery() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setRawQuery("one=two%20three");
        assertThat(uri).isEqualTo(uri("http://www.example.com?one=two%20three"));
    }

    @Test
    public void shouldRemoveRawQuery() throws Exception {
        MutableUri uri = uri("http://www.example.com?a=b");
        uri.setRawQuery(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
    }

    @Test
    public void shouldAddRawFragment() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setRawFragment("marker%20one");
        assertThat(uri).isEqualTo(uri("http://www.example.com#marker%20one"));
    }

    @Test
    public void shouldRemoveRawFragment() throws Exception {
        MutableUri uri = uri("http://www.example.com#marker");
        uri.setRawFragment(null);
        assertThat(uri).isEqualTo(uri("http://www.example.com"));
    }

    // Test for getter (raw and normal)
    // ----------------------------------------------------------

    @Test
    public void testGetters() throws Exception {
        MutableUri uri = uri("http://my%20user:pass%3Fword@www.example.com:80/path%20space/fr?x=%3D#marker%20one");
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getUserInfo()).isEqualTo("my user:pass?word");
        assertThat(uri.getRawUserInfo()).isEqualTo("my%20user:pass%3Fword");
        assertThat(uri.getHost()).isEqualTo("www.example.com");
        assertThat(uri.getPort()).isEqualTo(80);
        assertThat(uri.getAuthority()).isEqualTo("my user:pass?word@www.example.com:80");
        assertThat(uri.getRawAuthority()).isEqualTo("my%20user:pass%3Fword@www.example.com:80");
        assertThat(uri.getPath()).isEqualTo("/path space/fr");
        assertThat(uri.getRawPath()).isEqualTo("/path%20space/fr");
        assertThat(uri.getQuery()).isEqualTo("x==");
        assertThat(uri.getRawQuery()).isEqualTo("x=%3D");
        assertThat(uri.getFragment()).isEqualTo("marker one");
        assertThat(uri.getRawFragment()).isEqualTo("marker%20one");
    }

    // Other methods
    // ---------------------------------------

    @Test
    public void shouldRebaseSchemeHostAndPort() throws Exception {
        MutableUri uri = uri("https://doot.doot.doo.org/all/good/things?come=to&those=who#breakdance");
        uri.rebase(new URI("http://www.example.com:8080"));
        assertThat(uri.toString())
                .isEqualTo("http://www.example.com:8080/all/good/things?come=to&those=who#breakdance");
    }

    @Test
    public void shouldRebaseSchemeHostAndPortAndIgnoringOtherElements() throws Exception {
        MutableUri uri = uri("https://doot.doot.doo.org/all/good/things?come=to&those=who#breakdance");
        uri.rebase(new URI("http://www.example.com:8080/mypath?a=b#marker"));
        assertThat(uri.toString())
                .isEqualTo("http://www.example.com:8080/all/good/things?come=to&those=who#breakdance");
    }

    // Tests for correct encoding of clear values with reserved characters in URI components
    // -------------------------------------------------------------------------------------------
    // Note: It appears that when we re-create a URI with decoded values that contains reserved
    // characters ('=', '?', ...) the URI inner parser doesn't re-encode properly the reserved char
    // for some components.
    // -------------------------------------------------------------------------------------------

    @Test(enabled = false)
    public void shouldAcceptReservedCharactersInSetQuery() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setQuery("x=?");
        assertThat(uri.getQuery()).isEqualTo("x=?");
        assertThat(uri.getRawQuery()).isEqualTo("x=%3F");
    }

    @Test(enabled = false)
    public void shouldAcceptReservedCharactersInSetPath() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setPath("/=");
        assertThat(uri.getPath()).isEqualTo("/=");
        assertThat(uri.getRawPath()).isEqualTo("/%3F");
    }

    @Test(enabled = false)
    public void shouldAcceptReservedCharactersInSetFragment() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setFragment("marker=");
        assertThat(uri.getFragment()).isEqualTo("marker=");
        assertThat(uri.getRawFragment()).isEqualTo("marker%3F");
    }

    @Test
    public void shouldAcceptReservedCharactersInSetUserInfo() throws Exception {
        MutableUri uri = uri("http://www.example.com");
        uri.setUserInfo("test:pass?word");
        assertThat(uri.getUserInfo()).isEqualTo("test:pass?word");
        assertThat(uri.getRawUserInfo()).isEqualTo("test:pass%3Fword");
    }
}
