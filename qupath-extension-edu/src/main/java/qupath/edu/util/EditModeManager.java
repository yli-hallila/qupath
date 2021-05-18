package qupath.edu.util;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Used to manage various aspects of edit mode for QuPath Edu.
 */
public class EditModeManager {

    private final Logger logger = LoggerFactory.getLogger(EditModeManager.class);

    private final SimpleBooleanProperty editModeEnabled = new SimpleBooleanProperty(false);
    private final ByteArrayOutputStream osImageData = new ByteArrayOutputStream(0);

    public SimpleBooleanProperty editModeEnabledProperty() {
        return editModeEnabled;
    }

    public boolean isEditModeEnabled() {
        return editModeEnabled.get();
    }

    public boolean isEditModeDisabled() {
        return !(editModeEnabled.get());
    }

    public void toggleEditMode() {
        setEditModeEnabled(!(isEditModeEnabled()));
    }

    public void setEditModeEnabled(boolean enabled) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        if (enabled) {
            editModeEnabledProperty().set(true);
            qupath.setReadOnly(false);

            if (qupath.getImageData() != null) {
                try {
                    osImageData.reset();
                    PathIO.writeImageData(osImageData, qupath.getImageData());
                } catch (IOException e) {
                    logger.error("Error when backing up image data", e);
                }
            }
        } else {
            var choice = Dialogs.builder()
                    .title("Confirm")
                    .contentText("Do you wish to save changes or restore changes?")
                    .buttons("Save", "Restore", "Cancel")
                    .build()
                    .showAndWait()
                    .orElseGet(() -> new ButtonType("Cancel"))
                    .getText();

            if (choice.equals("Save")) {
                ReflectionUtil.checkSaveChanges(qupath.getImageData());
                qupath.setReadOnly(true);
                editModeEnabledProperty().set(false);
            } else if (choice.equals("Restore")) {
                restoreImageData();
                qupath.setReadOnly(true);
                editModeEnabledProperty().set(false);
            }
        }
    }

    public void restoreImageData() {
        try {
            QuPathGUI.getInstance().getViewer().setImageData(PathIO.readImageData(new ByteArrayInputStream(osImageData.toByteArray()), null, null, BufferedImage.class));
        } catch (IOException e) {
            logger.error("Error when restoring image data", e);
        }
    }

    public void backupImageData(ImageData<BufferedImage> imageData) {
        try {
            osImageData.reset();
            PathIO.writeImageData(osImageData, imageData);
        } catch (IOException e) {
            logger.error("Error when backing up image data", e);
        }
    }
}
