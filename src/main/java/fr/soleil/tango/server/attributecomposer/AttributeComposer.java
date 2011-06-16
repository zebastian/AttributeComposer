package fr.soleil.tango.server.attributecomposer;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.tango.server.DeviceState;
import org.tango.server.ServerManager;
import org.tango.server.idl4.annotation.Attribute;
import org.tango.server.idl4.annotation.Command;
import org.tango.server.idl4.annotation.Delete;
import org.tango.server.idl4.annotation.Device;
import org.tango.server.idl4.annotation.DeviceProperty;
import org.tango.server.idl4.annotation.DynamicManagement;
import org.tango.server.idl4.annotation.Init;
import org.tango.server.idl4.annotation.State;
import org.tango.server.idl4.annotation.Status;
import org.tango.server.idl4.dynamic.DynamicManager;
import org.tango.utils.DevFailedUtils;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.DeviceProxy;
import fr.esrf.TangoApi.QualityUtilities;
import fr.soleil.tango.attributecomposer.PriorityQualityManager;
import fr.soleil.tango.clientapi.TangoGroupAttribute;

@Device
public class AttributeComposer {

    /*
     * The supported logical gates
     */
    private enum LogicalGateType {
	AND, NONE, OR, XOR
    }

    private enum PropertyType {
	ALARM_MAX, ALARM_MIN, FORMAT, LABEL, MAX_VAL, MIN_VAL, UNIT;
    }

    /**
     * SimpleDateFormat to timeStamp the error messages
     */
    private static final SimpleDateFormat dateInsertformat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private static Logger logger = LoggerFactory.getLogger(AttributeComposer.class);

    private static String versionStatic;

    private final static XLogger xlogger = XLoggerFactory.getXLogger(AttributeComposer.class);

    /**
     * MAIN
     */
    public static void main(final String[] args) {
	final ResourceBundle rb = ResourceBundle.getBundle("fr.soleil.attributecomposer.application");
	versionStatic = rb.getString("project.version");
	try {
	    ServerManager.getInstance().addClass("AttributeComposer", AttributeComposer.class);
	    ServerManager.getInstance().start(args, "AttributeComposer");
	} catch (final DevFailed e) {
	    DevFailedUtils.printDevFailed(e);
	    logger.debug(DevFailedUtils.toString(e));
	}
    }

    /**
     * The table of the attribute name and their associated proxy group
     * <attributeName, Group>
     */
    TangoGroupAttribute attributeGroup;

    /**
     * The attribute names without device name
     */
    private String[] attributeNameArray;

    /**
     * The list of attribute name used to composed the resum state and the
     * spectrum result.
     */
    @DeviceProperty
    private String[] attributeNameList;

    /**
     * The list of the attributes quality in priority number format. Call
     * GetAttributeNameForIndex to know which attributes corresponds to an index
     * of the spectrum. Call GetPriorityForQuality to know the values of tango
     * qualities.
     */
    @Attribute
    private short[] attributesNumberPriorityList;

    /**
     * The list of the attribute quality in string format. Call
     * GetAttributeNameForIndex to know which attribute corresponds to an index
     * of the spectrum
     */
    @Attribute
    private String[] attributesQualityList;

    /**
     * The result of the writing and reading instruction
     */
    @Attribute
    private String[] attributesResultReport;

    /**
     * Application of the logical gate LogicalBoolean gates on booleanSpectrum
     * attribute
     */
    @SuppressWarnings("unused")
    @Attribute
    private boolean booleanResult;

    /**
     * Spectrum of boolean value
     */
    @Attribute
    private boolean[] booleanSpectrum;

    @DynamicManagement
    DynamicManager dynMngt;

    private ScheduledExecutorService executor;

    ScheduledFuture<?> future;

    /**
     * The time out of the device proxy
     */
    @SuppressWarnings("unused")
    @DeviceProperty
    private String[] individualTimeout;

    /**
     * The internal period of the Reading Thread
     */
    @DeviceProperty
    private String[] internalReadingPeriod;

    private long internalReadingPeriodL;

    /**
     * The last state event : STATE at DATE
     */
    @SuppressWarnings("unused")
    @Attribute
    private String lastStateEvent = "";
    /**
     * The logical gates to apply on the list of attribute.
     */
    @DeviceProperty
    private String[] logicalBoolean;

