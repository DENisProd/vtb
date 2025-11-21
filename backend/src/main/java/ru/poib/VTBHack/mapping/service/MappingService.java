package ru.poib.VTBHack.mapping.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.mapping.model.*;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.ProcessTask;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;

import java.util.*;

/**
 * Основной сервис для автоматического сопоставления задач процесса с API эндпоинтами.
 * Фокусируется на сопоставлении всех BPMN задач с API эндпоинтами.
 */
@Slf4j
@Service
public class MappingService {
    
    private final EndpointExtractor endpointExtractor;
    private final SemanticAnalysisService semanticAnalysisService;
    private final DataFlowAnalyzer dataFlowAnalyzer;
    private final OpenApiDependencyAnalyzer openApiDependencyAnalyzer;
    private final CommonFieldAnalyzer commonFieldAnalyzer;
    private final SecretFieldAnalyzer secretFieldAnalyzer;
    private final AIVerificationService aiVerificationService;
    
    // Пороги уверенности для различных стратегий
    private static final double EXACT_MATCH_THRESHOLD = 0.95;
    private static final double SEMANTIC_MATCH_THRESHOLD = 0.4; // Снижен для увеличения вероятности сопоставления BPMN задач
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.3; // Снижен для увеличения вероятности сопоставления BPMN задач
    
    @Autowired
    public MappingService(EndpointExtractor endpointExtractor,
                          SemanticAnalysisService semanticAnalysisService,
                          DataFlowAnalyzer dataFlowAnalyzer,
                          OpenApiDependencyAnalyzer openApiDependencyAnalyzer,
                          CommonFieldAnalyzer commonFieldAnalyzer,
                          SecretFieldAnalyzer secretFieldAnalyzer,
                          AIVerificationService aiVerificationService) {
        this.endpointExtractor = endpointExtractor;
        this.semanticAnalysisService = semanticAnalysisService;
        this.dataFlowAnalyzer = dataFlowAnalyzer;
        this.openApiDependencyAnalyzer = openApiDependencyAnalyzer;
        this.commonFieldAnalyzer = commonFieldAnalyzer;
        this.secretFieldAnalyzer = secretFieldAnalyzer;
        this.aiVerificationService = aiVerificationService;
    }

    // Backward-compatible constructor for tests/manual usage
    public MappingService(EndpointExtractor endpointExtractor,
                          SemanticAnalysisService semanticAnalysisService,
                          DataFlowAnalyzer dataFlowAnalyzer) {
        this(endpointExtractor, semanticAnalysisService, dataFlowAnalyzer, 
             new OpenApiDependencyAnalyzer(), new CommonFieldAnalyzer(), new SecretFieldAnalyzer(), null);
    }
    
    // Конструктор для тестов без AIVerificationService
    public MappingService(EndpointExtractor endpointExtractor,
                          SemanticAnalysisService semanticAnalysisService,
                          DataFlowAnalyzer dataFlowAnalyzer,
                          OpenApiDependencyAnalyzer openApiDependencyAnalyzer,
                          CommonFieldAnalyzer commonFieldAnalyzer,
                          SecretFieldAnalyzer secretFieldAnalyzer) {
        this(endpointExtractor, semanticAnalysisService, dataFlowAnalyzer, 
             openApiDependencyAnalyzer, commonFieldAnalyzer, secretFieldAnalyzer, null);
    }
    
    /**
     * Выполняет сопоставление задач процесса с API эндпоинтами.
     * Главная цель - найти соответствия для всех задач BPMN.
     * Эндпоинты OpenAPI, не сопоставленные с задачами, игнорируются.
     */
    public MappingResult mapProcessToEndpoints(ProcessModel processModel, OpenApiModel openApiModel) {
        return mapProcessToEndpoints(processModel, openApiModel, null, null);
    }
    
