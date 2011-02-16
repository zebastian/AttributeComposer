package fr.soleil.tango.server.attributecomposer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.tango.server.idl4.DeviceState;

import AttributeComposer.PriorityQualityManager;
import fr.esrf.Tango.AttrQuality;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoApi.Group.GroupAttrReply;
import fr.esrf.TangoApi.Group.GroupAttrReplyList;
import fr.soleil.device.utils.AttributeHelper;
import fr.soleil.tango.clientapi.TangoGroupAttribute;
import fr.soleil.tango.util.TangoUtil;

public class ValueReader implements Runnable {

    private static final SimpleDateFormat dateInsertformat = new SimpleDateFormat(
	    "dd-MM-yyyy HH:mm:ss");
    private final static XLogger xlogger = XLoggerFactory.getXLogger(AttributeComposer.class);
    TangoGroupAttribute attributeGroup;
    private final Map<String, String> attributeResultReportMap = new HashMap<String, String>();
    private final Map<String, Double> attributeValueMap = new HashMap<String, Double>();
    PriorityQualityManager qualityManager;

    private DeviceState state;
    private String status = null;

    public ValueReader(TangoGroupAttribute attributeGroup, PriorityQualityManager qualityManager) {
	this.attributeGroup = attributeGroup;
	this.qualityManager = qualityManager;
    }

    public Map<String, String> getAttributeResultReportMap() {
	return attributeResultReportMap;
    }

    public Map<String, Double> getAttributeValueMap() {
	return attributeValueMap;
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
	GroupAttrReplyList resultGroup = null;
	// read attributes
	try {
	    resultGroup = attributeGroup.read();
	} catch (final DevFailed devFailed) {
	    state = DeviceState.FAULT;
	    status = dateInsertformat.format(new Date()) + " : Unexpected Error, cannot read: \n"
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
		System.out.println("oneresult = " + oneResult.obj_name());
		deviceAttribute = oneResult.get_data();
		double tmpReadValue = Double.NaN;
		tmpReadValue = AttributeHelper.extractToDouble(deviceAttribute);
		quality = deviceAttribute.getQuality();
		attributeValueMap.put(deviceName + "/" + attrName, tmpReadValue);
		qualityManager.putAttributeQuality(deviceName + "/" + attrName, quality);
	    } catch (final DevFailed devFailed) {
		tmpHasFailed = true;
		qualityManager.putAttributeQuality(deviceName + "/" + attrName, quality);
		attributeResultReportMap.put(
			deviceName + "/" + attrName,
			dateInsertformat.format(new Date()) + " : "
				+ TangoUtil.getDevFailedString(devFailed));
		devFailed.printStackTrace();
	    }
	}
	if (tmpHasFailed) {
	    state = DeviceState.FAULT;
	    status = dateInsertformat.format(new Date()) + " : Error see attributesResultReport";
	} else {
	    state = DeviceState.getDeviceState(qualityManager.getHighestPriorityState());
	    status = "At least one attribute is of quality "
		    + qualityManager.getHighestPriorityQualityAsString();
	}
	xlogger.exit();
    }
}