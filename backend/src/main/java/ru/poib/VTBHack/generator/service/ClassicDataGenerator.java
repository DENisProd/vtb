package ru.poib.VTBHack.generator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.poib.VTBHack.generator.model.TestDataStep;
import ru.poib.VTBHack.mapping.model.DataFlowEdge;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.mapping.model.TaskEndpointMapping;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;
import ru.poib.VTBHack.parser.model.openapi.Parameter;
import ru.poib.VTBHack.parser.model.openapi.Response;

import java.util.*;
import java.util.Random;

/**
 * Классический генератор тестовых данных на основе правил и шаблонов
 */
@Component
public class ClassicDataGenerator {
    private final SchemaDataGenerator schemaDataGenerator;
    private final SmartFieldGenerator smartFieldGenerator;
    
    @Autowired
    public ClassicDataGenerator(SchemaDataGenerator schemaDataGenerator, 
                                SmartFieldGenerator smartFieldGenerator) {
        this.schemaDataGenerator = schemaDataGenerator;
        this.smartFieldGenerator = smartFieldGenerator;
    }
    
    /**
     * Генерирует тестовые данные для всех шагов процесса
     */
    public List<TestDataStep> generateTestData(MappingResult mappingResult, 
                                                OpenApiModel openApiModel,
                                                String scenario) {
        List<TestDataStep> steps = new ArrayList<>();
        
        if (mappingResult == null || mappingResult.getTaskMappings() == null) {
            return steps;
        }
        
        // Создаем маппинг для хранения сгенерированных данных из предыдущих шагов
        Map<String, Map<String, Object>> stepResponseData = new HashMap<>();
        
        // Генерируем данные для каждой задачи
        for (Map.Entry<String, TaskEndpointMapping> entry : mappingResult.getTaskMappings().entrySet()) {
            String taskId = entry.getKey();
            TaskEndpointMapping mapping = entry.getValue();
            
            TestDataStep step = generateStepData(taskId, mapping, openApiModel, 
                                                mappingResult.getDataFlowEdges(), 
                                                stepResponseData, scenario);
            steps.add(step);
            
            // Сохраняем response данные для использования в следующих шагах
            if (step.getResponseData() != null) {
                stepResponseData.put(taskId, step.getResponseData());
            }
        }
        
        return steps;
    }
    
    /**
     * Генерирует данные для одного шага
     */
    private TestDataStep generateStepData(String taskId, 
                                         TaskEndpointMapping mapping,
                                         OpenApiModel openApiModel,
                                         List<DataFlowEdge> dataFlowEdges,
                                         Map<String, Map<String, Object>> previousStepData,
                                         String scenario) {
        TestDataStep step = new TestDataStep();
        step.setTaskId(taskId);
        step.setTaskName(mapping.getTaskName());
        
        // Находим Operation для этого эндпоинта
        Operation operation = findOperation(openApiModel, mapping.getEndpointPath(), mapping.getEndpointMethod());
        
        // Генерируем request и query данные (даже если operation null)
        generateRequestData(step, operation, openApiModel, dataFlowEdges,
                taskId, previousStepData, scenario,
                mapping.getEndpointPath(),
                mapping.getEndpointMethod(),
                mapping.getCustomRequestData());
        
        // responseData не генерируется здесь - этим занимается другой модуль
        step.setResponseData(new HashMap<>());
        
        // Определяем зависимости данных
        Map<String, String> dependencies = extractDependencies(dataFlowEdges, taskId);
        step.setDataDependencies(dependencies);
        
        return step;
    }
    
