package ru.poib.VTBHack.execution.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.AllArgsConstructor;
import ru.poib.VTBHack.execution.model.ExecutionConfig;
import ru.poib.VTBHack.execution.model.TestExecutionRequest;
import ru.poib.VTBHack.execution.model.TestExecutionResult;
import ru.poib.VTBHack.execution.service.TestExecutionService;
import ru.poib.VTBHack.generator.model.TestDataGenerationResult;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.service.BpmnParserService;
import ru.poib.VTBHack.parser.service.OpenApiParserService;

/**
 * REST контроллер для модуля выполнения тестов
 */
@RestController
@RequestMapping("/api/execution")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class TestExecutionController {
    
    private final TestExecutionService testExecutionService;
    private final BpmnParserService bpmnParserService;
    private final OpenApiParserService openApiParserService;
    
    /**
     * Выполняет тест с полным запросом
     * 
     * @param request полный запрос на выполнение теста
     * @return результат выполнения
     */
    @PostMapping("/execute")
    public ResponseEntity<TestExecutionResult> executeTest(@RequestBody TestExecutionRequest request) {
        try {
            TestExecutionResult result = testExecutionService.executeTest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Выполняет тест с параметрами из строк запроса
     * Удобный метод для быстрого запуска тестов
     * 
     * @param bpmnXml XML содержимое BPMN файла
     * @param openApiJson JSON содержимое OpenAPI спецификации
     * @param testDataJson JSON с тестовыми данными (TestDataGenerationResult)
     * @param mappingResultJson JSON с результатом маппинга (MappingResult)
     * @param baseUrl базовый URL API
     * @param variantIndex индекс варианта тестовых данных (по умолчанию 0)
     * @param stopOnFirstError остановить выполнение при первой ошибке (по умолчанию false)
     * @return результат выполнения
     */
    @PostMapping("/execute-simple")
    public ResponseEntity<TestExecutionResult> executeTestSimple(
            @RequestParam String bpmnXml,
            @RequestParam(required = false) String openApiJson,
            @RequestParam String testDataJson,
            @RequestParam String mappingResultJson,
            @RequestParam String baseUrl,
            @RequestParam(defaultValue = "0") int variantIndex,
            @RequestParam(defaultValue = "false") boolean stopOnFirstError) {
        try {
            // Парсим входные данные
            ProcessModel processModel = bpmnParserService.parse(bpmnXml);
            
            // Парсим OpenAPI если предоставлен
            ru.poib.VTBHack.parser.model.openapi.OpenApiModel openApiModel = null;
            if (openApiJson != null && !openApiJson.isEmpty()) {
                openApiModel = openApiParserService.parseOpenApi(openApiJson);
            }
            
            // Парсим тестовые данные и маппинг (нужно использовать ObjectMapper)
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            TestDataGenerationResult testData = objectMapper.readValue(testDataJson, TestDataGenerationResult.class);
            MappingResult mappingResult = objectMapper.readValue(mappingResultJson, MappingResult.class);
            
            // Создаем конфигурацию
            ExecutionConfig config = new ExecutionConfig();
            config.setBaseUrl(baseUrl);
            config.setRequestTimeoutMs(30000);
            config.setConnectionTimeoutMs(10000);
            config.setMaxExecutionTimeMs(120000);
            config.setAuthConfig(new ExecutionConfig.AuthConfig(
                    ExecutionConfig.AuthConfig.AuthType.BEARER,
                    null,
                    null,
                    null,
                    null
            ));
            
            // Создаем запрос
            TestExecutionRequest request = new TestExecutionRequest();
            request.setProcessModel(processModel);
            request.setMappingResult(mappingResult);
            request.setTestData(testData);
            request.setConfig(config);
            request.setOpenApiModel(openApiModel);
            request.setTestDataVariantIndex(variantIndex);
            request.setStopOnFirstError(stopOnFirstError);
            
            // Выполняем тест
            TestExecutionResult result = testExecutionService.executeTest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.out.println("Ошибка выполнения простого теста: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Получает статус выполнения теста (для асинхронных операций)
     * В текущей реализации тесты выполняются синхронно, но метод оставлен для будущего расширения
     * 
     * @param executionId ID выполнения (если будет реализована асинхронность)
     * @return результат выполнения
     */
    @GetMapping("/status/{executionId}")
    public ResponseEntity<TestExecutionResult> getExecutionStatus(@PathVariable String executionId) {
        // В будущем здесь можно реализовать получение статуса асинхронного выполнения
        return ResponseEntity.notFound().build();
    }
}

