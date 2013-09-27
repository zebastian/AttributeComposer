package fr.soleil.tango.server.attributecomposer;

import java.util.HashMap;
import java.util.Map;

import org.tango.DeviceState;
import org.tango.server.dynamic.attribute.GroupAttribute;

import fr.esrf.Tango.AttrQuality;
import fr.esrf.TangoApi.AttributeInfoEx;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoApi.Group.AttributeGroupTaskReader;
import fr.esrf.TangoApi.Group.IAttributeGroupTaskListener;
import fr.soleil.tango.attributecomposer.PriorityQualityManager;
import fr.soleil.tango.clientapi.TangoGroupAttribute;

public final class AttributeComposerReader implements IAttributeGroupTaskListener {

    private final PriorityQualityManager qualityManager;
    private final GroupAttribute attributeToUpdate;
    private final Map<String, String> errorReportMap = new HashMap<String, String>();
    private final Map<String, Double> attributeValueMap = new HashMap<String, Double>();
    private final Runnable task;


    private DeviceState state = DeviceState.UNKNOWN;
    private String status = "";

    public AttributeComposerReader(final TangoGroupAttribute attributeGroup, final GroupAttribute attributeToUpdate,
            final PriorityQualityManager qualityManager) {
        this.qualityManager = qualityManager;
        this.attributeToUpdate = attributeToUpdate;
        task = new AttributeGroupTaskReader(this, attributeGroup, false, true, false);
    }

    public Runnable getTask() {
        return task;
    }

    @Override
    public void updateDeviceAttribute(DeviceAttribute[] resultGroup) {
        attributeToUpdate.setReadValue(resultGroup);
    }

    @Override
    public void updateReadValue(String completeAttributeName, double value) {
        attributeValueMap.put(completeAttributeName, value);
        errorReportMap.remove(completeAttributeName);
    }

    @Override
    public void updateWriteValue(String completeAttributeName, double value) {
        // Nothing to do

    }

    @Override
    public void updateErrorMessage(String completeAttributeName, String errorMessage) {
        errorReportMap.put(completeAttributeName, errorMessage);
    }

    @Override
    public void updateAttributeInfoEx(String completeAttributeName, AttributeInfoEx attributeInfo) {
        // Nothing to do
    }

    @Override
    public void updateQuality(String completeAttributeName, AttrQuality quality) {
        qualityManager.putAttributeQuality(completeAttributeName, quality);
    }

    @Override
    public void catchException(Exception exception) {
        if (exception != null) {
            state = DeviceState.FAULT;
            status = exception.getMessage() + "\nError see attributesResultReport";
        }
    }

    @Override
    public void readingLoopFinished() {
        state = DeviceState.getDeviceState(qualityManager.getHighestPriorityState());
        status = "At least one attribute is of quality " + qualityManager.getHighestPriorityQualityAsString();
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