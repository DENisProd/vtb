package ru.poib.VTBHack.generator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.AllArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.poib.VTBHack.generator.model.GenerationType;
import ru.poib.VTBHack.generator.model.TestDataGenerationRequest;
import ru.poib.VTBHack.generator.model.TestDataGenerationResult;
import ru.poib.VTBHack.generator.service.TestDataGeneratorService;

/**
 * REST контроллер для генерации тестовых данных
 */
@RestController
@RequestMapping("/api/generator")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class TestDataGeneratorController {
    private final TestDataGeneratorService generatorService;
    private static final Logger log = LoggerFactory.getLogger(TestDataGeneratorController.class);
    
    /**
     * Генерирует тестовые данные
     * 
     * @param request запрос на генерацию данных
     * @return результат генерации
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateTestData(
            @RequestBody TestDataGenerationRequest request) {
        try {
            System.out.println("Received generation request: " + request);
            TestDataGenerationResult result = generatorService.generateTestData(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // Логируем и возвращаем диагностируемое сообщение
            log.error("Ошибка генерации тестовых данных", e);
            return ResponseEntity.status(500).body(
                java.util.Map.of(
                    "error", "GENERATION_FAILED",
                    "message", e.getMessage() != null ? e.getMessage() : "Internal Server Error"
                )
            );
        }
    }
    
    /**
     * Получает доступные типы генерации
     */
    @GetMapping("/types")
    public ResponseEntity<GenerationType[]> getGenerationTypes() {
        return ResponseEntity.ok(GenerationType.values());
    }
}


