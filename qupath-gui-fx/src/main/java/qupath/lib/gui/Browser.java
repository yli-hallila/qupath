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
            adjustHeight();
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
                    webView.setPrefHeight(height);
                }
            } catch (JSException ignored) {
                webView.setPrefHeight(0);
            }
        });
    }

    private String getHtml(String content, boolean body) {
        String DATA_FOLDER_URI = "";
        if (QuPathGUI.getInstance().getProject() != null) {
            DATA_FOLDER_URI = QuPathGUI.getInstance().getProjectDataDirectory(true).toPath().toUri().toString();
        }

        String CSS = "#content{word-wrap:break-word}blockquote,span[lang]{font-style:italic}figure,figure>figcaption{text-align:center}body{font-family:sans-serif,Arial,Verdana,\"Trebuchet MS\";font-size:12px;color:#333;background-color:#fff;margin:10px}blockquote{font-family:Georgia,Times,\"Times New Roman\",serif;padding:2px 0;border-style:solid;border-color:#ccc;border-width:0}a{color:#0782C1}dl,ol,ul{padding:0 40px}h1,h2,h3,h4,h5,h6{font-weight:400;line-height:1.2}hr{border:0;border-top:1px solid #ccc}img.left,img.right{border:1px solid #ccc;padding:5px}img.right{float:right;margin-left:15px}img.left{float:left;margin-right:15px}pre{white-space:pre-wrap;word-wrap:break-word;tab-size:4}.marker{background-color:#ff0}figure{outline:#ccc solid 1px;background:rgba(0,0,0,.05);padding:10px;margin:10px 20px;display:inline-block}a>img{padding:1px;margin:1px;border:none;outline:#0782C1 solid 1px}";

        if (!isTextHighlightable()) {
            CSS += "#content { -webkit-user-select: none; cursor: default; }";
        }

        content = content.replace("qupath://", DATA_FOLDER_URI);

        if (body) {
            return ("<html id=\"content\">" +
                    "<head>" +
                        "<style>" + CSS + "</style>" +
                    "</head>" +
                    "<body>" + content + "</body>" +
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
