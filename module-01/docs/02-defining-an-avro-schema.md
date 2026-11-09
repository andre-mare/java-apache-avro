# Avro Schemas: Defining a Record with Optional Fields

In JSON or XML, the data describes itself: every value carries its field name, and a reader can guess the rest. Avro turns this around. The data carries nothing but values, and everything else (which fields exist, in what order, of which type, and which may be missing) lives in a separate document: the schema. That makes Avro data small and fast, but it also means the schema is the most important thing you write. A field you forget to make optional, or a default you leave out, shows up later as an exception in someone else's code.

This guide reads the schema from the [Getting Started (Java)](https://avro.apache.org/docs/++version++/getting-started-java/#defining-a-schema) guide of Apache Avro 1.12.2 line by line. You'll learn what a record, a namespace and a field are, how a union with `null` makes a field optional, the difference between *nullable* and *has a default*, and how to parse and inspect a schema in Java with the `SchemaParser` that Avro 1.12 introduced, all checked by JUnit 6 tests.

## What is an Avro Schema?

An Avro schema is a JSON document that describes a type. The [Specification](https://avro.apache.org/docs/++version++/specification/#schema-declaration) allows three forms:

1. **A JSON string** naming a type, such as `"string"` or `"long"`.
2. **A JSON object** with a `type` attribute, such as `{"type": "record", ...}`.
3. **A JSON array**, which is a union of the types it lists, such as `["int", "null"]`.

The types themselves come in two families. The eight **primitive types** (`null`, `boolean`, `int`, `long`, `float`, `double`, `bytes` and `string`) hold a single value. The **complex types** (`record`, `enum`, `array`, `map`, `union` and `fixed`) are built from other types. Module 2 of this course covers all of them; the Getting Started schema needs only a record, two primitives and two unions.

## Anatomy of the User Schema

Here is the guide's schema, `src/main/resources/avro/user.avsc`, unchanged:

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

### The Record, Its Name and Its Namespace

`"type": "record"` makes this a [record](https://avro.apache.org/docs/++version++/specification/#schema-record): a named type with an ordered list of fields, much like a Java class with fields. A record must have a `name`. The `namespace` qualifies it, the way a package qualifies a Java class: together they form the **full name**, `example.avro.User`. The code generator turns that full name into the package `example.avro` and the class `User`.

### The Fields

Each field has a `name` and a `type`. The order of the fields is part of the schema: in the binary data, `name` is always written first, then `favorite_number`, then `favorite_color`, with no field names in between. A field can also have a `doc`, a `default`, an `order` and `aliases`, but the guide's schema keeps it minimal.

### Optional Fields: Unions with null

`"type": ["int", "null"]` is a [union](https://avro.apache.org/docs/++version++/specification/#unions): the value is either an `int` or `null`. This is how Avro says a value may be missing. There is no `optional` keyword; a union with `null` is the whole mechanism.

The order of the branches matters in two ways. In the binary data, each union value starts with the index of its branch, so for `["int", "null"]` an int is written as branch 0 and a missing value as branch 1. And if the field had a default, its value would have to fit the union. The guide puts `int` first; in your own schemas you'll more often see `["null", "int"]` with `"default": null`, which section 1.4 explains.

## Nullable Is Not the Same as Having a Default

This is the most important idea in this section. `favorite_number` is **nullable**: `null` is a valid value. But it has **no default**: the schema doesn't say what value to use when none is given. Those are separate properties:

- **Nullable** is about the data: can this field hold `null`?
- **A default** is about missing information: what should a builder, or a reader with a newer schema, use when the value isn't there?

Because the guide's three fields have no defaults, a builder must be given all three, even the nullable ones. [Section 1.4](04-creating-avro-records-in-java.md) shows the exception you get otherwise, and Module 7 shows why defaults are what make schema evolution work.

## Parsing a Schema in Java

The generated `User` class carries its schema, but you often need the schema itself: to inspect it, to validate data, or to work without generated code.

### Step 1: Parse a Schema File with SchemaParser

Avro 1.12 introduced [`SchemaParser`](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/SchemaParser.html), which the guide uses:

```java
Schema schema = new SchemaParser().parse(new File("src/main/resources/avro/user.avsc")).mainSchema();
```

`parse(..)` returns a result that can hold several schemas, because one file can define more than one named type. `mainSchema()` is the one the file itself defines.

### Step 2: Parse a Schema from the Classpath

Because the course keeps schemas under `src/main/resources`, they're on the classpath too, which works wherever the code runs:

```java
try (InputStream in = getClass().getClassLoader().getResourceAsStream("avro/user.avsc")) {
    Schema schema = new SchemaParser().parse(in).mainSchema();
}
```

### Step 3: Know the Classic Parser When You See It

Older code uses `new Schema.Parser().parse(..)`. It still works and returns the same `Schema`. The difference shows up with schemas that refer to types in other files, which Module 2 covers.

## Schema Walkthrough

A parsed schema is a navigable object. These are real calls and the values they return for `user.avsc`:

### The Record

```java
schema.getType();          // RECORD
schema.getName();          // User
schema.getNamespace();     // example.avro
schema.getFullName();      // example.avro.User
```

### A Field and Its Union

```java
Schema favoriteNumber = schema.getField("favorite_number").schema();

favoriteNumber.getType();                                  // UNION
favoriteNumber.getTypes();                                 // ["int", "null"]
favoriteNumber.isNullable();                               // true
schema.getField("favorite_color").pos();                   // 2
schema.getField("favorite_number").hasDefaultValue();      // false
```

### Back to JSON

`toString()` writes the schema back as compact JSON:

```json
{"type":"record","name":"User","namespace":"example.avro","fields":[{"name":"name","type":"string"},{"name":"favorite_number","type":["int","null"]},{"name":"favorite_color","type":["string","null"]}]}
```

That is exactly the string the code generator embeds in the `User` class, and exactly what a data file stores in its header.

> **Run every example yourself**
> All the code in this post is in Module 1 of the Java Apache Avro course: a Maven project where every Avro concept is a JUnit 6 test you can run, break and fix.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Read the course outline →](https://github.com/andre-mare/java-apache-avro/blob/main/Apache%20Avro%20Outline.md)

## Testing the Schema

`DefiningASchemaTest` parses the file once and checks each statement made above:

```java
class DefiningASchemaTest {

    private static Schema schema;

    @BeforeAll
    static void parseTheSchemaFile() throws IOException {
        schema = new SchemaParser().parse(new File("src/main/resources/avro/user.avsc")).mainSchema();
    }

    @Test
    void theSchemaIsARecordWithAFullName() {
        assertThat(schema.getType()).isEqualTo(Type.RECORD);
        assertThat(schema.getName()).isEqualTo("User");
        assertThat(schema.getNamespace()).isEqualTo("example.avro");
        assertThat(schema.getFullName()).isEqualTo("example.avro.User");
    }

    @Test
    void theSchemaHasThreeFieldsInOrder() {
        assertThat(schema.getFields()).extracting(Field::name)
            .containsExactly("name", "favorite_number", "favorite_color");
        assertThat(schema.getField("favorite_color").pos()).isEqualTo(2);
    }

    @Test
    void nameIsARequiredString() {
        Schema name = schema.getField("name").schema();

        assertThat(name.getType()).isEqualTo(Type.STRING);
        assertThat(name.isNullable()).isFalse();
    }

    @Test
    void favoriteNumberIsAUnionOfIntAndNull() {
        Schema favoriteNumber = schema.getField("favorite_number").schema();

        assertThat(favoriteNumber.getType()).isEqualTo(Type.UNION);
        assertThat(favoriteNumber.getTypes()).extracting(Schema::getType).containsExactly(Type.INT, Type.NULL);
        assertThat(favoriteNumber.isNullable()).isTrue();
    }

    @Test
    void noFieldHasADefault() {
        assertThat(schema.getFields()).noneMatch(Field::hasDefaultValue);
    }
}
```

The class has two more tests: `favoriteColorIsAUnionOfStringAndNull` and `theSchemaPrintsBackAsJson`. Notice how AssertJ's `extracting(..)` turns a list of fields into a list of names or types, so one assertion checks the whole order.

## Common Pitfalls

1. **Treating "nullable" as "optional".** A nullable field without a default still has to be given a value in a builder. If a field may be left out, give it `"default": null` and put `"null"` first in the union.
2. **Relying on field order you didn't intend.** The binary data has no field names; the order in the schema *is* the format. Reordering fields changes the bytes, though Module 7 shows that readers can still cope when they know both schemas.
3. **Writing an invalid name.** Names must start with a letter or underscore and contain only letters, digits and underscores. `favorite-number` would be rejected with a `SchemaParseException`.
4. **Typos in type names.** `"strin"` isn't a primitive, so the parser treats it as the name of a type you never defined and refuses the schema.

## Wrapping Up

The Getting Started schema is small, but it already shows the three ideas everything else in Avro builds on: a record is a named, ordered list of typed fields; a union with `null` is how a value becomes optional; and a default is a separate decision from nullability. In Java, `SchemaParser` turns the JSON into a `Schema` object you can inspect, and the same JSON comes back out of `toString()`.

Next, [section 1.3](03-compiling-avro-schemas.md) feeds this schema to the compiler and looks inside the `User` class it produces.

> **Run every example yourself**
> Run `mvn test -pl module-01 -Dtest=DefiningASchemaTest`, then change `"int"` to `"long"` in `user.avsc` and predict which tests fail before you run them again.
> [Get the code on GitHub →](https://github.com/andre-mare/java-apache-avro) · [Module 1 README →](https://github.com/andre-mare/java-apache-avro/blob/main/module-01/README.md)

---

**Filed under:** Apache Avro, Module 1: Getting Started

**Tags:** `apache-avro` `schemas` `avsc` `unions` `nullable` `schemaparser` `java` `junit6`

### About the Author

**André Maré** is a Senior Security Architect working on identity and access management for financial services and open banking. He writes example-driven courses in which every concept is a test you can run. The code for this series is at [github.com/andre-mare/java-apache-avro](https://github.com/andre-mare/java-apache-avro).

[GitHub](https://github.com/andre-mare) · [Website](https://andre-mare.github.io)

### Related Articles

- **Apache Avro · Module 1:** [Apache Avro in Java: Setting Up a Maven Project That Generates Code from Schemas](01-setting-up-an-avro-maven-project.md) · 8 November 2026
- **Apache Avro · Module 1:** [Compiling Avro Schemas: Inside the Generated Java Class](03-compiling-avro-schemas.md) · 8 November 2026
- **Apache Avro · Module 1:** [Creating Avro Records in Java: Setters, Constructors and Builders](04-creating-avro-records-in-java.md) · 8 November 2026

**References:** [Defining a schema](https://avro.apache.org/docs/++version++/getting-started-java/#defining-a-schema) · [Schema Declaration](https://avro.apache.org/docs/++version++/specification/#schema-declaration) · [Records](https://avro.apache.org/docs/++version++/specification/#schema-record) · [Unions](https://avro.apache.org/docs/++version++/specification/#unions) · [SchemaParser](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/SchemaParser.html) · [Schema](https://avro.apache.org/docs/++version++/api/java/org/apache/avro/Schema.html)
