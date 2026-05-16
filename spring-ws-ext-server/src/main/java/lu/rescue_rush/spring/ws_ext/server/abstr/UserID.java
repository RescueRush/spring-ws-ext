package lu.rescue_rush.spring.ws_ext.server.abstr;

public interface UserID extends Comparable<UserID> {

	Object getId();

	@Override
	int compareTo(UserID o);

	@Override
	int hashCode();

	@Override
	boolean equals(Object obj);

}
