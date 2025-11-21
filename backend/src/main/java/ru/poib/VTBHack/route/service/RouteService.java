package ru.poib.VTBHack.route.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.mapping.model.AIVerificationReport;
import ru.poib.VTBHack.mapping.model.MappingResult;
import ru.poib.VTBHack.route.model.Route;
import ru.poib.VTBHack.route.repo.RouteRepository;

@Service
@RequiredArgsConstructor
public class RouteService {
    private final RouteRepository repository;

    public Route saveMapping(MappingResult result) {
        Route route = new Route();
        route.setMapping(result);
        return repository.save(route);
    }

    public void attachAiReport(String routeId, AIVerificationReport report) {
        repository.findById(routeId).ifPresent(route -> {
            MappingResult mapping = route.getMapping();
            if (mapping != null) {
                mapping.setAiVerificationReport(report);
                route.setMapping(mapping);
                repository.save(route);
            }
        });
    }
}