package fr.soleil.tango.server.attributecomposer;

import java.util.HashMap;
import java.util.Map;

import org.tango.DeviceState;
import org.tango.server.dynamic.attribute.GroupAttribute;
import org.tango.utils.DevFailedUtils;

import fr.esrf.Tango.AttrQuality;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.AttributeInfoEx;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.soleil.tango.attributecomposer.IAttributeGroupTaskListener;
import fr.soleil.tango.attributecomposer.PriorityQualityManager;
import fr.soleil.tango.clientapi.TangoGroupAttribute;

public final class AttributeComposerReader implements IAttributeGroupTaskListener {

    private final PriorityQualityManager qualityManager;
    /**
     * Dynamic attribute of the device. Read part will be updated here
     */
    private final GroupAttribute attributeToUpdate;
    private final Map<String, String> errorReportMap = new HashMap<String, String>();
    private final Map<String, Double> attributeValueMap = new HashMap<String, Double>();

    private DeviceState state = DeviceState.UNKNOWN;
    private String status = "";

    public AttributeComposerReader(final TangoGroupAttribute attributeGroup, final GroupAttribute attributeToUpdate,
            final PriorityQualityManager qualityManager) {
        this.qualityManager = qualityManager;
        this.attributeToUpdate = attributeToUpdate;
    }

    @Override
    public void updateDeviceAttribute(final DeviceAttribute[] resultGroup) {
        attributeToUpdate.setReadValue(resultGroup);
    }

    @Override
    public void updateReadValue(final String completeAttributeName, final Object value) {
        final double doubleValue = Double.valueOf(value.toString());
        attributeValueMap.put(completeAttributeName, doubleValue);
        errorReportMap.remove(completeAttributeName);
    }

    @Override
    public void updateWriteValue(final String completeAttributeName, final Object value) {
        // Nothing to do
    }

    @Override
    public void updateErrorMessage(final String completeAttributeName, final String errorMessage) {
        errorReportMap.put(completeAttributeName, errorMessage);
        attributeValueMap.put(completeAttributeName, Double.NaN);
    }

    @Override
    public void updateAttributeInfoEx(final String completeAttributeName, final AttributeInfoEx attributeInfo) {
        // Nothing to do
    }

    @Override
    public void updateQuality(final String completeAttributeName, final AttrQuality quality) {
        qualityManager.putAttributeQuality(completeAttributeName, quality);
    }

    @Override
    public void catchException(final Exception exception) {
        if (exception != null) {
            state = DeviceState.FAULT;
            status = "Error,  check attributesResultReport for details:\n " + exception.getMessage();
        }
    }

    @Override
    public void catchDevFailed(final DevFailed exception) {
        if (exception != null) {
            state = DeviceState.FAULT;
            status = "Error,  check attributesResultReport for details:\n " + DevFailedUtils.toString(exception);
        }

    }

    @Override
    public void updateWriteValueErrorMessage(final String completeAttributeName, final String errorMessage) {
        // Nothing to do

    }

    @Override
    public void updateAttributeInfoErrorMessage(final String completeAttributeName, final String errorMessage) {
        // Nothing to do
    }

    @Override
    public void readingLoopFinished() {
        if (errorReportMap.isEmpty()) {
            state = DeviceState.getDeviceState(qualityManager.getHighestPriorityState());
            status = "At least one attribute is of quality " + qualityManager.getHighestPriorityQualityAsString();
        }
    }

    public Map<String, String> getErrorReportMap() {
        return new HashMap<String, String>(errorReportMap);
    }

    public Map<String, Double> getAttributeValueMap() {
        return new HashMap<String, Double>(attributeValueMap);
    }

    public DeviceState getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }

}