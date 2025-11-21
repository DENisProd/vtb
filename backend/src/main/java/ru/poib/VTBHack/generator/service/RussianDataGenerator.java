package ru.poib.VTBHack.generator.service;

import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Генератор российских данных (ИНН, СНИЛС, паспорта)
 */
@Component
public class RussianDataGenerator {
    private final Random random = new Random();
    
    /**
     * Генерирует валидный ИНН (10 или 12 цифр)
     */
    public String generateINN() {
        // Генерируем 10-значный ИНН для юридических лиц
        int[] inn = new int[10];
        for (int i = 0; i < 9; i++) {
            inn[i] = random.nextInt(10);
        }
        
        // Вычисляем контрольную сумму для 10-значного ИНН
        int[] coefficients = {2, 4, 10, 3, 5, 9, 4, 6, 8};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += inn[i] * coefficients[i];
        }
        inn[9] = (sum % 11) % 10;
        
        StringBuilder sb = new StringBuilder();
        for (int digit : inn) {
            sb.append(digit);
        }
        return sb.toString();
    }
    
    /**
     * Генерирует валидный СНИЛС (11 цифр в формате XXX-XXX-XXX XX)
     */
    public String generateSNILS() {
        int[] snils = new int[11];
        for (int i = 0; i < 9; i++) {
            snils[i] = random.nextInt(10);
        }
        
        // Вычисляем контрольную сумму
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += snils[i] * (9 - i);
        }
        
        int checkSum = sum % 101;
        if (checkSum == 100 || checkSum == 101) {
            checkSum = 0;
        }
        
        snils[9] = checkSum / 10;
        snils[10] = checkSum % 10;
        
        return String.format("%d%d%d-%d%d%d-%d%d%d %d%d",
                snils[0], snils[1], snils[2],
                snils[3], snils[4], snils[5],
                snils[6], snils[7], snils[8],
                snils[9], snils[10]);
    }
    
    /**
     * Генерирует номер паспорта РФ (10 цифр в формате XXXX XXXXXX)
     */
    public String generatePassportNumber() {
        // Серия (4 цифры)
        int series = 1000 + random.nextInt(9000);
        // Номер (6 цифр)
        int number = 100000 + random.nextInt(900000);
        
        return String.format("%04d %06d", series, number);
    }
    
    /**
     * Генерирует БИК банка (9 цифр)
     */
    public String generateBIK() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
    
    /**
     * Генерирует корреспондентский счет (20 цифр)
     */
    public String generateCorrespondentAccount() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
    
    /**
     * Генерирует расчетный счет (20 цифр)
     */
    public String generateSettlementAccount() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}


