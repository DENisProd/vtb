package ru.poib.VTBHack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {HttpClientAutoConfiguration.class})
@EnableAsync
public class VtbHackApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = SpringApplication.run(VtbHackApplication.class, args);
		// DevConsoleTests dev = ctx.getBean(DevConsoleTests.class);
		// dev.testOpenApiParser();
		// dev.testBpmnParser();
		// dev.testPlantUmlParser();
		// dev.testMappingModule();
	}

    

    

    

    
}
