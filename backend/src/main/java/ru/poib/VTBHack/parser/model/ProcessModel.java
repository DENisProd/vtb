package ru.poib.VTBHack.parser.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProcessModel {
    private String id;
    private String name;
    private List<ProcessTask> tasks;
    private Map<String, String> sequenceFlows; // sourceId -> targetId
    private String startEventName;
    private String endEventName;
}
