package qupath.edu.gui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker.State;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

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
        webView.setPrefHeight(0);

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

                    // Works, but isn't perfect. Should maintain the previous URI
                    webEngine.getLoadWorker().cancel();
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
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setContent(content, body));
            return;
        }

        webEngine.loadContent(getHtml(content, body));
        adjustHeight();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        layoutInArea(webView, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
    }

    private void adjustHeight() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::adjustHeight);
            return;
        }

        try {
            Object result = webEngine.executeScript("document.getElementById('content').clientHeight");
            if (result instanceof Integer) {
                double height = Double.parseDouble(result.toString());
                if (height > 0) {
                    height += 20;
                }

                webView.setPrefHeight(height);
                this.setPrefHeight(height);
            }
        } catch (JSException ignored) {
            webView.setPrefHeight(0);
        }
    }

    private String getHtml(String content, boolean body) {
        content = content == null ? "" : content;

        String CSS = "#content{border:1px solid var(--ck-color-base-border);border-radius:var(--ck-border-radius);max-height:700px;display:flex;flex-flow:column nowrap}#content__toolbar{z-index:1;box-shadow:0 0 5px hsla(0,0%,0%,.2);border-bottom:1px solid var(--ck-color-toolbar-border)}#content__toolbar .ck-toolbar{border:0;border-radius:0}#content__editable-container{padding:calc(2 * var(--ck-spacing-large));background:var(--ck-color-base-foreground);overflow-y:scroll}#content__editable-container #content__editable.ck-editor__editable{width:15.8cm;min-height:21cm;padding:1cm 2cm 2cm;border:1px hsl(0,0%,82.7%) solid;border-radius:var(--ck-border-radius);background:#fff;box-shadow:0 0 5px hsla(0,0%,0%,.1);margin:0 auto}.main__content-wide #content__editable-container #content__editable.ck-editor__editable{width:18cm}#content .ck-content,#content .ck-heading-dropdown .ck-list .ck-button__label{font:16px/1.6 \"Helvetica Neue\",Helvetica,Arial,sans-serif}#content .ck-heading-dropdown .ck-list .ck-button__label{line-height:calc(1.7 * var(--ck-line-height-base) * var(--ck-font-size-base));min-width:6em}#content .ck-heading-dropdown .ck-list .ck-heading_heading1 .ck-button__label,#content .ck-heading-dropdown .ck-list .ck-heading_heading2 .ck-button__label{transform:scale(.8);transform-origin:left}#content .ck-content h2,#content .ck-heading-dropdown .ck-heading_heading1 .ck-button__label{font-size:2.18em;font-weight:400}#content .ck-content h2{line-height:1.37em;padding-top:.342em;margin-bottom:.142em}#content .ck-content h3,#content .ck-heading-dropdown .ck-heading_heading2 .ck-button__label{font-size:1.75em;font-weight:400;color:#009dff}#content .ck-heading-dropdown .ck-heading_heading2.ck-on .ck-button__label{color:var(--ck-color-list-button-on-text)}#content .ck-content h3{line-height:1.86em;padding-top:.171em;margin-bottom:.357em}#content .ck-content h4,#content .ck-heading-dropdown .ck-heading_heading3 .ck-button__label{font-size:1.31em;font-weight:700}#content .ck-content h4{line-height:1.24em;padding-top:.286em;margin-bottom:.952em}#content .ck-content blockquote{font-family:Georgia,serif;margin-left:calc(2 * var(--ck-spacing-large));margin-right:calc(2 * var(--ck-spacing-large))}";

        if (!isTextHighlightable()) {
            CSS += "#content { -webkit-user-select: none; cursor: default; }";
        }

        if (body) {
            return ("<html>" +
                    "<head>" +
                        "<style>" + CSS + "</style>" +
                    "</head>" +
                    "<body>" +
                        "<div id=\"content\">" + content + "</div>" +
                    "</body>" +
                    "</html>");
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