    /**
     * The priority number of a quality (the greater is the most important is
     * ex: 5 for ALARM) Call GetTangoQuality to know the list of the Tango
     * Quality order.
     */
    @DeviceProperty
    private String[] priorityList;

    PriorityQualityManager qualityManager;
    /**
     * Spectrum Result
     */
    @Attribute
    private double[] spectrumResult;
    /**
     * The state of the device
     */
    @State
    private DeviceState state = DeviceState.ON;
    /**
     * The status of the device
     */
    @SuppressWarnings("unused")
    @Status
    private String status = "";

    @SuppressWarnings("unused")
    @DeviceProperty
    private String[] textTalkerDeviceProxy;

    private AttributeGroupTaskReader valueReader;

    /**
     * The number version of the device
     */
    @SuppressWarnings("unused")
    @Attribute
    private String version;

    /**
     * Initialize the device.
     */

    /**
     * Execute command "ActivateAll" on device. This command write 1 or true on
     * all the attributes
     */
    @Command(name = "ActivateAll")
    public void activateAll() throws DevFailed {
	setAllValues(1);
    }

    /**
     * Execute command "DeactivateAll" on device. This command write 0 or false
     * on all the attributes
     */
    @Command(name = "DeactivateAll")
    public void deactivateAll() throws DevFailed {
	setAllValues(0);
    }

    /**
     * Execute command "GetAttributeNameForIndex" on device. This command return
     * the attribute of an associated index
     */
    @Command(name = "GetAttributeNameForIndex")
    public String getAttributeNameForIndex(final short argin) throws DevFailed {
	xlogger.entry();
	String argout = "Unknown Index";
	logger.debug("argin {}", argin);
	if (attributeNameList != null && argin > -1 && argin < attributeNameList.length) {
	    argout = attributeNameList[argin];
	}
	xlogger.exit();
	return argout;
    }

    public short[] getAttributesNumberPriorityList() {
	xlogger.entry();
	attributesNumberPriorityList = qualityManager.getQualityNumberArray();
	xlogger.exit();
	return attributesNumberPriorityList;
    }

    public String[] getAttributesQualityList() {
	xlogger.entry();
	attributesQualityList = qualityManager.getQualityArray();
	xlogger.exit();
	return attributesQualityList;
    }

    public String[] getAttributesResultReport() {
	xlogger.entry();
	final Map<String, String> errorReportMap = valueReader.getErrorReportMap();
	int index = 0;
	if (!errorReportMap.isEmpty()) {
	    attributesResultReport = new String[errorReportMap.size()];
	    for (final Map.Entry<String, String> entry : errorReportMap.entrySet()) {
		attributesResultReport[index++] = entry.getKey() + "->" + entry.getValue();
	    }
	} else {
	    attributesResultReport = new String[1];
	    attributesResultReport[index] = "no value";
	}
	xlogger.exit();
	return attributesResultReport;
    }

    public boolean[] getBooleanSpectrum() {
	xlogger.entry();
	booleanSpectrum = new boolean[attributeNameList.length];
	Boolean a = null;
	Boolean b = null;
	for (final Map.Entry<String, Double> entry : valueReader.getAttributeValueMap().entrySet()) {
	    final String attrName = entry.getKey();
	    final double value = entry.getValue();
	    final int index = getIndexForAttribute(attrName);
	    if (value == 1) {
		booleanSpectrum[index] = true;
		a = true;
	    } else {
		booleanSpectrum[index] = false;
		b = false;
	    }
	}
	if (a == null) {
	    a = false;
	}
	if (b == null) {
	    b = true;
	}
	if (logicalBoolean[0].equalsIgnoreCase("NONE")) {
	    booleanResult = false;
	} else if (logicalBoolean[0].equalsIgnoreCase("XOR")) {
	    booleanResult = a ^ b;
	} else if (logicalBoolean[0].equalsIgnoreCase("OR")) {
	    booleanResult = a | b;
	} else if (logicalBoolean[0].equalsIgnoreCase("AND")) {
	    booleanResult = a & b;
	}

	xlogger.exit();
	return booleanSpectrum;
    }

