
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.cors.CorsConfiguration;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry;
import lu.rescue_rush.spring.ws_ext.server.config.AuthHandshakeInterceptor;
import lu.rescue_rush.spring.ws_ext.server.config.WSExtServerConfiguration;

public class AutoConfigTest {

	@Test
	void autoConfigTest() {
		new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(WSExtServerConfiguration.class)).run(context -> {
			assertThat(context).hasSingleBean(WSExtServerConfiguration.class);
			assertThat(context).hasSingleBean(AuthHandshakeInterceptor.class);
			assertThat(context).hasSingleBean(WSExtServerMappingRegistry.class);
			assertThat(context).hasSingleBean(CorsConfiguration.class);
		});
	}

}
