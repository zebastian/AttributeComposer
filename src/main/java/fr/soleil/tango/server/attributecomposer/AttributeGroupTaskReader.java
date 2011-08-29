package fr.soleil.tango.server.attributecomposer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.tango.DeviceState;
import org.tango.utils.DevFailedUtils;

import fr.esrf.Tango.AttrDataFormat;
import fr.esrf.Tango.AttrQuality;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.soleil.tango.attributecomposer.PriorityQualityManager;
import fr.soleil.tango.clientapi.InsertExtractUtils;
import fr.soleil.tango.clientapi.TangoGroupAttribute;

public class AttributeGroupTaskReader implements Runnable {

    private static final SimpleDateFormat dateInsertformat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private final static XLogger xlogger = XLoggerFactory.getXLogger(AttributeGroupTaskReader.class);
    private final TangoGroupAttribute attributeGroup;
    private final Map<String, String> errorReportMap = new HashMap<String, String>();
    private final Map<String, Double> attributeValueMap = new HashMap<String, Double>();
    private final PriorityQualityManager qualityManager;
    private final String[] attributeNames;

    private DeviceState state = DeviceState.UNKNOWN;
    private String status = "";

    public AttributeGroupTaskReader(final TangoGroupAttribute attributeGroup,
	    final PriorityQualityManager qualityManager) {
	this.attributeGroup = attributeGroup;
	this.qualityManager = qualityManager;
	attributeNames = attributeGroup.getGroup().getAttributeNames();
    }

    public Map<String, String> getErrorReportMap() {
	return new HashMap<String, String>(errorReportMap);
    }

    public DeviceState getState() {
	return state;
    }

    public String getStatus() {
	return status;
    }

    @Override
    public void run() {
	valueReader();
    }

    public void valueReader() {
	xlogger.entry();
	try {
	    DeviceAttribute[] resultGroup = null;
	    // read attributes
	    try {
		resultGroup = attributeGroup.read();
	    } catch (final DevFailed devFailed) {
		state = DeviceState.FAULT;
		status = dateInsertformat.format(new Date()) + " : Cannot read attribute group: \n"
			+ DevFailedUtils.toString(devFailed);
		return;
	    }
	    // extract results
	    boolean tmpHasFailed = false;
	    int i = 0;
	    for (final DeviceAttribute deviceAttribute : resultGroup) {
		final String attrName = attributeNames[i++];
		try {
		    final double tmpReadValue = InsertExtractUtils.extractRead(deviceAttribute, AttrDataFormat.SCALAR,
			    double.class);
		    attributeValueMap.put(attrName, tmpReadValue);
		    qualityManager.putAttributeQuality(attrName, deviceAttribute.getQuality());
		} catch (final DevFailed devFailed) {
		    tmpHasFailed = true;
		    qualityManager.putAttributeQuality(attrName, AttrQuality.ATTR_INVALID);
		    errorReportMap.put(attrName,
			    dateInsertformat.format(new Date()) + " : " + DevFailedUtils.toString(devFailed));
		}
	    }
	    if (tmpHasFailed) {
		state = DeviceState.FAULT;
		status = dateInsertformat.format(new Date()) + " : Error see attributesResultReport";
	    } else {
		state = DeviceState.getDeviceState(qualityManager.getHighestPriorityState());
		status = "At least one attribute is of quality " + qualityManager.getHighestPriorityQualityAsString();
	    }
	} catch (final Exception e) {
	    state = DeviceState.FAULT;
	    status = dateInsertformat.format(new Date()) + " Unexpected error " + e.getClass().getCanonicalName();
	}
	xlogger.exit();
    }

    public Map<String, Double> getAttributeValueMap() {
	return new HashMap<String, Double>(attributeValueMap);
    }
}