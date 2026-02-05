package io.mosip.registration.controller;

import static io.mosip.registration.constants.RegistrationConstants.EMPTY;
import static io.mosip.registration.constants.RegistrationConstants.HASH;
import static io.mosip.registration.constants.RegistrationConstants.REG_AUTH_PAGE;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;
import io.mosip.kernel.core.idvalidator.spi.PridValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.ProcessNames;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.auth.AuthenticationController;
import io.mosip.registration.controller.reg.RegistrationPreviewController;
import io.mosip.registration.dao.MasterSyncDao;
import io.mosip.registration.dto.ErrorResponseDTO;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.SuccessResponseDTO;
import io.mosip.registration.dto.schema.ProcessSpecDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.dto.schema.UiScreenDTO;
import io.mosip.registration.entity.LocationHierarchy;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.service.sync.PreRegistrationDataSyncService;
import io.mosip.registration.util.control.FxControl;
import io.mosip.registration.util.control.impl.BiometricFxControl;
import io.mosip.registration.util.control.impl.ButtonFxControl;
import io.mosip.registration.util.control.impl.CheckBoxFxControl;
import io.mosip.registration.util.control.impl.DOBAgeFxControl;
import io.mosip.registration.util.control.impl.DOBFxControl;
import io.mosip.registration.util.control.impl.DocumentFxControl;
import io.mosip.registration.util.control.impl.DropDownFxControl;
import io.mosip.registration.util.control.impl.HtmlFxControl;
import io.mosip.registration.util.control.impl.TextFieldFxControl;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.scene.Parent; // Add this import
import lombok.SneakyThrows;

/**
 * {@code GenericController} is to capture the demographic/demo/Biometric
 * details
 *
 * @author YASWANTH S
 * @since 1.0.0
 *
 */

@Controller
public class GenericController extends BaseController {

	protected static final Logger LOGGER = AppConfig.getLogger(GenericController.class);

	private static final String TAB_LABEL_ERROR_CLASS = "tabErrorLabel";
	private static final String LABEL_CLASS = "additionaInfoReqIdLabel";
	private static final String NAV_LABEL_CLASS = "navigationLabel";
	private static final String TEXTFIELD_CLASS = "preregFetchBtnStyle";
	private static final String CONTROLTYPE_TEXTFIELD = "textbox";
	private static final String CONTROLTYPE_BIOMETRICS = "biometrics";
	private static final String CONTROLTYPE_DOCUMENTS = "fileupload";
	private static final String CONTROLTYPE_DROPDOWN = "dropdown";
	private static final String CONTROLTYPE_CHECKBOX = "checkbox";
	private static final String CONTROLTYPE_BUTTON = "button";
	private static final String CONTROLTYPE_DOB = "date";
	private static final String CONTROLTYPE_DOB_AGE = "ageDate";
	private static final String CONTROLTYPE_HTML = "html";

	/**
	 * Top most Grid pane in FXML
	 */
	@FXML
	private GridPane genericScreen;

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private AnchorPane navigationAnchorPane;

	@FXML
	private Button next;

	@FXML
	private Button authenticate;

	@FXML
	private Label notification;
	private ComboBox<String> nrcCodeComboBox;
	private ComboBox<String> cityCodeComboBox;
	private ComboBox<String> citizenTypeComboBox;
	private TextField nrcNumberTextField;
	private TextField nrcNumber;
	private TextField constructedPridTextField;
	private Button nrcFetchBtn;

	private ProgressIndicator progressIndicator;
	
	private TextField registrationNumberTextField;

	private ProcessSpecDto processSpecDto;
	
	@Autowired
	private QrCodePopUpViewController qrCodePopUpViewController;

	@Autowired
	private AuthenticationController authenticationController;

	@Autowired
	private MasterSyncDao masterSyncDao;

	@Autowired
	private RegistrationPreviewController registrationPreviewController;

	@Autowired
	private PridValidator<String> pridValidatorImpl;

	@Autowired
	private PreRegistrationDataSyncService preRegistrationDataSyncService;

	@Autowired
	private io.mosip.registration.service.sync.MasterSyncService masterSyncService;

	private static TreeMap<Integer, UiScreenDTO> orderedScreens = new TreeMap<>();
	private static Map<String, FxControl> fxControlMap = new HashMap<String, FxControl>();
	private Stage keyboardStage;
	private boolean keyboardVisible = false;
	private String previousId;
	private Integer additionalInfoReqIdScreenOrder = null;
	public static Map<String, TreeMap<Integer, String>> hierarchyLevels = new HashMap<String, TreeMap<Integer, String>>();
	public static Map<String, TreeMap<Integer, String>> currentHierarchyMap = new HashMap<String, TreeMap<Integer, String>>();
	public static List<UiFieldDTO> fields = new ArrayList<>();

	public static Map<String, FxControl> getFxControlMap() {
		return fxControlMap;
	}

	public void disableAuthenticateButton(boolean disable) {
		authenticate.setDisable(disable);
	}

	private void initialize(RegistrationDTO registrationDTO) {
		orderedScreens.clear();
		fxControlMap.clear();
		hierarchyLevels.clear();
		currentHierarchyMap.clear();
		fillHierarchicalLevelsByLanguage();
		anchorPane.prefWidthProperty().bind(genericScreen.widthProperty());
		anchorPane.prefHeightProperty().bind(genericScreen.heightProperty());
		fields = getAllFields(registrationDTO.getProcessId(), registrationDTO.getIdSchemaVersion());
		additionalInfoReqIdScreenOrder = null;
		initializeNrcComponents();
	}


	private void fillHierarchicalLevelsByLanguage() {
		for(String langCode : getConfiguredLangCodes()) {
			TreeMap<Integer, String> hierarchicalData = new TreeMap<>();
			List<LocationHierarchy> hierarchies = masterSyncDao.getAllLocationHierarchy(langCode);
			hierarchies.forEach( hierarchy -> {
				hierarchicalData.put(hierarchy.getHierarchyLevel(), hierarchy.getHierarchyLevelName());
			});
			hierarchyLevels.put(langCode, hierarchicalData);
		}
	}

	private VBox getPreRegistrationFetchComponent() {
		String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

//		HBox hBox = new HBox();
//		hBox.setAlignment(Pos.CENTER_LEFT);
//		hBox.setSpacing(20);
//		hBox.setPrefHeight(100);
//		hBox.setPrefWidth(200);
		VBox mainContainer = new VBox();
		mainContainer.setSpacing(15);
		mainContainer.setPrefWidth(800);

		// Pre-Registration ID Search Section
		VBox preRegSection = new VBox();
		preRegSection.setSpacing(10);
		preRegSection.setStyle("-fx-background-color: #ffffff; -fx-padding: 10; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 5;");

		Label preRegTitleLabel = new Label();
		preRegTitleLabel.getStyleClass().add(LABEL_CLASS);
		preRegTitleLabel.setText("Pre-Registration ID Search");
		preRegTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

		HBox preRegHBox = new HBox();
		preRegHBox.setAlignment(Pos.CENTER_LEFT);
		preRegHBox.setSpacing(20);
		preRegHBox.setPrefHeight(40);


		Label label = new Label();
		label.getStyleClass().add(LABEL_CLASS);
		label.setId("preRegistrationLabel");
		label.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("search_for_Pre_registration_id"));
//		hBox.getChildren().add(label);
		preRegHBox.getChildren().add(label);

		TextField textField = new TextField();
		textField.setId("preRegistrationId");
		textField.getStyleClass().add(TEXTFIELD_CLASS);
//		hBox.getChildren().add(textField);
		textField.setPrefWidth(200);
		preRegHBox.getChildren().add(textField);
		this.registrationNumberTextField = textField;

		Button scanQRbutton = new Button();
		scanQRbutton.setId("scanQRBtn");
		scanQRbutton.setGraphic(new ImageView(
				new Image(this.getClass().getResourceAsStream("/images/QRCode.jpg"), 25, 25, true, true)));
		scanQRbutton.getStyleClass().add("demoGraphicPaneContentButton");

		Tooltip qrTooltip=new Tooltip();
		try{
			String tooltipText=ApplicationContext.getBundle(langCode,RegistrationConstants.MESSAGES).getString("SCAN_QR_TO_FETCH");
			qrTooltip.setText(tooltipText);
		} catch (Exception e){
			qrTooltip.setText("Click here to Scan QR to Fetch Pre-Registration Id Details");
		}
		scanQRbutton.setTooltip(qrTooltip);

		// FIX: Set this to open the QR Scanner, NOT the fetch task
		scanQRbutton.setOnAction(event -> {
			executeQRCodeScan();
		});
		preRegHBox.getChildren().add(scanQRbutton);

		Button button = new Button();
		button.setId("fetchBtn");
		button.getStyleClass().add("demoGraphicPaneContentButton");
		button.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("fetch"));

		button.setOnAction(event -> {
			// FIX: Safety check to prevent NullPointerException
			String flow = (this.processSpecDto != null) ? this.processSpecDto.getFlow() : "NEW";
			executePreRegFetchTask(textField, flow);
		});

