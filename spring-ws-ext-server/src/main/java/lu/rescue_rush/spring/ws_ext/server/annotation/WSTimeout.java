package lu.rescue_rush.spring.ws_ext.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(
{ ElementType.TYPE, ElementType.METHOD })
public @interface WSTimeout {

	boolean value();

	long timeout() default WSExtServerHandler.TIMEOUT;

}
