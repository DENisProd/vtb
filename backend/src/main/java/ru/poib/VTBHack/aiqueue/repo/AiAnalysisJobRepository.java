package ru.poib.VTBHack.aiqueue.repo;

import org.springframework.stereotype.Repository;
import ru.poib.VTBHack.aiqueue.model.AiAnalysisJob;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class AiAnalysisJobRepository {
    private final Map<String, AiAnalysisJob> storage = new ConcurrentHashMap<>();

    public Optional<AiAnalysisJob> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public AiAnalysisJob save(AiAnalysisJob job) {
        if (job.getId() == null) {
            job.setId(UUID.randomUUID().toString());
        }
        storage.put(job.getId(), job);
        return job;
    }

    public List<AiAnalysisJob> findByProjectIdOrderByCreatedAtDesc(String projectId) {
        return storage.values().stream()
                .filter(job -> projectId != null && projectId.equals(job.getProjectId()))
                .sorted((a, b) -> {
                    Instant ta = a.getCreatedAt();
                    Instant tb = b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                })
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        storage.remove(id);
    }

    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}