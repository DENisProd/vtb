package ru.poib.VTBHack.mapping.service;

import org.springframework.stereotype.Component;
import ru.poib.VTBHack.mapping.model.EndpointInfo;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;

import java.util.ArrayList;
import java.util.List;

/**
 * Извлекает список эндпоинтов из OpenAPI модели
 */
@Component
public class EndpointExtractor {
    
    /**
     * Извлекает все эндпоинты из OpenAPI модели
     */
    public List<EndpointInfo> extractEndpoints(OpenApiModel openApiModel) {
        List<EndpointInfo> endpoints = new ArrayList<>();
        
        if (openApiModel == null || openApiModel.getPaths() == null) {
            return endpoints;
        }
        
        openApiModel.getPaths().forEach((path, pathItem) -> {
            if (pathItem != null) {
                if (pathItem.getGet() != null) {
                    endpoints.add(createEndpointInfo(path, "GET", pathItem.getGet()));
                }
                if (pathItem.getPost() != null) {
                    endpoints.add(createEndpointInfo(path, "POST", pathItem.getPost()));
                }
                if (pathItem.getPut() != null) {
                    endpoints.add(createEndpointInfo(path, "PUT", pathItem.getPut()));
                }
                if (pathItem.getDelete() != null) {
                    endpoints.add(createEndpointInfo(path, "DELETE", pathItem.getDelete()));
                }
            }
        });
        
        return endpoints;
    }
    
    private EndpointInfo createEndpointInfo(String path, String method, Operation operation) {
        String summary = operation.getSummary() != null ? operation.getSummary() : "";
        String description = operation.getDescription() != null ? operation.getDescription() : "";
        String operationId = operation.getOperationId() != null ? operation.getOperationId() : "";
        
        return new EndpointInfo(path, method, operationId, summary, description);
    }
}


