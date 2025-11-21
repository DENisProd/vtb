package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramStatistics {
    private int participantCount;
    private int interactionCount;
    private int apiCallCount;
    private int responseCount;
    private int noteCount;
    private Map<String, Integer> methodDistribution = new HashMap<>();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Статистика диаграммы ===\n");
        sb.append("Участников: ").append(participantCount).append("\n");
        sb.append("Взаимодействий: ").append(interactionCount).append("\n");
        sb.append("API вызовов: ").append(apiCallCount).append("\n");
        sb.append("Ответов: ").append(responseCount).append("\n");
        sb.append("Заметок: ").append(noteCount).append("\n");

        if (!methodDistribution.isEmpty()) {
            sb.append("\nРаспределение по методам:\n");
            methodDistribution.forEach((method, count) ->
                    sb.append("  ").append(method).append(": ").append(count).append("\n")
            );
        }

        return sb.toString();
    }
}
