package ru.poib.VTBHack.project.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.mapping.service.MappingService;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.service.BpmnParserService;
import ru.poib.VTBHack.parser.service.OpenApiParserService;
import ru.poib.VTBHack.project.model.Project;
import ru.poib.VTBHack.project.service.ProjectStoreService;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class ProjectController {
    private final ProjectStoreService store;
    private final MappingService mappingService;
    private final BpmnParserService bpmnParserService;
    private final OpenApiParserService openApiParserService;

    @GetMapping
    public ResponseEntity<List<Project>> list() {
        return ResponseEntity.ok(store.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> get(@PathVariable String id) {
        Project p = store.get(id);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(p);
    }

    @PostMapping
    public ResponseEntity<Project> create(@RequestParam String name,
                                          @RequestParam String bpmnXml,
                                          @RequestParam String openApiJson,
                                          @RequestParam(required = false) String pumlContent) {
        Project p = store.create(name, bpmnXml, openApiJson, pumlContent);
        try {
            ProcessModel processModel = bpmnParserService.parse(bpmnXml);
            OpenApiModel openApiModel = openApiParserService.parseOpenApi(openApiJson);
            MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel, openApiJson, bpmnXml);
            p.setMappingResult(result);
            store.save(p);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(p);
    }

    @PostMapping("/{id}/remap")
    public ResponseEntity<Project> remap(@PathVariable String id,
                                         @RequestParam(required = false) String bpmnXml,
                                         @RequestParam(required = false) String openApiJson,
                                         @RequestParam(required = false) String pumlContent) {
        Project p = store.get(id);
        if (p == null) return ResponseEntity.notFound().build();
        String bpmn = bpmnXml != null ? bpmnXml : p.getBpmnXml();
        String openapi = openApiJson != null ? openApiJson : p.getOpenApiJson();
        String puml = pumlContent != null ? pumlContent : p.getPumlContent();
        p.setBpmnXml(bpmn);
        p.setOpenApiJson(openapi);
        p.setPumlContent(puml);
        try {
            ProcessModel processModel = bpmnParserService.parse(bpmn);
            OpenApiModel openApiModel = openApiParserService.parseOpenApi(openapi);
            MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel, openapi, bpmn);
            p.setMappingResult(result);
            store.save(p);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(p);
    }
}