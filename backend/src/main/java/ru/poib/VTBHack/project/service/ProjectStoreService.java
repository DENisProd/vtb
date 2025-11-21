package ru.poib.VTBHack.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.project.model.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectStoreService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path baseDir;

    public ProjectStoreService() {
        String cwd = System.getProperty("user.dir");
        this.baseDir = Paths.get(cwd).resolve("data").resolve("projects");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException ignored) {}
    }

    public Project create(String name, String bpmnXml, String openApiJson, String pumlContent) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        Project p = new Project(id, name, now, now, bpmnXml, openApiJson, pumlContent, null);
        save(p);
        return p;
    }

    public void save(Project p) {
        p.setUpdatedAt(Instant.now().toString());
        Path file = baseDir.resolve(p.getId() + ".json");
        try {
            byte[] data = objectMapper.writeValueAsBytes(p);
            Files.write(file, data);
        } catch (IOException ignored) {}
    }

    public Project get(String id) {
        Path file = baseDir.resolve(id + ".json");
        if (!Files.exists(file)) return null;
        try {
            byte[] data = Files.readAllBytes(file);
            return objectMapper.readValue(data, Project.class);
        } catch (IOException e) {
            return null;
        }
    }

    public List<Project> list() {
        List<Project> res = new ArrayList<>();
        try {
            Files.list(baseDir).filter(p -> p.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    byte[] data = Files.readAllBytes(path);
                    Project pr = objectMapper.readValue(data, Project.class);
                    res.add(pr);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
        return res;
    }
}