package fr.soleil.tango.server.attributecomposer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
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
import org.tango.server.annotation.DeviceManagement;
import org.tango.server.annotation.DeviceProperty;
import org.tango.server.annotation.DynamicManagement;
import org.tango.server.annotation.Init;
import org.tango.server.annotation.State;
import org.tango.server.annotation.Status;
import org.tango.server.annotation.TransactionType;
import org.tango.server.device.DeviceManager;
import org.tango.server.dynamic.DynamicManager;
import org.tango.server.dynamic.attribute.GroupAttribute;
import org.tango.server.dynamic.command.GroupCommand;
import org.tango.utils.CircuitBreakerCommand;
import org.tango.utils.DevFailedUtils;
import org.tango.utils.SimpleCircuitBreaker;
import org.tango.utils.TangoUtil;

import fr.esrf.Tango.DevFailed;
import fr.esrf.Tango.DispLevel;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.DeviceProxy;
import fr.esrf.TangoApi.QualityUtilities;
import fr.soleil.tango.attributecomposer.AttributeGroupReader;
import fr.soleil.tango.attributecomposer.AttributeGroupScheduler;
import fr.soleil.tango.attributecomposer.PriorityQualityManager;
import fr.soleil.tango.clientapi.TangoGroupAttribute;
import fr.soleil.tango.statecomposer.StateResolver;