//		hBox.getChildren().add(button);
		preRegHBox.getChildren().add(button);
		preRegSection.getChildren().addAll(preRegTitleLabel, preRegHBox);

		// NRC Search Section
		VBox nrcSection = new VBox();
		nrcSection.setSpacing(10);
		nrcSection.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 5;");

		Label nrcTitleLabel = new Label();
		nrcTitleLabel.getStyleClass().add(LABEL_CLASS);
		nrcTitleLabel.setText("NRC Number Search");
		nrcTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

		Label nrcLabel = new Label();
		nrcLabel.getStyleClass().add(LABEL_CLASS);
		nrcLabel.setId("nrcSearchLabel");
		nrcLabel.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("search_by_nrc_number"));

		HBox nrcInputHBox = new HBox();
		nrcInputHBox.setSpacing(10);
		nrcInputHBox.setAlignment(Pos.CENTER_LEFT);

		// NRC Components
		nrcCodeComboBox = new ComboBox<>();
		nrcCodeComboBox.setId("nrcCodeComboBox");
		nrcCodeComboBox.getStyleClass().add(TEXTFIELD_CLASS);
		nrcCodeComboBox.setPrefWidth(80);
		nrcCodeComboBox.setPromptText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("nrc_code"));
		nrcCodeComboBox.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");

		cityCodeComboBox = new ComboBox<>();
		cityCodeComboBox.setId("cityCodeComboBox");
		cityCodeComboBox.getStyleClass().add(TEXTFIELD_CLASS);
		cityCodeComboBox.setPrefWidth(120);
		cityCodeComboBox.setPromptText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("city_code"));
		cityCodeComboBox.getItems().addAll("Mandalay", "Yangon", "Naypyidaw", "Mawlamyine", "Bago", "Pathein", "Monywa", "Sittwe", "Magway", "Sagaing", "Taunggyi", "Myingyan", "Tamana", "Pyay");

		citizenTypeComboBox = new ComboBox<>();
		citizenTypeComboBox.setId("citizenTypeComboBox");
		citizenTypeComboBox.getStyleClass().add(TEXTFIELD_CLASS);
		citizenTypeComboBox.setPrefWidth(80);
		citizenTypeComboBox.setPromptText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("citizen_type"));
		citizenTypeComboBox.getItems().addAll("N", "P", "T");

		nrcNumberTextField = new TextField();
		nrcNumberTextField.setId("nrcNumberTextField");
		nrcNumberTextField.getStyleClass().add(TEXTFIELD_CLASS);
		nrcNumberTextField.setPrefWidth(100);
		nrcNumberTextField.setPromptText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("nrc_number"));

		nrcNumber = new TextField();
		nrcNumber.setId("nrcNumber");
		nrcNumber.getStyleClass().add(TEXTFIELD_CLASS);
		nrcNumber.setPrefWidth(200);
		nrcNumber.setEditable(false);
		nrcNumber.setPromptText("Full NRC Number");

		constructedPridTextField = new TextField();
		constructedPridTextField.setId("constructedPridTextField");
		constructedPridTextField.getStyleClass().add(TEXTFIELD_CLASS);
		constructedPridTextField.setPrefWidth(200);
		constructedPridTextField.setEditable(false);
		constructedPridTextField.setPromptText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("constructed_prid"));

		nrcInputHBox.getChildren().addAll(nrcCodeComboBox, cityCodeComboBox, citizenTypeComboBox, nrcNumberTextField, constructedPridTextField);

		HBox nrcButtonHBox = new HBox();
		nrcButtonHBox.setSpacing(10);
		nrcButtonHBox.setAlignment(Pos.CENTER_LEFT);

		nrcFetchBtn = new Button();
		nrcFetchBtn.setId("nrcFetchBtn");
		nrcFetchBtn.getStyleClass().add("demoGraphicPaneContentButton");
		nrcFetchBtn.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("fetch"));
		nrcFetchBtn.setOnAction(event -> fetchNrcData());

		nrcButtonHBox.getChildren().add(nrcFetchBtn);

		nrcSection.getChildren().addAll(nrcTitleLabel, nrcLabel, nrcInputHBox, nrcButtonHBox);

		// Add both sections to main container
		mainContainer.getChildren().addAll(preRegSection, nrcSection);

		// Initialize NRC components
		initializeNrcComponents();

		progressIndicator = new ProgressIndicator();
		progressIndicator.setId("progressIndicator");
		progressIndicator.setVisible(false);
