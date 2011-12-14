package fr.soleil.tango.server.attributecomposer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.stat.StatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.tango.DeviceState;
import org.tango.server.ServerManager;
import org.tango.server.annotation.Attribute;
import org.tango.server.annotation.Command;
import org.tango.server.annotation.Delete;
import org.tango.server.annotation.Device;
import org.tango.server.annotation.DeviceProperty;
import org.tango.server.annotation.DynamicManagement;
import org.tango.server.annotation.Init;
import org.tango.server.annotation.State;
import org.tango.server.annotation.Status;
import org.tango.server.dynamic.DynamicManager;
import org.tango.server.dynamic.command.GroupCommand;
import org.tango.utils.DevFailedUtils;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.DeviceProxy;
import fr.esrf.TangoApi.QualityUtilities;
import fr.soleil.tango.attributecomposer.PriorityQualityManager;
import fr.soleil.tango.clientapi.TangoGroupAttribute;
import fr.soleil.tango.statecomposer.StateResolver;
import fr.soleil.tango.util.TangoUtil;

@Device
public final class AttributeComposer {

    private static final int REFRESH_PERIOD = 3000;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeComposer.class);

    private static final XLogger XLOGGER = XLoggerFactory.getXLogger(AttributeComposer.class);

    /**
     * MAIN
     */
    public static void main(final String[] args) {
	final ResourceBundle rb = ResourceBundle.getBundle("fr.soleil.attributecomposer.application");
	version = rb.getString("project.version");
	try {
	    ServerManager.getInstance().addClass("AttributeComposer", AttributeComposer.class);
	    ServerManager.getInstance().start(args, "AttributeComposer");
	} catch (final DevFailed e) {
	    DevFailedUtils.printDevFailed(e);
	    LOGGER.debug(DevFailedUtils.toString(e));
	}
    }

    /**
     * The list of attribute names to compose. The name may be may contain the wild char * for device name (ie.
     * *VI*\/pressure)
     */
    @DeviceProperty
    private String[] attributeNameList;

    private final List<String> fullAttributeNameList = new ArrayList<String>();

    /**
     * The priority number of a quality (the greater is the most important is ex: 5 for ALARM) Call GetTangoQuality to
     * know the list of the Tango Quality order.
     */
    @DeviceProperty
    private String[] priorityList = new String[0];

    /**
     * The time out for a device proxy
     */
    @DeviceProperty
    private int individualTimeout;

    /**
     * define is the device will read the monitored attributes' device states
     */
    @DeviceProperty
    private boolean isStateComposer;

    /**
     * The list of priorities for state composer
     */
    @DeviceProperty
    private String[] statePriorities = new String[0];

    /**
     * The internal period of the Reading Thread
     */
    @DeviceProperty
    private long internalReadingPeriod = REFRESH_PERIOD;
    /**
     * The logical gates to apply on the list of attribute.
     */
    @DeviceProperty
    private String logicalBoolean = "";

    /**
     * The request grouped commands
     */
    @DeviceProperty
    private String[] commandNameList = new String[0];

    /**
     * Application of the logical gate LogicalBoolean gates on booleanSpectrum attribute
     */
    @Attribute
    private boolean booleanResult;

    /**
     * The last state event : STATE at DATE
     */
    @Attribute
    private String lastStateEvent = "";

    @Attribute
    private double mean = 0;

    @Attribute
    private double std = 0;

    @Attribute
    private double max = 0;

    @Attribute
    private double min = 0;

    @DynamicManagement
    private DynamicManager dynMngt;

    private ScheduledExecutorService executor;
    /**
     * The table of the attribute name and their associated proxy group <attributeName, Group>
     */
    private TangoGroupAttribute attributeGroup;

    /**
     * The attribute names without device name
     */
    private String[] attributeNameArray;

    private ScheduledFuture<?> future;

    private StateResolver stateReader;

    private final PriorityQualityManager qualityManager = new PriorityQualityManager();
    /**
     * Spectrum Result
     */
    @Attribute
    private double[] spectrumResult = new double[] {};
    /**
     * The state of the device
     */
    @State
    private DeviceState state = DeviceState.ON;
    /**
     * The status of the device
     */
    @Status
    private String status = "";

    private AttributeGroupTaskReader valueReader;

    /**
     * The number version of the device
     */
    @Attribute
    private static String version;

    /**
     * Initialize the device.
     */

    /**
     * Execute command "ActivateAll" on device. This command write 1 or true on all the attributes
     */
    @Command(name = "ActivateAll")
    public void activateAll() throws DevFailed {
	setAllValues(1);
    }

    /**
     * Execute command "DeactivateAll" on device. This command write 0 or false on all the attributes
     */
    @Command(name = "DeactivateAll")
    public void deactivateAll() throws DevFailed {
	setAllValues(0);
    }

    /**
     * Execute command "GetAttributeNameForIndex" on device. This command return the attribute of an associated index
     */
    @Command(name = "GetAttributeNameForIndex")
    public String getAttributeNameForIndex(final short argin) throws DevFailed {
	XLOGGER.entry();
	String argout = "Unknown Index";
	LOGGER.debug("argin {}", argin);
	if (!fullAttributeNameList.isEmpty()) {
	    argout = fullAttributeNameList.get(argin);
	}
	XLOGGER.exit();
	return argout;
    }

    /**
     * The list of the attributes quality in priority number format. Call GetAttributeNameForIndex to know which
     * attributes corresponds to an index of the spectrum. Call GetPriorityForQuality to know the values of tango
     * qualities.
     */
    @Attribute
    public short[] getAttributesNumberPriorityList() {
	XLOGGER.entry();
	final short[] attributesNumberPriorityList = qualityManager.getQualityNumberArray();
	XLOGGER.exit();
	return attributesNumberPriorityList;
    }

    /**
     * The list of the attribute quality in string format. Call GetAttributeNameForIndex to know which attribute
     * corresponds to an index of the spectrum
     */
    @Attribute
    public String[] getAttributesQualityList() {
	XLOGGER.entry();
	final String[] attributesQualityList = qualityManager.getQualityArray();
	XLOGGER.exit();
	return attributesQualityList;
    }

    /**
     * The result of the writing and reading instruction
     */
    @Attribute
    public String[] getAttributesResultReport() {
	XLOGGER.entry();
	String[] attributesResultReport = null;
	if (valueReader != null) {
	    final Map<String, String> errorReportMap = valueReader.getErrorReportMap();
	    int index = 0;
	    if (!errorReportMap.isEmpty()) {
		attributesResultReport = new String[errorReportMap.size()];
		for (final Map.Entry<String, String> entry : errorReportMap.entrySet()) {
		    attributesResultReport[index++] = entry.getKey() + "->" + entry.getValue();
		}
	    } else {
		attributesResultReport = new String[] { "no value" };
	    }
	}
	XLOGGER.exit();
	return attributesResultReport;
    }

    /**
     * Spectrum of boolean values
     */
    @Attribute
    public boolean[] getBooleanSpectrum() {
	XLOGGER.entry();
	final boolean[] booleanSpectrum;
	if (valueReader != null) {
	    booleanSpectrum = new boolean[fullAttributeNameList.size()];
	    boolean a = false;
	    boolean b = true;
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
	    if (logicalBoolean.equalsIgnoreCase("NONE")) {
		booleanResult = false;
	    } else if (logicalBoolean.equalsIgnoreCase("XOR")) {
		booleanResult = a ^ b;
	    } else if (logicalBoolean.equalsIgnoreCase("OR")) {
		booleanResult = a | b;
	    } else if (logicalBoolean.equalsIgnoreCase("AND")) {
		booleanResult = a & b;
	    }
	} else {
	    booleanSpectrum = new boolean[0];
	}
	XLOGGER.exit();
	return booleanSpectrum;
    }

    /**
     * Get the custom priority list
     */
    private void configureCustomPriorityList() {
	XLOGGER.entry();
	// Get the custom priority
	for (final String element : priorityList) {
	    // So count the token separated by ","
	    final StringTokenizer token = new StringTokenizer(element.trim(), ",");
	    if (token.countTokens() == 2) {
		// To avoid the pb of case
		final String tmpQualityName = token.nextToken().trim().toUpperCase(Locale.getDefault());
		// If the custom state exist
		if (QualityUtilities.isQualityExist(tmpQualityName)) {
		    final int tmpPriority = Integer.valueOf(token.nextToken().trim());
		    qualityManager.putQualityPriority(QualityUtilities.getQualityForName(tmpQualityName), tmpPriority);
		}
	    }
	}
	XLOGGER.exit();
    }

    private int getIndexForAttribute(final String attributeName) {
	XLOGGER.entry();
	int idx = -1;
	for (int i = 0; i < fullAttributeNameList.size(); i++) {
	    if (fullAttributeNameList.get(i).trim().equalsIgnoreCase(attributeName)) {
		idx = i;
	    }
	}
	XLOGGER.exit();
	return idx;
    }

    /**
     * Execute command "GetLogicalChoices" on device. This command return the list of possibles logical gates
     */
    @Command(name = "GetLogicalChoices", displayLevel = 1)
    public String[] getLogicalChoices() throws DevFailed {
	XLOGGER.entry();
	final LogicalGateType[] values = LogicalGateType.values();
	final String[] logicalChoices = new String[values.length];
	for (int i = 0; i < logicalChoices.length; i++) {
	    logicalChoices[i] = values[i].toString();
	}
	XLOGGER.exit();
	return logicalChoices;
    }

    /**
     * Execute command "GetPriorityForQuality" on device. This command return the priority for a given quality
     */
    @Command(name = "GetPriorityForQuality")
    public short getPriorityForQuality(final String argin) throws DevFailed {
	XLOGGER.entry();
	LOGGER.debug(" argin {}", argin);
	final short argout = (short) qualityManager.getPriorityForQuality(argin);
	XLOGGER.exit();
	return argout;

    }

    public double[] getSpectrumResult() {
	XLOGGER.entry();

	if (valueReader != null) {
	    spectrumResult = new double[fullAttributeNameList.size()];
	    for (final Map.Entry<String, Double> entry : valueReader.getAttributeValueMap().entrySet()) {
		final String attrName = entry.getKey();
		final double value = entry.getValue();
		final int index = getIndexForAttribute(attrName);
		spectrumResult[index] = value;
	    }
	}
	XLOGGER.exit();
	return Arrays.copyOf(spectrumResult, spectrumResult.length);
    }

    public DeviceState getState() {
	if (stateReader != null) {
	    final DeviceState newState = DeviceState.getDeviceState(stateReader.getState());
	    if (!state.equals(newState)) {
		lastStateEvent = newState.toString() + " at " + dateInsertformat.format(new Date());
		state = newState;
	    }
	} else if (valueReader != null) {
	    final DeviceState newState = valueReader.getState();
	    if (!state.equals(newState)) {
		lastStateEvent = newState.toString() + " at " + dateInsertformat.format(new Date());
		state = newState;
	    }
	}
	return state;
    }

    public String getStatus() {
	if (valueReader != null) {
	    status = valueReader.getStatus();
	}
	return status;
    }

    /**
     * Execute command "GetTangoQualities" on device. This command return the list of possibles qualities
     */
    @Command(name = "GetTangoQualities", displayLevel = 1)
    public String[] getTangoQualities() throws DevFailed {
	XLOGGER.entry();
	XLOGGER.exit();
	return QualityUtilities.QUALITYIST;
    }

    @Init(lazyLoading = true)
    public void initDevice() throws DevFailed {
	XLOGGER.entry();

	createAttributeGroup();

	configureCustomPriorityList();
	// create a timer to read attributes

	executor = Executors.newScheduledThreadPool(1);
	valueReader = new AttributeGroupTaskReader(attributeGroup, qualityManager);
	future = executor.scheduleAtFixedRate(valueReader, 0L, internalReadingPeriod, TimeUnit.MILLISECONDS);

	// retrieve device name from attribute name
	final Set<String> deviceNameList = new HashSet<String>();
	for (final String element : fullAttributeNameList) {
	    final String deviceName = TangoUtil.getfullDeviceNameForAttribute(element);
	    deviceNameList.add(deviceName);
	}
	LOGGER.debug("doing state composition {}", isStateComposer);
	// configure state composition
	if (isStateComposer) {
	    LOGGER.debug("doing state composition");
	    stateReader = new StateResolver(internalReadingPeriod, false);
	    stateReader.configurePriorities(statePriorities);

	    stateReader.setMonitoredDevices(individualTimeout,
		    deviceNameList.toArray(new String[deviceNameList.size()]));
	    stateReader.start();
	}

	// creat dynamic group command
	createDynamicCommands(deviceNameList);

	XLOGGER.exit();
    }

    private void createAttributeGroup() throws DevFailed {
	// configure the attribute group

	// set to remove duplications
	final HashSet<String> set = new HashSet<String>(Arrays.asList(attributeNameList));
	// retrieve full name from pattern
	for (final String attributePattern : set) {
	    if (attributePattern.contains("*") && attributePattern.contains("/")) {
		final String devicePattern = attributePattern.substring(0, attributePattern.lastIndexOf('/'));
		final String attributeName = attributePattern.substring(attributePattern.lastIndexOf('/'));
		final List<String> deviceNames = Arrays.asList(TangoUtil.getDevicesForPattern(devicePattern));
		final List<String> attributesNames = new ArrayList<String>();
		for (final String deviceName : deviceNames) {
		    attributesNames.add(deviceName + attributeName);
		}
		fullAttributeNameList.addAll(attributesNames);
	    } else {
		fullAttributeNameList.add(attributePattern);
	    }
	}
	attributeGroup = new TangoGroupAttribute("attribute composer",
		fullAttributeNameList.toArray(new String[fullAttributeNameList.size()]));
	attributeNameArray = new String[fullAttributeNameList.size()];
	int i = 0;
	for (final String attribute : fullAttributeNameList) {
	    attributeNameArray[i++] = TangoUtil.getAttributeName(attribute);
	}
    }

    private void createDynamicCommands(final Set<String> deviceNameList) throws DevFailed {
	if (commandNameList.length > 0 && !commandNameList[0].trim().equals("")) {
	    // use set to suppress duplicate elements
	    final Set<String> cmdList = new HashSet<String>(Arrays.asList(commandNameList));
	    for (final String element : cmdList) {
		final String commandName = element.trim();
		if (!commandName.isEmpty()) {
		    final GroupCommand behavior = new GroupCommand(commandName,
			    deviceNameList.toArray(new String[deviceNameList.size()]));
		    dynMngt.addCommand(behavior);
		}
	    }
	}
    }

    @Delete
    public void deleteDevice() throws DevFailed {
	fullAttributeNameList.clear();
	if (stateReader != null) {
	    stateReader.stop();
	}
	if (future != null) {
	    future.cancel(true);
	}
	if (executor != null) {
	    executor.shutdownNow();
	}
	dynMngt.clearAll();
    }

    /**
     * Execute command "SetAllFormat" on device. This command set the format property eg : %6.3f on all the attributes
     */
    @Command(name = "SetAllFormat")
    public void setAllFormat(final String argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(argin, PropertyType.FORMAT);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllLabel" on device. This command set the Label property on all the attributes
     */
    @Command(name = "SetAllLabel")
    public void setAllLabel(final String argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(argin, PropertyType.LABEL);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllMaxAlarm" on device. This command set the maximum alarm value property on all the
     * attributes
     */
    @Command(name = "SetAllMaxAlarm")
    public void setAllMaxAlarm(final double argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.ALARM_MAX);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllMaxValue" on device. This command set the maximum value property on all the attributes
     */
    @Command(name = "SetAllMaxValue")
    public void setAllMaxValue(final double argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.MAX_VAL);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllMinAlarm" on device. This command set the minimum alarm value property on all the
     * attributes
     */
    @Command(name = "SetAllMinAlarm")
    public void setAllMinAlarm(final double argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.ALARM_MIN);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllMinValue" on device. This command set the minimum value property on all the attributes
     */
    @Command(name = "SetAllMinValue")
    public void setAllMinValue(final double argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(String.valueOf(argin), PropertyType.MIN_VAL);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllUnit" on device. This command set the Unit property eg : Volt on all the attributes
     */
    @Command(name = "SetAllUnit")
    public void setAllUnit(final String argin) throws DevFailed {
	XLOGGER.entry(argin);
	setAttributeProperty(argin, PropertyType.UNIT);
	XLOGGER.exit();
    }

    /**
     * Execute command "SetAllValues" on device. This command write the given value on all the attributes
     */
    @Command(name = "SetAllValues")
    public void setAllValues(final double argin) throws DevFailed {
	XLOGGER.entry(argin);
	attributeGroup.write(argin);
	XLOGGER.exit();
    }

    private void setAttributeProperty(final String property, final PropertyType type) throws DevFailed {
	XLOGGER.entry();
	LOGGER.debug("property {} ", property);
	LOGGER.debug("type {} ", type);
	// Get each proxy
	for (int i = 0; i < attributeNameList.length; i++) {
	    final DeviceProxy deviceProxy = attributeGroup.getGroup().getDevice(attributeNameList[i]);
	    final AttributeInfo attributeInfo = deviceProxy.get_attribute_info(attributeNameArray[i]);
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
	    default:
		DevFailedUtils.throwDevFailed("unknown property " + type);
	    }
	    deviceProxy.set_attribute_info(new AttributeInfo[] { attributeInfo });
	}
	XLOGGER.exit();
    }

    public double getMean() {
	getSpectrumResult();
	if (!ArrayUtils.isEmpty(spectrumResult)) {
	    mean = StatUtils.mean(spectrumResult);
	}
	return mean;
    }

    public double getStd() {
	getSpectrumResult();
	if (!ArrayUtils.isEmpty(spectrumResult)) {
	    std = Math.sqrt(StatUtils.variance(spectrumResult));
	}
	return std;
    }

    public double getMax() {
	getSpectrumResult();
	if (!ArrayUtils.isEmpty(spectrumResult)) {
	    max = StatUtils.max(spectrumResult);
	}
	return max;
    }

    public double getMin() {
	getSpectrumResult();
	if (!ArrayUtils.isEmpty(spectrumResult)) {
	    min = StatUtils.min(spectrumResult);
	}
	return min;
    }

    public boolean isBooleanResult() {
	return booleanResult;
    }

    public String getLastStateEvent() {
	return lastStateEvent;
    }

    public String getVersion() {
	return version;
    }

    public void setState(final DeviceState state) {
	this.state = state;
    }

    public void setStatus(final String status) {
	this.status = status;
    }

    public void setDynMngt(final DynamicManager dynMngt) {
	this.dynMngt = dynMngt;
    }

    public void setAttributeNameList(final String[] attributeNameList) throws DevFailed {
	if (attributeNameList.length > 0 || !attributeNameList[0].trim().isEmpty()) {
	    this.attributeNameList = Arrays.copyOf(attributeNameList, attributeNameList.length);
	} else {
	    DevFailedUtils.throwDevFailed("INIT_ERROR", "No attribute defined in property");
	}

    }

    public void setIndividualTimeout(final int individualTimeout) {
	this.individualTimeout = individualTimeout;
    }

    public void setCommandNameList(final String[] commandNameList) {
	this.commandNameList = Arrays.copyOf(commandNameList, commandNameList.length);
    }

    public void setPriorityList(final String[] priorityList) {
	this.priorityList = Arrays.copyOf(priorityList, priorityList.length);
    }

    public void setStateComposer(final boolean isStateComposer) {
	this.isStateComposer = isStateComposer;
    }

    public void setStatePriorities(final String[] statePriorities) {
	this.statePriorities = Arrays.copyOf(statePriorities, statePriorities.length);
    }

    public void setInternalReadingPeriod(final long internalReadingPeriod) {
	this.internalReadingPeriod = internalReadingPeriod;
    }

    public void setLogicalBoolean(final String logicalBoolean) {
	this.logicalBoolean = logicalBoolean;
    }

}