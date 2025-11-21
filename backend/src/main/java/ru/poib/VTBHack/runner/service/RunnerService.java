package ru.poib.VTBHack.runner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.aiqueue.service.AiAnalysisQueueService;
import ru.poib.VTBHack.execution.model.*;
import ru.poib.VTBHack.execution.service.TestExecutionService;
import ru.poib.VTBHack.generator.model.TestDataGenerationRequest;
import ru.poib.VTBHack.generator.model.TestDataGenerationResult;
import ru.poib.VTBHack.generator.service.TestDataGeneratorService;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.service.BpmnParserService;
import ru.poib.VTBHack.parser.service.OpenApiParserService;
import ru.poib.VTBHack.project.model.Project;
import ru.poib.VTBHack.project.service.ProjectStoreService;
import ru.poib.VTBHack.runner.model.RunExecution;
import ru.poib.VTBHack.runner.repo.RunExecutionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class RunnerService {
    private final RunExecutionRepository repository;
    private final TestExecutionService testExecutionService;
    private final ProjectStoreService projectStoreService;
    private final BpmnParserService bpmnParserService;
    private final OpenApiParserService openApiParserService;
    private final TestDataGeneratorService testDataGeneratorService;
    private final AiAnalysisQueueService aiAnalysisQueueService;
    private final ObjectMapper objectMapper;

    public RunnerService(
            RunExecutionRepository repository,
            TestExecutionService testExecutionService,
            ProjectStoreService projectStoreService,
            BpmnParserService bpmnParserService,
            OpenApiParserService openApiParserService,
            TestDataGeneratorService testDataGeneratorService,
            AiAnalysisQueueService aiAnalysisQueueService) {
        this.repository = repository;
        this.testExecutionService = testExecutionService;
        this.projectStoreService = projectStoreService;
        this.bpmnParserService = bpmnParserService;
        this.openApiParserService = openApiParserService;
        this.testDataGeneratorService = testDataGeneratorService;
        this.aiAnalysisQueueService = aiAnalysisQueueService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Создает новый запуск и начинает асинхронное выполнение
     */
    public RunExecution startRun(String scenarioId, String projectId, Integer parallelism, String dataTemplateId) {
        RunExecution execution = new RunExecution();
        execution.setScenarioId(scenarioId);
        execution.setProjectId(projectId);
        execution.setStatus(RunExecution.RunStatus.QUEUED);
        execution.setParallelism(parallelism != null ? parallelism : 1);
        execution.setCreatedAt(Instant.now());
        
        repository.save(execution);
        
        // Запускаем асинхронное выполнение
        executeAsync(execution.getId());
        
        return execution;
    }

    /**
     * Получает выполнение по ID
     */
    public RunExecution getExecution(String executionId) {
        return repository.findById(executionId).orElse(null);
    }

    /**
     * Получает список выполнений для проекта
     */
    public List<RunExecution> getExecutionsByProject(String projectId) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * Асинхронное выполнение теста
     */
    @Async
    public void executeAsync(String executionId) {
        RunExecution execution = repository.findById(executionId).orElse(null);
        if (execution == null) {
            log.error("Execution {} not found", executionId);
            return;
        }

        try {
            execution.setStatus(RunExecution.RunStatus.RUNNING);
            execution.setStartedAt(Instant.now());
            repository.save(execution);

            // Загружаем проект
            Project project = null;
            if (execution.getProjectId() != null) {
                project = projectStoreService.get(execution.getProjectId());
            }

            if (project == null) {
                throw new RuntimeException("Project not found");
            }

            // Парсим BPMN и OpenAPI
            ProcessModel processModel = bpmnParserService.parse(project.getBpmnXml());
            var openApiModel = openApiParserService.parseOpenApi(project.getOpenApiJson());
            MappingResult mappingResult = project.getMappingResult();

            if (mappingResult == null) {
                throw new RuntimeException("Mapping result not found in project");
            }

            // Генерируем тестовые данные
            TestDataGenerationRequest testDataRequest = new TestDataGenerationRequest();
            testDataRequest.setMappingResult(mappingResult);
            testDataRequest.setOpenApiModel(openApiModel);
            testDataRequest.setGenerationType(ru.poib.VTBHack.generator.model.GenerationType.CLASSIC);
            testDataRequest.setScenario("positive");
            testDataRequest.setVariantsCount(1);

            TestDataGenerationResult testData = testDataGeneratorService.generateTestData(testDataRequest);

            // Создаем конфигурацию выполнения
            ExecutionConfig config = new ExecutionConfig();
            config.setBaseUrl("https://abank.open.bankingapi.ru"); // TODO: из настроек проекта
            config.setRequestTimeoutMs(10000);
            config.setMaxExecutionTimeMs(300000); // 5 минут
            config.setDefaultHeaders(new java.util.HashMap<>());

            // Создаем запрос на выполнение
            TestExecutionRequest executionRequest = new TestExecutionRequest();
            executionRequest.setProcessModel(processModel);
            executionRequest.setMappingResult(mappingResult);
            executionRequest.setTestData(testData);
            executionRequest.setConfig(config);
            executionRequest.setOpenApiModel(openApiModel);
            executionRequest.setTestDataVariantIndex(0);
            executionRequest.setStopOnFirstError(false); // MVP: всегда идем до конца

            // Выполняем тест с обновлением статуса
            TestExecutionResult result = executeWithProgress(execution, executionRequest);

            // Сохраняем результат
            execution.setExecutionResult(result);
            execution.setStatus(RunExecution.RunStatus.COMPLETED);
            execution.setFinishedAt(Instant.now());
            execution.setProgress(1.0);
            repository.save(execution);

            // Запускаем AI анализ истории вызовов
            try {
                String callHistory = buildCallHistory(result);
                log.info("Starting AI analysis for execution {}", executionId);
                // Используем существующий сервис AI анализа
                var aiJob = aiAnalysisQueueService.enqueue(
                    project.getOpenApiJson(),
                    project.getBpmnXml(),
                    null, // modelName
                    execution.getProjectId()
                );
                execution.setAiAnalysisJobId(aiJob.getId());
                repository.save(execution);
            } catch (Exception e) {
                log.error("Failed to start AI analysis", e);
                addLog(execution, "error", "Failed to start AI analysis: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Execution {} failed", executionId, e);
            execution.setStatus(RunExecution.RunStatus.FAILED);
            execution.setFinishedAt(Instant.now());
            addLog(execution, "error", "Execution failed: " + e.getMessage());
            repository.save(execution);
        }
    }

    /**
     * Выполняет тест с обновлением прогресса в реальном времени
     */
    private TestExecutionResult executeWithProgress(RunExecution execution, TestExecutionRequest request) {
        // Используем существующий сервис, но обновляем RunExecution после каждого шага
        // Для этого нужно выполнить тест и обновлять прогресс
        TestExecutionResult result = testExecutionService.executeTest(request);
        
        // Преобразуем результаты шагов и обновляем выполнение
        List<RunExecution.StepExecution> stepExecutions = new ArrayList<>();
        for (TestExecutionStep step : result.getSteps()) {
            RunExecution.StepExecution stepExec = convertStep(step);
            stepExecutions.add(stepExec);
        }
        
        execution.setSteps(stepExecutions);
        execution.setProgress(1.0);
        repository.save(execution);
        
        return result;
    }
    
    private RunExecution.StepExecution convertStep(TestExecutionStep step) {
        RunExecution.StepExecution stepExec = new RunExecution.StepExecution();
        stepExec.setStepId(step.getTaskId());
        stepExec.setTaskId(step.getTaskId());
        stepExec.setTaskName(step.getTaskName());
        
        // Преобразуем статус
        if (step.getStatus() == TestExecutionStep.StepStatus.SUCCESS) {
            stepExec.setStatus(RunExecution.StepExecution.StepStatus.SUCCESS);
        } else if (step.getStatus() == TestExecutionStep.StepStatus.FAILED) {
            stepExec.setStatus(RunExecution.StepExecution.StepStatus.FAILED);
        } else {
            stepExec.setStatus(RunExecution.StepExecution.StepStatus.PENDING);
        }
        
        stepExec.setStartedAt(step.getStartTime());
        stepExec.setFinishedAt(step.getEndTime());
        stepExec.setDurationMs(step.getDurationMs());
        stepExec.setErrorMessage(step.getErrorMessage());
        
        // Преобразуем запрос
        if (step.getRequest() != null) {
            RunExecution.StepRequest req = new RunExecution.StepRequest();
            req.setMethod(step.getRequest().getMethod());
            req.setUrl(step.getRequest().getUrl());
            req.setHeaders(step.getRequest().getHeaders());
            req.setBody(step.getRequest().getBody());
            req.setTimestamp(step.getRequest().getTimestamp());
            stepExec.setRequest(req);
        }
        
        // Преобразуем ответ
        if (step.getResponse() != null) {
            RunExecution.StepResponse resp = new RunExecution.StepResponse();
            resp.setStatusCode(step.getResponse().getStatusCode());
            resp.setHeaders(step.getResponse().getHeaders());
            resp.setBody(step.getResponse().getBody());
            resp.setResponseTimeMs(step.getResponse().getResponseTimeMs());
            resp.setTimestamp(step.getResponse().getTimestamp());
            stepExec.setResponse(resp);
        }
        
        return stepExec;
    }

    /**
     * Строит историю вызовов для AI анализа
     */
    private String buildCallHistory(TestExecutionResult result) {
        StringBuilder history = new StringBuilder();
        history.append("Test Execution History:\n");
        history.append("Process: ").append(result.getProcessName()).append("\n");
        history.append("Total Steps: ").append(result.getSteps().size()).append("\n\n");

        for (int i = 0; i < result.getSteps().size(); i++) {
            TestExecutionStep step = result.getSteps().get(i);
            history.append("Step ").append(i + 1).append(": ").append(step.getTaskName()).append("\n");
            history.append("  Status: ").append(step.getStatus()).append("\n");
            
            if (step.getRequest() != null) {
                history.append("  Request: ").append(step.getRequest().getMethod())
                    .append(" ").append(step.getRequest().getUrl()).append("\n");
            }
            
            if (step.getResponse() != null) {
                history.append("  Response: ").append(step.getResponse().getStatusCode()).append("\n");
            }
            
            if (step.getErrorMessage() != null) {
                history.append("  Error: ").append(step.getErrorMessage()).append("\n");
            }
            
            history.append("\n");
        }

        return history.toString();
    }

    /**
     * Добавляет лог в выполнение
     */
    private void addLog(RunExecution execution, String level, String message) {
        RunExecution.LogEntry logEntry = new RunExecution.LogEntry();
        logEntry.setLevel(level);
        logEntry.setMessage(message);
        logEntry.setTimestamp(Instant.now().toString());
        
        if (execution.getLogs() == null) {
            execution.setLogs(new ArrayList<>());
        }
        execution.getLogs().add(logEntry);
    }

}