//		hBox.getChildren().add(progressIndicator);
//		return hBox;
		mainContainer.getChildren().add(progressIndicator);

		return mainContainer;
	}

	void executePreRegFetchTask(TextField textField, String flow) {
		genericScreen.setDisable(true);
		progressIndicator.setVisible(true);

		Service<Void> taskService = new Service<Void>() {
			@Override
			protected Task<Void> createTask() {
				return new Task<Void>() {
					/*
					 * (non-Javadoc)
					 *
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected Void call() {
						Platform.runLater(() -> {
//							boolean isValid = false;
//							try {
//								isValid = pridValidatorImpl.validateId(textField.getText());
//							} catch (InvalidIDException invalidIDException) { isValid = false; }
//
//							if(!isValid) {
//								generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationUIConstants.PRE_REG_ID_NOT_VALID);
//								return;
//							}
							ResponseDTO responseDTO = preRegistrationDataSyncService.getPreRegistration(textField.getText(), false);

							if (responseDTO.getErrorResponseDTOs() != null
									&& !responseDTO.getErrorResponseDTOs().isEmpty()
									&& responseDTO.getErrorResponseDTOs().get(0).getMessage() != null
									&& responseDTO.getErrorResponseDTOs().get(0).getMessage()
									.equalsIgnoreCase(RegistrationConstants.CONSUMED_PRID_ERROR_CODE)) {
								generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationConstants.PRE_REG_CONSUMED_PACKET_ERROR);
								return;
							}

							try {
								loadPreRegSync(responseDTO);
								if (responseDTO.getSuccessResponseDTO() != null) {
									getRegistrationDTOFromSession().setPreRegistrationId(textField.getText());
									getRegistrationDTOFromSession().setAppId(textField.getText());
									TabPane tabPane = (TabPane) anchorPane.lookup(HASH+getRegistrationDTOFromSession().getRegistrationId());
									tabPane.setId(textField.getText());
									getRegistrationDTOFromSession().setRegistrationId(textField.getText());
								}
							} catch (RegBaseCheckedException exception) {
								generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationConstants.PRE_REG_TO_GET_PACKET_ERROR);
							}
						});
						return null;
					}
				};
			}
		};

		progressIndicator.progressProperty().bind(taskService.progressProperty());
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				genericScreen.setDisable(false);
				progressIndicator.setVisible(false);
			}
		});
		taskService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				LOGGER.debug("Pre Registration Fetch failed");
				genericScreen.setDisable(false);
				progressIndicator.setVisible(false);
			}
		});
	}

	private HBox getAdditionalInfoRequestIdComponent() {
		String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);
		HBox hBox = new HBox();
		hBox.setAlignment(Pos.CENTER_LEFT);
		hBox.setSpacing(20);
		hBox.setPrefHeight(100);
		hBox.setPrefWidth(200);
		Label label = new Label();
		label.getStyleClass().add(LABEL_CLASS);
		label.setId("additionalInfoRequestIdLabel");
		label.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("additionalInfoRequestId"));
		hBox.getChildren().add(label);
		TextField textField = new TextField();
		textField.setId("additionalInfoRequestId");
		textField.getStyleClass().add(TEXTFIELD_CLASS);
		hBox.getChildren().add(textField);

		textField.textProperty().addListener((observable, oldValue, newValue) -> {
			getRegistrationDTOFromSession().setAdditionalInfoReqId(newValue);
			getRegistrationDTOFromSession().setAppId(newValue.split("-")[0]);
			TabPane tabPane = (TabPane) anchorPane.lookup(HASH+getRegistrationDTOFromSession().getRegistrationId());
			tabPane.setId(getRegistrationDTOFromSession().getAppId());
			getRegistrationDTOFromSession().setRegistrationId(getRegistrationDTOFromSession().getAppId());
		});

		return hBox;
	}

	private boolean isAdditionalInfoRequestIdProvided(UiScreenDTO screenDTO) {
		Node node =  anchorPane.lookup("#additionalInfoRequestId");
		if(node == null) {
			LOGGER.debug("#additionalInfoRequestId component is not created!");
			return true; //as the element is not present, it's either not required / enabled.
		}

		TextField textField = (TextField) node;
		boolean provided = (textField.getText() != null && !textField.getText().isBlank());

		if(screenDTO.getOrder() < additionalInfoReqIdScreenOrder)
			return true; //bypass check as current screen order is less than the screen it is displayed in.

		if (!provided) {
			showHideErrorNotification(ApplicationContext.getBundle(ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES)
					.getString(RegistrationUIConstants.ADDITIONAL_INFO_REQ_ID_MISSING));
		}
		return provided;
	}

	private void loadPreRegSync(ResponseDTO responseDTO) throws RegBaseCheckedException{
		auditFactory.audit(AuditEvent.REG_DEMO_PRE_REG_DATA_FETCH, Components.REG_DEMO_DETAILS, SessionContext.userId(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		SuccessResponseDTO successResponseDTO = responseDTO.getSuccessResponseDTO();
		List<ErrorResponseDTO> errorResponseDTOList = responseDTO.getErrorResponseDTOs();

		if (errorResponseDTOList != null && !errorResponseDTOList.isEmpty() ||
				successResponseDTO==null ||
				successResponseDTO.getOtherAttributes() == null ||
				!successResponseDTO.getOtherAttributes().containsKey(RegistrationConstants.REGISTRATION_DTO)) {
			throw new RegBaseCheckedException(RegistrationExceptionConstants.PRE_REG_SYNC_FAIL.getErrorCode(),
					RegistrationExceptionConstants.PRE_REG_SYNC_FAIL.getErrorMessage());
		}

		for (UiScreenDTO screenDTO : orderedScreens.values()) {
			for (UiFieldDTO field : screenDTO.getFields()) {
				FxControl fxControl = getFxControl(field.getId());
				if (fxControl != null) {
					switch (fxControl.getUiSchemaDTO().getType()) {
						case "biometricsType":
							break;
						case "documentType":
							fxControl.selectAndSet(getRegistrationDTOFromSession().getDocuments().get(field.getId()));
							break;
						default:
							fxControl.selectAndSet(getRegistrationDTOFromSession().getDemographics().get(field.getId()));
							//it will read data from field components and set it in registrationDTO along with selectedCodes and ageGroups
							//kind of supporting data
							fxControl.setData(getRegistrationDTOFromSession().getDemographics().get(field.getId()));
							break;
					}
				}
			}
		}
		// Handle NRC concatenation after loading data
		handleNrcConcatenation("nrc");
		handleNrcConcatenation("father");
		handleNrcConcatenation("mother");
	}


	private void getScreens(List<UiScreenDTO> screenDTOS) {
		screenDTOS.forEach( dto -> {
			orderedScreens.put(dto.getOrder(), dto);
		});
	}

	private Map<String, List<UiFieldDTO>> getFieldsBasedOnAlignmentGroup(List<UiFieldDTO> screenFields) {
		Map<String, List<UiFieldDTO>> groupedScreenFields = new LinkedHashMap<>();
		if(screenFields == null || screenFields.isEmpty())
			return groupedScreenFields;

		//Applies only during Update flow
		if(getRegistrationDTOFromSession().getUpdatableFieldGroups() != null) {
			screenFields = screenFields.stream()
					.filter(f -> f.getGroup() != null && (getRegistrationDTOFromSession().getUpdatableFieldGroups().contains(f.getGroup()) ||
							getRegistrationDTOFromSession().getDefaultUpdatableFieldGroups().contains(f.getGroup())) )
					.collect(Collectors.toList());
			screenFields.forEach(f -> { getRegistrationDTOFromSession().getUpdatableFields().add(f.getId()); });
		}

		screenFields.forEach( field -> {
			String alignmentGroup = field.getAlignmentGroup() == null ? field.getId()+"TemplateGroup"
					: field.getAlignmentGroup();

			if(field.isInputRequired()) {
				if(!groupedScreenFields.containsKey(alignmentGroup))
					groupedScreenFields.put(alignmentGroup, new LinkedList<UiFieldDTO>());

				groupedScreenFields.get(alignmentGroup).add(field);
			}
		});
		return groupedScreenFields;
	}

	private GridPane getScreenGridPane(String screenName) {
		GridPane gridPane = new GridPane();
		gridPane.setId(screenName);
		RowConstraints topRowConstraints = new RowConstraints();
		topRowConstraints.setPercentHeight(2);
		RowConstraints midRowConstraints = new RowConstraints();
		midRowConstraints.setPercentHeight(96);
		RowConstraints bottomRowConstraints = new RowConstraints();
		bottomRowConstraints.setPercentHeight(2);
		gridPane.getRowConstraints().addAll(topRowConstraints,midRowConstraints, bottomRowConstraints);

		ColumnConstraints columnConstraint1 = new ColumnConstraints();
		columnConstraint1.setPercentWidth(5);
		ColumnConstraints columnConstraint2 = new ColumnConstraints();
		columnConstraint2.setPercentWidth(90);
		ColumnConstraints columnConstraint3 = new ColumnConstraints();
		columnConstraint3.setPercentWidth(5);

		gridPane.getColumnConstraints().addAll(columnConstraint1, columnConstraint2,
				columnConstraint3);

		return gridPane;
	}

	private GridPane getScreenGroupGridPane(String id, GridPane screenGridPane) {
		GridPane groupGridPane = new GridPane();
		groupGridPane.setId(id);
		groupGridPane.prefWidthProperty().bind(screenGridPane.widthProperty());
		groupGridPane.getColumnConstraints().clear();
		ColumnConstraints columnConstraint = new ColumnConstraints();
		columnConstraint.setPercentWidth(100);
		groupGridPane.getColumnConstraints().add(columnConstraint);
		groupGridPane.setHgap(20);
		groupGridPane.setVgap(20);
		return groupGridPane;
	}

	private void addNavigationButtons(ProcessSpecDto processSpecDto) {

		Label navigationLabel = new Label();
		navigationLabel.getStyleClass().add(NAV_LABEL_CLASS);
		navigationLabel.setText(addZwnjIfMyanmar(processSpecDto.getLabel().get(ApplicationContext.applicationLanguage())));
		navigationLabel.prefWidthProperty().bind(navigationAnchorPane.widthProperty());
		navigationLabel.setWrapText(true);

		navigationAnchorPane.getChildren().add(navigationLabel);
		AnchorPane.setTopAnchor(navigationLabel, 5.0);
		AnchorPane.setLeftAnchor(navigationLabel, 10.0);

		next.setOnAction(getNextActionHandler());
		authenticate.setOnAction(getRegistrationAuthActionHandler());
	}

	/*private String getScreenName(Tab tab) {
		return tab.getId().replace("_tab", EMPTY);
	}*/

	private boolean refreshScreenVisibility(String screenName) {
		boolean atLeastOneVisible = true;
		Optional<UiScreenDTO> screenDTO = orderedScreens.values()
				.stream()
				.filter(screen -> screen.getName().equals(screenName))
				.findFirst();

		if(screenDTO.isPresent()) {
			LOGGER.info("Refreshing Screen: {}", screenName);
			screenDTO.get().getFields().forEach( field -> {
				FxControl fxControl = getFxControl(field.getId());
				if(fxControl != null)
					fxControl.refresh();
			});

			atLeastOneVisible = screenDTO.get()
					.getFields()
					.stream()
					.anyMatch( field -> getFxControl(field.getId()) != null && getFxControl(field.getId()).getNode().isVisible() );
		}
		LOGGER.info("Screen refreshed, Screen: {} visible : {}", screenName, atLeastOneVisible);
		return atLeastOneVisible;
	}

	private EventHandler getNextActionHandler() {
		return new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				TabPane tabPane = (TabPane) anchorPane.lookup(HASH+getRegistrationDTOFromSession().getRegistrationId());
				int selectedIndex = tabPane.getSelectionModel().getSelectedIndex();
				while(selectedIndex < tabPane.getTabs().size()) {
					selectedIndex++;
					String newScreenName = tabPane.getTabs().get(selectedIndex).getId().replace("_tab", EMPTY);
					tabPane.getTabs().get(selectedIndex).setDisable(!refreshScreenVisibility(newScreenName));
					if(!tabPane.getTabs().get(selectedIndex).isDisabled()) {
						tabPane.getSelectionModel().select(selectedIndex);
						break;
					}
				}
			}
		};
	}

	private EventHandler getRegistrationAuthActionHandler() {
		return new EventHandler<ActionEvent>() {
			@SneakyThrows
			@Override
			public void handle(ActionEvent event) {
				TabPane tabPane = (TabPane) anchorPane.lookup(HASH+getRegistrationDTOFromSession().getRegistrationId());
				String incompleteScreen = getInvalidScreenName(tabPane);

				if(incompleteScreen == null) {
					generateAlert(RegistrationConstants.ERROR, incompleteScreen +" Screen with ERROR !");
					return;
				}
				authenticationController.goToNextPage();
			}
		};
	}

	private void setTabSelectionChangeEventHandler(TabPane tabPane) {

		tabPane.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>(){
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				LOGGER.debug("Old selection : {} New Selection : {}", oldValue, newValue);

				if (isKeyboardVisible() && keyboardStage != null) {
					keyboardStage.close();
				}

				int newSelection = newValue.intValue() < 0 ? 0 : newValue.intValue();
				final String newScreenName = tabPane.getTabs().get(newSelection).getId().replace("_tab", EMPTY);

				//Hide continue button in preview page
				next.setVisible(newScreenName.equals("AUTH") ? false : true);
				authenticate.setVisible(newScreenName.equals("AUTH") ? true : false);

				if(oldValue.intValue() < 0) {
					tabPane.getSelectionModel().selectFirst();
					return;
				}

				//request to load Preview / Auth page, allowed only when no errors are found in visible screens
				if((newScreenName.equals("AUTH") || newScreenName.equals("PREVIEW"))) {
					String invalidScreenName = getInvalidScreenName(tabPane);
					if(invalidScreenName.equals(EMPTY)) {
						notification.setVisible(false);
						loadPreviewOrAuthScreen(tabPane, tabPane.getTabs().get(newValue.intValue()));
						return;
					}
					else {
						tabPane.getSelectionModel().select(oldValue.intValue());
						return;
					}
				}

				//Refresh screen visibility
				tabPane.getTabs().get(newSelection).setDisable(!refreshScreenVisibility(newScreenName));
				boolean isSelectedDisabledTab = tabPane.getTabs().get(newSelection).isDisabled();

				//selecting disabled tab, take no action, stay in the same screen
				if(isSelectedDisabledTab) {
					tabPane.getSelectionModel().select(oldValue.intValue());
					return;
				}

				//traversing back is allowed without a need to validate current / next screen
				if(oldValue.intValue() > newSelection) {
					tabPane.getSelectionModel().select(newValue.intValue());
					return;
				}

				//traversing forward is always one step next
				if(!isScreenValid(tabPane.getTabs().get(oldValue.intValue()).getId())) {
					LOGGER.error("Current screen is not fully valid : {}", oldValue.intValue());
					tabPane.getSelectionModel().select(oldValue.intValue());
					return;
				}

				tabPane.getTabs().get(oldValue.intValue()).getStyleClass().remove(TAB_LABEL_ERROR_CLASS);
				//tabPane.getSelectionModel().select(newSelection);
				tabPane.getSelectionModel().select(getNextSelection(tabPane, oldValue.intValue(), newSelection));
			}
		});
	}

	private int getNextSelection(TabPane tabPane, int oldSelection, int newSelection) {
		if (newSelection-oldSelection <= 1) {
			return newSelection;
		}
		for (int i = oldSelection + 1; i < newSelection; i++) {
			if (!tabPane.getTabs().get(i).isDisabled()) {
				return oldSelection;
			}
		}
		return newSelection;
	}

	private boolean isScreenValid(final String screenName) {
		Optional<UiScreenDTO> result = orderedScreens.values()
				.stream().filter(screen -> screen.getName().equals(screenName.replace("_tab", EMPTY))).findFirst();

		boolean isValid = true;
		if(result.isPresent()) {

			if(!isAdditionalInfoRequestIdProvided(result.get())) {
				showHideErrorNotification(ApplicationContext.getBundle(ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES)
						.getString(RegistrationUIConstants.ADDITIONAL_INFO_REQ_ID_MISSING));
				return false;
			}

			for(UiFieldDTO field : result.get().getFields()) {
				if(getFxControl(field.getId()) != null && !getFxControl(field.getId()).canContinue()) {
					LOGGER.error("Screen validation , fieldId : {} has invalid value", field.getId());
					String label = getFxControl(field.getId()).getUiSchemaDTO().getLabel().getOrDefault(ApplicationContext.applicationLanguage(), field.getId());
					showHideErrorNotification(label);
					isValid = false;
					break;
				}
			}
		}
		if (isValid) {
			showHideErrorNotification(null);
			auditFactory.audit(AuditEvent.REG_NAVIGATION, Components.REGISTRATION_CONTROLLER,
					SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());
		}
		return isValid;
	}

	private void showHideErrorNotification(String fieldName) {
		Tooltip toolTip = new Tooltip(fieldName);
		toolTip.prefWidthProperty().bind(notification.widthProperty());
		toolTip.setWrapText(true);
		notification.setTooltip(toolTip);
		notification.setText((fieldName == null) ? EMPTY : ApplicationContext.getBundle(ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES)
				.getString("SCREEN_VALIDATION_ERROR") + " [ " + fieldName + " ]");
	}

	private String getInvalidScreenName(TabPane tabPane) {
		String errorScreen = EMPTY;
		for(UiScreenDTO screen : orderedScreens.values()) {
			LOGGER.error("Started to validate screen : {} ", screen.getName());

			if(!isAdditionalInfoRequestIdProvided(screen)) {
				LOGGER.error("Screen validation failed {}, Additional Info request Id is required", screen.getName());
				errorScreen = screen.getName();
				break;
			}

			boolean anyInvalidField = screen.getFields()
					.stream()
					.anyMatch( field -> getFxControl(field.getId()) != null &&
							getFxControl(field.getId()).canContinue() == false );

			Optional<Tab> result = tabPane.getTabs().stream()
					.filter(t -> t.getId().equalsIgnoreCase(screen.getName()+"_tab"))
					.findFirst();
			if (anyInvalidField && result.isPresent()) {
				LOGGER.error("Screen validation failed {}", screen.getName());
				errorScreen = screen.getName();
				result.get().getStyleClass().add(TAB_LABEL_ERROR_CLASS);
				break;
			}
			else if (result.isPresent())
				result.get().getStyleClass().remove(TAB_LABEL_ERROR_CLASS);
		}
		return errorScreen;
	}

	private TabPane createTabPane(ProcessSpecDto processSpecDto) {
		TabPane tabPane = new TabPane();
		tabPane.setId(getRegistrationDTOFromSession().getRegistrationId());
		tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabPane.prefWidthProperty().bind(anchorPane.widthProperty());
		tabPane.prefHeightProperty().bind(anchorPane.heightProperty());

		setTabSelectionChangeEventHandler(tabPane);
		anchorPane.getChildren().add(tabPane);
		addNavigationButtons(processSpecDto);
		return tabPane;
	}

	public void populateScreens() throws Exception {
		RegistrationDTO registrationDTO = getRegistrationDTOFromSession();
		LOGGER.debug("Populating Dynamic screens for process : {}", registrationDTO.getProcessId());
		initialize(registrationDTO);
		this.processSpecDto = getProcessSpec(registrationDTO.getProcessId(), registrationDTO.getIdSchemaVersion());
		getScreens(processSpecDto.getScreens());
		TabPane tabPane = createTabPane(processSpecDto);

		for(UiScreenDTO screenDTO : orderedScreens.values()) {
			Map<String, List<UiFieldDTO>> screenFieldGroups = getFieldsBasedOnAlignmentGroup(screenDTO.getFields());

			List<String> labels = new ArrayList<>();
			getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().forEach(langCode -> {
				labels.add(screenDTO.getLabel().get(langCode));
			});

			String tabNameInApplicationLanguage = screenDTO.getLabel().get(getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0));

			if(screenFieldGroups == null || screenFieldGroups.isEmpty())
				continue;

			Tab screenTab = new Tab();
			screenTab.setId(screenDTO.getName()+"_tab");
			screenTab.setText(
					addZwnjIfMyanmar(
							tabNameInApplicationLanguage == null ?
									labels.get(0) : tabNameInApplicationLanguage
					)
			);
			screenTab.setTooltip(
					new Tooltip(
							addZwnjIfMyanmar(
									String.join(RegistrationConstants.SLASH, labels)
							)
					)
			);
			GridPane screenGridPane = getScreenGridPane(screenDTO.getName());
			screenGridPane.prefWidthProperty().bind(tabPane.widthProperty());
			screenGridPane.prefHeightProperty().bind(tabPane.heightProperty());

			int rowIndex = 0;
			GridPane gridPane = getScreenGroupGridPane(screenGridPane.getId()+"_col_1", screenGridPane);

			if(screenDTO.isPreRegFetchRequired()) {
				gridPane.add(getPreRegistrationFetchComponent(), 0, rowIndex++);
			}
			if(screenDTO.isAdditionalInfoRequestIdRequired()) {
				additionalInfoReqIdScreenOrder = screenDTO.getOrder();
				gridPane.add(getAdditionalInfoRequestIdComponent(), 0, rowIndex++);
			}

			for(Entry<String, List<UiFieldDTO>> groupEntry : screenFieldGroups.entrySet()) {
				FlowPane groupFlowPane = new FlowPane();
				groupFlowPane.prefWidthProperty().bind(gridPane.widthProperty());
				groupFlowPane.setHgap(20);
				groupFlowPane.setVgap(20);

				for(UiFieldDTO fieldDTO : groupEntry.getValue()) {
					try {
						FxControl fxControl = buildFxElement(fieldDTO);
						if(fxControl.getNode() instanceof GridPane) {
							((GridPane)fxControl.getNode()).prefWidthProperty().bind(groupFlowPane.widthProperty());
						}
						groupFlowPane.getChildren().add(fxControl.getNode());
					} catch (Exception exception){
						LOGGER.error("Failed to build control " + fieldDTO.getId(), exception);
					}
				}
				gridPane.add(groupFlowPane, 0, rowIndex++);
			}

			screenGridPane.setStyle("-fx-background-color: white;");
			screenGridPane.add(gridPane, 1, 1);
			final ScrollPane scrollPane = new ScrollPane(screenGridPane);
			scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
			scrollPane.setId("scrollPane");
			screenTab.setContent(scrollPane);
			tabPane.getTabs().add(screenTab);
		}

		//refresh to reflect the initial visibility configuration
		refreshFields();

		// DIAGNOSTIC: Log all registered controls
		LOGGER.info("========================================");
		LOGGER.info("REGISTERED FX CONTROLS:");
		for (String key : fxControlMap.keySet()) {
			LOGGER.info("  - {}", key);
		}
		LOGGER.info("Total controls: {}", fxControlMap.size());
		LOGGER.info("========================================");

		// Now add listeners
		addNrcFieldListeners();
		Platform.runLater(() -> populateSearchDropdownsFromDemographicFields());

		addPreviewAndAuthScreen(tabPane);
	}

	private void addPreviewAndAuthScreen(TabPane tabPane) throws Exception {
		List<String> previewLabels = new ArrayList<>();
		List<String> authLabels = new ArrayList<>();
		for (String langCode : getRegistrationDTOFromSession().getSelectedLanguagesByApplicant()) {
			previewLabels.add(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
					.getString(RegistrationConstants.previewHeader));
			authLabels.add(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
					.getString(RegistrationConstants.authentication));
		}

		String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

		Tab previewScreen = new Tab();
		previewScreen.setId("PREVIEW");
		previewScreen.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString(RegistrationConstants.previewHeader));
		previewScreen.setTooltip(new Tooltip(String.join(RegistrationConstants.SLASH, previewLabels)));
		tabPane.getTabs().add(previewScreen);

		Tab authScreen = new Tab();
		authScreen.setId("AUTH");
		authScreen.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString(RegistrationConstants.authentication));
		authScreen.setTooltip(new Tooltip(String.join(RegistrationConstants.SLASH, authLabels)));
		tabPane.getTabs().add(authScreen);
	}

	private void loadPreviewOrAuthScreen(TabPane tabPane, Tab tab) {
		switch (tab.getId()) {
			case "PREVIEW":
				try {
					tabPane.getSelectionModel().select(tab);
					tab.setContent(getPreviewContent(tabPane));
				} catch (Exception exception) {
					LOGGER.error("Failed to load preview page!!, clearing registration data.");
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_PREVIEW_PAGE));
				}
				break;

			case "AUTH":
				try {
					tabPane.getSelectionModel().select(tab);
					tab.setContent(loadAuthenticationPage(tabPane));
					authenticationController.initData(ProcessNames.PACKET.getType());
				} catch (Exception exception) {
					LOGGER.error("Failed to load auth page!!, clearing registration data.");
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_APPROVAL_PAGE));
				}
				break;
		}
	}

	private Node getPreviewContent(TabPane tabPane) throws Exception {
		String content = registrationPreviewController.getPreviewContent();
		if(content != null) {
			final WebView webView = new WebView();
			webView.setId("webView");
			webView.prefWidthProperty().bind(tabPane.widthProperty());
			webView.prefHeightProperty().bind(tabPane.heightProperty());
			webView.getEngine().loadContent(content);
			final GridPane gridPane = new GridPane();
			gridPane.prefWidthProperty().bind(tabPane.widthProperty());
			gridPane.prefHeightProperty().bind(tabPane.heightProperty());
			gridPane.setAlignment(Pos.TOP_LEFT);
			gridPane.getChildren().add(webView);
			return gridPane;
		}
		throw new RegBaseCheckedException("", "Failed to load preview screen");
	}

	private Node loadAuthenticationPage(TabPane tabPane) throws Exception {
		GridPane gridPane = (GridPane)BaseController.load(getClass().getResource(REG_AUTH_PAGE));
		gridPane.prefWidthProperty().bind(tabPane.widthProperty());
		gridPane.prefHeightProperty().bind(tabPane.heightProperty());

		Node node = gridPane.lookup("#backButton");
		if(node != null) {
			node.setVisible(false);
			node.setDisable(true);
		}

		node = gridPane.lookup("#operatorAuthContinue");
		if(node != null) {
			node.setVisible(false);
			node.setDisable(true);
		}
		return gridPane;
	}


	private FxControl buildFxElement(UiFieldDTO uiFieldDTO) throws Exception {
		LOGGER.info("Building fxControl for field : {}", uiFieldDTO.getId());

		FxControl fxControl = null;
		if (uiFieldDTO.getControlType() != null) {
			switch (uiFieldDTO.getControlType()) {
				case CONTROLTYPE_TEXTFIELD:
					fxControl = new TextFieldFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_BIOMETRICS:
					fxControl = new BiometricFxControl(/*getProofOfExceptionFields()*/).build(uiFieldDTO);
					break;

				case CONTROLTYPE_BUTTON:
					fxControl =  new ButtonFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_CHECKBOX:
					fxControl = new CheckBoxFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DOB:
					fxControl =  new DOBFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DOB_AGE:
					fxControl =  new DOBAgeFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DOCUMENTS:
					fxControl =  new DocumentFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DROPDOWN:
					fxControl = new DropDownFxControl().build(uiFieldDTO);
					break;
				case "nrcConcat": // NRC result field
					LOGGER.info("Creating nrcConcat field: {}", uiFieldDTO.getId());
					fxControl = new TextFieldFxControl().build(uiFieldDTO);
					if (fxControl != null && fxControl.getNode() != null) {
						LOGGER.info("Making {} read-only", uiFieldDTO.getId());
						makeTextFieldReadOnly(fxControl.getNode());
					}
					break;
				case CONTROLTYPE_HTML:
					fxControl = new HtmlFxControl().build(uiFieldDTO);
					break;
			}
		}

		if(fxControl == null) {
			throw new Exception("Failed to build fxControl for field: " + uiFieldDTO.getId()
					+ " with controlType: " + uiFieldDTO.getControlType());
		}
		fxControlMap.put(uiFieldDTO.getId(), fxControl);
		LOGGER.info("Successfully built and added fxControl for: {}", uiFieldDTO.getId());
		return fxControl;
	}
	private void makeTextFieldReadOnly(Node node) {
		if (node instanceof TextField) {
			TextField tf = (TextField) node;
			tf.setEditable(false);
			tf.setStyle("-fx-background-color: #f0f0f0;");
			LOGGER.debug("Made TextField read-only: {}", tf.getId());
		} else if (node instanceof Parent) {
			for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
				makeTextFieldReadOnly(child);
			}
		}
	}
	public void refreshFields() {
		orderedScreens.values().forEach(screen -> { refreshScreenVisibility(screen.getName()); });
	}

	/*public List<UiFieldDTO> getProofOfExceptionFields() {
		return fields.stream().filter(field ->
				field.getSubType().contains(RegistrationConstants.POE_DOCUMENT)).collect(Collectors.toList());
	}*/

	private FxControl getFxControl(String fieldId) {
		return GenericController.getFxControlMap().get(fieldId);
	}
	private <T extends Node> T findNode(Node root, Class<T> type) {
		if (root == null) return null;
		if (type.isInstance(root)) return type.cast(root);
		if (root instanceof Parent) {
			for (Node child : ((Parent) root).getChildrenUnmodifiable()) {
				T found = findNode(child, type);
				if (found != null) return found;
			}
		}
		return null;
	}

	private void addNrcFieldListeners() {
		LOGGER.debug("========================================");
		LOGGER.debug("SETTING UP NRC LISTENERS");
		LOGGER.debug("========================================");

		String[] prefixes = {"nrc", "father", "mother"};

		for (String prefix : prefixes) {
			LOGGER.debug("Setting up listeners for prefix: {}", prefix);

			// Dropdown fields
			String nrcCodeField = prefix.equals("nrc") ? "nrcCode" : prefix + "NrcCode";
			String cityCodeField = prefix.equals("nrc") ? "cityCode" : prefix + "CityCode";
			String residenceStatusField = prefix.equals("nrc") ? "residenceStatus" : prefix + "ResidenceStatus";

			addDropdownListener(prefix, nrcCodeField);
			addDropdownListener(prefix, cityCodeField);
			addDropdownListener(prefix, residenceStatusField);

			// Number part field (INPUT only, NOT result)
			String numberPartFieldId = prefix.equals("nrc") ? "nrcNumberPart" : prefix + "NrcNumberPart";
			FxControl numberPartControl = getFxControl(numberPartFieldId);

			if (numberPartControl != null) {
				LOGGER.debug("Found control for: {}", numberPartFieldId);
				findAllTextFieldsAndAttachListener(numberPartControl.getNode(), prefix, numberPartFieldId);
			} else {
				LOGGER.warn("Control not found for: {}", numberPartFieldId);
			}
		}

		LOGGER.debug("Triggering initial NRC calculations...");
		Platform.runLater(() -> {
			handleNrcConcatenation("nrc");
			handleNrcConcatenation("father");
			handleNrcConcatenation("mother");
		});
	}

	private void findAllTextFieldsAndAttachListener(Node node, String prefix, String expectedFieldId) {
		if (node instanceof TextField) {
			TextField tf = (TextField) node;
			String tfId = tf.getId();

			// CRITICAL: Only attach if this is actually the INPUT field, not the result
			if (tfId != null && tfId.startsWith(expectedFieldId)) {
				LOGGER.debug("Attaching listener to TextField: {}", tfId);
				tf.textProperty().addListener((obs, oldVal, newVal) -> {
					LOGGER.debug("TextField {} changed: '{}' -> '{}'", tfId, oldVal, newVal);
					Platform.runLater(() -> handleNrcConcatenation(prefix));
				});
			}
		} else if (node instanceof Parent) {
			for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
				findAllTextFieldsAndAttachListener(child, prefix, expectedFieldId);
			}
		}
	}

	private void addDropdownListener(String prefix, String fieldId) {
		FxControl control = getFxControl(fieldId);
		if (control == null) {
			LOGGER.warn("Control not found for dropdown: {}", fieldId);
			return;
		}

		ComboBox<?> comboBox = findNode(control.getNode(), ComboBox.class);
		if (comboBox != null) {
			LOGGER.debug("Attaching listener to ComboBox: {}", fieldId);
			comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
				LOGGER.debug("ComboBox {} changed: {} -> {}", fieldId, oldVal, newVal);
				Platform.runLater(() -> handleNrcConcatenation(prefix));
			});
		} else {
			LOGGER.error("Could not find ComboBox for field: {}", fieldId);
		}
	}


	/**
	 * Handles NRC concatenation for applicant, father, and mother
	 */
	public void handleNrcConcatenation(String fieldPrefix) {
		try {
			LOGGER.error("====== NRC CONCATENATION START: {} ======", fieldPrefix);

			// Define field IDs
			String nrcCodeField = fieldPrefix.equals("nrc") ? "nrcCode" : fieldPrefix + "NrcCode";
			String cityCodeField = fieldPrefix.equals("nrc") ? "cityCode" : fieldPrefix + "CityCode";
			String residenceStatusField = fieldPrefix.equals("nrc") ? "residenceStatus" : fieldPrefix + "ResidenceStatus";
			String nrcNumberPartField = fieldPrefix.equals("nrc") ? "nrcNumberPart" : fieldPrefix + "NrcNumberPart";
			String nrcNumberResultField = fieldPrefix.equals("nrc") ? "nrcNumber" : fieldPrefix + "NrcNumber";

			List<String> languages = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant();
			LOGGER.error("Selected Languages: {}", languages);

			// Get codes (language-independent)
			String nrcCodeVal = getSelectedCodeValue(nrcCodeField);
			String cityCodeVal = getSelectedCodeValue(cityCodeField);
			String resStatusVal = getSelectedCodeValue(residenceStatusField);

			LOGGER.error("RAW CODES - NRC: '{}', City: '{}', Status: '{}'", nrcCodeVal, cityCodeVal, resStatusVal);

			// Clear if missing required values
			if (nrcCodeVal == null || cityCodeVal == null) {
				LOGGER.error("Missing codes - CLEARING");
				for (String langCode : languages) {
					setFieldValue(nrcNumberResultField, langCode, "");
				}
				return;
			}

			// Process EACH language independently
			for (String langCode : languages) {
				LOGGER.error(">>>>>> PROCESSING LANGUAGE: {} <<<<<<", langCode);

				// Get language-specific labels from dropdowns with detailed logging
				LOGGER.error("  Getting NRC label for code '{}' in lang '{}'...", nrcCodeVal, langCode);
				String nrcLabel = getDropdownLabelForCode(nrcCodeField, nrcCodeVal, langCode);
				LOGGER.error("  -> NRC Label: '{}'", nrcLabel);

				LOGGER.error("  Getting City label for code '{}' in lang '{}'...", cityCodeVal, langCode);
				String cityLabel = getDropdownLabelForCode(cityCodeField, cityCodeVal, langCode);
				LOGGER.error("  -> City Label: '{}'", cityLabel);

				LOGGER.error("  Getting Status for code '{}' in lang '{}'...", resStatusVal, langCode);
				String resStatusDisplay = getResidenceStatusForLanguage(resStatusVal, langCode);
				LOGGER.error("  -> Status: '{}'", resStatusDisplay);

				// Get digits
				LOGGER.error("  Getting digits for lang '{}'...", langCode);
				String digits = getFieldValue(nrcNumberPartField, langCode);
				LOGGER.error("  -> Digits from '{}': '{}'", langCode, digits);

				if (digits == null || digits.trim().isEmpty()) {
					for (String otherLang : languages) {
						if (!otherLang.equals(langCode)) {
							digits = getFieldValue(nrcNumberPartField, otherLang);
							if (digits != null && !digits.trim().isEmpty()) {
								LOGGER.error("  -> Got digits from OTHER lang '{}': '{}'", otherLang, digits);
								break;
							}
						}
					}
				}

				// Transliterate digits
				if (digits != null && !digits.trim().isEmpty()) {
					LOGGER.error("  Transliterating digits: '{}'", digits);
					String beforeNormalize = digits;
					digits = toEnglishDigits(digits);
					LOGGER.error("  -> After normalize to English: '{}'", digits);

					if (langCode.equals("bur")) {
						digits = toBurmeseDigits(digits);
						LOGGER.error("  -> After convert to Burmese: '{}'", digits);
					}
				}

				// Build result
				StringBuilder result = new StringBuilder();

				if (nrcLabel != null && !nrcLabel.isEmpty()) {
					result.append(nrcLabel);
				}

				if (cityLabel != null && !cityLabel.isEmpty()) {
					// Check if the current result (nrcLabel) already ends with a slash
					// Only add a slash separator if it is missing
					if (result.length() > 0 && result.charAt(result.length() - 1) != '/') {
						result.append("/");
					}
					result.append(cityLabel);
				}

				if (resStatusDisplay != null && !resStatusDisplay.isEmpty()) {
					result.append(resStatusDisplay);
				}

				if (digits != null && !digits.isEmpty()) {
					result.append(digits);
				}
				String finalResult = result.toString();
				LOGGER.error("  =====> FINAL RESULT for '{}': '{}' <=====", langCode, finalResult);

				// Check what script the result contains
				boolean hasEnglish = finalResult.matches(".*[A-Za-z0-9]+.*");
				boolean hasBurmese = containsBurmese(finalResult);
				LOGGER.error("  Result analysis - hasEnglish: {}, hasBurmese: {}", hasEnglish, hasBurmese);

				// Set the result
				setFieldValue(nrcNumberResultField, langCode, finalResult);
			}

			LOGGER.error("====== NRC CONCATENATION END ======");
		} catch (Exception e) {
			LOGGER.error("ERROR in NRC concatenation: ", e);
		}
	}


	private String getDropdownLabelForCode(String fieldId, String code, String langCode) {
		LOGGER.error("    getDropdownLabel - field: {}, code: {}, lang: {}", fieldId, code, langCode);

		if (code == null || code.isEmpty()) {
			LOGGER.error("    -> Code is empty, returning empty string");
			return "";
		}

		FxControl fxControl = getFxControl(fieldId);
		if (!(fxControl instanceof DropDownFxControl)) {
			LOGGER.error("    -> Not a dropdown, returning code: {}", code);
			return code;
		}

		DropDownFxControl dropdown = (DropDownFxControl) fxControl;
		List<GenericDto> options = dropdown.getPossibleValues(langCode);

		LOGGER.error("    -> Got {} options for lang '{}'", options != null ? options.size() : 0, langCode);

		if (options != null && !options.isEmpty()) {
			// Log first few options to see what we're working with
			int logCount = Math.min(3, options.size());
			for (int i = 0; i < logCount; i++) {
				GenericDto opt = options.get(i);
				LOGGER.error("       Option {}: code='{}', name='{}'", i, opt.getCode(), opt.getName());
			}

			for (GenericDto option : options) {
				if (option.getCode().equals(code)) {
					String label = option.getName();
					boolean labelIsBurmese = containsBurmese(label);
					LOGGER.error("    -> FOUND MATCH! Label: '{}' (isBurmese: {})", label, labelIsBurmese);
					return label;
				}
			}
		}

		LOGGER.error("    -> NO MATCH FOUND, returning code: {}", code);
		return code;
	}


	private String getResidenceStatusForLanguage(String code, String langCode) {
		if (code == null || code.trim().isEmpty()) return "";

		// English mappings
		Map<String, String> toEnglish = new HashMap<>();
		toEnglish.put("(နိုင်)", "(C)");
		toEnglish.put("(ပြု)", "(N)");
		toEnglish.put("(ဧည့်)", "(A)");
		toEnglish.put("နိုင်", "(C)");
		toEnglish.put("ပြု", "(N)");
		toEnglish.put("ဧည့်", "(A)");
		toEnglish.put("C", "(C)");
		toEnglish.put("N", "(N)");
		toEnglish.put("A", "(A)");

		// Burmese mappings
		Map<String, String> toBurmese = new HashMap<>();
		toBurmese.put("(C)", "(နိုင်)");
		toBurmese.put("(N)", "(ပြု)");
		toBurmese.put("(A)", "(ဧည့်)");
		toBurmese.put("C", "(နိုင်)");
		toBurmese.put("N", "(ပြု)");
		toBurmese.put("A", "(ဧည့်)");

		boolean isBurmese = langCode.equals("bur");

		if (isBurmese) {
			return toBurmese.getOrDefault(code, code);
		} else {
			return toEnglish.getOrDefault(code, code);
		}
	}

	private String getSelectedCodeValue(String fieldId) {
		FxControl fxControl = getFxControl(fieldId);
		if (fxControl != null) {
			ComboBox<?> comboBox = findNode(fxControl.getNode(), ComboBox.class);
			if (comboBox != null && comboBox.getValue() != null) {
				if (comboBox.getValue() instanceof GenericDto) {
					String code = ((GenericDto) comboBox.getValue()).getCode();
					LOGGER.debug("Got code from ComboBox {}: '{}'", fieldId, code);
					return code;
				}
			}
		}

		// Fallback to DTO
		if (getRegistrationDTOFromSession().SELECTED_CODES != null) {
			String code = getRegistrationDTOFromSession().SELECTED_CODES.get(fieldId + "Code");
			if (code == null) {
				code = getRegistrationDTOFromSession().SELECTED_CODES.get(fieldId);
			}
			if (code != null) {
				LOGGER.debug("Got code from DTO {}: '{}'", fieldId, code);
			}
			return code;
		}
		return null;
	}
	private String getFieldValue(String fieldId, String langCode) {
		FxControl fxControl = getFxControl(fieldId);
		if (fxControl == null) return null;

		// Try finding TextField with language suffix
		TextField tf = findTextFieldById(fxControl.getNode(), fieldId + "_" + langCode);

		if (tf == null) {
			tf = findTextFieldById(fxControl.getNode(), fieldId);
		}

		if (tf == null && fxControl.getNode() instanceof TextField) {
			tf = (TextField) fxControl.getNode();
		}

		if (tf != null) {
			String value = tf.getText();
			LOGGER.debug("Got value from UI {}: '{}'", fieldId, value);
			return value;
		}

		// Fallback to DTO
		Object data = fxControl.getData();
		if (data instanceof List) {
			for (Object item : (List<?>) data) {
				if (item instanceof SimpleDto && ((SimpleDto) item).getLanguage().equals(langCode)) {
					return ((SimpleDto) item).getValue();
				}
			}
		} else if (data instanceof String) {
			return (String) data;
		}

		return null;
	}

	private TextField findTextFieldById(Node root, String targetId) {
		if (root == null || targetId == null) return null;

		if (root instanceof TextField) {
			String id = root.getId();
			if (id != null && id.equals(targetId)) {
				LOGGER.debug("Found TextField with matching ID: {}", targetId);
				return (TextField) root;
			}
		}

		if (root instanceof Parent) {
			for (Node child : ((Parent) root).getChildrenUnmodifiable()) {
				TextField found = findTextFieldById(child, targetId);
				if (found != null) return found;
			}
		}
		return null;
	}

	/**
	 * Get selected code from dropdown
	 */


	private boolean containsBurmese(String input) {
		return input != null && input.chars().anyMatch(c -> c >= 0x1000 && c <= 0x109F);
	}


	private String toBurmeseDigits(String input) {
		if (input == null || input.isEmpty()) return "";
		char[] eng = {'0','1','2','3','4','5','6','7','8','9'};
		char[] bur = {'၀','၁','၂','၃','၄','၅','၆','၇','၈','၉'};
		return replaceChars(input, eng, bur);
	}

	private String toEnglishDigits(String input) {
		if (input == null || input.isEmpty()) return "";
		char[] eng = {'0','1','2','3','4','5','6','7','8','9'};
		char[] bur = {'၀','၁','၂','၃','၄','၅','၆','၇','၈','၉'};
		return replaceChars(input, bur, eng);
	}

	private String replaceChars(String input, char[] source, char[] target) {
		char[] chars = input.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			for (int j = 0; j < source.length; j++) {
				if (chars[i] == source[j]) {
					chars[i] = target[j];
					break;
				}
			}
		}
		return new String(chars);
	}

	private void setFieldValue(String fieldId, String langCode, String value) {
		LOGGER.debug("Setting field {} ({}) to '{}'", fieldId, langCode, value);

		FxControl fxControl = getFxControl(fieldId);
		if (fxControl == null) {
			LOGGER.error("FxControl not found: {}", fieldId);
			return;
		}

		Node rootNode = fxControl.getNode();
		TextField tf = null;

		// 1. Identify if this is the Primary Language
		String primaryLang = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);
		boolean isPrimaryLanguage = langCode.equalsIgnoreCase(primaryLang);

		// 2. Try Exact Language ID (e.g., nrcNumber_bur)
		tf = findTextFieldById(rootNode, fieldId + "_" + langCode);

		// 3. Try Alias Language ID (e.g., nrcNumber_my)
		if (tf == null) {
			String alias = getLanguageAlias(langCode);
			if (alias != null) {
				tf = findTextFieldById(rootNode, fieldId + "_" + alias);
			}
		}

		// 4. Fallback Strategies (ONLY allowed for Primary Language)
		if (tf == null && isPrimaryLanguage) {
			LOGGER.debug("Strict match failed for Primary Lang '{}'. Attempting fallbacks...", langCode);

			// 4a. Try Base ID on the Root Node itself
			if (rootNode instanceof TextField && fieldId.equals(rootNode.getId())) {
				tf = (TextField) rootNode;
			}

			// 4b. Try Base ID search in children
			if (tf == null) {
				tf = findTextFieldById(rootNode, fieldId);
			}

			// 4c. RESTORED: Strategy 5 - Find First Available TextField
			// This is what was making English work originally.
			// We only allow this for Primary Language to prevent Burmese from grabbing this field.
			if (tf == null && rootNode instanceof Parent) {
				tf = findFirstTextField((Parent) rootNode);
				if (tf != null) {
					LOGGER.debug("Found field via findFirstTextField strategy (Primary Language)");
				}
			}
		}

		if (tf != null) {
			boolean wasEditable = tf.isEditable();
			tf.setEditable(true);
			tf.setText(value);
			tf.setEditable(wasEditable);
			LOGGER.debug("SUCCESS: Set value for {} in lang {}", fieldId, langCode);
		} else {
			// DIAGNOSTIC LOGGING: If we still can't find the field (especially Burmese),
			// print what IDs actually exist so we can fix the mapping.
			LOGGER.error("FAILED to find TextField for {} in lang {}. Primary: {}", fieldId, langCode, isPrimaryLanguage);
			LOGGER.error("Available IDs in this control:");
			logAvailableIds(rootNode);
		}

		// Update DTO
		updateDataObject(fxControl, langCode, value);
	}

	// Add this helper method to your class to see IDs in the logs
	private void logAvailableIds(Node node) {
		if (node == null) return;
		if (node.getId() != null && !node.getId().isEmpty()) {
			LOGGER.error(" - Found Node ID: '{}' (Type: {})", node.getId(), node.getClass().getSimpleName());
		}
		if (node instanceof Parent) {
			for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
				logAvailableIds(child);
			}
		}
	}

	private String getLanguageAlias(String langCode) {
		if (langCode == null) return null;
		if ("my".equalsIgnoreCase(langCode)) return "bur";
		if ("bur".equalsIgnoreCase(langCode)) return "my";
		if ("eng".equalsIgnoreCase(langCode)) return "en";
		if ("en".equalsIgnoreCase(langCode)) return "eng";
		return null;
	}

	private void updateDataObject(FxControl fxControl, String langCode, String value) {
		Object currentData = fxControl.getData();
		if (currentData instanceof List) {
			List<SimpleDto> list = (List<SimpleDto>) currentData;
			boolean found = false;
			for (SimpleDto dto : list) {
				if (dto.getLanguage().equals(langCode)) {
					dto.setValue(value);
					found = true;
					break;
				}
			}
			if (!found) {
				list.add(new SimpleDto(langCode, value));
			}
			fxControl.setData(list);
		} else {
			fxControl.setData(value);
		}
		LOGGER.debug("Updated DTO for field, lang: {}", langCode);
	}

	private TextField findFirstTextField(Parent parent) {
		if (parent == null) return null;

		LOGGER.debug("Searching for TextField in {} with {} children",
				parent.getClass().getSimpleName(),
				parent.getChildrenUnmodifiable().size());

		for (Node child : parent.getChildrenUnmodifiable()) {
			LOGGER.debug("  Checking child: {} (id: {})",
					child.getClass().getSimpleName(),
					child.getId());

			if (child instanceof TextField) {
				LOGGER.debug("  -> Found TextField!");
				return (TextField) child;
			}

			if (child instanceof Parent) {
				TextField found = findFirstTextField((Parent) child);
				if (found != null) return found;
			}
		}

		return null;
	}


	public Stage getKeyboardStage() {
		return keyboardStage;
	}

	public void setKeyboardStage(Stage keyboardStage) {
		this.keyboardStage = keyboardStage;
	}

	public boolean isKeyboardVisible() {
		return keyboardVisible;
	}

	public void setKeyboardVisible(boolean keyboardVisible) {
		this.keyboardVisible = keyboardVisible;
	}

	public String getPreviousId() {
		return previousId;
	}

	public void setPreviousId(String previousId) {
		this.previousId = previousId;
	}

	public TextField getRegistrationNumberTextField() {
		return registrationNumberTextField;
	}

	public String getCurrentScreenName() {
		TabPane tabPane = (TabPane) anchorPane.lookup(HASH + getRegistrationDTOFromSession().getRegistrationId());
		return tabPane.getSelectionModel().getSelectedItem().getId().replace("_tab", EMPTY);
	}

	/**
	 * Set up hierarchical filtering between NRC Code and City Code dropdowns
	 */
	private void setupNrcHierarchyFiltering() {
		if (nrcCodeComboBox != null && cityCodeComboBox != null) {
			// Store all possible cities for filtering
			final List<String> allCities = new ArrayList<>(cityCodeComboBox.getItems());

			nrcCodeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
				if (newValue == null || newValue.trim().isEmpty()) {
					// If no NRC code selected, show all cities
					cityCodeComboBox.getItems().clear();
					cityCodeComboBox.getItems().addAll(allCities);
					return;
				}

				// Filter cities based on selected NRC code
				cityCodeComboBox.getItems().clear();
				String selectedNrcCode = newValue.trim();

				// Try to get filtered cities from the actual demographic cityCode dropdown
				// by simulating the selection
				filterCitiesByNrcCode(selectedNrcCode, allCities);
			});
		}
	}

	/**
	 * Filter cities based on the selected NRC code using the same logic as demographic fields
	 */
	private void filterCitiesByNrcCode(String selectedNrcCode, List<String> allCities) {
		try {
			FxControl cityCodeControl = getFxControl("cityCode");
			FxControl nrcCodeControl = getFxControl("nrcCode");

			if (cityCodeControl instanceof DropDownFxControl && nrcCodeControl instanceof DropDownFxControl) {
				String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

				// Find the code value for the selected NRC name
				DropDownFxControl nrcDropdown = (DropDownFxControl) nrcCodeControl;
				List<io.mosip.registration.dto.mastersync.GenericDto> nrcOptions = nrcDropdown.getPossibleValues(langCode);

				String selectedNrcCodeValue = null;
				for (io.mosip.registration.dto.mastersync.GenericDto option : nrcOptions) {
					if (option.getName().equals(selectedNrcCode)) {
						selectedNrcCodeValue = option.getCode();
						break;
					}
				}

				if (selectedNrcCodeValue != null) {
					// Try to get filtered cities by simulating the demographic dropdown behavior
					// Instead of manipulating the actual dropdown, we'll use the hierarchical logic
					try {
						// Get the master sync service to get cities for the selected region
						List<io.mosip.registration.dto.mastersync.GenericDto> filteredCities =
								masterSyncService.getFieldValues(selectedNrcCodeValue, langCode, true);

						// Add filtered cities to search dropdown
						for (io.mosip.registration.dto.mastersync.GenericDto city : filteredCities) {
							cityCodeComboBox.getItems().add(city.getName());
						}
					} catch (Exception e) {
						LOGGER.debug("Could not get hierarchical cities, using fallback", e);
						// If hierarchical filtering fails, fall back to region-based cities
						addDefaultCitiesForRegion(selectedNrcCode);
					}

					// If no cities found, use fallback
					if (cityCodeComboBox.getItems().isEmpty()) {
						addDefaultCitiesForRegion(selectedNrcCode);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error filtering cities by NRC code", e);
			// Fallback to showing all cities
			cityCodeComboBox.getItems().clear();
			cityCodeComboBox.getItems().addAll(allCities);
		}
	}

	/**
	 * Map residence status display names to codes expected by getResidenceStatusForLanguage
	 */
	/**
	 * Maps the display name selected in the Search ComboBox back to its Master Data Code.
	 * This ensures the search logic stays in sync with the demographic dropdown data.
	 */
	private String mapDisplayNameToResidenceCode(String displayName) {
		if (displayName == null || displayName.trim().isEmpty()) {
			return "";
		}

		try {
			// 1. Get the demographic "residenceStatus" control which acts as the source of truth
			FxControl residenceStatusControl = getFxControl("residenceStatus");

			if (residenceStatusControl instanceof DropDownFxControl) {
				String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

				// 2. Get the list of GenericDto objects (contains both Code and Name)
				List<io.mosip.registration.dto.mastersync.GenericDto> options =
						((DropDownFxControl) residenceStatusControl).getPossibleValues(langCode);

				if (options != null) {
					for (io.mosip.registration.dto.mastersync.GenericDto option : options) {
						// 3. Match the selected name with the Master Data Name
						if (displayName.equals(option.getName())) {
							LOGGER.debug("Mapped Search Label '{}' to Master Code '{}'", displayName, option.getCode());
							return option.getCode(); // Returns 'C', 'N', 'A', etc.
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error mapping display name to residence code from master data", e);
		}

		// Fallback: If master data lookup fails, try basic manual mapping
		LOGGER.warn("Master data lookup failed for '{}', using fallback mapping", displayName);
		String trimmed = displayName.trim();
		if (trimmed.contains("နိုင်")) return "C";
		if (trimmed.contains("ပြု")) return "N";
		if (trimmed.contains("ဧည့်")) return "A";

		return displayName; // Return as-is if no mapping found
	}
	/**
	 * Add default cities for a region when filtering fails
	 */
	private void addDefaultCitiesForRegion(String nrcCode) {
		// Map NRC codes to their typical cities
		Map<String, List<String>> regionCities = new HashMap<>();
		regionCities.put("1", Arrays.asList("Mandalay", "Meiktila", "Pyin Oo Lwin"));
		regionCities.put("2", Arrays.asList("Yangon", "Bago", "Pathein"));
		regionCities.put("3", Arrays.asList("Naypyidaw", "Pyinmana"));
		regionCities.put("4", Arrays.asList("Mawlamyine", "Thaton", "Hpa-An"));
		regionCities.put("5", Arrays.asList("Taunggyi", "Loikaw", "Taungoo"));
		regionCities.put("6", Arrays.asList("Monywa", "Sagaing", "Shwebo"));
		regionCities.put("7", Arrays.asList("Sittwe", "Kyaukpyu", "Thandwe"));
		regionCities.put("8", Arrays.asList("Magway", "Pakokku", "Chauk"));
		regionCities.put("9", Arrays.asList("Myingyan", "Pakokku", "Natogyi"));
		regionCities.put("10", Arrays.asList("Hakha", "Falam", "Mindat"));
		regionCities.put("11", Arrays.asList("Myitkyina", "Bhamo", "Mohnyin"));
		regionCities.put("12", Arrays.asList("Taunggyi", "Kengtung", "Lashio"));
		regionCities.put("13", Arrays.asList("Dawei", "Myeik", "Kawthaung"));
		regionCities.put("14", Arrays.asList("Naypyidaw", "Ottarathiri"));

		List<String> cities = regionCities.get(nrcCode);
		if (cities != null && !cities.isEmpty()) {
			cityCodeComboBox.getItems().addAll(cities);
		} else {
			// Final fallback - show some cities
			cityCodeComboBox.getItems().addAll("Mandalay", "Yangon", "Naypyidaw", "Mawlamyine");
		}
	}
	/**
	 * Initialize NRC component listeners for auto-constructing NRC number and PRID
	 */
	private void initializeNrcComponents() {
		// Add listeners to auto-construct NRC number and PRID when components change
		if (nrcCodeComboBox != null) {
			nrcCodeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
				handleSearchNrcConcatenation();
				constructPridFromNrc();
			});
		}
		if (cityCodeComboBox != null) {
			cityCodeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
				handleSearchNrcConcatenation();
				constructPridFromNrc();
			});
		}
		if (citizenTypeComboBox != null) {
			citizenTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
				handleSearchNrcConcatenation();
				constructPridFromNrc();
			});
		}
		if (nrcNumberTextField != null) {
			nrcNumberTextField.textProperty().addListener((obs, oldVal, newVal) -> {
				handleSearchNrcConcatenation();
				constructPridFromNrc();
			});
		}
	}

	private void handleSearchNrcConcatenation() {
		try {
			if (nrcNumber == null) return;

			String selectedLang = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

			String nrcCodeDisplay = nrcCodeComboBox != null ? nrcCodeComboBox.getValue() : "";
			String cityCodeDisplay = cityCodeComboBox != null ? cityCodeComboBox.getValue() : "";
			String residenceStatusDisplay = citizenTypeComboBox != null ? citizenTypeComboBox.getValue() : "";
			String nrcDigits = nrcNumberTextField != null ? nrcNumberTextField.getText() : "";

			if (nrcCodeDisplay == null || nrcCodeDisplay.isEmpty() || cityCodeDisplay == null || cityCodeDisplay.isEmpty()) {
				nrcNumber.setText("");
				constructedPridTextField.setText("");
				return;
			}

			// --- START: Master Data Lookup for City Code (e.g., LaPaTa7) ---
			String cityCodeValue = cityCodeDisplay;
			try {
				FxControl nrcControl = getFxControl("nrcCode");
				if (nrcControl instanceof DropDownFxControl) {
					String parentLocCode = "";
					List<GenericDto> nrcOptions = ((DropDownFxControl) nrcControl).getPossibleValues(selectedLang);
					for (GenericDto opt : nrcOptions) {
						if (opt.getName().equals(nrcCodeDisplay)) {
							parentLocCode = opt.getCode();
							break;
						}
					}

					if (parentLocCode != null && !parentLocCode.isEmpty()) {
						List<GenericDto> cityOptions = masterSyncService.getFieldValues(parentLocCode, selectedLang, true);
						for (GenericDto city : cityOptions) {
							if (city.getName().equals(cityCodeDisplay)) {
								cityCodeValue = city.getCode(); // Retrieves "LaPaTa7"
								cityCodeValue = cityCodeValue.replaceAll("\\d+$", "");
								break;
							}
						}
					}
				}
			} catch (Exception e) {
				LOGGER.error("Error fetching city code from master data", e);
			}

			String resCode = mapDisplayNameToResidenceCode(residenceStatusDisplay);
			String resLabel = getResidenceStatusForLanguage(resCode, selectedLang);
			String digits = toEnglishDigits(nrcDigits.trim());
			if ("bur".equals(selectedLang)) {
				digits = toBurmeseDigits(digits);
			}

			// 1. Display Version (User sees this)
			StringBuilder displaySb = new StringBuilder();
			displaySb.append(nrcCodeDisplay.trim());
			if (!nrcCodeDisplay.endsWith("/")) displaySb.append("/");
			displaySb.append(cityCodeDisplay.trim());
			displaySb.append(resLabel);
			displaySb.append(digits);

			// 2. API Version (Hidden logic used for fetch)
			StringBuilder apiSb = new StringBuilder();
			apiSb.append(nrcCodeDisplay.trim());
			if (!nrcCodeDisplay.endsWith("/")) apiSb.append("/");
			apiSb.append(cityCodeValue.trim());
			apiSb.append(resLabel);
			apiSb.append(digits);

			String displayNrc = displaySb.toString();
			String apiPrid = apiSb.toString();

			// Set values to UI components (Both show the display name to the user)
			nrcNumber.setText(displayNrc);
			constructedPridTextField.setText(displayNrc);

			LOGGER.info("Constructed Display NRC for UI: [{}]", displayNrc);
			LOGGER.info("Internal API PRID ready for fetch: [{}]", apiPrid);

		} catch (Exception e) {
			LOGGER.error("Error in search NRC concatenation", e);
		}
	}

	/**
	 * Construct PRID from NRC components in format: nrcCode/cityCode(citizenType)nrcNumber
	 */
	private void constructPridFromNrc() {
		if (constructedPridTextField == null || nrcNumber == null) return;

		String fullNrc = nrcNumber.getText();

		if (fullNrc != null && !fullNrc.trim().isEmpty()) {
			// Use the full concatenated NRC as the PRID for searching
			constructedPridTextField.setText(fullNrc);
		} else {
			constructedPridTextField.setText("");
		}
	}

	/**
	 * Validate NRC components
	 */
	private boolean validateNrcComponents() {
		String nrcCode = nrcCodeComboBox != null ? nrcCodeComboBox.getValue() : "";
		String cityCode = cityCodeComboBox != null ? cityCodeComboBox.getValue() : "";
		String citizenType = citizenTypeComboBox != null ? citizenTypeComboBox.getValue() : "";
		String nrcDigits = nrcNumberTextField != null ? nrcNumberTextField.getText() : "";
		String fullNrc = nrcNumber != null ? nrcNumber.getText() : "";

		if (nrcCode == null || nrcCode.trim().isEmpty()) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "Please select NRC Code");
			return false;
		}
		if (cityCode == null || cityCode.trim().isEmpty()) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "Please select City Code");
			return false;
		}
		if (citizenType == null || citizenType.trim().isEmpty()) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "Please select Citizen Type");
			return false;
		}
		if (nrcDigits == null || nrcDigits.trim().isEmpty()) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "Please enter NRC digits");
			return false;
		}
		if (!nrcDigits.matches("\\d{6}")) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "NRC digits must be 6 digits");
			return false;
		}
		if (fullNrc == null || fullNrc.trim().isEmpty()) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "NRC number could not be constructed");
			return false;
		}

		return true;
	}


	@FXML
	private void fetchNrcData() {
		if (!validateNrcComponents()) {
			return;
		}

		try {
			String selectedLang = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);
			String nrcCodeDisplay = nrcCodeComboBox.getValue();
			String cityCodeDisplay = cityCodeComboBox.getValue();
			String residenceStatusDisplay = citizenTypeComboBox.getValue();
			String nrcDigits = nrcNumberTextField.getText();

//			0. Recalculate the nrcCode (eg ., 14/  -> 14)
			String nrcCodeValue=  nrcCodeDisplay;

			// 1. Recalculate the City Code (e.g., LaPaTa7)
			String cityCodeValue = cityCodeDisplay;
			FxControl nrcControl = getFxControl("nrcCode");
			if (nrcControl instanceof DropDownFxControl) {
				String parentLocCode = "";
				List<GenericDto> nrcOptions = ((DropDownFxControl) nrcControl).getPossibleValues(selectedLang);
				for (GenericDto opt : nrcOptions) {
					if (opt.getName().equals(nrcCodeDisplay)) {
						parentLocCode = opt.getCode();
						nrcCodeValue = parentLocCode;
						break;
					}
				}
				if (parentLocCode != null && !parentLocCode.isEmpty()) {
					List<GenericDto> cityOptions = masterSyncService.getFieldValues(parentLocCode, selectedLang, true);
					for (GenericDto city : cityOptions) {
						if (city.getName().equals(cityCodeDisplay)) {
							cityCodeValue = city.getCode();
							cityCodeValue = cityCodeValue.replaceAll("\\d+$", "");
							break;
						}
					}
				}
			}

			// 2. Recalculate Residence Label and Digits
			String resCode = mapDisplayNameToResidenceCode(residenceStatusDisplay);
			String resLabel = getResidenceStatusForLanguage(resCode, selectedLang);
			String digits = toEnglishDigits(nrcDigits.trim());
			// For API call, we usually send English digits.
			// If your API requires Burmese digits when lang is bur, uncomment below:
			// if ("bur".equals(selectedLang)) { digits = toBurmeseDigits(digits); }

			// 3. Construct the API PRID using the Code
			StringBuilder apiSb = new StringBuilder();
			apiSb.append(nrcCodeValue.trim());
			// if (!nrcCodeDisplay.endsWith("/")) apiSb.append("/");
			apiSb.append(cityCodeValue.trim()); // Uses LaPaTa7
			if (resLabel.equals("(နိုင်)")) resLabel="(C)";
			else if (resLabel.equals("(ပြု)")) resLabel="(N)";
			else if (resLabel.equals("(ဧည့်)")) resLabel="(A)";
			apiSb.append(resLabel);
			apiSb.append(digits);

			String apiPrid = apiSb.toString();

			if (apiPrid.isEmpty()) {
				generateAlertLanguageSpecific(RegistrationConstants.ERROR, "Unable to construct NRC for search");
				return;
			}

			LOGGER.info("Starting NRC fetch using calculated apiPrid (with Master Code): [{}]", apiPrid);

			executeNrcSearchTask(apiPrid);

		} catch (Exception e) {
			LOGGER.error("Error constructing fetch ID", e);
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, "System error constructing Search ID");
		}
	}
	/**
	 * Handle the response from NRC search
	 */
	private void handleNrcSearchResponse(ResponseDTO responseDTO, String apiPrid) {
		try {
			genericScreen.setDisable(false);
			if (progressIndicator != null) progressIndicator.setVisible(false);

			if (responseDTO == null || responseDTO.getErrorResponseDTOs() != null && !responseDTO.getErrorResponseDTOs().isEmpty()) {
				String errorMessage = "Failed to find pre-registration for the given NRC";
				if (responseDTO != null && responseDTO.getErrorResponseDTOs() != null && !responseDTO.getErrorResponseDTOs().isEmpty()) {
					errorMessage = responseDTO.getErrorResponseDTOs().get(0).getMessage();
				}
				generateAlertLanguageSpecific(RegistrationConstants.ERROR, errorMessage);
				return;
			}

			LOGGER.info("Using pre-registration ID (apiPrid): [{}]", apiPrid);

			// If successful, the response should contain the pre-registration data
			handleFetchResponse(responseDTO, apiPrid);

		} catch (Exception exception) {
			LOGGER.error("Error handling NRC search response", exception);
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationConstants.PRE_REG_TO_GET_PACKET_ERROR);
		}
	}

	private void handleFetchResponse(ResponseDTO responseDTO, String prid) {
		if (responseDTO.getErrorResponseDTOs() != null && !responseDTO.getErrorResponseDTOs().isEmpty()) {
			String msg = responseDTO.getErrorResponseDTOs().get(0).getMessage();
			if (RegistrationConstants.CONSUMED_PRID_ERROR_CODE.equalsIgnoreCase(msg)) {
				generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationConstants.PRE_REG_CONSUMED_PACKET_ERROR);
			} else {
				generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationConstants.PRE_REG_TO_GET_PACKET_ERROR);
			}
			return;
		}

		try {
			loadPreRegSync(responseDTO);
            if (responseDTO.getSuccessResponseDTO() != null) {
                String currentTabPaneId = getRegistrationDTOFromSession().getRegistrationId();

                getRegistrationDTOFromSession().setPreRegistrationId(prid);
                getRegistrationDTOFromSession().setAppId(prid);
                getRegistrationDTOFromSession().setRegistrationId(prid);

                TabPane tabPane = (TabPane) anchorPane.lookup(HASH + currentTabPaneId);
                if (tabPane != null) {
                    tabPane.setId(prid);
                } else {
                    LOGGER.warn("TabPane not found using id: {} while updating to {}", currentTabPaneId, prid);
                }
            }
		} catch (RegBaseCheckedException exception) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationConstants.PRE_REG_TO_GET_PACKET_ERROR);
		}
	}

	private void executeNrcSearchTask(String nrc) {
		// Replace nrc
		//String cleanNrc = nrc.replace("/", "");
		String cleanNrc = nrc;
		LOGGER.info("Starting NRC fetch for clean PRID: [{}]", cleanNrc);  // This should show no (C)

		genericScreen.setDisable(true);
		if (progressIndicator != null) progressIndicator.setVisible(true);

		Service<ResponseDTO> searchService = new Service<ResponseDTO>() {
			@Override
			protected Task<ResponseDTO> createTask() {
				return new Task<ResponseDTO>() {
					@Override
					protected ResponseDTO call() throws Exception {
						LOGGER.info("Calling pre-reg sync API with PRID: {}", cleanNrc);
						return preRegistrationDataSyncService.getPreRegistration(cleanNrc, true);
					}
				};
			}
		};

		searchService.setOnSucceeded(event -> {
			handleNrcSearchResponse(searchService.getValue(),cleanNrc);
		});

		searchService.setOnFailed(event -> {
			LOGGER.error("NRC search task failed", searchService.getException());
			handleNrcSearchResponse(null,cleanNrc);
		});

		searchService.start();
	}

	/**
	 * Populate search dropdowns by copying data from actual demographic dropdowns
	 */
	private void populateSearchDropdownsFromDemographicFields() {
		try {
			// Wait a bit for demographic dropdowns to be fully populated
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		Platform.runLater(() -> {
			// Copy NRC Code dropdown data
			if (nrcCodeComboBox != null) {
				nrcCodeComboBox.getItems().clear();
				FxControl nrcCodeControl = getFxControl("nrcCode");
				if (nrcCodeControl instanceof DropDownFxControl) {
					List<io.mosip.registration.dto.mastersync.GenericDto> options = ((DropDownFxControl) nrcCodeControl).getPossibleValues(getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0));
					for (io.mosip.registration.dto.mastersync.GenericDto option : options) {
						nrcCodeComboBox.getItems().add(option.getName());
					}
				}
				// Fallback if no data
				if (nrcCodeComboBox.getItems().isEmpty()) {
					nrcCodeComboBox.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
				}
			}

			// Copy City Code dropdown data (initially all cities)
			if (cityCodeComboBox != null) {
				cityCodeComboBox.getItems().clear();
				FxControl cityCodeControl = getFxControl("cityCode");
				if (cityCodeControl instanceof DropDownFxControl) {
					// Get all possible cities initially (before any nrcCode selection)
					List<io.mosip.registration.dto.mastersync.GenericDto> allCities = ((DropDownFxControl) cityCodeControl).getPossibleValues(getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0));
					for (io.mosip.registration.dto.mastersync.GenericDto option : allCities) {
						cityCodeComboBox.getItems().add(option.getName());
					}
				}
				// Fallback if no data
				if (cityCodeComboBox.getItems().isEmpty()) {
					cityCodeComboBox.getItems().addAll("Mandalay", "Yangon", "Naypyidaw", "Mawlamyine", "Bago", "Pathein", "Monywa", "Sittwe", "Magway", "Sagaing", "Taunggyi", "Myingyan", "Tamana", "Pyay");
				}
			}

			// Copy Citizen Type dropdown data
			if (citizenTypeComboBox != null) {
				citizenTypeComboBox.getItems().clear();
				FxControl residenceStatusControl = getFxControl("residenceStatus");
				if (residenceStatusControl instanceof DropDownFxControl) {
					List<io.mosip.registration.dto.mastersync.GenericDto> options = ((DropDownFxControl) residenceStatusControl).getPossibleValues(getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0));
					LOGGER.debug("Found {} residence status options from demographic dropdown", options.size());
					for (io.mosip.registration.dto.mastersync.GenericDto option : options) {
						citizenTypeComboBox.getItems().add(option.getName());
						LOGGER.debug("Added residence status option: name='{}', code='{}'", option.getName(), option.getCode());
					}
				}
				// Fallback if no data
				if (citizenTypeComboBox.getItems().isEmpty()) {
					LOGGER.debug("No residence status options found, using fallback values");
					citizenTypeComboBox.getItems().addAll("N", "P", "T");
				}
			}
			// Set up hierarchical filtering for NRC Code -> City Code
			setupNrcHierarchyFiltering();
		});
	}
		
	private void executeQRCodeScan() {
		genericScreen.setDisable(true);
		Service<Void> taskService = new Service<Void>() {
			@Override
			protected Task<Void> createTask() {
				return new Task<Void>() {
					@Override
					protected Void call() {
						Platform.runLater(() -> {
							qrCodePopUpViewController.init("Scan QR Code");
						});
						return null;
					}
				};
			}
		};
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				genericScreen.setDisable(false);
			}
		});
		taskService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				LOGGER.debug("QR code scan failed");
				genericScreen.setDisable(false);
			}
		});
	}

	private String addZwnjIfMyanmar(String text) {
		if (text == null) return null;

		// Myanmar Unicode range
		if (text.matches(".*[\\u1000-\\u109F].*")) {
			return text + "\u200C";
		}
		return text;
	}


}







