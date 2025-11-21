package ru.poib.VTBHack.mapping.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для семантического анализа текстов с использованием TF-IDF и cosine similarity
 */
@Service
public class SemanticAnalysisService {
    // Небольшой словарь синонимов для русского языка (можно дополнять)
    private static final Map<String, String> SYNONYMS = new HashMap<>();

    static {
        SYNONYMS.put("авторизация", "аутентификация");
        SYNONYMS.put("логин", "аутентификация");
        SYNONYMS.put("вход", "аутентификация");
        SYNONYMS.put("получение", "get");
        SYNONYMS.put("создание", "create");
        // Добавьте другие пары по мере необходимости
    }
    
    /**
     * Вычисляет семантическое сходство между двумя текстами
     * @return значение от 0.0 до 1.0 (1.0 - полное совпадение)
     */
    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }
        
        // Нормализуем тексты
        String normalized1 = normalizeText(text1);
        String normalized2 = normalizeText(text2);
        
        if (normalized1.equals(normalized2)) {
            return 1.0;
        }
        
        // Создаем TF-IDF векторы
        Map<String, Double> vector1 = createTfIdfVector(normalized1, Arrays.asList(normalized1, normalized2));
        Map<String, Double> vector2 = createTfIdfVector(normalized2, Arrays.asList(normalized1, normalized2));
        
        // Вычисляем cosine similarity
        return cosineSimilarity(vector1, vector2);
    }
    
    /**
     * Нормализует текст: приводим к нижнему регистру, удаляем лишние пробелы
     */
    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    
    /**
     * Создает TF-IDF вектор для текста
     */
    private Map<String, Double> createTfIdfVector(String text, List<String> documents) {
        Map<String, Double> vector = new HashMap<>();
        List<String> words = tokenize(text);
        
        if (words.isEmpty()) {
            return vector;
        }
        
        // Вычисляем TF (Term Frequency)
        Map<String, Integer> termFreq = new HashMap<>();
        for (String word : words) {
            termFreq.put(word, termFreq.getOrDefault(word, 0) + 1);
        }
        
        // Вычисляем IDF (Inverse Document Frequency)
        int docCount = documents.size();
        Map<String, Integer> docFreq = new HashMap<>();
        for (String doc : documents) {
            Set<String> uniqueWords = new HashSet<>(tokenize(doc));
            for (String word : uniqueWords) {
                docFreq.put(word, docFreq.getOrDefault(word, 0) + 1);
            }
        }
        
        // Вычисляем TF-IDF
        for (String word : termFreq.keySet()) {
            double tf = (double) termFreq.get(word) / words.size();
            int df = docFreq.getOrDefault(word, 1);
            double idf = Math.log((double) docCount / (df + 1)) + 1; // +1 чтобы избежать деления на 0
            vector.put(word, tf * idf);
        }
        
        return vector;
    }
    
    /**
     * Токенизирует текст на слова
     */
    private List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String normalized = normalizeText(text);
        return Arrays.stream(normalized.split("\\s+"))
                .map(word -> {
                    // Применяем синонимы (нормализуем слова)
                    String mapped = SYNONYMS.get(word);
                    return mapped != null ? mapped : word;
                })
                .filter(word -> word.length() > 2) // Игнорируем очень короткие слова
                .collect(Collectors.toList());
    }
    
    /**
     * Вычисляет cosine similarity между двумя векторами
     */
    private double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        if (vector1.isEmpty() || vector2.isEmpty()) {
            return 0.0;
        }
        
        // Получаем все уникальные ключи
        Set<String> allKeys = new HashSet<>(vector1.keySet());
        allKeys.addAll(vector2.keySet());
        
        if (allKeys.isEmpty()) {
            return 0.0;
        }
        
        // Вычисляем dot product и нормы
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (String key : allKeys) {
            double val1 = vector1.getOrDefault(key, 0.0);
            double val2 = vector2.getOrDefault(key, 0.0);
            
            dotProduct += val1 * val2;
            norm1 += val1 * val1;
            norm2 += val2 * val2;
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Находит наиболее похожий текст из списка
     */
    public Map.Entry<String, Double> findMostSimilar(String query, Map<String, String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        
        double maxSimilarity = -1.0;
        String bestMatch = null;
        
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            double similarity = calculateSimilarity(query, entry.getValue());
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatch = entry.getKey();
            }
        }
        
        return bestMatch != null ? new AbstractMap.SimpleEntry<>(bestMatch, maxSimilarity) : null;
    }
}