    /**
     * Get the custom priority list
     */
    private void configureCustomPriorityList() {
	xlogger.entry();
	// Get the custom priority
	for (final String element : priorityList) {
	    // So count the token separated by ","
	    final StringTokenizer token = new StringTokenizer(element.trim(), ",");
	    if (token.countTokens() == 2) {
		// To avoid the pb of case
		final String tmpQualityName = token.nextToken().trim().toUpperCase();
		// If the custom state exist
		if (QualityUtilities.isQualityExist(tmpQualityName)) {
		    final int tmpPriority = Integer.valueOf(token.nextToken().trim());
		    qualityManager.putQualityPriority(QualityUtilities.getQualityForName(tmpQualityName), tmpPriority);
		}
	    }
	}
	xlogger.exit();
    }

    private int getIndexForAttribute(final String attributeName) {
	xlogger.entry();
	int idx = -1;
	for (int i = 0; i < attributeNameList.length; i++) {
	    if (attributeNameList[i].trim().equalsIgnoreCase(attributeName)) {
		idx = i;
	    }
	}
	xlogger.exit();
	return idx;
    }

    /**
     * Execute command "GetLogicalChoices" on device. This command return the
     * list of possibles logical gates
     */
    @Command(name = "GetLogicalChoices", displayLevel = 1)
    public String[] getLogicalChoices() throws DevFailed {
	xlogger.entry();
	final LogicalGateType[] values = LogicalGateType.values();
	final String[] logicalChoices = new String[values.length];
	for (int i = 0; i < logicalChoices.length; i++) {
	    logicalChoices[i] = values[i].toString();
	}
	xlogger.exit();
	return logicalChoices;
    }

    /**
     * Execute command "GetPriorityForQuality" on device. This command return
     * the priority for a given quality
     */
    @Command(name = "GetPriorityForQuality")
    public short getPriorityForQuality(final String argin) throws DevFailed {
	xlogger.entry();
	logger.debug(" argin {}", argin);
	final short argout = (short) qualityManager.getPriorityForQuality(argin);
	xlogger.exit();
	return argout;

    }

    public double[] getSpectrumResult() {
	xlogger.entry();
	spectrumResult = new double[attributeNameList.length];
	for (final Map.Entry<String, Double> entry : valueReader.getAttributeValueMap().entrySet()) {
	    final String attrName = entry.getKey();
	    final double value = entry.getValue();
	    final int index = getIndexForAttribute(attrName);
	    spectrumResult[index] = value;
	}
	xlogger.exit();
	return spectrumResult;
    }

    public DeviceState getState() {
	if (valueReader != null) {
	    final DeviceState newState = valueReader.getState();
	    if (!state.equals(newState)) {
		lastStateEvent = newState.toString() + " at " + dateInsertformat.format(new Date());
		state = newState;
		status = valueReader.getStatus();
	    }

	}
	return state;
    }

    /**
     * Execute command "GetTangoQualities" on device. This command return the
     * list of possibles qualities
     */
    @Command(name = "GetTangoQualities", displayLevel = 1)
    public String[] getTangoQualities() throws DevFailed {
	xlogger.entry();
	xlogger.exit();
	return QualityUtilities.QUALITYIST;
    }

    /**
     * Creation of the group of devices
     */
    private void createTangoGroup() throws DevFailed {
	xlogger.entry();
	// If no property defined the devices is in STANDBY
	if (attributeNameList.length > 0 || !attributeNameList[0].trim().isEmpty()) {
	    // set to remove duplications
	    final HashSet<String> set = new HashSet<String>(Arrays.asList(attributeNameList));
	    attributeGroup = new TangoGroupAttribute("attribute composer", set.toArray(new String[set.size()]));
	    attributeNameArray = new String[attributeNameList.length];
	    int i = 0;
	    for (final String attribute : attributeNameList) {
		attributeNameArray[i++] = attribute.substring(attribute.lastIndexOf("/") + 1);
	    }
	} else {
	    DevFailedUtils.throwDevFailed("INIT_ERROR", "No attribute defined in property");
	}
	xlogger.exit();
    }

