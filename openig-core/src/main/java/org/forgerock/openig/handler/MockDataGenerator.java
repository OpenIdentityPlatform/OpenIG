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
import net.datafaker.Faker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Generates realistic mock values for OpenAPI schema properties.
 *
 * <p>Values are chosen using the following priority order:
 * <ol>
 *   <li>Schema {@code format} (date, date-time, email, uri, uuid, ipv4, hostname, byte, password, …)</li>
 *   <li>Field-name heuristic powered by <a href="https://github.com/datafaker-net/datafaker">Datafaker</a>
 *       (case-insensitive, separator-agnostic lookup)</li>
 *   <li>Schema {@code type} fallback (generic string / integer / number / boolean)</li>
 * </ol>
 *
 * <p>The generator uses a seeded {@link Faker} so results are deterministic and
 * reproducible across test runs.
 *
 * <p>Numeric and string constraints ({@code minimum}, {@code maximum},
 * {@code minLength}, {@code maxLength}) are respected when present.
 */
public class MockDataGenerator {

    /** Seeded random shared by the Faker instance and numeric generators for deterministic output. */
    private static final Random RNG = new Random(42L);

    /** Datafaker instance backed by the same seed. */
    private static final Faker FAKER = new Faker(Locale.ENGLISH, RNG);

