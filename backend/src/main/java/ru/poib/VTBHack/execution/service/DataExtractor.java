package ru.poib.VTBHack.execution.service;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для извлечения данных из JSON ответов с использованием JSONPath
 */
@Slf4j
@Service
public class DataExtractor {
    
    /**
     * Извлекает значения из JSON по JSONPath выражениям
     * 
     * @param jsonBody JSON тело ответа
     * @param jsonPaths Map: имя переменной -> JSONPath выражение
     * @return Map: имя переменной -> извлеченное значение
     */
    public Map<String, Object> extractData(String jsonBody, Map<String, String> jsonPaths) {
        Map<String, Object> extractedData = new HashMap<>();
        
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            log.warn("Empty JSON body, cannot extract data");
            return extractedData;
        }
        
        try {
            Object document = JsonPath.parse(jsonBody).json();
            
            for (Map.Entry<String, String> entry : jsonPaths.entrySet()) {
                String variableName = entry.getKey();
                String jsonPathExpression = entry.getValue();
                
                try {
                    Object value = JsonPath.read(document, jsonPathExpression);
                    extractedData.put(variableName, value);
                    log.debug("Extracted {} = {} from JSONPath: {}", variableName, value, jsonPathExpression);
                } catch (PathNotFoundException e) {
                    log.warn("Path not found for variable {} with JSONPath: {}", variableName, jsonPathExpression);
                    // Не добавляем в extractedData, если путь не найден
                } catch (Exception e) {
                    log.error("Error extracting data for variable {} with JSONPath {}: {}", 
                            variableName, jsonPathExpression, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error parsing JSON body: {}", e.getMessage());
        }
        
        return extractedData;
    }
    
    /**
     * Извлекает значение по одному JSONPath выражению
     * 
     * @param jsonBody JSON тело ответа
     * @param jsonPath JSONPath выражение
     * @return извлеченное значение или null
     */
    public Object extractValue(String jsonBody, String jsonPath) {
        if (jsonBody == null || jsonBody.trim().isEmpty() || jsonPath == null) {
            return null;
        }
        
        try {
            Object document = JsonPath.parse(jsonBody).json();
            return JsonPath.read(document, jsonPath);
        } catch (PathNotFoundException e) {
            log.debug("Path not found: {}", jsonPath);
            return null;
        } catch (Exception e) {
            log.error("Error extracting value with JSONPath {}: {}", jsonPath, e.getMessage());
            return null;
        }
    }
}

