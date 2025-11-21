package ru.poib.VTBHack.mapping.service;

import org.springframework.stereotype.Service;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;
import ru.poib.VTBHack.parser.model.openapi.Parameter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализирует описания полей OpenAPI и извлекает зависимости вида
 * «подставить ответ из METHOD /path», чтобы можно было построить поток данных.
 * Улучшенная версия, которая анализирует зависимости на уровне параметров.
 */
@Service
public class OpenApiDependencyAnalyzer {

    // Ищем явные упоминания HTTP-метода и пути (учитываем скобки и другие символы)
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile("(?i)(GET|POST|PUT|DELETE|PATCH)\\s+(/[-\\w{}./]+)");
    // Подсказки, что это именно зависимость (а не просто упоминание)
    private static final Pattern DEP_HINT_PATTERN = Pattern.compile("(?iu)(получите|получить|подставить|возьмите|используйте|из ответа|response of|через|via|through|get from)");
    // Эвристика извлечения имени поля из ответа рядом с упоминанием
    private static final Pattern FIELD_HINT_PATTERN = Pattern.compile("(?iu)(поле|field|значение|token|id|identifier|consent[_-]?id|data)[:\n\r\s]*([A-Za-z0-9_.-]+)");
    // Паттерн для поиска упоминаний эндпоинтов в описаниях (более гибкий, учитывает скобки)
    // Пример: "(получите через POST /account-consents/request)"
    private static final Pattern ENDPOINT_IN_TEXT_PATTERN = Pattern.compile("(?i)(?:через|via|through|get from|получите через|получить через)\\s+(GET|POST|PUT|DELETE|PATCH)\\s+(/[-\\w{}./]+)");

    /**
     * Возвращает зависимости: для каждой операции список эндпоинтов, из ответа которых нужно подставить данные
     * Ключ: method:path текущей операции. Значение: список зависимых эндпоинтов с информацией о параметре и поле.
     */
    public Map<String, List<OpenApiDependency>> analyze(OpenApiModel openApiModel) {
        Map<String, List<OpenApiDependency>> result = new HashMap<>();

        if (openApiModel == null || openApiModel.getPaths() == null) {
            return result;
        }

        openApiModel.getPaths().forEach((path, pathItem) -> {
            if (pathItem == null) return;
            analyzeOperation(result, "GET", path, pathItem.getGet());
            analyzeOperation(result, "POST", path, pathItem.getPost());
            analyzeOperation(result, "PUT", path, pathItem.getPut());
            analyzeOperation(result, "DELETE", path, pathItem.getDelete());
        });

        return result;
    }

    private void analyzeOperation(Map<String, List<OpenApiDependency>> acc,
                                  String method,
                                  String path,
                                  Operation op) {
        if (op == null) return;
        String key = method + ":" + path;
        List<OpenApiDependency> deps = new ArrayList<>();

        // Анализируем зависимости на уровне операции (description, summary)
        if (op.getDescription() != null) {
            deps.addAll(extractDependenciesFromText(op.getDescription(), null, null));
        }
        if (op.getSummary() != null) {
            deps.addAll(extractDependenciesFromText(op.getSummary(), null, null));
        }

        // Анализируем зависимости на уровне параметров (более точно)
        if (op.getParameters() != null) {
            for (Parameter param : op.getParameters()) {
                String paramName = param.getName();
                String paramIn = param.getIn(); // header, query, path
                
                // Анализируем описание параметра
                if (param.getDescription() != null) {
                    List<OpenApiDependency> paramDeps = extractDependenciesFromText(
                        param.getDescription(), paramName, paramIn);
                    deps.addAll(paramDeps);
                }
                
                // Анализируем описание схемы параметра
                if (param.getSchema() != null && param.getSchema().getDescription() != null) {
                    List<OpenApiDependency> schemaDeps = extractDependenciesFromText(
                        param.getSchema().getDescription(), paramName, paramIn);
                    deps.addAll(schemaDeps);
                }
            }
        }

        if (!deps.isEmpty()) {
            acc.computeIfAbsent(key, k -> new ArrayList<>()).addAll(deps);
        }
    }

