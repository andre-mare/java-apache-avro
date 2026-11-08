package com.example.module_01.section_01;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import example.avro.User;

import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/**
 * Module 1.1 – setting up the project.
 * <p>
 * The Getting Started guide asks for two things in the POM: the {@code avro} dependency, and the
 * {@code avro-maven-plugin} running its {@code schema} goal in the {@code generate-sources} phase. These tests check
 * that the build did what the POM asks, before any Avro code runs.
 * <p>
 * Maven runs tests with the module directory as the working directory, so the paths below are relative to
 * {@code module-01}.
 */
class ProjectSetupTest {

    @Test
    void theUserClassIsGeneratedDuringTheBuild() {
        // If this compiles, the plugin generated the class before the test sources were compiled.
        assertThat(User.getClassSchema().getFullName()).isEqualTo("example.avro.User");
        assertThat(SpecificRecord.class).isAssignableFrom(User.class);
    }

    @Test
    void theGeneratedSourceIsBuildOutput() {
        // The default outputDirectory, which this course keeps. Running 'mvn clean' deletes it; the next build
        // generates it again.
        assertThat(Path.of("target/generated-sources/avro/example/avro/User.java")).isRegularFile();

        // The guide's outputDirectory, src/main/java, would mix generated code with code you write and commit.
        assertThat(Path.of("src/main/java/example/avro/User.java")).doesNotExist();
    }

    @Test
    void theSchemaFileIsWhereThePluginLooksForIt() {
        // The POM points the plugin's sourceDirectory at src/main/resources/avro instead of its default, src/main/avro.
        assertThat(Path.of("src/main/resources/avro/user.avsc")).isRegularFile();
        assertThat(Path.of("src/main/avro")).doesNotExist();
    }

    @Test
    void theSchemaIsAlsoOnTheClasspath() {
        // A side effect of keeping schemas under resources: Maven copies them to target/classes, so code can load the
        // schema at runtime from the classpath (section 7), and it ships inside the jar.
        assertThat(Path.of("target/classes/avro/user.avsc")).isRegularFile();
        assertThat(getClass().getClassLoader().getResource("avro/user.avsc")).isNotNull();
    }
}
