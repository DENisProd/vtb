package ru.poib.VTBHack.mapping.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Результат сопоставления задачи с API эндпоинтом
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskEndpointMapping {
    private String taskId;
    private String taskName;
    private String endpointPath;
    private String endpointMethod;
    private String operationId;
    private double confidenceScore; // 0.0 - 1.0
    private String matchingStrategy; // EXACT, DESCRIPTION, CUSTOM_PROPERTY, SEMANTIC
    private String recommendation; // Для ручной корректировки
    // Пользовательские переопределения request data для этого шага (ключ->значение)
    private Map<String, Object> customRequestData;
}


