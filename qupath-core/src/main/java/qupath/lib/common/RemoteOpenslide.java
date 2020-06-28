package qupath.lib.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

// TODO: Error management
public class RemoteOpenslide {

	private final static Logger logger = LoggerFactory.getLogger(RemoteOpenslide.class);

	private static URI host;

	private static AuthType authType = AuthType.UNAUTHENTICATED; // TODO: Implement. Avoid unnecessary API calls when GUEST and send proper Authentication based on AuthType
	private static String token = "";
	private static String username;
	private static String password;

	/**
	 * A GUID provided by the Azure Active Directory, which is unique and consistent for every user.
	 * @see <a href="https://docs.microsoft.com/fi-fi/onedrive/find-your-office-365-tenant-id">Microsoft documentation</a>
	 */
	private static String userId = "";

	/**
	 * The GUID of the Azure AD Tenant (= Organization) this user belongs to.
	 */
	private static String tenantId = "";

	private static List<String> roles = new ArrayList<>();

	public static String getUserId() {
		return userId;
	}

	public static void setUserId(String userId) {
		RemoteOpenslide.userId = userId;
	}

	public static String getTenantId() {
		return tenantId;
	}

	public static void setTenantId(String tenantId) {
		RemoteOpenslide.tenantId = tenantId;
	}

	public static void setHost(String host) {
		if (host == null) {
			RemoteOpenslide.host = null;
		} else {
			RemoteOpenslide.host = URI.create(host);
		}
	}

	public static URI getHost() {
		return host;
	}

	public static boolean isConnected() {
		return host != null;
	}

	public static void setAuthentication(String username, String password) {
		RemoteOpenslide.authType = AuthType.USERNAME;
		RemoteOpenslide.username = username;
		RemoteOpenslide.password = password;
	}

	public static String getToken() {
		return token;
	}

	public static void setToken(String token) {
		RemoteOpenslide.authType = AuthType.TOKEN;
		RemoteOpenslide.token = token;
	}

	/* Roles and Permissions*/

	public static boolean hasRole(String role) {
		return roles.contains(role);
	}

	public static boolean hasRole(String... temp) {
		List<String> permitted = List.of(temp);

		return roles.stream().anyMatch(permitted::contains);
	}

	/**
	 * User is treated as a owner when ...
	 * 	  a) The ownerId is the same as userId and the user has the role MANAGE_PERSONAL PROJECTS.
	 * 	  b) The ownerId is the same as tenantId and the user has the the role MANAGE_PROJECTS.
	 * @param ownerId ID of project or workspace owner.
	 * @return True if owner (=has write permissions)
	 */
	public static boolean isOwner(String ownerId) {
		return (userId.equals(ownerId) && roles.contains("MANAGE_PERSONAL_PROJECTS")) ||
				(tenantId.equals(ownerId) && roles.contains("MANAGE_PROJECTS"));
	}

	private static Map<String, Boolean> permissions = new HashMap<>();

	/**
	 * This method checks if the user is authorized to edit a given ID.
	 * Use isOwner(String ownerId) if the ownerId is available;
	 * use this method only when only the object ID is available.
	 * @param id Project or workspace Id.
	 * @return True if has write permissions.
	 */
	public static boolean hasPermission(String id) {
		if (permissions.containsKey(id)) {
			return permissions.get(id);
		}

		var response = get("/api/v0/users/write/" + e(id));

		if (response.isEmpty()) {
			return false;
		}

		var result = Boolean.parseBoolean(response.get().body());
		permissions.put(id, result);

		return result;
	}

	/* Authentication */

	public static boolean login(String username, String password) {
		setAuthentication(username, password);

		var response = get("/api/v0/users/login");

		if (response.isEmpty() || response.get().statusCode() != 200) {
			setAuthentication(null, null);
			return false;
		}

		JsonObject result = JsonParser.parseString(response.get().body()).getAsJsonObject();

		setUserId(result.get("userId").getAsString());
		setTenantId(result.get("organizationId").getAsString());

		JsonArray permissions = result.get("roles").getAsJsonArray();
		for (JsonElement element : permissions) {
			String permission = element.getAsString();
			roles.add(permission);
		}

		return true;
	}

	public static boolean validate(String token) {
		setToken(token);

		var response = get("/api/v0/users/verify");

		if (response.isEmpty()) {
			setToken("");
			return false;
		}

		JsonArray roles = JsonParser.parseString(response.get().body()).getAsJsonArray();
		for (JsonElement role : roles) {
			String permission = role.getAsString().toUpperCase();
			RemoteOpenslide.roles.add(permission);
		}

		return true;
	}

	public static void logout() {
		setHost(null);
		setToken("");
		setAuthentication(null, null);
		roles.clear();
		permissions.clear();
	}

