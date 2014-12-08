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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.forgerock.json.fluent.JsonValue.field;
import static org.forgerock.json.fluent.JsonValue.json;
import static org.forgerock.json.fluent.JsonValue.object;
import static org.mockito.Mockito.when;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for the OAuth2Session class..
 */
@SuppressWarnings("javadoc")
public class OAuth2SessionTest {
    // @formatter:off
    // From OIDC core spec.
    private final String idToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjFlOWdkazcifQ.ewogImlzc"
            + "yI6ICJodHRwOi8vc2VydmVyLmV4YW1wbGUuY29tIiwKICJzdWIiOiAiMjQ4Mjg5"
            + "NzYxMDAxIiwKICJhdWQiOiAiczZCaGRSa3F0MyIsCiAibm9uY2UiOiAibi0wUzZ"
            + "fV3pBMk1qIiwKICJleHAiOiAxMzExMjgxOTcwLAogImlhdCI6IDEzMTEyODA5Nz"
            + "AKfQ.ggW8hZ1EuVLuxNuuIJKX_V8a_OMXzR0EHR9R6jgdqrOOF4daGU96Sr_P6q"
            + "Jp6IcmD3HP99Obi1PRs-cwh3LO-p146waJ8IhehcwL7F09JdijmBqkvPeB2T9CJ"
            + "NqeGpe-gccMg4vfKjkM8FcGvnzZUN4_KSP0aAp1tOJ1zZwgjxqGByKHiOtX7Tpd"
            + "QyHE5lcMiKPXfEIQILVq0pc_E2DzL7emopWoaoZTF_m0_N0YzFC6g6EJbOEoRoS"
            + "K5hoDalrcvRYLSrQAZZKflyuVCyixEoV9GfNQC3_osjzw2PAithfubEEBLuVVk4"
            + "XUVrWOLrLl0nx7RkKU8NXNHq-rvKMzqg";
    // @formatter:on

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testStateNew() {
        OAuth2Session session = OAuth2Session.stateNew(time);
        assertThat(session.getAccessToken()).isNull();
        assertThat(session.getAccessTokenResponse()).isEmpty();
        assertThat(session.getAuthorizationRequestNonce()).isNull();
        assertThat(session.getClientEndpoint()).isNull();
        assertThat(session.getExpiresIn()).isNull();
        assertThat(session.getIdToken()).isNull();
        assertThat(session.getProviderName()).isNull();
        assertThat(session.getRefreshToken()).isNull();
        assertThat(session.getScopes()).isEmpty();
        assertThat(session.getTokenType()).isNull();
        assertThat(session.isAuthorized()).isFalse();
        assertThat(session.isAuthorizing()).isFalse();
        assertThat(session.toJson().asMap()).isEmpty();
    }

    @Test
    public void testStateAuthorizing() throws Exception {
        // @formatter:off
        OAuth2Session session = OAuth2Session.stateNew(time)
                                             .stateAuthorizing("provider",
                                                               "endpoint",
                                                               "nonce",
                                                               asList("scope1", "scope2"));
        // @formatter:on
        testStateAuthorizing0(session);
        session = OAuth2Session.fromJson(time, session.toJson());
        testStateAuthorizing0(session);
    }

    private void testStateAuthorizing0(OAuth2Session session) {
        assertThat(session.getAccessToken()).isNull();
        assertThat(session.getAccessTokenResponse()).isEmpty();
        assertThat(session.getAuthorizationRequestNonce()).isEqualTo("nonce");
        assertThat(session.getClientEndpoint()).isEqualTo("endpoint");
        assertThat(session.getExpiresIn()).isNull();
        assertThat(session.getIdToken()).isNull();
        assertThat(session.getProviderName()).isEqualTo("provider");
        assertThat(session.getRefreshToken()).isNull();
        assertThat(session.getScopes()).containsExactly("scope1", "scope2");
        assertThat(session.getTokenType()).isNull();
        assertThat(session.isAuthorized()).isFalse();
        assertThat(session.isAuthorizing()).isTrue();
        // @formatter:off
        assertThat(session.toJson().asMap()).containsOnly(
                entry("pn", "provider"),
                entry("ce", "endpoint"),
                entry("arn", "nonce"),
                entry("s", asList("scope1", "scope2")));
        // @formatter:on
    }

