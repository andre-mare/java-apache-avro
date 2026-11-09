# Avro Without Code Generation: Working with GenericRecord

Many Avro tutorials make code generation look mandatory: write a schema, run the plugin, use the class. But some of the most common Avro programs can't work that way. A tool that dumps any Avro file, a Kafka connector that moves records it has never seen, a service that receives schemas at runtime from a registry: none of them can have a generated class for every schema, because the schemas don't exist when the code is compiled.

This guide follows the [Serializing and deserializing without code generation](https://avro.apache.org/docs/++version++/getting-started-java/#serializing-and-deserializing-without-code-generation) part of Apache Avro 1.12.2's Getting Started (Java) guide. You'll load the schema from the classpath, build users as `GenericRecord`s, write and read them, see what the generic API checks and what it doesn't, and prove with `GenericUsersTest` that a file written generically reads back as generated `User` objects. The bytes are the same.

## What is a GenericRecord?

A [`GenericRecord`](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/generic/GenericRecord.html) is a record whose fields you reach by name or position, like a map with a schema attached:

```java
record.put("name", "Alyssa");
Object name = record.get("name");
```

The usual implementation is `GenericData.Record`. It holds the schema and an array of values, one per field. There's no class per schema, so one piece of code handles every record type. What you give up is compile-time checking: `get(..)` returns `Object`, and a misspelt field name is only caught when the code runs.

## Generic API Overview

The generic flow mirrors the specific one from sections 1.5 and 1.6, with the schema supplied at runtime:

1. **Parse the schema**, from a file, the classpath, a registry, or a data file's header.
2. **Create records** with `new GenericData.Record(schema)` and `put(..)`.
3. **Write** with a `GenericDatumWriter` inside a `DataFileWriter`.
4. **Read** with a `GenericDatumReader` inside a `DataFileReader`.

## Building the Generic Example

### Step 1: Load the Schema from the Classpath

The guide parses `user.avsc` from a path relative to the working directory. That only works when the program runs from the right folder. Because the course keeps schemas under `src/main/resources`, Maven copies the file to `target/classes/avro/user.avsc` and packages it in the jar, so `GenericUsersFile` loads it from the classpath instead:

```java
public static final String SCHEMA_RESOURCE = "avro/user.avsc";

public static Schema schema() throws IOException {
    try (InputStream in = GenericUsersFile.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
        if (in == null) {
            throw new FileNotFoundException("No schema on the classpath at " + SCHEMA_RESOURCE);
        }
        return new SchemaParser().parse(in).mainSchema();
    }
}
```

The `null` check matters: `getResourceAsStream` returns `null` for a missing resource, and without the check you'd get a confusing error from the parser instead of a clear one.

### Step 2: Create Generic Users

```java
public static GenericRecord alyssa(Schema schema) {
    GenericRecord user1 = new GenericData.Record(schema);
    user1.put("name", "Alyssa");
    user1.put("favorite_number", 256);
    // Leave favorite color null
    return user1;
}

public static GenericRecord ben(Schema schema) {
    GenericRecord user2 = new GenericData.Record(schema);
    user2.put("name", "Ben");
    user2.put("favorite_number", 7);
    user2.put("favorite_color", "red");
    return user2;
}
```

Field names are the schema's names, `favorite_number`, not the camel-case Java names of the generated class.

### Step 3: Write Them

```java
public static void write(File file, Schema schema, List<GenericRecord> users) throws IOException {
    DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
    try (DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {
        dataFileWriter.create(schema, file);
        for (GenericRecord user : users) {
            dataFileWriter.append(user);
        }
    }
}
```

### Step 4: Read Them Back

```java
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
```

This calls `next()` without an argument, so every record is a new object and the list can be kept as it is. [Section 1.6](06-deserializing-avro-data-files.md) explains the reuse trap this avoids.

## Walkthrough: What the Generic API Does

### Reading Back

```java
for (GenericRecord user : GenericUsersFile.read(file, schema)) {
    System.out.println(user + " name class=" + user.get("name").getClass().getName());
}
```

```text
{"name": "Alyssa", "favorite_number": 256, "favorite_color": null} name class=org.apache.avro.util.Utf8
{"name": "Ben", "favorite_number": 7, "favorite_color": "red"} name class=org.apache.avro.util.Utf8
```

The same JSON `toString()` as generated records, and the same `Utf8` strings.

### A Field That Isn't in the Schema

The guide notes that putting a field the schema doesn't define is an error, and it's an immediate one:

```java
GenericRecord user = new GenericData.Record(schema);
user.put("favorite_animal", "cat");
```

```text
org.apache.avro.AvroRuntimeException: Not a valid schema field: favorite_animal
```

### A Value of the Wrong Type

`put` checks the *name*, not the *value*. `user.put("favorite_number", "seven")` is accepted without complaint; the mismatch only surfaces when the record is written:

```text
org.apache.avro.file.DataFileWriter$AppendWriteException: org.apache.avro.UnresolvedUnionException: Not in union ["int","null"]: java.lang.String (field=favorite_number)
```

With the generic API, the writer is your validator. [Section 1.5](05-serializing-avro-records-to-a-data-file.md) shows how write failures are reported.

### Fields by Position

Every field also has a position, so `get(0)` and `get("name")` return the same value. Position access is faster, because it skips the name lookup, which is how generic code that processes many records usually works.

> **Run every example yourself**
> All the code in this post is in Module 1 of the Java Apache Avro course: a Maven project where every Avro concept is a JUnit 6 test you can run, break and fix.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Read the course outline →](https://github.com/andre-mare/java-apache-avro/blob/main/Apache%20Avro%20Outline.md)

## Generic and Specific Are the Same Bytes

An Avro file doesn't record how it was written. A file written with `GenericDatumWriter` is byte for byte what a `SpecificDatumWriter` would produce for the same data, so either kind of reader can read it:

```java
GenericUsersFile.write(file, schema, List.of(GenericUsersFile.alyssa(schema), GenericUsersFile.ben(schema)));

List<User> users = SpecificUsersFile.read(file);    // generated User objects
```

That's what makes mixed systems work: a producer can use generated classes while a generic tool reads, routes or archives the same data.

## When to Use the Generic API

- **Tools that handle any schema**: viewers, converters to JSON or CSV, data quality checks.
- **Pipelines and connectors** that move records between systems without caring about their content.
- **Schemas known only at runtime**, from a schema registry or from a data file's header.
- **Routers and filters** that read one or two fields and pass the rest through.

Use generated classes when your code knows the schema and works with its fields, which is most application code.

## Testing the Generic API

`GenericUsersTest` parses the schema once from the classpath and writes to a `@TempDir`:

```java
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
    void anUnknownFieldIsRejected() {
        GenericRecord user = new GenericData.Record(schema);

        assertThatThrownBy(() -> user.put("favorite_animal", "cat"))
            .isInstanceOf(AvroRuntimeException.class)
            .hasMessageContaining("favorite_animal");
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
```

The class has two more tests: `stringsComeBackAsUtf8` and `fieldsCanBeReachedByPositionToo`. Look closely at `aRecordIsEqualToTheRecordItWasReadFrom`: the record you built holds `String`s, the record you read holds `Utf8`s, and they're still equal, because `GenericData` compares strings by their text. A single `Utf8` compared with a `String`, as in section 1.6, is still not equal.

## Common Pitfalls

1. **Loading schemas from relative file paths.** They break as soon as the program runs from another directory or from a jar. Use the classpath.
2. **Using Java names for fields.** Generic records use the schema's names: `favorite_number`, not `favoriteNumber`.
3. **Assuming `put` validates values.** It only checks the field name; wrong types fail at write time.
4. **Casting strings to `String`.** `(String) record.get("name")` throws `ClassCastException` on a record you read, because the value is a `Utf8`. Call `toString()`.

## Wrapping Up

The generic API reads and writes Avro with nothing but a schema at runtime. `GenericData.Record` holds the values, `GenericDatumWriter` and `GenericDatumReader` encode them, and the bytes are identical to what generated classes produce, so generic and specific code can share the same files. Load schemas from the classpath, use the schema's field names, and remember that `put` checks names while the writer checks values.

Next, [section 1.8](08-faster-avro-serialization-with-custom-coders.md) goes back to generated classes and switches on their faster, beta encoder.

> **Run every example yourself**
> Run `mvn test -pl module-01 -Dtest=GenericUsersTest`, then add `user2.put("favorite_number", "seven")` to `GenericUsersFile.ben(..)` and see which tests fail and with what message.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Module 1 README →](https://github.com/andre-mare/java-apache-avro/blob/main/module-01/README.md)

---

**Filed under:** Apache Avro, Module 1: Getting Started

**Tags:** `apache-avro` `genericrecord` `genericdatumwriter` `genericdatumreader` `schemaparser` `classpath` `java` `junit6`

### About the Author

**André Maré** is a Senior Security Architect working on identity and access management for financial services and open banking. He writes example-driven courses in which every concept is a test you can run. The code for this series is at [github.com/andre-mare/java-apache-avro](https://github.com/andre-mare/java-apache-avro).

[GitHub](https://github.com/andre-mare) · [Website](https://andre-mare.github.io)

### Related Articles

- **Apache Avro · Module 1:** [Avro Schemas: Defining a Record with Optional Fields](02-defining-an-avro-schema.md) · 8 November 2026
- **Apache Avro · Module 1:** [Deserializing Avro Data Files: Object Reuse, Utf8 and Trusted Classes](06-deserializing-avro-data-files.md) · 8 November 2026
- **Apache Avro · Module 1:** [Faster Avro Serialization with Custom Coders](08-faster-avro-serialization-with-custom-coders.md) · 8 November 2026

**References:** [Serializing and deserializing without code generation](https://avro.apache.org/docs/++version++/getting-started-java/#serializing-and-deserializing-without-code-generation) · [GenericRecord](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/generic/GenericRecord.html) · [GenericData.Record](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/generic/GenericData.Record.html) · [GenericDatumWriter](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/generic/GenericDatumWriter.html) · [GenericDatumReader](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/generic/GenericDatumReader.html) · [SchemaParser](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/SchemaParser.html)
