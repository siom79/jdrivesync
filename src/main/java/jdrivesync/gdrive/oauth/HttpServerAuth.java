package jdrivesync.gdrive.oauth;

import jdrivesync.exception.JDriveSyncException;
import jdrivesync.logging.LoggerFactory;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServerAuth {
	private static final Logger LOGGER = LoggerFactory.getLogger();
	private HttpServer httpServer;
	private int localPort;
	private final BasicFuture<String> codeFuture = new BasicFuture<>(null);

	public Future<String> start() {
		httpServer = ServerBootstrap.bootstrap()
				.addInterceptorFirst(createHttpRequestInterceptor())
				.create();
		try {
			httpServer.start();
			localPort = httpServer.getLocalPort();
			return codeFuture;
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to start internal web server: " + e.getMessage(), e);
		}
	}

	private HttpRequestInterceptor createHttpRequestInterceptor() {
		return (request, context) -> {
			String uri = request.getRequestLine().getUri();
			LOGGER.log(Level.INFO, "OAuth2-URI: " + uri);
			if (uri.startsWith("/?") && uri.length() > 2) {
				String queryParams = uri.substring(2);
				String[] params = queryParams.split("&");
				for (String param : params) {
					String[] paramParts = param.split("=");
					if (paramParts.length == 2) {
						if ("code".equals(paramParts[0])) {
							codeFuture.completed(paramParts[1]);
						}
					} else {
						LOGGER.log(Level.FINE, "Unsupported param: " + param);
					}
				}
			} else {
				LOGGER.log(Level.FINE, "Unsupported URI: " + uri);
			}
		};
	}

	public void stop() {
		httpServer.stop();
	}

	public int getLocalPort() {
		return localPort;
	}
}
