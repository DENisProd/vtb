package ru.poib.VTBHack.mapping.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Полный результат сопоставления процесса с API эндпоинтами
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MappingResult {
    // Маппинг: Task ID -> API Endpoint
    private Map<String, TaskEndpointMapping> taskMappings;
    
    // Граф потока данных
    private List<DataFlowEdge> dataFlowEdges;
    
    // Несопоставленные элементы (только для задач BPMN)
    private List<UnmatchedElement> unmatchedTasks;
    
    // Статистика
    private double overallConfidence; // Средняя уверенность сопоставления
    private int totalTasks;
    private int matchedTasks;
    private int totalEndpoints; // Общее количество доступных эндпоинтов OpenAPI
    private int matchedEndpoints; // Количество использованных эндпоинтов
    
    // Общие поля, используемые в нескольких эндпоинтах
    private List<CommonField> commonFields;
    
    // Секретные поля, которые требуют ввода пользователем
    private List<SecretField> secretFields;
    
    // Отчет о проверке файлов с помощью ИИ
    private AIVerificationReport aiVerificationReport;
}


