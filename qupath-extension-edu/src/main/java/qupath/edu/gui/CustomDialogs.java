package qupath.edu.gui;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.lib.RemoteOpenslide;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;
import java.util.Optional;

public class CustomDialogs {

	private static final Logger logger = LoggerFactory.getLogger(CustomDialogs.class);

	public static Optional<String> showWYSIWYGEditor(String input) {
		String resourceRoot = QuPathGUI.class.getResource("/ckeditor/ckeditor.js").toString();
		resourceRoot = resourceRoot.substring(0, resourceRoot.length() - 20); // Hacky wacky way to get jar:file: ... URI

		if (input == null) {
			input = "";
		}

		String HTML;

		try {
			HTML = GeneralTools.readInputStreamAsString(QuPathGUI.class.getResourceAsStream("/html/editor.html"));
			HTML = HTML.replace("{{qupath-input}}", input)
					   .replace("{{qupath-resource-root}}", resourceRoot)
			           .replace("{{qupath-upload-url}}", RemoteOpenslide.getCKEditorUploadUrl());

			if (RemoteOpenslide.getAuthType() == RemoteOpenslide.AuthType.TOKEN) {
				HTML = HTML.replace("{{qupath-auth}}", "'Token': '" + RemoteOpenslide.getToken() + "'");
			} else if (RemoteOpenslide.getAuthType() == RemoteOpenslide.AuthType.USERNAME) {
				HTML = HTML.replace("{{qupath-auth}}", "Authorization: '" + RemoteOpenslide.getBasicAuthHeader() + "'");
			} else {
				HTML = HTML.replace("{{qupath-auth}}", "");
			}
		} catch (IOException e) {
			logger.error("Error when opening editor", e);
			Dialogs.showErrorNotification("Error when opening editor", e);
			return Optional.empty();
		}

		/* Dialog */

		Browser browser = new Browser(HTML, false);

		ButtonType btnSave = new ButtonType("Save & Close", ButtonBar.ButtonData.OK_DONE);

		Dialog<ButtonType> dialog = Dialogs.builder()
				.title("Editor")
				.content(browser)
				.buttons(btnSave, ButtonType.CLOSE)
				.size(1200, 800)
				.modality(Modality.APPLICATION_MODAL)
				.resizable()
				.build();

		dialog.setOnCloseRequest(confirmCloseEventHandler);

		var result = dialog.showAndWait();
		if (result.isPresent() && result.get() == btnSave) {
			return Optional.of(browser.getWebEngine().executeScript("window.editor.getData();").toString());
		}

		return Optional.empty();
	}

	// This does not work when using the 'X' button to close the dialog.
	private static final EventHandler<DialogEvent> confirmCloseEventHandler = event -> {
		boolean cancel = Dialogs.showYesNoDialog("Exit", "Are you sure you want to exit?");

		if (!cancel) {
			event.consume();
		}
	};
}
