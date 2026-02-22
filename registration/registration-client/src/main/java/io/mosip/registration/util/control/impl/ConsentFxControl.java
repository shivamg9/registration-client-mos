package io.mosip.registration.util.control.impl;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.util.control.FxControl;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsentFxControl extends FxControl implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsentFxControl.class);

    // Zero Width Non-Joiner used to force complex text shaping in JavaFX
    private static final String ZWNJ = "\u200c";

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private VBox consentVBox;

    // This must match fx:id="consentTitledPane" in the FXML
    @FXML
    private TitledPane consentTitledPane;

    @FXML
    private VBox consentContent;

    @FXML
    private TextFlow consentTextFlow;

    private AuditManagerService auditFactory;

    public ConsentFxControl() {
        try {
            org.springframework.context.ApplicationContext applicationContext = ClientApplication.getApplicationContext();
            auditFactory = applicationContext.getBean(AuditManagerService.class);
        } catch (Exception e) {
            LOGGER.error("Failed to get audit factory", e);
        }
    }

    @Override
    public FxControl build(UiFieldDTO uiFieldDTO) {
        this.uiFieldDTO = uiFieldDTO;
        this.control = this;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(RegistrationConstants.CONSENT_FXML_PATH));
            loader.setController(this);

            // FIX: Using ScrollPane as root to match FXML
            ScrollPane root = loader.load();

            initializeConsent(uiFieldDTO);

            this.node = root;

            auditFactory.audit(AuditEvent.REG_HTML_FX_CONTROL, Components.REG_DEMO_DETAILS,
                    SessionContext.userId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

        } catch (IOException e) {
            LOGGER.error("Failed to load consent FXML", e);
            VBox fallback = new VBox();
            Label errorLabel = new Label("Failed to load consent screen");
            fallback.getChildren().add(errorLabel);
            this.node = fallback;
        }

        return this.control;
    }

    private void initializeConsent(UiFieldDTO fieldDTO) {
        List<String> selectedLanguages = getRegistrationDTo().getSelectedLanguagesByApplicant();
        String firstLanguage = selectedLanguages.get(0);

        // Set consent Dropdown Title based on language
        String titleText = getLocalizedConsentTitle(firstLanguage);

        if (consentTitledPane != null) {
            consentTitledPane.setText(titleText);

            // Ensure font is applied to the title of the dropdown
            applyFontForLanguage(consentTitledPane, firstLanguage);

            // Force it to be collapsed (closed) initially
            consentTitledPane.setExpanded(false);
        } else {
            LOGGER.error("consentTitledPane is NULL. Check FXML fx:id.");
        }

        buildConsentText(selectedLanguages);
    }

    private String getLocalizedConsentTitle(String langCode) {
        switch (langCode.toLowerCase()) {
            case "eng":
            case "en":
                return "Consent";
            case "bur":
            case "my":
                return ZWNJ + "သဘောတူညီချက်" + ZWNJ;
            default:
                return "Consent";
        }
    }

    private void buildConsentText(List<String> languages) {
        if (consentTextFlow == null) return;

        consentTextFlow.getChildren().clear();

        for (String langCode : languages) {
            String consentText = getConsentTextForLanguage(langCode);

            Text textNode = new Text(consentText);
            applyFontForLanguage(textNode, langCode);

            consentTextFlow.getChildren().add(textNode);

            if (languages.indexOf(langCode) < languages.size() - 1) {
                Text separator = new Text("\n\n");
                consentTextFlow.getChildren().add(separator);
            }
        }
    }

    private String getConsentTextForLanguage(String langCode) {
        switch (langCode.toLowerCase()) {
            case "eng":
            case "en":
                return "I understand that the data collected about me during registration by the said authority includes:\n\n" +
                        "• Name\n" +
                        "• Date of birth\n" +
                        "• Gender\n" +
                        "• Address\n" +
                        "• Contact details\n" +
                        "• Documents\n" +
                        "• Biometrics\n\n" +
                        "I also understand that this information will be stored and processed for the purpose of verifying my identity in order \n" +
                        "to access various services, or to comply with a legal obligation.\n" +
                        "I give my consent for the collection of this data for this purpose.";

            case "bur":
            case "my":
                return ZWNJ + "အဆိုပါ အာဏာပိုင်အဖွဲ့အစည်းမှ မှတ်ပုံတင်ခြင်းလုပ်ငန်းစဉ်အတွင်း ကျွန်ုပ်နှင့်ပတ်သက်၍ ကောက်ယူမည့် အချက်အလက်များတွင် အောက်ပါတို့ ပါဝင်ကြောင်း ကျွန်ုပ်နားလည်ပါသည်-\n\n" + ZWNJ+
                        "• အမည်\n"+ ZWNJ +
                        "• မွေးသက္ကရာဇ်\n" + ZWNJ+
                        "• ကျား/မ\n" + ZWNJ+
                        "• လိပ်စာ\n"+ ZWNJ +
                        "• ဆက်သွယ်ရန်လိပ်စာ\n"+ ZWNJ +
                        "• စာရွက်စာတမ်းများ\n"+ ZWNJ +
                        "• ဇီဝအချက်အလက်များ\n\n" + ZWNJ+
                        "ဤအချက်အလက်များကို ဝန်ဆောင်မှုအမျိုးမျိုးရယူရန်အတွက် ကျွန်ုပ်၏ မည်သူမည်ဝါဖြစ်ကြောင်း အတည်ပြုရန် (သို့မဟုတ်) ဥပဒေကြောင်းအရ လိုက်နာဆောင်ရွက်ရန်\n"+ ZWNJ +
                        "ရည်ရွယ်ချက်ဖြင့် သိမ်းဆည်းပြီး လုပ်ဆောင်သွားမည်ကိုလည်း ကျွန်ုပ်နားလည်ပါသည်။ ဤရည်ရွယ်ချက်အတွက် ဤဒေတာ (အချက်အလက်)\n "+ ZWNJ +
                        "များ စုဆောင်းခြင်းကို ကျွန်ုပ်သဘောတူညီပါသည်။" + ZWNJ;
            default:
                return getConsentTextForLanguage("eng");
        }
    }

    private void applyFontForLanguage(javafx.scene.Node node, String langCode) {
        String burmeseStyle = "-fx-font-family: 'Myanmar Text', 'Noto Sans Myanmar', sans-serif; -fx-font-size: 14px;";

        if ("bur".equalsIgnoreCase(langCode) || "my".equalsIgnoreCase(langCode)) {
            if (node instanceof Label) {
                ((Label) node).setStyle(burmeseStyle);
            } else if (node instanceof Text) {
                Text text = (Text) node;
                text.setStyle(text.getStyle() + burmeseStyle);
            } else if (node instanceof TitledPane) {
                ((TitledPane) node).setStyle(burmeseStyle);
            }
        }
    }

    @Override
    public void setData(Object data) {
        String consentValue = RegistrationConstants.YES;
        if (this.uiFieldDTO.getType().equalsIgnoreCase(RegistrationConstants.SIMPLE_TYPE)) {
            List<SimpleDto> values = new ArrayList<SimpleDto>();
            for (String langCode : getRegistrationDTo().getSelectedLanguagesByApplicant()) {
                values.add(new SimpleDto(langCode, consentValue));
            }
            getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), values);
        } else {
            getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), consentValue);
        }
    }

    @Override
    public void fillData(Object data) { }

    @Override
    public Object getData() { return null; }

    @Override
    public boolean isValid() { return true; }

    @Override
    public boolean isEmpty() { return true; }

    @Override
    public List<GenericDto> getPossibleValues(String langCode) { return null; }

    @Override
    public void setListener(Node node) { }

    @Override
    public void selectAndSet(Object data) { }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (consentVBox != null) consentVBox.getStyleClass().add("consent-vbox");
        if (consentContent != null) consentContent.getStyleClass().add("consent-content-vbox");
        if (consentTextFlow != null) consentTextFlow.getStyleClass().add("consent-text-flow");
        if (consentTitledPane != null) consentTitledPane.getStyleClass().add("consent-titled-pane");
        if (scrollPane != null) scrollPane.setFitToWidth(true);
    }
}
