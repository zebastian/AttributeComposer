package AttributeComposer;

import java.util.ArrayList;

import fr.esrf.Tango.DevFailed;

public interface AttributeGroupListener {

    public void receiveResponse(ArrayList<AttributeGroupReply> replyList) throws DevFailed;

}
