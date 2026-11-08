package com.example.module_01.section_06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.gettingstarted.SpecificUsersFile;
import com.example.gettingstarted.Users;

import example.avro.User;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.util.ClassSecurityValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module 1.6 – deserializing {@code users.avro} back into {@code User} objects.
 * <p>
 * The guide reads with {@code next(user)}, passing the previous object back in so Avro can fill it again instead of
 * creating a new one. That saves garbage in a long loop, but the same object is returned every time.
 * <p>
 * Avro 1.12.2 adds a step the guide doesn't mention: a {@code SpecificDatumReader} only creates instances of classes
 * that are trusted. The parent POM trusts the package {@code example.avro} with the system property
 * {@code org.apache.avro.SERIALIZABLE_PACKAGES}. The last test shows what happens without it.
 */
class DeserializingTest {

    @TempDir
    Path dir;

    private File file;

    @BeforeEach
    void writeTheThreeUsers() throws IOException {
        file = dir.resolve("users.avro").toFile();
        SpecificUsersFile.write(file, List.of(Users.alyssa(), Users.ben(), Users.charlie()));
    }

    @AfterEach
    void restoreTheDefaultClassTrust() {
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.DEFAULT);
    }

    @Test
    void theThreeUsersAreReadBackInOrder() throws IOException {
        List<User> users = SpecificUsersFile.read(file);

        assertThat(users).containsExactly(Users.alyssa(), Users.ben(), Users.charlie());
    }

    @Test
    void theGuidesLoopReusesOneUserObject() throws IOException {
        List<String> names = new ArrayList<>();
        try (var dataFileReader = new DataFileReader<>(file, new SpecificDatumReader<>(User.class))) {
            User user = null;
            while (dataFileReader.hasNext()) {
                user = dataFileReader.next(user);
                names.add(user.getName().toString());        // copy the value before the next read
            }
        }

        assertThat(names).containsExactly("Alyssa", "Ben", "Charlie");
    }

    @Test
    void keepingTheReusedObjectKeepsOnlyTheLastUser() throws IOException {
        List<User> kept = new ArrayList<>();
        try (var dataFileReader = new DataFileReader<>(file, new SpecificDatumReader<>(User.class))) {
            User user = null;
            while (dataFileReader.hasNext()) {
                user = dataFileReader.next(user);
                kept.add(user);                              // the bug: the same object, three times
            }
        }

        assertThat(kept).hasSize(3);
        assertThat(kept).extracting(user -> user.getName().toString()).containsOnly("Charlie");
    }

    @Test
    void readStringsAreUtf8NotString() throws IOException {
        User ben = SpecificUsersFile.read(file).get(1);

        // The generated getter returns CharSequence (section 3). After reading, that is Avro's own Utf8 class, which
        // is not equal to a String with the same text.
        assertThat(ben.getName()).isInstanceOf(org.apache.avro.util.Utf8.class);
        assertThat(ben.getName()).isNotEqualTo("Ben");
        assertThat(ben.getName()).hasToString("Ben");
    }

    @Test
    void readingGeneratedClassesRequiresTrustingTheirPackage() {
        // Trust only Avro's built-in list (java.lang and java.math types), ignoring SERIALIZABLE_PACKAGES. A new
        // SpecificData has no cached classes yet, so it has to load and check example.avro.User.
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.DEFAULT_TRUSTED_CLASSES);
        var untrustingReader = new SpecificDatumReader<User>(User.getClassSchema(), User.getClassSchema(),
            new SpecificData());

        assertThatThrownBy(() -> {
            try (var dataFileReader = new DataFileReader<>(file, untrustingReader)) {
                dataFileReader.next();
            }
        })
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("example.avro.User")
            .hasMessageContaining("org.apache.avro.SERIALIZABLE_PACKAGES");
    }
}
