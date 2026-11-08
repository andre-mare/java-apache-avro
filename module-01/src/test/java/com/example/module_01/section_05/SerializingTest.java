package com.example.module_01.section_05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.gettingstarted.SpecificUsersFile;
import com.example.gettingstarted.Users;

import example.avro.User;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module 1.5 – serializing the three users to {@code users.avro}.
 * <p>
 * A {@code SpecificDatumWriter} turns a {@code User} into Avro binary. A {@code DataFileWriter} wraps it in the
 * object container file format: a header with the schema, then blocks of records. See
 * {@link SpecificUsersFile#write}.
 * <p>
 * The guide writes {@code users.avro} into the working directory. These tests use a JUnit {@link TempDir}, which is
 * new for every test and deleted afterwards, so no test sees another's file.
 */
class SerializingTest {

    @TempDir
    Path dir;

    @Test
    void threeUsersAreWrittenToUsersAvro() throws IOException {
        File file = dir.resolve("users.avro").toFile();

        SpecificUsersFile.write(file, List.of(Users.alyssa(), Users.ben(), Users.charlie()));

        // Every Avro data file starts with the magic bytes "Obj" and the format version, 1.
        assertThat(Files.readAllBytes(file.toPath())).startsWith(new byte[] {'O', 'b', 'j', 1});
    }

    @Test
    void theFileStoresTheSchemaInItsHeader() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        SpecificUsersFile.write(file, List.of(Users.ben()));

        // Opening the file reads only its header. The reader is given no schema; it takes the one in the file.
        try (var reader = new DataFileReader<GenericRecord>(file, new GenericDatumReader<>())) {
            assertThat(reader.getSchema()).isEqualTo(User.getClassSchema());
            assertThat(reader.getMetaString("avro.schema")).contains("\"name\":\"User\"");
            // Without compression, Avro leaves the codec out of the header; a reader treats a missing codec as
            // "null", no compression. Module 8 adds a codec.
            assertThat(reader.getMetaKeys()).containsExactly("avro.schema");
        }
    }

    @Test
    void theFileIsCompleteWhenTheWriterIsClosed() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        var writer = new DataFileWriter<>(new SpecificDatumWriter<>(User.class));
        writer.create(User.getClassSchema(), file);
        writer.append(Users.ben());

        long beforeClose = file.length();          // the header only: Ben is still in the writer's buffer
        writer.close();                             // writes the last block and closes the file

        assertThat(file.length()).isGreaterThan(beforeClose);
    }

    @Test
    void aUserMissingARequiredFieldCannotBeWritten() {
        File file = dir.resolve("users.avro").toFile();
        User nobody = new User();                   // section 4: the constructor checked nothing

        // The data file writer wraps the problem; the cause says which field of which record was null.
        assertThatThrownBy(() -> SpecificUsersFile.write(file, List.of(nobody)))
            .isInstanceOf(DataFileWriter.AppendWriteException.class)
            .cause()
            .isInstanceOf(NullPointerException.class)
            .hasMessage("null value for (non-nullable) string at User.name");
    }
}
