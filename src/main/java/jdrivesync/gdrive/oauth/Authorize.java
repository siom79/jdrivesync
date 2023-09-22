/*
 * Copyright (c) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package jdrivesync.gdrive.oauth;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import jdrivesync.exception.JDriveSyncException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Authorize {
	private static final String LOCALHOST = "http://127.0.0.1:";
	private final List<String> SCOPES = List.of("https://www.googleapis.com/auth/drive");
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
	private static final String AUTHORIZATION_SERVER_URL = "https://oauth2.googleapis.com/token";
	private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	public Credential authorize() {
		HttpServerAuth httpServerAuth = new HttpServerAuth();
		Future<String> codeFuture = httpServerAuth.start();
		System.out.println("Please point your browser to the following URL and copy the access token provided after having authorized this application to access your Google Drive:");
		System.out.println(createAuthUrl(httpServerAuth.getLocalPort()));
		AuthorizationCodeFlow codeFlow = new AuthorizationCodeFlow.Builder(
				BearerToken.formEncodedBodyAccessMethod(),
				HTTP_TRANSPORT,
				JSON_FACTORY,
				new GenericUrl(TOKEN_SERVER_URL),
				new ClientParametersAuthentication(OAuth2ClientCredentials.CLIENT_ID,
						OAuth2ClientCredentials.CLIENT_SECRET),
				OAuth2ClientCredentials.CLIENT_ID,
				AUTHORIZATION_SERVER_URL
		).setScopes(SCOPES).build();
		try {
			TokenResponse response = codeFlow.newTokenRequest(codeFuture.get()).setScopes(SCOPES).setRedirectUri(LOCALHOST + httpServerAuth.getLocalPort()).execute();
			return codeFlow.createAndStoreCredential(response, null);
		} catch (IOException | ExecutionException | InterruptedException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to execute token request and store credentials: " + e.getMessage(), e);
		}
	}

	private String createAuthUrl(int localPort) {
		// see https://developers.google.com/identity/protocols/oauth2/native-app?hl=de
		return "https://accounts.google.com/o/oauth2/v2/auth?" +
				"scope="+urlEncode("https://www.googleapis.com/auth/drive")+"&" +
				"response_type=code&" +
				"redirect_uri=" + urlEncode(LOCALHOST + localPort) + "&" +
				"client_id=" + urlEncode(OAuth2ClientCredentials.CLIENT_ID);
	}

	private String urlEncode(String str) {
		return URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	private String readCode() {
		try {
			System.out.println("Please enter the code: ");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String line = reader.readLine();
			if (line != null && line.length() > 0) {
				return line;
			}
			throw new JDriveSyncException(JDriveSyncException.Reason.IllegalStateException, "Please provide a valid code.");
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to read from stdin: " + e.getMessage(), e);
		}
	}
}
