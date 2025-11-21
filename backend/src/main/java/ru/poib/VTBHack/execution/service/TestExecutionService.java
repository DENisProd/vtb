package ru.poib.VTBHack.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.execution.model.*;
import ru.poib.VTBHack.generator.model.TestDataStep;
import ru.poib.VTBHack.mapping.model.DataFlowEdge;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.mapping.model.TaskEndpointMapping;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;
import ru.poib.VTBHack.parser.model.openapi.Parameter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Основной сервис для выполнения тестов
 */
@Slf4j
@Service
public class TestExecutionService {
    
    private final HttpRequestExecutor httpRequestExecutor;
    private final ResponseValidator responseValidator;
    private final DataExtractor dataExtractor;
    private final BpmnExecutionEngine bpmnExecutionEngine;
    private final ObjectMapper objectMapper;
    
    public TestExecutionService(
            HttpRequestExecutor httpRequestExecutor,
            ResponseValidator responseValidator,
            DataExtractor dataExtractor,
            BpmnExecutionEngine bpmnExecutionEngine) {
        this.httpRequestExecutor = httpRequestExecutor;
        this.responseValidator = responseValidator;
        this.dataExtractor = dataExtractor;
        this.bpmnExecutionEngine = bpmnExecutionEngine;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Выполняет тест согласно запросу
     * 
     * @param request запрос на выполнение теста
     * @return результат выполнения
     */
    public TestExecutionResult executeTest(TestExecutionRequest request) {
        Instant startTime = Instant.now();
        TestExecutionResult result = new TestExecutionResult();
        result.setStartTime(startTime);
        result.setSteps(new ArrayList<>());
        result.setProblems(new ArrayList<>());
        
        ProcessModel processModel = request.getProcessModel();
        MappingResult mappingResult = request.getMappingResult();
        ExecutionConfig config = request.getConfig();
        OpenApiModel openApiModel = request.getOpenApiModel();
        
        result.setProcessId(processModel.getId());
        result.setProcessName(processModel.getName());
        
        try {
            // Определяем порядок выполнения шагов
            List<String> executionOrder = bpmnExecutionEngine.determineExecutionOrder(processModel, mappingResult);
            
            // Получаем тестовые данные для выбранного варианта
            List<TestDataStep> testDataSteps = request.getTestData().getVariants().get(request.getTestDataVariantIndex());
            Map<String, TestDataStep> testDataMap = testDataSteps.stream()
                    .collect(Collectors.toMap(TestDataStep::getTaskId, step -> step));
            
            // Безопасно получаем маппинги задач (могут отсутствовать при простом запуске)
            Map<String, TaskEndpointMapping> safeTaskMappings =
                    (mappingResult != null && mappingResult.getTaskMappings() != null)
                            ? mappingResult.getTaskMappings()
                            : Collections.emptyMap();

            // Сопоставим ID задачи с именем для фолбэка поиска
            Map<String, String> taskIdToName = processModel.getTasks().stream()
                    .collect(Collectors.toMap(t -> t.getId(), t -> t.getName()));
            Map<String, ru.poib.VTBHack.parser.model.ProcessTask> taskIdToTask = processModel.getTasks().stream()
                    .collect(Collectors.toMap(ru.poib.VTBHack.parser.model.ProcessTask::getId, t -> t));

            // Контекст для хранения извлеченных данных между шагами
            Map<String, Object> executionContext = new HashMap<>();
            
            // Выполняем каждый шаг последовательно
            for (String taskId : executionOrder) {
                // Проверяем, не превышено ли максимальное время выполнения
                if (Instant.now().toEpochMilli() - startTime.toEpochMilli() > config.getMaxExecutionTimeMs()) {
                    ExecutionProblem problem = createProblem(
                            ExecutionProblem.ProblemType.TIMEOUT,
                            taskId,
                            "Process execution timeout",
                            "Maximum execution time exceeded",
                            null,
                            null,
                            null
                    );
                    result.getProblems().add(problem);
                    
                    if (request.isStopOnFirstError()) {
                        break;
                    }
                    continue;
                }
                
                // Проверяем, должна ли задача быть выполнена
                if (!bpmnExecutionEngine.shouldExecuteTask(taskId, executionContext)) {
                    log.debug("Skipping task {} based on gateway conditions", taskId);
                    TestExecutionStep skippedStep = createSkippedStep(taskId, "Task skipped by gateway condition");
                    result.getSteps().add(skippedStep);
                    continue;
                }
                
                // Получаем маппинг для задачи (если маппинги отсутствуют, считаем, что сопоставления нет)
                TaskEndpointMapping mapping = safeTaskMappings.get(taskId);

                // Фолбэк: попробуем найти по совпадению taskId внутри значений
                if (mapping == null) {
                    for (TaskEndpointMapping m : safeTaskMappings.values()) {
                        if (m != null && taskId.equals(m.getTaskId())) {
                            mapping = m;
                            log.debug("Found mapping by inner taskId for {} -> {} {}", taskId, m.getEndpointMethod(), m.getEndpointPath());
                            break;
                        }
                    }
                }

                // Фолбэк: попробуем найти по имени задачи
                if (mapping == null) {
                    String taskName = taskIdToName.get(taskId);
                    if (taskName != null) {
                        for (TaskEndpointMapping m : safeTaskMappings.values()) {
                            if (m != null && taskName.equalsIgnoreCase(m.getTaskName())) {
                                mapping = m;
                                log.debug("Found mapping by taskName for {} ({}) -> {} {}", taskId, taskName, m.getEndpointMethod(), m.getEndpointPath());
                                break;
                            }
                        }
                    }
                }
                // Фолбэк: синтезируем маппинг из BPMN, если указан METHOD/PATH в имени задачи
                if (mapping == null) {
                    ru.poib.VTBHack.parser.model.ProcessTask pTask = taskIdToTask.get(taskId);
                    if (pTask != null && pTask.getApiEndpointInfo() != null &&
                            pTask.getApiEndpointInfo().getMethod() != null && pTask.getApiEndpointInfo().getPath() != null) {
                        TaskEndpointMapping synthetic = new TaskEndpointMapping();
                        synthetic.setTaskId(pTask.getId());
                        synthetic.setTaskName(pTask.getName());
                        synthetic.setEndpointMethod(pTask.getApiEndpointInfo().getMethod().toUpperCase());
                        synthetic.setEndpointPath(pTask.getApiEndpointInfo().getPath());
                        synthetic.setConfidenceScore(0.5);
                        synthetic.setMatchingStrategy("BPMN_NAME_INFERRED");
                        mapping = synthetic;
                        log.debug("Synthesized mapping for {} from BPMN: {} {}", taskId, synthetic.getEndpointMethod(), synthetic.getEndpointPath());
                    }
                }
                if (mapping == null) {
                    log.warn("No mapping found for task {}", taskId);
                    ExecutionProblem problem = createProblem(
                            ExecutionProblem.ProblemType.BUSINESS_LOGIC_ERROR,
                            taskId,
                            "No endpoint mapping",
                            "Task has no corresponding API endpoint mapping",
                            null,
                            null,
                            null
                    );
                    result.getProblems().add(problem);
                    continue;
                }
                
                // Выполняем шаг
                TestExecutionStep stepResult = executeStep(
                        taskId,
                        mapping,
                        testDataMap.get(taskId),
                        config,
                        executionContext,
                        mappingResult,
                        openApiModel
                );
                
                result.getSteps().add(stepResult);
                
                if (stepResult.getStatus() == TestExecutionStep.StepStatus.SUCCESS && stepResult.getResponse() != null) {
                    extractAndStoreData(stepResult, taskId, mappingResult, executionContext, request.getConfig());
                }
                if (stepResult.getStatus() == TestExecutionStep.StepStatus.FAILED) {
                    String message = stepResult.getErrorMessage() != null ? stepResult.getErrorMessage() : "HTTP error";
                    String details = null;
                    if (stepResult.getResponse() != null && stepResult.getResponse().getBody() != null) {
                        String body = stepResult.getResponse().getBody();
                        details = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    }
                    ExecutionProblem.ProblemType type = ExecutionProblem.ProblemType.HTTP_ERROR;
                    if (message.toLowerCase(java.util.Locale.ROOT).contains("network")) {
                        type = ExecutionProblem.ProblemType.NETWORK_ERROR;
                    }
                    ExecutionProblem problem = createProblem(
                            type,
                            taskId,
                            mapping != null ? mapping.getTaskName() : taskId,
                            message,
                            details,
                            stepResult.getRequest() != null ? stepResult.getRequest().getUrl() : null,
                            stepResult.getRequest() != null ? stepResult.getRequest().getMethod() : null
                    );
                    result.getProblems().add(problem);
                }
                
                if (stepResult.getStatus() == TestExecutionStep.StepStatus.FAILED && request.isStopOnFirstError()) {
                    log.info("Stopping execution due to error in step {}", taskId);
                    break;
                }
            }
            
            // Вычисляем статистику
            result.setStatistics(calculateStatistics(result));
            
            // Определяем общий статус
            result.setStatus(determineOverallStatus(result));
            
        } catch (Exception e) {
            log.error("Error during test execution", e);
            ExecutionProblem problem = createProblem(
                    ExecutionProblem.ProblemType.UNEXPECTED_RESPONSE,
                    null,
                    "Execution error",
                    "Unexpected error during test execution: " + e.getMessage(),
                    e.toString(),
                    null,
                    null
            );
            result.getProblems().add(problem);
            result.setStatus(TestExecutionResult.ExecutionStatus.FAILED);
        } finally {
            result.setEndTime(Instant.now());
            result.setTotalDurationMs(result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli());
        }
        
        return result;
    }
    
    private TestExecutionStep executeStep(
            String taskId,
            TaskEndpointMapping mapping,
            TestDataStep testData,
            ExecutionConfig config,
            Map<String, Object> executionContext,
            MappingResult mappingResult,
            OpenApiModel openApiModel) {
        
        Instant stepStartTime = Instant.now();
        TestExecutionStep step = new TestExecutionStep();
        step.setTaskId(taskId);
        step.setTaskName(mapping.getTaskName());
        step.setStartTime(stepStartTime);
        step.setStatus(TestExecutionStep.StepStatus.FAILED);
        
        try {
            // Получаем Operation из OpenAPI для правильного разделения параметров
            Operation operation = findOperation(openApiModel, mapping.getEndpointPath(), mapping.getEndpointMethod());
            
            // Формируем URL с query параметрами и подстановкой path
            String url = buildUrl(config.getBaseUrl(), mapping.getEndpointPath(), executionContext, testData, operation);
            
            // Формируем тело запроса (до формирования заголовков, чтобы извлечь x-* поля)
            Object requestBody = buildRequestBody(testData, mapping, executionContext, operation);
            
            // Извлекаем x-* поля из requestData для заголовков
            Map<String, String> xHeaders = extractXHeaders(testData, requestBody, operation);
            
            // Формируем заголовки (с учётом данных из предыдущих шагов и зависимостей)
            Map<String, String> headers = buildHeaders(config, mapping, taskId, mappingResult, executionContext, xHeaders, operation, testData);
            
            // Выполняем HTTP запрос
            HttpRequestExecutor.ExecutionResult httpResult = httpRequestExecutor.execute(
                    mapping.getEndpointMethod(),
                    url,
                    headers,
                    requestBody,
                    config
            );
            
            Instant stepEndTime = Instant.now();
            step.setEndTime(stepEndTime);
            step.setDurationMs(stepEndTime.toEpochMilli() - stepStartTime.toEpochMilli());
            
            // Сохраняем детали запроса
            TestExecutionStep.RequestDetails requestDetails = new TestExecutionStep.RequestDetails();
            requestDetails.setUrl(url);
            requestDetails.setMethod(mapping.getEndpointMethod());
            requestDetails.setHeaders(headers);
            try {
                requestDetails.setBody(requestBody != null ? objectMapper.writeValueAsString(requestBody) : null);
            } catch (Exception e) {
                requestDetails.setBody(requestBody != null ? requestBody.toString() : null);
            }
            requestDetails.setTimestamp(stepStartTime);
            step.setRequest(requestDetails);
            
            if (!httpResult.isSuccess()) {
                step.setErrorMessage(httpResult.getErrorMessage());
                step.setStatus(TestExecutionStep.StepStatus.FAILED);
                return step;
            }
            
            // Сохраняем детали ответа
            TestExecutionStep.ResponseDetails responseDetails = new TestExecutionStep.ResponseDetails();
            responseDetails.setStatusCode(httpResult.getStatusCode());
            responseDetails.setHeaders(httpResult.getHeaders());
            responseDetails.setBody(httpResult.getBody());
            responseDetails.setResponseTimeMs(httpResult.getDurationMs());
            responseDetails.setTimestamp(stepEndTime);
            step.setResponse(responseDetails);
            
            // Валидируем ответ
            String contentType = httpResult.getHeaders() != null ? 
                    httpResult.getHeaders().get("Content-Type") : null;
            ValidationResult validation = responseValidator.validate(
                    httpResult.getStatusCode(),
                    200, // Ожидаемый статус код (можно сделать настраиваемым)
                    contentType,
                    "application/json", // Ожидаемый Content-Type (можно сделать настраиваемым)
                    httpResult.getBody(),
                    null, // JSON схема (можно извлечь из OpenAPI)
                    httpResult.getDurationMs(),
                    config.getRequestTimeoutMs()
            );
            step.setValidation(validation);
            
            // Определяем статус шага
            if (validation.isValid() && httpResult.getStatusCode() >= 200 && httpResult.getStatusCode() < 300) {
                step.setStatus(TestExecutionStep.StepStatus.SUCCESS);
            } else {
                step.setStatus(TestExecutionStep.StepStatus.FAILED);
                if (!validation.isValid()) {
                    step.setErrorMessage("Validation failed: " + String.join("; ", validation.getErrors()));
                } else {
                    step.setErrorMessage("HTTP error: status code " + httpResult.getStatusCode());
                }
            }
            
        } catch (Exception e) {
            log.error("Error executing step {}", taskId, e);
            step.setEndTime(Instant.now());
            step.setDurationMs(step.getEndTime().toEpochMilli() - stepStartTime.toEpochMilli());
            step.setErrorMessage("Error executing step: " + e.getMessage());
            step.setStatus(TestExecutionStep.StepStatus.FAILED);
        }
        
        return step;
    }
    
    /**
     * Находит Operation из OpenAPI по path и method
     */
    private Operation findOperation(OpenApiModel openApiModel, String path, String method) {
        if (openApiModel == null || openApiModel.getPaths() == null || path == null || method == null) {
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
     */
    private OpenApiModel.PathItem findPathItemByTemplate(Map<String, OpenApiModel.PathItem> paths, String targetPath) {
        if (paths == null || targetPath == null) {
            return null;
        }
        
        for (Map.Entry<String, OpenApiModel.PathItem> entry : paths.entrySet()) {
            String templatePath = entry.getKey();
            // Простая проверка: заменяем {param} на .+ и проверяем соответствие
            String regex = templatePath.replaceAll("\\{[^}]+\\}", "[^/]+");
            if (targetPath.matches(regex)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    private String buildUrl(String baseUrl, String endpointPath, Map<String, Object> context, TestDataStep testData, Operation operation) {
        String url = baseUrl;
        if (!url.endsWith("/") && !endpointPath.startsWith("/")) {
            url += "/";
        }
        url += endpointPath.startsWith("/") ? endpointPath.substring(1) : endpointPath;
        
        // Собираем все параметры из тестовых данных
        Map<String, Object> allParams = new HashMap<>();
        if (testData != null) {
            if (testData.getRequestData() != null) {
                allParams.putAll(testData.getRequestData());
            }
            if (testData.getQueryParams() != null) {
                allParams.putAll(testData.getQueryParams());
            }
        }
        
        // Подстановка переменных из контекста (например, {id} -> значение из context)
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        // Подстановка path параметров из тестовых данных
        if (testData != null && testData.getRequestData() != null) {
            for (Map.Entry<String, Object> entry : testData.getRequestData().entrySet()) {
                String key = entry.getKey();
                // Проверяем, является ли параметр path параметром согласно OpenAPI
                if (isPathParameter(operation, key) || (endpointPath != null && endpointPath.contains("{" + key + "}"))) {
                    url = url.replace("{" + key + "}", String.valueOf(entry.getValue()));
                }
            }
        }
        
        // Собираем query параметры согласно OpenAPI спецификации
        Map<String, Object> queryParams = new HashMap<>();
        if (testData != null && testData.getQueryParams() != null) {
            queryParams.putAll(testData.getQueryParams());
        }
        
        // Добавляем параметры из requestData, которые должны быть в query согласно OpenAPI
        if (testData != null && testData.getRequestData() != null && operation != null) {
            for (Map.Entry<String, Object> entry : testData.getRequestData().entrySet()) {
                String key = entry.getKey();
                // Если параметр определен как query в OpenAPI, добавляем его в query
                if (isQueryParameter(operation, key)) {
                    queryParams.put(key, entry.getValue());
                }
            }
        }
        
        // Добавляем query параметры в URL
        if (!queryParams.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean hasQuery = url.contains("?");
            sb.append(hasQuery ? "&" : "?");
            Iterator<Map.Entry<String, Object>> it = queryParams.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                String key = e.getKey();
                Object val = e.getValue();
                if (val == null) continue;
                String encodedVal;
                try {
                    encodedVal = java.net.URLEncoder.encode(String.valueOf(val), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    encodedVal = String.valueOf(val);
                }
                sb.append(key).append("=").append(encodedVal);
                if (it.hasNext()) sb.append("&");
            }
            url += sb.toString();
        }
        
        return url;
    }
    
    /**
     * Проверяет, является ли параметр query параметром согласно OpenAPI
     */
    private boolean isQueryParameter(Operation operation, String paramName) {
        if (operation == null || operation.getParameters() == null) {
            return false;
        }
        for (Parameter param : operation.getParameters()) {
            if (paramName.equals(param.getName()) && "query".equalsIgnoreCase(param.getIn())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Проверяет, является ли параметр path параметром согласно OpenAPI
     */
    private boolean isPathParameter(Operation operation, String paramName) {
        if (operation == null || operation.getParameters() == null) {
            return false;
        }
        for (Parameter param : operation.getParameters()) {
            if (paramName.equals(param.getName()) && "path".equalsIgnoreCase(param.getIn())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Проверяет, является ли параметр header параметром согласно OpenAPI
     */
    private boolean isHeaderParameter(Operation operation, String paramName) {
        if (operation == null || operation.getParameters() == null) {
            return false;
        }
        for (Parameter param : operation.getParameters()) {
            if (paramName.equals(param.getName()) && "header".equalsIgnoreCase(param.getIn())) {
                return true;
            }
        }
        return false;
    }
    
    private Map<String, String> buildHeaders(ExecutionConfig config,
                                             TaskEndpointMapping mapping,
                                             String taskId,
                                             MappingResult mappingResult,
                                             Map<String, Object> context,
                                             Map<String, String> xHeaders,
                                             Operation operation,
                                             TestDataStep testData) {
        Map<String, String> headers = new HashMap<>();
        
        // Добавляем заголовки по умолчанию
        if (config.getDefaultHeaders() != null) {
            headers.putAll(config.getDefaultHeaders());
        }
        
        // Добавляем заголовки аутентификации
        if (config.getAuthConfig() != null) {
            ExecutionConfig.AuthConfig auth = config.getAuthConfig();
            switch (auth.getType()) {
                case BASIC:
                    // В реальной реализации нужно использовать Base64 кодирование
                    if (auth.getUsername() != null && auth.getPassword() != null) {
                        String credentials = auth.getUsername() + ":" + auth.getPassword();
                        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                        headers.put("Authorization", "Basic " + encoded);
                    }
                    break;
                case BEARER:
                    if (auth.getValue() != null) {
                        headers.put("Authorization", "Bearer " + auth.getValue());
                    }
                    break;
                case API_KEY:
                    if (auth.getHeaderName() != null && auth.getValue() != null) {
                        headers.put(auth.getHeaderName(), auth.getValue());
                    }
                    break;
                case NONE:
                    // Нет аутентификации
                    break;
            }
        }
        
        // Добавляем Content-Type для POST/PUT запросов
        if (mapping != null && mapping.getEndpointMethod() != null && 
            (mapping.getEndpointMethod().equals("POST") || mapping.getEndpointMethod().equals("PUT"))) {
            headers.putIfAbsent("Content-Type", "application/json");
        }
        
        // Эвристическая подстановка x-consent-id из зависимостей данных
        try {
            if (mappingResult != null && mappingResult.getTaskMappings() != null) {
                List<DataFlowEdge> deps = bpmnExecutionEngine.getDataDependencies(taskId, mappingResult);
                for (DataFlowEdge edge : deps) {
                    String sourceTaskId = edge.getSourceTaskId();
                    TaskEndpointMapping sourceMapping = mappingResult.getTaskMappings().get(sourceTaskId);
                    if (sourceMapping == null) {
                        // Попытка найти по совпадению внутреннего taskId
                        for (TaskEndpointMapping m : mappingResult.getTaskMappings().values()) {
                            if (m != null && sourceTaskId.equals(m.getTaskId())) { sourceMapping = m; break; }
                        }
                    }
                    String sourcePath = sourceMapping != null ? sourceMapping.getEndpointPath() : null;
                    // Ищем явные согласия по пути источника
                    boolean isConsentSource = sourcePath != null && sourcePath.toLowerCase(Locale.ROOT).contains("consent");
                    if (!isConsentSource) continue;
                    
                    // Пытаемся найти значение consentId в контексте
                    Object consentIdVal = null;
                    // Прямые ключи
                    if (consentIdVal == null) consentIdVal = context.get(sourceTaskId + ".consentId");
                    if (consentIdVal == null) consentIdVal = context.get(sourceTaskId + ".id");
                    // Если сохраняли целый ответ под ключом data
                    if (consentIdVal == null) {
                        Object rawData = context.get(sourceTaskId + ".data");
                        if (rawData instanceof Map) {
                            Map<?,?> mm = (Map<?,?>) rawData;
                            Object c1 = mm.get("consentId");
                            Object c2 = mm.get("id");
                            consentIdVal = c1 != null ? c1 : c2;
                        }
                    }
                    
                    if (consentIdVal != null) {
                        headers.put("x-consent-id", String.valueOf(consentIdVal));
                        // Как только нашли подходящий источник, можно не продолжать
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Consent header injection skipped: {}", e.getMessage());
        }
        
        // Добавляем заголовки из requestData, которые должны быть в headers согласно OpenAPI
        if (testData != null && testData.getRequestData() != null && operation != null) {
            for (Map.Entry<String, Object> entry : testData.getRequestData().entrySet()) {
                String key = entry.getKey();
                // Если параметр определен как header в OpenAPI, добавляем его в заголовки
                if (isHeaderParameter(operation, key)) {
                    headers.put(key, String.valueOf(entry.getValue()));
                }
            }
        }
        
        // Добавляем x-* заголовки из requestData (если они не определены в OpenAPI как query/path/body)
        if (xHeaders != null) {
            for (Map.Entry<String, String> entry : xHeaders.entrySet()) {
                String key = entry.getKey();
                // Проверяем, что параметр не определен в OpenAPI как query или path
                if (!isQueryParameter(operation, key) && !isPathParameter(operation, key)) {
                    headers.put(key, entry.getValue());
                }
            }
        }
        
        return headers;
    }
    
    /**
     * Извлекает поля, начинающиеся с x-, из requestData для использования в заголовках
     */
    private Map<String, String> extractXHeaders(TestDataStep testData, Object requestBody, Operation operation) {
        Map<String, String> xHeaders = new HashMap<>();
        
        if (testData == null || testData.getRequestData() == null) {
            return xHeaders;
        }
        
        // Извлекаем x-* поля из requestData, которые не определены в OpenAPI как query/path
        for (Map.Entry<String, Object> entry : testData.getRequestData().entrySet()) {
            String key = entry.getKey();
            if (key != null && key.toLowerCase().startsWith("x-")) {
                // Проверяем, что параметр не определен в OpenAPI как query или path
                if (!isQueryParameter(operation, key) && !isPathParameter(operation, key)) {
                    Object value = entry.getValue();
                    if (value != null) {
                        xHeaders.put(key, String.valueOf(value));
                    }
                }
            }
        }
        
        // Также проверяем в requestBody (если это Map)
        if (requestBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = (Map<String, Object>) requestBody;
            for (Map.Entry<String, Object> entry : bodyMap.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.toLowerCase().startsWith("x-")) {
                    // Проверяем, что параметр не определен в OpenAPI как query или path
                    if (!isQueryParameter(operation, key) && !isPathParameter(operation, key)) {
                        Object value = entry.getValue();
                        if (value != null) {
                            xHeaders.put(key, String.valueOf(value));
                        }
                    }
                }
            }
        }
        
        return xHeaders;
    }
    
    private Object buildRequestBody(TestDataStep testData, TaskEndpointMapping mapping, Map<String, Object> context, Operation operation) {
        String method = mapping != null ? mapping.getEndpointMethod() : null;
        
        // Для GET/DELETE запросов body всегда должен быть null
        if (method != null && (method.equals("GET") || method.equals("DELETE"))) {
            return null;
        }
        
        // Если нет requestBody в OpenAPI, возвращаем null для методов без body
        if (operation != null && operation.getRequestBody() == null) {
            return null;
        }
        
        if (testData == null || testData.getRequestData() == null) {
            // Для POST/PUT отправляем пустой JSON, если requestBody определен в OpenAPI
            if (method != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                if (operation != null && operation.getRequestBody() != null) {
                    return new java.util.HashMap<String, Object>();
                }
            }
            return null;
        }
        
        Map<String, Object> requestData = new HashMap<>();
        
        // Сначала собираем только те параметры, которые НЕ являются query/path/header согласно OpenAPI
        if (operation != null && operation.getParameters() != null) {
            // Создаем множество имен параметров, которые являются query/path/header
            Set<String> excludedParams = new HashSet<>();
            for (Parameter param : operation.getParameters()) {
                String paramName = param.getName();
                String paramIn = param.getIn();
                if ("query".equalsIgnoreCase(paramIn) || "path".equalsIgnoreCase(paramIn) || "header".equalsIgnoreCase(paramIn)) {
                    excludedParams.add(paramName);
                }
            }
            
            // Добавляем в requestData только те параметры, которые НЕ исключены
            for (Map.Entry<String, Object> entry : testData.getRequestData().entrySet()) {
                String key = entry.getKey();
                if (!excludedParams.contains(key)) {
                    requestData.put(key, entry.getValue());
                }
            }
        } else {
            // Фолбэк: исключаем query параметры из тела
            Set<String> excludedKeys = new HashSet<>();
            if (testData.getQueryParams() != null) {
                excludedKeys.addAll(testData.getQueryParams().keySet());
            }
            // Исключаем path параметры из тела (если они присутствуют в пути)
            if (mapping != null && mapping.getEndpointPath() != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(mapping.getEndpointPath());
                while (m.find()) {
                    String pathKey = m.group(1);
                    excludedKeys.add(pathKey);
                }
            }
            
            // Добавляем в requestData только те параметры, которые НЕ исключены
            for (Map.Entry<String, Object> entry : testData.getRequestData().entrySet()) {
                String key = entry.getKey();
                if (!excludedKeys.contains(key)) {
                    requestData.put(key, entry.getValue());
                }
            }
        }
        
        // Исключаем x-* поля из тела (они должны быть в заголовках, если не определены в OpenAPI)
        requestData.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (key != null && key.toLowerCase().startsWith("x-")) {
                // Если параметр не определен в OpenAPI как body, исключаем его
                return !isBodyParameter(operation, key);
            }
            return false;
        });
        
        // Подстановка данных из контекста
        if (testData.getDataDependencies() != null) {
            for (Map.Entry<String, String> dependency : testData.getDataDependencies().entrySet()) {
                String fieldName = dependency.getKey();
                String sourceStepId = dependency.getValue();
                
                // Ищем значение в контексте (формат: stepId.fieldName)
                String contextKey = sourceStepId + "." + fieldName;
                Object value = context.get(contextKey);
                if (value != null) {
                    // Проверяем, что поле не является query/path/header параметром
                    if (operation != null) {
                        if (!isQueryParameter(operation, fieldName) && 
                            !isPathParameter(operation, fieldName) && 
                            !isHeaderParameter(operation, fieldName)) {
                            requestData.put(fieldName, value);
                        }
                    } else {
                        // Фолбэк: проверяем, что не query параметр
                        if (testData.getQueryParams() == null || !testData.getQueryParams().containsKey(fieldName)) {
                            requestData.put(fieldName, value);
                        }
                    }
                }
            }
        }
        
        // Применяем пользовательские переопределения из маппинга
        if (mapping != null && mapping.getCustomRequestData() != null) {
            for (Map.Entry<String, Object> e : mapping.getCustomRequestData().entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                // Если ключ определен в OpenAPI как query/path/header, не добавляем его в body
                if (operation != null) {
                    if (isQueryParameter(operation, key) || isPathParameter(operation, key) || isHeaderParameter(operation, key)) {
                        continue;
                    }
                } else {
                    // Фолбэк: если ключ является query параметром, не добавляем его в body
                    if (testData.getQueryParams() != null && testData.getQueryParams().containsKey(key)) {
                        continue;
                    }
                    // Если ключ является path параметром, не добавляем его в body
                    if (mapping.getEndpointPath() != null && mapping.getEndpointPath().contains("{" + key + "}")) {
                        continue;
                    }
                }
                // Если ключ начинается с x- и не определен в OpenAPI как body, не добавляем его в body
                if (key != null && key.toLowerCase().startsWith("x-")) {
                    if (!isBodyParameter(operation, key)) {
                        continue;
                    }
                }
                requestData.put(key, val);
            }
        }
        
        // Если requestBody не определен в OpenAPI, возвращаем null (даже если есть данные)
        if (operation != null && operation.getRequestBody() == null) {
            return null;
        }
        
        // Для POST/PUT/PATCH запросов, если требуется body, но requestData пустое,
        // возвращаем пустой объект или структуру с data в зависимости от требований API
        if (requestData.isEmpty() && method != null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
            // Если requestBody определен в OpenAPI, возвращаем пустой объект
            if (operation != null && operation.getRequestBody() != null) {
                // Проверяем, требуется ли структура с data (по пути эндпоинта)
                String path = mapping != null ? mapping.getEndpointPath() : null;
                if (path != null && (path.contains("/payments") || path.contains("/account-consents") || 
                    path.contains("/product-agreements"))) {
                    // Для этих эндпоинтов требуется структура с data
                    Map<String, Object> dataWrapper = new HashMap<>();
                    dataWrapper.put("data", new HashMap<>());
                    return dataWrapper;
                }
                // Иначе возвращаем пустой объект
                return new HashMap<>();
            }
            return null;
        }
        
        if (requestData.isEmpty()) {
            // Если requestBody определен в OpenAPI, возвращаем пустой объект
            if (operation != null && operation.getRequestBody() != null && method != null && 
                ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                return new HashMap<>();
            }
            return null;
        }
        
        // Если requestBody не определен в OpenAPI, но есть данные, все равно возвращаем null
        if (operation != null && operation.getRequestBody() == null) {
            return null;
        }
        
        return requestData;
    }
    
    /**
     * Проверяет, является ли параметр частью body согласно OpenAPI
     * Параметр считается body параметром, если он не определен как query/path/header
     */
    private boolean isBodyParameter(Operation operation, String paramName) {
        if (operation == null) {
            return true; // Если нет OpenAPI, считаем что может быть в body
        }
        // Если параметр определен как query/path/header, он не может быть в body
        return !isQueryParameter(operation, paramName) && 
               !isPathParameter(operation, paramName) && 
               !isHeaderParameter(operation, paramName);
    }
    
    private void extractAndStoreData(TestExecutionStep step, String taskId, MappingResult mappingResult, Map<String, Object> context, ExecutionConfig config) {
        if (step.getResponse() == null || step.getResponse().getBody() == null) {
            return;
        }
        
        // Получаем зависимости данных для этой задачи
        List<DataFlowEdge> dataDependencies = bpmnExecutionEngine.getDataDependencies(taskId, mappingResult);
        
        Map<String, String> jsonPaths = new HashMap<>();
        for (DataFlowEdge edge : dataDependencies) {
            if (edge.getFields() != null) {
                for (String field : edge.getFields()) {
                    // Если поле обозначено как 'data', сохраняем весь корень ответа
                    if ("data".equalsIgnoreCase(field)) {
                        jsonPaths.put(field, "$" );
                    } else {
                        // Используем простое JSONPath выражение (можно улучшить)
                        jsonPaths.put(field, "$." + field);
                    }
                }
            }
        }
        
        // Извлекаем данные
        Map<String, Object> extractedData = dataExtractor.extractData(step.getResponse().getBody(), jsonPaths);
        step.setExtractedData(extractedData);
        
        // Сохраняем в контекст для следующих шагов
        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
            String contextKey = taskId + "." + entry.getKey();
            context.put(contextKey, entry.getValue());
        }

        // Дополнительно: если ответ содержит access_token, сохраняем его в конфигурацию авторизации
        try {
            String rawBody = step.getResponse().getBody();
            if (rawBody == null) return;
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(rawBody);
            // Если корневой узел — строка с вложенным JSON, парсим повторно
            if (root != null && root.isTextual()) {
                String text = root.asText();
                try {
                    root = objectMapper.readTree(text);
                } catch (Exception ignored) {
                }
            }
            if (root != null) {
                com.fasterxml.jackson.databind.JsonNode tokenNode = root.get("access_token");
                if (tokenNode == null && root.has("data")) {
                    com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
                    tokenNode = dataNode != null ? dataNode.get("access_token") : null;
                }
                if (tokenNode == null && root.has("token")) {
                    tokenNode = root.get("token");
                }
                if (tokenNode != null && !tokenNode.isNull()) {
                    String tokenValue = tokenNode.asText();
                    if (tokenValue != null && !tokenValue.isBlank() && config != null) {
                        if (config.getAuthConfig() == null) {
                            config.setAuthConfig(new ExecutionConfig.AuthConfig(ExecutionConfig.AuthConfig.AuthType.BEARER, null, null, null, null));
                        }
                        // Устанавливаем тип BEARER, если он еще не установлен
                        if (config.getAuthConfig().getType() != ExecutionConfig.AuthConfig.AuthType.BEARER) {
                            config.getAuthConfig().setType(ExecutionConfig.AuthConfig.AuthType.BEARER);
                        }
                        config.getAuthConfig().setValue(tokenValue);
                        log.info("Captured access token for subsequent requests (type: {}): {}",
                                config.getAuthConfig().getType(),
                                "***");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Unable to parse response for access_token: {}", e.getMessage());
        }
    }
    
    private TestExecutionStep createSkippedStep(String taskId, String reason) {
        TestExecutionStep step = new TestExecutionStep();
        step.setTaskId(taskId);
        step.setStatus(TestExecutionStep.StepStatus.SKIPPED);
        step.setStartTime(Instant.now());
        step.setEndTime(Instant.now());
        step.setDurationMs(0);
        step.setErrorMessage(reason);
        return step;
    }
    
    private ExecutionProblem createProblem(
            ExecutionProblem.ProblemType type,
            String stepId,
            String stepName,
            String message,
            String details,
            String requestUrl,
            String requestMethod) {
        ExecutionProblem problem = new ExecutionProblem();
        problem.setType(type);
        problem.setStepId(stepId);
        problem.setStepName(stepName);
        problem.setSeverity(ExecutionProblem.Severity.ERROR);
        problem.setMessage(message);
        problem.setDetails(details);
        problem.setRequestUrl(requestUrl);
        problem.setRequestMethod(requestMethod);
        problem.setTimestamp(Instant.now());
        return problem;
    }
    
    private TestExecutionResult.ExecutionStatistics calculateStatistics(TestExecutionResult result) {
        TestExecutionResult.ExecutionStatistics stats = new TestExecutionResult.ExecutionStatistics();
        
        int totalSteps = result.getSteps().size();
        int successfulSteps = 0;
        int failedSteps = 0;
        int skippedSteps = 0;
        long totalDuration = 0;
        long minDuration = Long.MAX_VALUE;
        long maxDuration = 0;
        int successfulRequests = 0;
        int validationErrors = 0;
        
        for (TestExecutionStep step : result.getSteps()) {
            switch (step.getStatus()) {
                case SUCCESS -> successfulSteps++;
                case FAILED -> failedSteps++;
                case SKIPPED -> skippedSteps++;
            }
            
            if (step.getDurationMs() > 0) {
                totalDuration += step.getDurationMs();
                minDuration = Math.min(minDuration, step.getDurationMs());
                maxDuration = Math.max(maxDuration, step.getDurationMs());
            }
            
            if (step.getResponse() != null) {
                stats.setTotalRequests(stats.getTotalRequests() + 1);
                if (step.getResponse().getStatusCode() >= 200 && step.getResponse().getStatusCode() < 300) {
                    successfulRequests++;
                }
            }
            
            if (step.getValidation() != null && !step.getValidation().isValid()) {
                validationErrors++;
            }
        }
        
        stats.setTotalSteps(totalSteps);
        stats.setSuccessfulSteps(successfulSteps);
        stats.setFailedSteps(failedSteps);
        stats.setSkippedSteps(skippedSteps);
        stats.setAverageStepDurationMs(totalSteps > 0 ? (double) totalDuration / totalSteps : 0);
        stats.setMinStepDurationMs(minDuration == Long.MAX_VALUE ? 0 : minDuration);
        stats.setMaxStepDurationMs(maxDuration);
        stats.setSuccessfulRequests(successfulRequests);
        stats.setValidationErrors(validationErrors);
        
        return stats;
    }
    
    private TestExecutionResult.ExecutionStatus determineOverallStatus(TestExecutionResult result) {
        if (result.getSteps().isEmpty()) {
            return TestExecutionResult.ExecutionStatus.FAILED;
        }
        
        boolean hasFailed = result.getSteps().stream()
                .anyMatch(step -> step.getStatus() == TestExecutionStep.StepStatus.FAILED);
        boolean hasSuccess = result.getSteps().stream()
                .anyMatch(step -> step.getStatus() == TestExecutionStep.StepStatus.SUCCESS);
        
        if (hasFailed && hasSuccess) {
            return TestExecutionResult.ExecutionStatus.PARTIAL;
        } else if (hasFailed) {
            return TestExecutionResult.ExecutionStatus.FAILED;
        } else {
            return TestExecutionResult.ExecutionStatus.SUCCESS;
        }
    }
}



