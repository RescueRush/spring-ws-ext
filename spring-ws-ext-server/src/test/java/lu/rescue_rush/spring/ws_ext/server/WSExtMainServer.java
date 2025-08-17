package lu.rescue_rush.spring.ws_ext.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lu.rescue_rush.spring.ws_ext.server.config.CorsConfig;
import lu.rescue_rush.spring.ws_ext.server.config.WSExtServerConfiguration;

@SpringBootApplication(scanBasePackageClasses = {Test1WSServer.class, WSExtServerConfiguration.class, CorsConfig.class})
public class WSExtMainServer {

	public static WSExtMainServer INSTANCE;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(WSExtMainServer.class);

		app.run(args);
	}

}
