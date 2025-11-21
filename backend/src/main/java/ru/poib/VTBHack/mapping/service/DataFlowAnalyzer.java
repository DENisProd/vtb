package ru.poib.VTBHack.mapping.service;

import org.springframework.stereotype.Service;
import ru.poib.VTBHack.mapping.model.DataFlowEdge;
import ru.poib.VTBHack.mapping.model.TaskEndpointMapping;
import ru.poib.VTBHack.parser.model.ProcessModel;
import ru.poib.VTBHack.parser.model.ProcessTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Анализатор потока данных между шагами процесса
 */
@Service
public class DataFlowAnalyzer {
    
    /**
     * Анализирует поток данных между задачами процесса
     * Определяет, какие поля из response одной задачи используются в request следующей
     */
    public List<DataFlowEdge> analyzeDataFlow(ProcessModel processModel, 
                                             Map<String, TaskEndpointMapping> taskMappings) {
        List<DataFlowEdge> edges = new ArrayList<>();
        
        if (processModel == null || processModel.getTasks() == null || taskMappings == null) {
            return edges;
        }
        
        List<ProcessTask> tasks = processModel.getTasks();
        
        // Проходим по последовательности задач
        for (int i = 0; i < tasks.size() - 1; i++) {
            ProcessTask sourceTask = tasks.get(i);
            ProcessTask targetTask = tasks.get(i + 1);
            
            TaskEndpointMapping sourceMapping = taskMappings.get(sourceTask.getId());
            TaskEndpointMapping targetMapping = taskMappings.get(targetTask.getId());
            
            if (sourceMapping != null && targetMapping != null) {
                // Анализируем поток данных между этими задачами
                DataFlowEdge edge = analyzeEdge(sourceTask, targetTask, sourceMapping, targetMapping);
                if (edge != null) {
                    edges.add(edge);
                }
            }
        }
        
        // Также проверяем связи через sequence flows
        if (processModel.getSequenceFlows() != null) {
            processModel.getSequenceFlows().forEach((sourceId, targetId) -> {
                ProcessTask sourceTask = findTaskById(tasks, sourceId);
                ProcessTask targetTask = findTaskById(tasks, targetId);
                
                if (sourceTask != null && targetTask != null) {
                    TaskEndpointMapping sourceMapping = taskMappings.get(sourceTask.getId());
                    TaskEndpointMapping targetMapping = taskMappings.get(targetTask.getId());
                    
                    if (sourceMapping != null && targetMapping != null) {
                        // Проверяем, не добавили ли мы уже это ребро
                        boolean alreadyExists = edges.stream()
                                .anyMatch(e -> e.getSourceTaskId().equals(sourceId) && 
                                             e.getTargetTaskId().equals(targetId));
                        
                        if (!alreadyExists) {
                            DataFlowEdge edge = analyzeEdge(sourceTask, targetTask, sourceMapping, targetMapping);
                            if (edge != null) {
                                edges.add(edge);
                            }
                        }
                    }
                }
            });
        }
        
        return edges;
    }
    
    /**
     * Анализирует поток данных между двумя задачами
     */
    private DataFlowEdge analyzeEdge(ProcessTask sourceTask, ProcessTask targetTask,
                                    TaskEndpointMapping sourceMapping, TaskEndpointMapping targetMapping) {
        // Базовая эвристика: определяем направление передачи данных
        // В реальной реализации здесь должен быть анализ схем request/response
        
        // Пока используем простую эвристику:
        // Если sourceTask возвращает данные (GET) или создает ресурс (POST),
        // а targetTask использует эти данные (POST, PUT с параметрами)
        
        List<String> fields = inferDataFields(sourceMapping, targetMapping);
        double confidence = calculateDataFlowConfidence(sourceMapping, targetMapping);
        
        if (confidence > 0.3) { // Минимальный порог уверенности
            return new DataFlowEdge(
                sourceTask.getId(),
                targetTask.getId(),
                fields,
                confidence
            );
        }
        
        return null;
    }
    
    /**
     * Выводит поля данных, которые могут передаваться между задачами
     */
    private List<String> inferDataFields(TaskEndpointMapping sourceMapping, 
                                         TaskEndpointMapping targetMapping) {
        List<String> fields = new ArrayList<>();
        
        // Простая эвристика: если source - GET, то возвращает данные
        // Если target - POST/PUT, то принимает данные
        if ("GET".equalsIgnoreCase(sourceMapping.getEndpointMethod())) {
            // Извлекаем возможные поля из path (например, /accounts/{accountId} -> accountId)
            String path = sourceMapping.getEndpointPath();
            if (path != null && path.contains("{")) {
                int start = path.indexOf("{");
                int end = path.indexOf("}");
                if (end > start) {
                    fields.add(path.substring(start + 1, end));
                }
            }
            
            // Добавляем общие поля, которые часто возвращаются
            fields.add("id");
            fields.add("data");
        }
        
        // Если target - POST/PUT, добавляем поля, которые могут быть в request
        if ("POST".equalsIgnoreCase(targetMapping.getEndpointMethod()) ||
            "PUT".equalsIgnoreCase(targetMapping.getEndpointMethod())) {
            String path = targetMapping.getEndpointPath();
            if (path != null && path.contains("{")) {
                int start = path.indexOf("{");
                int end = path.indexOf("}");
                if (end > start) {
                    String param = path.substring(start + 1, end);
                    if (!fields.contains(param)) {
                        fields.add(param);
                    }
                }
            }
        }
        
        return fields.isEmpty() ? List.of("data") : fields;
    }
    
    /**
     * Вычисляет уверенность в потоке данных
     */
    private double calculateDataFlowConfidence(TaskEndpointMapping sourceMapping, 
                                              TaskEndpointMapping targetMapping) {
        double confidence = 0.5; // Базовая уверенность
        
        // Если source - GET, а target - POST/PUT, увеличиваем уверенность
        if ("GET".equalsIgnoreCase(sourceMapping.getEndpointMethod()) &&
            ("POST".equalsIgnoreCase(targetMapping.getEndpointMethod()) ||
             "PUT".equalsIgnoreCase(targetMapping.getEndpointMethod()))) {
            confidence = 0.7;
        }
        
        // Если есть общие параметры в путях, увеличиваем уверенность
        String sourcePath = sourceMapping.getEndpointPath();
        String targetPath = targetMapping.getEndpointPath();
        if (sourcePath != null && targetPath != null) {
            // Простая проверка на общие части пути
            if (sourcePath.contains(targetPath) || targetPath.contains(sourcePath)) {
                confidence = Math.min(0.9, confidence + 0.2);
            }
        }
        
        return confidence;
    }
    
    /**
     * Находит задачу по ID
     */
    private ProcessTask findTaskById(List<ProcessTask> tasks, String taskId) {
        return tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
}


