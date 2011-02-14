package fr.soleil.tango.server.attributecomposer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
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
import org.tango.server.idl4.DeviceState;
import org.tango.server.idl4.ServerManager;
import org.tango.server.idl4.annotation.Attribute;
import org.tango.server.idl4.annotation.Command;
import org.tango.server.idl4.annotation.Device;
import org.tango.server.idl4.annotation.DeviceProperty;
import org.tango.server.idl4.annotation.DynamicAttributeManagement;
import org.tango.server.idl4.annotation.Init;
import org.tango.server.idl4.annotation.State;
import org.tango.server.idl4.annotation.Status;
import org.tango.server.idl4.dynamic.DynamicManager;
import org.tango.utils.DevFailedUtils;

import AttributeComposer.PriorityQualityManager;
import fr.esrf.Tango.AttrQuality;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoApi.DeviceProxy;
import fr.esrf.TangoApi.QualityUtilities;
import fr.esrf.TangoApi.Group.GroupAttrReply;
import fr.esrf.TangoApi.Group.GroupAttrReplyList;
import fr.soleil.device.utils.AttributeHelper;
import fr.soleil.tango.clientapi.TangoGroupAttribute;
import fr.soleil.tango.util.TangoUtil;

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

    public class ValueReader implements Runnable {
	@Override
	public void run() {
	    valueReader();
	}

	public void valueReader() {
	    xlogger.entry();
	    GroupAttrReplyList resultGroup = null;
	    // read attributes
	    try {
		resultGroup = attributeGroup.read();
	    } catch (final DevFailed devFailed) {
		setState(DeviceState.FAULT);
		status = dateInsertformat.format(new Date())
			+ " : Unexpected Error, cannot read: \n"
			+ TangoUtil.getDevFailedString(devFailed);
	    }

	    // extract results
	    boolean tmpHasFailed = false;
	    final Enumeration<?> enumeration = resultGroup.elements();
	    DeviceAttribute deviceAttribute = null;
	    String deviceName;
	    GroupAttrReply oneResult = null;
	    String attrName = null;
	    AttrQuality quality;

	    while (enumeration.hasMoreElements()) {
		oneResult = (GroupAttrReply) enumeration.nextElement();
		quality = AttrQuality.ATTR_INVALID;
		deviceName = oneResult.dev_name();
		attrName = oneResult.obj_name();
		try {
		    deviceAttribute = oneResult.get_data();
		    double tmpReadValue = Double.NaN;
		    tmpReadValue = AttributeHelper.extractToDouble(deviceAttribute);
		    quality = deviceAttribute.getQuality();
		    attributeValueMap.put(deviceName + "/" + attrName, tmpReadValue);
		    qualityManager.putAttributeQuality(deviceName + "/" + attrName, quality);
		} catch (final DevFailed devFailed) {
		    tmpHasFailed = true;
		    qualityManager.putAttributeQuality(deviceName + "/" + attrName, quality);
		    attributeResultReportMap.put(deviceName + "/" + attrName, dateInsertformat
			    .format(new Date())
			    + " : " + TangoUtil.getDevFailedString(devFailed));
		    devFailed.printStackTrace();
		}
	    }
	    logger.debug("valueReader -> tmpHasFailed = " + tmpHasFailed);
	    if (tmpHasFailed) {
		setState(DeviceState.FAULT);
		status = dateInsertformat.format(new Date())
			+ " : Error see attributesResultReport";
	    } else {
		setState(DeviceState.getDeviceState(qualityManager.getHighestPriorityState()));
		status = "At least one attribute is of quality "
			+ qualityManager.getHighestPriorityQualityAsString();
	    }
	    xlogger.exit();
	}
    }

    /**
     * SimpleDateFormat to timeStamp the error messages
     */
    public static final SimpleDateFormat dateInsertformat = new SimpleDateFormat(
	    "dd-MM-yyyy HH:mm:ss");

    private static Logger logger = LoggerFactory.getLogger(AttributeComposer.class);

    /**
     * The number version of the device
     */
    @SuppressWarnings("unused")
    @Attribute
    private static String version;

    private final static XLogger xlogger = XLoggerFactory.getXLogger(AttributeComposer.class);

    /**
     * MAIN
     */
    public static void main(final String[] args) {
	// VH: TODO too platform dependant
	System.setProperty("TANGO_HOST", "calypso:20001");
	final ResourceBundle rb = ResourceBundle
		.getBundle("fr.soleil.AttributeComposer.application");
	version = rb.getString("project.version");
	final String[] arg = new String[] { "test" };
	try {
	    ServerManager.getInstance().addClass("" + "AttributeComposer", AttributeComposer.class);
	    ServerManager.getInstance().start(arg, "AttributeComposer");
	} catch (final DevFailed e) {
	    logger.debug(DevFailedUtils.toString(e));
	}
    }

    /**
     * The table of the attribute name and their associated proxy group
     * <attributeName, Group>
     */
    TangoGroupAttribute attributeGroup;

    private String[] attributeNameArray;

    /**
     * The list of attribute name used to composed the resum state and the
     * spectrum result.
     */
    @DeviceProperty
    private String[] attributeNameList;

    /**
     * The table of the attribute name and their associated message result
     * <attributeName, message report> the messages are generated during the
     * connexion, read or write instruction
     */
    private final Map<String, String> attributeResultReportMap = new HashMap<String, String>();

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
     * The table of the attribute name and their associated read values
     * <attributeName, values>
     */
    private final Map<String, Double> attributeValueMap = new HashMap<String, Double>();

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

    @DynamicAttributeManagement
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
     * The last DevState
     */
    private DeviceState lastState = null;
    /**
     * The last state event : STATE at DATE
     */
    @SuppressWarnings("unused")
    @Attribute
    private String lastStateEvent = null;

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
    private DeviceState state = null;
    /**
     * The status of the device
     */
    @SuppressWarnings("unused")
    @Status
    private String status = null;

    @SuppressWarnings("unused")
    @DeviceProperty
    private String[] textTalkerDeviceProxy;

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
	logger.debug("getAttributeNameForIndex -> argin = " + argin);
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
	int index = 0;
	if (!attributeResultReportMap.isEmpty()) {
	    logger.debug("copy of attributeResultReportMap in attributesResultReport");
	    attributesResultReport = new String[attributeResultReportMap.size()];
	    for (final Map.Entry<String, String> entry : attributeResultReportMap.entrySet()) {
		attributesResultReport[index++] = entry.getKey() + "->" + entry.getValue();
	    }
	} else {
	    logger.debug("attributeResultReportMap is empty");
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
	for (final Map.Entry<String, Double> entry : attributeValueMap.entrySet()) {
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
    private void getCustomPriorityList() {
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
		    qualityManager.putQualityPriority(QualityUtilities
			    .getQualityForName(tmpQualityName), tmpPriority);
		}
	    }
	}
	xlogger.exit();
    }

    private int getIndexForAttribute(final String aAttributeName) {
	xlogger.entry();
	logger.debug("getIndexForAttribute -> argin(aAttributeName) = " + aAttributeName);
	for (int i = 0; i < attributeNameList.length; i++) {
	    if (attributeNameList[i].trim().equalsIgnoreCase(aAttributeName)) {
		return i;
	    }
	}
	xlogger.exit();
	// if no entry return -1
	return -1;
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
	logger.debug("getPriorityForQuality -> argin = " + argin);
	short argout = (short) qualityManager.getPriorityForQuality(argin);
	xlogger.exit();
	return argout;

    }

    public double[] getSpectrumResult() {
	xlogger.entry();
	spectrumResult = new double[attributeNameList.length];
	for (final Map.Entry<String, Double> entry : attributeValueMap.entrySet()) {
	    final String attrName = entry.getKey();
	    final double value = entry.getValue();
	    final int index = getIndexForAttribute(attrName);
	    spectrumResult[index] = value;
	}
	xlogger.exit();
	return spectrumResult;
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
    private void groupCreation() throws DevFailed {
	xlogger.entry();
	// If no property defined the devices is in STANDBY
	if (attributeNameList.length > 0 || !attributeNameList[0].trim().isEmpty()) {
	    attributeGroup = new TangoGroupAttribute("attribute composer", attributeNameList);
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

    /**
     * Initialize the device.
     */
    @Init(lazyLoading = true)
    public void initDevice() throws DevFailed {
	xlogger.entry();
	setState(DeviceState.INIT);
	qualityManager = new PriorityQualityManager();
	getCustomPriorityList();

	groupCreation();
	// create a timer to read attributes
	internalReadingPeriodL = Long.parseLong(internalReadingPeriod[0]);
	if (internalReadingPeriodL < 0) {
	    internalReadingPeriodL = 3000;
	}
	executor = Executors.newScheduledThreadPool(1);
	future = executor.scheduleAtFixedRate(new ValueReader(), 0L, internalReadingPeriodL,
		TimeUnit.MILLISECONDS);
	xlogger.exit();
    }

    /**
     * Execute command "SetAllFormat" on device. This command set the format
     * property eg : %6.3f on all the attributes
     */
    @Command(name = "SetAllFormat")
    public void setAllFormat(final String argin) throws DevFailed {
	xlogger.entry();
	logger.debug("setAllFormat -> argin = " + argin);
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
	logger.debug("SetAllLabel -> argin = " + argin);
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
	logger.debug("SetAllMaxAlarm -> argin = " + argin);
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
	logger.debug("setAllMaxValue -> argin = " + argin);
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
	logger.debug("setAllMinAlarm -> argin = " + argin);
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
	logger.debug("setAllMinValue -> argin = " + argin);
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
	logger.debug("setAllUnit -> argin = " + argin);
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
	logger.debug("setAllValues -> argin = " + argin);
	writeAttribute(argin);
	xlogger.exit();
    }

    private void setAttributeProperty(final String property, final PropertyType type)
	    throws DevFailed {
	xlogger.entry();
	logger.debug("setAttributeProperty -> property = " + property);
	logger.debug("setAttributeProperty -> type = " + type);
	// Get each proxy
	DeviceProxy devicePoxy;
	String deviceName;
	AttributeInfo attributeInfo;
	for (int i = 0; i < attributeNameList.length; i++) {
	    devicePoxy = attributeGroup.getGroup().getDevice(attributeNameList[i]);
	    deviceName = devicePoxy.get_name();
	    attributeInfo = devicePoxy.get_attribute_info(attributeNameArray[i]);

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

	    try {
		devicePoxy.set_attribute_info(new AttributeInfo[] { attributeInfo });
		attributeResultReportMap.put(deviceName + "/" + attributeNameArray[i],
			dateInsertformat.format(new Date()) + " : Set " + type + " property to "
				+ property + " : SUCCESS");
	    } catch (final DevFailed e) {
		attributeResultReportMap.put(deviceName + "/" + attributeNameArray[i],
			dateInsertformat.format(new Date()) + " : Set " + type + " property to "
				+ property + " : FAILED");
		throw e;
	    }
	}
	xlogger.exit();
    }

    public void setState(final DeviceState aState) {
	xlogger.entry();
	if (!state.equals(aState)) {
	    lastState = state;
	    lastStateEvent = lastState.toString() + " at " + dateInsertformat.format(new Date());
	    state = aState;
	}
	xlogger.exit();
    }

    public void writeAttribute(final double argin) throws DevFailed {
	xlogger.entry();
	logger.debug("writeAttribute -> argin = " + argin);
	if (Double.isNaN(argin)) {
	    logger.debug("writeAttribute -> argin is not a double. Exit");
	    xlogger.exit();
	    return;
	}
	attributeGroup.insert(new Double(argin).toString());
	attributeGroup.write();
	xlogger.exit();
    }
}