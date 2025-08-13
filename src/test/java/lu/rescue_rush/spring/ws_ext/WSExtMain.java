package lu.rescue_rush.spring.ws_ext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lu.rescue_rush.spring.ws_ext.config.CorsConfig;
import lu.rescue_rush.spring.ws_ext.config.WSExtConfiguration;

@SpringBootApplication(scanBasePackageClasses = {Test1WS.class, WSExtConfiguration.class, CorsConfig.class})
public class WSExtMain {

	public static WSExtMain INSTANCE;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(WSExtMain.class);

		app.run(args);
	}

}
