package ru.poib.VTBHack.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.poib.VTBHack.generator.model.TestDataGenerationResult;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.parser.model.ProcessModel;

/**
 * Запрос на выполнение тестов
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestExecutionRequest {
    /**
     * Модель процесса (BPMN)
     */
    private ProcessModel processModel;
    
    /**
     * Результат маппинга процесса на API
     */
    private MappingResult mappingResult;
    
    /**
     * Сгенерированные тестовые данные
     */
    private TestDataGenerationResult testData;
    
    /**
     * Конфигурация окружения
     */
    private ExecutionConfig config;
    
    /**
     * Индекс варианта тестовых данных для выполнения (по умолчанию 0)
     */
    private int testDataVariantIndex = 0;
    
    /**
     * Флаг для остановки выполнения при первой ошибке (по умолчанию false)
     */
    private boolean stopOnFirstError = false;
}

