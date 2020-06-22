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

	private static String token;

	private static String username;
	private static String password;

	private static boolean WRITE_ACCESS = false;

	public static boolean hasWriteAccess() {
		return WRITE_ACCESS;
	}

	public static void setWriteAccess(boolean writeAccess) {
		WRITE_ACCESS = writeAccess;
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

	public static void setAuthentication(String username, String password) {
		RemoteOpenslide.username = username;
		RemoteOpenslide.password = password;
	}

	public static boolean isConnected() {
		return host != null;
	}

	public static String getToken() {
		return token;
	}

	public static void setToken(String token) {
		RemoteOpenslide.token = token;
	}

	/* Authentication */

	public static boolean login(String username, String password) {
		setAuthentication(username, password);

		if (login()) {
			return true;
		}

		setAuthentication(null, null);
		return false;
	}

	public static boolean login() {
		Optional<HttpResponse<String>> response = get(
			"/api/v0/users/login"
		);

		if (response.isEmpty()) {
			return false;
		}

		setWriteAccess(false);

		JsonArray permissions = JsonParser.parseString(response.get().body()).getAsJsonArray();
		for (JsonElement element : permissions) {
			if (element.getAsString().equals("TEACHER") || element.getAsString().equals("ADMIN")) {
				setWriteAccess(true);
			}
		}

		return true;
	}

	public static boolean validate() {
		Optional<HttpResponse<String>> response = get(
			"/api/v0/users/verify"
		);

		if (response.isEmpty()) {
			return false;
		}

		setWriteAccess(false);

		JsonArray permissions = JsonParser.parseString(response.get().body()).getAsJsonArray();
		for (JsonElement element : permissions) {
			String permission = element.getAsString().toUpperCase();

			if (permission.equals("TEACHER") || permission.equals("ADMIN")) {
				setWriteAccess(true);
			}
		}

		return true;
	}

	public static void logout() {
		setHost(null);
		setToken(null);
		setAuthentication(null, null);
		setWriteAccess(false);
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


	public static Result createNewProject(String workspaceId, String projectName) {
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

	public static Optional<String> getWorkspace() {
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
			HttpRequest request = HttpRequest.newBuilder()
				.uri(host.resolve(path))
				.header("token", token)
				.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP GET request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> post(String path, Map<Object, Object> data) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.POST(ofFormData(data))
				.uri(host.resolve(path))
				.header("token", token)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (Exception e) {
			logger.error("Error when making HTTP POST request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> delete(String path) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.DELETE()
				.header("token", token)
				.uri(host.resolve(path))
				.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP DELETE request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> put(String path, Map<Object, Object> data) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.PUT(ofFormData(data))
				.header("token", token)
				.uri(host.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP PUT request", e);
		}

		return Optional.empty();
	}

	private static HttpClient getHttpClient() {
		if (username != null && password != null) {
			return HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.authenticator(new Authenticator() {
						@Override
						protected PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(username, password.toCharArray());
						}
					})
					.version(HttpClient.Version.HTTP_1_1)
					.build();
		}

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

	public enum Result {
		OK,
		FAIL
	}
}
