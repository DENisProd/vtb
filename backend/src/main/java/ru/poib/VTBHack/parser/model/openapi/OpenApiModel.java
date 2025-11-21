package ru.poib.VTBHack.parser.model.openapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenApiModel {
    @JsonProperty("openapi")
    private String openApiVersion;
    
    @JsonProperty("info")
    private Info info;
    
    @JsonProperty("paths")
    private Map<String, PathItem> paths;
    
    // Getters and Setters
    public String getOpenApiVersion() {
        return openApiVersion;
    }

    public void setOpenApiVersion(String openApiVersion) {
        this.openApiVersion = openApiVersion;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public Map<String, PathItem> getPaths() {
        return paths;
    }

    public void setPaths(Map<String, PathItem> paths) {
        this.paths = paths;
    }

    // Inner classes
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        private String title;
        private String version;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PathItem {
        private Operation post;
        private Operation get;
        private Operation put;
        private Operation delete;

        public Operation getPost() {
            return post;
        }

        public void setPost(Operation post) {
            this.post = post;
        }

        public Operation getGet() {
            return get;
        }

        public void setGet(Operation get) {
            this.get = get;
        }

        public Operation getPut() {
            return put;
        }

        public void setPut(Operation put) {
            this.put = put;
        }

        public Operation getDelete() {
            return delete;
        }

        public void setDelete(Operation delete) {
            this.delete = delete;
        }
    }
}