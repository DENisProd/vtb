package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.poib.VTBHack.parser.model.ProcessModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель Sequence диаграммы
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SequenceDiagramModel {
    private Map<String, Participant> participants = new LinkedHashMap<>();
    private List<Interaction> interactions = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
    private ProcessModel processModel;
}
