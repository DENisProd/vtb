package ru.poib.VTBHack.aiqueue.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.poib.VTBHack.aiqueue.model.AiAnalysisJob;
import ru.poib.VTBHack.aiqueue.service.AiAnalysisQueueService;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class AIVerificationController {
    private final AiAnalysisQueueService queueService;

    @PostMapping("/verify")
    public ResponseEntity<JobResponse> startVerification(@RequestParam String bpmnXml,
                                                         @RequestParam String openApiJson,
                                                         @RequestParam(required = false) Integer modelId,
                                                         @RequestParam(required = false) String projectId) {
        String modelName = resolveModelName(modelId);
        AiAnalysisJob job = queueService.enqueue(openApiJson, bpmnXml, modelName, projectId);
        job.setModelId(modelId);
        job.setModelName(modelName);
        return ResponseEntity.ok(new JobResponse(job.getId()));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getStatus(@PathVariable String jobId) {
        AiAnalysisJob job = queueService.getJob(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        JobStatusResponse resp = new JobStatusResponse();
        resp.status = job.getStatus().name().toLowerCase();
        resp.result = job.getResult();
        resp.error = job.getErrorMessage();
        resp.createdAt = job.getCreatedAt();
        resp.startedAt = job.getStartedAt();
        resp.finishedAt = job.getFinishedAt();
        resp.modelName = job.getModelName();
        resp.projectId = job.getProjectId();
        return ResponseEntity.ok(resp);
    }

    @Data
    public static class JobResponse { private final String jobId; }

    @Data
    public static class JobStatusResponse {
        public String status;
        public Object result;
        public String error;
        public java.time.Instant createdAt;
        public java.time.Instant startedAt;
        public java.time.Instant finishedAt;
        public String modelName;
        public String projectId;
    }

    @GetMapping("/jobs")
    public ResponseEntity<JobsListResponse> listJobs(@RequestParam String projectId) {
        var jobs = queueService.listByProject(projectId);
        return ResponseEntity.ok(new JobsListResponse(jobs));
    }

    @Data
    public static class JobsListResponse { public final java.util.List<AiAnalysisJob> jobs; }

    @GetMapping("/models")
    public ResponseEntity<ModelList> getModels() {
        return ResponseEntity.ok(new ModelList(getAvailableModels()));
    }

    private String resolveModelName(Integer modelId) {
        if (modelId == null) return null;
        var models = getAvailableModels();
        return models.stream().filter(m -> m.id.equals(modelId)).map(m -> m.name).findFirst().orElse(null);
    }

    private java.util.List<ModelInfo> getAvailableModels() {
        java.util.List<ModelInfo> list = new java.util.ArrayList<>();
        list.add(new ModelInfo(2, "Qwen/Qwen2.5-1.5B-Instruct"));
        return list;
    }

    @Data
    public static class ModelInfo { public final Integer id; public final String name; }

    @Data
    public static class ModelList { public final java.util.List<ModelInfo> models; }
}