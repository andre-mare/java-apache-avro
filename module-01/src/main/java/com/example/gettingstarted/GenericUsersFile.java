package com.example.gettingstarted;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.SchemaParser;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;

/**
 * The guide's "Serializing and deserializing without code generation" (section 1.7): the same users, with the
 * schema parsed at runtime and the data held in {@link GenericRecord}s instead of generated classes.
 */
public final class GenericUsersFile {

    /**
     * Where the schema is on the classpath. Maven copies {@code src/main/resources/avro/user.avsc} to
     * {@code target/classes/avro/user.avsc}, and it is packaged in the module's jar, so this works wherever the code
     * runs, not only from the module directory.
     */
    public static final String SCHEMA_RESOURCE = "avro/user.avsc";

    private GenericUsersFile() {
    }

    /** Parses {@code user.avsc} from the classpath with the {@link SchemaParser} that Avro 1.12 recommends. */
    public static Schema schema() throws IOException {
        try (InputStream in = GenericUsersFile.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new FileNotFoundException("No schema on the classpath at " + SCHEMA_RESOURCE);
            }
            return new SchemaParser().parse(in).mainSchema();
        }
    }

    /** The guide's first generic user. Fields you don't put stay null. */
    public static GenericRecord alyssa(Schema schema) {
        GenericRecord user1 = new GenericData.Record(schema);
        user1.put("name", "Alyssa");
        user1.put("favorite_number", 256);
        // Leave favorite color null
        return user1;
    }

    /** The guide's second generic user. */
    public static GenericRecord ben(Schema schema) {
        GenericRecord user2 = new GenericData.Record(schema);
        user2.put("name", "Ben");
        user2.put("favorite_number", 7);
        user2.put("favorite_color", "red");
        return user2;
    }

    public static void write(File file, Schema schema, List<GenericRecord> users) throws IOException {
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        try (DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {
            dataFileWriter.create(schema, file);
            for (GenericRecord user : users) {
                dataFileWriter.append(user);
            }
        }
    }

    /**
     * Reads every user with a new record each time ({@code next()} without an argument), so the result can be kept
     * as it is. The guide's reuse of one record is shown in section 1.6.
     */
    public static List<GenericRecord> read(File file, Schema schema) throws IOException {
        List<GenericRecord> users = new ArrayList<>();
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
        try (DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(file, datumReader)) {
            while (dataFileReader.hasNext()) {
                users.add(dataFileReader.next());
            }
        }
        return users;
    }
}
