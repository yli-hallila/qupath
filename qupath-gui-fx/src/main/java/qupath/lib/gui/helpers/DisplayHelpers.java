/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.helpers;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import javax.swing.*;

import com.google.gson.Gson;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.*;
import javafx.util.Pair;
import jfxtras.scene.layout.GridPane;
import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.w3c.dom.Element;
import javafx.scene.robot.Robot;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DefaultColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.Browser;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.helpers.dialogs.TextAreaDialog;
import qupath.lib.gui.legacy.swing.ParameterPanel;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.roi.PointsROI;

/**
 * Collection of static methods to help with showing information to a user, 
 * as well as requesting some basic input.
 * <p>
 * In general, 'showABCMessage' produces a dialog box that requires input from the user.
 * By contrast, 'showABCNotification' shows a message that will disappear without user input.
 *
 * @author Pete Bankhead
 *
 */
public class DisplayHelpers {
	
	final private static Logger logger = LoggerFactory.getLogger(DisplayHelpers.class);
	
	/**
	 * Possible buttons pressed in a yes/no/cancel dialog.
	 */
	public static enum DialogButton {YES, NO, CANCEL}
	
	/**
	 * Make a semi-educated guess at the image type of a PathImageServer.
	 * 
	 * @param server
	 * @param imgThumbnail Thumbnail for the image. This is now a required parameter (previously &lt;= 0.1.2 it was optional).
	 *
	 * @return
	 */
	public static ImageData.ImageType estimateImageType(final ImageServer<BufferedImage> server, final BufferedImage imgThumbnail) {
		
//		logger.warn("Image type will be automatically estimated");
		
		if (!server.isRGB())
			return ImageData.ImageType.FLUORESCENCE;
		
		BufferedImage img = imgThumbnail;
//		BufferedImage img;
//		if (imgThumbnail == null)
//			img = server.getBufferedThumbnail(220, 220, 0);
//		else {
//			img = imgThumbnail;
//			// Rescale if necessary
//			if (img.getWidth() * img.getHeight() > 400*400) {
//				imgThumbnail.getS
//			}
//		}
		int w = img.getWidth();
		int h = img.getHeight();
		int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
		long rSum = 0;
		long gSum = 0;
		long bSum = 0;
		int nDark = 0;
		int nLight = 0;
		int n = 0;
		int darkThreshold = 25;
		int lightThreshold = 220;
		for (int v : rgb) {
			int r = ColorTools.red(v);
			int g = ColorTools.green(v);
			int b = ColorTools.blue(v);
			if (r < darkThreshold & g < darkThreshold && b < darkThreshold)
				nDark++;
			else if (r > lightThreshold & g > lightThreshold && b > lightThreshold)
				nLight++;
			else {
				n++;
				rSum += r;
				gSum += g;
				bSum += b;
			}
		}
		if (nDark == 0 && nLight == 0)
			return ImageData.ImageType.UNSET;
		// If we have more dark than light pixels, assume fluorescence
		if (nDark >= nLight)
			return ImageData.ImageType.FLUORESCENCE;
		
//		Color color = new Color(
//				(int)(rSum/n + .5),
//				(int)(gSum/n + .5),
//				(int)(bSum/n + .5));
//		logger.debug("Color: " + color.toString());

		// Compare optical density vector angles with the defaults for hematoxylin, eosin & DAB
		ColorDeconvolutionStains stainsH_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_E);
		double rOD = ColorDeconvolutionHelper.makeOD(rSum/n, stainsH_E.getMaxRed());
		double gOD = ColorDeconvolutionHelper.makeOD(gSum/n, stainsH_E.getMaxGreen());
		double bOD = ColorDeconvolutionHelper.makeOD(bSum/n, stainsH_E.getMaxBlue());
		StainVector stainMean = StainVector.createStainVector("Mean Stain", rOD, gOD, bOD);
		double angleH = StainVector.computeAngle(stainMean, stainsH_E.getStain(1));
		double angleE = StainVector.computeAngle(stainMean, stainsH_E.getStain(2));
		ColorDeconvolutionStains stainsH_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_DAB);
		double angleDAB = StainVector.computeAngle(stainMean, stainsH_DAB.getStain(2));
	
		// For H&E staining, eosin is expected to predominate... if it doesn't, assume H-DAB
		logger.debug("Angle hematoxylin: " + angleH);
		logger.debug("Angle eosin: " + angleE);
		logger.debug("Angle DAB: " + angleDAB);
		if (angleDAB < angleE || angleH < angleE) {
			logger.info("Estimating H-DAB staining");
			return ImageData.ImageType.BRIGHTFIELD_H_DAB;
		} else {
			logger.info("Estimating H & E staining");
			return ImageData.ImageType.BRIGHTFIELD_H_E;
		}
	}
	
	
	/**
	 * Show a confirm dialog (OK/Cancel).
	 * @param title
	 * @param text
	 * @return
	 */
	public static boolean showConfirmDialog(String title, String text) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
