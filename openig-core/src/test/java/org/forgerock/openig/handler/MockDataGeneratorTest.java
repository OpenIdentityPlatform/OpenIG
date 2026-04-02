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

package org.forgerock.openig.handler;

import io.swagger.v3.oas.models.media.Schema;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class MockDataGeneratorTest {

    // -----------------------------------------------------------------------
    // normalise
    // -----------------------------------------------------------------------

    @Test
    public void normalise_removesUnderscoresHyphensAndDots() {
        assertThat(MockDataGenerator.normalise("first_name")).isEqualTo("firstname");
        assertThat(MockDataGenerator.normalise("first-name")).isEqualTo("firstname");
        assertThat(MockDataGenerator.normalise("first.name")).isEqualTo("firstname");
        assertThat(MockDataGenerator.normalise("firstName")).isEqualTo("firstname");
        assertThat(MockDataGenerator.normalise("FirstName")).isEqualTo("firstname");
    }

    @Test
    public void normalise_handlesNullAndEmpty() {
        assertThat(MockDataGenerator.normalise(null)).isEqualTo("");
        assertThat(MockDataGenerator.normalise("")).isEqualTo("");
    }

    // -----------------------------------------------------------------------
    // Format-based generation
    // -----------------------------------------------------------------------

    @Test
    public void generate_returnsEmail_forEmailFormat() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("email");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).contains("@");
    }

    @Test
    public void generate_returnsIsoDate_forDateFormat() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("date");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    public void generate_returnsIsoDateTime_forDateTimeFormat() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("date-time");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).contains("T");
        assertThat((String) value).endsWith("Z");
    }

    @Test
    public void generate_returnsUuid_forUuidFormat() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("uuid");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    public void generate_returnsUrl_forUriFormat() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("uri");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).startsWith("https://");
    }

    @Test
    public void generate_returnsIpv4_forIpv4Format() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("ipv4");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }

    @Test
    public void generate_returnsPassword_forPasswordFormat() {
        Schema<String> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("password");

        final Object value = MockDataGenerator.generate("anyField", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // Field-name dictionary
    // -----------------------------------------------------------------------

    @DataProvider(name = "fieldNameCases")
    public static Object[][] fieldNameCases() {
        return new Object[][] {
                {"firstName",  String.class,  "John"},
                {"lastName",   String.class,  "Doe"},
                {"username",   String.class,  "johndoe"},
                {"email",      String.class,  "john.doe@example.com"},
                {"phone",      String.class,  "+1-555-123-4567"},
                {"city",       String.class,  "Springfield"},
                {"country",    String.class,  "United States"},
                {"company",    String.class,  "Acme Corporation"},
                {"role",       String.class,  "admin"},
                {"description",String.class,  "Sample description text."},
                {"title",      String.class,  "Sample Title"},
                {"url",        String.class,  "https://www.example.com"},
        };
    }

    @Test(dataProvider = "fieldNameCases")
    public void generate_usesFieldNameDictionary(
            final String fieldName, final Class<?> expectedType, final Object expectedValue) {
        final Schema<String> schema = new Schema<>();
        schema.setType("string");
        final Object value = MockDataGenerator.generate(fieldName, schema);
        assertThat(value).isInstanceOf(expectedType);
        assertThat(value).isEqualTo(expectedValue);
    }

    @Test
    public void generate_normalisesFieldNameForLookup() {
        final Schema<String> schema = new Schema<>();
        schema.setType("string");
        // "first_name" should normalise to "firstname" which maps to "John"
        assertThat(MockDataGenerator.generate("first_name", schema)).isEqualTo("John");
        assertThat(MockDataGenerator.generate("first-name", schema)).isEqualTo("John");
        assertThat(MockDataGenerator.generate("FIRSTNAME",  schema)).isEqualTo("John");
    }

    // -----------------------------------------------------------------------
    // Numeric generation with constraints
    // -----------------------------------------------------------------------

    @Test
    public void generateInt_respectsMinimumConstraint() {
        final Schema<Integer> schema = new Schema<>();
        schema.setType("integer");
        schema.setMinimum(BigDecimal.valueOf(500));
        schema.setMaximum(BigDecimal.valueOf(510));

        final int value = MockDataGenerator.generateInt(schema, 0);
        assertThat(value).isBetween(500, 510);
    }

    @Test
    public void generateInt_respectsMaximumConstraint() {
        final Schema<Integer> schema = new Schema<>();
        schema.setType("integer");
        schema.setMinimum(BigDecimal.valueOf(1));
        schema.setMaximum(BigDecimal.valueOf(5));

        for (int i = 0; i < 50; i++) {
            final int value = MockDataGenerator.generateInt(schema, 0);
            assertThat(value).isBetween(1, 5);
        }
    }

    @Test
    public void generateDouble_respectsMinMaxConstraint() {
        final Schema<Double> schema = new Schema<>();
        schema.setType("number");
        schema.setMinimum(BigDecimal.valueOf(10.0));
        schema.setMaximum(BigDecimal.valueOf(20.0));

        final double value = MockDataGenerator.generateDouble(schema, 0);
        assertThat(value).isBetween(10.0, 20.0);
    }

    // -----------------------------------------------------------------------
    // Boolean heuristics
    // -----------------------------------------------------------------------

    @DataProvider(name = "booleanTrueCases")
    public static Object[][] booleanTrueCases() {
        return new Object[][] {
                {"enabled"}, {"active"}, {"verified"}, {"confirmed"}, {"approved"},
                {"published"}, {"available"}, {"isActive"}, {"hasAccess"},
        };
    }

    @Test(dataProvider = "booleanTrueCases")
    public void generateBoolean_returnsTrue_forPositiveNames(final String fieldName) {
        assertThat(MockDataGenerator.generateBoolean(fieldName)).isTrue();
    }

    @DataProvider(name = "booleanFalseCases")
    public static Object[][] booleanFalseCases() {
        return new Object[][] {
                {"deleted"}, {"archived"}, {"banned"}, {"blocked"}, {"disabled"},
        };
    }

    @Test(dataProvider = "booleanFalseCases")
    public void generateBoolean_returnsFalse_forNegativeNames(final String fieldName) {
        assertThat(MockDataGenerator.generateBoolean(fieldName)).isFalse();
    }

    @Test
    public void generateBoolean_returnsTrue_forUnknownFieldName() {
        assertThat(MockDataGenerator.generateBoolean("unknownField")).isTrue();
        assertThat(MockDataGenerator.generateBoolean(null)).isTrue();
    }

    // -----------------------------------------------------------------------
    // Type-based fallbacks
    // -----------------------------------------------------------------------

    @Test
    public void generate_returnsInteger_forIntegerType() {
        final Schema<Integer> schema = new Schema<>();
        schema.setType("integer");
        final Object value = MockDataGenerator.generate("count", schema);
        assertThat(value).isInstanceOf(Number.class);
    }

    @Test
    public void generate_returnsNumber_forNumberType() {
        final Schema<Double> schema = new Schema<>();
        schema.setType("number");
        final Object value = MockDataGenerator.generate("total", schema);
        assertThat(value).isInstanceOf(Number.class);
    }

    @Test
    public void generate_returnsBoolean_forBooleanType() {
        final Schema<Boolean> schema = new Schema<>();
        schema.setType("boolean");
        final Object value = MockDataGenerator.generate("flag", schema);
        assertThat(value).isInstanceOf(Boolean.class);
    }

    @Test
    public void generate_returnsString_forStringType_withUnknownFieldName() {
        final Schema<String> schema = new Schema<>();
        schema.setType("string");
        final Object value = MockDataGenerator.generate("unknownXyz", schema);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).isNotEmpty();
    }

    @Test
    public void generate_handlesNullSchema() {
        final Object value = MockDataGenerator.generate("field", null);
        assertThat(value).isNotNull();
    }

    @Test
    public void generate_handlesNullFieldName() {
        final Schema<String> schema = new Schema<>();
        schema.setType("string");
        final Object value = MockDataGenerator.generate(null, schema);
        assertThat(value).isNotNull();
    }
}
