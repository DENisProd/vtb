package ru.poib.VTBHack.parser.service;

import org.junit.jupiter.api.Test;
import ru.poib.VTBHack.parser.model.ApiEndpointInfo;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.ProcessTask;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BpmnParserServiceTest {

    private final BpmnParserService service = new BpmnParserService();

    @Test
    void parseValidBpmn() throws Exception {
        String bpmn =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                        "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "             xmlns:flowable=\"http://flowable.org/bpmn\"\n" +
                        "             targetNamespace=\"Examples\">\n" +
                        "  <process id=\"process1\" name=\"Test Process\" isExecutable=\"true\">\n" +
                        "    <startEvent id=\"start\" name=\"Start\"/>\n" +
                        "    <serviceTask id=\"service1\" name=\"Аутентификация: POST /auth/bank-token\"/>\n" +
                        "    <endEvent id=\"end\" name=\"End\"/>\n" +
                        "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"service1\"/>\n" +
                        "    <sequenceFlow id=\"flow2\" sourceRef=\"service1\" targetRef=\"end\"/>\n" +
                        "  </process>\n" +
                        "</definitions>";

        ProcessModel model = service.parse(bpmn);

        assertNotNull(model);
        assertEquals("process1", model.getId());
        assertEquals("Test Process", model.getName());
        assertEquals("Start", model.getStartEventName());
        assertEquals("End", model.getEndEventName());

        List<ProcessTask> tasks = model.getTasks();
        assertNotNull(tasks);
        // должен содержать задачу с id service1
        Optional<ProcessTask> maybe = tasks.stream().filter(t -> "service1".equals(t.getId())).findFirst();
        assertTrue(maybe.isPresent(), "Ожидается задача service1");
        ProcessTask serviceTask = maybe.get();

        // Проверяем, что распарсилось ApiEndpointInfo из имени задачи
        ApiEndpointInfo api = serviceTask.getApiEndpointInfo();
        assertNotNull(api, "Ожидается ApiEndpointInfo");
        assertEquals("POST", api.getMethod());
        assertEquals("/auth/bank-token", api.getPath());
        assertEquals("Аутентификация", api.getDescription());
    }

    @Test
    void validateNoTasks() {
        String bpmn =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                        "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "             targetNamespace=\"Examples\">\n" +
                        "  <process id=\"process2\" name=\"NoTasks\" isExecutable=\"true\">\n" +
                        "    <startEvent id=\"start\" name=\"Start\"/>\n" +
                        "    <endEvent id=\"end\" name=\"End\"/>\n" +
                        "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"end\"/>\n" +
                        "  </process>\n" +
                        "</definitions>";

        List<String> errors = service.validate(bpmn);
        assertNotNull(errors);
        // Ожидаем хотя бы одну ошибку о том, что нет задач
        assertTrue(errors.stream().anyMatch(s -> s.contains("Процесс не содержит задач")),
                "Ожидается сообщение 'Процесс не содержит задач'");
        // Start и End присутствуют, поэтому другие ошибки не обязательны
    }

    @Test
    void validateTaskWithoutOutgoingFlow() {
        String bpmn =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                        "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "             targetNamespace=\"Examples\">\n" +
                        "  <process id=\"process3\" name=\"MissingOutgoing\" isExecutable=\"true\">\n" +
                        "    <startEvent id=\"start\" name=\"Start\"/>\n" +
                        "    <serviceTask id=\"service1\" name=\"Do something\"/>\n" +
                        "    <endEvent id=\"end\" name=\"End\"/>\n" +
                        "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"service1\"/>\n" +
                        "    <!-- нет sequenceFlow от service1 к end -->\n" +
                        "  </process>\n" +
                        "</definitions>";

        List<String> errors = service.validate(bpmn);
        assertNotNull(errors);
        // Ожидаем ошибку о том, что задача service1 не имеет исходящего потока
        assertTrue(errors.stream().anyMatch(s -> s.contains("Задача service1 не имеет исходящего потока")),
                "Ожидается сообщение о том, что задача service1 не имеет исходящего потока");
    }
}
