package lu.rescue_rush.spring.ws_ext.common;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class SelfReferencingBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof SelfReferencingBean) {
			((SelfReferencingBean) bean).setProxy(bean);
		}
		return bean;
	}

}
