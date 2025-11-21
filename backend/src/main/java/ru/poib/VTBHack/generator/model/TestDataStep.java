package ru.poib.VTBHack.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Тестовые данные для одного шага процесса
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDataStep {
    /**
     * ID задачи/шага процесса
     */
    private String taskId;
    
    /**
     * Имя задачи
     */
    private String taskName;
    
    /**
     * Тестовые данные для request (JSON объект)
     */
    private Map<String, Object> requestData;
    
    /**
     * Query параметры запроса (будут отправлены в URL)
     */
    private Map<String, Object> queryParams;
    
    /**
     * Тестовые данные для response (JSON объект)
     */
    private Map<String, Object> responseData;
    
    /**
     * Маппинг зависимостей: какие поля из response используются в других шагах
     */
    private Map<String, String> dataDependencies; // fieldName -> targetStepId
}


