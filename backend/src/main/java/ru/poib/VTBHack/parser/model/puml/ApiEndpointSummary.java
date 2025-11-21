package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointSummary {
    private String method;
    private String path;
    private String source;
    private String target;
    private List<String> parameters;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(path);
        sb.append(" (").append(source).append(" → ").append(target).append(")");
        if (parameters != null && !parameters.isEmpty()) {
            sb.append(" [").append(String.join(", ", parameters)).append("]");
        }
        return sb.toString();
    }
}
