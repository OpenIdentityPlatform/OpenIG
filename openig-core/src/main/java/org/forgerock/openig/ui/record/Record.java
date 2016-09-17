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

import static org.forgerock.json.JsonValue.json;
import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.json.JsonValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a persisted JSON object.
 */
class Record {
    private final String id;
    private final String revision;
    private final JsonValue content;

    @JsonCreator
    private Record(@JsonProperty("id") String id,
                   @JsonProperty("rev") String revision,
                   @JsonProperty("content") Object content) {
        this(id, revision, json(content));
    }

    Record(String id, String revision, JsonValue content) {
        this.id = checkNotNull(id);
        this.revision = checkNotNull(revision);
        this.content = checkNotNull(content);
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("rev")
    public String getRevision() {
        return revision;
    }

    @JsonProperty("content")
    public JsonValue getContent() {
        return content;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Record[");
        sb.append("id='").append(id).append('\'');
        sb.append(", revision=").append(revision);
        sb.append(']');
        return sb.toString();
    }
}
