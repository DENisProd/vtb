package ru.poib.VTBHack.aiqueue.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.aiqueue.model.AiAnalysisJob;
import ru.poib.VTBHack.aiqueue.repo.AiAnalysisJobRepository;
import ru.poib.VTBHack.aiqueue.service.AiJobStoreService;
import ru.poib.VTBHack.mapping.model.AIVerificationReport;
import ru.poib.VTBHack.mapping.service.AIVerificationService;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class AiAnalysisQueueService {
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Map<String, AiAnalysisJob> jobs = new ConcurrentHashMap<>();
    private final AIVerificationService aiVerificationService;
    private final AiAnalysisJobRepository repository;
    private final AiJobStoreService fileStore;

    public AiAnalysisQueueService(AIVerificationService aiVerificationService, AiAnalysisJobRepository repository, AiJobStoreService fileStore) {
        this.aiVerificationService = aiVerificationService;
        this.repository = repository;
        this.fileStore = fileStore;

        Thread worker = new Thread(this::processLoop, "ai-analysis-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public AiAnalysisJob enqueue(String openApiJson, String bpmnXml, String modelName, String projectId) {
        AiAnalysisJob job = new AiAnalysisJob();
        job.setOpenApiJson(openApiJson);
        job.setBpmnXml(bpmnXml);
        job.setModelName(modelName);
        job.setProjectId(projectId);
        jobs.put(job.getId(), job);
        safeSave(job);
        safeFileSave(job);
        queue.offer(job.getId());
        log.info("Enqueued AI analysis job {}", job.getId());
        return job;
    }

    public AiAnalysisJob getJob(String jobId) {
        AiAnalysisJob inMem = jobs.get(jobId);
        if (inMem != null) return inMem;
        try {
            var db = repository.findById(jobId).orElse(null);
            if (db != null) return db;
        } catch (Exception ignored) {}
        try {
            var fs = fileStore.get(jobId);
            if (fs != null) return fs;
        } catch (Exception ignored) {}
        return null;
    }

    public java.util.List<AiAnalysisJob> listByProject(String projectId) {
        // 1) Попытка получить из БД
        try {
            java.util.List<AiAnalysisJob> db = repository.findByProjectIdOrderByCreatedAtDesc(projectId);
            if (db != null && !db.isEmpty()) {
                return db;
            }
        } catch (Exception ignored) {}

        // 2) Фолбэк: ин‑мемори
        java.util.List<AiAnalysisJob> fromMem = new java.util.ArrayList<>();
        for (AiAnalysisJob j : jobs.values()) {
            if (projectId != null && projectId.equals(j.getProjectId())) {
                fromMem.add(j);
            }
        }
        fromMem.sort((a,b) -> {
            var ta = a.getCreatedAt();
            var tb = b.getCreatedAt();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        if (!fromMem.isEmpty()) return fromMem;

        // 3) Фолбэк: файловое хранилище
        try {
            java.util.List<AiAnalysisJob> fs = fileStore.listByProject(projectId);
            if (fs != null) return fs;
        } catch (Exception ignored) {}

        return java.util.Collections.emptyList();
    }

    private void processLoop() {
        while (true) {
            try {
                String jobId = queue.take();
                AiAnalysisJob job = jobs.get(jobId);
                if (job == null) continue;

                job.setStatus(AiAnalysisJob.Status.RUNNING);
                job.setStartedAt(Instant.now());
                safeSave(job);
                safeFileSave(job);
                try {
                    AIVerificationReport report = job.getModelName() != null
                            ? aiVerificationService.verifyFilesWithModel(job.getOpenApiJson(), job.getBpmnXml(), job.getModelName())
                            : aiVerificationService.verifyFiles(job.getOpenApiJson(), job.getBpmnXml());
                    job.setResult(report);
                    job.setStatus(AiAnalysisJob.Status.COMPLETED);
                    job.setFinishedAt(Instant.now());
                    safeSave(job);
                    safeFileSave(job);
                    log.info("AI job {} completed", jobId);
                } catch (Exception e) {
                    job.setStatus(AiAnalysisJob.Status.ERROR);
                    job.setErrorMessage(e.getMessage());
                    job.setFinishedAt(Instant.now());
                    safeSave(job);
                    safeFileSave(job);
                    log.error("AI job {} failed", jobId, e);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                log.error("AI worker unexpected error", t);
            }
        }
    }

    private void safeSave(AiAnalysisJob job) {
        try {
            repository.save(job);
        } catch (Exception ignored) {}
    }

    private void safeFileSave(AiAnalysisJob job) {
        try {
            fileStore.save(job);
        } catch (Exception ignored) {}
    }
}