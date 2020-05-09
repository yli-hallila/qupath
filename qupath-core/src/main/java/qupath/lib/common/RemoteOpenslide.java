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
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class RemoteOpenslide {

	private final static Logger logger = LoggerFactory.getLogger(RemoteOpenslide.class);

	private static URI host;

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

	public static void setAuthentication(String username, String password) {
		RemoteOpenslide.username = username;
		RemoteOpenslide.password = password;
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

	public static Optional<JsonArray> getSlides() {
		Optional<HttpResponse<String>> response = get(
			"/api/v0/slides/"
		);

		if (response.isEmpty()) {
			return Optional.empty();
		}

		JsonArray slides = JsonParser.parseString(response.get().body()).getAsJsonArray();
		return Optional.of(slides);
	}

	public static Optional<InputStream> getProject(String id) {
		try { // todo: fix this piece of shit code; only changed ofString() -> ofInputStream()
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.uri(host.resolve(
					"/api/v0/projects/" + e(id)
				))
				.build();

			return Optional.of(client.send(request, BodyHandlers.ofInputStream()).body());
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP GET request", e);
		}

		return Optional.empty();
	}

	public static Optional<String> getWorkspace() {
		Optional<HttpResponse<String>> response = get(
			"/api/v0/workspaces"
		);

		if (response.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	public static Optional<JsonObject> getProperties(String id) {
		Optional<HttpResponse<String>> response = get(
			"/api/v0/slides/" + e(id)
		);

		if (response.isEmpty()) {
			return Optional.empty();
		}

		JsonObject slides = JsonParser.parseString(response.get().body()).getAsJsonObject();
		return Optional.of(slides);
	}

	public static Result uploadProject(String projectName, File projectFile) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(host.resolve(
						"/api/v0/projects/" + e(projectName)
					))
					.POST(HttpRequest.BodyPublishers.ofFile(projectFile.toPath()))
					.build();

			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			return response.statusCode() == 200 ? Result.OK : Result.FAIL;
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making uploading project", e);
		}

		return Result.FAIL;
	}

	public static Result createNewProject(String workspaceName, String projectName) {
		Optional<HttpResponse<String>> response = post(
		"/api/v0/projects",
			Map.of(
				"workspace-name", workspaceName,
				"project-name", projectName
			)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result editProjectDescription(String projectName, String description) {
		Optional<HttpResponse<String>> response = put(
		"/api/v0/projects/" + projectName,
			Map.of(
				//"name", newProjectName,
				"description", description
			)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	/**
	 * Formats the URI by replacing placeholders with proper values.
	 * @return formatted string as a URI.
	 */
	public static URI getRenderRegionURL(String uri, String slide, int tileX, int tileY, int level, int tileWidth, int tileHeight) {
		return URI.create(uri
				.replace("{slideName}", e(slide))
				.replace("{tileX}", String.valueOf(tileX))
				.replace("{tileY}", String.valueOf(tileY))
				.replace("{level}", String.valueOf(level))
				.replace("{tileWidth}", String.valueOf(tileWidth))
				.replace("{tileHeight}", String.valueOf(tileHeight))
		);
	}

	public static Result createNewWorkspace(String workspaceName) {
		Optional<HttpResponse<String>> response = post(
			"/api/v0/workspaces",
				Map.of("workspace-name", workspaceName)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result deleteWorkspace(String workspaceName) {
		Optional<HttpResponse<String>> response = delete(
			"/api/v0/workspaces/" + e(workspaceName)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	public static Result deleteProject(String projectName) {
		Optional<HttpResponse<String>> response = delete(
			"/api/v0/projects/" + e(projectName)
		);

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return (response.get().statusCode() == 200) ? Result.OK : Result.FAIL;
	}

	private static Optional<HttpResponse<String>> get(String path) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.uri(host.resolve(path))
				.header("name", username)
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
					.uri(host.resolve(path))
					.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP POST request", e);
		}

		return Optional.empty();
	}

	private static Optional<HttpResponse<String>> put(String path, Map<Object, Object> data) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.PUT(ofFormData(data))
					.uri(host.resolve(path))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP POST request", e);
		}

		return Optional.empty();
	}

	private static HttpClient getHttpClient() {
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

	private static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
		var builder = new StringBuilder();

		for (Map.Entry<Object, Object> entry : data.entrySet()) {
			if (builder.length() > 0) {
				builder.append("&");
			}

			builder.append(e(entry.getKey().toString()));
			builder.append("=");
			builder.append(e(entry.getValue().toString()));
		}

		return HttpRequest.BodyPublishers.ofString(builder.toString());
	}

	/**
	 * Encodes given string with UTF-8. Java URLEncoder changes spaces into +
	 * @param toEncode plain string
	 * @return An encoded URL valid for HTTP
	 */
	public static String e(String toEncode) {
		return URLEncoder.encode(toEncode, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public static URI getHost() {
		return host;
	}

	public enum Result {
		OK,
		FAIL
	}
}
