package ru.poib.VTBHack.parser.model.openapi;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {
    private String description;
    private Map<String, MediaType> content = new HashMap<>();

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, MediaType> getContent() {
        return content;
    }

    public void setContent(Map<String, MediaType> content) {
        this.content = content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaType {
        private Schema schema;
        private Object example;
        private Map<String, Object> examples;

        public Schema getSchema() {
            return schema;
        }

        public void setSchema(Schema schema) {
            this.schema = schema;
        }

        public Object getExample() {
            return example;
        }

        public void setExample(Object example) {
            this.example = example;
        }

        public Map<String, Object> getExamples() {
            return examples;
        }

        public void setExamples(Map<String, Object> examples) {
            this.examples = examples;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Schema {
        private String type;
        private String format;
        private String description;
        private Map<String, Schema> properties = new HashMap<>();
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Schema> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Schema> properties) {
            this.properties = properties;
        }

        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }
    }
}