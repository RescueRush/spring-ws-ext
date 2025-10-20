package lu.rescue_rush.spring.ws_ext.server.abstr;

public interface UserID extends Comparable<UserID> {

	long getId();

	@Override
	default int compareTo(UserID o) {
		return Long.compare(this.getId(), o.getId());
	}

	@Override
	int hashCode();

	@Override
	boolean equals(Object obj);

}
