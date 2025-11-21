package ru.poib.VTBHack.runner.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import ru.poib.VTBHack.execution.model.TestExecutionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class RunExecution {
    public enum RunStatus {
        QUEUED("queued"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String value;

        RunStatus(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    private String id = UUID.randomUUID().toString();
    private String scenarioId;
    private String projectId;
    private RunStatus status = RunStatus.QUEUED;
    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
    private double progress = 0.0;
    private int parallelism = 1;
    
    // Детали выполнения
    private TestExecutionResult executionResult;
    
    // Шаги выполнения
    private List<StepExecution> steps = new ArrayList<>();
    
    // Логи
    private List<LogEntry> logs = new ArrayList<>();
    
    // AI анализ (после завершения)
    private String aiAnalysisJobId;
    private String aiAnalysisResult;

    @Data
    public static class StepExecution {
        private String stepId;
        private String taskId;
        private String taskName;
        private StepStatus status = StepStatus.PENDING;
        
        public enum StepStatus {
            PENDING("pending"),
            RUNNING("running"),
            SUCCESS("success"),
            FAILED("failed");

            private final String value;

            StepStatus(String value) {
                this.value = value;
            }

            @JsonValue
            public String getValue() {
                return value;
            }
        }
        private Instant startedAt;
        private Instant finishedAt;
        private Long durationMs;
        private String errorMessage;
        private StepRequest request;
        private StepResponse response;
    }

    @Data
    public static class StepRequest {
        private String method;
        private String url;
        private java.util.Map<String, String> headers;
        private String body;
        private Instant timestamp;
    }

    @Data
    public static class StepResponse {
        private Integer statusCode;
        private java.util.Map<String, String> headers;
        private String body;
        private Long responseTimeMs;
        private Instant timestamp;
    }

    @Data
    public static class LogEntry {
        private String id = UUID.randomUUID().toString();
        private String level; // debug, info, warn, error
        private String message;
        private String timestamp = Instant.now().toString();
        private String stepId;
        private Object payloadPreview;
    }
}

