package com.example.module_01.section_03;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import example.avro.User;

import org.apache.avro.SchemaParser;
import org.junit.jupiter.api.Test;

/**
 * Module 1.3 – compiling the schema.
 * <p>
 * The guide compiles {@code user.avsc} with {@code avro-tools}:
 * <pre>
 * java -jar avro-tools-1.12.2.jar compile schema src/main/resources/avro/user.avsc /tmp/generated
 * </pre>
 * The avro-maven-plugin does the same on every build. These tests look at what the compiler made of the schema, using
 * reflection on the generated class.
 */
class CompilingTheSchemaTest {

    @Test
    void fieldNamesBecomeCamelCaseAccessors() throws NoSuchMethodException {
        // favorite_number in the schema; getFavoriteNumber and setFavoriteNumber in Java.
        assertThat(User.class.getMethod("getFavoriteNumber")).isNotNull();
        assertThat(User.class.getMethod("setFavoriteNumber", Integer.class)).isNotNull();
        assertThat(User.class.getMethod("getFavoriteColor")).isNotNull();
    }

    @Test
    void aNullableIntIsABoxedInteger() throws NoSuchMethodException {
        // ["int", "null"] can hold null, so it can't be a primitive int.
        assertThat(User.class.getMethod("getFavoriteNumber").getReturnType()).isEqualTo(Integer.class);
    }

    @Test
    void stringsAreCharSequencesByDefault() throws NoSuchMethodException {
        // The guide's plugin configuration doesn't set stringType, so the default applies: CharSequence. Section 7
        // shows what that means after a round trip; Module 3 shows how to get String instead.
        assertThat(User.class.getMethod("getName").getReturnType()).isEqualTo(CharSequence.class);
    }

    @Test
    void theClassEmbedsItsSchema() {
        assertThat(User.SCHEMA$).isSameAs(User.getClassSchema());
        assertThat(new User().getSchema()).isSameAs(User.SCHEMA$);
    }

    @Test
    void theEmbeddedSchemaIsTheSchemaInTheFile() throws IOException {
        assertThat(User.getClassSchema())
            .isEqualTo(new SchemaParser().parse(new File("src/main/resources/avro/user.avsc")).mainSchema());
    }
}
