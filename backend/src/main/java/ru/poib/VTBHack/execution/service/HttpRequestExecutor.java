package ru.poib.VTBHack.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.execution.model.ExecutionConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для выполнения HTTP запросов
 */
@Slf4j
@Service
public class HttpRequestExecutor {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public HttpRequestExecutor(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Выполняет HTTP запрос
     * 
     * @param method HTTP метод (GET, POST, PUT, DELETE)
     * @param url полный URL запроса
     * @param headers заголовки запроса
     * @param body тело запроса (может быть null)
     * @param config конфигурация выполнения
     * @return результат выполнения запроса
     */
    public ExecutionResult execute(String method, String url, Map<String, String> headers, 
                                   Object body, ExecutionConfig config) {
        Instant startTime = Instant.now();
        
        try {
            // Создаем HTTP запрос
            HttpUriRequestBase request = createRequest(method, url);
            
            // Настраиваем таймауты из конфигурации
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(config.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS))
                    .setResponseTimeout(Timeout.of(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS))
                    .build();
            request.setConfig(requestConfig);
            
            // Устанавливаем заголовки
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    request.setHeader(entry.getKey(), entry.getValue());
                }
            }
            
            // Устанавливаем тело запроса
            if (body != null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                String bodyString;
                if (body instanceof String) {
                    bodyString = (String) body;
                } else {
                    bodyString = objectMapper.writeValueAsString(body);
                }
                request.setEntity(new StringEntity(bodyString, StandardCharsets.UTF_8));
            }
            
            // Выполняем запрос
            log.debug("Executing {} request to {}", method, url);
            return httpClient.execute(request, response -> {
                Instant responseTime = Instant.now();
                long durationMs = responseTime.toEpochMilli() - startTime.toEpochMilli();
                
                int statusCode = response.getCode();
                Map<String, String> responseHeaders = extractHeaders(response);
                String responseBody = extractBody(response);
                
                log.debug("Response received: status={}, duration={}ms", statusCode, durationMs);
                
                return new ExecutionResult(true, statusCode, responseHeaders, responseBody, durationMs, null);
            });
            
        } catch (IOException e) {
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.error("Network error for {} {}: {}", method, url, e.getMessage());
            return new ExecutionResult(false, 0, null, null, durationMs, 
                    "Network error: " + e.getMessage());
        } catch (Exception e) {
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.error("Unexpected error for {} {}: {}", method, url, e.getMessage(), e);
            return new ExecutionResult(false, 0, null, null, durationMs, 
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    private HttpUriRequestBase createRequest(String method, String url) {
        return switch (method.toUpperCase()) {
            case "GET" -> new HttpGet(url);
            case "POST" -> new HttpPost(url);
            case "PUT" -> new HttpPut(url);
            case "DELETE" -> new HttpDelete(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }
    
    private Map<String, String> extractHeaders(ClassicHttpResponse response) {
        Map<String, String> headers = new HashMap<>();
        response.headerIterator().forEachRemaining(header -> 
            headers.put(header.getName(), header.getValue())
        );
        return headers;
    }
    
    private String extractBody(ClassicHttpResponse response) {
        try {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return null;
            }
            return EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error extracting response body: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Результат выполнения HTTP запроса
     */
    public static class ExecutionResult {
        private final boolean success;
        private final int statusCode;
        private final Map<String, String> headers;
        private final String body;
        private final long durationMs;
        private final String errorMessage;
        
        public ExecutionResult(boolean success, int statusCode, Map<String, String> headers, 
                              String body, long durationMs, String errorMessage) {
            this.success = success;
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.durationMs = durationMs;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public Map<String, String> getHeaders() { return headers; }
        public String getBody() { return body; }
        public long getDurationMs() { return durationMs; }
        public String getErrorMessage() { return errorMessage; }
    }
}

