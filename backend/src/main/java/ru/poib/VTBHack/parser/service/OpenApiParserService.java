package ru.poib.VTBHack.parser.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import java.io.IOException;
import java.io.InputStream;

@Service
public class OpenApiParserService {
    private final ObjectMapper objectMapper;

    public OpenApiParserService() {
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public OpenApiModel parseOpenApi(String jsonContent) throws IOException {
        return objectMapper.readValue(jsonContent, OpenApiModel.class);
    }

    public OpenApiModel parseOpenApi(InputStream inputStream) throws IOException {
        return objectMapper.readValue(inputStream, OpenApiModel.class);
    }

    // Additional methods for specific parsing needs can be added here
    public String getApiTitle(OpenApiModel openApi) {
        return openApi.getInfo() != null ? openApi.getInfo().getTitle() : null;
    }

    public String getApiVersion(OpenApiModel openApi) {
        return openApi.getInfo() != null ? openApi.getInfo().getVersion() : null;
    }
}