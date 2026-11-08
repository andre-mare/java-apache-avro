package com.example.module_01.section_08;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.gettingstarted.SpecificUsersFile;
import com.example.gettingstarted.Users;

import example.avro.User;

import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module 1.8 – the beta feature "generating faster code".
 * <p>
 * Generated classes contain hand-written-style encode and decode methods, which skip the generic walk over the
 * schema. They are used only when the system property {@code org.apache.avro.specific.use_custom_coders} is
 * {@code true}. Avro reads it once, so this class runs in its own Surefire execution that sets it (see this module's
 * POM). Run it on its own with:
 * <pre>mvn test -pl module-01 -Dtest=CustomCodersTest -Dorg.apache.avro.specific.use_custom_coders=true</pre>
 */
class CustomCodersTest {

    @TempDir
    Path dir;

    @Test
    void theFlagIsOn() {
        assertThat(SpecificData.getForClass(User.class).useCustomCoders()).isTrue();
    }

    @Test
    void usersRoundTripWithCustomCoders() throws IOException {
        File file = dir.resolve("users.avro").toFile();
        List<User> users = List.of(Users.alyssa(), Users.ben(), Users.charlie());

        SpecificUsersFile.write(file, users);

        assertThat(SpecificUsersFile.read(file)).containsExactlyElementsOf(users);
    }

    @Test
    void theBytesAreTheSameEitherWay() throws IOException {
        // The custom coders are a faster route to the same format. A GenericDatumWriter never uses them.
        User ben = Users.ben();

        byte[] custom = write(new SpecificDatumWriter<>(User.class), ben);
        byte[] generic = write(new GenericDatumWriter<>(User.getClassSchema()), ben);

        assertThat(custom).isEqualTo(generic);
    }

    private static byte[] write(DatumWriter<User> writer, User user) throws IOException {
        var out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(user, encoder);
        encoder.flush();
        return out.toByteArray();
    }
}
