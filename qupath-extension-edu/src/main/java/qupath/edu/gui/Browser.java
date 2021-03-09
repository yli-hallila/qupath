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
        this(content, true);
    }

    public Browser(String content, boolean body) {
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

        setContent(content, body);
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

        String CSS = ":root{--ck-color-mention-background:hsla(341,100%,30%,0.1);--ck-color-mention-text:hsl(341,100%,30%);--ck-highlight-marker-blue:hsl(201,97%,72%);--ck-highlight-marker-green:hsl(120,93%,68%);--ck-highlight-marker-pink:hsl(345,96%,73%);--ck-highlight-marker-yellow:hsl(60,97%,73%);--ck-highlight-pen-green:hsl(112,100%,27%);--ck-highlight-pen-red:hsl(0,85%,49%);--ck-image-style-spacing:1.5em;--ck-todo-list-checkmark-size:16px}.ck-content .image-style-side{float:right;margin-left:var(--ck-image-style-spacing);max-width:50%}.ck-content .image-style-align-left{float:left;margin-right:var(--ck-image-style-spacing)}.ck-content .image-style-align-center{margin-left:auto;margin-right:auto}.ck-content .image-style-align-right{float:right;margin-left:var(--ck-image-style-spacing)}.ck-content .image.image_resized{max-width:100%;display:block;box-sizing:border-box}.ck-content .image.image_resized img{width:100%}.ck-content .image.image_resized>figcaption{display:block}.ck-content .image>figcaption{display:table-caption;caption-side:bottom;word-break:break-word;color:hsl(0,0%,20%);background-color:hsl(0,0%,97%);padding:.6em;font-size:.75em;outline-offset:-1px}.ck-content .image{display:table;clear:both;text-align:center;margin:1em auto}.ck-content .image img{display:block;margin:0 auto;max-width:100%;min-width:50px}.ck-content .marker-yellow{background-color:var(--ck-highlight-marker-yellow)}.ck-content .marker-green{background-color:var(--ck-highlight-marker-green)}.ck-content .marker-pink{background-color:var(--ck-highlight-marker-pink)}.ck-content .marker-blue{background-color:var(--ck-highlight-marker-blue)}.ck-content .pen-red{color:var(--ck-highlight-pen-red);background-color:transparent}.ck-content .pen-green{color:var(--ck-highlight-pen-green);background-color:transparent}.ck-content .text-tiny{font-size:.7em}.ck-content .text-small{font-size:.85em}.ck-content .text-big{font-size:1.4em}.ck-content .text-huge{font-size:1.8em}.ck-content blockquote{overflow:hidden;padding-right:1.5em;padding-left:1.5em;margin-left:0;margin-right:0;font-style:italic;border-left:solid 5px hsl(0,0%,80%)}.ck-content[dir=\"rtl\"] blockquote{border-left:0;border-right:solid 5px hsl(0,0%,80%)}.ck-content code{background-color:hsla(0,0%,78%,0.3);padding:.15em;border-radius:2px}.ck-content .table{margin:1em auto;display:table}.ck-content .table table{border-collapse:collapse;border-spacing:0;width:100%;height:100%;border:1px double hsl(0,0%,70%)}.ck-content .table table td,.ck-content .table table th{min-width:2em;padding:.4em;border:1px solid hsl(0,0%,75%)}.ck-content .table table th{font-weight:bold;background:hsla(0,0%,0%,5%)}.ck-content[dir=\"rtl\"] .table th{text-align:right}.ck-content[dir=\"ltr\"] .table th{text-align:left}.ck-content .page-break{position:relative;clear:both;padding:5px 0;display:flex;align-items:center;justify-content:center}.ck-content .page-break::after{content:'';position:absolute;border-bottom:2px dashed hsl(0,0%,77%);width:100%}.ck-content .page-break__label{position:relative;z-index:1;padding:.3em .6em;display:block;text-transform:uppercase;border:1px solid hsl(0,0%,77%);border-radius:2px;font-family:Helvetica,Arial,Tahoma,Verdana,Sans-Serif;font-size:.75em;font-weight:bold;color:hsl(0,0%,20%);background:hsl(0,0%,100%);box-shadow:2px 2px 1px hsla(0,0%,0%,0.15);-webkit-user-select:none;-moz-user-select:none;-ms-user-select:none;user-select:none}.ck-content .todo-list{list-style:none}.ck-content .todo-list li{margin-bottom:5px}.ck-content .todo-list li .todo-list{margin-top:5px}.ck-content .todo-list .todo-list__label>input{-webkit-appearance:none;display:inline-block;position:relative;width:var(--ck-todo-list-checkmark-size);height:var(--ck-todo-list-checkmark-size);vertical-align:middle;border:0;left:-25px;margin-right:-15px;right:0;margin-left:0}.ck-content .todo-list .todo-list__label>input::before{display:block;position:absolute;box-sizing:border-box;content:'';width:100%;height:100%;border:1px solid hsl(0,0%,20%);border-radius:2px;transition:250ms ease-in-out box-shadow,250ms ease-in-out background,250ms ease-in-out border}.ck-content .todo-list .todo-list__label>input::after{display:block;position:absolute;box-sizing:content-box;pointer-events:none;content:'';left:calc(var(--ck-todo-list-checkmark-size) / 3);top:calc(var(--ck-todo-list-checkmark-size) / 5.3);width:calc(var(--ck-todo-list-checkmark-size) / 5.3);height:calc(var(--ck-todo-list-checkmark-size) / 2.6);border-style:solid;border-color:transparent;border-width:0 calc(var(--ck-todo-list-checkmark-size) / 8) calc(var(--ck-todo-list-checkmark-size) / 8) 0;transform:rotate(45deg)}.ck-content .todo-list .todo-list__label>input[checked]::before{background:hsl(126,64%,41%);border-color:hsl(126,64%,41%)}.ck-content .todo-list .todo-list__label>input[checked]::after{border-color:hsl(0,0%,100%)}.ck-content .todo-list .todo-list__label .todo-list__label__description{vertical-align:middle}.ck-content .media{clear:both;margin:1em 0;display:block;min-width:15em}.ck-content .raw-html-embed{margin:1em auto;min-width:15em;font-style:normal}.ck-content hr{margin:15px 0;height:4px;background:hsl(0,0%,87%);border:0}.ck-content pre{padding:1em;color:hsl(0,0%,20.8%);background:hsla(0,0%,78%,0.3);border:1px solid hsl(0,0%,77%);border-radius:2px;text-align:left;direction:ltr;tab-size:4;white-space:pre-wrap;font-style:normal;min-width:200px}.ck-content pre code{background:unset;padding:0;border-radius:0}.ck-content .mention{background:var(--ck-color-mention-background);color:var(--ck-color-mention-text)}@media print{.ck-content .page-break{padding:0}.ck-content .page-break::after{display:none}}";

        if (!isTextHighlightable()) {
            CSS += ".ck-content { -webkit-user-select: none; cursor: default; }";
        }

        if (body) {
            return ("<html>" +
                    "<head>" +
                        "<style>" + CSS + "</style>" +
                    "</head>" +
                    "<body>" +
                        "<div class=\"ck-content\">" + content + "</div>" +
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
