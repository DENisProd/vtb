package ru.poib.VTBHack.runner.repo;

import org.springframework.stereotype.Repository;
import ru.poib.VTBHack.runner.model.RunExecution;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class RunExecutionRepository {
    private final Map<String, RunExecution> storage = new ConcurrentHashMap<>();

    public Optional<RunExecution> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public RunExecution save(RunExecution execution) {
        if (execution.getId() == null) {
            execution.setId(UUID.randomUUID().toString());
        }
        storage.put(execution.getId(), execution);
        return execution;
    }

    public List<RunExecution> findByProjectIdOrderByCreatedAtDesc(String projectId) {
        return storage.values().stream()
                .filter(exec -> projectId != null && projectId.equals(exec.getProjectId()))
                .sorted((a, b) -> {
                    var ta = a.getCreatedAt();
                    var tb = b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta); // descending order
                })
                .collect(Collectors.toList());
    }

    public List<RunExecution> findByScenarioIdOrderByCreatedAtDesc(String scenarioId) {
        return storage.values().stream()
                .filter(exec -> scenarioId != null && scenarioId.equals(exec.getScenarioId()))
                .sorted((a, b) -> {
                    var ta = a.getCreatedAt();
                    var tb = b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta); // descending order
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