//			alert.setContentText(text);
			alert.getDialogPane().setContent(createContentLabel(text));
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else
			return JOptionPane.showConfirmDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
	}
	
	/**
	 * Show a message dialog (OK button only), with the content contained within a Node.
	 * @param title
	 * @param node
	 * @return
	 */
	public static boolean showMessageDialog(final String title, final Node node) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK);
			alert.setTitle(title);
			alert.getDialogPane().setContent(node);
//			if (resizable) {
//				// Note: there is nothing to stop the dialog being shrunk to a ridiculously small size!
//				alert.setResizable(resizable);
//			}
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else {
			JFXPanel panel = new JFXPanel();
			panel.setScene(new Scene(new StackPane(node)));
			return JOptionPane.showConfirmDialog(null, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
		}
	}
	
	/**
	 * Show a standard message dialog.
	 * @param title
	 * @param message
	 */
	public static void showMessageDialog(String title, String message) {
		logger.info("{}: {}", title, message);
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK);
			alert.setTitle(title);
			alert.getDialogPane().setHeader(null);
//			alert.getDialogPane().setContentText(message);
			alert.getDialogPane().setContent(createContentLabel(message));
			alert.showAndWait();
		} else
			Platform.runLater(() -> showMessageDialog(title, message));
	}

	public static boolean showConfirmDialog(String title, Node node) {
		return showConfirmDialog(title, node, true);
	}
	
	/**
	 * Show a confirm dialog (OK/Cancel).
	 * @param title
	 * @param node
	 * @return
	 */
	public static boolean showConfirmDialog(String title, Node node, boolean resizable) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK, ButtonType.CANCEL);
			if (QuPathGUI.getInstance() != null)
				alert.initOwner(QuPathGUI.getInstance().getStage());
			alert.setTitle(title);
			alert.getDialogPane().setContent(node);
			alert.setResizable(resizable);
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else {
			JFXPanel panel = new JFXPanel();
			panel.setScene(new Scene(new StackPane(node)));
			return JOptionPane.showConfirmDialog(null, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
		}
	}
	
	/**
	 * Show a confirm dialog (OK/Cancel) with a Swing component.
	 * @param title
	 * @param content
	 * @return
	 */
	public static boolean showConfirmDialog(String title, JComponent content) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK, ButtonType.CANCEL);
			if (QuPathGUI.getInstance() != null)
				alert.initOwner(QuPathGUI.getInstance().getStage());
			alert.getDialogPane().setHeaderText(null);
			alert.setTitle(title);
			SwingNode node = new SwingNode();
			node.setContent(content);
			content.validate();
			content.setSize(content.getPreferredSize());
			StackPane pane = new StackPane(node);
			pane.setPrefSize(content.getPreferredSize().width + 25, content.getPreferredSize().height + 25);
			alert.getDialogPane().setContent(pane);
			Optional<ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == ButtonType.OK;
		} else
			return JOptionPane.showConfirmDialog(null, content, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
	}
	
	/**
	 * Show a Yes/No dialog.
	 * @param title
	 * @param text
	 * @return
	 */
	public static boolean showYesNoDialog(String title, String text) {
		if (Platform.isFxApplicationThread()) {
			Alert alert = new Alert(AlertType.NONE);
			if (QuPathGUI.getInstance() != null)
				alert.initOwner(QuPathGUI.getInstance().getStage());
			alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
			alert.setTitle(title);
//			alert.setContentText(text);
			alert.getDialogPane().setContent(createContentLabel(text));
			Optional<ButtonType> result = alert.showAndWait();
			boolean response = result.isPresent() && result.get() == ButtonType.YES;
			return response;
		} else
			return JOptionPane.showConfirmDialog(null, text, title, JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION;
	}
	
	/**
	 * Create a content label. This is patterned on the default behavior for {@link DialogPane} but
	 * sets the min size to be the preferred size, which is necessary to avoid ellipsis when using long
	 * Strings on Windows with scaling other than 100%.
	 * @param text
	 * @return
	 */
	private static Label createContentLabel(String text) {
		var label = new Label(text);
		label.setMaxWidth(Double.MAX_VALUE);
        label.setMaxHeight(Double.MAX_VALUE);
        label.setMinSize(Label.USE_PREF_SIZE, Label.USE_PREF_SIZE);
        label.setWrapText(true);
        label.setPrefWidth(360);
        return label;
	}

	/**
	 * Show a Yes/No/Cancel dialog.
	 * @param title
	 * @param text
	 * @return
	 */
	public static DialogButton showYesNoCancelDialog(String title, String text) {
		if (Platform.isFxApplicationThread()) {
			// TODO: Check the order of buttons in Yes, No, Cancel dialog - seems weird on OSX
			Alert alert = new Alert(AlertType.NONE);
			alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
			alert.setTitle(title);
//			alert.setContentText(text);
			alert.getDialogPane().setContent(createContentLabel(text));
			Optional<ButtonType> result = alert.showAndWait();
			if (result.isPresent())
				return getJavaFXPaneYesNoCancel(result.get());
			else
				return DialogButton.CANCEL;
		} else {
			int response = JOptionPane.showConfirmDialog(null, text, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			return getJOptionPaneYesNoCancel(response);
		}
	}
	
	private static DialogButton getJOptionPaneYesNoCancel(final int response) {
		switch (response) {
		case JOptionPane.YES_OPTION:
			return DialogButton.YES;
		case JOptionPane.NO_OPTION:
			return DialogButton.NO;
		case JOptionPane.CANCEL_OPTION:
			return DialogButton.CANCEL;
		default:
			return null;
		}
	}
	
	
	private static DialogButton getJavaFXPaneYesNoCancel(final ButtonType buttonType) {
		if (buttonType == ButtonType.YES)
			return DialogButton.YES;
		if (buttonType == ButtonType.NO)
			return DialogButton.NO;
		if (buttonType == ButtonType.CANCEL)
			return DialogButton.CANCEL;
		return null;
	}
		

	/**
	 * Show a (modal) dialog for a specified ParameterList.
	 * 
	 * @param title
	 * @param params
	 * @return False if the user pressed 'cancel', true otherwise
	 */
	public static boolean showParameterDialog(String title, ParameterList params) {
		if (Platform.isFxApplicationThread()) {
			return showConfirmDialog(title, new ParameterPanelFX(params).getPane());
//			return showComponentContainerDialog(owner, title, new ParameterPanel(params));
		} else {
			JOptionPane pane = new JOptionPane(new ParameterPanel(params), JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
			JDialog dialog = pane.createDialog(null, title);
			dialog.setAlwaysOnTop(true);
			dialog.setVisible(true);
			dialog.dispose();
			Object value = pane.getValue();
			return (value instanceof Integer) && ((Integer)value == JOptionPane.OK_OPTION);
	//		return JOptionPane.showConfirmDialog(null, new ParameterPanel(params), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
		}
	}

	
	
	/**
	 * Returns null.
	 * 
	 * Previously returned QuPath's JFrame... when Swing was used.
	 * 
	 * @return
	 */
	private static JFrame getPossibleParent() {
		return null;
	}
	
	
	/**
	 * Show an input dialog requesting a numeric value.
	 * 
	 * @param title
	 * @param message
	 * @param initialInput
	 * @return Number input by the user, or NaN if no valid number was entered, or null if cancel was pressed.
	 */
	public static Double showInputDialog(final String title, final String message, final Double initialInput) {
		String result = showInputDialog(title, message, initialInput == null ? "" : initialInput.toString());
		if (result == null)
			return null;
		try {
			return Double.parseDouble(result);
		} catch (Exception e) {
			logger.error("Unable to parse numeric value from {}", result);
			return Double.NaN;
		}
	}
	
	/**
	 * Show an input dialog requesting a String input.
	 * 
	 * @param title
	 * @param message
	 * @param initialInput
	 * @return
	 */
	public static String showInputDialog(final String title, final String message, final String initialInput) {
		if (Platform.isFxApplicationThread()) {
			TextInputDialog dialog = new TextInputDialog(initialInput);
			dialog.setTitle(title);
			dialog.setHeaderText(null);
			dialog.setContentText(message);
			dialog.setResizable(true);
			// Traditional way to get the response value.
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent())
			    return result.get();
		} else {
			Object result = JOptionPane.showInputDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null, null, initialInput);
			if (result instanceof String)
				return (String)result;
		}
		return null;
	}

	// todo: MERGEFIX
	public static String showTextAreaDialog(final String title, final String message, final String initialInput) {
		if (Platform.isFxApplicationThread()) {
			TextAreaDialog dialog = new TextAreaDialog(initialInput);
			dialog.setTitle(title);
			dialog.setHeaderText(null);
			dialog.setContentText(message);
			// Traditional way to get the response value.
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent())
				return result.get();
		}

		return null;
	}

	public static String showTextAreaDialogWithHeader(final String title, final String message, final String initialInput) {
		if (Platform.isFxApplicationThread()) {
			TextAreaDialog dialog = new TextAreaDialog(initialInput);
			dialog.setTitle(title);
			dialog.setHeaderText(message);
			dialog.setContentText(null);
			// Traditional way to get the response value.
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent())
				return result.get();
		}

		return null;
	}

    /**
     * Show a choice dialog with an array of choices (selection from ComboBox or similar).
     * @param <T>
     * @param title
     * @param message
     * @param choices
     * @param defaultChoice
     * @return
     */
	public static <T> T showChoiceDialog(final String title, final String message, final T[] choices, final T defaultChoice) {
		return showChoiceDialog(title, message, Arrays.asList(choices), defaultChoice);
	}

	/**
	 * Show a choice dialog with a collection of choices (selection from ComboBox or similar).
	 * @param <T>
	 * @param title
	 * @param message
	 * @param choices
	 * @param defaultChoice
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> T showChoiceDialog(final String title, final String message, final Collection<T> choices, final T defaultChoice) {
		if (Platform.isFxApplicationThread()) {
			ChoiceDialog<T> dialog = new ChoiceDialog<>(defaultChoice, choices);
			dialog.setTitle(title);
			dialog.getDialogPane().setHeaderText(null);
			if (message != null)
				dialog.getDialogPane().setContentText(message);
			Optional<T> result = dialog.showAndWait();
			if (result.isPresent())
				return result.get();
			return null;
		} else
			return (T)JOptionPane.showInputDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null, choices.toArray(), defaultChoice);
	}
	
	/**
	 * Show an error message, displaying the localized message of a {@link Throwable}.
	 * @param title
	 * @param e
	 */
	public static void showErrorMessage(final String title, final Throwable e) {
		logger.error("Error", e);
		String message = e.getLocalizedMessage();
		if (message == null)
			message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please report it with 'Help -> Report bug (web)'.\n\n" + e;
		showErrorMessage(title, message);
	}
	
	/**
	 * Show an error notification, displaying the localized message of a {@link Throwable}.
	 * @param title
	 * @param e
	 */
	public static void showErrorNotification(final String title, final Throwable e) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showErrorNotification(title, e));
			return;
		}
		String message = e.getLocalizedMessage();
		if (message != null && !message.isBlank() && !message.equals(title))
			logger.error(title + ": " + e.getLocalizedMessage(), e);
		else
			logger.error(title , e);
		if (message == null)
			message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please report it with 'Help > Report bug'.\n\n" + e;
		if (Platform.isFxApplicationThread()) {
			createNotifications().title(title).text(message).showError();
		} else {
			String finalMessage = message;
			Platform.runLater(() -> {
				createNotifications().title(title).text(finalMessage).showError();
			});
		}
	}

	/**
	 * Show an error notification.
	 * @param title
	 * @param message
	 */
	public static void showErrorNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showErrorNotification(title, message));
			return;
		}
		logger.error(title + ": " + message);
		createNotifications().title(title).text(message).showError();
	}

	/**
	 * Show a warning notification.
	 * @param title
	 * @param message
	 */
	public static void showWarningNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showWarningNotification(title, message));
			return;
		}
		logger.warn(title + ": " + message);
		createNotifications().title(title).text(message).showWarning();
	}

	/**
	 * Show an info notification.
	 * @param title
	 * @param message
	 */
	public static void showInfoNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showInfoNotification(title, message));
			return;
		}
		logger.info(title + ": " + message);
		createNotifications().title(title).text(message).showInformation();
	}

	/**
	 * Show a plain notification.
	 * @param title
	 * @param message
	 */
	public static void showPlainNotification(final String title, final String message) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showPlainNotification(title, message));
			return;
		}
		logger.info(title + ": " + message);
		createNotifications().title(title).text(message).show();
	}

	/**
	 * Necessary to have owner when calling notifications (bug in controlsfx?).
	 */
	private static Notifications createNotifications() {
		var stage = QuPathGUI.getInstance() == null ? null : QuPathGUI.getInstance().getStage();
		var notifications = Notifications.create();
		if (stage == null)
			return notifications;

		if (!QuPathStyleManager.isDefaultStyle())
			notifications = notifications.darkStyle();

		return notifications.owner(stage);
	}

    private static final String[] imageExtensions = { ".png", ".jpg", ".jpeg", ".bmp", ".gif" };

    public static Optional<String> showEditor(String input) {
        QuPathGUI qupath = QuPathGUI.getInstance();
        String dataFolderURI = qupath.getProject().getPath().getParent().toUri().toString();
        String resourceRoot = QuPathGUI.class.getResource("/ckeditor/ckeditor.js").toString();
        resourceRoot = resourceRoot.substring(0, resourceRoot.length() - 20); // hacky wacky way to get jar:file: ... URI

        String result = null;

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

            result = DisplayHelpers.showHTML(HTML);
        } catch (IOException e) {
            logger.error("Error when opening editor", e);
            DisplayHelpers.showErrorNotification("Error when opening editor", e);
        }

        if (result != null) {
            return Optional.of(result.replace(dataFolderURI, "qupath://"));
        }

        return Optional.empty();
    }

	public static String showHTML(final String content) {
		if (Platform.isFxApplicationThread()) {
			Browser browser = new Browser();
			browser.setContent(content, false);

			Dialog<String> dialog = new Dialog<>();
			dialog.setResizable(true);
			dialog.setOnCloseRequest(confirmCloseEventHandler);
			dialog.setTitle("Editor");

			try {
				Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
				dialog.getDialogPane().setPrefSize(bounds.getWidth() * 0.8, bounds.getHeight() * 0.8);
			} catch (Exception e) {
				logger.debug("Unable to set stage size using primary screen {}", Screen.getPrimary());
				dialog.getDialogPane().setPrefSize(1000, 800);
			}

			ButtonType closeButton = new ButtonType("Save & Close", ButtonBar.ButtonData.OK_DONE);
			dialog.getDialogPane().getButtonTypes().addAll(closeButton, ButtonType.CANCEL);
			dialog.getDialogPane().setContent(browser);

			dialog.setResultConverter(dialogButton -> {
				if (dialogButton == closeButton) {
					Element textArea = browser.getWebEngine().getDocument().getElementById("editor");
					return textArea.getTextContent();
				}

				return null;
			});

			Optional<String> result = dialog.showAndWait();

			if (result.isPresent())
				return result.get();
		}

		return null;
	}

	private static EventHandler<DialogEvent> confirmCloseEventHandler = event -> {
		boolean cancel = showConfirmDialog("Exit", "Press 'OK' if you want to exit.");

		if (!cancel) {
			event.consume();
		}
	};

	/**
	 * Try to open a file in the native application.
	 * 
	 * This can be used to open a directory in Finder (Mac OSX) or Windows Explorer etc.
	 * This can however fail on Linux, so an effort is made to query Desktop support and
	 * offer to copy the path instead of opening the file, if necessary.
	 *
	 * @param file
	 * @return
	 */
	public static boolean openFile(final File file) {
		if (file == null || !file.exists()) {
			DisplayHelpers.showErrorMessage("Open", "File " + file + " does not exist!");
			return false;
		}
		if (file.isDirectory())
			return browseDirectory(file);
		if (Desktop.isDesktopSupported()) {
			try {
				var desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.OPEN))
					desktop.open(file);
				else {
					if (DisplayHelpers.showConfirmDialog("Open file",
							"Opening files not supported on this platform!\nCopy directory path to clipboard instead?")) {
						var content = new ClipboardContent();
						content.putString(file.getAbsolutePath());
						Clipboard.getSystemClipboard().setContent(content);
					}
				}
				return true;
			} catch (Exception e1) {
				DisplayHelpers.showErrorNotification("Open file", e1);
			}
		}
		return false;
	}
	
	/**
	 * Open the directory containing a file for browsing.
	 * @param file
	 * @return
	 */
	public static boolean browseDirectory(final File file) {
		if (file == null || !file.exists()) {
			DisplayHelpers.showErrorMessage("Open", "File " + file + " does not exist!");
			return false;
		}
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			try {
				// Seems to work on Mac
				if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR))
					desktop.browseFileDirectory(file);
				else {
					// Can open directory in Windows
					if (GeneralTools.isWindows()) {
						if (file.isDirectory())
							desktop.open(file);
						else
							desktop.open(file.getParentFile());
						return true;
					}
					// Trouble on Linux - just copy
					if (DisplayHelpers.showConfirmDialog("Browse directory",
							"Directory browsing not supported on this platform!\nCopy directory path to clipboard instead?")) {
						var content = new ClipboardContent();
						content.putString(file.getAbsolutePath());
						Clipboard.getSystemClipboard().setContent(content);
					}
				}
				return true;
			} catch (Exception e1) {
				DisplayHelpers.showErrorNotification("Browse directory", e1);
			}
		}
		return false;
	}



	/**
	 * Try to open a URI in a web browser.
	 * 
	 * @param uri
	 * @return True if the request succeeded, false otherwise.
	 */
	public static boolean browseURI(final URI uri) {
		return QuPathGUI.launchBrowserWindow(uri.toString());
	}

	/**
	 * Show an error message that no image is available. This is included to help
	 * standardize the message throughout the software.
	 * @param title
	 */
	public static void showNoImageError(String title) {
		showErrorMessage(title, "No image is available!");
	}
	

	/**
	 * Show an error message.
	 * @param title
	 * @param message
	 */
	public static void showErrorMessage(final String title, final String message) {
		logger.error(title + ": " + message);
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle(title);
				alert.getDialogPane().setHeaderText(null);
//				alert.setContentText(message);
				alert.getDialogPane().setContent(createContentLabel(message));
				alert.show();
			} else
				Platform.runLater(() -> showErrorMessage(title, message));
