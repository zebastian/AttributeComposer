package fr.soleil.tango.server.attributecomposer;

import fr.esrf.Tango.AttrDataFormat;
import fr.esrf.Tango.AttrWriteType;
import fr.esrf.Tango.AttributeDataType;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoDs.TangoConst;
import fr.soleil.tango.clientapi.InsertExtractUtils;
import fr.soleil.tango.clientapi.TangoGroupAttribute;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.tango.server.StateMachineBehavior;
import org.tango.server.attribute.AttributeConfiguration;
import org.tango.server.attribute.AttributePropertiesImpl;
import org.tango.server.attribute.AttributeValue;
import org.tango.server.attribute.IAttributeBehavior;
import org.tango.server.dynamic.attribute.GroupAttribute;
import org.tango.utils.DevFailedUtils;
import org.tango.utils.TangoUtil;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Composed all underlying attributes
 */
public class ComposedAttribute implements IAttributeBehavior {
    AttributeConfiguration configuration = new AttributeConfiguration();
    private final static String ATTR_NAME = "composedValues";
    private AttributeComposer reader;

    private final String name;
    private final TangoGroupAttribute attributeGroup;
    private AttrDataFormat attributeFormat;
    private AttrWriteType attributeWritable;
    private final String[] attributeNames;
    private final boolean isExternalRead;
    private DeviceAttribute[] readValues;

    public ComposedAttribute(final String groupAttributeName, final boolean isExternalRead, final String... attributeNames) throws DevFailed {
        name = groupAttributeName;
        this.attributeNames = attributeNames;
        this.isExternalRead = isExternalRead;
        attributeGroup = new TangoGroupAttribute(attributeNames);
        for (int i = 0; i < attributeNames.length; i++) {
            final AttributeInfo info = attributeGroup.getGroup().getDevice(attributeNames[i])
                    .get_attribute_info(TangoUtil.getAttributeName(attributeNames[i]));
            if (info.data_type == TangoConst.Tango_DEV_STRING) {
                throw DevFailedUtils.newDevFailed(attributeNames[i] + " is a String, not supported");
            }
            if (i == 0) {
                if (info.data_format.equals(AttrDataFormat.SCALAR))
                    attributeFormat = AttrDataFormat.SPECTRUM;
                else if (info.data_format.equals(AttrDataFormat.SPECTRUM)) {
                    attributeFormat = AttrDataFormat.IMAGE;
                } else {
                    throw DevFailedUtils.newDevFailed("image format not supported");
                }
            } else {
                attributeWritable = AttrWriteType.READ;
            }
        }

    }

    @Override
    public AttributeConfiguration getConfiguration() throws DevFailed {
        final AttributeConfiguration config = new AttributeConfiguration();
        config.setName(name);
        config.setTangoType(TangoConst.Tango_DEV_DOUBLE, attributeFormat);
        config.setWritable(attributeWritable);
        final AttributePropertiesImpl props = new AttributePropertiesImpl();
        props.setDescription("manage attributes: " + Arrays.toString(attributeNames)
                + "\nread part: average for scalars, write part: write on all writable attributes");
        config.setAttributeProperties(props);
        return config;
    }

    @Override
    public AttributeValue getValue() throws DevFailed {
        AttributeValue value = null;
        final DeviceAttribute[] result;
        if (isExternalRead) {
            result = readValues;
        } else {
            result = attributeGroup.read();
        }
        // calculate average
        if (attributeFormat.equals(AttrDataFormat.SCALAR)) {
            final double[] data = new double[result.length];
            for (int i = 0; i < result.length; i++) {
                data[i] = InsertExtractUtils.extractRead(result[i], AttrDataFormat.SCALAR, double.class);
            }
            value = new AttributeValue(data);
        } else {
            final List<double[]> values = new LinkedList<double[]>();
            int maxLength = 0;
            int maxDimX = 0;
            int maxDimY = 0;
            for (final DeviceAttribute element : result) {
                final double[] data = InsertExtractUtils.extractRead(element, AttrDataFormat.SPECTRUM, double[].class);
                values.add(data);
                // each attribute may have a different size, finding max size
                if (data.length > maxLength) {
                    maxLength = data.length;
                }
                if (element.getDimX() > maxDimX) {
                    maxDimX = element.getDimX();
                }
                if (element.getDimY() > maxDimY) {
                    maxDimY = element.getDimY();
                }
            }
            final double[][] array = new double[maxLength][attributeNames.length];
            for (int i = 0; i < attributeNames.length; i++) {
                final double[] attrValue = values.get(i);
                double[] totalArray = attrValue;
                if (attrValue.length < maxLength) {
                    // put some trailing 0 if not maxLength
                    final double[] fillArray = new double[maxLength - attrValue.length];
                    Arrays.fill(fillArray, 0);
                    totalArray = ArrayUtils.addAll(attrValue, fillArray);
                }

                for (int x = 0; x < maxLength; x++) {
                    array[x][i] = totalArray[x];
                }
            }
            value = new AttributeValue(array);
        }
        return value;
    }

    public void setReadValue(final DeviceAttribute[] readValues) {
        this.readValues = Arrays.copyOf(readValues, readValues.length);
    }

    @Override
    public void setValue(AttributeValue attributeValue) throws DevFailed {

    }

    @Override
    public StateMachineBehavior getStateMachine() throws DevFailed {
        return null;
    }
}
