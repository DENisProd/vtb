package ru.poib.VTBHack.parser.service;

import ru.poib.VTBHack.parser.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.parser.model.puml.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Сервис для парсинга PlantUML Sequence диаграмм
 * Поддерживает различные форматы взаимодействий и извлечение API информации
 */
@Slf4j
@Service
public class PlantUmlParserService {

    // Регулярные выражения для различных элементов PlantUML

    // Actor: actor Client as C
    private static final Pattern ACTOR_PATTERN = Pattern.compile(
            "actor\\s+([\\w\\s]+?)\\s+as\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    // Participant: participant "API Bank" as Bank
    private static final Pattern PARTICIPANT_PATTERN = Pattern.compile(
            "participant\\s+\"([^\"]+)\"\\s+as\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    // Participant без кавычек: participant APIBank as Bank
    private static final Pattern PARTICIPANT_NO_QUOTES_PATTERN = Pattern.compile(
            "participant\\s+([\\w]+)\\s+as\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    // Взаимодействие: C -> Bank: POST /auth/token
    private static final Pattern INTERACTION_PATTERN = Pattern.compile(
            "(\\w+)\\s*(->|-->|->>|<<-|<--|<<--|->x|x<-)\\s*(\\w+)\\s*:\\s*(.+)"
    );

    // HTTP методы: GET, POST, PUT, DELETE, PATCH
    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile(
            "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+([^\\s(]+)(?:\\s*\\(([^)]+)\\))?",
            Pattern.CASE_INSENSITIVE
    );

    // Активация: activate Bank
    private static final Pattern ACTIVATE_PATTERN = Pattern.compile(
            "activate\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    // Деактивация: deactivate Bank
    private static final Pattern DEACTIVATE_PATTERN = Pattern.compile(
            "deactivate\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    // Note: note left/right/over: текст
    private static final Pattern NOTE_PATTERN = Pattern.compile(
            "note\\s+(left|right|over)\\s*(?:of\\s+(\\w+))?\\s*:\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );

    // Alt/Else блоки
    private static final Pattern ALT_PATTERN = Pattern.compile(
            "alt\\s+(.+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ELSE_PATTERN = Pattern.compile(
            "else\\s*(.+)?",
            Pattern.CASE_INSENSITIVE
    );

    // Loop блоки
    private static final Pattern LOOP_PATTERN = Pattern.compile(
            "loop\\s+(.+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Основной метод парсинга PlantUML диаграммы
     */
    public SequenceDiagramModel parse(String plantUmlContent) {
        log.info("Начало парсинга PlantUML диаграммы");

        if (plantUmlContent == null || plantUmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("PlantUML контент пуст");
        }

        SequenceDiagramModel model = new SequenceDiagramModel();

        // Нормализация контента
        String normalizedContent = normalizeContent(plantUmlContent);
        String[] lines = normalizedContent.split("\n");

        Map<String, Participant> participants = new LinkedHashMap<>();
        List<Interaction> interactions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // Стек для обработки вложенных блоков (alt, loop, etc.)
        Stack<BlockContext> blockStack = new Stack<>();

        int lineNumber = 0;

        for (String line : lines) {
            lineNumber++;
            line = line.trim();

            // Пропускаем пустые строки и комментарии
            if (line.isEmpty() || line.startsWith("'") || line.startsWith("/'")) {
                continue;
            }

            // Пропускаем служебные теги
            if (line.equals("@startuml") || line.equals("@enduml")) {
                continue;
            }

            try {
                // Парсим различные элементы
                if (tryParseActor(line, participants)) continue;
                if (tryParseParticipant(line, participants)) continue;
                if (tryParseInteraction(line, participants, interactions, blockStack)) continue;
                if (tryParseActivate(line)) continue;
                if (tryParseDeactivate(line)) continue;
                if (tryParseNote(line, notes)) continue;
                if (tryParseAlt(line, blockStack)) continue;
                if (tryParseElse(line, blockStack)) continue;
                if (tryParseLoop(line, blockStack)) continue;
                if (tryParseEnd(line, blockStack)) continue;

                // Если ничего не распарсилось, логируем предупреждение
                log.debug("Не удалось распарсить строку {}: {}", lineNumber, line);

            } catch (Exception e) {
                log.error("Ошибка парсинга строки {}: {}", lineNumber, line, e);
            }
        }

        model.setParticipants(participants);
        model.setInteractions(interactions);
        model.setNotes(notes);

        // Конвертируем в ProcessModel для единообразной работы
        ProcessModel processModel = convertToProcessModel(model);
        model.setProcessModel(processModel);

        log.info("Парсинг завершён. Участников: {}, Взаимодействий: {}",
                participants.size(), interactions.size());

        return model;
    }

    /**
     * Нормализация контента: удаление лишних пробелов, унификация переносов строк
     */
    private String normalizeContent(String content) {
        return content
                .replaceAll("\\r\\n", "\n")  // Windows → Unix
                .replaceAll("\\r", "\n")     // Mac → Unix
                .replaceAll("\\t", "    ");  // Табы → пробелы
    }

    /**
     * Парсинг Actor
     */
    private boolean tryParseActor(String line, Map<String, Participant> participants) {
        Matcher matcher = ACTOR_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            String alias = matcher.group(2).trim();

            Participant participant = new Participant();
            participant.setAlias(alias);
            participant.setName(name);
            participant.setType(ParticipantType.ACTOR);

            participants.put(alias, participant);
            log.debug("Найден Actor: {} as {}", name, alias);
            return true;
        }
        return false;
    }

    /**
     * Парсинг Participant
     */
    private boolean tryParseParticipant(String line, Map<String, Participant> participants) {
        // С кавычками
        Matcher matcher = PARTICIPANT_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            String alias = matcher.group(2).trim();

            Participant participant = new Participant();
            participant.setAlias(alias);
            participant.setName(name);
            participant.setType(ParticipantType.SYSTEM);

            participants.put(alias, participant);
            log.debug("Найден Participant: {} as {}", name, alias);
            return true;
        }

        // Без кавычек
        matcher = PARTICIPANT_NO_QUOTES_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            String alias = matcher.group(2).trim();

            Participant participant = new Participant();
            participant.setAlias(alias);
            participant.setName(name);
            participant.setType(ParticipantType.SYSTEM);

            participants.put(alias, participant);
            log.debug("Найден Participant: {} as {}", name, alias);
            return true;
        }

        return false;
    }

    /**
     * Парсинг взаимодействия
     */
    private boolean tryParseInteraction(String line,
                                        Map<String, Participant> participants,
                                        List<Interaction> interactions,
                                        Stack<BlockContext> blockStack) {
        Matcher matcher = INTERACTION_PATTERN.matcher(line);
        if (matcher.find()) {
            String fromAlias = matcher.group(1).trim();
            String arrow = matcher.group(2).trim();
            String toAlias = matcher.group(3).trim();
            String message = matcher.group(4).trim();

            Interaction interaction = new Interaction();

            // Определяем участников
            Participant from = participants.get(fromAlias);
            Participant to = participants.get(toAlias);

            if (from == null) {
                log.warn("Неизвестный участник (from): {}", fromAlias);
                from = createUnknownParticipant(fromAlias);
                participants.put(fromAlias, from);
            }

            if (to == null) {
                log.warn("Неизвестный участник (to): {}", toAlias);
                to = createUnknownParticipant(toAlias);
                participants.put(toAlias, to);
            }

            interaction.setFrom(from);
            interaction.setTo(to);
            interaction.setMessage(message);
            interaction.setArrowType(determineArrowType(arrow));

            // Если находимся в блоке (alt, loop), помечаем это
            if (!blockStack.isEmpty()) {
                interaction.setBlockContext(blockStack.peek());
            }

            // Парсим API информацию из message
            ApiCallInfo apiInfo = parseApiCallInfo(message);
            interaction.setApiCallInfo(apiInfo);

            interactions.add(interaction);

            log.debug("Найдено взаимодействие: {} {} {} : {}",
                    fromAlias, arrow, toAlias, message);
            return true;
        }
        return false;
    }

    /**
     * Определение типа стрелки
     */
    private ArrowType determineArrowType(String arrow) {
        switch (arrow) {
            case "->": return ArrowType.SYNC;
            case "-->": return ArrowType.ASYNC;
            case "->>": return ArrowType.SYNC;
            case "<<-": return ArrowType.RETURN;
            case "<--": return ArrowType.RETURN;
            case "<<--": return ArrowType.RETURN;
            case "->x": return ArrowType.LOST;
            case "x<-": return ArrowType.LOST;
            default: return ArrowType.SYNC;
        }
    }

    /**
     * Создание неизвестного участника
     */
    private Participant createUnknownParticipant(String alias) {
        Participant participant = new Participant();
        participant.setAlias(alias);
        participant.setName(alias);
        participant.setType(ParticipantType.UNKNOWN);
        return participant;
    }

    /**
     * Парсинг API информации из сообщения
     */
    private ApiCallInfo parseApiCallInfo(String message) {
        ApiCallInfo apiInfo = new ApiCallInfo();

        // Пытаемся найти HTTP метод
        Matcher httpMatcher = HTTP_METHOD_PATTERN.matcher(message);
        if (httpMatcher.find()) {
            apiInfo.setRequest(true);
            apiInfo.setMethod(httpMatcher.group(1).toUpperCase());
            apiInfo.setPath(httpMatcher.group(2));

            // Парсим параметры, если есть
            String paramsString = httpMatcher.group(3);
            if (paramsString != null && !paramsString.trim().isEmpty()) {
                List<String> params = Arrays.stream(paramsString.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                apiInfo.setParameters(params);
            }

            log.debug("Извлечена API информация: {} {}", apiInfo.getMethod(), apiInfo.getPath());
        } else {
            // Это response или просто сообщение
            apiInfo.setRequest(false);
            apiInfo.setResponseDescription(message);
        }

        return apiInfo;
    }

    /**
     * Парсинг activate
     */
    private boolean tryParseActivate(String line) {
        Matcher matcher = ACTIVATE_PATTERN.matcher(line);
        if (matcher.find()) {
            String participant = matcher.group(1);
            log.debug("Активация участника: {}", participant);
            // В MVP можем просто логировать, полная обработка - позже
            return true;
        }
        return false;
    }

    /**
     * Парсинг deactivate
     */
    private boolean tryParseDeactivate(String line) {
        Matcher matcher = DEACTIVATE_PATTERN.matcher(line);
        if (matcher.find()) {
            String participant = matcher.group(1);
            log.debug("Деактивация участника: {}", participant);
            return true;
        }
        return false;
    }

    /**
     * Парсинг note
     */
    private boolean tryParseNote(String line, List<String> notes) {
        Matcher matcher = NOTE_PATTERN.matcher(line);
        if (matcher.find()) {
            String position = matcher.group(1);
            String participant = matcher.group(2);
            String text = matcher.group(3);

            notes.add(text);
            log.debug("Найдена заметка: {}", text);
            return true;
        }
        return false;
    }

    /**
     * Парсинг alt блока
     */
    private boolean tryParseAlt(String line, Stack<BlockContext> blockStack) {
        Matcher matcher = ALT_PATTERN.matcher(line);
        if (matcher.find()) {
            String condition = matcher.group(1);

            BlockContext block = new BlockContext();
            block.setType(BlockType.ALT);
            block.setCondition(condition);

            blockStack.push(block);
            log.debug("Начало ALT блока: {}", condition);
            return true;
        }
        return false;
    }

    /**
     * Парсинг else
     */
    private boolean tryParseElse(String line, Stack<BlockContext> blockStack) {
        Matcher matcher = ELSE_PATTERN.matcher(line);
        if (matcher.find()) {
            if (!blockStack.isEmpty() && blockStack.peek().getType() == BlockType.ALT) {
                String condition = matcher.group(1);
                blockStack.peek().setElseCondition(condition);
                log.debug("ELSE блок: {}", condition);
                return true;
            }
        }
        return false;
    }

    /**
     * Парсинг loop блока
     */
    private boolean tryParseLoop(String line, Stack<BlockContext> blockStack) {
        Matcher matcher = LOOP_PATTERN.matcher(line);
        if (matcher.find()) {
            String condition = matcher.group(1);

            BlockContext block = new BlockContext();
            block.setType(BlockType.LOOP);
            block.setCondition(condition);

            blockStack.push(block);
            log.debug("Начало LOOP блока: {}", condition);
            return true;
        }
        return false;
    }

    /**
     * Парсинг end (закрытие блока)
     */
    private boolean tryParseEnd(String line, Stack<BlockContext> blockStack) {
        if (line.equalsIgnoreCase("end")) {
            if (!blockStack.isEmpty()) {
                BlockContext closed = blockStack.pop();
                log.debug("Закрытие блока: {}", closed.getType());
                return true;
            }
        }
        return false;
    }

    /**
     * Конвертация SequenceDiagramModel в ProcessModel
     */
    private ProcessModel convertToProcessModel(SequenceDiagramModel seqModel) {
        ProcessModel processModel = new ProcessModel();
        processModel.setId("sequence_process_" + UUID.randomUUID().toString().substring(0, 8));
        processModel.setName("Process from Sequence Diagram");

        List<ProcessTask> tasks = new ArrayList<>();
        Map<String, String> sequenceFlows = new LinkedHashMap<>();

        ProcessTask previousTask = null;
        int taskIndex = 0;

        // Конвертируем только request взаимодействия в задачи
        for (Interaction interaction : seqModel.getInteractions()) {
            ApiCallInfo apiInfo = interaction.getApiCallInfo();

            if (apiInfo != null && apiInfo.isRequest()) {
                ProcessTask task = new ProcessTask();
                task.setId("seq_task_" + taskIndex);
                task.setName(interaction.getMessage());
                task.setType("ServiceTask");
                task.setDescription(
                        interaction.getFrom().getName() + " → " + interaction.getTo().getName()
                );

                // Маппим API информацию
                ApiEndpointInfo endpointInfo = new ApiEndpointInfo();
                endpointInfo.setMethod(apiInfo.getMethod());
                endpointInfo.setPath(apiInfo.getPath());
                endpointInfo.setDescription(interaction.getFrom().getName() + " calls " +
                        interaction.getTo().getName());
                task.setApiEndpointInfo(endpointInfo);

                // Custom properties
                Map<String, String> properties = new HashMap<>();
                if (apiInfo.getParameters() != null && !apiInfo.getParameters().isEmpty()) {
                    properties.put("request_parameters", String.join(", ", apiInfo.getParameters()));
                }
                properties.put("source", interaction.getFrom().getName());
                properties.put("target", interaction.getTo().getName());
                task.setCustomProperties(properties);

                tasks.add(task);

                // Создаем sequence flow
                if (previousTask != null) {
                    sequenceFlows.put(previousTask.getId(), task.getId());
                }

                previousTask = task;
                taskIndex++;
            }
        }

        processModel.setTasks(tasks);
        processModel.setSequenceFlows(sequenceFlows);

        // Устанавливаем start и end события
        if (!tasks.isEmpty()) {
            processModel.setStartEventName("Start of sequence");
            processModel.setEndEventName("End of sequence");
        }

        return processModel;
    }

    /**
     * Валидация PlantUML диаграммы
     */
    public ValidationResult validate(String plantUmlContent) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (plantUmlContent == null || plantUmlContent.trim().isEmpty()) {
            errors.add("PlantUML контент пуст");
            result.setValid(false);
            result.setErrors(errors);
            return result;
        }

        // Проверка базовой структуры
        if (!plantUmlContent.contains("@startuml")) {
            errors.add("Отсутствует открывающий тег @startuml");
        }

        if (!plantUmlContent.contains("@enduml")) {
            errors.add("Отсутствует закрывающий тег @enduml");
        }

        try {
            SequenceDiagramModel model = parse(plantUmlContent);

            // Проверка участников
            if (model.getParticipants().isEmpty()) {
                warnings.add("Диаграмма не содержит явно объявленных участников");
            }

            // Проверка взаимодействий
            if (model.getInteractions().isEmpty()) {
                errors.add("Диаграмма не содержит взаимодействий");
            }

            // Проверка API вызовов
            long apiCallCount = model.getInteractions().stream()
                    .filter(i -> i.getApiCallInfo() != null && i.getApiCallInfo().isRequest())
                    .count();

            if (apiCallCount == 0) {
                warnings.add("Диаграмма не содержит явных API вызовов (HTTP методов)");
            }

            // Проверка неизвестных участников
            long unknownParticipants = model.getParticipants().values().stream()
                    .filter(p -> p.getType() == ParticipantType.UNKNOWN)
                    .count();

            if (unknownParticipants > 0) {
                warnings.add("Обнаружено " + unknownParticipants +
                        " неявно объявленных участников");
            }

        } catch (Exception e) {
            errors.add("Ошибка парсинга: " + e.getMessage());
        }

        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        result.setWarnings(warnings);

        return result;
    }

    /**
     * Извлечение списка всех API endpoints
     */
    public List<ApiEndpointSummary> extractApiEndpoints(String plantUmlContent) {
        SequenceDiagramModel model = parse(plantUmlContent);
        List<ApiEndpointSummary> endpoints = new ArrayList<>();

        for (Interaction interaction : model.getInteractions()) {
            ApiCallInfo apiInfo = interaction.getApiCallInfo();

            if (apiInfo != null && apiInfo.isRequest()) {
                ApiEndpointSummary summary = new ApiEndpointSummary();
                summary.setMethod(apiInfo.getMethod());
                summary.setPath(apiInfo.getPath());
                summary.setSource(interaction.getFrom().getName());
                summary.setTarget(interaction.getTo().getName());
                summary.setParameters(apiInfo.getParameters());

                endpoints.add(summary);
            }
        }

        return endpoints;
    }

    /**
     * Получение статистики по диаграмме
     */
    public DiagramStatistics getStatistics(String plantUmlContent) {
        SequenceDiagramModel model = parse(plantUmlContent);

        DiagramStatistics stats = new DiagramStatistics();
        stats.setParticipantCount(model.getParticipants().size());
        stats.setInteractionCount(model.getInteractions().size());

        long apiCallCount = model.getInteractions().stream()
                .filter(i -> i.getApiCallInfo() != null && i.getApiCallInfo().isRequest())
                .count();
        stats.setApiCallCount((int) apiCallCount);

        long responseCount = model.getInteractions().stream()
                .filter(i -> i.getApiCallInfo() != null && !i.getApiCallInfo().isRequest())
                .count();
        stats.setResponseCount((int) responseCount);

        stats.setNoteCount(model.getNotes().size());

        // Подсчет методов
        Map<String, Integer> methodCount = model.getInteractions().stream()
                .filter(i -> i.getApiCallInfo() != null && i.getApiCallInfo().isRequest())
                .collect(Collectors.groupingBy(
                        i -> i.getApiCallInfo().getMethod(),
                        Collectors.summingInt(e -> 1)
                ));
        stats.setMethodDistribution(methodCount);

        return stats;
    }
}
