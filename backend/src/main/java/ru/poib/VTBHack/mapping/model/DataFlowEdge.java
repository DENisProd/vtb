package ru.poib.VTBHack.mapping.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ребро графа потока данных между шагами процесса
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataFlowEdge {
    private String sourceTaskId;
    private String targetTaskId;
    private List<String> fields; // Поля из response шага N, используемые в request шага N+1
    private double confidence; // Уверенность в определении потока данных
    
    // Дополнительная информация о параметрах, которые нужно заполнить
    // Ключ: имя параметра (например, "x-consent-id"), значение: информация о параметре
    private Map<String, ParameterMapping> parameterMappings;
    
    /**
     * Информация о маппинге параметра: откуда брать значение и куда его подставлять
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterMapping {
        private String parameterName;      // Имя параметра (например, "x-consent-id")
        private String parameterIn;        // Тип параметра: header, query, path
        private String sourceField;        // Поле из ответа source-эндпоинта (например, "consentId" или "id")
        private String fieldHint;          // Подсказка о поле (может быть null)
    }
    
    /**
     * Конструктор для обратной совместимости
     */
    public DataFlowEdge(String sourceTaskId, String targetTaskId, List<String> fields, double confidence) {
        this.sourceTaskId = sourceTaskId;
        this.targetTaskId = targetTaskId;
        this.fields = fields;
        this.confidence = confidence;
        this.parameterMappings = new HashMap<>();
    }
}


