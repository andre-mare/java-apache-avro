package com.example.module_01.section_07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.gettingstarted.GenericUsersFile;
import com.example.gettingstarted.SpecificUsersFile;
import com.example.gettingstarted.Users;

import example.avro.User;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module 1.7 – serializing and deserializing without code generation.
 * <p>
 * The guide's second approach: parse {@code user.avsc} at runtime and hold the data in {@link GenericRecord}s. No
 * class is generated, so this works for schemas you only learn about while the program runs, such as tools and
 * routers. The price is that field names are strings, and mistakes show up at runtime instead of compile time. See
 * {@link GenericUsersFile}.
 */
class GenericUsersTest {

    private static Schema schema;

    @TempDir
    Path dir;

    @BeforeAll
    static void parseTheSchema() throws IOException {
        schema = GenericUsersFile.schema();
    }

    @Test
    void genericUsersSurviveAWriteAndRead() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        GenericUsersFile.write(file, schema, List.of(GenericUsersFile.alyssa(schema), GenericUsersFile.ben(schema)));

        List<GenericRecord> users = GenericUsersFile.read(file, schema);

        assertThat(users).hasSize(2);
        assertThat(users.get(0).get("favorite_number")).isEqualTo(256);
        assertThat(users.get(0).get("favorite_color")).isNull();
        assertThat(users.get(1).get("favorite_color")).hasToString("red");
    }

    @Test
    void aRecordIsEqualToTheRecordItWasReadFrom() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        GenericUsersFile.write(file, schema, List.of(GenericUsersFile.ben(schema)));

        // GenericData compares strings by their text, so a String and a Utf8 with the same text count as equal here.
        assertThat(GenericUsersFile.read(file, schema)).containsExactly(GenericUsersFile.ben(schema));
    }

    @Test
    void stringsComeBackAsUtf8() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        GenericUsersFile.write(file, schema, List.of(GenericUsersFile.alyssa(schema)));

        Object name = GenericUsersFile.read(file, schema).get(0).get("name");

        assertThat(name).isInstanceOf(Utf8.class);
        assertThat(name).isNotEqualTo("Alyssa");             // Utf8.equals(String) is false
        assertThat(name).hasToString("Alyssa");              // so compare the text
    }

    @Test
    void anUnknownFieldIsRejected() {
        GenericRecord user = new GenericData.Record(schema);

        // As the guide says: a field that isn't in the schema is an error at once.
        assertThatThrownBy(() -> user.put("favorite_animal", "cat"))
            .isInstanceOf(AvroRuntimeException.class)
            .hasMessageContaining("favorite_animal");
    }

    @Test
    void fieldsCanBeReachedByPositionToo() {
        GenericRecord ben = GenericUsersFile.ben(schema);

        assertThat(ben.get(0)).isEqualTo(ben.get("name"));
        assertThat(ben.get(2)).isEqualTo("red");
    }

    @Test
    void theGenericFileReadsAsGeneratedUsers() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        GenericUsersFile.write(file, schema, List.of(GenericUsersFile.alyssa(schema), GenericUsersFile.ben(schema)));

        // The bytes don't remember how they were written: the same file reads as generated User objects.
        List<User> users = SpecificUsersFile.read(file);

        assertThat(users).containsExactly(Users.alyssa(), Users.ben());
    }
}
