package ru.poib.VTBHack.aiqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.aiqueue.model.AiAnalysisJob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiJobStoreService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path baseDir;

    public AiJobStoreService() {
        String cwd = System.getProperty("user.dir");
        this.baseDir = Paths.get(cwd).resolve("data").resolve("ai-jobs");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException ignored) {}
    }

    public void save(AiAnalysisJob job) {
        String projectId = job.getProjectId() != null ? job.getProjectId() : "_unknown";
        Path dir = baseDir.resolve(projectId);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        Path file = dir.resolve(job.getId() + ".json");
        try {
            byte[] data = objectMapper.writeValueAsBytes(job);
            Files.write(file, data);
        } catch (IOException ignored) {}
    }

    public AiAnalysisJob get(String jobId) {
        try {
            // Поиск по всем проектам
            if (!Files.exists(baseDir)) return null;
            for (Path dir : Files.newDirectoryStream(baseDir)) {
                if (Files.isDirectory(dir)) {
                    Path file = dir.resolve(jobId + ".json");
                    if (Files.exists(file)) {
                        byte[] data = Files.readAllBytes(file);
                        return objectMapper.readValue(data, AiAnalysisJob.class);
                    }
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    public List<AiAnalysisJob> listByProject(String projectId) {
        List<AiAnalysisJob> res = new ArrayList<>();
        Path dir = baseDir.resolve(projectId);
        try {
            if (Files.exists(dir)) {
                Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".json")).forEach(path -> {
                    try {
                        byte[] data = Files.readAllBytes(path);
                        AiAnalysisJob job = objectMapper.readValue(data, AiAnalysisJob.class);
                        res.add(job);
                    } catch (IOException ignored) {}
                });
            }
        } catch (IOException ignored) {}
        // Сортировка по времени создания (новые сверху)
        res.sort((a,b) -> {
            var ta = a.getCreatedAt();
            var tb = b.getCreatedAt();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return res;
    }
}