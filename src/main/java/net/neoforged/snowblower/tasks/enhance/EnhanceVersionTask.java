/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.snowblower.tasks.enhance;

import net.neoforged.snowblower.data.Version;
import net.neoforged.snowblower.util.HashFunction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnhanceVersionTask {
    // TODO: I moved this out to its own package even tho its only one function, because I'm curious if we can cache it.
    // And doing that via a package level would be useful.

    /**
     * Extra compile-only dependencies that are stripped from the version json.
     * This also includes dependencies with bad OS filtering rules when they are needed on all systems for compiling.
     * Adding these to all generated Minecraft versions should be mostly safe.
     */
    private static final List<String> EXTRA_DEPENDENCIES = List.of("org.jetbrains:annotations:24.1.0", "com.google.code.findbugs:jsr305:3.0.2", "ca.weblite:java-objc-bridge:1.1");
    private static final String BUILD_GRADLE_CONTENT = """
            plugins {
                id 'java'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(%java_version%)
                }
            }

            repositories {
                mavenCentral()
                maven {
                    name = 'Mojang'
                    url = 'https://libraries.minecraft.net/'
                }
            }

            dependencies {
            %deps%
            }
            """;
    private static final String SETTINGS_GRADLE_CONTENT = """
            plugins {
                id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
            }
            """;

    public static List<Path> enhance(Path output, Version version) throws IOException {
        var buildData = BUILD_GRADLE_CONTENT
                .replace("%java_version%", Integer.toString(version.javaVersion().majorVersion())) // This assumes the minimum to be 8 (which it is)
                .replace("%deps%", Stream.concat(version.libraries().stream()
                                .filter(Version.Library::isAllowed)
                                .map(Version.Library::name), EXTRA_DEPENDENCIES.stream())
                        .sorted()
                        .map(lib -> "    implementation '" + lib + '\'')
                        .collect(Collectors.joining("\n")))
                .getBytes(StandardCharsets.UTF_8);
        var settingsData = SETTINGS_GRADLE_CONTENT.getBytes(StandardCharsets.UTF_8);

        List<Path> added = new ArrayList<>();

        writeCached(buildData, added, output.resolve("build.gradle"));
        writeCached(settingsData, added, output.resolve("settings.gradle"));

        return added;
    }

    private static void writeCached(byte[] data, List<Path> added, Path path) throws IOException {
        var existing = Files.exists(path) ? HashFunction.MD5.hash(path) : "";
        var created = HashFunction.MD5.hash(data);

        if (!existing.equals(created)) {
            Files.write(path, data);
            added.add(path);
        }
    }
}
