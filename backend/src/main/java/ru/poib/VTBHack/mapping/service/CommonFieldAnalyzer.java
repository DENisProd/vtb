package ru.poib.VTBHack.mapping.service;

import org.springframework.stereotype.Service;
import ru.poib.VTBHack.mapping.model.CommonField;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;
import ru.poib.VTBHack.parser.model.openapi.Parameter;

import java.util.*;

/**
 * Анализирует общие поля между эндпоинтами OpenAPI
 */
@Service
public class CommonFieldAnalyzer {

    /**
     * Анализирует все эндпоинты и находит общие поля (параметры, заголовки, query параметры)
     * Исключает поля, которые являются ответами других эндпоинтов (зависимости)
     * 
     * @param openApiModel Модель OpenAPI
     * @param dependencies Зависимости между эндпоинтами (для исключения полей-зависимостей)
     * @param minUsageCount Минимальное количество использований для того, чтобы поле считалось общим (по умолчанию 2)
     * @return Список общих полей, отсортированный по частоте использования
     */
    public List<CommonField> analyzeCommonFields(OpenApiModel openApiModel, 
                                                  Map<String, List<OpenApiDependencyAnalyzer.OpenApiDependency>> dependencies,
                                                  int minUsageCount) {
        List<CommonField> commonFields = new ArrayList<>();
        
        if (openApiModel == null || openApiModel.getPaths() == null) {
            return commonFields;
        }

        // Собираем множество имен полей, которые являются зависимостями (исключаем их по имени, без учета типа)
        Set<String> dependencyFieldNames = new HashSet<>();
        if (dependencies != null) {
            for (List<OpenApiDependencyAnalyzer.OpenApiDependency> deps : dependencies.values()) {
                for (OpenApiDependencyAnalyzer.OpenApiDependency dep : deps) {
                    if (dep.parameterName != null) {
                        // Исключаем по имени поля, независимо от типа (path, query, header)
                        // Нормализуем имя: убираем префиксы x-, приводим к нижнему регистру
                        String normalizedName = normalizeFieldName(dep.parameterName);
                        dependencyFieldNames.add(normalizedName);
                    }
                }
            }
        }

        // Собираем информацию о всех полях из всех эндпоинтов
        // Группируем по имени поля (без учета типа), чтобы объединить path, query, header версии одного поля
        // Ключ: нормализованное имя поля, значение: информация об использовании
        Map<String, FieldUsageInfo> fieldUsageMap = new HashMap<>();

        openApiModel.getPaths().forEach((path, pathItem) -> {
            if (pathItem == null) return;
            
            analyzeOperationFields(fieldUsageMap, "GET", path, pathItem.getGet());
            analyzeOperationFields(fieldUsageMap, "POST", path, pathItem.getPost());
            analyzeOperationFields(fieldUsageMap, "PUT", path, pathItem.getPut());
            analyzeOperationFields(fieldUsageMap, "DELETE", path, pathItem.getDelete());
        });

        // Фильтруем поля, которые используются в нескольких эндпоинтах и не являются зависимостями
        for (Map.Entry<String, FieldUsageInfo> entry : fieldUsageMap.entrySet()) {
            String fieldNameKey = entry.getKey();
            FieldUsageInfo usageInfo = entry.getValue();
            
            // Пропускаем поля, которые являются зависимостями от других эндпоинтов (по имени, без учета типа)
            if (dependencyFieldNames.contains(fieldNameKey)) {
                continue;
            }
            
            if (usageInfo.usageCount >= minUsageCount) {
                CommonField commonField = new CommonField();
                commonField.setFieldName(usageInfo.fieldName);
                // Если поле используется в разных типах, формируем строку типа (например, "path,header")
                commonField.setFieldType(formatFieldTypes(usageInfo.fieldTypes));
                commonField.setUsageCount(usageInfo.usageCount);
                commonField.setUsedInEndpoints(new ArrayList<>(usageInfo.endpoints));
                commonField.setRequired(usageInfo.isRequired);
                commonField.setDescription(usageInfo.description);
                commonField.setDataType(usageInfo.dataType);
                
                commonFields.add(commonField);
            }
        }

        // Сортируем по частоте использования (по убыванию)
        commonFields.sort((a, b) -> Integer.compare(b.getUsageCount(), a.getUsageCount()));

        return commonFields;
    }

