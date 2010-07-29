package AttributeComposer;

import fr.esrf.Tango.DevFailed;


public class AttributeGroupReply {

    private String attributeName = "";
    private boolean hasFailed = false;
    private DevFailed devFailed = null;

    public AttributeGroupReply(final String name) {
        attributeName = name;
    }

    public AttributeGroupReply(final String name, final DevFailed error) {
        attributeName = name;
        hasFailed = true;
        devFailed = error;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public boolean hasFailed() {
        return hasFailed;
    }

    public DevFailed getDevFailed() {
        return devFailed;
    }

}
