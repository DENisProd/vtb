package ru.poib.VTBHack.route.repo;

import org.springframework.stereotype.Repository;
import ru.poib.VTBHack.route.model.Route;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RouteRepository {
    private final Map<String, Route> storage = new ConcurrentHashMap<>();

    public Optional<Route> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public Route save(Route route) {
        if (route.getId() == null) {
            route.setId(UUID.randomUUID().toString());
        }
        storage.put(route.getId(), route);
        return route;
    }

    public void deleteById(String id) {
        storage.remove(id);
    }

    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}