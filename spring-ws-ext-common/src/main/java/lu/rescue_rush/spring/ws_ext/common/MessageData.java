package lu.rescue_rush.spring.ws_ext.common;

public record MessageData(String destination, String packetId, Object payload) {

	public static enum TransactionDirection {
		IN, IN_OUT, OUT;
	}

}
