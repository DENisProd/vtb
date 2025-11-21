package ru.poib.VTBHack.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Результат генерации тестовых данных
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDataGenerationResult {
    /**
     * Тип генерации, который был использован
     */
    private GenerationType generationType;
    
    /**
     * Сценарий генерации
     */
    private String scenario;
    
    /**
     * Список вариантов тестовых данных
     * Каждый вариант содержит данные для всех шагов процесса
     */
    private List<List<TestDataStep>> variants;
    
    /**
     * Маппинг зависимостей между шагами
     * Формат: "step1.fieldName" -> "step2.fieldName"
     */
    private Map<String, String> crossStepDependencies;
    
    /**
     * Статистика генерации
     */
    private GenerationStatistics statistics;
    
    /**
     * Статистика генерации
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationStatistics {
        /**
         * Общее количество сгенерированных полей
         */
        private int totalFieldsGenerated;
        
        /**
         * Количество полей, сгенерированных с использованием умных правил
         */
        private int smartFieldsGenerated;
        
        /**
         * Количество полей, сгенерированных на основе примеров из OpenAPI
         */
        private int exampleBasedFields;
        
        /**
         * Время генерации в миллисекундах
         */
        private long generationTimeMs;
        
        /**
         * Процент валидных данных (оценка)
         */
        private double validityPercentage;
    }
}


