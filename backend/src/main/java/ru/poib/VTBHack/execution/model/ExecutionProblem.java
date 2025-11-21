package ru.poib.VTBHack.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Выявленная проблема при выполнении теста
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionProblem {
    /**
     * Тип проблемы
     */
    private ProblemType type;
    
    /**
     * ID шага, на котором возникла проблема
     */
    private String stepId;
    
    /**
     * Название шага
     */
    private String stepName;
    
    /**
     * Уровень серьезности
     */
    private Severity severity;
    
    /**
     * Сообщение об ошибке
     */
    private String message;
    
    /**
     * Детали ошибки (stack trace, дополнительные данные)
     */
    private String details;
    
    /**
     * Время возникновения проблемы
     */
    private Instant timestamp;
    
    /**
     * URL запроса, на котором возникла проблема
     */
    private String requestUrl;
    
    /**
     * HTTP метод запроса
     */
    private String requestMethod;
    
    public enum ProblemType {
        TIMEOUT,
        NETWORK_ERROR,
        HTTP_ERROR,
        VALIDATION_ERROR,
        SCHEMA_ERROR,
        CONTRACT_ERROR,
        BUSINESS_LOGIC_ERROR,
        UNEXPECTED_RESPONSE,
        DATA_EXTRACTION_ERROR
    }
    
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }
}

