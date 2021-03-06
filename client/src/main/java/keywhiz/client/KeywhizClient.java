/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import javax.ws.rs.core.HttpHeaders;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.CreateClientRequest;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.api.LoginRequest;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import org.apache.http.HttpStatus;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Client for interacting with the Keywhiz Server.
 *
 * Facilitates the manipulation of Clients, Groups, Secrets and the connections between them.
 */
public class KeywhizClient {
  public static final MediaType JSON = MediaType.parse("application/json");

  public static class MalformedRequestException extends IOException {

    @Override public String getMessage() {
      return "Malformed request syntax from client (400)";
    }
  }
  public static class UnauthorizedException extends IOException {

    @Override public String getMessage() {
      return "Not allowed to login, password may be incorrect (401)";
    }
  }
  public static class ForbiddenException extends IOException {

    @Override public String getMessage() {
      return "Resource forbidden (403)";
    }
  }
  public static class NotFoundException extends IOException {

    @Override public String getMessage() {
      return "Resource not found (404)";
    }
  }
  public static class UnsupportedMediaTypeException extends IOException {

    @Override public String getMessage() {
      return "Resource media type is incorrect or incompatible (415)";
    }
  }
  public static class ConflictException extends IOException {

    @Override public String getMessage() {
      return "Conflicting resource (409)";
    }
  }
  public static class ValidationException extends IOException {

    @Override public String getMessage() {
      return "Malformed request semantics from client (422)";
    }
  }

  private final ObjectMapper mapper;
  private final OkHttpClient client;
  private final HttpUrl baseUrl;

  public KeywhizClient(ObjectMapper mapper, OkHttpClient client, HttpUrl baseUrl) {
    this.mapper = checkNotNull(mapper);
    this.client = checkNotNull(client);
    this.baseUrl = checkNotNull(baseUrl);
  }

  /**
   * Login to the Keywhiz server.
   *
   * Future requests made using this client instance will be authenticated.
   * @param username login username
   * @param password login password
   * @throws IOException if a network IO error occurs
   */
  public void login(String username, char[] password) throws IOException {
    httpPost(baseUrl.resolve("/admin/login"), LoginRequest.from(username, password));
  }

  public List<Group> allGroups() throws IOException {
    String response = httpGet(baseUrl.resolve("/admin/groups/"));
    return mapper.readValue(response, new TypeReference<List<Group>>() {});
  }

  public GroupDetailResponse createGroup(String name, String description) throws IOException {
    checkArgument(!name.isEmpty());
    String response = httpPost(baseUrl.resolve("/admin/groups"), new CreateGroupRequest(name, description));
    return mapper.readValue(response, GroupDetailResponse.class);
  }

  public GroupDetailResponse groupDetailsForId(int groupId) throws IOException {
    String response = httpGet(baseUrl.resolve(format("/admin/groups/%d", groupId)));
    return mapper.readValue(response, GroupDetailResponse.class);
  }

  public void deleteGroupWithId(int groupId) throws IOException {
    httpDelete(baseUrl.resolve(format("/admin/groups/%d", groupId)));
  }

  public List<SanitizedSecret> allSecrets() throws IOException {
    String response = httpGet(baseUrl.resolve("/admin/secrets"));
    return mapper.readValue(response, new TypeReference<List<SanitizedSecret>>() {});
  }

  public SecretDetailResponse createSecret(String name, String description, byte[] content, boolean withVersion,
      ImmutableMap<String, String> metadata) throws IOException {
    checkArgument(!name.isEmpty());
    checkArgument(content.length > 0, "Content must not be empty");

    String b64Content = Base64.getEncoder().encodeToString(content);
    CreateSecretRequest request = new CreateSecretRequest(name, description, b64Content,
        withVersion, metadata);
    String response = httpPost(baseUrl.resolve("/admin/secrets"), request);
    return mapper.readValue(response, SecretDetailResponse.class);
  }

  public SecretDetailResponse secretDetailsForId(int secretId) throws IOException {
    String response = httpGet(baseUrl.resolve(format("/admin/secrets/%d", secretId)));
    return mapper.readValue(response, SecretDetailResponse.class);
  }

  public void deleteSecretWithId(int secretId) throws IOException {
    httpDelete(baseUrl.resolve(format("/admin/secrets/%d", secretId)));
  }

  public <T> List<SanitizedSecret> generateSecrets(String generatorName, T params) throws IOException {
    checkArgument(!generatorName.isEmpty());
    String response = httpPost(baseUrl.resolve(
        format("/admin/secrets/generators/%s", generatorName)),
        params);
    return mapper.readValue(response, new TypeReference<List<SanitizedSecret>>() {});
  }

  public <T> List<SanitizedSecret> batchGenerateSecrets(String generatorName, List<T> params) throws IOException {
    checkArgument(!generatorName.isEmpty());
    String response = httpPost(baseUrl.resolve(
        format("/admin/secrets/generators/%s/batch", generatorName)),
        params);
    return mapper.readValue(response, new TypeReference<List<SanitizedSecret>>() {});
  }