    // Boolean heuristic sets (normalised names)
    private static final java.util.Set<String> BOOL_TRUE_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "enabled", "active", "verified", "confirmed", "approved",
                    "published", "available", "visible", "ispublic", "success",
                    "valid", "required", "locked"
            ));

    private static final java.util.Set<String> BOOL_FALSE_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "deleted", "archived", "banned", "blocked", "disabled",
                    "hidden", "isprivate", "deprecated", "failed", "error"
            ));

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
            final Object formatValue = generateByFormat(format, schema);
            if (formatValue != null) {
                return formatValue;
            }
        }

        // 2. Field-name-based (Datafaker)
        if (fieldName != null) {
            final Object fakerValue = generateByFieldName(fieldName, type, schema);
            if (fakerValue != null) {
                return fakerValue;
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
                return LocalDate.now().minusDays(RNG.nextInt(365))
                        .format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "date-time":
                return LocalDateTime.now().minusDays(RNG.nextInt(365))
                        .format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
            case "time":
                return "12:00:00";
            case "email":
                return FAKER.internet().emailAddress();
            case "uri":
            case "url":
                return "https://" + FAKER.internet().domainName();
            case "uri-reference":
                return "/api/resource/" + FAKER.number().numberBetween(1, 9999);
            case "uuid":
            case "guid":
                return UUID.randomUUID().toString();
            case "ipv4":
                return FAKER.internet().ipV4Address();
            case "ipv6":
                return FAKER.internet().ipV6Address();
            case "hostname":
                return FAKER.internet().domainName();
            case "byte":
                return java.util.Base64.getEncoder().encodeToString(
                        FAKER.lorem().word().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "binary":
                return "binary-data";
            case "password":
                return FAKER.internet().password(8, 16, true, true, true);
            case "int32":
                return generateInt(schema, FAKER.number().numberBetween(1, 10000));
            case "int64":
                return generateLong(schema, FAKER.number().numberBetween(1L, 100000L));
            case "float":
            case "double":
                return generateDouble(schema, FAKER.number().randomDouble(2, 1, 10000));
            default:
                return null;
        }
    }

    /**
     * Attempts to generate a value using Datafaker based on the normalised field name.
     *
     * @return a value, or {@code null} if no heuristic matches the field name
     */
    @SuppressWarnings("squid:S3776")
    private static Object generateByFieldName(final String fieldName,
                                               final String type,
                                               final Schema<?> schema) {
        final String key = normalise(fieldName);

        // --- Strings ---
        // Personal
        if (key.equals("firstname")) return coerce(FAKER.name().firstName(), type, schema);
        if (key.equals("lastname"))  return coerce(FAKER.name().lastName(),  type, schema);
        if (key.equals("fullname") || key.equals("displayname"))
            return coerce(FAKER.name().fullName(), type, schema);
        if (key.equals("name"))      return coerce(FAKER.name().fullName(), type, schema);
        if (key.equals("username") || key.equals("login"))
            return coerce(FAKER.name().username(), type, schema);

        // Contact
        if (key.equals("email") || key.equals("mail"))
            return coerce(FAKER.internet().emailAddress(), type, schema);
        if (key.equals("phone") || key.equals("phonenumber") || key.equals("mobile"))
            return coerce(FAKER.phoneNumber().phoneNumber(), type, schema);
        if (key.equals("fax"))
            return coerce(FAKER.phoneNumber().phoneNumber(), type, schema);

        // Address
        if (key.equals("address") || key.equals("street"))
            return coerce(FAKER.address().streetAddress(), type, schema);
        if (key.equals("city"))
            return coerce(FAKER.address().city(), type, schema);
        if (key.equals("state"))
            return coerce(FAKER.address().state(), type, schema);
        if (key.equals("country"))
            return coerce(FAKER.address().country(), type, schema);
        if (key.equals("zip") || key.equals("zipcode") || key.equals("postalcode"))
            return coerce(FAKER.address().zipCode(), type, schema);

        // Internet
        if (key.equals("url") || key.equals("website") || key.equals("homepage"))
            return coerce("https://" + FAKER.internet().domainName(), type, schema);
        if (key.equals("avatar") || key.equals("photo") || key.equals("picture")
                || key.equals("image") || key.equals("thumbnail"))
            return coerce("https://" + FAKER.internet().domainName() + "/images/"
                    + FAKER.internet().slug() + ".jpg", type, schema);
        if (key.equals("gravatar"))
            return coerce("https://www.gravatar.com/avatar/" + FAKER.hashing().md5(), type, schema);

        // Text / content
        if (key.equals("description") || key.equals("summary") || key.equals("content")
                || key.equals("body") || key.equals("note") || key.equals("comment")
                || key.equals("text"))
            return coerce(FAKER.lorem().sentence(8), type, schema);
        if (key.equals("message"))
            return coerce(FAKER.lorem().sentence(4), type, schema);
        if (key.equals("title") || key.equals("subject"))
            return coerce(FAKER.book().title(), type, schema);
        if (key.equals("label"))
            return coerce(FAKER.lorem().word(), type, schema);
        if (key.equals("slug"))
            return coerce(FAKER.internet().slug(), type, schema);
        if (key.equals("tag") || key.equals("tags"))
            return coerce(FAKER.lorem().word(), type, schema);
        if (key.equals("category"))
            return coerce(FAKER.book().genre(), type, schema);
        if (key.equals("locale") || key.equals("language"))
            return coerce("en-US", type, schema);
        if (key.equals("timezone"))
            return coerce(FAKER.address().timeZone(), type, schema);
        if (key.equals("currency"))
            return coerce(FAKER.currency().code(), type, schema);

        // Business
        if (key.equals("company") || key.equals("organisation") || key.equals("organization"))
            return coerce(FAKER.company().name(), type, schema);
        if (key.equals("department"))
            return coerce(FAKER.commerce().department(), type, schema);
        if (key.equals("role"))
            return coerce(FAKER.job().title(), type, schema);
        if (key.equals("team"))
            return coerce(FAKER.team().name(), type, schema);
        if (key.equals("project"))
            return coerce(FAKER.app().name(), type, schema);
        if (key.equals("version"))
            return coerce(FAKER.app().version(), type, schema);
        if (key.equals("code") || key.equals("reference"))
            return coerce(FAKER.code().isbnGs1(), type, schema);

        // Auth
        if (key.equals("password"))
            return coerce(FAKER.internet().password(8, 16, true, true, true), type, schema);
        if (key.equals("token") || key.equals("accesstoken"))
            return coerce(FAKER.hashing().sha256(), type, schema);
        if (key.equals("refreshtoken"))
            return coerce(FAKER.hashing().sha256(), type, schema);
        if (key.equals("apikey"))
            return coerce("sk-" + FAKER.hashing().sha256().substring(0, 24), type, schema);
        if (key.equals("secret"))
            return coerce(FAKER.hashing().sha256().substring(0, 16), type, schema);
        if (key.equals("hash"))
            return coerce(FAKER.hashing().md5(), type, schema);
        if (key.equals("salt"))
            return coerce(FAKER.hashing().sha256().substring(0, 8), type, schema);

        // --- Numbers ---
        if (key.equals("id") || key.equals("uid") || key.equals("userid") || key.equals("accountid"))
            return coerce(FAKER.number().numberBetween(1001, 99999), type, schema);
        if (key.equals("age"))
            return coerce(FAKER.number().numberBetween(18, 80), type, schema);
        if (key.equals("year"))
            return coerce(FAKER.number().numberBetween(2000, 2024), type, schema);
        if (key.equals("month"))
            return coerce(FAKER.number().numberBetween(1, 12), type, schema);
        if (key.equals("day"))
            return coerce(FAKER.number().numberBetween(1, 28), type, schema);
        if (key.equals("hour"))
            return coerce(FAKER.number().numberBetween(0, 23), type, schema);
        if (key.equals("minute") || key.equals("second"))
            return coerce(FAKER.number().numberBetween(0, 59), type, schema);
        if (key.equals("count") || key.equals("total"))
            return coerce(FAKER.number().numberBetween(1, 200), type, schema);
        if (key.equals("quantity") || key.equals("size"))
            return coerce(FAKER.number().numberBetween(1, 50), type, schema);
        if (key.equals("amount") || key.equals("price") || key.equals("cost"))
            return coerce(FAKER.number().randomDouble(2, 1, 9999), type, schema);
        if (key.equals("discount") || key.equals("tax"))
            return coerce(FAKER.number().randomDouble(2, 0, 50), type, schema);
        if (key.equals("rating"))
            return coerce(FAKER.number().randomDouble(1, 1, 5), type, schema);
        if (key.equals("score") || key.equals("rank"))
            return coerce(FAKER.number().numberBetween(1, 100), type, schema);
        if (key.equals("port"))
            return coerce(FAKER.number().numberBetween(1024, 65535), type, schema);
        if (key.equals("latitude"))
            return coerce(Double.parseDouble(FAKER.address().latitude().replace(",", ".")), type, schema);
        if (key.equals("longitude"))
            return coerce(Double.parseDouble(FAKER.address().longitude().replace(",", ".")), type, schema);

        // --- Booleans ---
        if ("boolean".equals(type)) {
            return generateBoolean(fieldName);
        }

        return null;
    }

    /**
     * Generates a value based on schema type when no format or field-name match was found.
     */
    private static Object generateByType(final String fieldName, final String type, final Schema<?> schema) {
        if (type == null) {
            return fieldName != null ? FAKER.lorem().word() : "value";
        }
        switch (type.toLowerCase()) {
            case "integer":
                return generateInt(schema, FAKER.number().numberBetween(1, 10000));
            case "number":
                return generateDouble(schema, FAKER.number().randomDouble(2, 1, 10000));
            case "boolean":
                return generateBoolean(fieldName);
            case "string":
                return generateString(fieldName, schema);
            case "array":
            case "object":
            default:
                return fieldName != null ? FAKER.lorem().word() : "value";
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
        if (BOOL_FALSE_NAMES.contains(key)) {
            return false;
        }
        if (BOOL_TRUE_NAMES.contains(key)) {
            return true;
        }
        // Heuristics for names not in the sets
        if (key.startsWith("is") || key.startsWith("has") || key.startsWith("can")
                || key.startsWith("should")) {
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
        String base = fieldName != null ? FAKER.lorem().word() : FAKER.lorem().word();
        // Respect minLength
        final Integer minLength = schema.getMinLength();
        if (minLength != null && base.length() < minLength) {
            final StringBuilder sb = new StringBuilder(base);
            while (sb.length() < minLength) {
                sb.append(FAKER.lorem().characters(1));
            }
            base = sb.toString();
        }
        // Respect maxLength
        final Integer maxLength = schema.getMaxLength();
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
     * Coerces a Datafaker-generated value to the expected schema type where possible.
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
                return generateInt(schema, FAKER.number().numberBetween(1, 10000));
            case "number":
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return generateDouble(schema, FAKER.number().randomDouble(2, 1, 10000));
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
     * and {@code first-name} all map to the same lookup key.
     */
    static String normalise(final String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
