package ru.poib.VTBHack.parser.service;

import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.ProcessTask;
import ru.poib.VTBHack.parser.model.puml.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit тесты для PlantUmlParserService
 */
class PlantUmlParserServiceTest {

    private PlantUmlParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new PlantUmlParserService();
    }

    @Test
    @DisplayName("Парсинг базовой sequence диаграммы")
    void testParseBasicSequenceDiagram() {
        String plantUml = """
            @startuml
            actor Client as C
            participant "API Bank" as Bank
            
            C -> Bank: POST /auth/token (client_id, client_secret)
            activate Bank
            Bank -> C: access_token
            deactivate Bank
            
            C -> Bank: GET /accounts
            activate Bank
            Bank -> C: list of accounts
            deactivate Bank
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        assertNotNull(model);
        assertEquals(2, model.getParticipants().size());
        assertTrue(model.getParticipants().containsKey("C"));
        assertTrue(model.getParticipants().containsKey("Bank"));

        // Проверяем участников
        Participant client = model.getParticipants().get("C");
        assertEquals("Client", client.getName());
        assertEquals(ParticipantType.ACTOR, client.getType());

        Participant bank = model.getParticipants().get("Bank");
        assertEquals("API Bank", bank.getName());
        assertEquals(ParticipantType.SYSTEM, bank.getType());

        // Проверяем взаимодействия
        assertEquals(4, model.getInteractions().size());

        // Первое взаимодействие
        Interaction first = model.getInteractions().get(0);
        assertEquals("Client", first.getFrom().getName());
        assertEquals("API Bank", first.getTo().getName());
        assertTrue(first.getMessage().contains("POST /auth/token"));

        ApiCallInfo apiInfo = first.getApiCallInfo();
        assertNotNull(apiInfo);
        assertTrue(apiInfo.isRequest());
        assertEquals("POST", apiInfo.getMethod());
        assertEquals("/auth/token", apiInfo.getPath());
        assertEquals(2, apiInfo.getParameters().size());
        assertTrue(apiInfo.getParameters().contains("client_id"));
        assertTrue(apiInfo.getParameters().contains("client_secret"));
    }

    @Test
    @DisplayName("Парсинг полной bonus_payment диаграммы")
    void testParseBonusPaymentDiagram() {
        String plantUml = """
            @startuml
            actor Client as C
            participant "API Bank" as Bank
            
            C -> Bank: POST /auth/bank-token (client_id, client_secret)
            activate Bank
            Bank -> C: access_token
            deactivate Bank
            
            C -> Bank: GET /accounts
            activate Bank
            Bank -> C: list of accounts
            deactivate Bank
            
            C -> Bank: GET /accounts/{bonus_account_id}/balances
            activate Bank
            Bank -> C: balance
            deactivate Bank
            
            C -> Bank: POST /payments (bonus account, service provider)
            activate Bank
            Bank -> C: payment_id
            deactivate Bank
            
            C -> Bank: GET /payments/{payment_id}
            activate Bank
            Bank -> C: status: completed
            deactivate Bank
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        assertNotNull(model);
        assertEquals(2, model.getParticipants().size());

        // Считаем только request (не response)
        long apiCalls = model.getInteractions().stream()
                .filter(i -> i.getApiCallInfo() != null && i.getApiCallInfo().isRequest())
                .count();
        assertEquals(5, apiCalls);

        // Проверяем ProcessModel
        ProcessModel processModel = model.getProcessModel();
        assertNotNull(processModel);
        assertEquals(5, processModel.getTasks().size());

        // Проверяем первую задачу
        ProcessTask firstTask = processModel.getTasks().get(0);
        assertNotNull(firstTask.getApiEndpointInfo());
        assertEquals("POST", firstTask.getApiEndpointInfo().getMethod());
        assertEquals("/auth/bank-token", firstTask.getApiEndpointInfo().getPath());
    }

    @Test
    @DisplayName("Извлечение API endpoints")
    void testExtractApiEndpoints() {
        String plantUml = """
            @startuml
            actor Client as C
            participant "API" as API
            
            C -> API: GET /users
            API -> C: user list
            
            C -> API: POST /users (name, email)
            API -> C: user_id
            
            C -> API: DELETE /users/{id}
            API -> C: success
            @enduml
            """;

        List<ApiEndpointSummary> endpoints = parserService.extractApiEndpoints(plantUml);

        assertEquals(3, endpoints.size());

        ApiEndpointSummary first = endpoints.get(0);
        assertEquals("GET", first.getMethod());
        assertEquals("/users", first.getPath());
        assertEquals("Client", first.getSource());
        assertEquals("API", first.getTarget());

        ApiEndpointSummary second = endpoints.get(1);
        assertEquals("POST", second.getMethod());
        assertEquals("/users", second.getPath());
        assertEquals(2, second.getParameters().size());

        ApiEndpointSummary third = endpoints.get(2);
        assertEquals("DELETE", third.getMethod());
        assertEquals("/users/{id}", third.getPath());
    }

    @Test
    @DisplayName("Валидация корректной диаграммы")
    void testValidateCorrectDiagram() {
        String plantUml = """
            @startuml
            actor Client as C
            participant "API" as API
            
            C -> API: GET /test
            API -> C: result
            @enduml
            """;

        ValidationResult result = parserService.validate(plantUml);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Валидация диаграммы без @startuml")
    void testValidateMissingStartTag() {
        String plantUml = """
            actor Client as C
            participant "API" as API
            
            C -> API: GET /test
            @enduml
            """;

        ValidationResult result = parserService.validate(plantUml);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("@startuml")));
    }

    @Test
    @DisplayName("Валидация диаграммы без взаимодействий")
    void testValidateNoInteractions() {
        String plantUml = """
            @startuml
            actor Client as C
            participant "API" as API
            @enduml
            """;

        ValidationResult result = parserService.validate(plantUml);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("взаимодействий")));
    }

    @Test
    @DisplayName("Валидация пустого контента")
    void testValidateEmptyContent() {
        ValidationResult result = parserService.validate("");

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("пуст")));
    }

    @Test
    @DisplayName("Статистика по диаграмме")
    void testGetStatistics() {
        String plantUml = """
            @startuml
            actor Client as C
            participant "API" as API
            
            C -> API: GET /users
            API -> C: user list
            
            C -> API: POST /users (name)
            API -> C: user_id
            
            C -> API: PUT /users/{id} (name)
            API -> C: success
            
            C -> API: DELETE /users/{id}
            API -> C: success
            
            note left: This is a note
            @enduml
            """;

        DiagramStatistics stats = parserService.getStatistics(plantUml);

        assertEquals(2, stats.getParticipantCount());
        assertEquals(8, stats.getInteractionCount());
        assertEquals(4, stats.getApiCallCount());
        assertEquals(4, stats.getResponseCount());
        assertEquals(1, stats.getNoteCount());

        Map<String, Integer> methodDist = stats.getMethodDistribution();
        assertEquals(1, methodDist.get("GET"));
        assertEquals(1, methodDist.get("POST"));
        assertEquals(1, methodDist.get("PUT"));
        assertEquals(1, methodDist.get("DELETE"));
    }

    @Test
    @DisplayName("Парсинг различных типов стрелок")
    void testParseArrowTypes() {
        String plantUml = """
            @startuml
            actor A
            participant B
            
            A -> B: sync call
            A --> B: async call
            B --> A: async return
            A ->> B: another sync
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        assertEquals(4, model.getInteractions().size());
        assertEquals(ArrowType.SYNC, model.getInteractions().get(0).getArrowType());
        assertEquals(ArrowType.ASYNC, model.getInteractions().get(1).getArrowType());
        assertEquals(ArrowType.ASYNC, model.getInteractions().get(2).getArrowType());
        assertEquals(ArrowType.SYNC, model.getInteractions().get(3).getArrowType());
    }

    @Test
    @DisplayName("Парсинг участников без явного объявления")
    void testParseImplicitParticipants() {
        String plantUml = """
            @startuml
            A -> B: test message
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        assertEquals(2, model.getParticipants().size());
        assertTrue(model.getParticipants().containsKey("A"));
        assertTrue(model.getParticipants().containsKey("B"));

        // Неявные участники должны быть помечены как UNKNOWN
        assertEquals(ParticipantType.UNKNOWN, model.getParticipants().get("A").getType());
        assertEquals(ParticipantType.UNKNOWN, model.getParticipants().get("B").getType());
    }

    @Test
    @DisplayName("Парсинг API вызова без параметров")
    void testParseApiCallWithoutParameters() {
        String plantUml = """
            @startuml
            A -> B: GET /users
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        Interaction interaction = model.getInteractions().get(0);
        ApiCallInfo apiInfo = interaction.getApiCallInfo();

        assertNotNull(apiInfo);
        assertTrue(apiInfo.isRequest());
        assertEquals("GET", apiInfo.getMethod());
        assertEquals("/users", apiInfo.getPath());
        assertNull(apiInfo.getParameters());
    }

    @Test
    @DisplayName("Парсинг response сообщений")
    void testParseResponseMessages() {
        String plantUml = """
            @startuml
            A -> B: GET /data
            B -> A: data response
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        assertEquals(2, model.getInteractions().size());

        Interaction request = model.getInteractions().get(0);
        assertTrue(request.getApiCallInfo().isRequest());

        Interaction response = model.getInteractions().get(1);
        assertFalse(response.getApiCallInfo().isRequest());
        assertEquals("data response", response.getApiCallInfo().getResponseDescription());
    }

    @Test
    @DisplayName("Игнорирование комментариев")
    void testIgnoreComments() {
        String plantUml = """
            @startuml
            ' This is a comment
            actor Client as C
            /' Multi-line
               comment '/
            participant API
            
            C -> API: GET /test
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);

        assertEquals(2, model.getParticipants().size());
        assertEquals(1, model.getInteractions().size());
    }

    @Test
    @DisplayName("Парсинг path с параметрами")
    void testParsePathWithParameters() {
        String plantUml = """
            @startuml
            C -> API: GET /users/{id}/posts/{postId}
            C -> API: POST /api/v1/payments (amount, currency)
            @enduml
            """;

        List<ApiEndpointSummary> endpoints = parserService.extractApiEndpoints(plantUml);

        assertEquals(2, endpoints.size());
        assertEquals("/users/{id}/posts/{postId}", endpoints.get(0).getPath());
        assertEquals("/api/v1/payments", endpoints.get(1).getPath());
        assertEquals(2, endpoints.get(1).getParameters().size());
    }

    @Test
    @DisplayName("Sequence flows в ProcessModel")
    void testProcessModelSequenceFlows() {
        String plantUml = """
            @startuml
            A -> B: GET /step1
            A -> B: POST /step2
            A -> B: PUT /step3
            @enduml
            """;

        SequenceDiagramModel model = parserService.parse(plantUml);
        ProcessModel processModel = model.getProcessModel();

        assertEquals(3, processModel.getTasks().size());
        assertEquals(2, processModel.getSequenceFlows().size());

        // Проверяем, что задачи связаны последовательно
        ProcessTask task1 = processModel.getTasks().get(0);
        ProcessTask task2 = processModel.getTasks().get(1);
        ProcessTask task3 = processModel.getTasks().get(2);

        assertEquals(task2.getId(), processModel.getSequenceFlows().get(task1.getId()));
        assertEquals(task3.getId(), processModel.getSequenceFlows().get(task2.getId()));
    }

    @Test
    @DisplayName("Парсинг с разными регистрами HTTP методов")
    void testParseDifferentCaseHttpMethods() {
        String plantUml = """
            @startuml
            A -> B: get /test1
            A -> B: Post /test2
            A -> B: DELETE /test3
            @enduml
            """;

        List<ApiEndpointSummary> endpoints = parserService.extractApiEndpoints(plantUml);

        assertEquals(3, endpoints.size());
        assertEquals("GET", endpoints.get(0).getMethod());
        assertEquals("POST", endpoints.get(1).getMethod());
        assertEquals("DELETE", endpoints.get(2).getMethod());
    }
}
