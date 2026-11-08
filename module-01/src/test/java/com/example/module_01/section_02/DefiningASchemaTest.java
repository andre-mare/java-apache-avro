package com.example.module_01.section_02;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.SchemaParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.2 – defining a schema.
 * <p>
 * {@code src/main/resources/avro/user.avsc} is the guide's schema, unchanged: a record named {@code User} in the namespace
 * {@code example.avro}, with three fields. Two of them are unions with {@code null}, which is how Avro says a value
 * may be missing.
 * <p>
 * The schema is parsed with {@link SchemaParser}, the parser Avro 1.12 introduced and the guide uses.
 * {@code parse(..)} returns a result that can hold several schemas; {@code mainSchema()} is the one the file
 * defines.
 */
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
        assertThat(schema.getFullName()).isEqualTo("example.avro.User");   // the namespace and the name together
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
    void favoriteColorIsAUnionOfStringAndNull() {
        assertThat(schema.getField("favorite_color").schema().getTypes()).extracting(Schema::getType)
            .containsExactly(Type.STRING, Type.NULL);
    }

    @Test
    void noFieldHasADefault() {
        // This matters in section 4: a builder can't fill in a field that has no default, even a nullable one.
        assertThat(schema.getFields()).noneMatch(Field::hasDefaultValue);
    }

    @Test
    void theSchemaPrintsBackAsJson() {
        assertThat(schema.toString())
            .startsWith("{\"type\":\"record\",\"name\":\"User\",\"namespace\":\"example.avro\"");
    }
}
