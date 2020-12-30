package qupath.edu.gui;

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
import qupath.edu.lib.RemoteOpenslide;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomDialogs {

	private static final Logger logger = LoggerFactory.getLogger(CustomDialogs.class);

	// TODO: This could be rewritten / looked into
	public static Optional<String> showWysiwygEditor(String input) {
		String resourceRoot = QuPathGUI.class.getResource("/ckeditor/ckeditor.js").toString();
		resourceRoot = resourceRoot.substring(0, resourceRoot.length() - 20); // Hacky wacky way to get jar:file: ... URI

		if (input == null) {
			input = "";
		}

		try {
			String HTML = GeneralTools.readInputStreamAsString(QuPathGUI.class.getResourceAsStream("/html/editor.html"));
			HTML = HTML.replace("{{qupath-input}}", input)
					   .replace("{{qupath-resource-root}}", resourceRoot);

			if (RemoteOpenslide.getAuthType() == RemoteOpenslide.AuthType.TOKEN) {
				HTML = HTML.replace("{{qupath-auth}}", "'Token': '" + RemoteOpenslide.getToken() + "'");
			} else if (RemoteOpenslide.getAuthType() == RemoteOpenslide.AuthType.USERNAME) {
				HTML = HTML.replace("{{qupath-auth}}", "Authorization: '" + RemoteOpenslide.getBasicAuthHeader() + "'");
			} else {
				HTML = HTML.replace("{{qupath-auth}}", "");
			}

			return Optional.ofNullable(CustomDialogs.showHTML(HTML));
		} catch (IOException e) {
			logger.error("Error when opening editor", e);
			Dialogs.showErrorNotification("Error when opening editor", e);
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
				try {
					Element editor = browser.getWebEngine().getDocument().getElementById("qupath-editor");

					StringWriter writer = new StringWriter();

					Transformer transformer = TransformerFactory.newInstance().newTransformer();;
					transformer.transform(new DOMSource(editor), new StreamResult(writer));

					return writer.toString();
				} catch (TransformerException e) {
					Dialogs.showErrorNotification("Error when saving project information", e);
				}
			}

			return null;
		});

		Optional<String> result = dialog.showAndWait();

		return result.orElse(null);
	}

	private static EventHandler<DialogEvent> confirmCloseEventHandler = event -> {
		// TODO: This doesn't cancel when closing via display manager
		boolean cancel = Dialogs.showYesNoDialog("Exit", "Are you sure you want to exit?");

		if (!cancel) {
			event.consume();
		}
	};
}
