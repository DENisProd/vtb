package ru.poib.VTBHack.generator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.poib.VTBHack.generator.model.TestDataStep;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;

import java.util.List;

/**
 * ИИ-генератор тестовых данных (заглушка)
 * Пока что вызывает классический метод генерации
 */
@Component
public class AIDataGenerator {
    private final ClassicDataGenerator classicDataGenerator;
    
    @Autowired
    public AIDataGenerator(ClassicDataGenerator classicDataGenerator) {
        this.classicDataGenerator = classicDataGenerator;
    }
    
    /**
     * Генерирует тестовые данные с использованием ИИ
     * TODO: В будущем здесь будет интеграция с GPT-подобными моделями
     * 
     * @param mappingResult результат маппинга процесса с эндпоинтами
     * @param openApiModel OpenAPI спецификация
     * @param scenario сценарий генерации
     * @return список тестовых данных для всех шагов
     */
    public List<TestDataStep> generateTestData(MappingResult mappingResult, 
                                                OpenApiModel openApiModel,
                                                String scenario) {
        // Заглушка: пока что используем классическую генерацию
        // В будущем здесь будет:
        // 1. Анализ контекста бизнес-процесса
        // 2. Генерация связанных данных через ИИ (заказ → товары)
        // 3. Генерация реалистичных текстов на русском языке
        // 4. Использование ONNX Runtime + GPT-2/GPT-J для текстовой генерации
        
        return classicDataGenerator.generateTestData(mappingResult, openApiModel, scenario);
    }
}


