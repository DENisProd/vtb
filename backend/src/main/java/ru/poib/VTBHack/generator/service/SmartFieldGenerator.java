package ru.poib.VTBHack.generator.service;

import com.github.javafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Умный генератор данных на основе имен полей и описаний
 */
@Component
public class SmartFieldGenerator {
    private final Faker faker;
    private final RussianDataGenerator russianDataGenerator;
    
    @Autowired
    public SmartFieldGenerator(RussianDataGenerator russianDataGenerator) {
        this.faker = new Faker(Locale.forLanguageTag("ru"));
        this.russianDataGenerator = russianDataGenerator;
    }
    
    /**
     * Генерирует значение на основе имени поля и описания
     */
    public Object generateByFieldName(String fieldName, String description, String type, String format) {
        if (fieldName == null) {
            return generateByType(type, format);
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        
        // Email
        if (lowerFieldName.contains("email") || lowerFieldName.contains("e-mail")) {
            return faker.internet().emailAddress();
        }
        
        // Телефон
        if (lowerFieldName.contains("phone") || lowerFieldName.contains("tel")) {
            return generatePhoneNumber();
        }
        
        // ИНН
        if (lowerFieldName.contains("inn")) {
            return russianDataGenerator.generateINN();
        }
        
        // СНИЛС
        if (lowerFieldName.contains("snils")) {
            return russianDataGenerator.generateSNILS();
        }
        
        // Паспорт
        if (lowerFieldName.contains("passport")) {
            return russianDataGenerator.generatePassportNumber();
        }
        
        // БИК
        if (lowerFieldName.contains("bik")) {
            return russianDataGenerator.generateBIK();
        }
        
        // Счет
        if (lowerFieldName.contains("account") || lowerFieldName.contains("счет")) {
            if (lowerFieldName.contains("correspondent") || lowerFieldName.contains("корреспондентский")) {
                return russianDataGenerator.generateCorrespondentAccount();
            }
            return russianDataGenerator.generateSettlementAccount();
        }
        
        // Сумма, деньги
        if (lowerFieldName.contains("amount") || lowerFieldName.contains("sum") || 
            lowerFieldName.contains("price") || lowerFieldName.contains("cost") ||
            lowerFieldName.contains("сумма") || lowerFieldName.contains("цена")) {
            return generateRealisticAmount();
        }
        
        // Дата рождения
        if (lowerFieldName.contains("birthdate") || lowerFieldName.contains("birth") ||
            lowerFieldName.contains("дата рождения")) {
            return faker.date().birthday().toString();
        }
        
        // Имя
        if (lowerFieldName.contains("firstname") || lowerFieldName.contains("имя") ||
            (lowerFieldName.contains("name") && !lowerFieldName.contains("last"))) {
            return faker.name().firstName();
        }
        
        // Фамилия
        if (lowerFieldName.contains("lastname") || lowerFieldName.contains("surname") ||
            lowerFieldName.contains("фамилия")) {
            return faker.name().lastName();
        }
        
        // Отчество
        if (lowerFieldName.contains("middlename") || lowerFieldName.contains("отчество")) {
            return faker.name().firstName() + "ович"; // Упрощенная генерация отчества
        }
        
        // Адрес
        if (lowerFieldName.contains("address") || lowerFieldName.contains("адрес")) {
            return faker.address().fullAddress();
        }
        
        // Город
        if (lowerFieldName.contains("city") || lowerFieldName.contains("город")) {
            return faker.address().city();
        }
        
        // Страна
        if (lowerFieldName.contains("country") || lowerFieldName.contains("страна")) {
            return "Россия";
        }
        
        // ID
        if (lowerFieldName.contains("id") && !lowerFieldName.contains("guid") && !lowerFieldName.contains("uuid")) {
            return faker.number().randomNumber(10, true);
        }
        
        // UUID
        if (lowerFieldName.contains("uuid") || lowerFieldName.contains("guid")) {
            return java.util.UUID.randomUUID().toString();
        }
        
        // URL
        if (lowerFieldName.contains("url") || lowerFieldName.contains("link")) {
            return faker.internet().url();
        }
        
        // Если не найдено специальное правило, генерируем по типу
        return generateByType(type, format);
    }
    
    /**
     * Генерирует телефонный номер в российском формате
     */
    private String generatePhoneNumber() {
        String[] prefixes = {"+7", "8"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        return prefix + " (" + (900 + random.nextInt(100)) + ") " + 
               (100 + random.nextInt(900)) + "-" + (10 + random.nextInt(90)) + "-" + (10 + random.nextInt(90));
    }
    
    /**
     * Генерирует реалистичную сумму
     */
    private Object generateRealisticAmount() {
        // Генерируем суммы от 100 до 1,000,000 рублей
        double amount = 100.0 + random.nextDouble() * 999900.0;
        return Math.round(amount * 100.0) / 100.0; // Округляем до 2 знаков
    }
    
    /**
     * Генерирует значение по типу и формату
     */
    private Object generateByType(String type, String format) {
        if (type == null) {
            return faker.lorem().word();
        }
        
        switch (type.toLowerCase()) {
            case "string":
                if (format != null) {
                    switch (format.toLowerCase()) {
                        case "email":
                            return faker.internet().emailAddress();
                        case "uri":
                        case "url":
                            return faker.internet().url();
                        case "date":
                            return faker.date().birthday().toString();
                        case "date-time":
                            return java.time.Instant.now().toString();
                        case "uuid":
                            return java.util.UUID.randomUUID().toString();
                    }
                }
                return faker.lorem().word();
                
            case "integer":
            case "int":
                return faker.number().numberBetween(1, 10000);
                
            case "number":
            case "float":
            case "double":
                return faker.number().randomDouble(2, 1, 10000);
                
            case "boolean":
            case "bool":
                return faker.bool().bool();
                
            case "array":
                return new java.util.ArrayList<>();
                
            case "object":
                return new java.util.HashMap<>();
                
            default:
                return faker.lorem().word();
        }
    }
    
    private final java.util.Random random = new java.util.Random();
}

