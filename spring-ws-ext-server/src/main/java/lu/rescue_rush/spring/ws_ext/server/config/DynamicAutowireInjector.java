package lu.rescue_rush.spring.ws_ext.server.config;

import java.lang.reflect.Field;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

import lu.rescue_rush.spring.ws_ext.server.abstr.DynamicAutowireFactory;

@Component
public class DynamicAutowireInjector implements InstantiationAwareBeanPostProcessor {

	private final ConfigurableListableBeanFactory beanFactory;
	private final List<DynamicAutowireFactory> factories;
	private final AutowireCapableBeanFactory autowireFactory;

	@Autowired
	public DynamicAutowireInjector(ConfigurableListableBeanFactory beanFactory, List<DynamicAutowireFactory> factories,
			AutowireCapableBeanFactory autowireFactory) {
		this.beanFactory = beanFactory;
		this.factories = factories;
		this.autowireFactory = autowireFactory;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {

		for (Field field : bean.getClass().getDeclaredFields()) {
			if (!field.isAnnotationPresent(Autowired.class) || !field.isAnnotationPresent(Qualifier.class)) {
				continue;
			}

			String qualifier = field.getAnnotation(Qualifier.class).value();
			Class<?> fieldType = field.getType();

			factories.stream().filter(factory -> factory.getProvidedType().isAssignableFrom(fieldType)).findFirst().ifPresent(factory -> {
				if (!beanFactory.containsBean(qualifier)) {
					Object instance = factory.create(qualifier);

					autowireFactory.autowireBean(instance);
					autowireFactory.initializeBean(instance, qualifier);
					
					beanFactory.registerSingleton(qualifier, instance);
				}

				Object dependency = beanFactory.getBean(qualifier);
				field.setAccessible(true);
				try {
					field.set(bean, dependency);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			});
		}

		return pvs;
	}
}
