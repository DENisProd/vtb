package ru.poib.VTBHack.mapping.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.poib.VTBHack.mapping.model.AIVerificationReport;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;

/**
 * Сервис для проверки файлов OpenAPI и BPMN с помощью ИИ
 */
@Slf4j
@Service
public class AIVerificationService {
    
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    
    // Таймаут для выполнения Python скрипта (в секундах)
    private static final int PYTHON_SCRIPT_TIMEOUT_SECONDS = 300;
    
    // Флаг для отключения проверки ИИ (можно сделать через application.properties)
    private static final boolean AI_VERIFICATION_ENABLED = true;
    
    /**
     * Проверяет OpenAPI и BPMN файлы с помощью ИИ
     * 
     * @param openApiJson содержимое OpenAPI файла (JSON строка)
     * @param bpmnXml содержимое BPMN файла (XML строка)
     * @return отчет о проверке
     */
    public AIVerificationReport verifyFiles(String openApiJson, String bpmnXml) {
        long startTime = System.currentTimeMillis();
        log.info("Starting AI verification...");
        
        if (!AI_VERIFICATION_ENABLED) {
            log.info("AI verification is disabled, skipping");
            return null;
        }
        
        try {
            log.debug("Current directory: {}", System.getProperty("user.dir"));
            
            // Определяем путь к Python скрипту
            // user.dir может быть backend/ при запуске Spring Boot, поэтому проверяем оба варианта
            String currentDir = System.getProperty("user.dir");
            Path pythonScriptPath = null;
            
            // Вариант 1: скрипт в ai/ относительно текущей директории
            Path path1 = Paths.get(currentDir, "ai", "file_verification_service.py");
            
            // Вариант 2: скрипт в ../ai/ (если мы в backend/)
            Path path2 = null;
            Path currentPath = Paths.get(currentDir);
            Path parentPath = currentPath.getParent();
            if (parentPath != null) {
                path2 = parentPath.resolve("ai").resolve("file_verification_service.py");
            }
            
            if (Files.exists(path1)) {
                pythonScriptPath = path1;
            } else if (path2 != null && Files.exists(path2)) {
                pythonScriptPath = path2;
            } else {
                String pathsChecked = path1.toString();
                if (path2 != null) {
                    pathsChecked += " or " + path2.toString();
                }
                log.warn("Python verification script not found. Checked paths: {}", pathsChecked);
                return createFallbackReport("Python script not found. Checked: " + pathsChecked);
            }
            
            log.info("Using Python script at: {}", pythonScriptPath);
            
            // Создаем временные файлы для передачи данных
            log.debug("Creating temporary directory...");
            Path tempDir = Files.createTempDirectory("ai_verification_");
            Path openApiFile = tempDir.resolve("openapi.json");
            Path bpmnFile = tempDir.resolve("bpmn.xml");
            
            try {
                log.debug("Writing files to temp directory...");
                String openapiToWrite = openApiJson != null ? openApiJson : "";
                String bpmnToWrite = bpmnXml != null ? bpmnXml : "";
                Files.write(openApiFile, openapiToWrite.getBytes(StandardCharsets.UTF_8));
                Files.write(bpmnFile, bpmnToWrite.getBytes(StandardCharsets.UTF_8));
                log.debug("OpenAPI file written, size: {} bytes", openapiToWrite.length());
                log.debug("BPMN file written, size: {} bytes", bpmnToWrite.length());
                
                // Формируем команду для запуска Python скрипта
                log.info("Starting Python process...");
                ProcessBuilder processBuilder = new ProcessBuilder();
                List<String> baseCmd = resolvePythonCommand();
                List<String> command = new ArrayList<>(baseCmd);
                command.add(pythonScriptPath.toString());
                command.add(openApiFile.toString());
                command.add(bpmnFile.toString());
                processBuilder.command(command);
                
                // Настраиваем окружение
                // Используем директорию скрипта как рабочую директорию
                File scriptDir = pythonScriptPath.getParent().toFile();
                processBuilder.directory(scriptDir);
                // НЕ объединяем stderr с stdout, чтобы видеть логи отдельно
                processBuilder.redirectErrorStream(false);
                Map<String, String> env = processBuilder.environment();
                env.putIfAbsent("PYTHONIOENCODING", "UTF-8");
                env.putIfAbsent("PYTHONUTF8", "1");
                env.putIfAbsent("DISABLE_TRITON", "1");
                env.putIfAbsent("USE_ORT", "1");
                env.putIfAbsent("AI_VERIFICATION_PROFILE", "legacy");
                String model05b = System.getenv().getOrDefault("QWEN_MODEL_NAME", "Qwen/Qwen2.5-0.5B-Instruct");
                env.putIfAbsent("QWEN_MODEL_NAME", model05b);
                env.putIfAbsent("QWEN_CPU_MODEL", "Qwen/Qwen2.5-0.5B-Instruct");
                String hfToken = System.getenv("HF_TOKEN");
                if (hfToken != null && !hfToken.isEmpty()) {
                    env.put("HF_TOKEN", hfToken);
                }
                String remote = System.getenv().getOrDefault("USE_REMOTE_INFERENCE", "0");
                env.putIfAbsent("USE_REMOTE_INFERENCE", remote);
                
                log.debug("Python command: {}", String.join(" ", processBuilder.command()));
                log.debug("Working directory: {}", scriptDir.getAbsolutePath());
                
                // Запускаем процесс
                long processStartTime = System.currentTimeMillis();
                Process process = processBuilder.start();
                log.info("Python process started, PID: {}", process.pid());
                
                // Читаем stdout (JSON ответ) в отдельном потоке
                StringBuilder output = new StringBuilder();
                StringBuilder stderrBuf = new StringBuilder();
                Thread stdoutReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        log.error("Error reading Python stdout", e);
                    }
                });
                stdoutReader.start();
                
