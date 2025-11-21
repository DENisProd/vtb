package ru.poib.VTBHack.parser.model.puml;

public enum BlockType {
    ALT,        // Альтернативный путь (if-else)
    LOOP,       // Цикл
    OPT,        // Опциональный
    PAR,        // Параллельный
    CRITICAL    // Критическая секция
}
