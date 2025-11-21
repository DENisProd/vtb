package ru.poib.VTBHack.mapping.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для предзагрузки модели ИИ при старте приложения
 */
@Slf4j
@Service
public class ModelPreloaderService implements ApplicationRunner {
    
    // Таймаут для загрузки модели (в секундах) - 10 минут для больших моделей
    private static final int MODEL_PRELOAD_TIMEOUT_SECONDS = 600;
    
    // Флаг для отключения предзагрузки
    private static final boolean MODEL_PRELOAD_ENABLED = true;
    
    @Override
    public void run(ApplicationArguments args) {
        if (!MODEL_PRELOAD_ENABLED) {
            log.info("Model preloading is disabled");
            return;
        }
        
        // Запускаем предзагрузку асинхронно, чтобы не блокировать старт приложения
        CompletableFuture.runAsync(() -> {
            try {
                preloadModel();
            } catch (Exception e) {
                log.error("Error during model preloading", e);
            }
        });
    }
    
    /**
     * Предзагружает модель ИИ
     */
    public void preloadModel() {
        log.info("Starting AI model preload...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Определяем путь к Python скрипту
            String currentDir = System.getProperty("user.dir");
            Path pythonScriptPath = null;
            
            // Вариант 1: скрипт в ai/ относительно текущей директории
            Path path1 = Paths.get(currentDir, "ai", "preload_model.py");
            
            // Вариант 2: скрипт в ../ai/ (если мы в backend/)
            Path path2 = null;
            Path currentPath = Paths.get(currentDir);
            Path parentPath = currentPath.getParent();
            if (parentPath != null) {
                path2 = parentPath.resolve("ai").resolve("preload_model.py");
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
                log.warn("Model preload script not found. Checked paths: {}", pathsChecked);
                return;
            }
            
            log.info("Using preload script at: {}", pythonScriptPath);
            
            // Формируем команду для запуска Python скрипта
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("python3", pythonScriptPath.toString());
            
            // Настраиваем окружение
            File scriptDir = pythonScriptPath.getParent().toFile();
            processBuilder.directory(scriptDir);
            processBuilder.redirectErrorStream(false); // Разделяем stdout и stderr
            
            log.debug("Python command: {}", String.join(" ", processBuilder.command()));
            
            // Запускаем процесс
            Process process = processBuilder.start();
            log.info("Model preload process started, PID: {}", process.pid());
            
            // Читаем stderr (логи) в отдельном потоке
            StringBuilder stderrOutput = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrOutput.append(line).append("\n");
                        // Парсим уровень логирования из Python
                        if (line.contains("[INFO]")) {
                            String infoMsg = line.substring(line.indexOf("[INFO]") + 7).trim();
                            log.info("[Model Preload] {}", infoMsg);
                        } else if (line.contains("[ERROR]")) {
                            String errorMsg = line.substring(line.indexOf("[ERROR]") + 8).trim();
                            log.error("[Model Preload] {}", errorMsg);
                        } else {
                            log.debug("[Model Preload] {}", line);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error reading preload script stderr", e);
                }
            });
            stderrReader.start();
            
            // Читаем stdout
            StringBuilder stdoutOutput = new StringBuilder();
            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("Error reading preload script stdout", e);
                }
            });
            stdoutReader.start();
            
            // Ждем завершения процесса с таймаутом
            log.info("Waiting for model preload to complete (timeout: {}s)...", MODEL_PRELOAD_TIMEOUT_SECONDS);
            boolean finished = process.waitFor(MODEL_PRELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;
            
            if (!finished) {
                log.warn("Model preload timeout after {}ms", duration);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                stdoutReader.interrupt();
                stderrReader.interrupt();
                return;
            }
            
            // Ждем завершения потоков чтения
            stdoutReader.join(1000);
            stderrReader.join(1000);
            
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("Model preload completed successfully in {}ms", duration);
            } else {
                log.warn("Model preload completed with exit code {} in {}ms", exitCode, duration);
                if (!stderrOutput.toString().isEmpty()) {
                    log.debug("Preload script output: {}", stderrOutput.toString());
                }
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error during model preload after {}ms", duration, e);
        }
    }
}

