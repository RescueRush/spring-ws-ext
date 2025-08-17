package lu.rescue_rush.spring.ws_ext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lu.rescue_rush.spring.ws_ext.client.config.WSExtClientConfiguration;
import lu.rescue_rush.spring.ws_ext.server.Test1WSServer;
import lu.rescue_rush.spring.ws_ext.server.config.WSExtServerConfiguration;

@SpringBootApplication(scanBasePackageClasses = {Test1WSClient.class, Test1WSServer.class, WSExtClientConfiguration.class, WSExtServerConfiguration.class})
public class WSExtMain {

	public static WSExtMain INSTANCE;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(WSExtMain.class);

		app.run(args);
	}

}