    /**
     * Извлекает зависимости из текста описания.
     * @param text Текст для анализа
     * @param parameterName Имя параметра, если анализ идет на уровне параметра (null для уровня операции)
     * @param parameterIn Тип параметра (header, query, path) или null
     * @return Список найденных зависимостей
     */
    private List<OpenApiDependency> extractDependenciesFromText(String text, 
                                                                 String parameterName, 
                                                                 String parameterIn) {
        List<OpenApiDependency> deps = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return deps;
        }

        // Проверяем наличие подсказок о зависимости
        Matcher hintMatcher = DEP_HINT_PATTERN.matcher(text);
        boolean hasDependencyHint = hintMatcher.find();

        // Ищем упоминания эндпоинтов
        Matcher endpointMatcher = ENDPOINT_PATTERN.matcher(text);
        while (endpointMatcher.find()) {
            String depMethod = endpointMatcher.group(1).toUpperCase(Locale.ROOT);
            String depPath = endpointMatcher.group(2);
            
            // Пытаемся извлечь подсказку о поле из ответа
            String fieldHint = extractFieldHint(text, parameterName);
            
            // Определяем уверенность на основе наличия подсказок
            double confidence = 0.5; // базовая уверенность
            if (hasDependencyHint) {
                confidence = 0.9; // высокая уверенность при наличии явных подсказок
            } else if (parameterName != null) {
                // Если зависимость найдена в описании конкретного параметра, это более надежно
                confidence = 0.8;
            }
            
            // Если паттерн "через POST /path" найден явно, это очень надежно
            Matcher explicitMatcher = ENDPOINT_IN_TEXT_PATTERN.matcher(text);
            if (explicitMatcher.find()) {
                confidence = 0.95;
            }

            deps.add(new OpenApiDependency(
                depMethod, 
                depPath, 
                parameterName,  // параметр, который требует значение
                parameterIn,    // тип параметра (header, query, path)
                fieldHint,      // подсказка о поле из ответа
                confidence
            ));
        }

        return deps;
    }

    /**
     * Извлекает подсказку о поле из ответа из текста.
     * Пытается найти упоминание конкретного поля, которое нужно извлечь из ответа.
     */
    private String extractFieldHint(String text, String parameterName) {
        // Если имя параметра содержит подсказку (например, x-consent-id -> consent-id)
        if (parameterName != null) {
            // Убираем префиксы типа x-, X-
            String normalized = parameterName.replaceFirst("^[xX]-", "");
            // Если это ID-подобное поле, возвращаем его
            if (normalized.toLowerCase().contains("id") || 
                normalized.toLowerCase().contains("token") ||
                normalized.toLowerCase().contains("consent")) {
                return normalized;
            }
        }

        // Ищем явные упоминания полей в тексте
        Matcher fieldMatcher = FIELD_HINT_PATTERN.matcher(text);
        if (fieldMatcher.find()) {
            return fieldMatcher.group(2);
        }

        // По умолчанию возвращаем null - будет использоваться логика по умолчанию
        return null;
    }

    /**
     * Модель зависимости текущего эндпоинта от другого эндпоинта (для подстановки ответа)
     */
    public static class OpenApiDependency {
        public final String method;           // HTTP метод зависимого эндпоинта
        public final String path;             // Путь зависимого эндпоинта
        public final String parameterName;    // Имя параметра, который требует значение (null если на уровне операции)
        public final String parameterIn;      // Тип параметра: header, query, path (null если на уровне операции)
        public final String fieldHint;        // Подсказка о поле из ответа (например, consent-id, token)
        public final double confidence;       // Уверенность в зависимости

        public OpenApiDependency(String method, String path, String parameterName, 
                                String parameterIn, String fieldHint, double confidence) {
            this.method = method;
            this.path = path;
            this.parameterName = parameterName;
            this.parameterIn = parameterIn;
            this.fieldHint = fieldHint;
            this.confidence = confidence;
        }

        // Обратная совместимость со старым конструктором
        public OpenApiDependency(String method, String path, String fieldHint, double confidence) {
            this(method, path, null, null, fieldHint, confidence);
        }
    }
}