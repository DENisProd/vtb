package ru.poib.VTBHack.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;

/**
 * Запрос на генерацию тестовых данных
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDataGenerationRequest {
    /**
     * Тип генерации (CLASSIC или AI)
     */
    private GenerationType generationType;
    
    /**
     * Результат маппинга процесса с эндпоинтами
     */
    private MappingResult mappingResult;
    
    /**
     * OpenAPI спецификация
     */
    private OpenApiModel openApiModel;
    
    /**
     * Сценарий генерации (positive, negative, edge_case)
     */
    private String scenario = "positive";
    
    /**
     * Количество вариантов данных для генерации
     */
    private int variantsCount = 1;
}