    /**
     * Генерирует request данные на основе Operation
     */
    private void generateRequestData(TestDataStep step,
                                     Operation operation,
                                     OpenApiModel openApiModel,
                                     List<DataFlowEdge> dataFlowEdges,
                                     String taskId,
                                     Map<String, Map<String, Object>> previousStepData,
                                     String scenario,
                                     String endpointPath,
                                     String endpointMethod,
                                     Map<String, Object> requestOverrides) {
        Map<String, Object> requestData = new HashMap<>();
        Map<String, Object> queryParams = new HashMap<>();
        
        // Извлекаем path parameters из endpointPath (например, {account_id} -> account_id)
        extractPathParameters(endpointPath, requestData, taskId, dataFlowEdges, previousStepData);
        
        // Обрабатываем parameters из Operation
        Set<String> queryNames = new HashSet<>();
        Set<String> pathNames = new HashSet<>();
        if (operation != null && operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                String paramName = param.getName();
                String paramIn = param.getIn();
                if ("query".equalsIgnoreCase(paramIn)) {
                    queryNames.add(paramName);
                } else if ("path".equalsIgnoreCase(paramIn)) {
                    pathNames.add(paramName);
                }
                
                // Пропускаем, если уже добавили из path
                if (requestData.containsKey(paramName)) {
                    continue;
                }
                
                if (param.isRequired() || new Random().nextBoolean()) {
                    // Проверяем, есть ли зависимость от предыдущих шагов
                    Object value = resolveDependency(paramName, taskId, dataFlowEdges, previousStepData);
                    
                    if (value == null) {
                        // Генерируем новое значение
                        if (param.getSchema() != null) {
                            // Используем Schema из Parameter
                            Response.Schema schema = convertParameterSchema(param.getSchema());
                            value = schemaDataGenerator.generateFromSchema(schema, paramName, param.getDescription());
                        } else if (param.getExample() != null) {
                            value = param.getExample();
                        } else {
                            value = smartFieldGenerator.generateByFieldName(paramName, param.getDescription(), 
                                                                          null, null);
                        }
                    }
                    
                    if ("query".equalsIgnoreCase(paramIn)) {
                        queryParams.put(paramName, value);
                    } else {
                        requestData.put(paramName, value);
                    }
                }
            }
        }
        
        // Генерируем requestBody только если он определён в OpenAPI (operation.requestBody присутствует)
        if (("POST".equalsIgnoreCase(endpointMethod) || "PUT".equalsIgnoreCase(endpointMethod))
                && operation != null && operation.getRequestBody() != null) {
            generateRequestBody(operation, requestData, taskId, dataFlowEdges, previousStepData);
        }
        
        // Если данных все еще нет, генерируем базовые данные на основе метода и пути
        if (requestData.isEmpty() && queryParams.isEmpty()) {
            generateBasicRequestData(endpointPath, endpointMethod, requestData);
        }

        // Применяем пользовательские переопределения: если пользователь указал значения,
        // они должны сохраняться, а недостающие поля — генерироваться автоматически.
        if (requestOverrides != null && !requestOverrides.isEmpty()) {
            // Переопределяем / добавляем значения из requestOverrides с учетом размещения
            for (Map.Entry<String, Object> e : requestOverrides.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                // Определяем размещение параметра: query или body
                Placement placement = determinePlacementForOverride(key, operation, openApiModel, endpointPath, endpointMethod, queryNames);
                if (placement == Placement.QUERY) {
                    queryParams.put(key, val);
                } else if (placement == Placement.PATH) {
                    // path-параметры уже должны быть извлечены из endpointPath, но на случай явного override
                    requestData.put(key, val);
                } else {
                    requestData.put(key, val);
                }
            }
        }

