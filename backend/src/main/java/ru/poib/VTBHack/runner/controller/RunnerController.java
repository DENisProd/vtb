package ru.poib.VTBHack.runner.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.poib.VTBHack.runner.model.RunExecution;
import ru.poib.VTBHack.runner.service.RunnerService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/runner")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class RunnerController {
    private final RunnerService runnerService;
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Data
    public static class StartRunRequest {
        private String scenarioId;
        private String projectId;
        private Integer parallelism;
        private String dataTemplateId;
    }

    @PostMapping("/run")
    public ResponseEntity<StartRunResponse> startRun(@RequestBody StartRunRequest request) {
        try {
            RunExecution execution = runnerService.startRun(
                request.getScenarioId(),
                request.getProjectId(),
                request.getParallelism(),
                request.getDataTemplateId()
            );
            return ResponseEntity.ok(new StartRunResponse(execution.getId()));
        } catch (Exception e) {
            log.error("Failed to start run", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<RunExecution> getExecution(@PathVariable String executionId) {
        RunExecution execution = runnerService.getExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    @GetMapping("/history")
    public ResponseEntity<List<RunExecution>> getHistory(@RequestParam(required = false) String projectId) {
        List<RunExecution> executions;
        if (projectId != null) {
            executions = runnerService.getExecutionsByProject(projectId);
        } else {
            // TODO: получить все выполнения
            executions = List.of();
        }
        return ResponseEntity.ok(executions);
    }

    /**
     * SSE endpoint для получения обновлений статуса выполнения в реальном времени
     */
    @GetMapping(value = "/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecution(@PathVariable String executionId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 минут таймаут
        emitters.put(executionId, emitter);

        emitter.onCompletion(() -> emitters.remove(executionId));
        emitter.onTimeout(() -> emitters.remove(executionId));
        emitter.onError((ex) -> emitters.remove(executionId));

        // Начинаем отправку обновлений
        scheduler.scheduleAtFixedRate(() -> {
            try {
                RunExecution execution = runnerService.getExecution(executionId);
                if (execution != null) {
                    emitter.send(SseEmitter.event()
                        .name("update")
                        .data(execution));
                    
                    // Если выполнение завершено, закрываем соединение
                    if (execution.getStatus() == RunExecution.RunStatus.COMPLETED ||
                        execution.getStatus() == RunExecution.RunStatus.FAILED) {
                        emitter.complete();
                        emitters.remove(executionId);
                    }
                } else {
                    emitter.complete();
                    emitters.remove(executionId);
                }
            } catch (IOException e) {
                log.error("Error sending SSE update", e);
                emitter.completeWithError(e);
                emitters.remove(executionId);
            }
        }, 0, 500, TimeUnit.MILLISECONDS); // Обновление каждые 500мс

        return emitter;
    }

    @Data
    @AllArgsConstructor
    public static class StartRunResponse {
        private String runId;
    }
}

