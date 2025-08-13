
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.cors.CorsConfiguration;

import lu.rescue_rush.spring.ws_ext.WSMappingRegistry;
import lu.rescue_rush.spring.ws_ext.config.AuthHandshakeInterceptor;
import lu.rescue_rush.spring.ws_ext.config.WSExtConfiguration;

public class AutoConfigTest {

	@Test
	void autoConfigTest() {
		new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(WSExtConfiguration.class)).run(context -> {
			assertThat(context).hasSingleBean(WSExtConfiguration.class);
			assertThat(context).hasSingleBean(AuthHandshakeInterceptor.class);
			assertThat(context).hasSingleBean(WSMappingRegistry.class);
			assertThat(context).hasSingleBean(CorsConfiguration.class);
		});
	}

}
