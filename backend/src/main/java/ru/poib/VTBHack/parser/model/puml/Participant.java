package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Участник диаграммы (Actor или Participant)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Participant {
    private String alias;           // C, Bank, etc.
    private String name;            // Client, API Bank, etc.
    private ParticipantType type;   // ACTOR, SYSTEM, UNKNOWN
}
