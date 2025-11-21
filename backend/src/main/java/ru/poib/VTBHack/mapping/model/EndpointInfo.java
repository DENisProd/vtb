package ru.poib.VTBHack.mapping.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Унифицированное представление API эндпоинта для сопоставления
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndpointInfo {
    private String path;
    private String method; // GET, POST, PUT, DELETE
    private String operationId;
    private String summary;
    private String description;
    private String fullText; // Объединенный текст для семантического анализа
    
    public EndpointInfo(String path, String method, String operationId, String summary, String description) {
        this.path = path;
        this.method = method;
        this.operationId = operationId;
        this.summary = summary;
        this.description = description;
        this.fullText = buildFullText();
    }
    
    private String buildFullText() {
        StringBuilder sb = new StringBuilder();
        if (operationId != null) sb.append(operationId).append(" ");
        if (summary != null) sb.append(summary).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (path != null) sb.append(path).append(" ");
        return sb.toString().trim();
    }
}


