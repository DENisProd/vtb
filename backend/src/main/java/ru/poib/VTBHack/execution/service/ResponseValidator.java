package ru.poib.VTBHack.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.execution.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для валидации HTTP ответов
 */
@Slf4j
@Service
public class ResponseValidator {
    
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;
    
    public ResponseValidator() {
        this.objectMapper = new ObjectMapper();
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }
    
    /**
     * Валидирует ответ согласно всем правилам
     * 
     * @param statusCode фактический HTTP status code
     * @param expectedStatusCode ожидаемый HTTP status code
     * @param contentType Content-Type заголовок
     * @param expectedContentType ожидаемый Content-Type
     * @param responseBody тело ответа
     * @param responseSchema JSON схема для валидации (может быть null)
     * @param responseTimeMs время ответа в миллисекундах
     * @param maxResponseTimeMs максимально допустимое время ответа
     * @return результат валидации
     */
    public ValidationResult validate(
            int statusCode,
            int expectedStatusCode,
            String contentType,
            String expectedContentType,
            String responseBody,
            String responseSchema,
            long responseTimeMs,
            long maxResponseTimeMs) {
        
        ValidationResult result = new ValidationResult();
        result.setErrors(new ArrayList<>());
        result.setWarnings(new ArrayList<>());
        
        // Валидация status code
        ValidationResult.StatusCodeValidation statusCodeValidation = validateStatusCode(statusCode, expectedStatusCode);
        result.setStatusCodeValidation(statusCodeValidation);
        if (!statusCodeValidation.isValid()) {
            result.getErrors().add(statusCodeValidation.getMessage());
        }
        
        // Валидация контрактов
        ValidationResult.ContractValidation contractValidation = validateContract(contentType, expectedContentType, responseBody);
        result.setContractValidation(contractValidation);
        if (!contractValidation.isValid()) {
            result.getErrors().add(contractValidation.getMessage());
        }
        
        // Валидация схемы JSON (если предоставлена)
        if (responseSchema != null && responseBody != null && !responseBody.trim().isEmpty()) {
            ValidationResult.SchemaValidation schemaValidation = validateSchema(responseBody, responseSchema);
            result.setSchemaValidation(schemaValidation);
            if (!schemaValidation.isValid()) {
                result.getErrors().addAll(schemaValidation.getSchemaErrors());
            }
        } else {
            // Если схема не предоставлена, создаем пустую валидацию
            ValidationResult.SchemaValidation schemaValidation = new ValidationResult.SchemaValidation();
            schemaValidation.setValid(true);
            schemaValidation.setSchemaErrors(new ArrayList<>());
            result.setSchemaValidation(schemaValidation);
        }
        
        // Валидация производительности
        ValidationResult.PerformanceValidation performanceValidation = validatePerformance(responseTimeMs, maxResponseTimeMs);
        result.setPerformanceValidation(performanceValidation);
        if (!performanceValidation.isValid()) {
            result.getWarnings().add(performanceValidation.getMessage());
        }
        
        // Общий результат валидации
        result.setValid(result.getErrors().isEmpty());
        
        return result;
    }
    
    private ValidationResult.StatusCodeValidation validateStatusCode(int actual, int expected) {
        ValidationResult.StatusCodeValidation validation = new ValidationResult.StatusCodeValidation();
        validation.setExpectedStatusCode(expected);
        validation.setActualStatusCode(actual);
        validation.setValid(actual == expected);
        
        if (!validation.isValid()) {
            validation.setMessage(String.format("Expected status code %d, but got %d", expected, actual));
        } else {
            validation.setMessage("Status code validation passed");
        }
        
        return validation;
    }
    
    private ValidationResult.ContractValidation validateContract(String actualContentType, String expectedContentType, String responseBody) {
        ValidationResult.ContractValidation validation = new ValidationResult.ContractValidation();
        validation.setExpectedContentType(expectedContentType);
        validation.setActualContentType(actualContentType);
        validation.setMissingRequiredFields(new ArrayList<>());
        
        List<String> errors = new ArrayList<>();
        
        // Проверка Content-Type
        if (expectedContentType != null && !expectedContentType.isEmpty()) {
            if (actualContentType == null || !actualContentType.contains(expectedContentType.split(";")[0].trim())) {
                errors.add(String.format("Expected Content-Type '%s', but got '%s'", expectedContentType, actualContentType));
            }
        }
        
        // Проверка обязательных полей (базовая проверка на наличие JSON)
        if (expectedContentType != null && expectedContentType.contains("json")) {
            if (responseBody == null || responseBody.trim().isEmpty()) {
                errors.add("Expected JSON response body, but got empty body");
            } else {
                try {
                    JsonNode jsonNode = objectMapper.readTree(responseBody);
                    if (!jsonNode.isObject() && !jsonNode.isArray()) {
                        errors.add("Response body is not a valid JSON object or array");
                    }
                } catch (Exception e) {
                    errors.add("Response body is not valid JSON: " + e.getMessage());
                }
            }
        }
        
        validation.setValid(errors.isEmpty());
        if (errors.isEmpty()) {
            validation.setMessage("Contract validation passed");
        } else {
            validation.setMessage(String.join("; ", errors));
        }
        
        return validation;
    }
    
    private ValidationResult.SchemaValidation validateSchema(String responseBody, String schemaJson) {
        ValidationResult.SchemaValidation validation = new ValidationResult.SchemaValidation();
        validation.setSchemaErrors(new ArrayList<>());
        
        try {
            JsonSchema schema = schemaFactory.getSchema(schemaJson);
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            
            if (errors.isEmpty()) {
                validation.setValid(true);
                validation.setMessage("Schema validation passed");
            } else {
                validation.setValid(false);
                List<String> errorMessages = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.toList());
                validation.setSchemaErrors(errorMessages);
                validation.setMessage("Schema validation failed: " + String.join("; ", errorMessages));
            }
        } catch (Exception e) {
            validation.setValid(false);
            String errorMsg = "Error validating schema: " + e.getMessage();
            validation.setSchemaErrors(List.of(errorMsg));
            validation.setMessage(errorMsg);
            log.error("Error validating JSON schema", e);
        }
        
        return validation;
    }
    
    private ValidationResult.PerformanceValidation validatePerformance(long responseTimeMs, long maxResponseTimeMs) {
        ValidationResult.PerformanceValidation validation = new ValidationResult.PerformanceValidation();
        validation.setResponseTimeMs(responseTimeMs);
        validation.setMaxAllowedTimeMs(maxResponseTimeMs);
        validation.setValid(responseTimeMs <= maxResponseTimeMs);
        
        if (!validation.isValid()) {
            validation.setMessage(String.format("Response time %d ms exceeds maximum allowed time %d ms", 
                    responseTimeMs, maxResponseTimeMs));
        } else {
            validation.setMessage(String.format("Response time %d ms is within acceptable range", responseTimeMs));
        }
        
        return validation;
    }
}

