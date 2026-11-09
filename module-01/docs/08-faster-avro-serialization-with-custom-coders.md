# Faster Avro Serialization with Custom Coders

Performance advice for serialization usually means switching libraries, tuning buffers or rewriting hot paths by hand. Avro has a faster path already built into every class it generates, and almost nobody switches it on. The code is there in the `User` class from [section 1.3](03-compiling-avro-schemas.md); it just sits unused behind a system property, because the Avro project still labels it beta.

This guide covers the last part of the Apache Avro 1.12.2 [Getting Started (Java)](https://avro.apache.org/docs/++version++/getting-started-java/#beta-feature-generating-faster-code) guide, "Beta feature: Generating faster code". You'll see what the custom coders in a generated class look like, why the flag that enables them needs its own JVM, how the course runs one test class in a second Surefire execution to get one, and how `CustomCodersTest` proves the faster path writes exactly the same bytes as the normal one.

## What Are Custom Coders?

Normally, a `SpecificDatumWriter` encodes a record by walking its schema: for each field it looks up the field's schema, fetches the value through the record's generic `get(int)` method, and dispatches on the type. That works for any record, but it does the same lookups for every record it writes.

**Custom coders** are encode and decode methods that the compiler generates for each class. They already know the schema, so they write each field directly, with no lookups and no dispatch. According to the guide, which introduced the feature in Avro 1.9.0, this speeds up decoding by more than 10% and encoding by more than 30%.

## Custom Coders Overview

1. **The compiler generates `customEncode(..)` and `customDecode(..)`** in every specific record class, and `hasCustomCoders()` returns `true`.
2. **The flag `org.apache.avro.specific.use_custom_coders`** decides whether Avro uses them. It's off by default.
3. **`SpecificData` reads the flag once**, when its class is initialised.
4. **With the flag on, `SpecificDatumWriter` and `SpecificDatumReader`** call the custom coders instead of walking the schema.
5. **The bytes are the same either way.** Only the route to them changes.

## Inside the Generated Coders

### customEncode

This is the method from the generated `User` class, unchanged:

```java
@Override public void customEncode(org.apache.avro.io.Encoder out)
  throws java.io.IOException
{
  out.writeString(this.name);

  if (this.favorite_number == null) {
    out.writeIndex(1);
    out.writeNull();
  } else {
    out.writeIndex(0);
    out.writeInt(this.favorite_number);
  }

  if (this.favorite_color == null) {
    out.writeIndex(1);
    out.writeNull();
  } else {
    out.writeIndex(0);
    out.writeString(this.favorite_color);
  }
}
```

Compare it with the bytes decoded in [section 1.5](05-serializing-avro-records-to-a-data-file.md). For Ben, `writeString("Ben")` writes `06 42 65 6e`; `writeIndex(0)` and `writeInt(7)` write `00 0e`; `writeIndex(0)` and `writeString("red")` write `00 06 72 65 64`. The union branch indexes, 0 for the value and 1 for `null`, follow the order of `["int", "null"]` in the schema, hard-coded at generation time.

### customDecode

`customDecode(ResolvingDecoder in)` does the reverse. It also checks whether the data was written with a different schema; if so, it reads the fields in the order the resolver gives. Module 7 covers schema resolution.

## Switching the Flag On

### Step 1: Know That the Flag Is Read Once

The guide says to set the system property to `true` at runtime, with no need to recompile. What it doesn't stress is *when* it's read: `SpecificData` reads the property when the class is initialised and keeps the value. Setting it with `System.setProperty(..)` after Avro has been used, for example in a test's `@BeforeAll`, has no effect if another test already touched Avro in the same JVM.

### Step 2: Set It on the Command Line

For an application, set it when the JVM starts:

```bash
java -Dorg.apache.avro.specific.use_custom_coders=true -jar app.jar
```

### Step 3: Give the Test Its Own JVM

In a Maven build, all test classes of a module normally share one JVM, so one class can't have the flag on while the others have it off. The course solves this with a second Surefire execution. The default execution skips `CustomCodersTest`; the second runs only that class, with the property set:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <executions>
        <execution>
            <id>default-test</id>
            <configuration>
                <excludes>
                    <exclude>**/CustomCodersTest.java</exclude>
                </excludes>
            </configuration>
        </execution>
        <execution>
            <id>custom-coders</id>
            <goals>
                <goal>test</goal>
            </goals>
            <configuration>
                <test>CustomCodersTest</test>
                <systemPropertyVariables>
                    <org.apache.avro.specific.use_custom_coders>true</org.apache.avro.specific.use_custom_coders>
                </systemPropertyVariables>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Each Surefire execution starts its own forked JVM, so the property is in place before Avro is loaded.

### Step 4: Check the Build Log

```text
[INFO] --- surefire:3.5.5:test (default-test) @ module-01 ---
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- surefire:3.5.5:test (custom-coders) @ module-01 ---
[INFO] Running com.example.module_01.section_08.CustomCodersTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Two executions: 38 tests with the flag off, then 3 with it on.

> **Run every example yourself**
> All the code in this post is in Module 1 of the Java Apache Avro course: a Maven project where every Avro concept is a JUnit 6 test you can run, break and fix.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Read the course outline →](https://github.com/andre-mare/java-apache-avro/blob/main/Apache%20Avro%20Outline.md)

## Testing the Faster Path

`CustomCodersTest` answers the three questions that matter for a beta feature: is it really on, does data survive a round trip, and are the bytes unchanged?

```java
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
```

The first test guards the setup: without it, a broken POM would run the other two with the flag off and they'd pass for the wrong reason. The third is the key one. It writes Ben through the custom coder and through a `GenericDatumWriter`, which never uses custom coders, and requires identical bytes. Because the format doesn't change, files and messages written with the flag on can be read by any Avro reader, in any language, with the flag on or off.

## Production Considerations

- **It's beta.** The guide asks the community to try it and expects it to become the default eventually, but for now it's opt-in. Run your own round-trip tests with the flag on before enabling it in production.
- **It's reversible without a release.** The guide points out that you can turn it off again in production if something goes wrong, because it's only a system property and the format doesn't change.
- **Measure first.** The gain is in encoding and decoding. If your application spends its time in I/O or business logic, a 30% faster encoder may not show at all.
- **It only applies to generated classes.** Generic records ([section 1.7](07-avro-without-code-generation.md)) have no custom coders.
- **Set it at start-up.** Because the flag is read once, set it in the JVM arguments, not in code.

## Common Pitfalls

1. **Setting the property in code.** If any Avro class touched `SpecificData` first, the change is ignored. Use `-D` on the command line or in the Surefire configuration.
2. **Testing it in the shared test JVM.** One test class can't have its own value. Use a separate Surefire execution, as the course does.
3. **Not checking that it's on.** Assert `useCustomCoders()` in the test that relies on it.
4. **Expecting a different format.** The bytes are identical; only the speed changes.

## Wrapping Up

Every class the Avro compiler generates already carries `customEncode` and `customDecode` methods that write fields directly instead of walking the schema. The beta flag `org.apache.avro.specific.use_custom_coders` turns them on, it's read once per JVM, and the course gives the test that uses it a JVM of its own with a second Surefire execution. `CustomCodersTest` shows that the faster path produces exactly the same bytes, so it's safe to try and easy to turn off.

That completes Module 1. Module 2 takes the schema further, through every type Avro has, with names, namespaces and aliases.

> **Run every example yourself**
> Run `mvn test -pl module-01` and find both Surefire executions in the output, then delete the `<systemPropertyVariables>` block from the `custom-coders` execution and see which test catches it.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Module 1 README →](https://github.com/andre-mare/java-apache-avro/blob/main/module-01/README.md)

---

**Filed under:** Apache Avro, Module 1: Getting Started

**Tags:** `apache-avro` `performance` `custom-coders` `code-generation` `maven-surefire` `java` `junit6`

### About the Author

**André Maré** is a Senior Security Architect working on identity and access management for financial services and open banking. He writes example-driven courses in which every concept is a test you can run. The code for this series is at [github.com/andre-mare/java-apache-avro](https://github.com/andre-mare/java-apache-avro).

[GitHub](https://github.com/andre-mare) · [Website](https://andre-mare.github.io)

### Related Articles

- **Apache Avro · Module 1:** [Compiling Avro Schemas: Inside the Generated Java Class](03-compiling-avro-schemas.md) · 8 November 2026
- **Apache Avro · Module 1:** [Serializing Avro Records to a Data File, Byte by Byte](05-serializing-avro-records-to-a-data-file.md) · 8 November 2026
- **Apache Avro · Module 1:** [Apache Avro in Java: Setting Up a Maven Project That Generates Code from Schemas](01-setting-up-an-avro-maven-project.md) · 8 November 2026

**References:** [Beta feature: Generating faster code](https://avro.apache.org/docs/++version++/getting-started-java/#beta-feature-generating-faster-code) · [Binary Encoding](https://avro.apache.org/docs/++version++/specification/#binary-encoding) · [SpecificData](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/specific/SpecificData.html) · [SpecificRecordBase](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/specific/SpecificRecordBase.html) · [SpecificDatumWriter](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/specific/SpecificDatumWriter.html)
