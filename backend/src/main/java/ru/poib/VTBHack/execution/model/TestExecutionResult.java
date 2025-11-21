package ru.poib.VTBHack.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Результат выполнения теста
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestExecutionResult {
    /**
     * Общий статус выполнения: SUCCESS, FAILED, PARTIAL
     */
    private ExecutionStatus status;
    
    /**
     * Время начала выполнения
     */
    private Instant startTime;
    
    /**
     * Время окончания выполнения
     */
    private Instant endTime;
    
    /**
     * Общая длительность выполнения в миллисекундах
     */
    private long totalDurationMs;
    
    /**
     * Список результатов выполнения каждого шага
     */
    private List<TestExecutionStep> steps = new ArrayList<>();
    
    /**
     * Список выявленных проблем
     */
    private List<ExecutionProblem> problems = new ArrayList<>();
    
    /**
     * Статистика выполнения
     */
    private ExecutionStatistics statistics;
    
    /**
     * ID процесса
     */
    private String processId;
    
    /**
     * Название процесса
     */
    private String processName;
    
    public enum ExecutionStatus {
        SUCCESS,    // Все шаги выполнены успешно
        FAILED,     // Выполнение завершилось с ошибкой
        PARTIAL     // Часть шагов выполнена успешно, часть - с ошибками
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStatistics {
        /**
         * Общее количество шагов
         */
        private int totalSteps;
        
        /**
         * Количество успешно выполненных шагов
         */
        private int successfulSteps;
        
        /**
         * Количество шагов с ошибками
         */
        private int failedSteps;
        
        /**
         * Количество пропущенных шагов
         */
        private int skippedSteps;
        
        /**
         * Среднее время выполнения шага в миллисекундах
         */
        private double averageStepDurationMs;
        
        /**
         * Минимальное время выполнения шага в миллисекундах
         */
        private long minStepDurationMs;
        
        /**
         * Максимальное время выполнения шага в миллисекундах
         */
        private long maxStepDurationMs;
        
        /**
         * Общее количество HTTP запросов
         */
        private int totalRequests;
        
        /**
         * Количество успешных HTTP запросов (status 2xx)
         */
        private int successfulRequests;
        
        /**
         * Количество ошибок валидации
         */
        private int validationErrors;
    }
}

