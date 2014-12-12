package me.osm.gazetteer.web;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.restexpress.Flags;
import org.restexpress.Request;
import org.restexpress.exception.UnauthorizedException;
import org.restexpress.preprocessor.HttpBasicAuthenticationPreprocessor;
import org.restexpress.route.Route;

public final class BasikAuthPreprocessor extends
		HttpBasicAuthenticationPreprocessor {
	
	public BasikAuthPreprocessor(String realm) {
		super(realm);
	}

	@Override
	public void process(Request request) {

		Route route = request.getResolvedRoute();

		if (route != null && (route.isFlagged(Flags.Auth.PUBLIC_ROUTE)
			|| route.isFlagged(Flags.Auth.NO_AUTHENTICATION)))
		{
			return;
		}
		
		super.process(request);
		
		if(!"admin".equals(request.getHeader(X_AUTHENTICATED_USER)) ||
				!checkPass(request.getHeader(X_AUTHENTICATED_PASSWORD))) {
			throw new UnauthorizedException();
		}
	}

	private boolean checkPass(String header) {
		return Hex.encodeHexString(DigestUtils.sha(header))
				.equals(Main.config().getAdminPasswordHash());
	}
}