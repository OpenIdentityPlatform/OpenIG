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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openig.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** Checks the document titles Antora uses for generated pages. */
@SuppressWarnings("javadoc")
public class AntoraPageTitlesIT {
    @Test
    public void allGeneratedPagesHaveDocumentTitles() throws IOException {
        final String modules = System.getProperty("antoraModulesDirectory");
        if (modules == null) {
            throw new SkipException("Antora pages are generated with the man-pages profile");
        }
        final Path modulesDirectory = Path.of(modules);
        assertThat(modulesDirectory).as("Generated Antora modules").isDirectory();

        final List<Path> pages;
        try (Stream<Path> paths = Files.walk(modulesDirectory)) {
            pages = paths.filter(Files::isRegularFile)
                    .map(modulesDirectory::relativize)
                    .filter(path -> path.getNameCount() >= 3 && "pages".equals(path.getName(1).toString()))
                    .filter(path -> path.getFileName().toString().endsWith(".adoc"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            asciidoctor.javaExtensionRegistry().includeProcessor(
                    new RootPartialIncludeProcessor(modulesDirectory.resolve("ROOT/partials")));
            // A full parse can fall back to a section heading even when the document has no title.
            final Options headerOptions = Options.builder().safe(SafeMode.SAFE)
                    .attributes(Attributes.builder().attribute("env-site").build()).parseHeaderOnly(true).build();
            assertThat(pages).as("Antora pages in %s", modulesDirectory).isNotEmpty().allSatisfy(page -> {
                final Document document = asciidoctor.loadFile(modulesDirectory.resolve(page).toFile(), headerOptions);
                assertThat(document.getDoctitle()).as("document title of %s", page).isNotBlank();
                // A :title: override also supplies a title for documents that have no header.
                if (document.getAttributes().remove("title") != null) {
                    assertThat(document.getDoctitle()).as("document header title of %s", page).isNotBlank();
                }
            });
        }
    }

    private static final class RootPartialIncludeProcessor extends IncludeProcessor {
        private static final String PREFIX = "ROOT:partial$";
        private final Path partialsDirectory;

        private RootPartialIncludeProcessor(Path partialsDirectory) {
            this.partialsDirectory = partialsDirectory.toAbsolutePath().normalize();
        }

        @Override
        public boolean handles(String target) {
            return target.startsWith(PREFIX);
        }

        @Override
        public void process(Document document, PreprocessorReader reader, String target, Map<String, Object> attributes) {
            final Path partial = partialsDirectory.resolve(target.substring(PREFIX.length())).normalize();
            if (!partial.startsWith(partialsDirectory)) {
                throw new IllegalArgumentException("Invalid Antora ROOT partial: " + target);
            }
            final String path = partial.toString();
            try {
                reader.pushInclude(Files.readString(partial), path, path, 1, attributes);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read Antora partial " + partial, e);
            }
        }
    }
}
