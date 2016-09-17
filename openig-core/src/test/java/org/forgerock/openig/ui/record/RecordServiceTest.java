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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.ui.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.util.Files.delete;
import static org.assertj.core.util.Files.newTemporaryFolder;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.test.assertj.AssertJJsonValueAssert.assertThat;
import static org.forgerock.openig.ui.record.RecordService.MAPPER;
import static org.forgerock.openig.ui.record.RecordService.resourceFilename;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.assertj.core.api.iterable.Extractor;
import org.forgerock.json.JsonValue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RecordServiceTest {

    private static final String WEDGE_ANTILLES = "Wedge Antilles";
    private static final String ROGUE_SQUADRON_LEADER = "Rogue Squadron Leader";
    private static final String GRAND_MOFF_TARKIN = "Grand Moff Tarkin";
    private static final String COMMANDER_OF_THE_DEATH_STAR = "Commander of the Death Star";
    private static final String KILLED_IN_ACTION = "KiA";
    private static final String WEDGE_ID = "wedge";
    private static final String TARKIN_ID = "tarkin";

    private RecordService service;
    private File directory;

    @BeforeMethod
    public void setUp() throws Exception {
        // create a temporary empty directory to ensure test isolation
        directory = newTemporaryFolder();
        service = new RecordService(directory);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        delete(directory);
    }

    @Test
    public void shouldCreateRecord() throws Exception {
        // when
        Record record = service.create(json(object(field("name", WEDGE_ANTILLES))));

        // then
        assertThat(record.getId()).isNotNull();
        assertThat(record.getRevision()).isNotNull();
        assertThat(record.getContent().asMap()).containsOnly(entry("name", WEDGE_ANTILLES));

        assertThat(new File(directory, resourceFilename(record.getId()))).exists();
    }

    @Test
    public void shouldFindRecord() throws Exception {
        // given
        createWedgeAntillesResource();

        // when
        Record wedge = service.find(WEDGE_ID);

        // then
        assertThat(wedge.getId()).isEqualTo(WEDGE_ID);
        assertThat(wedge.getRevision()).isNotNull();
        assertThat(wedge.getContent()).isObject()
                                      .containsOnly(entry("name", WEDGE_ANTILLES),
                                                    entry("rank", ROGUE_SQUADRON_LEADER));
    }

    @Test
    public void shouldFailToFindUnknownRecord() throws Exception {
        assertThat(service.find("unknown")).isNull();
    }

    @Test
    public void shouldDeleteRecord() throws Exception {
        // given
        File resource = createGrandMoffTarkinResource();

        // when
        Record tarkin = service.delete(TARKIN_ID, "0");

        // then
        assertThat(tarkin.getId()).isEqualTo(TARKIN_ID);
        assertThat(tarkin.getRevision()).isEqualTo("0");
        assertThat(tarkin.getContent()).isObject()
                                       .containsOnly(entry("name", GRAND_MOFF_TARKIN),
                                                     entry("rank", COMMANDER_OF_THE_DEATH_STAR));
        assertThat(resource).doesNotExist();
    }

    @Test(expectedExceptions = RecordException.class)
    public void shouldNotDeleteRecordWhenRevisionDoesNotMatch() throws Exception {
        // given
        File resource = createGrandMoffTarkinResource();

        // when
        try {
            service.delete(TARKIN_ID, "unknown-revision");
        } finally {
            // Verify file is still there
            assertThat(resource).exists();
        }
    }

    @Test
    public void shouldReturnNullOnDeleteWhenRecordNotFound() throws Exception {
        assertThat(service.delete(TARKIN_ID, "0")).isNull();
    }

    @Test
    public void shouldQueryRecord() throws Exception {
        // given
        createWedgeAntillesResource();
        createGrandMoffTarkinResource();

        // when
        Set<Record> records = service.listAll();

        assertThat(records).hasSize(2)
                           .extracting(new Extractor<Record, String>() {
                               @Override
                               public String extract(Record input) {
                                   return input.getId();
                               }
                           })
                           .containsOnly(WEDGE_ID, TARKIN_ID);
    }

    @Test
    public void shouldUpdateRecord() throws Exception {
        // given
        File resource = createGrandMoffTarkinResource();
        JsonValue updated = json(object(field("name", GRAND_MOFF_TARKIN),
                                        field("rank", COMMANDER_OF_THE_DEATH_STAR),
                                        field("status", KILLED_IN_ACTION)));

        // when
        Record record = service.update(TARKIN_ID, "0", updated);

        // then
        assertThat(record.getId()).isEqualTo(TARKIN_ID);
        assertThat(record.getRevision()).isNotEqualTo("0").isNotNull();
        assertThat(record.getContent()).isObject()
                                       .containsOnly(entry("name", GRAND_MOFF_TARKIN),
                                                     entry("rank", COMMANDER_OF_THE_DEATH_STAR),
                                                     entry("status", KILLED_IN_ACTION));

        // Verify File is still there
        assertThat(resource).exists();
    }


    @Test(expectedExceptions = RecordException.class)
    public void shouldNotUpdateRecordWhenRevisionDoesNotMatch() throws Exception {
        // given
        createGrandMoffTarkinResource();

        // when
        service.update(TARKIN_ID, "1", json(object()));
    }

    @Test
    public void shouldReturnNullOnUpdateWhenRecordNotFound() throws Exception {
        assertThat(service.update(TARKIN_ID, "0", json(object()))).isNull();
    }

    private File createWedgeAntillesResource() throws IOException {
        return createTestResource(WEDGE_ID, WEDGE_ANTILLES, ROGUE_SQUADRON_LEADER);
    }

    private File createGrandMoffTarkinResource() throws IOException {
        return createTestResource(TARKIN_ID, GRAND_MOFF_TARKIN, COMMANDER_OF_THE_DEATH_STAR);
    }

    private File createTestResource(final String resourceId, final String name, final String rank) throws IOException {
        File resource = new File(directory, resourceFilename(resourceId));
        JsonValue content = json(object(field("name", name),
                                        field("rank", rank)));
        MAPPER.writeValue(resource, new Record(resourceId, "0", content));
        return resource;
    }
}
