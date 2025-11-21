package ru.poib.VTBHack.generator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RussianDataGeneratorTest {

    private final RussianDataGenerator gen = new RussianDataGenerator();

    @Test
    void innShouldHave10Digits() {
        String inn = gen.generateINN();
        assertNotNull(inn);
        assertTrue(inn.matches("\\d{10}"), "INN must be 10 digits");
    }

    @Test
    void snilsShouldMatchPattern() {
        String snils = gen.generateSNILS();
        assertNotNull(snils);
        assertTrue(snils.matches("\\d{3}-\\d{3}-\\d{3} \\d{2}"), "SNILS must match XXX-XXX-XXX XX");
    }

    @Test
    void passportShouldMatchFormat() {
        String passport = gen.generatePassportNumber();
        assertNotNull(passport);
        assertTrue(passport.matches("\\d{4} \\d{6}"), "Passport must match 'XXXX XXXXXX'");
    }

    @Test
    void bikShouldHave9Digits() {
        String bik = gen.generateBIK();
        assertNotNull(bik);
        assertEquals(9, bik.length());
        assertTrue(bik.matches("\\d{9}"));
    }

    @Test
    void accountsShouldHave20Digits() {
        String corr = gen.generateCorrespondentAccount();
        String sett = gen.generateSettlementAccount();
        assertNotNull(corr);
        assertNotNull(sett);
        assertEquals(20, corr.length());
        assertEquals(20, sett.length());
        assertTrue(corr.matches("\\d{20}"));
        assertTrue(sett.matches("\\d{20}"));
    }
}