    /**
     * Выполняет сопоставление задач процесса с API эндпоинтами с проверкой ИИ.
     * 
     * @param processModel модель процесса BPMN
     * @param openApiModel модель OpenAPI спецификации
     * @param openApiJson исходная JSON строка OpenAPI (для проверки ИИ)
     * @param bpmnXml исходная XML строка BPMN (для проверки ИИ)
     * @return результат сопоставления с отчетом ИИ
     */
    public MappingResult mapProcessToEndpoints(ProcessModel processModel, OpenApiModel openApiModel, 
                                             String openApiJson, String bpmnXml) {
        long mappingStartTime = System.currentTimeMillis();
        log.info("Starting mapping process: {} tasks, {} endpoints", 
            processModel.getTasks().size(), 
            openApiModel != null ? "processing" : "none");
        
        // Извлекаем эндпоинты из OpenAPI
        long extractStartTime = System.currentTimeMillis();
        List<EndpointInfo> endpoints = endpointExtractor.extractEndpoints(openApiModel);
        log.debug("Extracted {} endpoints in {}ms", endpoints.size(), System.currentTimeMillis() - extractStartTime);
        
        // Создаем маппинг для каждой задачи
        long matchingStartTime = System.currentTimeMillis();
        Map<String, TaskEndpointMapping> taskMappings = new HashMap<>();
        List<UnmatchedElement> unmatchedTasks = new ArrayList<>();
        Set<String> matchedEndpointIds = new HashSet<>();
        
        log.debug("Starting task matching for {} tasks...", processModel.getTasks().size());
        // Сначала пробуем найти точные совпадения
        for (ProcessTask task : processModel.getTasks()) {
            TaskEndpointMapping exactMatch = tryExactMatch(task, endpoints);
            if (exactMatch != null && exactMatch.getConfidenceScore() >= EXACT_MATCH_THRESHOLD) {
                taskMappings.put(task.getId(), exactMatch);
                matchedEndpointIds.add(exactMatch.getEndpointPath() + ":" + exactMatch.getEndpointMethod());
                continue;
            }
            
            // Если точного совпадения нет, ищем наилучшее возможное
            TaskEndpointMapping mapping = findBestMatch(task, endpoints);
            if (mapping != null && mapping.getConfidenceScore() >= MIN_CONFIDENCE_THRESHOLD) {
                taskMappings.put(task.getId(), mapping);
                matchedEndpointIds.add(mapping.getEndpointPath() + ":" + mapping.getEndpointMethod());
            } else {
                UnmatchedElement unmatched = createUnmatchedTask(task, endpoints);
                unmatchedTasks.add(unmatched);
            }
        }
        
        long matchingDuration = System.currentTimeMillis() - matchingStartTime;
        log.debug("Task matching completed in {}ms: {} matched, {} unmatched", 
            matchingDuration, taskMappings.size(), unmatchedTasks.size());
        
        // Анализируем поток данных на основе последовательности задач
        long dataFlowStartTime = System.currentTimeMillis();
        List<DataFlowEdge> dataFlowEdges = dataFlowAnalyzer.analyzeDataFlow(processModel, taskMappings);
        log.debug("Data flow analysis completed in {}ms: {} edges", 
            System.currentTimeMillis() - dataFlowStartTime, dataFlowEdges.size());

        // Анализируем зависимости из описаний OpenAPI
        Map<String, List<OpenApiDependencyAnalyzer.OpenApiDependency>> dependencies = 
                openApiDependencyAnalyzer.analyze(openApiModel);
        
        // Автоматически добавляем недостающие зависимости как виртуальные задачи
        addMissingDependencyTasks(taskMappings, dependencies, endpoints, matchedEndpointIds);
        
        // Создаем ребра зависимостей
        List<DataFlowEdge> dependencyEdges = buildEdgesFromOpenApiDependencies(taskMappings, openApiModel);
        // Избегаем дубликатов
        for (DataFlowEdge de : dependencyEdges) {
            boolean exists = dataFlowEdges.stream().anyMatch(e ->
                    Objects.equals(e.getSourceTaskId(), de.getSourceTaskId()) &&
                    Objects.equals(e.getTargetTaskId(), de.getTargetTaskId()) &&
                    Objects.equals(e.getFields(), de.getFields()));
            if (!exists) {
                dataFlowEdges.add(de);
            }
        }
        
        // Вычисляем статистику с фокусом на покрытии BPMN задач
        // Исключаем виртуальные задачи из подсчета
        long realBpmnTasksCount = taskMappings.values().stream()
                .filter(m -> m != null && !m.getTaskId().startsWith("VIRTUAL_DEP_"))
                .count();
        double overallConfidence = calculateBpmnTaskMatchingConfidence(taskMappings, processModel.getTasks().size());
        
        // Анализируем общие поля между эндпоинтами (исключаем поля-зависимости)
        List<CommonField> commonFields = commonFieldAnalyzer.analyzeCommonFields(openApiModel, dependencies);
        
        // Анализируем секретные поля, которые требуют ввода пользователем
        List<SecretField> secretFields = secretFieldAnalyzer.analyzeSecretFields(openApiModel);
        
        // Выполняем проверку файлов с помощью ИИ (если переданы исходные строки)
        // Делаем это с таймаутом, чтобы не блокировать основной процесс
        AIVerificationReport aiVerificationReport = null;
        if (openApiJson != null && bpmnXml != null && aiVerificationService != null) {
            long aiCheckStartTime = System.currentTimeMillis();
            log.info("Starting AI verification check...");
            try {
                aiVerificationReport = aiVerificationService.verifyFiles(openApiJson, bpmnXml);
                long aiCheckDuration = System.currentTimeMillis() - aiCheckStartTime;
                log.info("AI verification check completed in {}ms", aiCheckDuration);
            } catch (Exception e) {
                long aiCheckDuration = System.currentTimeMillis() - aiCheckStartTime;
                log.warn("AI verification check failed after {}ms, continuing without AI report", aiCheckDuration, e);
                // Логируем ошибку, но не прерываем выполнение
                // В случае ошибки проверки ИИ, просто не добавляем отчет
            }
        } else {
            if (aiVerificationService == null) {
                log.debug("AI verification service is not available");
            } else {
                log.debug("Skipping AI verification: openApiJson={}, bpmnXml={}", 
                    openApiJson != null, bpmnXml != null);
            }
        }
        
        MappingResult result = new MappingResult();
        result.setTaskMappings(taskMappings);
        // Убеждаемся, что списки не null
        result.setDataFlowEdges(dataFlowEdges != null ? dataFlowEdges : new ArrayList<>());
        result.setUnmatchedTasks(unmatchedTasks != null ? unmatchedTasks : new ArrayList<>());
        result.setOverallConfidence(overallConfidence);
        result.setTotalTasks(processModel.getTasks().size());
        result.setMatchedTasks((int) realBpmnTasksCount); // Только реальные задачи BPMN, без виртуальных
        // Эндпоинты отображаются только для информации, без списка несопоставленных
        result.setTotalEndpoints(endpoints.size());
        result.setMatchedEndpoints(matchedEndpointIds.size());
        result.setCommonFields(commonFields != null ? commonFields : new ArrayList<>());
        result.setSecretFields(secretFields != null ? secretFields : new ArrayList<>());
        result.setAiVerificationReport(aiVerificationReport);
        
        long totalMappingDuration = System.currentTimeMillis() - mappingStartTime;
        log.info("Mapping process completed in {}ms: {} tasks matched, confidence: {:.2f}%", 
            totalMappingDuration, result.getMatchedTasks(), result.getOverallConfidence() * 100.0);
        log.info("Mapping result summary: {} dataFlowEdges, {} commonFields, {} secretFields", 
            dataFlowEdges != null ? dataFlowEdges.size() : 0,
            commonFields != null ? commonFields.size() : 0,
            secretFields != null ? secretFields.size() : 0);
        
        return result;
    }
    
