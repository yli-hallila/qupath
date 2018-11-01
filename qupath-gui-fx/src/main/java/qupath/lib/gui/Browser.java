package qupath.lib.gui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker.State;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;

// Based on: http://java-no-makanaikata.blogspot.com/2012/10/javafx-webview-size-trick.html
public class Browser extends Region {

    static Logger logger = LoggerFactory.getLogger(Browser.class);

    private WebView webView = new WebView();
    private WebEngine webEngine = webView.getEngine();

    private boolean textHighlightable = true;

    public boolean isTextHighlightable() {
        return textHighlightable;
    }

    public void setTextHighlightable(boolean textHighlightable) {
        this.textHighlightable = textHighlightable;
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

        webView.setContextMenuEnabled(false);

        setContent(content);
        getChildren().add(webView);
    }

    public void setContent(String content) {
        Platform.runLater(() -> {
            webEngine.loadContent(getHtml(content));
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

    private String getHtml(String content) {
        String DATA_FOLDER_URI = "";
        if (QuPathGUI.getInstance().getProjectDataDirectory(false) != null) {
            DATA_FOLDER_URI = QuPathGUI.getInstance().getProjectDataDirectory(false).toPath().toUri().toString();
        }

        String CSS = "body { overflow-y: hidden; }";

        if (!isTextHighlightable()) {
            CSS += "#content { -webkit-user-select: none; cursor: default; }";
        }

        return ("<html><head>" +
                "<style>" + CSS + "</style>" +
                "</head><body>" +
                "<div id=\"content\">" + content + "</div>" +
                "</body></html>").replace("<qupath>", DATA_FOLDER_URI);
    }
}
