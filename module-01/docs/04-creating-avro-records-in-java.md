# Creating Avro Records in Java: Setters, Constructors and Builders

The Avro Getting Started guide creates three users in three different ways and moves on with a single sentence about which one to prefer. It's easy to read past, but the three approaches don't behave the same. Two of them will happily give you a `User` with no name, although the schema says a name is required, and you only find out when you try to write it. The third checks every field and refuses to build anything incomplete, including in a case most people don't expect.

This guide takes the [Creating Users](https://avro.apache.org/docs/++version++/getting-started-java/#creating-users) step of Apache Avro 1.12.2's guide and runs each approach through JUnit 6 tests. You'll see what setters, the all-arguments constructor and the builder each check, the exact exception the builder throws for a nullable field without a default, how generated records implement `equals` and `toString`, and when to use which.

## What is a Specific Record?

The `User` class that the compiler generated in [section 1.3](03-compiling-avro-schemas.md) extends `SpecificRecordBase` and implements `SpecificRecord`. A **specific record** is an ordinary Java object with one field per schema field, plus a reference to its schema. Avro's writers and readers use that schema to know which fields to write and in which order.

Because it's an ordinary object, you create it in ordinary ways, and Avro gives you three:

1. **The no-argument constructor and setters**, the JavaBean style.
2. **The all-arguments constructor**, with one argument per field in schema order.
3. **The builder**, from `User.newBuilder()`.

## Creating Users Overview

The guide's three users, each made a different way:

1. **Alyssa** is created with `new User()` and two setters. Her favourite colour is never set.
2. **Ben** is created with `new User("Ben", 7, "red")`.
3. **Charlie** is created with the builder, setting all three fields, including `favoriteNumber` to `null`.

The course keeps them in one helper class, `com.example.gettingstarted.Users`, so later sections can write and read the same three people.

## The Three Ways

### Setters

```java
public static User alyssa() {
    User user1 = new User();
    user1.setName("Alyssa");
    user1.setFavoriteNumber(256);
    // Leave favorite color null
    return user1;
}
```

`new User()` creates an object with every field `null`. The setters assign values and check nothing. Leaving the colour out is fine here because `null` is a valid value for `favorite_color`, but leaving out the *name* would be accepted just as quietly.

### The All-Arguments Constructor

```java
public static User ben() {
    return new User("Ben", 7, "red");
}
```

The arguments are matched to fields by position, in schema order: `name`, `favorite_number`, `favorite_color`. This is compact, but it's also fragile. If the schema ever reorders two fields of the same type, the code still compiles and silently puts values in the wrong fields. Like setters, the constructor validates nothing: `new User(null, 7, "red")` works.

### The Builder

```java
public static User charlie() {
    return User.newBuilder()
        .setName("Charlie")
        .setFavoriteColor("blue")
        .setFavoriteNumber(null)
        .build();
}
```

The builder records which fields you set, and `build()` checks every one. The guide sums up the trade-off: a builder takes more code than a constructor, but it fills in the defaults the schema specifies and validates the data as you set it, while directly constructed objects only fail when they're serialized.

Note the explicit `setFavoriteNumber(null)`. It looks redundant, but without it the builder refuses to build, as the next section shows.

## Why the Builder Needs setFavoriteNumber(null)

[Section 1.2](02-defining-an-avro-schema.md) made the point that *nullable* and *has a default* are different things. `favorite_number` is `["int", "null"]` with no default. Here is the builder's `build()` from the generated class, shortened to one field:

```java
record.favorite_number = fieldSetFlags()[1]
    ? this.favorite_number
    : (java.lang.Integer) defaultValue(fields()[1]);
```

If you didn't set the field, the builder asks the schema for a default. There isn't one, so it throws. Leave out the `setFavoriteNumber(null)` call and you get:

```text
org.apache.avro.AvroMissingFieldException: Field favorite_number type:UNION pos:1 not set and has no default value
```

The message tells you everything: the field, its type, its position, and the reason. If you want the builder to fill in `null` for you, the schema must say so:

```json
{"name": "favorite_number", "type": ["null", "int"], "default": null}
```

Put `"null"` first and give the field `"default": null`. That is the usual way to write an optional field, and Module 2 explains why Avro 1.12 also accepts the default when `null` isn't the first branch.

## Walkthrough: Three Users and Their Output

Generated records have a `toString()` that prints JSON, which is what the guide prints when it reads users back:

### Alyssa (setters)

```java
System.out.println(Users.alyssa());
```

```json
{"name": "Alyssa", "favorite_number": 256, "favorite_color": null}
```

### Ben (constructor)

```java
System.out.println(Users.ben());
```

```json
{"name": "Ben", "favorite_number": 7, "favorite_color": "red"}
```

### Charlie (builder)

```java
System.out.println(Users.charlie());
```

```json
{"name": "Charlie", "favorite_number": null, "favorite_color": "blue"}
```

### A User with No Name

```java
User nobody = new User();
System.out.println(nobody.getName());
```

```text
null
```

No error. The mistake surfaces only when the user is written, which [section 1.5](05-serializing-avro-records-to-a-data-file.md) demonstrates.

> **Run every example yourself**
> All the code in this post is in Module 1 of the Java Apache Avro course: a Maven project where every Avro concept is a JUnit 6 test you can run, break and fix.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Read the course outline →](https://github.com/andre-mare/java-apache-avro/blob/main/Apache%20Avro%20Outline.md)

## Equality and Copying

Generated records are values. `equals` and `hashCode` compare the field values through the schema, so two `User` objects with the same data are equal:

```java
Users.ben().equals(Users.ben());      // true
Users.ben().equals(Users.alyssa());   // false
```

The builder can also copy an existing record, which matters when you read records and want to keep them ([section 1.6](06-deserializing-avro-data-files.md)):

```java
User copy = User.newBuilder(original).build();
```

## Testing the Three Approaches

`CreatingUsersTest` has one test per behaviour described above:

```java
class CreatingUsersTest {

    @Test
    void settersLeaveUnsetFieldsNull() {
        User alyssa = Users.alyssa();

        assertThat(alyssa.getName()).hasToString("Alyssa");
        assertThat(alyssa.getFavoriteNumber()).isEqualTo(256);
        assertThat(alyssa.getFavoriteColor()).isNull();
    }

    @Test
    void theConstructorTakesFieldsInSchemaOrder() {
        User ben = Users.ben();

        assertThat(ben.getName()).hasToString("Ben");
        assertThat(ben.getFavoriteNumber()).isEqualTo(7);
        assertThat(ben.getFavoriteColor()).hasToString("red");
    }

    @Test
    void theBuilderValidatesAndBuilds() {
        User charlie = Users.charlie();

        assertThat(charlie.getName()).hasToString("Charlie");
        assertThat(charlie.getFavoriteNumber()).isNull();
        assertThat(charlie.getFavoriteColor()).hasToString("blue");
    }

    @Test
    void theBuilderRejectsAFieldThatIsNeitherSetNorDefaulted() {
        // favorite_number may be null, but the schema gives it no default, so the builder has nothing to fill in.
        assertThatThrownBy(() -> User.newBuilder().setName("Charlie").setFavoriteColor("blue").build())
            .isInstanceOf(AvroMissingFieldException.class)
            .hasMessageContaining("favorite_number");
    }

    @Test
    void theNoArgumentConstructorChecksNothing() {
        // A User with no name at all, although name is a required string. The mistake only shows when the user is
        // written (section 5).
        User nobody = new User();

        assertThat(nobody.getName()).isNull();
    }

    @Test
    void usersAreValuesWithEquals() {
        assertThat(Users.ben()).isEqualTo(Users.ben()).hasSameHashCodeAs(Users.ben());
        assertThat(Users.ben()).isNotEqualTo(Users.alyssa());
    }

    @Test
    void toStringShowsTheUserAsJson() {
        assertThat(Users.ben()).hasToString("{\"name\": \"Ben\", \"favorite_number\": 7, \"favorite_color\": \"red\"}");
    }
}
```

Notice `hasToString("Alyssa")` rather than `isEqualTo("Alyssa")`. The getter returns a `CharSequence` ([section 1.3](03-compiling-avro-schemas.md)). Here it happens to be the `String` you passed in, but after reading from a file it will be a `Utf8`, and `hasToString` works for both.

## When to Use Which

- **Use the builder by default.** It's the only one of the three that enforces the schema when the object is created, and it fills in defaults. The extra lines buy you errors at the point where the mistake was made.
- **Use setters when you're filling a record step by step**, for example while mapping from another object, and you'll validate or write it straight away.
- **Use the all-arguments constructor sparingly**, in tests or for tiny records, and never where two neighbouring fields have the same type.

The guide also notes that setters and constructors are faster, because they skip validation. That matters in a hot loop; it doesn't matter for most application code.

## Common Pitfalls

1. **Assuming nullable means "may be left out".** Without a default, the builder needs every field set, even to `null`.
2. **Trusting the constructor.** `new User()` and `new User(null, 7, "red")` both produce records that can't be written.
3. **Positional arguments after a schema change.** Reordering fields changes the constructor's parameter order without a compile error when the types match.
4. **Comparing names with `equals("Alyssa")`.** It works on a record you built and fails on one you read. Compare `toString()` values instead.

## Wrapping Up

Avro generates three ways to create a record, and only the builder checks anything. Setters and the constructor are quick and unchecked; the builder validates each field and fills in defaults, which is why it needs `setFavoriteNumber(null)` for a nullable field that has no default. Generated records are values with JSON `toString()`, `equals` and `hashCode`, and the builder can copy them.

Next, [section 1.5](05-serializing-avro-records-to-a-data-file.md) writes Alyssa, Ben and Charlie to a file and decodes the result byte by byte.

> **Run every example yourself**
> Run `mvn test -pl module-01 -Dtest=CreatingUsersTest`, then add `"default": null` to `favorite_number` in `user.avsc` (with `"null"` first) and see which tests now fail, and why.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Module 1 README →](https://github.com/andre-mare/java-apache-avro/blob/main/module-01/README.md)

---

**Filed under:** Apache Avro, Module 1: Getting Started

**Tags:** `apache-avro` `specificrecord` `builder` `avromissingfieldexception` `defaults` `java` `junit6`

### About the Author

**André Maré** is a Senior Security Architect working on identity and access management for financial services and open banking. He writes example-driven courses in which every concept is a test you can run. The code for this series is at [github.com/andre-mare/java-apache-avro](https://github.com/andre-mare/java-apache-avro).

[GitHub](https://github.com/andre-mare) · [Website](https://andre-mare.github.io)

### Related Articles

- **Apache Avro · Module 1:** [Avro Schemas: Defining a Record with Optional Fields](02-defining-an-avro-schema.md) · 8 November 2026
- **Apache Avro · Module 1:** [Compiling Avro Schemas: Inside the Generated Java Class](03-compiling-avro-schemas.md) · 8 November 2026
- **Apache Avro · Module 1:** [Serializing Avro Records to a Data File, Byte by Byte](05-serializing-avro-records-to-a-data-file.md) · 8 November 2026

**References:** [Creating Users](https://avro.apache.org/docs/++version++/getting-started-java/#creating-users) · [Records](https://avro.apache.org/docs/++version++/specification/#schema-record) · [SpecificRecordBase](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/specific/SpecificRecordBase.html) · [RecordBuilderBase](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/data/RecordBuilderBase.html) · [AvroMissingFieldException](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/AvroMissingFieldException.html)