//				JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.ERROR_MESSAGE, null);
		}
//			showDialog(title, message);
	}
	
	/**
	 * Show an error message, with the content defined within a {@link Node}.
	 * @param title
	 * @param message
	 */
	public static void showErrorMessage(final String title, final Node message) {
		logger.error(title + ": " + message);
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle(title);
				alert.getDialogPane().setHeaderText(null);
				alert.getDialogPane().setContent(message);
				alert.show();
			} else
				JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.ERROR_MESSAGE, null);
		}
//			showDialog(title, message);
	}

	/**
	 * Show a plain message.
	 * @param title
	 * @param message
	 */
	public static void showPlainMessage(final String title, final String message) {
		logger.info(title + ": " + message);
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.INFORMATION);
				alert.getDialogPane().setHeaderText(null);
				alert.setTitle(title);
//				alert.setContentText(message);
				alert.getDialogPane().setContent(createContentLabel(message));
				alert.show();
			} else
				JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null);
		}
//			showDialog(title, message);
	}
	
	/**
	 * Show a plain message with the content defined within a Swing component.
	 * @param title
	 * @param message
	 */
	public static void showPlainMessage(final String title, final JComponent message) {
		logger.info(title + ": " + message);
		if (!GraphicsEnvironment.isHeadless()) {
			if (Platform.isFxApplicationThread()) {
				Alert alert = new Alert(AlertType.INFORMATION);
				alert.getDialogPane().setHeaderText(null);
				alert.setTitle(title);
				SwingNode node = new SwingNode();
				node.setContent(message);
				alert.getDialogPane().setContent(node);
				alert.show();
			} else
				JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null);
		}
