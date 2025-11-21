package ru.poib.VTBHack.parser.model.puml;

public enum ArrowType {
    SYNC,       // -> (синхронный вызов)
    ASYNC,      // --> (асинхронный вызов)
    RETURN,     // <-- (возврат)
    LOST        // ->x (потерянное сообщение)
}
