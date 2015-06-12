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
import com.google.api.client.json.jackson2.JacksonFactory;
import jdrivesync.exception.JDriveSyncException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class Authorize {
    private static final String URN_IETF_WG_OAUTH_2_0_OOB = "urn:ietf:wg:oauth:2.0:oob";
    private final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/drive.readonly", "https://www.googleapis.com/auth/drive.metadata.readonly",
            "https://www.googleapis.com/auth/drive.appdata", "https://www.googleapis.com/auth/drive.apps.readonly",
            "https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile", "https://www.googleapis.com/auth/drive.scripts");
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static final String TOKEN_SERVER_URL = "https://accounts.google.com/o/oauth2/token";
    private static final String AUTHORIZATION_SERVER_URL = "https://accounts.google.com/o/oauth2/auth";
    private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    public Credential authorize() {
        String authorizationUrl = new AuthorizationCodeRequestUrl(AUTHORIZATION_SERVER_URL, OAuth2ClientCredentials.CLIENT_ID).setRedirectUri(URN_IETF_WG_OAUTH_2_0_OOB).setScopes(SCOPES).build();
        System.out.println("Please point your browser to the following URL and copy the access token provided after having authorized this application to access your Google Drive:");
        System.out.println(authorizationUrl);
        String code = readCode();
        AuthorizationCodeFlow codeFlow = new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT, JSON_FACTORY, new GenericUrl(
                TOKEN_SERVER_URL), new ClientParametersAuthentication(OAuth2ClientCredentials.CLIENT_ID,
                OAuth2ClientCredentials.CLIENT_SECRET), OAuth2ClientCredentials.CLIENT_ID,
                AUTHORIZATION_SERVER_URL
        ).setScopes(SCOPES).build();
        try {
            TokenResponse response = codeFlow.newTokenRequest(code).setScopes(SCOPES).setRedirectUri(URN_IETF_WG_OAUTH_2_0_OOB).execute();
            return codeFlow.createAndStoreCredential(response, null);
        } catch (IOException e) {
            throw new JDriveSyncException(JDriveSyncException.Reason.IOException, "Failed to execute token request and store credentials: " + e.getMessage(), e);
        }
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
