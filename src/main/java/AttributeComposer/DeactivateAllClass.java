//+======================================================================
// $Source: /users/chaize/newsvn/cvsroot/Calculation/AttributeComposer/src/main/java/AttributeComposer/DeactivateAllClass.java,v $
//
// Project:      Tango Device Server
//
// Description:  Java source code for the command TemplateClass of the
//               AttributeComposer class.
//
// $Author: abeilleg $
//
// $Revision: 1.3 $
//
// $Log: not supported by cvs2svn $
// Revision 1.2  2011/01/04 10:52:52  gramer
// fix bug http://controle/mantis/view.php?id=17846
//
// Revision 1.1  2007/09/25 16:03:51  katyho
// Use TangORB-5.1.2 that fixe Bug in Group and probl�me in java server.
// Use only group of TangORB
//
// Revision 1.5  2007/03/28 12:21:19  katyho
// Add the version attribute and use the dtu librairie
//
// Revision 1.1  2006/03/17 16:21:33  katyho
// add new Command
//
//
// copyleft :    European Synchrotron Radiation Facility
//               BP 220, Grenoble 38043
//               FRANCE
//
//-======================================================================
//
//  		This file is generated by POGO
//	(Program Obviously used to Generate tango Object)
//
//         (c) - Software Engineering Group - ESRF
//=============================================================================

/**
 * @author	$Author: abeilleg $
 * @version	$Revision: 1.3 $
 */
package AttributeComposer;

import org.omg.CORBA.Any;

import fr.esrf.Tango.DevFailed;
import fr.esrf.Tango.DispLevel;
import fr.esrf.TangoDs.Command;
import fr.esrf.TangoDs.DeviceImpl;
import fr.esrf.TangoDs.Except;
import fr.esrf.TangoDs.TangoConst;
import fr.esrf.TangoDs.Util;

/**
 * Class Description: This command return the priority associated to a given
 * State.
 */
@Deprecated
public class DeactivateAllClass extends Command implements TangoConst {
    // ===============================================================
    /**
     * Constructor for Command class GetPriorityForStateClass
     * 
     * @param name
     *            command name
     * @param in
     *            argin type
     * @param out
     *            argout type
     */
    // ===============================================================
    public DeactivateAllClass(final String name, final int in, final int out) {
	super(name, in, out);
    }

    // ===============================================================
    /**
     * Constructor for Command class GetPriorityForStateClass
     * 
     * @param name
     *            command name
     * @param in
     *            argin type
     * @param in_comments
     *            argin description
     * @param out
     *            argout type
     * @param out_comments
     *            argout description
     */
    // ===============================================================
    public DeactivateAllClass(final String name, final int in, final int out, final String in_comments,
	    final String out_comments) {
	super(name, in, out, in_comments, out_comments);
    }

    // ===============================================================
    /**
     * Constructor for Command class GetPriorityForStateClass
     * 
     * @param name
     *            command name
     * @param in
     *            argin type
     * @param in_comments
     *            argin description
     * @param out
     *            argout type
     * @param out_comments
     *            argout description
     * @param level
     *            The command display type OPERATOR or EXPERT
     */
    // ===============================================================
    public DeactivateAllClass(final String name, final int in, final int out, final String in_comments,
	    final String out_comments, final DispLevel level) {
	super(name, in, out, in_comments, out_comments, level);
    }

    // ===============================================================
    /**
     * return the result of the device's command.
     */
    // ===============================================================
    @Override
    public Any execute(final DeviceImpl device, final Any in_any) throws DevFailed {
	if (!(device instanceof AttributeComposer)) {
	    Except.throw_exception("DEVICE_ERROR", "Device parameter is not instance of AttributeComposer",
		    "AttributeComposer");
	}

	Util.out2.println("DeactivateAllClass.execute(): arrived");
	((AttributeComposer) device).deactivage_all();
	return insert();
    }

    // ===============================================================
    /**
     * Check if it is allowed to execute the command.
     */
    // ===============================================================
    @Override
    public boolean is_allowed(final DeviceImpl device, final Any data_in) {
	// End of Generated Code

	// Re-Start of Generated Code
	return true;
    }
}
// -----------------------------------------------------------------------------
/*
 * end of $Source:
 * /cvsroot/tango-ds/Calculation/AttributeComposer/src/main/java/
 * AttributeComposer/DeactivateAllClass.java,v $
 */
