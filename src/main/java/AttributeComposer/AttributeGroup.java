package AttributeComposer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.AttributeProxy;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoApi.helpers.AttributeHelper;

public class AttributeGroup {

    private String name = "";
    private int timeOut = 3000;
    private final Map<String, String> attributeNameMap = new HashMap<String, String>();
    private final ArrayList<AttributeGroupReply> attributeGroupReply = new ArrayList<AttributeGroupReply>();

    public AttributeGroup(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void add(final String attributeName) {
        final String name = attributeName.trim();
        final String key = name.toLowerCase();
        if (!attributeNameMap.containsKey(key)) {
            attributeNameMap.put(key, name);
        }
    }

    public void add(final String[] attributeNameList) {
        if (attributeNameList == null || attributeNameList.length == 0
                || attributeNameList[0].trim().isEmpty()) {
            return;
        }

        for (final String element : attributeNameList) {
            add(element);
        }
    }

    public void clear() {
        attributeNameMap.clear();
        attributeGroupReply.clear();
    }

    public ArrayList<AttributeGroupReply> write(final double value) {
        attributeGroupReply.clear();

        if (Double.isNaN(value)) {
            return attributeGroupReply;
        }

        final Collection<String> values = attributeNameMap.values();
        final Iterator<String> iterator = values.iterator();
        String attributeName = null;
        AttributeProxy attributeProxy = null;
        DeviceAttribute deviceAttribute = null;
        while (iterator.hasNext()) {
            attributeName = iterator.next();
            try {
                attributeProxy = new AttributeProxy(attributeName);
                attributeProxy.set_timeout_millis(timeOut);
                deviceAttribute = attributeProxy.read();
                AttributeHelper.insertFromDouble(value, deviceAttribute);
                attributeProxy.write(deviceAttribute);
                attributeGroupReply.add(new AttributeGroupReply(attributeName));
            }
            catch (final DevFailed e) {
                attributeGroupReply.add(new AttributeGroupReply(attributeName, e));
            }
        }
        return attributeGroupReply;
    }

    public void set_timeout_millis(final int timeout) {
        if (timeout > 1000) {
            timeOut = timeout;
        }
    }
}
