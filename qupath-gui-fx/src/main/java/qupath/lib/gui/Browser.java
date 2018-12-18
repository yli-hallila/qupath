package qupath.lib.gui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker.State;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.control.Dialog;
import netscape.javascript.JSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;

// Based on: http://java-no-makanaikata.blogspot.com/2012/10/javafx-webview-size-trick.html
public class Browser extends Region {

    private final Logger logger = LoggerFactory.getLogger(Browser.class);

    private WebView webView = new WebView();
    private WebEngine webEngine = webView.getEngine();

    private boolean textHighlightable = true;

    public boolean isTextHighlightable() {
        return textHighlightable;
    }

    public void setTextHighlightable(boolean textHighlightable) {
        this.textHighlightable = textHighlightable;
    }

    public Browser() {
        this("");
    }

    public Browser(String content) {
        webView.setPrefHeight(5);
        this.setPadding(new Insets(5));

        widthProperty().addListener((ChangeListener<Object>) (observable, oldWidth, newWidth) -> {
            webView.setPrefWidth((Double) newWidth);
            adjustHeight();
        });

        webView.getEngine().getLoadWorker().stateProperty().addListener((arg0, oldState, newState) -> {
            if (newState == State.SUCCEEDED) {
                adjustHeight();
            }
        });

        webEngine.locationProperty().addListener((observable, oldValue, location) -> {
            if (!location.isEmpty()) {
                Platform.runLater(() -> {
                    try {
                        URI uri = new URI(location);
                        Desktop.getDesktop().browse(uri);
                    } catch (Exception ignored) {}

                    webEngine.getLoadWorker().cancel(); // Works, but isn't perfect. Should maintain the previous URI
                });
            }
        });

        webEngine.setOnAlert(event -> showAlert(event.getData()));
        webEngine.setConfirmHandler(this::showConfirm);

        webView.setContextMenuEnabled(false);

        setContent(content);
        getChildren().add(webView);
    }

    public void setContent(String content) {
        setContent(content, true);
    }

    public void setContent(String content, boolean body) {
        Platform.runLater(() -> {
            webEngine.loadContent(getHtml(content, body));
            Platform.runLater(this::adjustHeight);
        });
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        layoutInArea(webView, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
    }

    private void adjustHeight() {
        Platform.runLater(() -> {
            try {
                Object result = webEngine.executeScript("document.getElementById('content').offsetHeight");
                if (result instanceof Integer) {
                    double height = new Double(result.toString());
                    webView.setPrefHeight(height + 8);
                }
            } catch (JSException ignored) {}
        });
    }

    private String getHtml(String content, boolean body) {
        String DATA_FOLDER_URI = "";
        if (QuPathGUI.getInstance().getProject() != null) {
            DATA_FOLDER_URI = QuPathGUI.getInstance().getProjectDataDirectory(true).toPath().toUri().toString();
        }

        String CSS = "";

        if (!isTextHighlightable()) {
            CSS += "#content { -webkit-user-select: none; cursor: default; }";
        }

        content = content.replace("qupath://", DATA_FOLDER_URI);

        if (body) {
            return ("<html><head>" +
                    "<style>" + CSS + "</style>" +
                    "</head><body>" +
                    "<div id=\"content\">" + content + "</div>" +
                    "</body></html>");
        }

        return content;
    }

    public WebView getWebView() {
        return webView;
    }

    public WebEngine getWebEngine() {
        return webEngine;
    }

    private void showAlert(String message) {
        Dialog<Void> alert = new Dialog<>();
        alert.getDialogPane().setContentText(message);
        alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
        alert.showAndWait();
    }

    private boolean showConfirm(String message) {
        Dialog<ButtonType> confirm = new Dialog<>();
        confirm.getDialogPane().setContentText(message);
        confirm.getDialogPane().getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
        boolean result = confirm.showAndWait().filter(ButtonType.YES::equals).isPresent();

        return result;
    }
}
