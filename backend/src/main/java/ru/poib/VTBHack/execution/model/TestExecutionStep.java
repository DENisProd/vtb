package ru.poib.VTBHack.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Результат выполнения одного шага теста
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestExecutionStep {
    /**
     * ID задачи/шага
     */
    private String taskId;
    
    /**
     * Название задачи
     */
    private String taskName;
    
    /**
     * Статус выполнения: SUCCESS, FAILED, SKIPPED
     */
    private StepStatus status;
    
    /**
     * Время начала выполнения
     */
    private Instant startTime;
    
    /**
     * Время окончания выполнения
     */
    private Instant endTime;
    
    /**
     * Длительность выполнения в миллисекундах
     */
    private long durationMs;
    
    /**
     * Детали запроса
     */
    private RequestDetails request;
    
    /**
     * Детали ответа
     */
    private ResponseDetails response;
    
    /**
     * Результат валидации
     */
    private ValidationResult validation;
    
    /**
     * Извлеченные данные из response для использования в следующих шагах
     */
    private Map<String, Object> extractedData;
    
    /**
     * Сообщение об ошибке (если есть)
     */
    private String errorMessage;
    
    public enum StepStatus {
        SUCCESS,
        FAILED,
        SKIPPED
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestDetails {
        private String url;
        private String method;
        private Map<String, String> headers;
        private String body;
        private Instant timestamp;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseDetails {
        private int statusCode;
        private Map<String, String> headers;
        private String body;
        private long responseTimeMs;
        private Instant timestamp;
    }
}

