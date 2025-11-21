package ru.poib.VTBHack.mapping.service;

import org.springframework.stereotype.Service;
import ru.poib.VTBHack.mapping.model.SecretField;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;
import ru.poib.VTBHack.parser.model.openapi.Parameter;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Анализирует секретные поля (пароли, токены, API ключи) из всех эндпоинтов OpenAPI
 */
@Service
public class SecretFieldAnalyzer {

    // Паттерны для определения секретных полей по имени
    private static final List<Pattern> SECRET_NAME_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*password.*"),
        Pattern.compile("(?i).*secret.*"),
        Pattern.compile("(?i).*token.*"),
        Pattern.compile("(?i).*api[_-]?key.*"),
        Pattern.compile("(?i).*api[_-]?token.*"),
        Pattern.compile("(?i).*auth[_-]?token.*"),
        Pattern.compile("(?i).*access[_-]?token.*"),
        Pattern.compile("(?i).*refresh[_-]?token.*"),
        Pattern.compile("(?i).*bearer.*"),
        Pattern.compile("(?i).*credential.*"),
        Pattern.compile("(?i).*authorization.*"),
        Pattern.compile("(?i).*x[_-]?api[_-]?key.*"),
        Pattern.compile("(?i).*x[_-]?auth.*"),
        Pattern.compile("(?i).*x[_-]?token.*"),
        Pattern.compile("(?i).*private[_-]?key.*"),
        Pattern.compile("(?i).*session[_-]?id.*")
    );

    // Имена заголовков, которые обычно содержат секреты
    private static final Set<String> SECRET_HEADER_NAMES = new HashSet<>(Arrays.asList(
        "authorization",
        "x-api-key",
        "x-auth-token",
        "x-access-token",
        "api-key",
        "auth-token",
        "x-authorization"
    ));

    /**
     * Анализирует все эндпоинты и находит секретные поля
     * 
     * @param openApiModel Модель OpenAPI
     * @return Список секретных полей, отсортированный по имени
     */
    public List<SecretField> analyzeSecretFields(OpenApiModel openApiModel) {
        List<SecretField> secretFields = new ArrayList<>();
        
        if (openApiModel == null || openApiModel.getPaths() == null) {
            return secretFields;
        }

        // Используем Set для избежания дубликатов (по ключу "fieldName:fieldType")
        Map<String, SecretFieldInfo> secretFieldsMap = new HashMap<>();

        openApiModel.getPaths().forEach((path, pathItem) -> {
            if (pathItem == null) return;
            
            analyzeOperationSecrets(secretFieldsMap, "GET", path, pathItem.getGet());
            analyzeOperationSecrets(secretFieldsMap, "POST", path, pathItem.getPost());
            analyzeOperationSecrets(secretFieldsMap, "PUT", path, pathItem.getPut());
            analyzeOperationSecrets(secretFieldsMap, "DELETE", path, pathItem.getDelete());
        });

        // Преобразуем в список
        for (SecretFieldInfo info : secretFieldsMap.values()) {
            SecretField secretField = new SecretField();
            secretField.setFieldName(info.fieldName);
            secretField.setFieldType(info.fieldType);
            secretField.setDescription(info.description);
            secretField.setDataType(info.dataType);
            secretField.setRequired(info.isRequired);
            secretField.setUsedInEndpoints(new ArrayList<>(info.endpoints));
            secretField.setReason(info.reason);
            
            secretFields.add(secretField);
        }

        // Сортируем по имени поля
        secretFields.sort(Comparator.comparing(SecretField::getFieldName));

        return secretFields;
    }

    /**
     * Анализирует секретные поля операции
     */
    private void analyzeOperationSecrets(Map<String, SecretFieldInfo> secretFieldsMap,
                                         String method,
                                         String path,
                                         Operation operation) {
        if (operation == null) return;

        String endpointKey = method + " " + path;

        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                String fieldName = param.getName();
                String fieldType = param.getIn() != null ? param.getIn() : "unknown";
                
                // Проверяем, является ли поле секретным
                String reason = isSecretField(fieldName, fieldType);
                if (reason != null) {
                    String key = fieldName + ":" + fieldType;
                    
                    SecretFieldInfo info = secretFieldsMap.computeIfAbsent(key, 
                        k -> new SecretFieldInfo(fieldName, fieldType, reason));
                    
                    info.endpoints.add(endpointKey);
                    
                    if (param.isRequired()) {
                        info.isRequired = true;
                    }
                    
                    // Сохраняем описание из первого найденного использования
                    if (info.description == null && param.getDescription() != null) {
                        info.description = param.getDescription();
                    }
                    
                    // Сохраняем тип данных
                    if (info.dataType == null && param.getSchema() != null && param.getSchema().getType() != null) {
                        info.dataType = param.getSchema().getType();
                    }
                }
            }
        }

        // TODO: Также можно анализировать поля из requestBody, но это требует более глубокого парсинга схем
    }

    /**
     * Проверяет, является ли поле секретным
     * @param fieldName Имя поля
     * @param fieldType Тип поля (header, query, path)
     * @return Причина, по которой поле считается секретом, или null если это не секрет
     */
    private String isSecretField(String fieldName, String fieldType) {
        if (fieldName == null) {
            return null;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        
        // Проверяем паттерны по имени
        for (Pattern pattern : SECRET_NAME_PATTERNS) {
            if (pattern.matcher(lowerFieldName).matches()) {
                return "Field name matches secret pattern: " + pattern.pattern();
            }
        }
        
        // Проверяем специальные заголовки
        if ("header".equalsIgnoreCase(fieldType)) {
            if (SECRET_HEADER_NAMES.contains(lowerFieldName)) {
                return "Known secret header name";
            }
        }
        
        return null;
    }

    /**
     * Внутренний класс для хранения информации о секретном поле
     */
    private static class SecretFieldInfo {
        String fieldName;
        String fieldType;
        String reason;
        Set<String> endpoints = new HashSet<>();
        boolean isRequired = false;
        String description;
        String dataType;

        SecretFieldInfo(String fieldName, String fieldType, String reason) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.reason = reason;
        }
    }
}




