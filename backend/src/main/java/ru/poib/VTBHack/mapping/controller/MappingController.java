package ru.poib.VTBHack.mapping.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.mapping.service.MappingService;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.service.BpmnParserService;
import ru.poib.VTBHack.parser.service.OpenApiParserService;

/**
 * REST контроллер для модуля сопоставления
 */
@Slf4j
@RestController
@RequestMapping("/api/mapping")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class MappingController {
    
    private final MappingService mappingService;
    private final BpmnParserService bpmnParserService;
    private final OpenApiParserService openApiParserService;
    private final ru.poib.VTBHack.route.service.RouteService routeService;
    
    /**
     * Сопоставляет BPMN процесс с OpenAPI спецификацией
     */
    @PostMapping("/map")
    public ResponseEntity<MappingResult> mapProcessToApi(
            @RequestParam String bpmnXml,
            @RequestParam String openApiJson) {
        long requestStartTime = System.currentTimeMillis();
        log.info("Received mapping request: BPMN size={} bytes, OpenAPI size={} bytes", 
            bpmnXml != null ? bpmnXml.length() : 0,
            openApiJson != null ? openApiJson.length() : 0);
        
        try {
            long parseStartTime = System.currentTimeMillis();
            log.debug("Parsing BPMN and OpenAPI...");
            ProcessModel processModel = bpmnParserService.parse(bpmnXml);
            OpenApiModel openApiModel = openApiParserService.parseOpenApi(openApiJson);
            log.debug("Parsing completed in {}ms", System.currentTimeMillis() - parseStartTime);
            
            // Передаем исходные строки для AI проверки и полного анализа
            MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel, openApiJson, bpmnXml);
            routeService.saveMapping(result);
            
            long totalDuration = System.currentTimeMillis() - requestStartTime;
            log.info("Mapping request completed successfully in {}ms", totalDuration);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - requestStartTime;
            log.error("Mapping request failed after {}ms", totalDuration, e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Получает рекомендации для несопоставленных задач
     * Использует POST метод для поддержки больших BPMN и OpenAPI файлов
     */
    @PostMapping("/recommendations")
    public ResponseEntity<MappingResult> getRecommendations(
            @RequestParam String bpmnXml,
            @RequestParam String openApiJson) {
        try {
            ProcessModel processModel = bpmnParserService.parse(bpmnXml);
            OpenApiModel openApiModel = openApiParserService.parseOpenApi(openApiJson);

            // Передаем исходные строки для проверки ИИ
            MappingResult result = mappingService.mapProcessToEndpoints(processModel, openApiModel, openApiJson, bpmnXml);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}


