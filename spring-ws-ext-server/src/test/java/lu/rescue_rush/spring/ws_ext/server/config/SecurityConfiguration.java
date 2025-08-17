package lu.rescue_rush.spring.ws_ext.server.config;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity(debug = false)
public class SecurityConfiguration {

	private static final Logger LOGGER = Logger.getLogger(SecurityConfiguration.class.getName());

	@Autowired
	private SecurityContextRepository securityContextRepository;
	@Autowired
	private CorsConfigurationSource corsConfigurationSource;
	@Autowired
	private UserDetailsService userDetailsService;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		LOGGER.info("Registered SecurityFilterChain");

		//@formatter:off
		return http
			.csrf(csrf -> csrf.disable())
			.formLogin(formLogin -> formLogin.disable())
			.httpBasic(httpBasic -> httpBasic.disable())
			.anonymous(a -> a.disable())
			.logout(logout -> logout.disable())
			
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
			.securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
			.userDetailsService(userDetailsService)
			
			.authorizeHttpRequests(auth -> auth
				.anyRequest().permitAll()
			)
			
			.build();
		//@formatter:on
	}
}
