package lu.rescue_rush.spring.ws_ext.common.annotations;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

@Component
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(
{ ElementType.TYPE, ElementType.METHOD })
public @interface WSMapping {

	String path() default "";

}
