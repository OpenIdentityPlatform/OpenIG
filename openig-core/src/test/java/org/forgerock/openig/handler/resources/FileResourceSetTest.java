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

package org.forgerock.openig.handler.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.Files.getRelative;
import static org.forgerock.openig.Files.getRelativeDirectory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import org.forgerock.openig.handler.ClientHandlerTest;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FileResourceSetTest {

    @Test
    public void shouldFindResource() throws Exception {
        File directory = getRelativeDirectory(getClass(), "");
        ResourceSet resourceSet = new FileResourceSet(directory);

        // Ensure resource exists
        File file = new File(directory, "README.md");
        assertThat(file).isFile();

        Resource resource = resourceSet.find("README.md");
        assertThat(resource).isNotNull();

        assertThat(resource.getType()).isEqualTo("text/markdown");
        assertThat(resource.getLastModified()).isEqualTo(file.lastModified());
        try (InputStream stream = resource.open()) {
            assertThat(stream).isNotNull()
                              .hasSameContentAs(new ByteArrayInputStream("# Hello World".getBytes()));
        }
    }

    @Test
    public void shouldNotFindResource() throws Exception {
        ResourceSet resourceSet = new FileResourceSet(getRelativeDirectory(getClass(), ""));
        assertThat(resourceSet.find("ABSENT")).isNull();
    }

    @Test
    public void shouldNotServeDirectoryResource() throws Exception {
        File directory = getRelativeDirectory(ClientHandlerTest.class, "");
        assertThat(new File(directory, "resources")).isDirectory();

        ResourceSet resourceSet = new FileResourceSet(directory);
        assertThat(resourceSet.find("resources")).isNull();
    }

    @Test
    public void shouldNotFindResourceOutsideOfRootDirectory() throws Exception {
        File directory = getRelativeDirectory(getClass(), "");
        // Verify that the file exists
        assertThat(new File(directory, "../ClientHandlerTest.class")).exists();

        // But we can't access it through the resource-set
        ResourceSet resourceSet = new FileResourceSet(directory);
        assertThat(resourceSet.find("../ClientHandlerTest.class")).isNull();
    }

    @Test
    public void shouldProduceValidResource() throws Exception {
        File file = getRelative(getClass(), "README.md");
        FileResourceSet.FileResource resource = new FileResourceSet.FileResource(file, "/path/README.md");

        assertThat(resource.getLastModified()).isEqualTo(file.lastModified());
        try (InputStream stream = resource.open()) {
            assertThat(stream).isNotNull()
                              .hasSameContentAs(new ByteArrayInputStream("# Hello World".getBytes()));
        }
    }

}
