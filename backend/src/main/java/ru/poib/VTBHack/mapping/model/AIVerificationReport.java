package ru.poib.VTBHack.mapping.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Отчет о проверке файлов с помощью ИИ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIVerificationReport {
    /**
     * Результаты проверки OpenAPI файла
     */
    private FileVerificationResult openapi;
    
    /**
     * Результаты проверки BPMN файла
     */
    private FileVerificationResult bpmn;
    
    /**
     * Общий статус проверки (ok, warning, error)
     */
    private String overallStatus;
    
    /**
     * Общее количество ошибок
     */
    private int totalErrors;
    
    /**
     * Общее количество предупреждений
     */
    private int totalWarnings;
    
    /**
     * Общее количество рекомендаций
     */
    private int totalSuggestions;

    private String rawModelOutput;

    private String rawModelStderr;
    
    /**
     * Результат проверки одного файла
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileVerificationResult {
        /**
         * Статус проверки (ok, warning, error)
         */
        private String status;
        
        /**
         * Список ошибок
         */
        private List<String> errors;
        
        /**
         * Список предупреждений
         */
        private List<String> warnings;
        
        /**
         * Список рекомендаций
         */
        private List<String> suggestions;
        
        /**
         * Краткое резюме
         */
        private String summary;
    }
}

