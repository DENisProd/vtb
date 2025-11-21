package ru.poib.VTBHack.mapping.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Модель для представления общего поля, используемого в нескольких эндпоинтах
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonField {
    /**
     * Имя поля/параметра (например, "client_id", "x-consent-id")
     */
    private String fieldName;
    
    /**
     * Тип параметра: header, query, path, body
     */
    private String fieldType;
    
    /**
     * Количество эндпоинтов, использующих это поле
     */
    private int usageCount;
    
    /**
     * Список эндпоинтов, использующих это поле (формат: "METHOD /path")
     */
    private List<String> usedInEndpoints;
    
    /**
     * Является ли поле обязательным хотя бы в одном эндпоинте
     */
    private boolean required;
    
    /**
     * Описание поля (берется из первого найденного использования)
     */
    private String description;
    
    /**
     * Тип данных поля (string, integer, etc.)
     */
    private String dataType;
}

