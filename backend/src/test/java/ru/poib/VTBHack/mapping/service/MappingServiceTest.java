package ru.poib.VTBHack.mapping.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.ProcessTask;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MappingServiceTest {
    
    private MappingService mappingService;
    private EndpointExtractor endpointExtractor;
    private SemanticAnalysisService semanticAnalysisService;
    private DataFlowAnalyzer dataFlowAnalyzer;
    
    @BeforeEach
    void setUp() {
        endpointExtractor = new EndpointExtractor();
        semanticAnalysisService = new SemanticAnalysisService();
        dataFlowAnalyzer = new DataFlowAnalyzer();
        mappingService = new MappingService(endpointExtractor, semanticAnalysisService, dataFlowAnalyzer);
    }
    
    @Test
    void testExactMatchByOperationId() {
        // Создаем тестовую модель процесса
        ProcessModel processModel = createTestProcessModel();
        
        // Создаем тестовую OpenAPI модель
        OpenApiModel openApiModel = createTestOpenApiModel();
        
        // Выполняем сопоставление
        MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel);
        
        assertNotNull(result);
        assertNotNull(result.getTaskMappings());
        
        // Проверяем, что хотя бы одна задача сопоставлена
        assertFalse(result.getTaskMappings().isEmpty());
    }
    
    @Test
    void testSemanticMatching() {
        SemanticAnalysisService service = new SemanticAnalysisService();
        
        // Тест похожих текстов
        double similarity1 = service.calculateSimilarity(
            "Авторизация пользователя",
            "Аутентификация пользователя в системе"
        );
        assertTrue(similarity1 > 0.5, "Похожие тексты должны иметь высокое сходство");
        
        // Тест разных текстов
        double similarity2 = service.calculateSimilarity(
            "Авторизация пользователя",
            "Получение списка счетов"
        );
        assertTrue(similarity2 < similarity1, "Разные тексты должны иметь меньшее сходство");
        
        // Тест идентичных текстов
        double similarity3 = service.calculateSimilarity(
            "Авторизация пользователя",
            "Авторизация пользователя"
        );
        assertEquals(1.0, similarity3, 0.01, "Идентичные тексты должны иметь сходство 1.0");
    }
    
    @Test
    void testMappingAccuracy() {
        ProcessModel processModel = createTestProcessModel();
        OpenApiModel openApiModel = createTestOpenApiModel();
        
        MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel);
        
        // Проверяем, что общая уверенность разумна
        assertTrue(result.getOverallConfidence() >= 0.0 && result.getOverallConfidence() <= 1.0);
        
        // Проверяем статистику
        assertEquals(processModel.getTasks().size(), result.getTotalTasks());
        assertTrue(result.getMatchedTasks() <= result.getTotalTasks());
    }
    
    @Test
    void testDataFlowAnalysis() {
        ProcessModel processModel = createTestProcessModel();
        OpenApiModel openApiModel = createTestOpenApiModel();
        
        MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel);
        
        assertNotNull(result.getDataFlowEdges());
        // Если есть сопоставленные задачи, должны быть ребра потока данных
        if (result.getMatchedTasks() > 1) {
            assertFalse(result.getDataFlowEdges().isEmpty());
        }
    }
    
    @Test
    void testPerformance() {
        // Тест производительности: должно быть < 10 секунд для 20 задач и 50 эндпоинтов
        ProcessModel processModel = createLargeProcessModel(20);
        OpenApiModel openApiModel = createLargeOpenApiModel(50);
        
        long startTime = System.currentTimeMillis();
        MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        assertTrue(duration < 10000, "Сопоставление должно выполняться менее чем за 10 секунд. Время: " + duration + "ms");
        
        assertNotNull(result);
    }
    
    // Вспомогательные методы для создания тестовых данных
    
    private ProcessModel createTestProcessModel() {
        ProcessModel model = new ProcessModel();
        model.setId("test-process");
        model.setName("Test Process");
        
        List<ProcessTask> tasks = new ArrayList<>();
        
        ProcessTask task1 = new ProcessTask();
        task1.setId("auth-task");
        task1.setName("Аутентификация");
        task1.setDescription("Авторизация пользователя в системе");
        task1.setType("ServiceTask");
        tasks.add(task1);
        
        ProcessTask task2 = new ProcessTask();
        task2.setId("get-accounts-task");
        task2.setName("Получение счетов");
        task2.setDescription("Получение списка счетов пользователя");
        task2.setType("ServiceTask");
        tasks.add(task2);
        
        model.setTasks(tasks);
        model.setSequenceFlows(new HashMap<>());
        
        return model;
    }
    
    private OpenApiModel createTestOpenApiModel() {
        OpenApiModel model = new OpenApiModel();
        model.setOpenApiVersion("3.0.0");
        
        OpenApiModel.Info info = new OpenApiModel.Info();
        info.setTitle("Test API");
        info.setVersion("1.0.0");
        model.setInfo(info);
        
        Map<String, OpenApiModel.PathItem> paths = new HashMap<>();
        
        // Эндпоинт для авторизации
        OpenApiModel.PathItem authPath = new OpenApiModel.PathItem();
        ru.poib.VTBHack.parser.model.openapi.Operation authOp = new ru.poib.VTBHack.parser.model.openapi.Operation();
        authOp.setOperationId("auth-task");
        authOp.setSummary("Авторизация пользователя");
        authOp.setDescription("Аутентификация пользователя в системе");
        authPath.setPost(authOp);
        paths.put("/auth/token", authPath);
        
        // Эндпоинт для получения счетов
        OpenApiModel.PathItem accountsPath = new OpenApiModel.PathItem();
        ru.poib.VTBHack.parser.model.openapi.Operation accountsOp = new ru.poib.VTBHack.parser.model.openapi.Operation();
        accountsOp.setOperationId("get-accounts-task");
        accountsOp.setSummary("Получение счетов");
        accountsOp.setDescription("Получение списка счетов пользователя");
        accountsPath.setGet(accountsOp);
        paths.put("/accounts", accountsPath);
        
        model.setPaths(paths);
        
        return model;
    }
    
    private ProcessModel createLargeProcessModel(int taskCount) {
        ProcessModel model = new ProcessModel();
        model.setId("large-process");
        model.setName("Large Process");
        
        List<ProcessTask> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            ProcessTask task = new ProcessTask();
            task.setId("task-" + i);
            task.setName("Task " + i);
            task.setDescription("Description for task " + i);
            task.setType("ServiceTask");
            tasks.add(task);
        }
        
        model.setTasks(tasks);
        model.setSequenceFlows(new HashMap<>());
        
        return model;
    }
    
    private OpenApiModel createLargeOpenApiModel(int endpointCount) {
        OpenApiModel model = new OpenApiModel();
        model.setOpenApiVersion("3.0.0");
        
        OpenApiModel.Info info = new OpenApiModel.Info();
        info.setTitle("Large API");
        info.setVersion("1.0.0");
        model.setInfo(info);
        
        Map<String, OpenApiModel.PathItem> paths = new HashMap<>();
        for (int i = 0; i < endpointCount; i++) {
            OpenApiModel.PathItem pathItem = new OpenApiModel.PathItem();
            ru.poib.VTBHack.parser.model.openapi.Operation op = new ru.poib.VTBHack.parser.model.openapi.Operation();
            op.setOperationId("operation-" + i);
            op.setSummary("Operation " + i);
            op.setDescription("Description for operation " + i);
            pathItem.setGet(op);
            paths.put("/endpoint/" + i, pathItem);
        }
        
        model.setPaths(paths);
        
        return model;
    }
}

