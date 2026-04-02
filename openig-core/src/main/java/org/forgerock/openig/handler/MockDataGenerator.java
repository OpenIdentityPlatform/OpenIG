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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates realistic mock values for OpenAPI schema properties.
 *
 * <p>Values are chosen using the following priority order:
 * <ol>
 *   <li>Schema {@code format} (date, date-time, email, uri, uuid, ipv4, hostname, byte, password, …)</li>
 *   <li>Field-name dictionary (case-insensitive, separator-agnostic lookup)</li>
 *   <li>Schema {@code type} fallback (generic string / integer / number / boolean)</li>
 * </ol>
 *
 * <p>The generator uses a seeded {@link Random} so results are deterministic and
 * reproducible across test runs.
 *
 * <p>Numeric and string constraints ({@code minimum}, {@code maximum},
 * {@code minLength}, {@code maxLength}) are respected when present.
 */
public class MockDataGenerator {

    /** Seeded random for deterministic output. */
    private static final Random RNG = new Random(42L);

    /** Normalised-name → realistic value dictionary. */
    private static final Map<String, Object> FIELD_DICTIONARY = new HashMap<>();

    static {
        // Personal
        FIELD_DICTIONARY.put("firstname",  "John");
        FIELD_DICTIONARY.put("lastname",   "Doe");
        FIELD_DICTIONARY.put("fullname",   "John Doe");
        FIELD_DICTIONARY.put("name",       "John Doe");
        FIELD_DICTIONARY.put("username",   "johndoe");
        FIELD_DICTIONARY.put("login",      "johndoe");
        FIELD_DICTIONARY.put("displayname","John Doe");

        // Contact
        FIELD_DICTIONARY.put("email",      "john.doe@example.com");
        FIELD_DICTIONARY.put("mail",       "john.doe@example.com");
        FIELD_DICTIONARY.put("phone",      "+1-555-123-4567");
        FIELD_DICTIONARY.put("phonenumber","+1-555-123-4567");
        FIELD_DICTIONARY.put("mobile",     "+1-555-123-4567");
        FIELD_DICTIONARY.put("fax",        "+1-555-123-4568");

        // Address
        FIELD_DICTIONARY.put("address",    "123 Main St");
        FIELD_DICTIONARY.put("street",     "123 Main St");
        FIELD_DICTIONARY.put("city",       "Springfield");
        FIELD_DICTIONARY.put("state",      "Illinois");
        FIELD_DICTIONARY.put("country",    "United States");
        FIELD_DICTIONARY.put("zip",        "62701");
        FIELD_DICTIONARY.put("zipcode",    "62701");
        FIELD_DICTIONARY.put("postalcode", "62701");

        // Internet
        FIELD_DICTIONARY.put("url",        "https://www.example.com");
        FIELD_DICTIONARY.put("website",    "https://www.example.com");
        FIELD_DICTIONARY.put("homepage",   "https://www.example.com");
        FIELD_DICTIONARY.put("avatar",     "https://www.example.com/images/avatar.jpg");
        FIELD_DICTIONARY.put("photo",      "https://www.example.com/images/photo.jpg");
        FIELD_DICTIONARY.put("picture",    "https://www.example.com/images/photo.jpg");
        FIELD_DICTIONARY.put("image",      "https://www.example.com/images/photo.jpg");
        FIELD_DICTIONARY.put("thumbnail",  "https://www.example.com/images/thumb.jpg");
        FIELD_DICTIONARY.put("gravatar",   "https://www.gravatar.com/avatar/00000000000000000000000000000000");

        // Text / content
        FIELD_DICTIONARY.put("description","Sample description text.");
        FIELD_DICTIONARY.put("summary",    "Sample summary.");
        FIELD_DICTIONARY.put("content",    "Sample content.");
        FIELD_DICTIONARY.put("body",       "Sample body content.");
        FIELD_DICTIONARY.put("message",    "Sample message.");
        FIELD_DICTIONARY.put("title",      "Sample Title");
        FIELD_DICTIONARY.put("subject",    "Sample Subject");
        FIELD_DICTIONARY.put("label",      "Sample Label");
        FIELD_DICTIONARY.put("note",       "Sample note.");
        FIELD_DICTIONARY.put("comment",    "Sample comment.");
        FIELD_DICTIONARY.put("text",       "Sample text.");
        FIELD_DICTIONARY.put("slug",       "sample-slug");
        FIELD_DICTIONARY.put("tag",        "sample-tag");
        FIELD_DICTIONARY.put("tags",       "sample-tag");
        FIELD_DICTIONARY.put("category",   "General");
        FIELD_DICTIONARY.put("type",       "default");
        FIELD_DICTIONARY.put("status",     "active");
        FIELD_DICTIONARY.put("format",     "json");
        FIELD_DICTIONARY.put("locale",     "en-US");
        FIELD_DICTIONARY.put("language",   "en");
        FIELD_DICTIONARY.put("timezone",   "UTC");
        FIELD_DICTIONARY.put("currency",   "USD");

        // Business
        FIELD_DICTIONARY.put("company",    "Acme Corporation");
        FIELD_DICTIONARY.put("organisation","Acme Corporation");
        FIELD_DICTIONARY.put("organization","Acme Corporation");
        FIELD_DICTIONARY.put("department", "Engineering");
        FIELD_DICTIONARY.put("role",       "admin");
        FIELD_DICTIONARY.put("permission", "read");
        FIELD_DICTIONARY.put("scope",      "openid profile");
        FIELD_DICTIONARY.put("group",      "users");
        FIELD_DICTIONARY.put("team",       "dev-team");
        FIELD_DICTIONARY.put("project",    "My Project");
        FIELD_DICTIONARY.put("version",    "1.0.0");
        FIELD_DICTIONARY.put("code",       "SAMPLE-CODE");
        FIELD_DICTIONARY.put("reference",  "REF-1001");
        FIELD_DICTIONARY.put("key",        "sample-key");
        FIELD_DICTIONARY.put("value",      "sample-value");

        // Auth
        FIELD_DICTIONARY.put("token",      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U");
        FIELD_DICTIONARY.put("accesstoken","eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U");
        FIELD_DICTIONARY.put("refreshtoken","dGhpcyBpcyBhIG1vY2sgcmVmcmVzaCB0b2tlbg==");
        FIELD_DICTIONARY.put("password",   "P@ssw0rd123!");
        FIELD_DICTIONARY.put("secret",     "s3cr3t-v4lu3");
        FIELD_DICTIONARY.put("apikey",     "sk-mock-api-key-1234567890abcdef");
        FIELD_DICTIONARY.put("hash",       "5f4dcc3b5aa765d61d8327deb882cf99");
        FIELD_DICTIONARY.put("salt",       "random-salt-value");

        // Numbers (stored as Number so callers can down-cast)
        FIELD_DICTIONARY.put("id",         1001);
        FIELD_DICTIONARY.put("uid",        1001);
        FIELD_DICTIONARY.put("userid",     1001);
        FIELD_DICTIONARY.put("accountid",  1001);
        FIELD_DICTIONARY.put("age",        30);
        FIELD_DICTIONARY.put("year",       2024);
        FIELD_DICTIONARY.put("month",      6);
        FIELD_DICTIONARY.put("day",        15);
        FIELD_DICTIONARY.put("hour",       12);
        FIELD_DICTIONARY.put("minute",     30);
        FIELD_DICTIONARY.put("second",     0);
        FIELD_DICTIONARY.put("count",      10);
        FIELD_DICTIONARY.put("total",      100);
        FIELD_DICTIONARY.put("quantity",   5);
        FIELD_DICTIONARY.put("amount",     99.99);
        FIELD_DICTIONARY.put("price",      29.99);
        FIELD_DICTIONARY.put("cost",       19.99);
        FIELD_DICTIONARY.put("discount",   5.0);
        FIELD_DICTIONARY.put("tax",        7.5);
        FIELD_DICTIONARY.put("rating",     4.5);
        FIELD_DICTIONARY.put("score",      85);
        FIELD_DICTIONARY.put("rank",       1);
        FIELD_DICTIONARY.put("index",      0);
        FIELD_DICTIONARY.put("size",       10);
        FIELD_DICTIONARY.put("length",     100);
        FIELD_DICTIONARY.put("width",      800);
        FIELD_DICTIONARY.put("height",     600);
        FIELD_DICTIONARY.put("weight",     70.5);
        FIELD_DICTIONARY.put("limit",      20);
        FIELD_DICTIONARY.put("offset",     0);
        FIELD_DICTIONARY.put("page",       1);
        FIELD_DICTIONARY.put("pagesize",   20);
        FIELD_DICTIONARY.put("maxresults", 100);
        FIELD_DICTIONARY.put("duration",   3600);
        FIELD_DICTIONARY.put("timeout",    30);
        FIELD_DICTIONARY.put("retries",    3);
        FIELD_DICTIONARY.put("priority",   1);
        FIELD_DICTIONARY.put("order",      1);
        FIELD_DICTIONARY.put("sort",       1);
        FIELD_DICTIONARY.put("port",       8080);
        FIELD_DICTIONARY.put("latitude",   37.7749);
        FIELD_DICTIONARY.put("longitude",  -122.4194);

        // Booleans (true group)
        FIELD_DICTIONARY.put("enabled",    true);
        FIELD_DICTIONARY.put("active",     true);
        FIELD_DICTIONARY.put("verified",   true);
        FIELD_DICTIONARY.put("confirmed",  true);
        FIELD_DICTIONARY.put("approved",   true);
        FIELD_DICTIONARY.put("published",  true);
        FIELD_DICTIONARY.put("available",  true);
        FIELD_DICTIONARY.put("visible",    true);
        FIELD_DICTIONARY.put("public",     true);
        FIELD_DICTIONARY.put("success",    true);
        FIELD_DICTIONARY.put("valid",      true);
        FIELD_DICTIONARY.put("required",   true);
        FIELD_DICTIONARY.put("locked",     false);

        // Booleans (false group)
        FIELD_DICTIONARY.put("deleted",    false);
        FIELD_DICTIONARY.put("archived",   false);
        FIELD_DICTIONARY.put("banned",     false);
        FIELD_DICTIONARY.put("blocked",    false);
        FIELD_DICTIONARY.put("disabled",   false);
        FIELD_DICTIONARY.put("hidden",     false);
        FIELD_DICTIONARY.put("private",    false);
        FIELD_DICTIONARY.put("deprecated", false);
        FIELD_DICTIONARY.put("failed",     false);
        FIELD_DICTIONARY.put("error",      false);
    }

    private MockDataGenerator() {
        // utility class
    }

    /**
     * Generates a realistic mock value for the given field name and schema.
     *
     * @param fieldName the property name (may be {@code null} for anonymous/array items)
     * @param schema    the OpenAPI schema for this field
     * @return a mock value compatible with the schema type
     */
    @SuppressWarnings("rawtypes")
    public static Object generate(final String fieldName, final Schema<?> schema) {
        if (schema == null) {
            return fieldName != null ? fieldName + "-value" : "value";
        }

        final String format = schema.getFormat();
        final String type   = schema.getType();

        // 1. Format-based generation
        if (format != null) {
            Object formatValue = generateByFormat(format, schema);
            if (formatValue != null) {
                return formatValue;
            }
        }

        // 2. Field-name-based (dictionary lookup)
        if (fieldName != null) {
            final String key = normalise(fieldName);
            Object dictValue = FIELD_DICTIONARY.get(key);
            if (dictValue != null) {
                return coerce(dictValue, type, schema);
            }
        }

        // 3. Type-based fallback
        return generateByType(fieldName, type, schema);
    }

    /**
     * Generates a value based on the OpenAPI format string.
     *
     * @return a value, or {@code null} if no specific generator exists for the format
     */
    private static Object generateByFormat(final String format, final Schema<?> schema) {
        switch (format.toLowerCase()) {
            case "date":
                return "2024-06-15";
            case "date-time":
                return "2024-06-15T12:00:00Z";
            case "time":
                return "12:00:00";
            case "email":
                return "john.doe@example.com";
            case "uri":
            case "url":
                return "https://www.example.com";
            case "uri-reference":
                return "/api/resource/1001";
            case "uuid":
                return "550e8400-e29b-41d4-a716-446655440000";
            case "ipv4":
                return "192.168.1.1";
            case "ipv6":
                return "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
            case "hostname":
                return "api.example.com";
            case "byte":
                return "SGVsbG8gV29ybGQ=";
            case "binary":
                return "binary-data";
            case "password":
                return "P@ssw0rd123!";
            case "int32":
                return generateInt(schema, 1001);
            case "int64":
                return generateLong(schema, 1001L);
            case "float":
            case "double":
                return generateDouble(schema, 29.99);
            default:
                return null;
        }
    }

    /**
     * Generates a value based on schema type when no format or dictionary match was found.
     */
    private static Object generateByType(final String fieldName, final String type, final Schema<?> schema) {
        if (type == null) {
            return fieldName != null ? fieldName + "-value" : "value";
        }
        switch (type.toLowerCase()) {
            case "integer":
                return generateInt(schema, 1001);
            case "number":
                return generateDouble(schema, 29.99);
            case "boolean":
                return generateBoolean(fieldName);
            case "string":
                return generateString(fieldName, schema);
            case "array":
            case "object":
            default:
                return fieldName != null ? fieldName + "-value" : "value";
        }
    }

    /**
     * Generates a realistic boolean value using field-name heuristics.
     * Fields whose name implies truth (e.g. "enabled") return {@code true};
     * fields implying falsehood (e.g. "deleted") return {@code false};
     * all others default to {@code true}.
     */
    static boolean generateBoolean(final String fieldName) {
        if (fieldName == null) {
            return true;
        }
        final String key = normalise(fieldName);
        final Object dictValue = FIELD_DICTIONARY.get(key);
        if (dictValue instanceof Boolean) {
            return (Boolean) dictValue;
        }
        // Heuristics for names not in dictionary
        if (key.startsWith("is") || key.startsWith("has") || key.startsWith("can") || key.startsWith("should")) {
            return true;
        }
        if (key.contains("delete") || key.contains("archive") || key.contains("disable")
                || key.contains("block") || key.contains("ban")) {
            return false;
        }
        return true;
    }

    /**
     * Generates a mock string value, respecting {@code minLength} / {@code maxLength} constraints.
     */
    private static String generateString(final String fieldName, final Schema<?> schema) {
        String base = fieldName != null ? fieldName + "-value" : "sample-value";
        // Respect minLength
        Integer minLength = schema.getMinLength();
        if (minLength != null && base.length() < minLength) {
            StringBuilder sb = new StringBuilder(base);
            while (sb.length() < minLength) {
                sb.append("-x");
            }
            base = sb.toString();
        }
        // Respect maxLength
        Integer maxLength = schema.getMaxLength();
        if (maxLength != null && base.length() > maxLength) {
            base = base.substring(0, maxLength);
        }
        return base;
    }

    /**
     * Generates an integer within schema {@code minimum} / {@code maximum} constraints.
     */
    static int generateInt(final Schema<?> schema, final int defaultValue) {
        int min = defaultValue;
        int max = defaultValue + 1000;
        if (schema.getMinimum() != null) {
            min = schema.getMinimum().intValue();
        }
        if (schema.getMaximum() != null) {
            max = schema.getMaximum().intValue();
        }
        if (min >= max) {
            return min;
        }
        return min + RNG.nextInt(max - min + 1);
    }

    /**
     * Generates a long within schema {@code minimum} / {@code maximum} constraints.
     */
    private static long generateLong(final Schema<?> schema, final long defaultValue) {
        long min = defaultValue;
        long max = defaultValue + 1000L;
        if (schema.getMinimum() != null) {
            min = schema.getMinimum().longValue();
        }
        if (schema.getMaximum() != null) {
            max = schema.getMaximum().longValue();
        }
        if (min >= max) {
            return min;
        }
        return min + (long) (RNG.nextDouble() * (max - min));
    }

    /**
     * Generates a double within schema {@code minimum} / {@code maximum} constraints.
     */
    static double generateDouble(final Schema<?> schema, final double defaultValue) {
        double min = defaultValue;
        double max = defaultValue + 100.0;
        if (schema.getMinimum() != null) {
            min = schema.getMinimum().doubleValue();
        }
        if (schema.getMaximum() != null) {
            max = schema.getMaximum().doubleValue();
        }
        if (min >= max) {
            return min;
        }
        return min + RNG.nextDouble() * (max - min);
    }

    /**
     * Coerces a dictionary value to the expected schema type where possible.
     */
    private static Object coerce(final Object value, final String type, final Schema<?> schema) {
        if (type == null) {
            return value;
        }
        switch (type.toLowerCase()) {
            case "integer":
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return generateInt(schema, 1001);
            case "number":
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return generateDouble(schema, 29.99);
            case "boolean":
                if (value instanceof Boolean) {
                    return value;
                }
                return generateBoolean(null);
            case "string":
                if (value instanceof String) {
                    return value;
                }
                return String.valueOf(value);
            default:
                return value;
        }
    }

    /**
     * Normalises a field name by lower-casing it and removing all non-alphanumeric characters
     * (e.g. underscores, hyphens, dots) so that {@code first_name}, {@code firstName},
     * and {@code first-name} all map to the same dictionary key.
     */
    static String normalise(final String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
