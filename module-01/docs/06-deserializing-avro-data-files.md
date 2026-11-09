*Filed under: Apache Avro · Module 1: Getting Started*

# Deserializing Avro Data Files: Object Reuse, Utf8 and Trusted Classes

Reading an Avro file looks like the easy half of the job: open a reader, loop, print. The Getting Started guide does exactly that, and its loop works. But copy that loop into real code that keeps the records, and you end up with a list of three users who are all Charlie. Compare a name you read with a `String` and the comparison fails although the text matches. And since Avro 1.12.2, the same reading code can stop with a `SecurityException` before it reads a single record.

This guide follows the [Deserializing](https://avro.apache.org/docs/++version++/getting-started-java/#deserializing) step of Apache Avro 1.12.2's Getting Started (Java) guide. You'll read back the `users.avro` file from [section 1.5](05-serializing-avro-records-to-a-data-file.md), see why the guide reuses one object and how to keep records safely, learn why strings come back as `Utf8`, and set up the class trust that Avro now requires for generated classes, all proven by `DeserializingTest`.

## What is Deserialization in Avro?

Deserialization turns bytes back into objects. In Avro it always involves a schema: the bytes contain no field names or types, so the reader must know the schema the data was **written** with. In a data file that's easy, because the writer's schema is in the header.

The Java API mirrors the writing side:

- **A `DatumReader`** decodes one value. `SpecificDatumReader<User>` produces generated `User` objects; `GenericDatumReader` produces generic records.
- **A `DataFileReader`** opens the file, reads the header and the schema, and iterates over the records block by block.

## Deserializing Overview

1. **Create a datum reader** for the class you want back.
2. **Open a data file reader** on the file. This reads the header only.
3. **Loop with `hasNext()` and `next(..)`**, which decodes one record at a time.
4. **Close the reader.**

## The Guide's Reading Loop

### Step 1: Create a DatumReader

```java
DatumReader<User> userDatumReader = new SpecificDatumReader<>(User.class);
```

### Step 2: Open the File

```java
DataFileReader<User> dataFileReader = new DataFileReader<>(file, userDatumReader);
```

### Step 3: Read with Reuse

```java
User user = null;
while (dataFileReader.hasNext()) {
    // Reuse user object by passing it to next(). This saves us from
    // allocating and garbage collecting many objects for files with
    // many items.
    user = dataFileReader.next(user);
    System.out.println(user);
}
```

`next(user)` decodes the next record **into the object you pass in**, and returns it. On the first call `user` is `null`, so Avro creates one; on every later call it overwrites the same object's fields. For a file with millions of records, that saves millions of allocations.

### Step 4: The Output

```json
{"name": "Alyssa", "favorite_number": 256, "favorite_color": null}
{"name": "Ben", "favorite_number": 7, "favorite_color": "red"}
{"name": "Charlie", "favorite_number": null, "favorite_color": "blue"}
```

## The Reuse Trap

Printing inside the loop is safe, because each user is used before the next read. Keeping them isn't:

```java
List<User> kept = new ArrayList<>();
User user = null;
while (dataFileReader.hasNext()) {
    user = dataFileReader.next(user);
    kept.add(user);                 // the same object, three times
}
```

`kept` has three entries, and all three are Charlie, because all three are the *same object*, last filled with Charlie's data. There are three correct ways to keep records:

- **Copy the values you need** before the next read, for example `user.getName().toString()`.
- **Copy the record** with the builder: `User.newBuilder(user).build()`. This is what the course's `SpecificUsersFile.read(..)` does.
- **Don't reuse**: call `next()` without an argument, which creates a new object each time.

```java
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
```

## Strings Come Back as Utf8

[Section 1.3](03-compiling-avro-schemas.md) showed that `getName()` returns `CharSequence`. After a read, the object behind it is Avro's own `org.apache.avro.util.Utf8`, which keeps the bytes as they were in the file instead of decoding them into a `String`:

```java
User ben = SpecificUsersFile.read(file).get(1);

ben.getName().getClass();          // class org.apache.avro.util.Utf8
ben.getName().equals("Ben");       // false
ben.getName().toString();          // "Ben"
```

`Utf8.equals(..)` only returns `true` for another `Utf8`, so comparing with a `String` is always `false`. Convert with `toString()` before comparing, or configure the plugin's `stringType` to `String`, which Module 3 does.

> **Run every example yourself**
> All the code in this post is in Module 1 of the Java Apache Avro course: a Maven project where every Avro concept is a JUnit 6 test you can run, break and fix.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Read the course outline →](https://github.com/andre-mare/java-apache-avro/blob/main/Apache%20Avro%20Outline.md)

## Security Considerations: Trusted Classes

To create a `User` while reading, Avro loads the class named in the schema, `example.avro.User`. A schema comes from the data, and data can come from anywhere, so loading whatever class a schema names would let a file decide which classes your application instantiates. Avro 1.12.2 closes that gap: a `SpecificDatumReader` only loads classes you trust.

### What Happens Without Trust

Without configuration, reading fails as soon as the first record needs a `User`:

```text
java.lang.SecurityException: Forbidden example.avro.User! This class is not trusted to be included in Avro schemas. You may either use the system properties org.apache.avro.SERIALIZABLE_CLASSES and org.apache.avro.SERIALIZABLE_PACKAGES to set the comma separated list of the classes or packages you trust, or you can set them via the API (see org.apache.avro.util.ClassSecurityValidator).
```

The message names both fixes.

### Option 1: A System Property

Trust a whole package, or list classes with `org.apache.avro.SERIALIZABLE_CLASSES`:

```bash
java -Dorg.apache.avro.SERIALIZABLE_PACKAGES=example.avro ...
```

The course sets it for every test in the parent POM:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <org.apache.avro.SERIALIZABLE_PACKAGES>${avro.trusted.packages}</org.apache.avro.SERIALIZABLE_PACKAGES>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

### Option 2: The ClassSecurityValidator API

Set the trust in code at application start-up, keeping Avro's defaults and adding your classes:

```java
ClassSecurityValidator.setGlobal(ClassSecurityValidator.composite(
    ClassSecurityValidator.DEFAULT,
    ClassSecurityValidator.builder().add(User.class).build()));
```

`DEFAULT` combines Avro's built-in trusted classes (`java.lang` and `java.math` types) with the system properties. `DEFAULT_TRUSTED_CLASSES` is the built-in list alone.

### What to Trust

- **Trust only the packages of your generated classes**, never a broad prefix like `com` or `org`.
- **Prefer packages that contain nothing but generated code.** Keeping generated classes in their own namespace, as `example.avro` does, makes the trust list obvious.
- **Remember that the namespace is the package.** Renaming a schema's namespace changes what you must trust.

## Testing the Reading Side

`DeserializingTest` writes the three users before each test and restores the default trust after each one, because the last test changes it:

```java
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
        assertThat(SpecificUsersFile.read(file)).containsExactly(Users.alyssa(), Users.ben(), Users.charlie());
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

        assertThat(ben.getName()).isInstanceOf(org.apache.avro.util.Utf8.class);
        assertThat(ben.getName()).isNotEqualTo("Ben");
        assertThat(ben.getName()).hasToString("Ben");
    }

    @Test
    void readingGeneratedClassesRequiresTrustingTheirPackage() {
        // Trust only Avro's built-in list, ignoring SERIALIZABLE_PACKAGES. A new SpecificData has no cached
        // classes yet, so it has to load and check example.avro.User.
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
```

The class has a fifth test, `theGuidesLoopReusesOneUserObject`, which runs the guide's loop and copies each name before the next read. Note the fresh `SpecificData` in the security test: the shared one caches classes it has already loaded, and a cached class isn't checked again.

## Common Pitfalls

1. **Keeping reused objects.** `next(user)` refills one object. Copy it, or call `next()` without an argument.
2. **Comparing `Utf8` with `String`.** Always `false`. Use `toString()`.
3. **Upgrading to 1.12.2 without configuring trust.** Code that read files yesterday throws `SecurityException` today. Set `SERIALIZABLE_PACKAGES` or use `ClassSecurityValidator`.
4. **Trusting too much.** A wide package list defeats the purpose of the check.
5. **Testing trust with a shared model.** A class loaded once is cached; a test that expects the check to fail needs a new `SpecificData`.

## Wrapping Up

Reading an Avro file is the guide's loop plus three things it doesn't say. `next(user)` reuses one object, so copy records you keep. Strings come back as `Utf8`, so compare their text. And since 1.12.2, generated classes must be trusted, with a system property or `ClassSecurityValidator`, before a `SpecificDatumReader` will create them.

Next, [section 1.7](07-avro-without-code-generation.md) reads and writes the same data with no generated class at all.

> **Run every example yourself**
> Run `mvn test -pl module-01 -Dtest=DeserializingTest`, then remove the `SERIALIZABLE_PACKAGES` property from the parent POM and see which tests in the module fail.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Module 1 README →](https://github.com/andre-mare/java-apache-avro/blob/main/module-01/README.md)

---

**Filed under:** Apache Avro, Module 1: Getting Started

**Tags:** `apache-avro` `deserialization` `datafilereader` `utf8` `classsecurityvalidator` `security` `java` `junit6`

### About the Author

**André Maré** is a Senior Security Architect working on identity and access management for financial services and open banking. He writes example-driven courses in which every concept is a test you can run. The code for this series is at [github.com/andre-mare/java-apache-avro](https://github.com/andre-mare/java-apache-avro).

[GitHub](https://github.com/andre-mare) · [Website](https://andre-mare.github.io)

### Related Articles

- **Apache Avro · Module 1:** [Serializing Avro Records to a Data File, Byte by Byte](05-serializing-avro-records-to-a-data-file.md) · 8 November 2026
- **Apache Avro · Module 1:** [Avro Without Code Generation: Working with GenericRecord](07-avro-without-code-generation.md) · 8 November 2026
- **Apache Avro · Module 1:** [Compiling Avro Schemas: Inside the Generated Java Class](03-compiling-avro-schemas.md) · 8 November 2026

**References:** [Deserializing](https://avro.apache.org/docs/++version++/getting-started-java/#deserializing) · [Object Container Files](https://avro.apache.org/docs/++version++/specification/#object-container-files) · [DataFileReader](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/file/DataFileReader.html) · [SpecificDatumReader](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/specific/SpecificDatumReader.html) · [Utf8](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/util/Utf8.html) · [ClassSecurityValidator](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/util/ClassSecurityValidator.html)
