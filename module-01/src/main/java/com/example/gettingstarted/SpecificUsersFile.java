package com.example.gettingstarted;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import example.avro.User;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

/**
 * Writing and reading {@code users.avro} with the generated {@link User} class: the guide's "Serializing" and
 * "Deserializing" steps (sections 1.5 and 1.6).
 * <p>
 * The guide calls {@code close()} itself; here try-with-resources does it, also when something fails halfway.
 */
public final class SpecificUsersFile {

    private SpecificUsersFile() {
    }

    /** Serializing: a datum writer for the class, wrapped in a data file writer that adds the file format. */
    public static void write(File file, List<User> users) throws IOException {
        DatumWriter<User> userDatumWriter = new SpecificDatumWriter<>(User.class);
        try (DataFileWriter<User> dataFileWriter = new DataFileWriter<>(userDatumWriter)) {
            dataFileWriter.create(users.get(0).getSchema(), file);
            for (User user : users) {
                dataFileWriter.append(user);
            }
        }
    }

    /**
     * Deserializing, as the guide does it: one {@code User} object is reused for every record, which saves
     * allocating a new object each time. Each user is copied into the result before the next read overwrites it.
     */
    public static List<User> read(File file) throws IOException {
        List<User> users = new ArrayList<>();
        DatumReader<User> userDatumReader = new SpecificDatumReader<>(User.class);
        try (DataFileReader<User> dataFileReader = new DataFileReader<>(file, userDatumReader)) {
            User user = null;
            while (dataFileReader.hasNext()) {
                user = dataFileReader.next(user);
                users.add(User.newBuilder(user).build());
            }
        }
        return users;
    }
}
