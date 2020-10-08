package qupath.edu.gui;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.stage.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomDialogs {

	private static final Logger logger = LoggerFactory.getLogger(CustomDialogs.class);

	private static final String[] imageExtensions = { ".png", ".jpg", ".jpeg", ".bmp", ".gif" };

	// TODO: This could be rewritten / looked into
	public static Optional<String> showWysiwygEditor(String input) {
		QuPathGUI qupath = QuPathGUI.getInstance();
		String dataFolderURI = qupath.getProject().getPath().getParent().toUri().toString();
		String resourceRoot = QuPathGUI.class.getResource("/ckeditor/ckeditor.js").toString();
		resourceRoot = resourceRoot.substring(0, resourceRoot.length() - 20); // Hacky wacky way to get jar:file: ... URI

		String result = null;

		if (input == null) {
			input = "";
		}

		try {
			String HTML = GeneralTools.readInputStreamAsString(QuPathGUI.class.getResourceAsStream("/html/editor.html"));

			List<String> images = new ArrayList<>();
			Files.list(qupath.getProject().getPath().getParent()).forEach(item -> {
				String fileName = item.getName(item.getNameCount() - 1).toString().toLowerCase();

				if (GeneralTools.checkExtensions(fileName, imageExtensions)) {
					images.add(fileName);
				}
			});

			HTML = HTML.replace("{{qupath-input}}", input)
					.replace("{{qupath-images}}", new Gson().toJson(images))
					.replace("{{qupath-project-dir}}", dataFolderURI)
					.replace("{{qupath-resource-root}}", resourceRoot);

			result = CustomDialogs.showHTML(HTML);
		} catch (IOException e) {
			logger.error("Error when opening editor", e);
			Dialogs.showErrorNotification("Error when opening editor", e);
		}

		if (result != null) {
			return Optional.of(result.replace(dataFolderURI, "qupath://"));
		}

		return Optional.empty();

	}

	public static String showHTML(String content) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showHTML(content));
		}

		Browser browser = new Browser();
		browser.setContent(content, false);

		Dialog<String> dialog = new Dialog<>();
		dialog.setResizable(true);
		dialog.initOwner(QuPathGUI.getInstance().getStage());
		dialog.setOnCloseRequest(confirmCloseEventHandler);
		dialog.setTitle("Editor");

		try {
			Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
			dialog.getDialogPane().setPrefSize(bounds.getWidth() * 0.8, bounds.getHeight() * 0.8);
		} catch (Exception e) {
			logger.debug("Unable to set stage size using primary screen {}", Screen.getPrimary());
			dialog.getDialogPane().setPrefSize(1000, 800);
		}

		ButtonType btnSave = new ButtonType("Save & Close", ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);
		dialog.getDialogPane().setContent(browser);

		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == btnSave) {
				Element textArea = browser.getWebEngine().getDocument().getElementById("editor");
				return textArea.getTextContent();
			}

			return null;
		});

		Optional<String> result = dialog.showAndWait();

		return result.orElse(null);
	}

	private static EventHandler<DialogEvent> confirmCloseEventHandler = event -> {
		boolean cancel = Dialogs.showYesNoDialog("Exit", "Are you sure you want to exit?");

		if (!cancel) {
			event.consume();
		}
	};
}
