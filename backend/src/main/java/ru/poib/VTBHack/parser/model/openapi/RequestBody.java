package ru.poib.VTBHack.parser.model.openapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestBody {
    private String description;
    private Boolean required;
    // Минимально необходимое: нам важно лишь наличие requestBody,
    // поэтому тип контента упрощаем до Map
    private Map<String, Object> content;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }
}