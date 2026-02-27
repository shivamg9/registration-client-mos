package io.mosip.registration.controller;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;
import io.mosip.kernel.core.idvalidator.spi.PridValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.schema.ProcessSpecDto;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

@Controller
public class QrCodePopUpViewController extends BaseController implements Initializable, Runnable, ThreadFactory {
    private static final Logger LOGGER = AppConfig.getLogger(QrCodePopUpViewController.class);

    @FXML
    private GridPane captureWindow;
    @FXML
    private ComboBox<String> availableWebcams;

    @Value("${mosip.doc.stage.width:400}")
    private int width;
    @Value("${mosip.doc.stage.height:400}")
    private int height;

    @Autowired
    private GenericController genericController;
    @Autowired
    private PridValidator<String> pridValidatorImpl;

    private Stage popupStage;
    private WebcamPanel panel = null;
    private Webcam webcam = null;
    private ExecutorService executor;
    private volatile boolean isRunning = false; // Control flag for the scanning loop

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (Webcam.getWebcams().isEmpty()) {
            generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_DEVICES_DETECTED));
        } else {
            availableWebcams.getItems().addAll(Webcam.getWebcams().stream().map(Webcam::getName).collect(Collectors.toList()));
            availableWebcams.getSelectionModel().select(0);
            availableWebcams.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
                stopStreaming();
                initWebcam(newValue);
            });
        }
    }

    public void init(String title) {
        try {
            Parent scanPopup = BaseController.load(getClass().getResource("/fxml/QrCode.fxml"));
            Scene scene = new Scene(scanPopup, width, height);
            if (getCssName() != null) {
                scene.getStylesheets().add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
            }

            popupStage = new Stage();
            popupStage.setScene(scene);
            popupStage.initModality(Modality.WINDOW_MODAL);
            popupStage.initOwner(fXComponents.getStage());
            popupStage.setTitle(title);
            popupStage.setOnCloseRequest(this::exitWindow);

            isRunning = true; // Set loop flag to true
            executor = Executors.newSingleThreadExecutor(this);

            initWebcam(availableWebcams.getSelectionModel().getSelectedItem());
            popupStage.show();
        } catch (IOException exception) {
            LOGGER.error("Unable to load QR popup", exception);
        }
    }

    private void initWebcam(String name) {
        try {
            Dimension size = WebcamResolution.VGA.getSize();
            webcam = Webcam.getWebcamByName(name);
            if (webcam != null && !webcam.isOpen()) {
                webcam.setViewSize(size);
                webcam.open(); // Open camera hardware

                panel = new WebcamPanel(webcam);
                panel.setPreferredSize(size);

                final SwingNode swingNode = new SwingNode();
                swingNode.setContent(panel);

                // FIX: Ensure UI updates happen on the JavaFX Thread
                Platform.runLater(() -> {
                    captureWindow.getChildren().clear();
                    captureWindow.getChildren().add(swingNode);
                });

                executor.execute(this);
            }
        } catch (Exception e) {
            LOGGER.error("Webcam init error", e);
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                if (webcam == null || !webcam.isOpen()) {
                    Thread.sleep(500);
                    continue;
                }

                BufferedImage image = webcam.getImage();
                if (image == null) continue;

                LuminanceSource source = new BufferedImageLuminanceSource(image);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                try {
                    Result result = new MultiFormatReader().decode(bitmap);
                    if (result != null && result.getText() != null) {
                        String scannedText = result.getText().trim();
                        LOGGER.info("QR Scanned: " + scannedText);

                        // Basic check to ensure it's not empty before proceeding
                        if (!scannedText.isEmpty()) {
                            isRunning = false; // STOP THE LOOP IMMEDIATELY
                            Platform.runLater(() -> processScannedResult(scannedText));
                        }
                    }
                } catch (NotFoundException nfe) {
                    // QR not in frame, keep searching
                }

                Thread.sleep(200); // Faster scanning (5 frames per sec)
            } catch (Exception e) {
                LOGGER.error("Scanning error", e);
            }
        }
    }

    private void processScannedResult(String resultText) {
        boolean isValid = false;
        try {
            isValid = pridValidatorImpl.validateId(resultText);
        } catch (Exception e) {
            isValid = false;
        }

        if (isValid) {
            closeAndFetch(resultText);
        } else {
            generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationUIConstants.PRE_REG_ID_NOT_VALID);
            isRunning = true; // Resume loop if invalid
            executor.execute(this);
        }
    }

    private void closeAndFetch(String registrationNumber) {
        // 1. Set the text in the Generic Controller
        if (genericController.getRegistrationNumberTextField() != null) {
            genericController.getRegistrationNumberTextField().setText(registrationNumber);

            // 2. Trigger the fetch logic automatically
            RegistrationDTO registrationDTO = getRegistrationDTOFromSession();
            ProcessSpecDto processSpecDto = genericController.getProcessSpec(registrationDTO.getProcessId(), registrationDTO.getIdSchemaVersion());
            genericController.executePreRegFetchTask(genericController.getRegistrationNumberTextField(), processSpecDto.getFlow());
        }

        stopStreaming();
        if (popupStage != null) {
            popupStage.close();
        }
    }

    public void exitWindow(WindowEvent event) {
        isRunning = false;
        stopStreaming();
        if (executor != null) executor.shutdownNow();
    }

    private void stopStreaming() {
        isRunning = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t = new Thread(r, "Scan-QR-Thread");
        t.setDaemon(true);
        return t;
    }
}
