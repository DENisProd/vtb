package ru.poib.VTBHack.parser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.poib.VTBHack.parser.model.openapi.OpenApiModel;
import ru.poib.VTBHack.parser.model.openapi.Operation;
import ru.poib.VTBHack.parser.model.openapi.Parameter;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiParserServiceTest {
    private OpenApiParserService parserService;
    private String sampleOpenApiJson;

    @BeforeEach
    void setUp() {
        parserService = new OpenApiParserService();
        sampleOpenApiJson = "{\n" +
                "  \"openapi\": \"3.1.0\",\n" +
                "  \"info\": {\n" +
                "    \"title\": \"Virtual Bank API\",\n" +
                "    \"version\": \"2.1\"\n" +
                "  },\n" +
                "  \"paths\": {\n" +
                "    \"/auth/bank-token\": {\n" +
                "      \"post\": {\n" +
                "        \"tags\": [\"Authentication\"],\n" +
                "        \"summary\": \"Create Bank Token\",\n" +
                "        \"operationId\": \"create_bank_token\",\n" +
                "        \"parameters\": [\n" +
                "          {\n" +
                "            \"name\": \"client_id\",\n" +
                "            \"in\": \"query\",\n" +
                "            \"required\": true,\n" +
                "            \"schema\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"description\": \"Team ID\"\n" +
                "            }\n" +
                "          }\n" +
                "        ],\n" +
                "        \"responses\": {\n" +
                "          \"200\": {\n" +
                "            \"description\": \"Successful Response\"\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    @Test
    void testParseOpenApiFromString() throws IOException {
        OpenApiModel model = parserService.parseOpenApi(sampleOpenApiJson);
        
        assertNotNull(model);
        assertEquals("3.1.0", model.getOpenApiVersion());
        assertEquals("Virtual Bank API", model.getInfo().getTitle());
        assertEquals("2.1", model.getInfo().getVersion());
    }

    @Test
    void testParseOpenApiFromInputStream() throws IOException {
        InputStream is = getClass().getResourceAsStream("/sample-openapi.json");
        assertNotNull(is, "Test resource file not found");
        
        OpenApiModel model = parserService.parseOpenApi(is);
        assertNotNull(model);
        assertNotNull(model.getOpenApiVersion());
    }

    @Test
    void testGetApiTitle() throws IOException {
        OpenApiModel model = parserService.parseOpenApi(sampleOpenApiJson);
        String title = parserService.getApiTitle(model);
        assertEquals("Virtual Bank API", title);
    }

    @Test
    void testGetApiVersion() throws IOException {
        OpenApiModel model = parserService.parseOpenApi(sampleOpenApiJson);
        String version = parserService.getApiVersion(model);
        assertEquals("2.1", version);
    }

    @Test
    void testPathOperations() throws IOException {
        OpenApiModel model = parserService.parseOpenApi(sampleOpenApiJson);
        
        assertNotNull(model.getPaths());
        assertTrue(model.getPaths().containsKey("/auth/bank-token"));
        
        Operation postOperation = model.getPaths().get("/auth/bank-token").getPost();
        assertNotNull(postOperation);
        assertEquals("Create Bank Token", postOperation.getSummary());
        assertEquals("create_bank_token", postOperation.getOperationId());
    }

    @Test
    void testParameters() throws IOException {
        OpenApiModel model = parserService.parseOpenApi(sampleOpenApiJson);
        Operation postOperation = model.getPaths().get("/auth/bank-token").getPost();
        
        assertNotNull(postOperation.getParameters());
        assertFalse(postOperation.getParameters().isEmpty());
        
        Parameter parameter = postOperation.getParameters().get(0);
        assertEquals("client_id", parameter.getName());
        assertEquals("query", parameter.getIn());
        assertTrue(parameter.isRequired());
        assertEquals("string", parameter.getSchema().getType());
    }
}