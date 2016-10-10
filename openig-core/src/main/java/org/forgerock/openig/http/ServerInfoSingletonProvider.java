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

package org.forgerock.openig.http;

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.util.VersionUtil.getBranch;
import static org.forgerock.openig.util.VersionUtil.getRevision;
import static org.forgerock.openig.util.VersionUtil.getTimeStamp;
import static org.forgerock.openig.util.VersionUtil.getVersion;

import org.forgerock.api.annotations.Handler;
import org.forgerock.api.annotations.Operation;
import org.forgerock.api.annotations.Read;
import org.forgerock.api.annotations.Schema;
import org.forgerock.api.annotations.SingletonProvider;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Responses;
import org.forgerock.json.resource.SingletonResourceProvider;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * Provides server info (build-time defined values only at the moment) in a read-only fashion.
 */
@SingletonProvider(@Handler(id = "server-info",
                            title = "i18n:#service.title",
                            description = "i18n:#service.desc",
                            resourceSchema = @Schema(id = "server-info", schemaResource = "server-info.json"),
                            mvccSupported = false))
public class ServerInfoSingletonProvider implements SingletonResourceProvider {

    @Override
    @Read(operationDescription = @Operation(description = "i18n:#read.desc"))
    public Promise<ResourceResponse, ResourceException> readInstance(final Context context, final ReadRequest request) {
        JsonValue info = json(object(field("version", getVersion()),
                                     field("revision", getRevision()),
                                     field("branch", getBranch()),
                                     field("timestamp", getTimeStamp())));
        return Responses.newResourceResponse(null, null, info).asPromise();
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(final Context context,
                                                                     final ActionRequest request) {
        return new NotSupportedException().asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(final Context context,
                                                                      final PatchRequest request) {
        return new NotSupportedException().asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(final Context context,
                                                                       final UpdateRequest request) {
        return new NotSupportedException().asPromise();
    }
}
