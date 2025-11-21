package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Контекст блока (alt, loop, etc.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockContext {
    private BlockType type;
    private String condition;
    private String elseCondition;
}