    /**
     * Перегрузка метода с минимальным количеством использований по умолчанию = 2
     */
    public List<CommonField> analyzeCommonFields(OpenApiModel openApiModel) {
        return analyzeCommonFields(openApiModel, null, 2);
    }
    
    /**
     * Перегрузка метода с зависимостями и минимальным количеством использований по умолчанию = 2
     */
    public List<CommonField> analyzeCommonFields(OpenApiModel openApiModel, 
                                                  Map<String, List<OpenApiDependencyAnalyzer.OpenApiDependency>> dependencies) {
        return analyzeCommonFields(openApiModel, dependencies, 2);
    }

    /**
     * Анализирует поля операции (параметры)
     * Группирует поля по имени (без учета типа), чтобы объединить path, query, header версии одного поля
     */
    private void analyzeOperationFields(Map<String, FieldUsageInfo> fieldUsageMap,
                                        String method,
                                        String path,
                                        Operation operation) {
        if (operation == null) return;

        String endpointKey = method + " " + path;

        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                String fieldName = param.getName();
                if (fieldName == null) continue;
                
                String fieldType = param.getIn() != null ? param.getIn() : "unknown"; // header, query, path
                // Используем нормализованное имя поля как ключ для группировки (без учета типа)
                String normalizedName = normalizeFieldName(fieldName);
                
                FieldUsageInfo usageInfo = fieldUsageMap.computeIfAbsent(normalizedName, 
                    k -> {
                        // При первом создании используем оригинальное имя (с учетом регистра)
                        return new FieldUsageInfo(fieldName, fieldType);
                    });
                
                // Обновляем информацию о типах использования
                usageInfo.addFieldType(fieldType);
                usageInfo.usageCount++;
                usageInfo.endpoints.add(endpointKey);
                
                if (param.isRequired()) {
                    usageInfo.isRequired = true;
                }
                
                // Сохраняем описание из первого найденного использования
                if (usageInfo.description == null && param.getDescription() != null) {
                    usageInfo.description = param.getDescription();
                }
                
                // Сохраняем тип данных
                if (usageInfo.dataType == null && param.getSchema() != null && param.getSchema().getType() != null) {
                    usageInfo.dataType = param.getSchema().getType();
                }
            }
        }

        // TODO: Также можно анализировать поля из requestBody, но это требует более глубокого парсинга схем
    }

    /**
     * Нормализует имя поля для сравнения:
     * - Убирает префиксы x-
     * - Приводит к нижнему регистру
     * - Заменяет дефисы и подчеркивания на единый разделитель для унификации
     *   (чтобы consent-id и consent_id считались одним полем)
     */
    private String normalizeFieldName(String fieldName) {
        if (fieldName == null) return "";
        String normalized = fieldName.toLowerCase();
        // Убираем префиксы x- или X-
        if (normalized.startsWith("x-")) {
            normalized = normalized.substring(2);
        }
        // Заменяем дефисы и подчеркивания на единый разделитель для унификации
        // Это позволяет считать consent-id и consent_id одним полем
        normalized = normalized.replace('-', '_');
        return normalized;
    }

    /**
     * Форматирует список типов поля в строку (например, "path,header" или "query")
     */
    private String formatFieldTypes(Set<String> fieldTypes) {
        if (fieldTypes == null || fieldTypes.isEmpty()) {
            return "unknown";
        }
        if (fieldTypes.size() == 1) {
            return fieldTypes.iterator().next();
        }
        // Сортируем типы для консистентности
        List<String> sortedTypes = new ArrayList<>(fieldTypes);
        sortedTypes.sort(String::compareTo);
        return String.join(",", sortedTypes);
    }

    /**
     * Внутренний класс для хранения информации об использовании поля
     */
    private static class FieldUsageInfo {
        String fieldName;
        Set<String> fieldTypes = new HashSet<>(); // Множество типов, в которых используется поле (path, query, header)
        int usageCount = 0;
        Set<String> endpoints = new HashSet<>();
        boolean isRequired = false;
        String description;
        String dataType;

        FieldUsageInfo(String fieldName, String fieldType) {
            this.fieldName = fieldName;
            this.fieldTypes.add(fieldType);
        }

        /**
         * Добавляет тип использования поля
         */
        void addFieldType(String fieldType) {
            if (fieldType != null) {
                this.fieldTypes.add(fieldType);
            }
        }
    }
}