        // Эвристика для GET: если остались overrides в body, перенести их в query
        if ("GET".equalsIgnoreCase(endpointMethod)) {
            if (requestOverrides != null) {
                for (String key : requestOverrides.keySet()) {
                    if (!pathNames.contains(key) && !queryParams.containsKey(key) && requestData.containsKey(key)) {
                        Object val = requestData.remove(key);
                        queryParams.put(key, val);
                    }
                }
            }
        }
        step.setRequestData(requestData);
        step.setQueryParams(queryParams);
    }
    
    /**
     * Извлекает path parameters из пути (например, /accounts/{account_id} -> account_id)
     */
    private void extractPathParameters(String path, Map<String, Object> requestData,
                                      String taskId, List<DataFlowEdge> dataFlowEdges,
                                      Map<String, Map<String, Object>> previousStepData) {
        if (path == null) {
            return;
        }
        
        // Ищем паттерны {paramName}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(path);
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            
            // Проверяем, есть ли зависимость от предыдущих шагов
            Object value = resolveDependency(paramName, taskId, dataFlowEdges, previousStepData);
            
            if (value == null) {
                // Генерируем значение на основе имени параметра
                // Убираем подчеркивания и преобразуем в camelCase для умной генерации
                String fieldName = paramName.replace("_", "");
                value = smartFieldGenerator.generateByFieldName(fieldName, null, "string", null);
            }
            
            requestData.put(paramName, value);
        }
    }
    
    /**
     * Генерирует requestBody для POST/PUT запросов
     */
    private void generateRequestBody(Operation operation, Map<String, Object> requestData,
                                     String taskId, List<DataFlowEdge> dataFlowEdges,
                                     Map<String, Map<String, Object>> previousStepData) {
        // Генерация requestBody на основе схемы из OpenAPI, если доступна
        if (operation == null || operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }
        Map<String, Object> content = operation.getRequestBody().getContent();
        if (content.isEmpty()) {
            return;
        }
        Object media = content.get("application/json");
        if (media == null) {
            // Берем первый доступный медиа-тип
            media = content.values().iterator().next();
        }
        if (!(media instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mt = (Map<String, Object>) media;
        Object rawSchema = mt.get("schema");
        Response.Schema schema = convertRawRequestSchema(rawSchema);
        if (schema == null) {
            // Фолбэк: если нет явной схемы— эвристики по summary
            String summary = operation.getSummary();
            if (summary != null && summary.toLowerCase().contains("payment")) {
                requestData.put("amount", smartFieldGenerator.generateByFieldName("amount", null, "number", null));
                requestData.put("currency", "RUB");
                requestData.put("description", "Test payment");
            } else if (summary != null && summary.toLowerCase().contains("auth")) {
                requestData.put("username", smartFieldGenerator.generateByFieldName("username", null, "string", null));
                requestData.put("password", smartFieldGenerator.generateByFieldName("password", null, "string", null));
            }
            return;
        }
        Object generated = schemaDataGenerator.generateFromSchema(schema, null, null);
        if (generated instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) generated;
            requestData.putAll(body);
        } else if (generated != null) {
            // Если схема не объект — кладем под ключ data
            requestData.put("data", generated);
        }
    }
    
    /**
     * Генерирует базовые request данные на основе пути и метода
     */
    private void generateBasicRequestData(String endpointPath, String endpointMethod, 
                                         Map<String, Object> requestData) {
        if (endpointPath == null) {
            return;
        }
        
        // Генерируем данные на основе пути
        String pathLower = endpointPath.toLowerCase();
        
        if (pathLower.contains("payment")) {
            requestData.put("amount", smartFieldGenerator.generateByFieldName("amount", null, "number", null));
            requestData.put("currency", "RUB");
        } else if (pathLower.contains("account")) {
            // Для GET запросов обычно не нужен body, но можем добавить query параметры
            if ("GET".equalsIgnoreCase(endpointMethod)) {
                requestData.put("limit", 10);
                requestData.put("offset", 0);
            }
        }
    }
    
    /**
     * Разрешает зависимость от предыдущих шагов
     */
    private Object resolveDependency(String fieldName, 
                                     String taskId,
                                     List<DataFlowEdge> dataFlowEdges,
                                     Map<String, Map<String, Object>> previousStepData) {
        if (dataFlowEdges == null || previousStepData == null) {
            return null;
        }
        
        // Ищем edge, где targetTaskId = taskId и fieldName в списке полей
        for (DataFlowEdge edge : dataFlowEdges) {
            if (edge.getTargetTaskId().equals(taskId) && 
                edge.getFields() != null && 
                edge.getFields().contains(fieldName)) {
                
                // Получаем данные из source шага
                Map<String, Object> sourceData = previousStepData.get(edge.getSourceTaskId());
                if (sourceData != null) {
                    return sourceData.get(fieldName);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Извлекает зависимости для шага
     */
    private Map<String, String> extractDependencies(List<DataFlowEdge> dataFlowEdges, String taskId) {
        Map<String, String> dependencies = new HashMap<>();
        
        if (dataFlowEdges != null) {
            for (DataFlowEdge edge : dataFlowEdges) {
                if (edge.getSourceTaskId().equals(taskId) && edge.getFields() != null) {
                    for (String field : edge.getFields()) {
                        dependencies.put(field, edge.getTargetTaskId());
                    }
                }
            }
        }
        
        return dependencies;
    }
    
    /**
     * Находит Operation по path и method
     * Поддерживает поиск по точному совпадению и по шаблону (например, /accounts/{id} соответствует /accounts/123)
     */
    private Operation findOperation(OpenApiModel openApiModel, String path, String method) {
        if (openApiModel == null || openApiModel.getPaths() == null) {
            return null;
        }
        
        // Сначала пробуем точное совпадение
        OpenApiModel.PathItem pathItem = openApiModel.getPaths().get(path);
        
        // Если не найдено, пробуем найти по шаблону
        if (pathItem == null) {
            pathItem = findPathItemByTemplate(openApiModel.getPaths(), path);
        }
        
        if (pathItem == null) {
            return null;
        }
        
        switch (method.toUpperCase()) {
            case "GET":
                return pathItem.getGet();
            case "POST":
                return pathItem.getPost();
            case "PUT":
                return pathItem.getPut();
            case "DELETE":
                return pathItem.getDelete();
            default:
                return null;
        }
    }
    
    /**
     * Находит PathItem по шаблону пути
     * Например, /accounts/123 соответствует /accounts/{account_id}
     */
    private OpenApiModel.PathItem findPathItemByTemplate(Map<String, OpenApiModel.PathItem> paths, String targetPath) {
        if (paths == null || targetPath == null) {
            return null;
        }
        
        // Нормализуем путь (убираем начальный/конечный слэш)
        String normalizedTarget = targetPath.trim();
        if (!normalizedTarget.startsWith("/")) {
            normalizedTarget = "/" + normalizedTarget;
        }
        
        // Пробуем найти совпадение по шаблону
        for (Map.Entry<String, OpenApiModel.PathItem> entry : paths.entrySet()) {
            String templatePath = entry.getKey();
            
            // Преобразуем шаблон в regex (например, /accounts/{id} -> /accounts/[^/]+)
            String regex = templatePath.replaceAll("\\{[^}]+\\}", "[^/]+");
            
            if (normalizedTarget.matches(regex)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Конвертирует Parameter.Schema в Response.Schema
     */
    private Response.Schema convertParameterSchema(Parameter.Schema paramSchema) {
        Response.Schema schema = new Response.Schema();
        schema.setType(paramSchema.getType());
        schema.setDescription(paramSchema.getDescription());
        return schema;
    }

    // Конвертация сырой схемы из requestBody.content[*].schema в Response.Schema
    private Response.Schema convertRawRequestSchema(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        Response.Schema schema = new Response.Schema();
        Object type = map.get("type");
        if (type instanceof String) schema.setType((String) type);
        Object format = map.get("format");
        if (format instanceof String) schema.setFormat((String) format);
        Object description = map.get("description");
        if (description instanceof String) schema.setDescription((String) description);

        // required
        Object required = map.get("required");
        if (required instanceof java.util.List<?>) {
            schema.getAdditionalProperties().put("required", required);
        }

        // properties
        Object props = map.get("properties");
        if (props instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propsMap = (Map<String, Object>) props;
            Map<String, Response.Schema> properties = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : propsMap.entrySet()) {
                Response.Schema child = convertRawRequestSchema(e.getValue());
                if (child == null) child = new Response.Schema();
                properties.put(e.getKey(), child);
            }
            schema.setProperties(properties);
        }

        // array items
        if ("array".equals(schema.getType())) {
            Object items = map.get("items");
            if (items instanceof Map<?, ?>) {
                schema.getAdditionalProperties().put("items", items);
            }
        }
        return schema;
    }

    // Размещение параметров при применении overrides
    private enum Placement { QUERY, PATH, BODY }

    private Placement determinePlacementForOverride(String key,
                                                    Operation operation,
                                                    OpenApiModel openApiModel,
                                                    String endpointPath,
                                                    String endpointMethod,
                                                    Set<String> knownQueryNames) {
        // Если заранее известен как query из текущей операции
        if (knownQueryNames != null && knownQueryNames.contains(key)) {
            return Placement.QUERY;
        }

        // Проверяем текущую операцию напрямую, если доступна
        if (operation != null && operation.getParameters() != null) {
            for (Parameter p : operation.getParameters()) {
                if (key.equals(p.getName())) {
                    if ("query".equalsIgnoreCase(p.getIn())) return Placement.QUERY;
                    if ("path".equalsIgnoreCase(p.getIn())) return Placement.PATH;
                }
            }
        }

        // Пробуем найти PathItem по шаблону и проверить параметры конкретного метода
        OpenApiModel.PathItem pathItem = (openApiModel != null)
                ? openApiModel.getPaths() != null ? openApiModel.getPaths().get(endpointPath) : null
                : null;
        if (pathItem == null && openApiModel != null) {
            pathItem = findPathItemByTemplate(openApiModel.getPaths(), endpointPath);
        }
        if (pathItem != null) {
            Operation opByMethod = null;
            if (endpointMethod != null) {
                switch (endpointMethod.toUpperCase()) {
                    case "GET": opByMethod = pathItem.getGet(); break;
                    case "POST": opByMethod = pathItem.getPost(); break;
                    case "PUT": opByMethod = pathItem.getPut(); break;
                    case "DELETE": opByMethod = pathItem.getDelete(); break;
                }
            }
            if (opByMethod != null && opByMethod.getParameters() != null) {
                for (Parameter p : opByMethod.getParameters()) {
                    if (key.equals(p.getName())) {
                        if ("query".equalsIgnoreCase(p.getIn())) return Placement.QUERY;
                        if ("path".equalsIgnoreCase(p.getIn())) return Placement.PATH;
                    }
                }
            }
            // Если не нашли в текущем методе, проверим остальные операции этого пути
            List<Operation> ops = new ArrayList<>();
            if (pathItem.getGet() != null) ops.add(pathItem.getGet());
            if (pathItem.getPost() != null) ops.add(pathItem.getPost());
            if (pathItem.getPut() != null) ops.add(pathItem.getPut());
            if (pathItem.getDelete() != null) ops.add(pathItem.getDelete());
            for (Operation op : ops) {
                if (op.getParameters() == null) continue;
                for (Parameter p : op.getParameters()) {
                    if (key.equals(p.getName())) {
                        if ("query".equalsIgnoreCase(p.getIn())) return Placement.QUERY;
                        if ("path".equalsIgnoreCase(p.getIn())) return Placement.PATH;
                    }
                }
            }
        }

        // Глобальная эвристика: если параметр встречается как query где-либо в спецификации — считаем query
        if (openApiModel != null && openApiModel.getPaths() != null) {
            for (Map.Entry<String, OpenApiModel.PathItem> e : openApiModel.getPaths().entrySet()) {
                OpenApiModel.PathItem item = e.getValue();
                List<Operation> ops = new ArrayList<>();
                if (item.getGet() != null) ops.add(item.getGet());
                if (item.getPost() != null) ops.add(item.getPost());
                if (item.getPut() != null) ops.add(item.getPut());
                if (item.getDelete() != null) ops.add(item.getDelete());
                for (Operation op : ops) {
                    if (op.getParameters() == null) continue;
                    for (Parameter p : op.getParameters()) {
                        if (key.equals(p.getName()) && "query".equalsIgnoreCase(p.getIn())) {
                            return Placement.QUERY;
                        }
                    }
                }
            }
        }

        // По умолчанию — BODY
        return Placement.BODY;
    }
}

