package ru.poib.VTBHack.generator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.poib.VTBHack.parser.model.openapi.Response;

import java.util.*;

/**
 * Генератор данных на основе JSON Schema из OpenAPI
 */
@Component
public class SchemaDataGenerator {
    private final SmartFieldGenerator smartFieldGenerator;
    
    @Autowired
    public SchemaDataGenerator(SmartFieldGenerator smartFieldGenerator) {
        this.smartFieldGenerator = smartFieldGenerator;
    }
    
    /**
     * Генерирует данные на основе Schema из OpenAPI
     */
    public Object generateFromSchema(Response.Schema schema, String fieldName, String description) {
        if (schema == null) {
            return smartFieldGenerator.generateByFieldName(fieldName, description, "string", null);
        }
        
        // Проверяем наличие example
        if (schema.getAdditionalProperties() != null) {
            Object example = schema.getAdditionalProperties().get("example");
            if (example != null) {
                return example;
            }
        }
        
        String type = schema.getType();
        String format = schema.getFormat();
        
        // Обрабатываем enum
        if (schema.getAdditionalProperties() != null) {
            Object enumValue = schema.getAdditionalProperties().get("enum");
            if (enumValue instanceof List && !((List<?>) enumValue).isEmpty()) {
                List<?> enumList = (List<?>) enumValue;
                return enumList.get(new Random().nextInt(enumList.size()));
            }
        }
        
        // Обрабатываем объект с properties
        if ("object".equals(type) || schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return generateObject(schema, fieldName);
        }
        
        // Обрабатываем массив
        if ("array".equals(type)) {
            return generateArray(schema, fieldName);
        }
        
        // Генерируем примитивные типы с учетом constraints
        return generatePrimitive(schema, fieldName, description, type, format);
    }
    
    /**
     * Генерирует объект на основе Schema
     */
    private Map<String, Object> generateObject(Response.Schema schema, String parentFieldName) {
        Map<String, Object> result = new HashMap<>();
        
        if (schema.getProperties() == null || schema.getProperties().isEmpty()) {
            return result;
        }
        
            // Получаем список required полей
        Set<String> requiredFields = new HashSet<>();
        if (schema.getAdditionalProperties() != null) {
            Object required = schema.getAdditionalProperties().get("required");
            if (required instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<String> requiredList = (List<String>) required;
                requiredFields.addAll(requiredList);
            }
        }
        
        // Генерируем данные для каждого свойства
        for (Map.Entry<String, Response.Schema> entry : schema.getProperties().entrySet()) {
            String fieldName = entry.getKey();
            Response.Schema fieldSchema = entry.getValue();
            
            // Генерируем только required поля или случайные опциональные
            boolean isRequired = requiredFields.contains(fieldName);
            if (isRequired || new Random().nextBoolean()) {
                String fullFieldName = parentFieldName != null ? parentFieldName + "." + fieldName : fieldName;
                String description = fieldSchema.getDescription();
                
                Object value = generateFromSchema(fieldSchema, fullFieldName, description);
                result.put(fieldName, value);
            }
        }
        
        return result;
    }
    
    /**
     * Генерирует массив на основе Schema
     */
    private List<Object> generateArray(Response.Schema schema, String fieldName) {
        List<Object> result = new ArrayList<>();
        
        // Получаем items schema
        Response.Schema itemsSchema = null;
        if (schema.getAdditionalProperties() != null) {
            Object items = schema.getAdditionalProperties().get("items");
            if (items instanceof Map) {
                // Преобразуем Map в Schema (упрощенная версия)
                itemsSchema = new Response.Schema();
                @SuppressWarnings("unchecked")
                Map<String, Object> itemsMap = (Map<String, Object>) items;
                if (itemsMap.containsKey("type")) {
                    itemsSchema.setType((String) itemsMap.get("type"));
                }
                if (itemsMap.containsKey("format")) {
                    itemsSchema.setFormat((String) itemsMap.get("format"));
                }
                if (itemsMap.containsKey("properties")) {
                    // Обрабатываем вложенные properties
                    Map<String, Response.Schema> properties = new HashMap<>();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propsMap = (Map<String, Object>) itemsMap.get("properties");
                    for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
                        Response.Schema propSchema = new Response.Schema();
                        if (entry.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> propMap = (Map<String, Object>) entry.getValue();
                            if (propMap.containsKey("type")) {
                                propSchema.setType((String) propMap.get("type"));
                            }
                            if (propMap.containsKey("format")) {
                                propSchema.setFormat((String) propMap.get("format"));
                            }
                        }
                        properties.put(entry.getKey(), propSchema);
                    }
                    itemsSchema.setProperties(properties);
                }
            }
        }
        
