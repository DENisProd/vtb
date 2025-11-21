package ru.poib.VTBHack.aiqueue.model;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;
import ru.poib.VTBHack.mapping.model.AIVerificationReport;

@Data
public class AiAnalysisJob {
    public enum Status { QUEUED, RUNNING, COMPLETED, ERROR }

    private String id = UUID.randomUUID().toString();
    private Status status = Status.QUEUED;
    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
    private String bpmnXml;
    private String openApiJson;
    private AIVerificationReport result;
    private String errorMessage;
    private Integer modelId;
    private String modelName;
    private String projectId;
}