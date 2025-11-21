package ru.poib.VTBHack.generator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SmartFieldGeneratorTest {

    private final RussianDataGenerator russian = new RussianDataGenerator();
    private final SmartFieldGenerator smart = new SmartFieldGenerator(russian);

    @Test
    void emailGeneration() {
        Object val = smart.generateByFieldName("userEmail", null, "string", "email");
        assertNotNull(val);
        assertTrue(val instanceof String);
        assertTrue(((String) val).contains("@"));
    }

    @Test
    void innAndSnilsAndPassport() {
        Object inn = smart.generateByFieldName("inn", null, "string", null);
        Object snils = smart.generateByFieldName("snils", null, "string", null);
        Object passport = smart.generateByFieldName("passportNumber", null, "string", null);

        assertNotNull(inn);
        assertTrue(((String) inn).matches("\\d{10}"));

        assertNotNull(snils);
        assertTrue(((String) snils).matches("\\d{3}-\\d{3}-\\d{3} \\d{2}"));

        assertNotNull(passport);
        assertTrue(((String) passport).matches("\\d{4} \\d{6}"));
    }

    @Test
    void phoneAndUuidAndAmount() {
        Object phone = smart.generateByFieldName("phoneNumber", null, null, null);
        Object uuid = smart.generateByFieldName("orderUuid", null, null, null);
        Object amount = smart.generateByFieldName("totalAmount", null, null, null);

        assertNotNull(phone);
        assertTrue(phone instanceof String);

        assertNotNull(uuid);
        assertTrue(uuid instanceof String);

        assertNotNull(amount);
        assertTrue(amount instanceof Number || amount instanceof Double || amount instanceof Float || amount instanceof Integer);
    }
}
