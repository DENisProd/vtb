package ru.poib.VTBHack.project.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.poib.VTBHack.mapping.model.MappingResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    private String id;
    private String name;
    private String createdAt;
    private String updatedAt;
    private String bpmnXml;
    private String openApiJson;
    private String pumlContent;
    private MappingResult mappingResult;
}