package lu.rescue_rush.spring.ws_ext.common;

public class WSPathUtils {

	public static String normalizeURI(String path) {
		if (path == null || path.isEmpty())
			return "/";
		String trimmed = path.replaceAll("^/+", "").replaceAll("/+$", "");
		return "/" + trimmed;
	}

}
