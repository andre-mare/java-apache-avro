# Module 1: Getting Started

This module turns the Apache Avro [Getting Started (Java)](https://avro.apache.org/docs/++version++/getting-started-java/) guide into tests. It uses the guide's `User` schema and its three users, Alyssa, Ben and Charlie. You define the schema, generate a Java class from it, create users three ways, write them to `users.avro` and read them back, first with the generated class and then without code generation. The last section switches on the guide's beta feature for faster generated code.

## Table of Contents

- [The Example](#the-example)
- [Running the Module](#running-the-module)
- [Sections](#sections)
  - [Section 1: Setting Up the Project](#section-1-setting-up-the-project)
  - [Section 2: Defining a Schema](#section-2-defining-a-schema)
  - [Section 3: Compiling the Schema](#section-3-compiling-the-schema)
  - [Section 4: Creating Users](#section-4-creating-users)
  - [Section 5: Serializing](#section-5-serializing)
  - [Section 6: Deserializing](#section-6-deserializing)
  - [Section 7: Without Code Generation](#section-7-without-code-generation)
  - [Section 8: Generating Faster Code](#section-8-generating-faster-code)
- [Two Things the Guide Doesn't Mention](#two-things-the-guide-doesnt-mention)

## The Example

[`src/main/resources/avro/user.avsc`](src/main/resources/avro/user.avsc) is the guide's schema, unchanged:

```json
{"namespace": "example.avro",
 "type": "record",
 "name": "User",
 "fields": [
     {"name": "name", "type": "string"},
     {"name": "favorite_number",  "type": ["int", "null"]},
     {"name": "favorite_color", "type": ["string", "null"]}
 ]
}
```

During the build, the `avro-maven-plugin` generates `example.avro.User` from it into `target/generated-sources/avro`. The schema lives under `src/main/resources` rather than the plugin's default `src/main/avro`, so Maven also copies it to `target/classes/avro/user.avsc`: it is on the classpath at runtime and packaged in the jar.

| Class | What it holds |
| --- | --- |
| [`Users`](src/main/java/com/example/gettingstarted/Users.java) | The guide's three users, each created a different way |
| [`SpecificUsersFile`](src/main/java/com/example/gettingstarted/SpecificUsersFile.java) | Writing and reading `users.avro` with the generated class |
| [`GenericUsersFile`](src/main/java/com/example/gettingstarted/GenericUsersFile.java) | The same without code generation: the schema loaded from the classpath at runtime, generic records |

## Running the Module

Run these commands from the repository root.

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-01` | 41 tests pass: 38 in the default run, 3 in the `custom-coders` run |
| Run one class | `mvn test -pl module-01 -Dtest=CreatingUsersTest` | 7 tests pass |
| Run section 8 on its own | `mvn test -pl module-01 -Dtest=CustomCodersTest -Dorg.apache.avro.specific.use_custom_coders=true` | 3 tests pass |
| See the generated class | `mvn generate-sources -pl module-01`, then open `module-01/target/generated-sources/avro/example/avro/User.java` | |

## Sections

### Section 1: Setting Up the Project

Guide: [Download](https://avro.apache.org/docs/++version++/getting-started-java/#download)

The [POM](pom.xml) has the guide's `avro` dependency and its `avro-maven-plugin` configuration, with two changes. The plugin's `sourceDirectory` is `src/main/resources/avro`, so the schema is also on the classpath. And the guide writes generated code to `src/main/java`, while this module leaves it in `target/generated-sources/avro`, so it is never committed.

Test class: [`ProjectSetupTest`](src/test/java/com/example/module_01/section_01/ProjectSetupTest.java)

### Section 2: Defining a Schema

Guide: [Defining a schema](https://avro.apache.org/docs/++version++/getting-started-java/#defining-a-schema)

The schema is parsed with `SchemaParser`, which Avro 1.12 introduced. The tests check the full name, the fields in order, the two unions with `null`, and that no field has a default.

Test class: [`DefiningASchemaTest`](src/test/java/com/example/module_01/section_02/DefiningASchemaTest.java)

### Section 3: Compiling the Schema

Guide: [Compiling the schema](https://avro.apache.org/docs/++version++/getting-started-java/#compiling-the-schema)

The tests look at what the compiler made of the schema: camel-case accessors, a boxed `Integer` for the nullable int, `CharSequence` for strings (the default), and the schema embedded in the class.

```bash
java -jar avro-tools-1.12.2.jar compile schema src/main/resources/avro/user.avsc /tmp/generated
```

Test class: [`CompilingTheSchemaTest`](src/test/java/com/example/module_01/section_03/CompilingTheSchemaTest.java)

### Section 4: Creating Users

Guide: [Creating Users](https://avro.apache.org/docs/++version++/getting-started-java/#creating-users)

Alyssa is made with setters, Ben with the all-arguments constructor, and Charlie with the builder. The builder checks fields: leaving out `favorite_number` throws `AvroMissingFieldException`, even though the field may be null, because it has no default.

Test class: [`CreatingUsersTest`](src/test/java/com/example/module_01/section_04/CreatingUsersTest.java)

### Section 5: Serializing

Guide: [Serializing](https://avro.apache.org/docs/++version++/getting-started-java/#serializing)

The three users go to `users.avro` in a JUnit `@TempDir`. The tests check the magic bytes, the schema in the header, that the last block is written on close, and that a user without a name can't be written: the error says `null value for (non-nullable) string at User.name`.

Test class: [`SerializingTest`](src/test/java/com/example/module_01/section_05/SerializingTest.java)

### Section 6: Deserializing

Guide: [Deserializing](https://avro.apache.org/docs/++version++/getting-started-java/#deserializing)

The guide's loop reuses one `User` object. The tests show the right way to use that, the bug when you keep the reused object, the `Utf8` strings that come back, and the trust setting Avro 1.12.2 requires (see below).

Test class: [`DeserializingTest`](src/test/java/com/example/module_01/section_06/DeserializingTest.java)

### Section 7: Without Code Generation

Guide: [Serializing and deserializing without code generation](https://avro.apache.org/docs/++version++/getting-started-java/#serializing-and-deserializing-without-code-generation)

The same file with `GenericRecord`s and a schema loaded from the classpath at runtime. An unknown field is rejected at once, strings come back as `Utf8`, and a file written generically reads back as generated `User` objects.

Test class: [`GenericUsersTest`](src/test/java/com/example/module_01/section_07/GenericUsersTest.java)

### Section 8: Generating Faster Code

Guide: [Beta feature: Generating faster code](https://avro.apache.org/docs/++version++/getting-started-java/#beta-feature-generating-faster-code)

The system property `org.apache.avro.specific.use_custom_coders=true` makes generated classes use their own encode and decode methods. Avro reads the property once, so the POM runs this class in a second Surefire execution with the property set. The tests show the flag is on, the users round-trip, and the bytes equal those of the normal writer.

Test class: [`CustomCodersTest`](src/test/java/com/example/module_01/section_08/CustomCodersTest.java)

## Two Things the Guide Doesn't Mention

**Trusting your own generated classes.** Since Avro 1.12.2, a `SpecificDatumReader` only creates instances of trusted classes, and by default only a few `java.lang` and `java.math` types are trusted. Without a setting, reading `users.avro` into `User` objects throws a `SecurityException`. The parent POM passes `-Dorg.apache.avro.SERIALIZABLE_PACKAGES=example.avro` to the tests; an application sets the same property at startup. `DeserializingTest.readingGeneratedClassesRequiresTrustingTheirPackage` shows the exception.

**Strings come back as `Utf8`.** With the plugin's default settings, string getters return `CharSequence`, and after reading that is Avro's `org.apache.avro.util.Utf8`, which is not equal to a `String`. Compare with `toString()`, or use AssertJ's `hasToString(..)`. Module 3 shows the plugin option that generates `String` instead.