    /**
     * Находит лучшее сопоставление для задачи
     */
    private TaskEndpointMapping findBestMatch(ProcessTask task, List<EndpointInfo> endpoints) {
        TaskEndpointMapping bestMatch = null;
        double bestScore = 0.0;
        
        // Стратегия 1: Точное совпадение по operationId и task ID/name
        TaskEndpointMapping exactMatch = tryExactMatch(task, endpoints);
        if (exactMatch != null && exactMatch.getConfidenceScore() >= EXACT_MATCH_THRESHOLD) {
            return exactMatch;
        }
        if (exactMatch != null && exactMatch.getConfidenceScore() > bestScore) {
            bestMatch = exactMatch;
            bestScore = exactMatch.getConfidenceScore();
        }
        
        // Стратегия 2: Совпадение по custom properties (api.endpoint)
        TaskEndpointMapping customPropertyMatch = tryCustomPropertyMatch(task, endpoints);
        if (customPropertyMatch != null && customPropertyMatch.getConfidenceScore() > bestScore) {
            bestMatch = customPropertyMatch;
            bestScore = customPropertyMatch.getConfidenceScore();
        }
        
        // Стратегия 3: Совпадение по описанию (summary/description)
        TaskEndpointMapping descriptionMatch = tryDescriptionMatch(task, endpoints);
        if (descriptionMatch != null && descriptionMatch.getConfidenceScore() > bestScore) {
            bestMatch = descriptionMatch;
            bestScore = descriptionMatch.getConfidenceScore();
        }
        
        // Стратегия 4: Семантический анализ
        TaskEndpointMapping semanticMatch = trySemanticMatch(task, endpoints);
        if (semanticMatch != null && semanticMatch.getConfidenceScore() > bestScore) {
            bestMatch = semanticMatch;
            bestScore = semanticMatch.getConfidenceScore();
        }
        
        return bestMatch;
    }
    
