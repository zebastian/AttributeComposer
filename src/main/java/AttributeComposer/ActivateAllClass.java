//+======================================================================
// $Source: /users/chaize/newsvn/cvsroot/Calculation/AttributeComposer/src/main/java/AttributeComposer/ActivateAllClass.java,v $
//
// Project:      Tango Device Server
//
// Description:  Java source code for the command TemplateClass of the
//               AttributeComposer class.
//
// $Author: gramer $
//
// $Revision: 1.2 $
//
// $Log: not supported by cvs2svn $
// Revision 1.1  2007/09/25 16:03:51  katyho
// Use TangORB-5.1.2 that fixe Bug in Group and probl�me in java server.
// Use only group of TangORB
//
// Revision 1.5  2007/03/28 12:21:18  katyho
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
 * @author	$Author: gramer $
 * @version	$Revision: 1.2 $
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

public class ActivateAllClass extends Command implements TangoConst {
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
	public ActivateAllClass(String name, int in, int out) {
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
	public ActivateAllClass(String name, int in, int out, String in_comments, String out_comments) {
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
	public ActivateAllClass(String name, int in, int out, String in_comments, String out_comments, DispLevel level) {
		super(name, in, out, in_comments, out_comments, level);
	}

	// ===============================================================
	/**
	 * return the result of the device's command.
	 */
	// ===============================================================
	@Override
	public Any execute(DeviceImpl device, Any in_any) throws DevFailed {
		if (!(device instanceof AttributeComposer)) {
			Except.throw_exception("DEVICE_ERROR", "Device parameter is not instance of AttributeComposer", "AttributeComposer");
		}
		Util.out2.println("ActivateAllClass.execute(): arrived");
		((AttributeComposer) (device)).activate_all();
		return insert();
	}

	// ===============================================================
	/**
	 * Check if it is allowed to execute the command.
	 */
	// ===============================================================
	@Override
	public boolean is_allowed(DeviceImpl device, Any data_in) {
		// End of Generated Code

		// Re-Start of Generated Code
		return true;
	}
}
// -----------------------------------------------------------------------------
/*
 * end of $Source:
 * /cvsroot/tango-ds/Calculation/AttributeComposer/src/main/java/
 * AttributeComposer/ActivateAllClass.java,v $
 */