@Device(transactionType = TransactionType.NONE)
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
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private Logger logger;

    private final XLogger xlogger = XLoggerFactory.getXLogger(AttributeComposer.class);

    /**
     * MAIN
     */
    public static void main(final String[] args) {
        final ResourceBundle rb = ResourceBundle.getBundle("fr.soleil.attributecomposer.application");
        version = rb.getString("project.version");
        ServerManager.getInstance().start(args, AttributeComposer.class);

    }

    /**
     * The list of attribute names to compose. The name may be may contain the wild char * for device name (ie.
     * *VI*\/pressure)
     */
    @DeviceProperty
    private String[] attributeNameList;

    private final List<String> fullAttributeNameList = new LinkedList<String>();

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
    @DeviceProperty(defaultValue = "true")
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

    // @Attribute
    // private double mean = 0;

    @Attribute
    private double std = 0;

    @Attribute
    private double max = 0;

    @Attribute
    private double min = 0;

    @DynamicManagement
    private DynamicManager dynMngt;

    @DeviceManagement
    private DeviceManager device;

    // private ScheduledExecutorService executor;
    /**
     * The table of the attribute name and their associated proxy group <attributeName, Group>
     */
    private TangoGroupAttribute attributeGroup;

    /**
     * The attribute names without device name
     */
    private String[] attributeNameArray;

    // private ScheduledFuture<?> future;

    private StateResolver stateReader;

    private final PriorityQualityManager qualityManager = new PriorityQualityManager();
    /**
     * Spectrum Result
     */
    @Attribute
    private double[] spectrumResult = new double[]{};
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

    private AttributeComposerReader valueReader;

    private AttributeGroupScheduler readScheduler;

    /**
     * The number version of the device
     */
    @Attribute(displayLevel = DispLevel._EXPERT)
    private static String version;


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
        xlogger.entry();
        String argout = "Unknown Index";
        logger.debug("argin {}", argin);
        if (!fullAttributeNameList.isEmpty()) {
            argout = fullAttributeNameList.get(argin);
        }
        xlogger.exit();
        return argout;
    }

    /**
     * The list of the attributes quality in priority number format. Call GetAttributeNameForIndex to know which
     * attributes corresponds to an index of the spectrum. Call GetPriorityForQuality to know the values of tango
     * qualities.
     */
    @Attribute
    public short[] getAttributesNumberPriorityList() {
        xlogger.entry();
        final short[] attributesNumberPriorityList = qualityManager.getQualityNumberArray();
        xlogger.exit();
        return attributesNumberPriorityList;
    }

    /**
     * The list of the attribute quality in string format. Call GetAttributeNameForIndex to know which attribute
     * corresponds to an index of the spectrum
     */
    @Attribute
    public String[] getAttributesQualityList() {
        xlogger.entry();
        final String[] attributesQualityList = qualityManager.getQualityArray();
        xlogger.exit();
        return attributesQualityList;
    }

    /**
     * The result of the writing and reading instruction
     */
    @Attribute
    public String[] getAttributesResultReport() {
        xlogger.entry();
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
                attributesResultReport = new String[]{"no value"};
            }
        }
        xlogger.exit();
        return attributesResultReport;
    }

    /**
     * Spectrum of boolean values
     */
    @Attribute
    public boolean[] getBooleanSpectrum() {
        xlogger.entry();
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
                final String tmpQualityName = token.nextToken().trim().toUpperCase(Locale.getDefault());
                // If the custom state exist
                if (QualityUtilities.isQualityExist(tmpQualityName)) {
                    final int tmpPriority = Integer.valueOf(token.nextToken().trim());
                    qualityManager.putQualityPriority(QualityUtilities.getQualityForName(tmpQualityName), tmpPriority);
                }
            }
        }
        xlogger.exit();
    }


    /**
     * Execute command "GetLogicalChoices" on device. This command return the list of possibles logical gates
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
     * Execute command "GetPriorityForQuality" on device. This command return the priority for a given quality
     */
    @Command(name = "GetPriorityForQuality")
    public short getPriorityForQuality(final String argin) throws DevFailed {
        xlogger.entry();
        logger.debug(" argin {}", argin);
        final short argout = (short) qualityManager.getPriorityForQuality(argin);
        xlogger.exit();
        return argout;

    }

    private void updateSpectrumResult() {
        if (valueReader != null) {
            spectrumResult = new double[fullAttributeNameList.size()];
            for (final Map.Entry<String, Double> entry : valueReader.getAttributeValueMap().entrySet()) {
                final String attrName = entry.getKey();
                final double value = entry.getValue();
                final int index = getIndexForAttribute(attrName);
                spectrumResult[index] = value;
            }
        }
    }

    public double[] getSpectrumResult() {
        xlogger.entry();
        updateSpectrumResult();
        xlogger.exit();
        return Arrays.copyOf(spectrumResult, spectrumResult.length);
    }

    public DeviceState getState() {
        if (stateReader != null) {
            final DeviceState newState = DeviceState.getDeviceState(stateReader.getState());
            if (!state.equals(newState)) {
                lastStateEvent = newState.toString() + " at " + DATE_FORMAT.format(new Date());
                state = newState;
            }
        } else if (valueReader != null) {
            final DeviceState newState = valueReader.getState();
            if (!state.equals(newState)) {
                lastStateEvent = newState.toString() + " at " + DATE_FORMAT.format(new Date());
                state = newState;
            }
        }
        return state;
    }

    public String getStatus() {
        final StringBuilder sb = new StringBuilder();
        if (stateReader != null) {
            final String[] st = stateReader.getDeviceStateArray();
            sb.append("At least one device is in ");
            sb.append(DeviceState.getDeviceState(stateReader.getState()));
            sb.append(" state:\n");
            for (final String element : st) {
                sb.append(element);
                sb.append("\n");
            }
            status = sb.toString();
        } else if (valueReader != null) {
            status = valueReader.getStatus();
        }
        return status;
    }

    /**
     * Execute command "GetTangoQualities" on device. This command return the list of possibles qualities
     */
    @Command(name = "GetTangoQualities", displayLevel = 1)
    public String[] getTangoQualities() throws DevFailed {
        xlogger.entry();
        xlogger.exit();
        return QualityUtilities.QUALITYIST;
    }

    /**
     * circuit breaker pattern for initializing the device
     *
     * @author ABEILLE
     */
    private class InitCommand implements CircuitBreakerCommand {

        @Override
        public void execute() throws DevFailed {
            createAttributeGroup();
            logger.info("trying init");
            status = "trying to init";

            // add attribute for group to write on it
            final GroupAttribute meanAttribute = new GroupAttribute("mean", false,
                    fullAttributeNameList.toArray(new String[fullAttributeNameList.size()]));
            dynMngt.addAttribute(meanAttribute);
            // add attribute for composition
            final GroupAttribute composedAttribute = new GroupAttribute("composition", false, false,
                    fullAttributeNameList.toArray(new String[fullAttributeNameList.size()]));
            dynMngt.addAttribute(composedAttribute);

            configureCustomPriorityList();

            // create a timer to read attributes
            valueReader = new AttributeComposerReader(attributeGroup, meanAttribute, composedAttribute, qualityManager);
            final AttributeGroupReader task = new AttributeGroupReader(valueReader, attributeGroup, false, true, false);
            readScheduler = new AttributeGroupScheduler();
            readScheduler.start(task, internalReadingPeriod);
            // future = executor.scheduleAtFixedRate(valueReader.getTask(), 0L, internalReadingPeriod,
            // TimeUnit.MILLISECONDS);

            // retrieve device names from attribute names
            final Set<String> deviceNameList = new HashSet<String>();
            for (final String element : fullAttributeNameList) {
                final String deviceName = TangoUtil.getfullDeviceNameForAttribute(element);
                deviceNameList.add(deviceName);
            }
            logger.debug("doing state composition {}", isStateComposer);
            // configure state composition
            if (isStateComposer) {
                stateReader = new StateResolver(internalReadingPeriod, false);
                stateReader.configurePriorities(statePriorities);
                stateReader.setMonitoredDevices(individualTimeout,
                        deviceNameList.toArray(new String[deviceNameList.size()]));
                stateReader.start(device.getName());
            }

            // create dynamic group command
            createDynamicCommands(deviceNameList);

        }

        @Override
        public void getFallback() throws DevFailed {
            logger.info("init failure fallback");
            partialDelete();
            dynMngt.clearAttributesWithExclude("log");
        }

        @Override
        public void notifyError(final DevFailed e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("INIT FAILED, will retry in a while\n").append(DevFailedUtils.toString(e));
            status = sb.toString();
            logger.error("{}", status);
        }
    }

    @Init(lazyLoading = true)
    public void initDevice() throws DevFailed {
        xlogger.entry();
        logger = LoggerFactory.getLogger(AttributeComposer.class.getSimpleName() + "." + device.getName());
        dynMngt.addAttribute(new org.tango.server.attribute.log.LogAttribute(1000, logger));
        new SimpleCircuitBreaker(new InitCommand()).execute();
        logger.info("init OK");
        xlogger.exit();
    }

    private void createAttributeGroup() throws DevFailed {
        // configure the attribute group

        // set to remove duplications
        final Set<String> set = new LinkedHashSet<String>(Arrays.asList(attributeNameList));
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

        attributeGroup = new TangoGroupAttribute(false,
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
        partialDelete();
        dynMngt.clearAll();
    }

    /**
     * clear to retry init
     *
     * @throws DevFailed
     */
    private void partialDelete() throws DevFailed {
        fullAttributeNameList.clear();
        if (stateReader != null) {
            stateReader.stop();
            stateReader = null;
        }
        if (readScheduler != null) {
            readScheduler.stop();
        }
    }

    /**
     * Execute command "SetAllFormat" on device. This command set the format property eg : %6.3f on all the attributes
     */
    @Command(name = "SetAllFormat")
    public void setAllFormat(final String argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(argin, PropertyType.FORMAT);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllLabel" on device. This command set the Label property on all the attributes
     */
    @Command(name = "SetAllLabel")
    public void setAllLabel(final String argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(argin, PropertyType.LABEL);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllMaxAlarm" on device. This command set the maximum alarm value property on all the
     * attributes
     */
    @Command(name = "SetAllMaxAlarm")
    public void setAllMaxAlarm(final double argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(String.valueOf(argin), PropertyType.ALARM_MAX);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllMaxValue" on device. This command set the maximum value property on all the attributes
     */
    @Command(name = "SetAllMaxValue")
    public void setAllMaxValue(final double argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(String.valueOf(argin), PropertyType.MAX_VAL);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllMinAlarm" on device. This command set the minimum alarm value property on all the
     * attributes
     */
    @Command(name = "SetAllMinAlarm")
    public void setAllMinAlarm(final double argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(String.valueOf(argin), PropertyType.ALARM_MIN);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllMinValue" on device. This command set the minimum value property on all the attributes
     */
    @Command(name = "SetAllMinValue")
    public void setAllMinValue(final double argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(String.valueOf(argin), PropertyType.MIN_VAL);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllUnit" on device. This command set the Unit property eg : Volt on all the attributes
     */
    @Command(name = "SetAllUnit")
    public void setAllUnit(final String argin) throws DevFailed {
        xlogger.entry(argin);
        setAttributeProperty(argin, PropertyType.UNIT);
        xlogger.exit();
    }

    /**
     * Execute command "SetAllValues" on device. This command write the given value on all the attributes
     */
    @Command(name = "SetAllValues")
    public void setAllValues(final double argin) throws DevFailed {
        xlogger.entry(argin);
        attributeGroup.write(argin);
        xlogger.exit();
    }

    private void setAttributeProperty(final String property, final PropertyType type) throws DevFailed {
        xlogger.entry();
        logger.debug("property {} ", property);
        logger.debug("type {} ", type);
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
                    throw DevFailedUtils.newDevFailed("unknown property " + type);
            }
            deviceProxy.set_attribute_info(new AttributeInfo[]{attributeInfo});
        }
        xlogger.exit();
    }

    public double getStd() {
        updateSpectrumResult();
        if (!ArrayUtils.isEmpty(spectrumResult)) {
            std = Math.sqrt(StatUtils.variance(spectrumResult));
        }
        return std;
    }

    public double getMax() {
        updateSpectrumResult();
        if (!ArrayUtils.isEmpty(spectrumResult)) {
            max = StatUtils.max(spectrumResult);
        }
        return max;
    }

    public double getMin() {
        updateSpectrumResult();
        if (!ArrayUtils.isEmpty(spectrumResult)) {
            min = StatUtils.min(spectrumResult);
        }
        return min;
    }

    public boolean isBooleanResult() {
        // calculate value
        getBooleanSpectrum();
        return booleanResult;
    }

    private int getIndexForAttribute(final String attributeName) {
        xlogger.entry();
        int idx = -1;
        for (int i = 0; i < fullAttributeNameList.size(); i++) {
            if (fullAttributeNameList.get(i).trim().equalsIgnoreCase(attributeName)) {
                idx = i;
            }
        }
        xlogger.exit();
        return idx;
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
            throw DevFailedUtils.newDevFailed("INIT_ERROR", "No attribute defined in property");
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

    public void setDevice(final DeviceManager device) {
        this.device = device;
    }

}