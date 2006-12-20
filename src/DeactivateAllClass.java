//+======================================================================
// $Source: /users/chaize/newsvn/cvsroot/Calculation/AttributeComposer/src/DeactivateAllClass.java,v $
//
// Project:      Tango Device Server
//
// Description:  Java source code for the command TemplateClass of the
//               AttributeComposer class.
//
// $Author: katyho $
//
// $Revision: 1.4 $
//
// $Log: not supported by cvs2svn $
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
 * @author	$Author: katyho $
 * @version	$Revision: 1.4 $
 */
package AttributeComposer;



import org.omg.CORBA.*;
import fr.esrf.Tango.*;
import fr.esrf.TangoDs.*;

/**
 *	Class Description:
 *	This command return the priority associated to a given State.
*/


public class DeactivateAllClass extends Command implements TangoConst
{
	//===============================================================
	/**
	 *	Constructor for Command class GetPriorityForStateClass
	 *
	 *	@param	name	command name
	 *	@param	in	argin type
	 *	@param	out	argout type
	 */
	//===============================================================
	public DeactivateAllClass(String name,int in,int out)
	{
		super(name, in, out);
	}

	//===============================================================
	/**
	 *	Constructor for Command class GetPriorityForStateClass
	 *
	 *	@param	name            command name
	 *	@param	in              argin type
	 *	@param	in_comments     argin description
	 *	@param	out             argout type
	 *	@param	out_comments    argout description
	 */
	//===============================================================
	public DeactivateAllClass(String name,int in,int out, String in_comments, String out_comments)
	{
		super(name, in, out, in_comments, out_comments);
	}
	//===============================================================
	/**
	 *	Constructor for Command class GetPriorityForStateClass
	 *
	 *	@param	name            command name
	 *	@param	in              argin type
	 *	@param	in_comments     argin description
	 *	@param	out             argout type
	 *	@param	out_comments    argout description
	 *	@param	level           The command display type OPERATOR or EXPERT
	 */
	//===============================================================
	public DeactivateAllClass(String name,int in,int out, String in_comments, String out_comments, DispLevel level)
	{
		super(name, in, out, in_comments, out_comments, level);
	}
	//===============================================================
	/**
	 *	return the result of the device's command.
	 */
	//===============================================================
	public Any execute(DeviceImpl device,Any in_any) throws DevFailed
	{
		Util.out2.println("DeactivateAllClass.execute(): arrived");
		((AttributeComposer)(device)).deactivage_all();
		return insert();
	}

	//===============================================================
	/**
	 *	Check if it is allowed to execute the command.
	 */
	//===============================================================
	public boolean is_allowed(DeviceImpl device, Any data_in)
	{
			//	End of Generated Code

			//	Re-Start of Generated Code
		return true;
	}
}
//-----------------------------------------------------------------------------
/* end of $Source: /users/chaize/newsvn/cvsroot/Calculation/AttributeComposer/src/DeactivateAllClass.java,v $ */