    /**
     * Стратегия 1: Точное совпадение по operationId и task ID/name
     */
    private TaskEndpointMapping tryExactMatch(ProcessTask task, List<EndpointInfo> endpoints) {
        String taskId = task.getId();
        String taskName = task.getName();
        
        for (EndpointInfo endpoint : endpoints) {
            // Проверяем совпадение operationId с task ID или name
            if (endpoint.getOperationId() != null) {
                if (endpoint.getOperationId().equalsIgnoreCase(taskId) ||
                    endpoint.getOperationId().equalsIgnoreCase(taskName)) {
                    return createMapping(task, endpoint, 1.0, "EXACT");
                }
            }
            
            // Проверяем совпадение path/method с именем задачи
            if (task.getApiEndpointInfo() != null) {
                String taskMethod = task.getApiEndpointInfo().getMethod();
                String taskPath = task.getApiEndpointInfo().getPath();
                
                if (taskMethod != null && taskPath != null &&
                    taskMethod.equalsIgnoreCase(endpoint.getMethod()) &&
                    taskPath.equals(endpoint.getPath())) {
                    return createMapping(task, endpoint, 0.95, "EXACT");
                }
            }
        }
        
        return null;
    }
    
    /**
     * Стратегия 2: Совпадение по custom properties
     */
    private TaskEndpointMapping tryCustomPropertyMatch(ProcessTask task, List<EndpointInfo> endpoints) {
        if (task.getCustomProperties() == null) {
            return null;
        }
        
        // Проверяем свойство api.endpoint
        String apiEndpoint = task.getCustomProperties().get("api.endpoint");
        if (apiEndpoint == null) {
            apiEndpoint = task.getCustomProperties().get("apiEndpoint");
        }
        
        if (apiEndpoint != null) {
            // Парсим "METHOD /path"
            String[] parts = apiEndpoint.trim().split("\\s+", 2);
            if (parts.length == 2) {
                String method = parts[0].toUpperCase();
                String path = parts[1];
                
                for (EndpointInfo endpoint : endpoints) {
                    if (endpoint.getMethod().equals(method) && endpoint.getPath().equals(path)) {
                        return createMapping(task, endpoint, 0.9, "CUSTOM_PROPERTY");
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Автоматически добавляет недостающие зависимости как виртуальные задачи.
     * Если эндпоинт требуется как зависимость, но не сопоставлен с задачей BPMN,
     * создается виртуальная задача для этого эндпоинта.
     */
    private void addMissingDependencyTasks(Map<String, TaskEndpointMapping> taskMappings,
                                          Map<String, List<OpenApiDependencyAnalyzer.OpenApiDependency>> dependencies,
                                          List<EndpointInfo> endpoints,
                                          Set<String> matchedEndpointIds) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        // Создаем индекс: endpointKey -> EndpointInfo
        Map<String, EndpointInfo> endpointMap = new HashMap<>();
        for (EndpointInfo endpoint : endpoints) {
            String key = endpoint.getMethod().toUpperCase(Locale.ROOT) + ":" + endpoint.getPath();
            endpointMap.put(key, endpoint);
        }

        // Создаем индекс: endpointKey -> taskId из существующих маппингов
        Map<String, String> endpointToTaskId = new HashMap<>();
        taskMappings.forEach((taskId, mapping) -> {
            if (mapping != null && mapping.getEndpointMethod() != null && mapping.getEndpointPath() != null) {
                String key = mapping.getEndpointMethod().toUpperCase(Locale.ROOT) + ":" + mapping.getEndpointPath();
                endpointToTaskId.put(key, taskId);
            }
        });

        // Находим все зависимости, которые требуются, но еще не сопоставлены
        Set<String> missingDependencies = new HashSet<>();
        for (List<OpenApiDependencyAnalyzer.OpenApiDependency> deps : dependencies.values()) {
            for (OpenApiDependencyAnalyzer.OpenApiDependency dep : deps) {
                String depKey = dep.method.toUpperCase(Locale.ROOT) + ":" + dep.path;
                // Если зависимость не сопоставлена и существует в OpenAPI
                if (!endpointToTaskId.containsKey(depKey) && endpointMap.containsKey(depKey)) {
                    missingDependencies.add(depKey);
                }
            }
        }

        // Создаем виртуальные задачи для недостающих зависимостей
        int virtualTaskCounter = 1;
        for (String depKey : missingDependencies) {
            EndpointInfo endpoint = endpointMap.get(depKey);
            if (endpoint == null) continue;

            // Создаем уникальный ID для виртуальной задачи
            String virtualTaskId = "VIRTUAL_DEP_" + virtualTaskCounter++;
            
            // Создаем маппинг для виртуальной задачи
            TaskEndpointMapping virtualMapping = new TaskEndpointMapping();
            virtualMapping.setTaskId(virtualTaskId);
            virtualMapping.setTaskName(generateVirtualTaskName(endpoint));
            virtualMapping.setEndpointPath(endpoint.getPath());
            virtualMapping.setEndpointMethod(endpoint.getMethod());
            virtualMapping.setOperationId(endpoint.getOperationId());
            virtualMapping.setConfidenceScore(1.0); // Высокая уверенность, так как это явная зависимость
            virtualMapping.setMatchingStrategy("DEPENDENCY_AUTO");
            virtualMapping.setRecommendation("Автоматически добавлено как зависимость для другого эндпоинта");

            taskMappings.put(virtualTaskId, virtualMapping);
            matchedEndpointIds.add(depKey);
        }
    }

    /**
     * Генерирует имя для виртуальной задачи на основе информации об эндпоинте
     */
    private String generateVirtualTaskName(EndpointInfo endpoint) {
        if (endpoint.getSummary() != null && !endpoint.getSummary().trim().isEmpty()) {
            return endpoint.getSummary();
        }
        if (endpoint.getOperationId() != null && !endpoint.getOperationId().trim().isEmpty()) {
            return endpoint.getOperationId();
        }
        // Генерируем имя из метода и пути
        String method = endpoint.getMethod() != null ? endpoint.getMethod() : "";
        String path = endpoint.getPath() != null ? endpoint.getPath() : "";
        return method + " " + path;
    }

    /**
     * Создает дополнительные ребра потока данных на основе зависимостей из описаний OpenAPI.
     * Учитывает зависимости на уровне параметров (например, когда параметр требует значение из другого эндпоинта).
     */
    private List<DataFlowEdge> buildEdgesFromOpenApiDependencies(Map<String, TaskEndpointMapping> taskMappings,
                                                                 OpenApiModel openApiModel) {
        List<DataFlowEdge> edges = new ArrayList<>();
        if (openApiModel == null || taskMappings == null || taskMappings.isEmpty()) {
            return edges;
        }

        // Индекс: endpointKey (METHOD:PATH) -> taskId
        Map<String, String> endpointToTask = new HashMap<>();
        taskMappings.forEach((taskId, mapping) -> {
            if (mapping != null && mapping.getEndpointMethod() != null && mapping.getEndpointPath() != null) {
                String key = mapping.getEndpointMethod().toUpperCase(Locale.ROOT) + ":" + mapping.getEndpointPath();
                endpointToTask.put(key, taskId);
            }
        });

        Map<String, List<OpenApiDependencyAnalyzer.OpenApiDependency>> depsByEndpoint =
                openApiDependencyAnalyzer.analyze(openApiModel);

        for (Map.Entry<String, List<OpenApiDependencyAnalyzer.OpenApiDependency>> entry : depsByEndpoint.entrySet()) {
            String targetEndpointKey = entry.getKey();
            String targetTaskId = endpointToTask.get(targetEndpointKey);
            if (targetTaskId == null) continue; // Текущий эндпоинт не сопоставлен ни с одной задачей

            for (OpenApiDependencyAnalyzer.OpenApiDependency dep : entry.getValue()) {
                String sourceEndpointKey = dep.method.toUpperCase(Locale.ROOT) + ":" + dep.path;
                String sourceTaskId = endpointToTask.get(sourceEndpointKey);
                if (sourceTaskId == null) continue; // Зависимый эндпоинт не сопоставлен ни с одной задачей

                // Определяем поля из ответа, которые нужно использовать
                List<String> fields = new ArrayList<>();
                String sourceField = null;
                
                if (dep.fieldHint != null && !dep.fieldHint.isBlank()) {
                    sourceField = dep.fieldHint;
                    fields.add(dep.fieldHint);
                } else if (dep.parameterName != null) {
                    // Если есть имя параметра, пытаемся извлечь подсказку из него
                    String normalized = dep.parameterName.replaceFirst("^[xX]-", "");
                    sourceField = normalized;
                    fields.add(normalized);
                } else {
                    // Базовые поля, если явного указания нет
                    sourceField = "id"; // по умолчанию ищем id
                    fields.add("id");
                    fields.add("data");
                }

                // Создаем ребро с информацией о параметрах
                DataFlowEdge edge = new DataFlowEdge(sourceTaskId, targetTaskId, fields, dep.confidence);
                
                // Если зависимость связана с конкретным параметром, добавляем информацию о маппинге
                if (dep.parameterName != null) {
                    if (edge.getParameterMappings() == null) {
                        edge.setParameterMappings(new HashMap<>());
                    }
                    
                    DataFlowEdge.ParameterMapping paramMapping = new DataFlowEdge.ParameterMapping(
                        dep.parameterName,
                        dep.parameterIn,
                        sourceField,
                        dep.fieldHint
                    );
                    edge.getParameterMappings().put(dep.parameterName, paramMapping);
                }
                
                edges.add(edge);
            }
        }

        return edges;
    }
    
    /**
     * Стратегия 3: Совпадение по описанию
     */
    private TaskEndpointMapping tryDescriptionMatch(ProcessTask task, List<EndpointInfo> endpoints) {
        String taskDescription = buildTaskText(task);
        if (taskDescription == null || taskDescription.trim().isEmpty()) {
            return null;
        }
        
        TaskEndpointMapping bestMatch = null;
        double bestScore = 0.0;
        
        for (EndpointInfo endpoint : endpoints) {
            String endpointText = endpoint.getFullText();
            double similarity = semanticAnalysisService.calculateSimilarity(taskDescription, endpointText);
            
            if (similarity > bestScore) {
                bestScore = similarity;
                bestMatch = createMapping(task, endpoint, similarity * 0.85, "DESCRIPTION");
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Стратегия 4: Семантический анализ
     */
    private TaskEndpointMapping trySemanticMatch(ProcessTask task, List<EndpointInfo> endpoints) {
        String taskText = buildTaskText(task);
        if (taskText == null || taskText.trim().isEmpty()) {
            return null;
        }
        
        // Создаем словарь текстов эндпоинтов
        Map<String, String> endpointTexts = new HashMap<>();
        for (EndpointInfo endpoint : endpoints) {
            String key = endpoint.getPath() + ":" + endpoint.getMethod();
            endpointTexts.put(key, endpoint.getFullText());
        }
        
        // Находим наиболее похожий эндпоинт
        Map.Entry<String, Double> bestMatch = semanticAnalysisService.findMostSimilar(taskText, endpointTexts);
        
        if (bestMatch != null && bestMatch.getValue() >= SEMANTIC_MATCH_THRESHOLD) {
            String[] parts = bestMatch.getKey().split(":");
            String path = parts[0];
            String method = parts.length > 1 ? parts[1] : "";
            
            EndpointInfo endpoint = endpoints.stream()
                    .filter(e -> e.getPath().equals(path) && e.getMethod().equals(method))
                    .findFirst()
                    .orElse(null);
            
            if (endpoint != null) {
                return createMapping(task, endpoint, bestMatch.getValue(), "SEMANTIC");
            }
        }
        
        return null;
    }
    
    /**
     * Создает текст задачи для семантического анализа
     */
    private String buildTaskText(ProcessTask task) {
        StringBuilder sb = new StringBuilder();
        if (task.getName() != null) sb.append(task.getName()).append(" ");
        if (task.getDescription() != null) sb.append(task.getDescription()).append(" ");
        if (task.getApiEndpointInfo() != null && task.getApiEndpointInfo().getDescription() != null) {
            sb.append(task.getApiEndpointInfo().getDescription()).append(" ");
        }
        return sb.toString().trim();
    }
    
    /**
     * Создает объект сопоставления
     */
    private TaskEndpointMapping createMapping(ProcessTask task, EndpointInfo endpoint, 
                                              double confidence, String strategy) {
        TaskEndpointMapping mapping = new TaskEndpointMapping();
        mapping.setTaskId(task.getId());
        mapping.setTaskName(task.getName());
        mapping.setEndpointPath(endpoint.getPath());
        mapping.setEndpointMethod(endpoint.getMethod());
        mapping.setOperationId(endpoint.getOperationId());
        mapping.setConfidenceScore(confidence);
        mapping.setMatchingStrategy(strategy);
        mapping.setRecommendation(generateRecommendation(task, endpoint, confidence, strategy));
        return mapping;
    }
    
    /**
     * Генерирует рекомендацию для сопоставления
     */
    private String generateRecommendation(ProcessTask task, EndpointInfo endpoint, 
                                         double confidence, String strategy) {
        if (confidence >= 0.9) {
            return "Высокая уверенность в сопоставлении";
        } else if (confidence >= 0.7) {
            return "Средняя уверенность. Рекомендуется проверить вручную";
        } else {
            return "Низкая уверенность. Требуется ручная проверка";
        }
    }
    
    /**
     * Создает объект несопоставленной задачи
     */
    private UnmatchedElement createUnmatchedTask(ProcessTask task, List<EndpointInfo> endpoints) {
        UnmatchedElement unmatched = new UnmatchedElement();
        unmatched.setElementId(task.getId());
        unmatched.setElementName(task.getName());
        unmatched.setElementType("TASK");
        
        // Находим топ-3 наиболее похожих эндпоинта для рекомендаций
        String taskText = buildTaskText(task);
        List<String> recommendations = new ArrayList<>();
        
        if (taskText != null && !taskText.trim().isEmpty()) {
            Map<String, Double> similarities = new HashMap<>();
            for (EndpointInfo endpoint : endpoints) {
                double sim = semanticAnalysisService.calculateSimilarity(taskText, endpoint.getFullText());
                similarities.put(endpoint.getMethod() + " " + endpoint.getPath(), sim);
            }
            
            similarities.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .forEach(entry -> {
                        if (entry.getValue() > 0.3) {
                            recommendations.add("Возможный эндпоинт: " + entry.getKey() + 
                                              " (сходство: " + String.format("%.2f", entry.getValue()) + ")");
                        }
                    });
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Не удалось найти похожие эндпоинты автоматически");
        }
        
        unmatched.setRecommendations(recommendations);
        unmatched.setMaxConfidence(recommendations.stream()
                .mapToDouble(r -> {
                    // Извлекаем значение сходства из рекомендации
                    if (r.contains("сходство: ")) {
                        try {
                            String scoreStr = r.substring(r.indexOf("сходство: ") + 10, r.indexOf(")"));
                            return Double.parseDouble(scoreStr.trim());
                        } catch (Exception e) {
                            return 0.0;
                        }
                    }
                    return 0.0;
                })
                .max()
                .orElse(0.0));
        
        return unmatched;
    }
    
    /**
     * Рассчитывает общую уверенность в сопоставлении с фокусом на покрытии BPMN задач
     * @param mappings Маппинг задач на эндпоинты
     * @param totalTasks Общее количество BPMN задач
     */
    private double calculateBpmnTaskMatchingConfidence(Map<String, TaskEndpointMapping> mappings, int totalTasks) {
        if (totalTasks == 0 || mappings.isEmpty()) {
            return 0.0;
        }
        
        // Средняя уверенность по сопоставленным задачам
        double avgConfidence = mappings.values().stream()
                .mapToDouble(TaskEndpointMapping::getConfidenceScore)
                .average()
                .orElse(0.0);
        
        // Процент покрытия BPMN задач
        double coverage = (double) mappings.size() / totalTasks;
        
        // Итоговая уверенность учитывает как качество сопоставления, так и покрытие задач
        // Делаем больший акцент на покрытии BPMN задач (60%) и меньший на уверенности сопоставления (40%)
        return (0.6 * coverage + 0.4 * avgConfidence);
    }
}