//			showDialog(title, message);
	}

//	private static void showDialog(final String title, String message) {
//		final JDialog dialog = new JDialog((Frame)null, title, true);
//
//		if (message.contains("\n"))
//			message = message.replaceAll("\n", "<br>");
//		JLabel label = new JLabel(message);
//		JPanel panel = new JPanel(new BorderLayout());
//		panel.add(label, BorderLayout.CENTER);
//		JButton btnOK = new JButton("OK");
//		btnOK.addActionListener(new ActionListener() {
//
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				dialog.setVisible(false);
//				dialog.dispose();
//			}
//
//		});
//		JPanel panelButtons = new JPanel();
//		panelButtons.add(btnOK);
//		panel.add(panelButtons, BorderLayout.SOUTH);
//
//		dialog.add(panel);
//		dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//		dialog.pack();
//		dialog.setLocationRelativeTo(null);
//		dialog.setVisible(true);
//	}


	/**
	 * Prompt to remove a single, specified selected object.
	 * 
	 * @param pathObjectSelected
	 * @param hierarchy
	 * @return
	 */
	public static boolean promptToRemoveSelectedObject(PathObject pathObjectSelected, PathObjectHierarchy hierarchy) {
			// Can't delete null - or a TMACoreObject
			if (pathObjectSelected == null || pathObjectSelected instanceof TMACoreObject)
				return false;
			
			// Deselect first
			hierarchy.getSelectionModel().deselectObject(pathObjectSelected);

			if (pathObjectSelected.hasChildren()) {
				DialogButton confirm = showYesNoCancelDialog("Delete object", String.format("Keep %d descendant object(s)?", PathObjectTools.countDescendants(pathObjectSelected)));
				if (confirm == DialogButton.CANCEL)
					return false;
				if (confirm == DialogButton.YES)
					hierarchy.removeObject(pathObjectSelected, true);
				else
					hierarchy.removeObject(pathObjectSelected, false);
			} else if (PathObjectTools.hasPointROI(pathObjectSelected)) {
				int nPoints = ((PointsROI)pathObjectSelected.getROI()).getNumPoints();
				if (nPoints > 1) {
					if (!DisplayHelpers.showYesNoDialog("Delete object", String.format("Delete %d points?", nPoints)))
						return false;
					else
						hierarchy.removeObject(pathObjectSelected, false);
				} else
					hierarchy.removeObject(pathObjectSelected, false);	
			} else if (pathObjectSelected.isDetection()) {
				// Check whether to delete a detection object... can't simply be redrawn (like an annotation), so be cautious...
				if (!DisplayHelpers.showYesNoDialog("Delete object", "Are you sure you want to delete this detection object?"))
					return false;
				else
					hierarchy.removeObject(pathObjectSelected, false);
			} else
				hierarchy.removeObject(pathObjectSelected, false);
	//		updateRoiEditor();
	//		pathROIs.getObjectList().remove(pathObjectSelected);
	//		repaint();
			return true;
		}
	
	
	
