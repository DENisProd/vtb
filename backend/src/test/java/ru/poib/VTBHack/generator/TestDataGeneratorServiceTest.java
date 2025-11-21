package ru.poib.VTBHack.generator;

import org.junit.jupiter.api.Test;
import ru.poib.VTBHack.generator.model.TestDataGenerationRequest;
import ru.poib.VTBHack.generator.model.GenerationType;
import ru.poib.VTBHack.generator.model.TestDataGenerationResult;
import ru.poib.VTBHack.generator.service.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestDataGeneratorServiceTest {

    private final RussianDataGenerator russian = new RussianDataGenerator();
    private final SmartFieldGenerator smart = new SmartFieldGenerator(russian);
    private final SchemaDataGenerator schema = new SchemaDataGenerator(smart);
    private final ClassicDataGenerator classic = new ClassicDataGenerator(schema, smart);
    private final AIDataGenerator ai = new AIDataGenerator(classic);
    private final TestDataGeneratorService service = new TestDataGeneratorService(classic, ai);

    @Test
    void generateClassicWithNullMappingProducesVariants() {
        TestDataGenerationRequest req = new TestDataGenerationRequest();
        req.setGenerationType(GenerationType.CLASSIC);
        req.setMappingResult(null);
        req.setOpenApiModel(null);
        req.setVariantsCount(2);

        TestDataGenerationResult result = service.generateTestData(req);

        assertNotNull(result);
        assertEquals(2, result.getVariants().size(), "Should produce requested number of variants (even if empty steps)");
        assertNotNull(result.getStatistics());
        assertEquals(0, result.getStatistics().getTotalFieldsGenerated());
    }

    @Test
    void generateAiDelegatesToClassic() {
        TestDataGenerationRequest req = new TestDataGenerationRequest();
        req.setGenerationType(GenerationType.AI);
        req.setMappingResult(null);
        req.setOpenApiModel(null);
        req.setVariantsCount(1);

        TestDataGenerationResult result = service.generateTestData(req);
        assertNotNull(result);
        assertEquals(1, result.getVariants().size());
    }
}