    @Init(lazyLoading = true)
    public void initDevice() throws DevFailed {
	xlogger.entry();
	version = versionStatic;
	qualityManager = new PriorityQualityManager();
	configureCustomPriorityList();

	createTangoGroup();
	// create a timer to read attributes
	internalReadingPeriodL = Long.parseLong(internalReadingPeriod[0]);
	if (internalReadingPeriodL < 0) {
	    internalReadingPeriodL = 3000;
	}
	executor = Executors.newScheduledThreadPool(1);
	valueReader = new AttributeGroupTaskReader(attributeGroup, qualityManager);
	future = executor.scheduleAtFixedRate(valueReader, 0L, internalReadingPeriodL, TimeUnit.MILLISECONDS);

	xlogger.exit();
    }

    @Delete
    public void deleteDevice() {
	if (future != null) {
	    future.cancel(true);
	}
	if (executor != null) {
	    executor.shutdownNow();
	}
	dynMngt.clearAll();
    }

    /**
     * Execute command "SetAllFormat" on device. This command set the format
     * property eg : %6.3f on all the attributes
     */
    @Command(name = "SetAllFormat")
    public void setAllFormat(final String argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin  {}", argin);
	setAttributeProperty(argin, PropertyType.FORMAT);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllLabel" on device. This command set the Label
     * property on all the attributes
     */
    @Command(name = "SetAllLabel")
    public void setAllLabel(final String argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin  {}", argin);
	setAttributeProperty(argin, PropertyType.LABEL);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllMaxAlarm" on device. This command set the maximum
     * alarm value property on all the attributes
     */
    @Command(name = "SetAllMaxAlarm")
    public void setAllMaxAlarm(final double argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin  {}", argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.ALARM_MAX);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllMaxValue" on device. This command set the maximum
     * value property on all the attributes
     */
    @Command(name = "SetAllMaxValue")
    public void setAllMaxValue(final double argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin  {}", argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.MAX_VAL);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllMinAlarm" on device. This command set the minimum
     * alarm value property on all the attributes
     */
    @Command(name = "SetAllMinAlarm")
    public void setAllMinAlarm(final double argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin  {}", argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.ALARM_MIN);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllMinValue" on device. This command set the minimum
     * value property on all the attributes
     */
    @Command(name = "SetAllMinValue")
    public void setAllMinValue(final double argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin {} ", argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.MIN_VAL);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllUnit" on device. This command set the Unit
     * property eg : Volt on all the attributes
     */
    @Command(name = "SetAllUnit")
    public void setAllUnit(final String argin) throws DevFailed {
	xlogger.entry();
	logger.debug("argin  {}", argin);
	setAttributeProperty(argin, PropertyType.UNIT);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllValues" on device. This command write the given
     * value on all the attributes
     */
    @Command(name = "SetAllValues")
    public void setAllValues(final double argin) throws DevFailed {
	xlogger.entry();
	logger.debug("writing {}", argin);
	if (!Double.isNaN(argin)) {
	    attributeGroup.insert(Double.toString(argin));
	    attributeGroup.write();
	}
	xlogger.exit();
    }

    private void setAttributeProperty(final String property, final PropertyType type) throws DevFailed {
	xlogger.entry();
	logger.debug("property {} ", property);
	logger.debug("type {} ", type);
	// Get each proxy
	DeviceProxy deviceProxy;
	AttributeInfo attributeInfo;
	for (int i = 0; i < attributeNameList.length; i++) {
	    deviceProxy = attributeGroup.getGroup().getDevice(attributeNameList[i]);
	    attributeInfo = deviceProxy.get_attribute_info(attributeNameArray[i]);
	    switch (type) {
	    case FORMAT:
		attributeInfo.format = property;
		break;
	    case UNIT:
		attributeInfo.unit = property;
		break;
	    case ALARM_MIN:
		attributeInfo.min_alarm = property;
		break;
	    case ALARM_MAX:
		attributeInfo.max_alarm = property;
		break;
	    case MIN_VAL:
		attributeInfo.min_value = property;
		break;
	    case MAX_VAL:
		attributeInfo.max_value = property;
		break;
	    case LABEL:
		attributeInfo.label = property;
		break;
	    }
	    deviceProxy.set_attribute_info(new AttributeInfo[] { attributeInfo });
	}
	xlogger.exit();
    }

}