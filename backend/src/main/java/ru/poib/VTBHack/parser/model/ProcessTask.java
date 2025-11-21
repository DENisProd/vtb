package ru.poib.VTBHack.parser.model;

import lombok.Data;

import java.util.Map;

@Data
public class ProcessTask {
    private String id;
    private String name;
    private String type; // ServiceTask, UserTask, Task
    private String description;
    private Map<String, String> customProperties;
    private ApiEndpointInfo apiEndpointInfo;
}
