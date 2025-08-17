
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry;
import lu.rescue_rush.spring.ws_ext.client.config.WSExtClientConfiguration;

public class AutoConfigTest {

	@Test
	void autoConfigTest() {
		new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(WSExtClientConfiguration.class)).run(context -> {
			assertThat(context).hasSingleBean(WSExtClientConfiguration.class);
			assertThat(context).hasSingleBean(WSExtClientMappingRegistry.class);
		});
	}

}
