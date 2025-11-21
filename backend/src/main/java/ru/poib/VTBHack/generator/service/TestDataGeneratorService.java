package ru.poib.VTBHack.generator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.generator.model.GenerationType;
import ru.poib.VTBHack.generator.model.TestDataGenerationRequest;
import ru.poib.VTBHack.generator.model.TestDataGenerationResult;
import ru.poib.VTBHack.generator.model.TestDataStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Основной сервис для генерации тестовых данных
 * Поддерживает выбор типа генерации: классическая или ИИ
 */
@Service
public class TestDataGeneratorService {
    private final ClassicDataGenerator classicDataGenerator;
    private final AIDataGenerator aiDataGenerator;
    
    @Autowired
    public TestDataGeneratorService(ClassicDataGenerator classicDataGenerator,
                                   AIDataGenerator aiDataGenerator) {
        this.classicDataGenerator = classicDataGenerator;
        this.aiDataGenerator = aiDataGenerator;
    }
    
    /**
     * Генерирует тестовые данные на основе запроса
     */
    public TestDataGenerationResult generateTestData(TestDataGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        
        GenerationType generationType = request.getGenerationType() != null 
            ? request.getGenerationType() 
            : GenerationType.CLASSIC;
        
        String scenario = request.getScenario() != null ? request.getScenario() : "positive";
        int variantsCount = request.getVariantsCount() > 0 ? request.getVariantsCount() : 1;
        
        List<List<TestDataStep>> variants = new ArrayList<>();
        int totalFieldsGenerated = 0;
        int smartFieldsGenerated = 0;
        int exampleBasedFields = 0;
        
        // Генерируем указанное количество вариантов
        for (int i = 0; i < variantsCount; i++) {
            List<TestDataStep> variant;
            
            // Выбираем генератор в зависимости от типа
            if (generationType == GenerationType.AI) {
                variant = aiDataGenerator.generateTestData(
                    request.getMappingResult(),
                    request.getOpenApiModel(),
                    scenario
                );
            } else {
                variant = classicDataGenerator.generateTestData(
                    request.getMappingResult(),
                    request.getOpenApiModel(),
                    scenario
                );
            }
            
            variants.add(variant);
            
            // Подсчитываем статистику
            for (TestDataStep step : variant) {
                if (step.getRequestData() != null) {
                    totalFieldsGenerated += step.getRequestData().size();
                }
                if (step.getResponseData() != null) {
                    totalFieldsGenerated += step.getResponseData().size();
                }
            }
        }
        
        // Извлекаем зависимости между шагами
        Map<String, String> crossStepDependencies = extractCrossStepDependencies(variants.isEmpty() ? 
            new ArrayList<>() : variants.get(0));
        
        long endTime = System.currentTimeMillis();
        long generationTimeMs = endTime - startTime;
        
        // Создаем статистику
        TestDataGenerationResult.GenerationStatistics statistics = 
            new TestDataGenerationResult.GenerationStatistics(
                totalFieldsGenerated,
                smartFieldsGenerated,
                exampleBasedFields,
                generationTimeMs,
                95.0 // Оценка валидности данных
            );
        
        // Создаем результат
        TestDataGenerationResult result = new TestDataGenerationResult();
        result.setGenerationType(generationType);
        result.setScenario(scenario);
        result.setVariants(variants);
        result.setCrossStepDependencies(crossStepDependencies);
        result.setStatistics(statistics);
        
        return result;
    }
    
    /**
     * Извлекает зависимости между шагами
     */
    private Map<String, String> extractCrossStepDependencies(List<TestDataStep> steps) {
        Map<String, String> dependencies = new HashMap<>();
        
        for (TestDataStep step : steps) {
            if (step.getDataDependencies() != null) {
                for (Map.Entry<String, String> dep : step.getDataDependencies().entrySet()) {
                    String key = step.getTaskId() + "." + dep.getKey();
                    dependencies.put(key, dep.getValue());
                }
            }
        }
        
        return dependencies;
    }
}


