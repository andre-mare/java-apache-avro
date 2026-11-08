package com.example.module_01.section_04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.gettingstarted.Users;

import example.avro.User;

import org.apache.avro.AvroMissingFieldException;
import org.junit.jupiter.api.Test;

/**
 * Module 1.4 – creating users, in the guide's three ways.
 * <ul>
 *   <li>Alyssa: the no-argument constructor and setters.</li>
 *   <li>Ben: the all-arguments constructor.</li>
 *   <li>Charlie: the builder.</li>
 * </ul>
 * See {@link Users} for the code. The guide notes the trade-off: constructors and setters are fast and check nothing;
 * the builder checks every field and fills in defaults.
 */
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
        // The guide prints users this way when it reads them back.
        assertThat(Users.ben()).hasToString("{\"name\": \"Ben\", \"favorite_number\": 7, \"favorite_color\": \"red\"}");
    }
}