  public List<Client> allClients() throws IOException {
    String httpResponse = httpGet(baseUrl.resolve("/admin/clients/"));
    return mapper.readValue(httpResponse, new TypeReference<List<Client>>() {});
  }

  public ClientDetailResponse createClient(String name) throws IOException {
    checkArgument(!name.isEmpty());
    String response = httpPost(baseUrl.resolve("/admin/clients"), new CreateClientRequest(name));
    return mapper.readValue(response, ClientDetailResponse.class);
  }

  public ClientDetailResponse clientDetailsForId(int clientId) throws IOException {
    String response = httpGet(baseUrl.resolve(format("/admin/clients/%d", clientId)));
    return mapper.readValue(response, ClientDetailResponse.class);
  }

  public void deleteClientWithId(int clientId) throws IOException {
    httpDelete(baseUrl.resolve(format("/admin/clients/%d", clientId)));
  }

  public void enrollClientInGroupByIds(int clientId, int groupId) throws IOException {
    httpPut(baseUrl.resolve(format("/admin/memberships/clients/%d/groups/%d", clientId, groupId)));
  }

  public void evictClientFromGroupByIds(int clientId, int groupId) throws IOException {
    httpDelete(baseUrl.resolve(format("/admin/memberships/clients/%d/groups/%d", clientId, groupId)));
  }

  public void grantSecretToGroupByIds(int secretId, int groupId) throws IOException {
    httpPut(baseUrl.resolve(format("/admin/memberships/secrets/%d/groups/%d", secretId, groupId)));
  }

  public void revokeSecretFromGroupByIds(int secretId, int groupId) throws IOException {
    httpDelete(baseUrl.resolve(format("/admin/memberships/secrets/%d/groups/%d", secretId, groupId)));
  }

  public Client getClientByName(String name) throws IOException {
    checkArgument(!name.isEmpty());
    String response = httpGet(baseUrl.resolve(format("/admin/clients?name=%s", name)));
    return mapper.readValue(response, Client.class);
  }

  public Group getGroupByName(String name) throws IOException {
    checkArgument(!name.isEmpty());
    String response = httpGet(baseUrl.resolve(format("/admin/groups?name=%s", name)));
    return mapper.readValue(response, Group.class);
  }

  public SanitizedSecret getSanitizedSecretByNameAndVersion(String name, String version) throws IOException {
    checkArgument(!name.isEmpty());
    String response = httpGet(baseUrl.resolve(
        format("/admin/secrets?name=%s&version=%s", name, version)));
    return mapper.readValue(response, SanitizedSecret.class);
  }

  public List<String> getVersionsForSecretName(String name) throws IOException {
    checkNotNull(name);
    String response = httpGet(baseUrl.resolve(format("/admin/secrets/versions?name=%s", name)));
    return mapper.readValue(response, new TypeReference<List<String>>() {});
  }

  public boolean isLoggedIn() throws IOException{
    HttpUrl url = baseUrl.resolve("/admin/me");
    Call call = client.newCall(new Request.Builder().get().url(url).build());
    return call.execute().code() != HttpStatus.SC_UNAUTHORIZED;
  }

  /**
   * Maps some of the common HTTP errors to the corresponding exceptions.
   */
  private void throwOnCommonError(Response response) throws IOException {
    int status = response.code();
    switch (status) {
      case HttpStatus.SC_BAD_REQUEST:
        throw new MalformedRequestException();
      case HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE:
        throw new UnsupportedMediaTypeException();
      case HttpStatus.SC_NOT_FOUND:
        throw new NotFoundException();
      case HttpStatus.SC_UNAUTHORIZED:
        throw new UnauthorizedException();
      case HttpStatus.SC_FORBIDDEN:
        throw new ForbiddenException();
      case HttpStatus.SC_CONFLICT:
        throw new ConflictException();
      case HttpStatus.SC_UNPROCESSABLE_ENTITY:
        throw new ValidationException();
    }
    if (status >= 400) {
      throw new IOException("Unexpected status code on response: " + status);
    }
  }

  private String httpGet(HttpUrl url) throws IOException {
    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    Response response = client.newCall(request).execute();
    throwOnCommonError(response);
    return response.body().string();
  }

  private String httpPost(HttpUrl url, Object content) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(content));
    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .addHeader(HttpHeaders.CONTENT_TYPE, JSON.toString())
        .build();

    Response response = client.newCall(request).execute();
    throwOnCommonError(response);
    return response.body().string();
  }

  private String httpPut(HttpUrl url) throws IOException {
    Request request = new Request.Builder()
        .url(url)
        .put(RequestBody.create(MediaType.parse("text/plain"), ""))
        .build();

    Response response = client.newCall(request).execute();
    throwOnCommonError(response);
    return response.body().string();
  }

  private String httpDelete(HttpUrl url) throws IOException {
    Request request = new Request.Builder()
        .url(url)
        .delete()
        .build();

    Response response = client.newCall(request).execute();
    throwOnCommonError(response);
    return response.body().string();
  }
}