    @Test
    public void testStateAuthorizedWithRequestedScopes() throws Exception {
        when(time.now()).thenReturn(1000L, 2000L);

        // @formatter:off
        JsonValue accessTokenResponse = json(object(
                field("access_token", "at"),
                field("refresh_token", "rt"),
                field("token_type", "tt"),
                field("expires_in", 3600),
                field("id_token", idToken)
        ));
        OAuth2Session session = OAuth2Session.stateNew(time)
                                             .stateAuthorizing("provider",
                                                               "endpoint",
                                                               "nonce",
                                                               asList("scope1", "scope2"))
                                             .stateAuthorized(accessTokenResponse);
        // @formatter:on

        testStateAuthorizedWithRequestedScopes0(session, accessTokenResponse);
        session = OAuth2Session.fromJson(time, session.toJson());
        testStateAuthorizedWithRequestedScopes0(session, accessTokenResponse);
    }

    private void testStateAuthorizedWithRequestedScopes0(OAuth2Session session,
            JsonValue accessTokenResponse) {
        assertThat(session.getAccessToken()).isEqualTo("at");
        assertThat(session.getAccessTokenResponse()).isEqualTo(accessTokenResponse.asMap());
        assertThat(session.getAuthorizationRequestNonce()).isNull();
        assertThat(session.getClientEndpoint()).isEqualTo("endpoint");
        assertThat(session.getExpiresIn()).isEqualTo(3599);
        assertThat(session.getIdToken()).isNotNull();
        assertThat(session.getProviderName()).isEqualTo("provider");
        assertThat(session.getRefreshToken()).isEqualTo("rt");
        assertThat(session.getScopes()).containsExactly("scope1", "scope2");
        assertThat(session.getTokenType()).isEqualTo("tt");
        assertThat(session.isAuthorized()).isTrue();
        assertThat(session.isAuthorizing()).isFalse();
        // @formatter:off
        assertThat(session.toJson().asMap()).containsOnly(
                entry("pn", "provider"),
                entry("ce", "endpoint"),
                entry("atr", accessTokenResponse.asMap()),
                entry("ea", 3601L),
                entry("s", asList("scope1", "scope2")));
        // @formatter:on
    }

    @Test
    public void testStateAuthorizedWithExpiresInExpressedAsString() throws Exception {
        when(time.now()).thenReturn(1000L, 2000L);

        // @Checkstyle:off
        JsonValue accessTokenResponse = json(object(
                field("access_token", "at"),
                field("refresh_token", "rt"),
                field("token_type", "tt"),
                field("expires_in", "3600"),
                field("id_token", idToken)
        ));
        // @Checkstyle:on

        OAuth2Session session = OAuth2Session.stateNew(time)
                                             .stateAuthorized(accessTokenResponse);

        assertThat(session.getExpiresIn()).isEqualTo(3599L);
    }

    @Test
    public void testStateAuthorizedWithDifferentScopes() throws Exception {
        when(time.now()).thenReturn(1000L, 2000L);

        // @formatter:off
        JsonValue accessTokenResponse = json(object(
                field("access_token", "at"),
                field("refresh_token", "rt"),
                field("token_type", "tt"),
                field("scope", "one two three"),
                field("expires_in", 3600),
                field("id_token", idToken)
        ));
        OAuth2Session session = OAuth2Session.stateNew(time)
                                             .stateAuthorizing("provider",
                                                               "endpoint",
                                                               "nonce",
                                                               asList("scope1", "scope2"))
                                             .stateAuthorized(accessTokenResponse);
        // @formatter:on

        testStateAuthorizedWithDifferentScopes0(session, accessTokenResponse);
        session = OAuth2Session.fromJson(time, session.toJson());
        testStateAuthorizedWithDifferentScopes0(session, accessTokenResponse);
    }

