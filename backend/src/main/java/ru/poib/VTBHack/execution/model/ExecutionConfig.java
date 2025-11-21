package ru.poib.VTBHack.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Конфигурация окружения для выполнения тестов
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionConfig {
    /**
     * Базовый URL для API запросов
     */
    private String baseUrl;
    
    /**
     * Глобальные заголовки для всех запросов
     */
    private Map<String, String> defaultHeaders;
    
    /**
     * Конфигурация аутентификации
     */
    private AuthConfig authConfig;
    
    /**
     * Таймаут для запросов в миллисекундах (по умолчанию 30000)
     */
    private int requestTimeoutMs = 30000;
    
    /**
     * Таймаут для подключения в миллисекундах (по умолчанию 10000)
     */
    private int connectionTimeoutMs = 10000;
    
    /**
     * Количество повторных попыток при ошибках (по умолчанию 0)
     */
    private int retryCount = 0;
    
    /**
     * Задержка между повторными попытками в миллисекундах (по умолчанию 1000)
     */
    private long retryDelayMs = 1000;
    
    /**
     * Максимальное время выполнения всего процесса в миллисекундах (по умолчанию 120000 = 2 минуты)
     */
    private long maxExecutionTimeMs = 120000;
    
    /**
     * Конфигурация аутентификации
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthConfig {
        /**
         * Тип аутентификации: NONE, BASIC, BEARER, API_KEY
         */
        private AuthType type = AuthType.NONE;
        
        /**
         * Значение токена/ключа/пароля
         */
        private String value;
        
        /**
         * Имя пользователя (для BASIC)
         */
        private String username;
        
        /**
         * Пароль (для BASIC)
         */
        private String password;
        
        /**
         * Имя заголовка для API_KEY
         */
        private String headerName;
        
        public enum AuthType {
            NONE, BASIC, BEARER, API_KEY
        }
    }
}

