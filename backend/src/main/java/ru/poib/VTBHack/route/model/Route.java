package ru.poib.VTBHack.route.model;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;
import ru.poib.VTBHack.mapping.model.MappingResult;

@Data
public class Route {
    private String id = UUID.randomUUID().toString();
    private Instant createdAt = Instant.now();
    private MappingResult mapping;
}