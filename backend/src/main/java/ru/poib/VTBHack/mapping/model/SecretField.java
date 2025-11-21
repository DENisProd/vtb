package ru.poib.VTBHack.mapping.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Модель для представления секретного поля, которое требует ввода пользователем
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretField {
    /**
     * Имя поля/параметра (например, "api_key", "x-api-key", "password")
     */
    private String fieldName;
    
    /**
     * Тип параметра: header, query, path, body
     */
    private String fieldType;
    
    /**
     * Описание поля
     */
    private String description;
    
    /**
     * Тип данных поля (string, integer, etc.)
     */
    private String dataType;
    
    /**
     * Является ли поле обязательным
     */
    private boolean required;
    
    /**
     * Список эндпоинтов, использующих это секретное поле (формат: "METHOD /path")
     */
    private List<String> usedInEndpoints;
    
    /**
     * Причина, по которой поле считается секретом (например, "contains 'password' in name")
     */
    private String reason;
}




