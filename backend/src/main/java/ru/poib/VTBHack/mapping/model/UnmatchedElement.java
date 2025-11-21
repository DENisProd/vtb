package ru.poib.VTBHack.mapping.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Элемент, который не удалось сопоставить автоматически
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnmatchedElement {
    private String elementId;
    private String elementName;
    private String elementType; // TASK или ENDPOINT
    private List<String> recommendations; // Рекомендации для ручного сопоставления
    private double maxConfidence; // Максимальная уверенность среди всех попыток сопоставления
}