	/* Projects */

	public static Optional<InputStream> downloadProject(String projectId) {
		try { // todo: fix this piece of shit code; only changed ofString() -> ofInputStream()
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(host.resolve(
							"/api/v0/projects/" + e(projectId)
					))
					.build();

			return Optional.of(client.send(request, BodyHandlers.ofInputStream()).body());
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP GET request", e);
		}

		return Optional.empty();
	}

	public static Result uploadProject(String projectId, File projectFile) {
		try {
			String boundary = new BigInteger(256, new Random()).toString();
			Map<Object, Object> data = new LinkedHashMap<>();
			data.put("project", projectFile.toPath());

			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(host.resolve(
						"/api/v0/projects/" + e(projectId)
					))
					.headers("token", token)
					.POST(ofMimeMultipartData(data, boundary))
					.header("Content-Type", "multipart/form-data;boundary=" + boundary)
					.build();

			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			return response.statusCode() == 200 ? Result.OK : Result.FAIL;
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making uploading project", e);
		}

		return Result.FAIL;
	}

	public static Optional<String> createPersonalProject(String projectName) {
		var response = post(
		"/api/v0/projects/personal",
			Map.of(
			"project-name", projectName
			)
		);

		if (response.isEmpty() || response.get().statusCode() != 200) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	/**
	 * Creates a new project and places it inside given workspace.
	 * @param workspaceId Workspace Id, null if personal project.
	 * @param projectName Name of the project.
	 * @return Result.OK if status code 200 else Result.FAIL.
	 */
	public static Result createProject(String workspaceId, String projectName) {
		var response = post(
		"/api/v0/projects",
			Map.of(
			"workspace-id", workspaceId,
			"project-name", projectName
			)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result editProject(String projectId, String name, String description) {
		var response = put(
		"/api/v0/projects/" + e(projectId),
			Map.of(
			"name", name,
			"description", description
			)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result deleteProject(String projectName) {
		var response = delete("/api/v0/projects/" + e(projectName));

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	/* Slides */

	public static Optional<JsonArray> getSlides() {
		var response = get("/api/v0/slides/");

		if (response.isEmpty()) {
			return Optional.empty();
		}

		JsonArray slides = JsonParser.parseString(response.get().body()).getAsJsonArray();
		return Optional.of(slides);
	}

	public static Optional<String> getSlidesV1() {
		var response = get("/api/v1/slides/");

		if (response.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	public static Optional<JsonObject> getSlideProperties(String slideId) {
		var response = get("/api/v0/slides/" + e(slideId));

		if (response.isEmpty() || response.get().statusCode() == 404) {
			return Optional.empty();
		}

		JsonObject slides = JsonParser.parseString(response.get().body()).getAsJsonObject();
		return Optional.of(slides);
	}

	public static Result editSlide(String slideId, String name) {
		var response = put(
			"/api/v0/slides/" + e(slideId),
			Map.of(
				"slide-name", name
			)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result deleteSlide(String slideId) {
		var response = delete("/api/v0/slides/" + e(slideId));

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result uploadSlideChunk(String fileName, long fileSize, byte[] buffer, int chunkSize, int chunkIndex) throws IOException, InterruptedException {
		String boundary = new BigInteger(256, new Random()).toString();
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("file", buffer);

		HttpClient client = getHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(getSlideUploadURL(fileName, fileSize, chunkIndex, chunkSize))
			.POST(ofMimeMultipartData(data, boundary))
			.header("Content-Type", "multipart/form-data;boundary=" + boundary)
			.build();

		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		return response.statusCode() == 200 ? Result.OK : Result.FAIL;
	}

	public static URI getSlideUploadURL(String fileName, long fileSize, int chunkIndex, int chunkSize) {
		return host.resolve(String.format(
			"/api/v0/upload/?filename=%s&fileSize=%s&chunk=%s&chunkSize=%s",
			fileName,
			fileSize,
			chunkIndex,
			chunkSize
		));
	}

	/**
	 * Formats the URI by replacing placeholders with proper values.
	 * @return formatted string as a URI.
	 */
	public static URI getRenderRegionURL(String uri, String slideId, int tileX, int tileY, int level, int tileWidth, int tileHeight) {
		return URI.create(uri
				.replace("{slideId}", e(slideId))
				.replace("{tileX}", String.valueOf(tileX))
				.replace("{tileY}", String.valueOf(tileY))
				.replace("{level}", String.valueOf(level))
				.replace("{tileWidth}", String.valueOf(tileWidth))
				.replace("{tileHeight}", String.valueOf(tileHeight))
		);
	}

	/* Workspaces */

	public static Optional<String> getWorkspace(String id) {
		var response = get("/api/v0/workspaces" + e(id));

		if (response.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	public static Optional<String> getAllWorkspaces() {
		var response = get("/api/v0/workspaces");

		if (response.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	public static Result createNewWorkspace(String workspaceName) {
		var response = post(
		"/api/v0/workspaces",
			Map.of("workspace-name", workspaceName)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result renameWorkspace(String workspaceId, String newName) {
		var response = put(
		"/api/v0/workspaces/" + e(workspaceId),
			Map.of("workspace-name", newName)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result deleteWorkspace(String workspaceId) {
		var response = delete("/api/v0/workspaces/" + e(workspaceId));

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	/* Private API */

	private static Optional<HttpResponse<String>> get(String path) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(host.resolve(path));

			addAuthorization(builder);
			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP GET request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> post(String path, Map<Object, Object> data) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.POST(ofFormData(data))
				.uri(host.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded");

			addAuthorization(builder);
			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (Exception e) {
			logger.error("Error when making HTTP POST request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> delete(String path) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.DELETE()
				.uri(host.resolve(path));

			addAuthorization(builder);
			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP DELETE request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> put(String path, Map<Object, Object> data) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.PUT(ofFormData(data))
				.uri(host.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded");

			addAuthorization(builder);

			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP PUT request", e);
		}

		return Optional.empty();
	}

	private static void addAuthorization(HttpRequest.Builder builder) {
		if (username != null && password != null) {
			builder.headers("Authorization", basicAuth(username, password));
		} else if (!getToken().isEmpty()) {
			builder.headers("Token", getToken());
		}
	}

	private static HttpClient getHttpClient() {
//		if (username != null && password != null) {
//			return HttpClient.newBuilder()
//					.connectTimeout(Duration.ofSeconds(10))
//					.authenticator(new Authenticator() {
//						@Override
//						protected PasswordAuthentication getPasswordAuthentication() {
//							return new PasswordAuthentication(username, password.toCharArray());
//						}
//					})
//					.version(HttpClient.Version.HTTP_1_1)
//					.build();
//		}

		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.version(HttpClient.Version.HTTP_1_1)
				.build();
	}

	private static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
		var builder = new StringBuilder();

		for (Map.Entry<Object, Object> entry : data.entrySet()) {
			if (builder.length() > 0) {
				builder.append("&");
			}

			builder.append(e(entry.getKey().toString()));
			builder.append("=");

			if (entry.getValue() instanceof byte[]) {
				builder.append(e(new String((byte[]) entry.getValue())));
			} else {
				builder.append(e(entry.getValue().toString()));
			}
		}

		return HttpRequest.BodyPublishers.ofString(builder.toString());
	}

	private static final String LINE_FEED = "\r\n";

	private static HttpRequest.BodyPublisher ofMimeMultipartData(Map<Object, Object> data, String boundary) throws IOException {
		var byteArrays = new ArrayList<byte[]>();
		byte[] separator = ("--" + boundary + LINE_FEED
				+"Content-Disposition: form-data; name=").getBytes();

		for (Map.Entry<Object, Object> entry : data.entrySet()) {
			byteArrays.add(separator);

			if (entry.getValue() instanceof Path) {
				var path = (Path) entry.getValue();
				String mimeType = Files.probeContentType(path);
				byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + path.getFileName() + "\""
						+ LINE_FEED + "Content-Type: " + mimeType).getBytes());
				byteArrays.add((LINE_FEED + LINE_FEED).getBytes());
				byteArrays.add(Files.readAllBytes(path));
				byteArrays.add(LINE_FEED.getBytes());
			} else if (entry.getValue() instanceof byte[]) {
				byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"unnamed\""
						+ LINE_FEED + "Content-Type: application/octet-stream").getBytes());
				byteArrays.add((LINE_FEED + LINE_FEED).getBytes());
				byteArrays.add((byte[]) entry.getValue());
				byteArrays.add(LINE_FEED.getBytes());
			} else {
				byteArrays.add(("\"" + entry.getKey() + "\"").getBytes());
				byteArrays.add((LINE_FEED + LINE_FEED).getBytes());
				byteArrays.add((entry.getValue() + LINE_FEED).getBytes());
			}
		}

		byteArrays.add(("--" + boundary + "--").getBytes());
		return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
	}

	/**
	 * Encodes given string with UTF-8. Java URLEncoder changes spaces into +
	 * @param toEncode plain string
	 * @return An encoded URL valid for HTTP
	 */
	public static String e(String toEncode) {
		return URLEncoder.encode(toEncode, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String basicAuth(String username, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
	}

	public enum Result {
		OK,
		FAIL
	}

	public enum AuthType {
		UNAUTHENTICATED,
		GUEST,
		USERNAME,
		TOKEN
	}
}