//	public static String showInputDialog(final String title, String message, String defaultText) {
//		if (!GraphicsEnvironment.isHeadless()) {
//	
//			final JDialog dialog = new JDialog((Frame)null, title, true);
//	
//			if (message.contains("\n"))
//				message = message.replaceAll("\n", "<br>");
//			JLabel label = new JLabel(message);
//			JPanel panel = new JPanel(new BorderLayout());
//			panel.add(label, BorderLayout.NORTH);
//			final JTextField textField = new JTextField();
//			if (defaultText != null)
//				textField.setText(defaultText);
//			panel.add(textField, BorderLayout.CENTER);
//			JButton btnOK = new JButton("OK");
//			btnOK.addActionListener(new ActionListener() {
//	
//				@Override
//				public void actionPerformed(ActionEvent e) {
//					dialog.setVisible(false);
//					dialog.dispose();
//				}
//				
//			});
//			JPanel panelButtons = new JPanel();
//			panelButtons.add(btnOK);
//			panel.add(panelButtons, BorderLayout.SOUTH);
//			
//			dialog.add(panel);
//			dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//			dialog.pack();
//			dialog.setLocationRelativeTo(null);
//			dialog.setModal(true);
//			dialog.setVisible(true);
//			return textField.getText();
//		}
//		return null;
//	}
	
	/**
	 * Kinds of snapshot image that can be created for QuPath.
	 */
	public static enum SnapshotType {
		/**
		 * Snapshot of the current viewer content.
		 */
		CURRENT_VIEWER,
		/**
		 * Snapshot of the full Scene of the main QuPath Window.
		 * This excludes the titlebar and any overlapping windows.
		 */
		MAIN_SCENE,
		/**
		 * Screenshot of the full QuPath window as it currently appears, including any overlapping windows.
		 */
		MAIN_WINDOW_SCREENSHOT,
		/**
		 * Full screenshot, including items outside of QuPath.
		 */
		FULL_SCREENSHOT
	};
	
	/**
	 * Make a snapshot (image) showing what is currently displayed in a QuPath window
	 * or the active viewer within QuPath, as determined by the SnapshotType.
	 * 
	 * @param qupath
	 * @param type
	 * @return
	 */
	public static BufferedImage makeSnapshot(final QuPathGUI qupath, final SnapshotType type) {
		return SwingFXUtils.fromFXImage(makeSnapshotFX(qupath, type), null);
	}
	
	/**
	 * Make a snapshot as a JavaFX {@link Image}.
	 * @param qupath
	 * @param type
	 * @return
	 */
	public static WritableImage makeSnapshotFX(final QuPathGUI qupath, final SnapshotType type) {
		Stage stage = qupath.getStage();
		Scene scene = stage.getScene();
		switch (type) {
		case CURRENT_VIEWER:
			// Temporarily remove the selected border color while copying
			Color borderColor = qupath.getViewer().getBorderColor();
			try {
				qupath.getViewer().setBorderColor(null);
				return qupath.getViewer().getView().snapshot(null, null);
			} finally {
				qupath.getViewer().setBorderColor(borderColor);
			}
		case MAIN_SCENE:
			return scene.snapshot(null);
		case MAIN_WINDOW_SCREENSHOT:
			double x = scene.getX() + stage.getX();
			double y = scene.getY() + stage.getY();
			double width = scene.getWidth();
			double height = scene.getHeight();
			try {
				// For reasons I do not understand, this occasionally throws an ArrayIndexOutOfBoundsException
				return new Robot().getScreenCapture(null,
						x, y, width, height, false);
			} catch (Exception e) {
				logger.error("Unable to make main window screenshot, will resort to trying to crop a full screenshot instead", e);
				var img2 = makeSnapshotFX(qupath, SnapshotType.FULL_SCREENSHOT);
				return new WritableImage(img2.getPixelReader(),
						(int)x, (int)y, (int)width, (int)height);
			}
		case FULL_SCREENSHOT:
			var screen = Screen.getPrimary();
			var bounds = screen.getBounds();
			return new Robot().getScreenCapture(null,
					bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
		default:
			throw new IllegalArgumentException("Unknown snaptop type " + type);
		}
	}

	/**
	 * Get an appropriate String to represent the magnification of the image currently in the viewer.
	 * @param viewer
	 * @return
	 */
	public static String getMagnificationString(final QuPathViewer viewer) {
		if (viewer == null || !viewer.hasServer())
			return "";
//		if (Double.isFinite(viewer.getServer().getMetadata().getMagnification()))
			return String.format("%.2fx", viewer.getMagnification());
//		else
//			return String.format("Scale %.2f", viewer.getDownsampleFactor());
	}




	/**
	 * Prompt user to select all currently-selected objects (except TMA core objects).
	 * 
	 * @param imageData
	 * @return
	 */
	public static boolean promptToClearAllSelectedObjects(final ImageData<?> imageData) {
		// Get all non-TMA core objects
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> selectedRaw = hierarchy.getSelectionModel().getSelectedObjects();
		List<PathObject> selected = selectedRaw.stream().filter(p -> !(p instanceof TMACoreObject)).collect(Collectors.toList());
	
		if (selected.isEmpty()) {
			if (selectedRaw.size() > selected.size())
				showErrorMessage("Delete selected objects", "No valid objects selected! \n\nNote: Individual TMA cores cannot be deleted with this method.");
			else
				showErrorMessage("Delete selected objects", "No objects selected!");
			return false;
		}
	
		int n = selected.size();
		if (showYesNoDialog("Delete objects", "Delete " + n + " objects?")) {
			// Check for descendants
			List<PathObject> children = new ArrayList<>();
			for (PathObject temp : selected) {
				children.addAll(temp.getChildObjects());
			}
			children.removeAll(selected);
			boolean keepChildren = true;
			if (!children.isEmpty()) {
				DialogButton response = DisplayHelpers.showYesNoCancelDialog("Delete objects", "Keep descendant objects?");
				if (response == DialogButton.CANCEL)
					return false;
				keepChildren = response == DialogButton.YES;
			}
			
			
			hierarchy.removeObjects(selected, keepChildren);
			hierarchy.getSelectionModel().clearSelection();
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Delete selected objects", "clearSelectedObjects(" + keepChildren + ");"));
			if (keepChildren)
				logger.info(selected.size() + " object(s) deleted");
			else
				logger.info(selected.size() + " object(s) deleted with descendants");
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear selected objects", "clearSelectedObjects();"));
			logger.info(selected.size() + " object(s) deleted");
			return true;
		} else
			return false;
	}
	

	/**
	 * Show a window containing plain text, with the specified properties.
	 * 
	 * @param owner
	 * @param title
	 * @param contents
	 * @param modality
	 * @param isEditable
	 */
	public static void showTextWindow(final Window owner, final String title, final String contents, final Modality modality, final boolean isEditable) {
		logger.info("{}\n{}", title, contents);
		Stage dialog = new Stage();
		dialog.initOwner(owner);
		dialog.initModality(modality);
		dialog.setTitle(title);
		
		TextArea textArea = new TextArea();
		textArea.setPrefColumnCount(60);
		textArea.setPrefRowCount(25);

		textArea.setText(contents);
		textArea.setWrapText(true);
		textArea.positionCaret(0);
		textArea.setEditable(isEditable);
		
		dialog.setScene(new Scene(textArea));
		dialog.show();
	}


	/**
	 * Prompt to enter a filename (but not full file path).
	 * This performs additional validation on the filename, stripping out illegal characters if necessary
	 * and requesting the user to confirm if the result is acceptable or showing an error message if
	 * no valid name can be derived from the input.
	 * @param title dialog title
	 * @param prompt prompt to display to the user
	 * @param defaultName default name when the dialog is shown
	 * @return the validated filename, or null if the user cancelled or did not provide any valid input
	 * @see GeneralTools#stripInvalidFilenameChars(String)
	 * @see GeneralTools#isValidFilename(String)
	 */
	public static String promptForFilename(String title, String prompt, String defaultName) {
		String name = showInputDialog(title, prompt, defaultName);
		if (name == null)
			return null;

		String nameValidated = GeneralTools.stripInvalidFilenameChars(name);
		if (!GeneralTools.isValidFilename(nameValidated)) {
			showErrorMessage(title, name + " is not a valid filename!");
			return null;
		}
		if (!nameValidated.equals(name)) {
			if (!showYesNoDialog(
					"Invalid classifier name", name + " contains invalid characters, do you want to use " + nameValidated + " instead?"))
				return null;
		}
		return nameValidated;
	}





	/**
	 * Return a result after executing a Callable on the JavaFX Platform thread.
	 *
	 * @param callable
	 * @return
	 */
	public static <T> T callOnApplicationThread(final Callable<T> callable) {
		if (Platform.isFxApplicationThread()) {
			try {
				return callable.call();
			} catch (Exception e) {
				logger.error("Error calling directly on Platform thread", e);
				return null;
			}
		}

		CountDownLatch latch = new CountDownLatch(1);
		ObjectProperty<T> result = new SimpleObjectProperty<>();
		Platform.runLater(() -> {
			T value;
			try {
				value = callable.call();
				result.setValue(value);
			} catch (Exception e) {
				logger.error("Error calling on Platform thread", e);
			} finally {
				latch.countDown();
			}
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			logger.error("Interrupted while waiting result", e);
		}
		return result.getValue();
	}

	
}
