package lu.rescue_rush.spring.ws_ext.server.abstr;

public interface DynamicAutowireFactory {

	Class<?> getProvidedType();

	Object create(String qualifier);

}