        // Генерируем от 1 до 3 элементов
        int minItems = getIntValue(schema, "minItems", 1);
        int maxItems = getIntValue(schema, "maxItems", 3);
        int count = minItems + new Random().nextInt(maxItems - minItems + 1);
        
        for (int i = 0; i < count; i++) {
            if (itemsSchema != null) {
                result.add(generateFromSchema(itemsSchema, fieldName + "[" + i + "]", null));
            } else {
                result.add(smartFieldGenerator.generateByFieldName(fieldName, null, "string", null));
            }
        }
        
        return result;
    }
    
    /**
     * Генерирует примитивное значение с учетом constraints
     */
    private Object generatePrimitive(Response.Schema schema, String fieldName, String description, 
                                     String type, String format) {
        // Получаем constraints из additionalProperties
        Map<String, Object> additionalProps = schema.getAdditionalProperties();
        
        // Для строковых типов
        if ("string".equals(type)) {
            Object generated = smartFieldGenerator.generateByFieldName(fieldName, description, type, format);
            String value = generated != null ? String.valueOf(generated) : "";
            
            // Применяем pattern если есть
            if (additionalProps != null && additionalProps.containsKey("pattern")) {
                // Упрощенная обработка pattern - просто возвращаем сгенерированное значение
                // В реальной реализации можно использовать библиотеку для генерации по regex
            }
            
            // Применяем minLength и maxLength
            if (additionalProps != null) {
                Integer minLength = getIntValue(additionalProps, "minLength");
                Integer maxLength = getIntValue(additionalProps, "maxLength");
                if (minLength != null || maxLength != null) {
                    int targetLength = maxLength != null ? maxLength : (minLength != null ? minLength : 10);
                    if (minLength != null && targetLength < minLength) {
                        targetLength = minLength;
                    }
                    // Упрощаем/расширяем строку до нужной длины
                    while (value.length() < targetLength) {
                        value += "x";
                    }
                    if (value.length() > targetLength) {
                        value = value.substring(0, targetLength);
                    }
                }
            }
            
            return value;
        }
        
        // Для числовых типов
        if ("integer".equals(type) || "number".equals(type)) {
            Object generated = smartFieldGenerator.generateByFieldName(fieldName, description, type, format);
            Double baseValue = null;
            if (generated instanceof Number) {
                baseValue = ((Number) generated).doubleValue();
            } else if (generated instanceof String) {
                try {
                    baseValue = Double.parseDouble((String) generated);
                } catch (NumberFormatException ignored) {
                }
            }
            
            if (additionalProps != null) {
                Double min = getDoubleValue(additionalProps, "minimum");
                Double max = getDoubleValue(additionalProps, "maximum");
                
                if (min != null || max != null) {
                    double minVal = min != null ? min : Double.MIN_VALUE;
                    double maxVal = max != null ? max : Double.MAX_VALUE;
                    double seed = baseValue != null ? baseValue : new Random().nextDouble();
                    double range = maxVal - minVal;
                    double randomValue = minVal + (Math.abs(seed) % 1.0) * range;
                    if ("integer".equals(type)) {
                        return (int) Math.round(randomValue);
                    } else {
                        return randomValue;
                    }
                }
            }
            if (baseValue != null) {
                if ("integer".equals(type)) {
                    return baseValue.intValue();
                } else {
                    return baseValue;
                }
            }
            // Фолбэк при невозможности преобразования
            return "integer".equals(type)
                    ? Integer.valueOf(new java.util.Random().nextInt(10000))
                    : Double.valueOf(new java.util.Random().nextDouble() * 10000);
        }
        
        // Для boolean
        if ("boolean".equals(type)) {
            return smartFieldGenerator.generateByFieldName(fieldName, description, type, format);
        }
        
        // По умолчанию
        return smartFieldGenerator.generateByFieldName(fieldName, description, type, format);
    }
    
    /**
     * Получает int значение из additionalProperties
     */
    private int getIntValue(Response.Schema schema, String key, int defaultValue) {
        if (schema.getAdditionalProperties() != null) {
            Object value = schema.getAdditionalProperties().get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return defaultValue;
    }
    
    /**
     * Получает int значение из Map
     */
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
    
    /**
     * Получает double значение из Map
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
}

