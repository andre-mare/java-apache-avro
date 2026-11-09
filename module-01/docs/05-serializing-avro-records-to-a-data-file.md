# Serializing Avro Records to a Data File, Byte by Byte

Serialization tutorials usually stop at "the file was written". You see a `DataFileWriter`, a loop of `append` calls, and a file on disk that no one ever opens. That leaves the most useful part of Avro invisible: how compact the data really is, why a reader with no schema can still read it, and what happens to a record that breaks the schema on its way in.

This guide follows the [Serializing](https://avro.apache.org/docs/++version++/getting-started-java/#serializing) step of Apache Avro 1.12.2's Getting Started (Java) guide. You'll write the three users from [section 1.4](04-creating-avro-records-in-java.md) to `users.avro`, then open the 292-byte result and decode every byte of it against the [Object Container Files](https://avro.apache.org/docs/++version++/specification/#object-container-files) and [Binary Encoding](https://avro.apache.org/docs/++version++/specification/#binary-encoding) sections of the specification. Four JUnit 6 tests in `SerializingTest` check what you see.

## What is an Avro Data File?

An Avro data file, which the specification calls an **object container file**, is a file of records with the schema in its header. That makes it self-describing as a whole, even though each individual record carries no field names and no type information.

Two layers of the Java API work together to write one:

- **A `DatumWriter`** knows how to encode one value with a schema. `SpecificDatumWriter<User>` encodes generated `User` objects; `GenericDatumWriter` encodes generic records ([section 1.7](07-avro-without-code-generation.md)).
- **A `DataFileWriter`** wraps a datum writer and adds the file format: the header, blocks of records, and sync markers between them.

You use the first to say *what* a record is and the second to say *where* it goes.

## Serializing Overview

Writing a data file always follows the same five steps:

1. **Create a datum writer** for the record class.
2. **Wrap it in a data file writer.**
3. **Call `create(schema, file)`**, which writes the header with the schema.
4. **Call `append(record)`** for each record. Records are encoded into an in-memory block.
5. **Call `close()`**, which writes the last block and closes the file.

## The Serialization Code

### Step 1: Create a DatumWriter

```java
DatumWriter<User> userDatumWriter = new SpecificDatumWriter<>(User.class);
```

The datum writer reads the schema from the generated class. It holds no file; it only knows how to encode.

### Step 2: Wrap It in a DataFileWriter

```java
DataFileWriter<User> dataFileWriter = new DataFileWriter<>(userDatumWriter);
```

### Step 3: Create the File

```java
dataFileWriter.create(users.get(0).getSchema(), file);
```

This writes the header, including the schema, straight away.

### Step 4: Append the Records

```java
for (User user : users) {
    dataFileWriter.append(user);
}
```

### Step 5: Close the Writer

The course's `SpecificUsersFile.write(..)` puts it all together with try-with-resources, so the writer is closed even when an `append` fails:

```java
public static void write(File file, List<User> users) throws IOException {
    DatumWriter<User> userDatumWriter = new SpecificDatumWriter<>(User.class);
    try (DataFileWriter<User> dataFileWriter = new DataFileWriter<>(userDatumWriter)) {
        dataFileWriter.create(users.get(0).getSchema(), file);
        for (User user : users) {
            dataFileWriter.append(user);
        }
    }
}
```

## Walkthrough: users.avro Decoded

Write Alyssa, Ben and Charlie and the file is 292 bytes. Here they all are.

### The Header

```text
00000000  4f 62 6a 01 02 16 61 76 72 6f 2e 73 63 68 65 6d  |Obj...avro.schem|
00000010  61 92 03 7b 22 74 79 70 65 22 3a 22 72 65 63 6f  |a..{"type":"reco|
   ...    (the schema JSON continues)
000000d0  22 2c 22 6e 75 6c 6c 22 5d 7d 5d 7d 00 d9 26 ea  |","null"]}]}..&.|
000000e0  0e a4 1e 38 b0 aa 67 f1 c1 b1 8d a9 2e           |...8..g......   |
```

Read it left to right:

- **`4f 62 6a 01`** is the magic: the ASCII letters `Obj` followed by the format version, 1.
- **`02`** starts the file metadata, a map. Avro writes counts and lengths as zig-zag varints, so `02` means 1 entry.
- **`16 61 76 72 6f ...`** is the key: length 11 (`16`), then `avro.schema`.
- **`92 03`** is the value's length: 201 bytes, followed by the schema's compact JSON.
- **`00`** ends the map. There's no `avro.codec` entry: without compression, Avro leaves the codec out, and a reader treats a missing codec as `null`, no compression.
- **`d9 26 ea 0e a4 1e 38 b0 aa 67 f1 c1 b1 8d a9 2e`** is the 16-byte **sync marker**, random for every file.

The header ends at byte 237.

### The Data Block

```text
000000e0                                         06 4a 0c  |            .J.|
000000f0  41 6c 79 73 73 61 00 80 04 02 06 42 65 6e 00 0e  |Alyssa.....Ben..|
00000100  00 06 72 65 64 0e 43 68 61 72 6c 69 65 02 00 08  |..red.Charlie...|
00000110  62 6c 75 65 d9 26 ea 0e a4 1e 38 b0 aa 67 f1 c1  |blue.&....8..g..|
00000120  b1 8d a9 2e                                      |....|
```

- **`06`** is the number of records in the block: 3.
- **`4a`** is the size of the block's data: 37 bytes.
- Then the three records, and the sync marker again, to close the block.

### The Records

Each record is just its field values, in schema order. Strings are a length and UTF-8 bytes; a union is the branch index, then the value:

- **Alyssa: `0c "Alyssa" 00 80 04 02`.** Name of length 6; `favorite_number` is branch 0 (`int`) with the value 256 as `80 04`; `favorite_color` is branch 1 (`null`), which has no bytes.
- **Ben: `06 "Ben" 00 0e 00 06 "red"`.** Name of length 3; branch 0, 7 encoded as `0e`; branch 0 (`string`), length 3, `red`.
- **Charlie: `0e "Charlie" 02 00 08 "blue"`.** Name of length 7; branch 1 (`null`); branch 0, length 4, `blue`.

Ben's whole record is 11 bytes. The same data as JSON is 62. No field names, no quotes, no braces: the schema in the header supplies all of that.

### Why Zig-Zag?

Avro writes `int` and `long` values as zig-zag varints: `0 → 00`, `-1 → 01`, `1 → 02`, `-2 → 03`, and so on, so small numbers of either sign take one byte. That's why 7 is `0e` (14) and the length 3 is `06`. 256 becomes 512, which needs two varint bytes, `80 04`.

> **Run every example yourself**
> All the code in this post is in Module 1 of the Java Apache Avro course: a Maven project where every Avro concept is a JUnit 6 test you can run, break and fix.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Read the course outline →](https://github.com/andre-mare/java-apache-avro/blob/main/Apache%20Avro%20Outline.md)

## Buffering: Nothing Is Written Until the Block Is

Records are collected in memory and written as a block when the block is full or the writer is closed. Write only Ben and look at the file size before and after `close()`:

```text
before close 237
after close 266
```

237 bytes is the header alone. Closing adds 29: the record count and block size (2 bytes), Ben (11 bytes) and the sync marker (16 bytes). A writer you forget to close leaves a file with a header and no data, which is why the course always uses try-with-resources.

## Writing a Record That Breaks the Schema

[Section 1.4](04-creating-avro-records-in-java.md) showed that `new User()` creates a user without a name and doesn't complain. Writing it does:

```text
org.apache.avro.file.DataFileWriter$AppendWriteException: java.lang.NullPointerException: null value for (non-nullable) string at User.name
```

The exception is wrapped twice, so it's worth knowing where the useful message is:

- **The outer exception** is `DataFileWriter.AppendWriteException`, thrown by `append`.
- **Its cause** is a `NullPointerException` with the message `null value for (non-nullable) string at User.name`: the type, the record and the field.
- **The root cause** is the JVM's own `Cannot invoke "Object.getClass()" because "datum" is null`, which tells you nothing about the schema.

So when you catch a write failure, log the cause, not the root.

## Testing the Data File

`SerializingTest` writes to a JUnit `@TempDir`, so nothing is left behind:

```java
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
```

The second test opens the file with a `GenericDatumReader` that was given no schema at all, and still gets the right one back. That's the self-describing header at work.

## Common Pitfalls

1. **Not closing the writer.** The last block lives in memory until `close()` or `flush()`. Use try-with-resources.
2. **Expecting a codec entry.** An uncompressed file has only `avro.schema` in its metadata. Code that requires `avro.codec` will fail on perfectly valid files.
3. **Logging the root cause.** The useful message is on the `NullPointerException` cause, not on the root.
4. **Assuming each record is self-describing.** Records have no field names. A record copied out of a data file without its header can only be read by someone who knows the exact schema it was written with. Module 6 covers formats for single records.

## Wrapping Up

A `SpecificDatumWriter` encodes records and a `DataFileWriter` adds the container: a header with the magic, the schema and a sync marker, then blocks of records. Decoded byte by byte, the three users take 37 bytes of data, because the schema in the header carries every name and type. Nothing reaches the disk until the writer closes, and a record that breaks the schema fails at `append`, with the explanation on the exception's cause.

Next, [section 1.6](06-deserializing-avro-data-files.md) reads the file back, including the reuse trap in the guide's own loop and the class trust check that Avro 1.12.2 added.

> **Run every example yourself**
> Run `mvn test -pl module-01 -Dtest=SerializingTest`, then write the three users to a real file and run `xxd users.avro` to see the bytes above for yourself.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Module 1 README →](https://github.com/andre-mare/java-apache-avro/blob/main/module-01/README.md)

---

**Filed under:** Apache Avro, Module 1: Getting Started

**Tags:** `apache-avro` `serialization` `datafilewriter` `object-container-file` `binary-encoding` `java` `junit6`

### About the Author

**André Maré** is a Senior Security Architect working on identity and access management for financial services and open banking. He writes example-driven courses in which every concept is a test you can run. The code for this series is at [github.com/andre-mare/java-apache-avro](https://github.com/andre-mare/java-apache-avro).

[GitHub](https://github.com/andre-mare) · [Website](https://andre-mare.github.io)

### Related Articles

- **Apache Avro · Module 1:** [Creating Avro Records in Java: Setters, Constructors and Builders](04-creating-avro-records-in-java.md) · 8 November 2026
- **Apache Avro · Module 1:** [Deserializing Avro Data Files: Object Reuse, Utf8 and Trusted Classes](06-deserializing-avro-data-files.md) · 8 November 2026
- **Apache Avro · Module 1:** [Avro Without Code Generation: Working with GenericRecord](07-avro-without-code-generation.md) · 8 November 2026

**References:** [Serializing](https://avro.apache.org/docs/++version++/getting-started-java/#serializing) · [Object Container Files](https://avro.apache.org/docs/++version++/specification/#object-container-files) · [Binary Encoding](https://avro.apache.org/docs/++version++/specification/#binary-encoding) · [DataFileWriter](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/file/DataFileWriter.html) · [SpecificDatumWriter](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/specific/SpecificDatumWriter.html)
