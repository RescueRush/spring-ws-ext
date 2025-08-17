package lu.rescue_rush.spring.ws_ext.server.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class CorsConfig {

	@Bean
	public CorsConfiguration corsConfiguration() {
		final CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOriginPattern("*"); // Allow all origins
		config.addAllowedMethod("*"); // Allow all HTTP methods
		config.addAllowedHeader("*"); // Allow all headers
		return config;
	}
	
}
