package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Interaction {
    private Participant from;
    private Participant to;
    private String message;
    private ArrowType arrowType;
    private ApiCallInfo apiCallInfo;
    private BlockContext blockContext; // Контекст блока (alt, loop, etc.)
}
