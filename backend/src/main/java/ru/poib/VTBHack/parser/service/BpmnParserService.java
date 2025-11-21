package ru.poib.VTBHack.parser.service;

import org.flowable.bpmn.model.Process;
import org.springframework.stereotype.Service;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.*;
import ru.poib.VTBHack.parser.model.ApiEndpointInfo;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.ProcessTask;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BpmnParserService {
    private final BpmnXMLConverter bpmnXMLConverter = new BpmnXMLConverter();

    /**
     * Парсит BPMN XML и возвращает модель процесса
     */
    public ProcessModel parse(String bpmnXml) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8));
        BpmnModel bpmnModel = bpmnXMLConverter.convertToBpmnModel(
                () -> inputStream,
                false,
                false
        );

        // Получаем главный процесс
        Process process = bpmnModel.getMainProcess();
        if (process == null) {
            throw new IllegalArgumentException("BPMN не содержит главного процесса");
        }

        return buildProcessModel(process, bpmnModel);
    }

    /**
     * Строит внутреннюю модель процесса
     */
    private ProcessModel buildProcessModel(Process process, BpmnModel bpmnModel) {
        ProcessModel model = new ProcessModel();
        model.setId(process.getId());
        model.setName(process.getName());

        // Извлекаем все задачи
        List<ProcessTask> tasks = new ArrayList<>();

        // Service Tasks - задачи, которые обычно соответствуют API вызовам
        for (ServiceTask serviceTask : process.findFlowElementsOfType(ServiceTask.class)) {
            tasks.add(createTaskFromServiceTask(serviceTask));
        }

        // User Tasks - для полноты картины
        for (UserTask userTask : process.findFlowElementsOfType(UserTask.class)) {
            tasks.add(createTaskFromUserTask(userTask));
        }

        // Обычные Tasks (generic)
        for (Task task : process.findFlowElementsOfType(Task.class)) {
            // Проверяем, не добавили ли мы уже эту задачу как ServiceTask или UserTask
            boolean alreadyAdded = tasks.stream()
                    .anyMatch(t -> t.getId().equals(task.getId()));
            if (!alreadyAdded) {
                tasks.add(createTaskFromGenericTask(task));
            }
        }

        // Сортируем задачи по порядку выполнения
        List<ProcessTask> sortedTasks = sortTasksBySequence(tasks, process);
        model.setTasks(sortedTasks);

        // Извлекаем sequence flows (связи между задачами)
        Map<String, String> flows = new HashMap<>();
        for (SequenceFlow flow : process.findFlowElementsOfType(SequenceFlow.class)) {
            flows.put(flow.getSourceRef(), flow.getTargetRef());
        }
        model.setSequenceFlows(flows);

        // Извлекаем start и end события
        process.findFlowElementsOfType(StartEvent.class).stream()
                .findFirst()
                .ifPresent(start -> model.setStartEventName(start.getName()));

        process.findFlowElementsOfType(EndEvent.class).stream()
                .findFirst()
                .ifPresent(end -> model.setEndEventName(end.getName()));

        return model;
    }

    /**
     * Создает ProcessTask из ServiceTask
     */
    private ProcessTask createTaskFromServiceTask(ServiceTask serviceTask) {
        ProcessTask task = new ProcessTask();
        task.setId(serviceTask.getId());
        task.setName(serviceTask.getName());
        task.setType("ServiceTask");
        task.setDescription(serviceTask.getDocumentation());

        // Извлекаем custom properties (extensionElements)
        // Например: <flowable:field name="apiEndpoint" stringValue="POST /auth/token"/>
        Map<String, String> properties = extractExtensionElements(serviceTask);
        task.setCustomProperties(properties);

        // Извлекаем информацию об API endpoint из имени задачи
        ApiEndpointInfo apiInfo = extractApiInfoFromTaskName(serviceTask.getName());
        task.setApiEndpointInfo(apiInfo);

        return task;
    }

    /**
     * Создает ProcessTask из UserTask
     */
    private ProcessTask createTaskFromUserTask(UserTask userTask) {
        ProcessTask task = new ProcessTask();
        task.setId(userTask.getId());
        task.setName(userTask.getName());
        task.setType("UserTask");
        task.setDescription(userTask.getDocumentation());

        Map<String, String> properties = extractExtensionElements(userTask);
        task.setCustomProperties(properties);

        return task;
    }

    /**
     * Создает ProcessTask из обычного Task
     */
    private ProcessTask createTaskFromGenericTask(Task task) {
        ProcessTask processTask = new ProcessTask();
        processTask.setId(task.getId());
        processTask.setName(task.getName());
        processTask.setType("Task");
        processTask.setDescription(task.getDocumentation());

        Map<String, String> properties = extractExtensionElements(task);
        processTask.setCustomProperties(properties);

        // Пытаемся извлечь API информацию из имени
        ApiEndpointInfo apiInfo = extractApiInfoFromTaskName(task.getName());
        processTask.setApiEndpointInfo(apiInfo);

        return processTask;
    }

    /**
     * Извлекает extension elements (custom properties)
     */
    private Map<String, String> extractExtensionElements(FlowElement element) {
        Map<String, String> properties = new HashMap<>();

        if (element.getExtensionElements() != null) {
            element.getExtensionElements().forEach((key, values) -> {
                if (!values.isEmpty()) {
                    ExtensionElement ext = values.get(0);
                    properties.put(key, ext.getElementText());
                }
            });
        }

        return properties;
    }

    /**
     * Извлекает информацию об API endpoint из имени задачи
     * Пример: "Аутентификация: POST /auth/bank-token"
     * Пример: "Получение списка счетов: GET /accounts"
     */
    private ApiEndpointInfo extractApiInfoFromTaskName(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return null;
        }

        ApiEndpointInfo info = new ApiEndpointInfo();

        // Паттерн: "Описание: METHOD /path"
        String[] parts = taskName.split(":");
        if (parts.length >= 2) {
            String apiPart = parts[1].trim();
            String[] methodAndPath = apiPart.split("\\s+", 2);

            if (methodAndPath.length == 2) {
                info.setMethod(methodAndPath[0].toUpperCase());
                info.setPath(methodAndPath[1]);
                info.setDescription(parts[0].trim());
                return info;
            }
        }

        // Если не нашли паттерн, возвращаем null
        return null;
    }

    /**
     * Сортирует задачи по порядку их выполнения в процессе
     */
    private List<ProcessTask> sortTasksBySequence(List<ProcessTask> tasks, Process process) {
        List<ProcessTask> sorted = new ArrayList<>();
        Map<String, ProcessTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(ProcessTask::getId, t -> t));

        // Находим start event
        StartEvent startEvent = process.findFlowElementsOfType(StartEvent.class)
                .stream()
                .findFirst()
                .orElse(null);

        if (startEvent == null) {
            return tasks; // Возвращаем как есть, если нет start event
        }

        // Обходим граф начиная со start event
        String currentId = startEvent.getId();
        Set<String> visited = new HashSet<>();

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);

            ProcessTask task = taskMap.get(currentId);
            if (task != null) {
                sorted.add(task);
            }

            // Находим следующий элемент
            String finalCurrentId = currentId;
            SequenceFlow outgoing = process.findFlowElementsOfType(SequenceFlow.class)
                    .stream()
                    .filter(sf -> sf.getSourceRef().equals(finalCurrentId))
                    .findFirst()
                    .orElse(null);

            currentId = (outgoing != null) ? outgoing.getTargetRef() : null;
        }

        return sorted;
    }

    /**
     * Валидация BPMN файла
     */
    public List<String> validate(String bpmnXml) {
        List<String> errors = new ArrayList<>();

        try {
            ProcessModel model = parse(bpmnXml);

            if (model.getTasks().isEmpty()) {
                errors.add("Процесс не содержит задач");
            }

            if (model.getStartEventName() == null) {
                errors.add("Процесс не содержит Start Event");
            }

            if (model.getEndEventName() == null) {
                errors.add("Процесс не содержит End Event");
            }

            for (ProcessTask task : model.getTasks()) {
                if (!model.getSequenceFlows().containsKey(task.getId())) {
                    errors.add("Задача " + task.getId() + " не имеет исходящего потока");
                }
            }

        } catch (Exception e) {
            errors.add("Ошибка парсинга BPMN: " + e.getMessage());
        }

        return errors;
    }

    private boolean isEndTask(ProcessTask task, ProcessModel model) {
        // Проверяем, является ли задача последней (перед EndEvent)
        return !model.getSequenceFlows().containsKey(task.getId());
    }
}