                // Читаем stderr (логи) в отдельном потоке и выводим в Java логи
                Thread stderrReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Парсим уровень логирования из Python
                            if (line.contains("[INFO]")) {
                                String infoMsg = line.substring(line.indexOf("[INFO]") + 7).trim();
                                log.info("[Python] {}", infoMsg);
                            } else if (line.contains("[DEBUG]")) {
                                String debugMsg = line.substring(line.indexOf("[DEBUG]") + 8).trim();
                                log.debug("[Python] {}", debugMsg);
                            } else {
                                // Просто выводим как есть
                                log.debug("[Python] {}", line);
                            }
                            stderrBuf.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        log.error("Error reading Python stderr", e);
                    }
                });
                stderrReader.start();
                
                // Ждем завершения процесса с таймаутом
                log.info("Waiting for Python process to complete (timeout: {}s)...", PYTHON_SCRIPT_TIMEOUT_SECONDS);
                boolean finished = process.waitFor(PYTHON_SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                long processDuration = System.currentTimeMillis() - processStartTime;
                
                if (!finished) {
                    log.error("Python verification script timeout after {}ms", processDuration);
                    process.destroyForcibly();
                    // Ждем еще немного для принудительного завершения
                    process.waitFor(2, TimeUnit.SECONDS);
                    stdoutReader.interrupt();
                    stderrReader.interrupt();
                    writeRawOutputToFile(output.toString(), "timeout");
                    return createFallbackReport("Verification timeout after " + PYTHON_SCRIPT_TIMEOUT_SECONDS + " seconds");
                }
                
                // Ждем завершения потоков чтения
                stdoutReader.join();
                stderrReader.join();
                log.info("Python process completed in {}ms", processDuration);
                String rawForLog = output.toString();
                String truncatedAll = rawForLog.length() > 1000 ? rawForLog.substring(0, 1000) + "..." : rawForLog;
                log.info("[Python] Raw model stdout (first 1000 chars): {}", truncatedAll);
                writeRawOutputToFile(rawForLog, "stdout");
                String rawStderr = stderrBuf.toString();
                if (!rawStderr.isEmpty()) {
                    String truncatedErr = rawStderr.length() > 1000 ? rawStderr.substring(0, 1000) + "..." : rawStderr;
                    log.info("[Python] Raw model stderr (first 1000 chars): {}", truncatedErr);
                    writeRawOutputToFile(rawStderr, "stderr");
                }
                
                int exitCode = process.exitValue();
                log.debug("Python process exit code: {}", exitCode);
                
                if (exitCode != 0) {
                    log.error("Python verification script exited with code: {}", exitCode);
                    String raw = output.toString();
                    String truncated = raw.length() > 1000 ? raw.substring(0, 1000) + "..." : raw;
                    log.info("[Python] Raw model output (first 1000 chars): {}", truncated);
                    writeRawOutputToFile(raw, "exit-" + exitCode);
                    return createRecommendationsOnlyReport(raw);
                }
                
                // Парсим JSON ответ
                log.debug("Parsing Python output, length: {} chars", output.length());
                String jsonOutput = output.toString().trim();
                
                if (jsonOutput.isEmpty()) {
                    log.warn("Python script returned empty output");
                    String raw = output.toString();
                    String truncated = raw.length() > 1000 ? raw.substring(0, 1000) + "..." : raw;
                    log.info("[Python] Raw model output (first 1000 chars): {}", truncated);
                    writeRawOutputToFile(raw, "empty-output");
                    return createRecommendationsOnlyReport(raw);
                }
                
                // Убираем возможные логи Python перед JSON
                int jsonStart = jsonOutput.indexOf("{");
                if (jsonStart < 0) {
                    String truncated = jsonOutput.length() > 1000 ? jsonOutput.substring(0, 1000) + "..." : jsonOutput;
                    log.info("[Python] Raw model output (first 1000 chars): {}", truncated);
                    log.error("No JSON found in output");
                    writeRawOutputToFile(output.toString(), "no-json");
                    return createRecommendationsOnlyReport(output.toString());
                }
                
                if (jsonStart > 0) {
                    log.debug("Removing {} characters before JSON", jsonStart);
                    jsonOutput = jsonOutput.substring(jsonStart);
                }
                
                log.debug("Parsing JSON (length: {} chars)...", jsonOutput.length());
                AIVerificationReport report;
                try {
                    report = objectMapper.readValue(jsonOutput, AIVerificationReport.class);
                } catch (Exception parseError1) {
                    String repaired = repairJsonOutput(jsonOutput);
                    try {
                        report = objectMapper.readValue(repaired, AIVerificationReport.class);
                        log.info("[Python] JSON was repaired before parsing");
                    } catch (Exception parseError2) {
                        AIVerificationReport lenient = parseReportLenient(jsonOutput);
                        if (lenient != null) {
                            report = lenient;
                        } else {
                            String truncated = jsonOutput.length() > 1000 ? jsonOutput.substring(0, 1000) + "..." : jsonOutput;
                            log.info("[Python] Raw model output (first 1000 chars): {}", truncated);
                            log.info("[Python] Could not parse JSON from model output, using fallback");
                            writeRawOutputToFile(output.toString(), "parse-error");
                            return createRecommendationsOnlyReport(output.toString());
                        }
                    }
                }

                if (report != null) {
                    report.setRawModelOutput(jsonOutput);
                    report.setRawModelStderr(rawStderr);
                }
                
                long totalDuration = System.currentTimeMillis() - startTime;
                log.info("AI verification completed in {}ms: {} errors, {} warnings, {} suggestions",
                    totalDuration, report.getTotalErrors(), report.getTotalWarnings(), report.getTotalSuggestions());
                
                return report;
                
            } finally {
                // Удаляем временные файлы
                try {
                    if (Files.exists(openApiFile)) {
                        Files.delete(openApiFile);
                    }
                    if (Files.exists(bpmnFile)) {
                        Files.delete(bpmnFile);
                    }
                    Files.delete(tempDir);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary files: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            log.error("Error during AI verification after {}ms", totalDuration, e);
            return createFallbackReport("Error: " + e.getMessage());
        }
    }

    public AIVerificationReport verifyFilesWithModel(String openApiJson, String bpmnXml, String modelName) {
        long startTime = System.currentTimeMillis();
        if (!AI_VERIFICATION_ENABLED) {
            return null;
        }
        try {
            String currentDir = System.getProperty("user.dir");
            Path pythonScriptPath;
            Path path1 = Paths.get(currentDir, "ai", "file_verification_service.py");
            Path path2 = Paths.get(currentDir).getParent() != null ? Paths.get(currentDir).getParent().resolve("ai").resolve("file_verification_service.py") : null;
            if (Files.exists(path1)) {
                pythonScriptPath = path1;
            } else if (path2 != null && Files.exists(path2)) {
                pythonScriptPath = path2;
            } else {
                return createFallbackReport("Python script not found");
            }
            Path tempDir = Files.createTempDirectory("ai_verification_");
            Path openApiFile = tempDir.resolve("openapi.json");
            Path bpmnFile = tempDir.resolve("bpmn.xml");
            try {
                String openapiToWrite = openApiJson != null ? openApiJson : "";
                String bpmnToWrite = bpmnXml != null ? bpmnXml : "";
                Files.write(openApiFile, openapiToWrite.getBytes(StandardCharsets.UTF_8));
                Files.write(bpmnFile, bpmnToWrite.getBytes(StandardCharsets.UTF_8));
                ProcessBuilder processBuilder = new ProcessBuilder();
                java.util.List<String> baseCmd = resolvePythonCommand();
                java.util.List<String> command = new java.util.ArrayList<>(baseCmd);
                command.add(pythonScriptPath.toString());
                command.add(openApiFile.toString());
                command.add(bpmnFile.toString());
                processBuilder.command(command);
                File scriptDir = pythonScriptPath.getParent().toFile();
                processBuilder.directory(scriptDir);
                processBuilder.redirectErrorStream(false);
                Map<String, String> env = processBuilder.environment();
                env.putIfAbsent("PYTHONIOENCODING", "UTF-8");
                env.putIfAbsent("PYTHONUTF8", "1");
                env.putIfAbsent("DISABLE_TRITON", "1");
                env.putIfAbsent("USE_ORT", "1");
                env.putIfAbsent("AI_VERIFICATION_PROFILE", "legacy");
                if (modelName != null && !modelName.isBlank()) {
                    env.put("QWEN_MODEL_NAME", modelName);
                    env.put("QWEN_CPU_MODEL", modelName);
                }
                String hfToken = System.getenv("HF_TOKEN");
                if (hfToken != null && !hfToken.isEmpty()) {
                    env.put("HF_TOKEN", hfToken);
                }
                String remote = System.getenv().getOrDefault("USE_REMOTE_INFERENCE", "0");
                env.putIfAbsent("USE_REMOTE_INFERENCE", remote);
                long processStartTime = System.currentTimeMillis();
                Process process = processBuilder.start();
                StringBuilder output = new StringBuilder();
                StringBuilder stderrBuf = new StringBuilder();
                Thread stdoutReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (IOException ignored) {}
                });
                stdoutReader.start();
                Thread stderrReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stderrBuf.append(line).append("\n");
                        }
                    } catch (IOException ignored) {}
                });
                stderrReader.start();
                boolean finished = process.waitFor(PYTHON_SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                    stdoutReader.interrupt();
                    stderrReader.interrupt();
                    return createFallbackReport("Verification timeout after " + PYTHON_SCRIPT_TIMEOUT_SECONDS + " seconds");
                }
                stdoutReader.join();
                stderrReader.join();
                int exitCode = process.exitValue();
                String jsonOutput = output.toString().trim();
                if (exitCode != 0 || jsonOutput.isEmpty()) {
                    return createRecommendationsOnlyReport(output.toString());
                }
                int jsonStart = jsonOutput.indexOf("{");
                if (jsonStart > 0) {
                    jsonOutput = jsonOutput.substring(jsonStart);
                }
                AIVerificationReport report;
                try {
                    report = objectMapper.readValue(jsonOutput, AIVerificationReport.class);
                } catch (Exception e) {
                    String repaired = repairJsonOutput(jsonOutput);
                    try {
                        report = objectMapper.readValue(repaired, AIVerificationReport.class);
                    } catch (Exception e2) {
                        return createRecommendationsOnlyReport(output.toString());
                    }
                }
                return report;
            } finally {
                try {
                    if (Files.exists(openApiFile)) {
                        Files.delete(openApiFile);
                    }
                    if (Files.exists(bpmnFile)) {
                        Files.delete(bpmnFile);
                    }
                    Files.delete(tempDir);
                } catch (IOException ignored) {}
            }
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            return createFallbackReport("Error: " + e.getMessage());
        }
    }

    private List<String> resolvePythonCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            if (canRunPython(Arrays.asList("py", "-3", "--version"))) return Arrays.asList("py", "-3");
            if (canRunPython(Arrays.asList("python", "--version"))) return Arrays.asList("python");
            if (canRunPython(Arrays.asList("python3", "--version"))) return Arrays.asList("python3");
        } else {
            if (canRunPython(Arrays.asList("python3", "--version"))) return Arrays.asList("python3");
            if (canRunPython(Arrays.asList("python", "--version"))) return Arrays.asList("python");
        }
        return Arrays.asList("python");
    }

    private boolean canRunPython(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(3, TimeUnit.SECONDS);
            if (!ok) {
                p.destroyForcibly();
                p.waitFor(1, TimeUnit.SECONDS);
            }
            int code = p.exitValue();
            return ok && code == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Создает отчет-заглушку в случае ошибки
     */
    private AIVerificationReport createFallbackReport(String message) {
        AIVerificationReport report = new AIVerificationReport();
        report.setOverallStatus("warning");
        report.setTotalErrors(0);
        report.setTotalWarnings(1);
        report.setTotalSuggestions(0);
        AIVerificationReport.FileVerificationResult result = new AIVerificationReport.FileVerificationResult();
        result.setStatus("warning");
        result.setErrors(java.util.Collections.emptyList());
        result.setWarnings(java.util.Collections.singletonList(message));
        result.setSuggestions(java.util.Collections.emptyList());
        result.setSummary(message);
        report.setOpenapi(result);
        report.setBpmn(new AIVerificationReport.FileVerificationResult());
        return report;
    }

    private String repairJsonOutput(String input) {
        if (input == null) return "{}";
        String s = input.trim();
        int start = s.indexOf('{');
        if (start > 0) s = s.substring(start);
        s = stripAfterBalancedObject(s);
        s = normalizeQuotes(s);
        s = balanceBrackets(s);
        s = removeTrailingCommas(s);
        return s;
    }

    private String stripAfterBalancedObject(String s) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return s.substring(0, i + 1);
                    }
                }
            }
        }
        return s;
    }

    private String normalizeQuotes(String s) {
        s = s.replace('\u201C', '"').replace('\u201D', '"').replace('\u2018', '\'').replace('\u2019', '\'');
        return s;
    }

    private String balanceBrackets(String s) {
        int objOpen = 0, objClose = 0, arrOpen = 0, arrClose = 0;
        boolean inString = false; boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) { escape = false; }
                else if (c == '\\') { escape = true; }
                else if (c == '"') { inString = false; }
            } else {
                if (c == '"') inString = true;
                else if (c == '{') objOpen++;
                else if (c == '}') objClose++;
                else if (c == '[') arrOpen++;
                else if (c == ']') arrClose++;
            }
        }
        StringBuilder sb = new StringBuilder(s);
        while (arrClose < arrOpen) { sb.append(']'); arrClose++; }
        while (objClose < objOpen) { sb.append('}'); objClose++; }
        return sb.toString();
    }

    private String removeTrailingCommas(String s) {
        return s.replaceAll(",\\s*([}\\\\]])", "$1");
    }

    private AIVerificationReport createFallbackReport(String message, String rawOutput) {
        return createRecommendationsOnlyReport(rawOutput);
    }

    private AIVerificationReport createRecommendationsOnlyReport(String rawOutput) {
        String warn = "ИИ анализ выполнен, но результат не удалось распарсить";
        String sugg = "ИИ анализ: " + (rawOutput != null ? rawOutput : "");
        AIVerificationReport.FileVerificationResult openRes = new AIVerificationReport.FileVerificationResult();
        openRes.setStatus("warning");
        openRes.setErrors(java.util.Collections.emptyList());
        openRes.setWarnings(java.util.Collections.singletonList(warn));
        openRes.setSuggestions(java.util.Collections.singletonList(sugg));
        openRes.setSummary("1 предупреждений, 1 рекомендаций");
        AIVerificationReport.FileVerificationResult bpmnRes = new AIVerificationReport.FileVerificationResult();
        bpmnRes.setStatus("warning");
        bpmnRes.setErrors(java.util.Collections.emptyList());
        bpmnRes.setWarnings(java.util.Collections.singletonList(warn));
        bpmnRes.setSuggestions(java.util.Collections.singletonList(sugg));
        bpmnRes.setSummary("1 предупреждений, 1 рекомендаций");
        AIVerificationReport report = new AIVerificationReport();
        report.setOpenapi(openRes);
        report.setBpmn(bpmnRes);
        report.setOverallStatus("warning");
        report.setTotalErrors(0);
        report.setTotalWarnings(2);
        report.setTotalSuggestions(2);
        return report;
    }

    private AIVerificationReport parseReportLenient(String json) {
        try {
            java.util.Map<?, ?> root = objectMapper.readValue(json, java.util.Map.class);
            AIVerificationReport r = new AIVerificationReport();
            Object overall = root.get("overall_status");
            if (overall == null) overall = root.get("overallStatus");
            r.setOverallStatus(overall instanceof String ? (String) overall : null);
            Object te = root.get("total_errors");
            if (te == null) te = root.get("totalErrors");
            r.setTotalErrors(te instanceof Number ? ((Number) te).intValue() : 0);
            Object tw = root.get("total_warnings");
            if (tw == null) tw = root.get("totalWarnings");
            r.setTotalWarnings(tw instanceof Number ? ((Number) tw).intValue() : 0);
            Object ts = root.get("total_suggestions");
            if (ts == null) ts = root.get("totalSuggestions");
            r.setTotalSuggestions(ts instanceof Number ? ((Number) ts).intValue() : 0);

            java.util.function.Function<Object, AIVerificationReport.FileVerificationResult> conv = o -> {
                if (!(o instanceof java.util.Map)) return null;
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
                AIVerificationReport.FileVerificationResult fr = new AIVerificationReport.FileVerificationResult();
                Object st = m.get("status");
                fr.setStatus(st instanceof String ? (String) st : null);
                java.util.List<String> toStrList = new java.util.ArrayList<>();
                Object errs = m.get("errors");
                if (errs instanceof java.util.List<?>) {
                    for (Object e : (java.util.List<?>) errs) {
                        toStrList.add(String.valueOf(e));
                    }
                }
                fr.setErrors(toStrList);
                toStrList = new java.util.ArrayList<>();
                Object warns = m.get("warnings");
                if (warns instanceof java.util.List<?>) {
                    for (Object e : (java.util.List<?>) warns) {
                        toStrList.add(String.valueOf(e));
                    }
                }
                fr.setWarnings(toStrList);
                toStrList = new java.util.ArrayList<>();
                Object suggs = m.get("suggestions");
                if (suggs instanceof java.util.List<?>) {
                    for (Object e : (java.util.List<?>) suggs) {
                        toStrList.add(String.valueOf(e));
                    }
                }
                fr.setSuggestions(toStrList);
                Object summ = m.get("summary");
                fr.setSummary(summ instanceof String ? (String) summ : null);
                return fr;
            };

            r.setOpenapi(conv.apply(root.get("openapi")));
            r.setBpmn(conv.apply(root.get("bpmn")));
            return r;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeRawOutputToFile(String content, String suffix) {
        try {
            String dirEnv = System.getenv("AI_VERIFICATION_LOG_DIR");
            Path dir = dirEnv != null && !dirEnv.isBlank()
                    ? Paths.get(dirEnv)
                    : Paths.get(System.getProperty("user.dir"), "ai-logs");
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String name = "model_output_" + ts + (suffix != null && !suffix.isBlank() ? ("_" + suffix) : "") + ".log";
            Path file = dir.resolve(name);
            byte[] bytes = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
            Files.write(file, bytes);
            log.info("[Python] Raw output saved to: {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to write raw output: {}", e.getMessage());
        }
    }
    
    /**
     * Альтернативный метод: проверка через HTTP API (если Python сервис запущен как REST API)
     * Можно использовать вместо прямого вызова Python скрипта
     */
    public AIVerificationReport verifyFilesViaAPI(String openApiJson, String bpmnXml) {
        // TODO: Реализовать вызов через HTTP, если Python сервис будет запущен как REST API
        // Это может быть полезно для продакшена
        return verifyFiles(openApiJson, bpmnXml);
    }
}

