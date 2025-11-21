package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат валидации
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean valid;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
        valid = false;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
