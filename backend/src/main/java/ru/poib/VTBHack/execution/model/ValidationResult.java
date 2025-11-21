package ru.poib.VTBHack.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат валидации ответа
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    /**
     * Общий результат валидации
     */
    private boolean isValid;
    
    /**
     * Проверка HTTP status code
     */
    private StatusCodeValidation statusCodeValidation;
    
    /**
     * Проверка схемы JSON
     */
    private SchemaValidation schemaValidation;
    
    /**
     * Проверка контрактов (content-type, обязательные поля)
     */
    private ContractValidation contractValidation;
    
    /**
     * Проверка производительности
     */
    private PerformanceValidation performanceValidation;
    
    /**
     * Список всех ошибок валидации
     */
    private List<String> errors = new ArrayList<>();
    
    /**
     * Список всех предупреждений
     */
    private List<String> warnings = new ArrayList<>();
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCodeValidation {
        private boolean isValid;
        private int expectedStatusCode;
        private int actualStatusCode;
        private String message;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaValidation {
        private boolean isValid;
        private String message;
        private List<String> schemaErrors = new ArrayList<>();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractValidation {
        private boolean isValid;
        private String expectedContentType;
        private String actualContentType;
        private List<String> missingRequiredFields = new ArrayList<>();
        private String message;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceValidation {
        private boolean isValid;
        private long responseTimeMs;
        private long maxAllowedTimeMs;
        private String message;
    }
}