    private void testStateAuthorizedWithDifferentScopes0(OAuth2Session session,
            JsonValue accessTokenResponse) {
        assertThat(session.getAccessToken()).isEqualTo("at");
        assertThat(session.getAccessTokenResponse()).isEqualTo(accessTokenResponse.asMap());
        assertThat(session.getAuthorizationRequestNonce()).isNull();
        assertThat(session.getClientEndpoint()).isEqualTo("endpoint");
        assertThat(session.getExpiresIn()).isEqualTo(3599);
        assertThat(session.getIdToken()).isNotNull();
        assertThat(session.getProviderName()).isEqualTo("provider");
        assertThat(session.getRefreshToken()).isEqualTo("rt");
        assertThat(session.getScopes()).containsExactly("one", "two", "three");
        assertThat(session.getTokenType()).isEqualTo("tt");
        assertThat(session.isAuthorized()).isTrue();
        assertThat(session.isAuthorizing()).isFalse();
        // @formatter:off
        assertThat(session.toJson().asMap()).containsOnly(
                entry("pn", "provider"),
                entry("ce", "endpoint"),
                entry("atr", accessTokenResponse.asMap()),
                entry("ea", 3601L),
                entry("s", asList("one", "two", "three")));
        // @formatter:on
    }

    @Test
    public void testStateRefreshed() throws Exception {
        when(time.now()).thenReturn(1000L, 2000L, 3000L);

        // @formatter:off
        JsonValue accessTokenResponse = json(object(
                field("access_token", "at"),
                field("refresh_token", "rt"),
                field("token_type", "tt"),
                field("expires_in", 3600),
                field("id_token", idToken)
        ));
        JsonValue refreshAccessTokenResponse = json(object(
                field("access_token", "at2"),
                field("refresh_token", "rt2"),
                field("token_type", "tt"),
                field("scope", "one two three"),
                field("expires_in", 1000),
                field("id_token", idToken)
        ));
        OAuth2Session session = OAuth2Session.stateNew(time)
                                             .stateAuthorizing("provider",
                                                               "endpoint",
                                                               "nonce",
                                                               asList("scope1", "scope2"))
                                             .stateAuthorized(accessTokenResponse)
                                             .stateRefreshed(refreshAccessTokenResponse);
        // @formatter:on

        testStateRefreshed0(session, refreshAccessTokenResponse);
        session = OAuth2Session.fromJson(time, session.toJson());
        testStateRefreshed0(session, refreshAccessTokenResponse);
    }

    private void testStateRefreshed0(OAuth2Session session, JsonValue accessTokenResponse) {
        assertThat(session.getAccessToken()).isEqualTo("at2");
        assertThat(session.getAccessTokenResponse()).isEqualTo(accessTokenResponse.asMap());
        assertThat(session.getAuthorizationRequestNonce()).isNull();
        assertThat(session.getClientEndpoint()).isEqualTo("endpoint");
        assertThat(session.getExpiresIn()).isEqualTo(999);
        assertThat(session.getIdToken()).isNotNull();
        assertThat(session.getProviderName()).isEqualTo("provider");
        assertThat(session.getRefreshToken()).isEqualTo("rt2");
        assertThat(session.getScopes()).containsExactly("one", "two", "three");
        assertThat(session.getTokenType()).isEqualTo("tt");
        assertThat(session.isAuthorized()).isTrue();
        assertThat(session.isAuthorizing()).isFalse();
        // @formatter:off
        assertThat(session.toJson().asMap()).containsOnly(
                entry("pn", "provider"),
                entry("ce", "endpoint"),
                entry("atr", accessTokenResponse.asMap()),
                entry("ea", 1002L),
                entry("s", asList("one", "two", "three")));
        // @formatter:on
    }
}
