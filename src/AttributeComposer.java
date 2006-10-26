//Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
//Jad home page: http://www.kpdus.com/jad.html
//Decompiler options: packimports(3) 
//Source File Name:   AttributeComposer.java

package AttributeComposer;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.UserException;

import fr.esrf.Tango.AttrQuality;
import fr.esrf.Tango.AttrWriteType;
import fr.esrf.Tango.DevFailed;
import fr.esrf.Tango.DevState;
import fr.esrf.Tango.DispLevel;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.Database;
import fr.esrf.TangoApi.DbDatum;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoApi.DeviceProxy;
import fr.esrf.TangoApi.Group.Group;
import fr.esrf.TangoApi.Group.GroupAttrReply;
import fr.esrf.TangoApi.Group.GroupAttrReplyList;
import fr.esrf.TangoDs.Attr;
import fr.esrf.TangoDs.Attribute;
import fr.esrf.TangoDs.DeviceClass;
import fr.esrf.TangoDs.DeviceImpl;
import fr.esrf.TangoDs.Except;
import fr.esrf.TangoDs.InitCmd;
import fr.esrf.TangoDs.SpectrumAttr;
import fr.esrf.TangoDs.StateCmd;
import fr.esrf.TangoDs.StatusCmd;
import fr.esrf.TangoDs.TangoConst;
import fr.esrf.TangoDs.UserDefaultAttrProp;
import fr.esrf.TangoDs.Util;

//Referenced classes of package AttributeComposer:
//         GetAttributeNameForIndexClass, GetTangoQualitiesClass, GetPriorityForQualityClass, GetLogicalBooleanClass, 
//         SetAllValuesClass, SetPropertyClass, ActivateAllClass, DeactivateAllClass

public class AttributeComposer extends DeviceImpl  implements TangoConst
{

protected int state;
protected double attr_spectrumResult_read[];
protected short attr_booleanSpectrum_read[];
protected String attr_attributesQualityList_read[];
protected short attr_attributesNumberPriorityList_read[];
protected short attr_booleanResult;
private static final String NONE = "NONE";
private static final String OR = "OR";
private static final String AND = "AND";
private static final String XOR = "XOR";
String attributeNameList[];
String priorityList[];
String logicalBoolean;
private Hashtable m_qualityTable;
private Hashtable m_priorityTable;
private Hashtable m_resumQualityTable;
private Hashtable m_resumpriorityTable;
private Hashtable m_stateQualityTable;
private Hashtable m_booleanLogical;
private Hashtable m_groupTable;
private Hashtable m_attributeValueTable;
private Hashtable m_attributeQualityTable;
private static final String stringQualityList[] = {
    "VALID", "CHANGING", "WARNING", "ALARM", "INVALID"
};
private static final String logicalChoices[] = {
    "NONE", "OR", "AND", "XOR"
};
private double sentValue;
private String sentProperty;
private boolean initialized;
private Thread myStateReader = null;
private Thread myValueReader = null;
private Thread myStateUpdater = null;
private Thread myValueUpdater = null;




 AttributeComposer(DeviceClass cl, String s)
     throws DevFailed
 {
     super(cl, s);
     attr_spectrumResult_read = new double[10000];
     attr_booleanSpectrum_read = new short[10000];
     attr_attributesQualityList_read = new String[1000];
     attr_attributesNumberPriorityList_read = new short[1000];
     attr_booleanResult = 0;
     logicalBoolean = "NONE";
     m_qualityTable = new Hashtable();
     m_priorityTable = new Hashtable();
     m_resumQualityTable = new Hashtable();
     m_resumpriorityTable = new Hashtable();
     m_stateQualityTable = new Hashtable();
     m_booleanLogical = new Hashtable();
     m_groupTable = new Hashtable();
     m_attributeValueTable= new Hashtable();
     m_attributeQualityTable= new Hashtable();
     sentValue = 0.0D;
     sentProperty = "";
     initialized = false;
     init_device();
 }

 AttributeComposer(DeviceClass cl, String s, String d)
     throws DevFailed
 {
     super(cl, s, d);
     attr_spectrumResult_read = new double[10000];
     attr_booleanSpectrum_read = new short[10000];
     attr_attributesQualityList_read = new String[1000];
     attr_attributesNumberPriorityList_read = new short[1000];
     attr_booleanResult = 0;
     logicalBoolean = "NONE";
     m_qualityTable = new Hashtable();
     m_priorityTable = new Hashtable();
     m_resumQualityTable = new Hashtable();
     m_resumpriorityTable = new Hashtable();
     m_stateQualityTable = new Hashtable();
     m_booleanLogical = new Hashtable();
     m_groupTable = new Hashtable();
     m_attributeValueTable= new Hashtable();
     m_attributeQualityTable= new Hashtable();
     sentValue = 0.0D;
     sentProperty = "";
     initialized = false;
     init_device();
 }

 public void init_device()
     throws DevFailed
 {
     System.out.println("AttributeComposer() create " + device_name);
     try
     {
         clearAll();
         get_device_class().get_command_list().add(new InitCmd("Init", 0, 0));
         get_device_class().get_command_list().add(new StateCmd("State", 0, 19, "Device state"));
         get_device_class().get_command_list().add(new StatusCmd("Status", 0, 8, "Device status"));
         get_device_class().get_command_list().add(new GetAttributeNameForIndexClass("GetAttributeNameForIndex", 2, 8, "The index of the spectrum data", "The attributeName corresponding to the argin index", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new GetTangoQualitiesClass("GetTangoQualities", 0, 16, "", "The list of the qualities", DispLevel.EXPERT));
         get_device_class().get_command_list().add(new GetPriorityForQualityClass("GetPriorityForQuality", 8, 2, "The qualities name (ex:VALID, ALARM)", "The priority of the quality", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new GetLogicalBooleanClass("GetLogicalChoices", 0, 16, "", "The list of the logical choice for LogicalBoolean property", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetAllValuesClass("SetAllValues", 5, 0, "", "Set given value on all the attribute", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllFormat", 8, 0, "The format of all the attribute", "", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllUnit", 8, 0, "The unit of all the attribute", "", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllMinValue", 5, 0, "The unit of all the attribute", "", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllMaxValue", 5, 0, "The unit of all the attribute", "", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllMinAlarm", 8, 0, "The unit of all the attribute", "", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllMaxAlarm", 8, 0, "The unit of all the attribute", "", DispLevel.OPERATOR));
         get_device_class().get_command_list().add(new SetPropertyClass("SetAllLabel", 8, 0, "The unit of all the attribute", "", DispLevel.OPERATOR));
         set_state(DevState.STANDBY);
         m_qualityTable.put(AttrQuality.ATTR_VALID, "VALID");
         m_qualityTable.put(AttrQuality.ATTR_CHANGING, "CHANGING");
         m_qualityTable.put(AttrQuality.ATTR_WARNING, "WARNING");
         m_qualityTable.put(AttrQuality.ATTR_ALARM, "ALARM");
         m_qualityTable.put(AttrQuality.ATTR_INVALID, "INVALID");
         priorityList = new String[m_qualityTable.size()];
         m_priorityTable.put(AttrQuality.ATTR_VALID, new Integer(2));
         m_priorityTable.put(AttrQuality.ATTR_CHANGING, new Integer(0));
         m_priorityTable.put(AttrQuality.ATTR_WARNING, new Integer(3));
         m_priorityTable.put(AttrQuality.ATTR_ALARM, new Integer(3));
         m_priorityTable.put(AttrQuality.ATTR_INVALID, new Integer(4));
         Enumeration enum = m_qualityTable.keys();
         for(int index = 0; enum.hasMoreElements(); index++)
         {
             AttrQuality key = (AttrQuality)enum.nextElement();
             String value = ((Integer)m_priorityTable.get(key)).toString();
             priorityList[index] = m_qualityTable.get(key) + "," + value;
         }

         get_device_property();
         for(int i = 0; i < priorityList.length; i++)
         {
             StringTokenizer token = new StringTokenizer(priorityList[i].trim(), ",");
             if(token.countTokens() == 2)
             {
                 String state = token.nextToken().trim().toUpperCase();
                 Integer prio = new Integer(0);
                 try
                 {
                     prio = Integer.valueOf(token.nextToken().trim());
                 }
                 catch(NumberFormatException numberformatexception) { }
                 if(m_qualityTable.containsValue(state))
                     m_priorityTable.put(getQualityForName(state), prio);
             }
         }

         AttrQuality key;
         Integer value;
         for(enum = m_qualityTable.keys(); enum.hasMoreElements(); m_resumpriorityTable.put(value, key))
         {
             key = (AttrQuality)enum.nextElement();
             value = (Integer)m_priorityTable.get(key);
         }

         m_stateQualityTable.put(AttrQuality.ATTR_CHANGING, DevState.MOVING);
         m_stateQualityTable.put(AttrQuality.ATTR_ALARM, DevState.ALARM);
         m_stateQualityTable.put(AttrQuality.ATTR_WARNING, DevState.ALARM);
         m_stateQualityTable.put(AttrQuality.ATTR_INVALID, DevState.UNKNOWN);
         m_stateQualityTable.put(AttrQuality.ATTR_VALID, DevState.ON);
         for(int i = 0; i < attributeNameList.length; i++)
         {
             String completeAttributeName = attributeNameList[i].trim();
             String attributeName = "";
             String deviceName = "";
             int pos = completeAttributeName.lastIndexOf("/");
             if(pos != -1)
             {
                 deviceName = completeAttributeName.substring(0, pos);
                 attributeName = completeAttributeName.substring(pos + 1, completeAttributeName.length());
             }
             if(!m_groupTable.containsKey(attributeName))
             {
                 Group newGroup = new Group(attributeName);
                 newGroup.add(deviceName);
                 m_groupTable.put(attributeName, newGroup);
             } else
             {
                 Group newGroup = (Group)m_groupTable.get(attributeName);
                 newGroup.add(deviceName);
             }
         }

         attr_spectrumResult_read = new double[attributeNameList.length];
         attr_booleanSpectrum_read = new short[attributeNameList.length];
         attr_attributesQualityList_read = new String[attributeNameList.length];
         attr_attributesNumberPriorityList_read = new short[attributeNameList.length];
         for(int i = 0; i < attr_spectrumResult_read.length; i++)
         {
             attr_spectrumResult_read[i] = (0.0D / 0.0D);
             attr_booleanSpectrum_read[i] = 0;
             attr_attributesQualityList_read[i] = (String)m_qualityTable.get(AttrQuality.ATTR_INVALID) + "-" + attributeNameList[i];
             attr_attributesNumberPriorityList_read[i] = ((Integer)m_priorityTable.get(AttrQuality.ATTR_INVALID)).shortValue();
         }

         if(!logicalBoolean.equals("NONE"))
         {
             SpectrumAttr attributeSpc = new SpectrumAttr("booleanSpectrum", 2, 10000);
             UserDefaultAttrProp props = new UserDefaultAttrProp();
             props.set_label("booleanSpectrum");
             props.set_description("Spectrum of boolean value");
             attributeSpc.set_default_properties(props);
             add_attribute(attributeSpc);
             Attr attribute = new Attr("booleanResult", Tango_DEV_BOOLEAN, AttrWriteType.READ);
             UserDefaultAttrProp props2 = new UserDefaultAttrProp();
             props2.set_label("booleanResult");
             props2.set_description("Application of the logical operator " + logicalBoolean + "on booleanSpectrum attribute");
             props2.set_format("%1d");
             props2.set_min_value("-1");
             props2.set_max_value("2");
             attribute.set_default_properties(props2);
             add_attribute(attribute);
             ActivateAllClass activateClass = new ActivateAllClass("ActivateAll", 0, 0, "", "Activate All attributes", DispLevel.OPERATOR);
             get_device_class().get_command_list().add(activateClass);
             DeactivateAllClass deactivateClass = new DeactivateAllClass("DeactivateAll", 0, 0, "", "Deactivate All attributes", DispLevel.OPERATOR);
             get_device_class().get_command_list().add(deactivateClass);
             set_state(DevState.RUNNING);
             set_status("Device is processing...");
         }
         initialized = true;
     }
     catch(Exception exception)
     {
         initialized = false;
         set_state(DevState.FAULT);
         set_status("Device is not initialzed properly :\n" + exception.getMessage());
     }
 }

 public void clearAll()
 {
     m_qualityTable.clear();
     m_priorityTable.clear();
     m_resumQualityTable.clear();
     m_resumpriorityTable.clear();
     m_stateQualityTable.clear();
     m_attributeValueTable.clear();
     m_attributeQualityTable.clear();
     myStateReader = null;
     myValueReader = null;
     myStateUpdater = null;
     myValueUpdater = null;
     get_device_class().get_command_list().removeAllElements();
 }

 public AttrQuality getQualityForName(String qualitytmp)
 {
     try
     {
         qualitytmp = "ATTR_" + qualitytmp.trim().toUpperCase();
         AttrQuality attrQualitytmp = (AttrQuality)fr.esrf.Tango.AttrQuality.class.getField(qualitytmp).get(this);
         return attrQualitytmp;
     }
     catch(Exception e)
     {
         return AttrQuality.ATTR_INVALID;
     }
 }

 private int get_index_for_attribute(String attrName)
 {
     int argout = -1;
     for(int i = 0; i < attributeNameList.length; i++)
         if(attributeNameList[i].trim().equalsIgnoreCase(attrName))
             return i;

     return argout;
 }

 public void get_device_property()
     throws DevFailed
 {
     if(!Util._UseDb)
         return;
     String propnames[] = {
         "AttributeNameList", "PriorityList", "LogicalBoolean"
     };
     DbDatum dev_prop[] = get_db_device().get_property(propnames);
     int i = -1;
     if(!dev_prop[++i].is_empty())
     {
         attributeNameList = dev_prop[i].extractStringArray();
     } else
     {
         DbDatum dev_prop1 = get_db_device().get_property("AttributeNameList");
         dev_prop1.insert(new String[0]);
         get_db_device().put_property(new DbDatum[] {
             dev_prop1
         });
         attributeNameList = new String[0];
     }
     if(!dev_prop[++i].is_empty())
     {
         String priorityListtmp[] = dev_prop[i].extractStringArray();
         if(priorityListtmp.length == priorityList.length && priorityListtmp.length == 5)
         {
             if(priorityListtmp[0].indexOf(",") == -1)
             {
                 priorityList[0] = m_qualityTable.get(AttrQuality.ATTR_VALID) + "," + priorityListtmp[0].trim();
                 priorityList[1] = m_qualityTable.get(AttrQuality.ATTR_CHANGING) + "," + priorityListtmp[1].trim();
                 priorityList[2] = m_qualityTable.get(AttrQuality.ATTR_WARNING) + "," + priorityListtmp[2].trim();
                 priorityList[3] = m_qualityTable.get(AttrQuality.ATTR_ALARM) + "," + priorityListtmp[3].trim();
                 priorityList[4] = m_qualityTable.get(AttrQuality.ATTR_INVALID) + "," + priorityListtmp[4].trim();
             } else
             {
                 priorityList = priorityListtmp;
             }
             DbDatum dev_prop1 = get_db_device().get_property("PriorityList");
             dev_prop1.insert(priorityList);
             get_db_device().put_property(new DbDatum[] {
                 dev_prop1
             });
         } else
         if(priorityListtmp.length == 1 && priorityListtmp[0].trim().equals(""))
         {
             DbDatum dev_prop1 = get_db_device().get_property("PriorityList");
             dev_prop1.insert(priorityList);
             get_db_device().put_property(new DbDatum[] {
                 dev_prop1
             });
         } else
         {
             AttrQuality key;
             for(Enumeration tempkeys = m_qualityTable.keys(); tempkeys.hasMoreElements(); m_priorityTable.put(key, new Integer(0)))
                 key = (AttrQuality)tempkeys.nextElement();

             for(int j = 0; j < priorityListtmp.length; j++)
             {
                 StringTokenizer token = new StringTokenizer(priorityListtmp[j].trim(), ",");
                 if(token.countTokens() == 2)
                 {
                     String state = token.nextToken().trim().toUpperCase();
                     Integer prio = new Integer(0);
                     try
                     {
                         prio = Integer.valueOf(token.nextToken().trim());
                     }
                     catch(NumberFormatException numberformatexception) { }
                     if(m_qualityTable.containsValue(state))
                         m_priorityTable.put(getQualityForName(state), prio);
                 }
             }

             Enumeration enum = m_priorityTable.keys();
             for(int index = 0; enum.hasMoreElements(); index++)
             {
                 key = (AttrQuality)enum.nextElement();
                 Integer value = (Integer)m_priorityTable.get(key);
                 priorityList[index] = m_qualityTable.get(key) + "," + value.toString();
             }

             DbDatum dev_prop1 = get_db_device().get_property("PriorityList");
             dev_prop1.insert(priorityList);
             get_db_device().put_property(new DbDatum[] {
                 dev_prop1
             });
         }
     } else
     {
         DbDatum dev_prop1 = get_db_device().get_property("PriorityList");
         dev_prop1.insert(priorityList);
         get_db_device().put_property(new DbDatum[] {
             dev_prop1
         });
     }
     if(!dev_prop[++i].is_empty())
     {
         logicalBoolean = dev_prop[i].extractString();
         if(logicalBoolean.trim().equals(""))
             logicalBoolean = "NONE";
     } else
     {
         DbDatum dev_prop1 = get_db_device().get_property("LogicalBoolean");
         dev_prop1.insert(logicalBoolean);
         get_db_device().put_property(new DbDatum[] {
             dev_prop1
         });
     }
 }

 public short get_priority_for_quality(String argin)
     throws DevFailed
 {
     short argout = 0;
     get_logger().info("Entering get_priority_for_state()");
     AttrQuality attrQualitytmp = getQualityForName(argin);
     argout = ((Integer)m_priorityTable.get(attrQualitytmp)).shortValue();
     get_logger().info("Exiting get_priority_for_state()");
     return argout;
 }

 public String[] get_tango_qualities()
     throws DevFailed
 {
     get_logger().info("Entering get_tango_states()");
     get_logger().info("Exiting get_tango_states()");
     return stringQualityList;
 }

 public String[] get_logical_boolean()
     throws DevFailed
 {
     get_logger().info("Entering get_tango_states()");
     get_logger().info("Exiting get_tango_states()");
     return logicalChoices;
 }

 public void always_executed_hook()
 {
     get_logger().info("In always_executed_hook method()");
    
     if(initialized && (myStateReader == null || !myStateReader.isAlive()))
     {
         myStateReader = new StateReader();
         myStateReader.start();
     }
     
     if(myStateUpdater == null || !myStateUpdater.isAlive())
     {
         myStateUpdater = new StateUpdater();
         myStateUpdater.start();
     }
  }

 public void read_attr_hardware(Vector attr_list)
 {
     get_logger().info("In read_attr_hardware for " + attr_list.size() + " attribute(s)");
     if(initialized && (myValueReader == null || !myValueReader.isAlive()))
     {
         myValueReader = new ValueReader();
         myValueReader.start();
         m_booleanLogical.clear();
         for(int i = 0; i < attr_booleanSpectrum_read.length; i++)
         {
             Boolean boolVal = new Boolean(false);
             if(attr_booleanSpectrum_read[i] == 1)
                 boolVal = new Boolean(true);
             m_booleanLogical.put(boolVal, boolVal);
             if(m_booleanLogical.size() == 2)
                 return;
         }

     }
     
     if(myValueUpdater == null || !myValueUpdater.isAlive())
     {
         myValueUpdater = new ValueUpdater();
         myValueUpdater.start();
     }
 }

 public void read_attr(Attribute attr)
     throws DevFailed
 {
     String attr_name = attr.get_name();
     get_logger().info("In read_attr for attribute " + attr_name);
     if(attr_name == "spectrumResult")
     {
         attr.set_quality(AttrQuality.ATTR_INVALID);
         attr.set_value(attr_spectrumResult_read, attr_spectrumResult_read.length);
     }
     if(attr_name == "attributesQualityList")
         attr.set_value(attr_attributesQualityList_read, attr_attributesQualityList_read.length);
     else
     if(attr_name == "attributesNumberPriorityList")
         attr.set_value(attr_attributesNumberPriorityList_read, attr_attributesNumberPriorityList_read.length);
     else
     if(attr_name == "booleanSpectrum")
         attr.set_value(attr_booleanSpectrum_read, attr_booleanSpectrum_read.length);
     else
     if(attr_name == "booleanResult")
     {
         boolean result = false;
         if(m_booleanLogical.size() == 1)
             if(logicalBoolean.equalsIgnoreCase("XOR"))
                 result = true;
             else
                 result = ((Boolean)m_booleanLogical.keys().nextElement()).booleanValue();
         if(m_booleanLogical.size() == 2)
             if(logicalBoolean.equalsIgnoreCase("XOR"))
                 result = false;
             else
             if(logicalBoolean.equalsIgnoreCase("OR"))
                 result = true;
             else
                 result = false;
       
         attr.set_value(result);
     }
 }

 public void set_all_values(double argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_values()");
     sentValue = argin;
     (new Thread() {

         public void run()
         {
             Enumeration enumeration = m_groupTable.keys();
             String attributeName;
             for(DeviceAttribute attr = null; enumeration.hasMoreElements(); attr = new DeviceAttribute(attributeName))
             {
                 attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     GroupAttrReplyList resultGroup = aGroup.read_attribute(attributeName, true);
                     for(Enumeration enum = resultGroup.elements(); enum.hasMoreElements();)
                     {
                         GroupAttrReply result = (GroupAttrReply)enum.nextElement();
                         try
                         {
                             attr = result.get_data();
                             switch(attr.getType())
                             {
                             case Tango_DEV_SHORT : 
                                 attr.insert((new Double(sentValue)).shortValue());
                                 break;

                             case Tango_DEV_BOOLEAN: 
                                 boolean boolVal = false;
                                 if(sentValue == 1.0D)
                                     boolVal = true;
                                 attr.insert(boolVal);
                                 break;

                             case Tango_DEV_CHAR:
                                 attr.insert((new Double(sentValue)).intValue());
                                 break;

                             case Tango_DEV_ULONG:
                                 attr.insert((new Double(sentValue)).intValue());
                                 break;

                             case Tango_DEV_LONG: 
                                 attr.insert((new Double(sentValue)).intValue());
                                 break;

                             case Tango_DEV_UCHAR: 
                                 attr.insert_uc((new Double(sentValue)).shortValue());
                                 break;
                                 
                             case Tango_DEV_USHORT: 
                                 attr.insert_us((new Double(sentValue)).shortValue());
                                 break;
                                 
                             case Tango_DEV_FLOAT: 
                                 attr.insert((new Double(sentValue)).floatValue());
                                 break;

                             case Tango_DEV_DOUBLE:
                                 attr.insert(sentValue);
                                 break;

                             default:
                                 sentValue = (0.0D / 0.0D);
                                 break;
                             }
                             if(sentValue != (0.0D / 0.0D))
                                 aGroup.write_attribute(attr, true);
                         }
                         catch(DevFailed e)
                         {
                             System.out.println("Cannot write on " + attributeName + " attribute");
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_values()");
 }

 public void set_all_format(String argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_format()");
     sentProperty = argin;
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.format = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_format()");
 }

 public void set_all_unit(String argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_unit()");
     sentProperty = argin;
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.unit = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_unit()");
 }

 public void set_all_min_value(double argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_min_value()");
     sentProperty = String.valueOf(argin);
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.min_value = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_min_value()");
 }

 public void set_all_max_value(double argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_max_value()");
     sentProperty = String.valueOf(argin);
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.max_value = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_max_value()");
 }

 public void set_all_min_alarm(double argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_min_alarm()");
     sentProperty = String.valueOf(argin);
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.min_alarm = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_min_alarm()");
 }

 public void set_all_max_alarm(double argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_max_alarm()");
     sentProperty = String.valueOf(argin);
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.max_alarm = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_max_alarm()");
 }

 public void set_all_label(String argin)
     throws DevFailed
 {
     get_logger().info("Entering set_all_label()");
     sentProperty = argin;
     (new Thread() {

         public void run()
         {
             for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
             {
                 String attributeName = (String)enumeration.nextElement();
                 Group aGroup = (Group)m_groupTable.get(attributeName);
                 try
                 {
                     for(int i = 0; i < aGroup.get_size(true); i++)
                     {
                         DeviceProxy proxy = aGroup.get_device(i);
                         if(proxy != null)
                         {
	                         AttributeInfo attrInfo = proxy.get_attribute_info(attributeName);
	                         attrInfo.label = sentProperty;
	                         proxy.set_attribute_info(new AttributeInfo[] {
	                             attrInfo
	                         });
	                         attrInfo = null;
	                         proxy = null;
                         }
                     }

                 }
                 catch(DevFailed e)
                 {
                     e.printStackTrace();
                 }
             }

         }

     }).start();
     get_logger().info("Exiting set_all_label()");
 }

 public void activate_all()
     throws DevFailed
 {
     get_logger().info("Entering reset()");
     set_all_values(1.0D);
     get_logger().info("Exiting reset()");
 }

 public void deactivage_all()
     throws DevFailed
 {
     get_logger().info("Entering reset()");
     set_all_values(0.0D);
     get_logger().info("Exiting reset()");
 }

 public String get_attribute_name_for_index(short argin)
     throws DevFailed
 {
     String argout = new String();
     get_logger().info("Entering get_attribute_name_for_index()");
     if(attributeNameList.length == 0)
         argout = "attribute list is empty";
     if(argin > attributeNameList.length - 1)
         argout = "index out of the bound attribute list";
     if(attributeNameList.length != 0 && argin < attributeNameList.length)
         argout = attributeNameList[argin];
     get_logger().info("Exiting get_attribute_name_for_index()");
     return argout;
 }

 public class StateReader extends Thread
 {
     public void run()
     {
         for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
         {
             String attributeName = (String)enumeration.nextElement();
             Group aGroup = (Group)m_groupTable.get(attributeName);
             
             int size = aGroup.get_size(true);
             DeviceProxy proxy = null;
             DeviceAttribute deviceAttribute  = null;
             AttrQuality attrQualityTmp = AttrQuality.ATTR_INVALID;
             for (int i = 0; i <size; i++)
             {
                 attrQualityTmp = AttrQuality.ATTR_INVALID;
                 proxy = aGroup.get_device(i);
                 if(proxy != null)
                 {
                     try
                     {
                         deviceAttribute = proxy.read_attribute(attributeName);
                         attrQualityTmp = deviceAttribute.getQuality();
                         m_attributeQualityTable.put(proxy.name() + "/" + attributeName,attrQualityTmp);
                     }
                     catch(Exception e)
                     {
                         e.printStackTrace();
                     }
                 }
             }
         }
             
             /*
             String[] deviList = aGroup.get_device_list(true);
             aGroup.remove_all();
             aGroup.add(deviList);
             GroupAttrReplyList resultGroup = null;
             try
             {
                 resultGroup = aGroup.read_attribute(attributeName, true);
             }
             catch(DevFailed e)
             {
                 e.printStackTrace();
             }
             if(resultGroup == null)
                 return;
             
             Enumeration enum = resultGroup.elements();
             
             AttrQuality attrQualityTmp = AttrQuality.ATTR_INVALID;
             while(enum.hasMoreElements()) 
             {
                 GroupAttrReply result = (GroupAttrReply)enum.nextElement();
                 try
                 {
                     attrQualityTmp = result.get_data().getQuality();
                 }
                 catch(Exception e)
                 {
                     e.printStackTrace();
                 }
                 m_attributeQualityTable.put(result.dev_name() + "/" + attributeName,attrQualityTmp);
             }
             resultGroup.clear();
             resultGroup = null;
             */
             System.gc();
        // }
       
     }
 }
 
 public class StateUpdater extends Thread
 {
     public void run()
     {
         m_resumQualityTable.clear();
         m_resumpriorityTable.clear();
         
         Enumeration enumeration = m_attributeQualityTable.keys();
         while (enumeration.hasMoreElements())
         {
            String attributeName = (String) enumeration.nextElement();
            AttrQuality attrQualityTmp = (AttrQuality) m_attributeQualityTable.get(attributeName);
            int index = get_index_for_attribute(attributeName);
            if(index != -1)
            {
                attr_attributesNumberPriorityList_read[index] = ((Integer)m_priorityTable.get(attrQualityTmp)).shortValue();
                attr_attributesQualityList_read[index] = (String)m_qualityTable.get(attrQualityTmp) + "-" + attributeNameList[index];
                if(!m_resumQualityTable.containsKey(attrQualityTmp) && !m_resumQualityTable.contains((Integer)m_priorityTable.get(attrQualityTmp)))
                    m_resumQualityTable.put(attrQualityTmp, (Integer)m_priorityTable.get(attrQualityTmp));
            }
            
        }
         
         try
         {
             if(m_resumQualityTable.size() == 0)
             {
                 set_state(DevState.UNKNOWN);
                 set_status("Not initialized yet");
             } else
             if(m_resumQualityTable.size() == 1)
             {
                 AttrQuality attrQuality_tmp = (AttrQuality)m_resumQualityTable.keys().nextElement();
                 set_state((DevState)m_stateQualityTable.get(attrQuality_tmp));
                 set_status("All the attributes are in " + (String)m_qualityTable.get(attrQuality_tmp) + " quality.");
             } else
             {
                 AttrQuality key;
                 Integer value;
                 for(Enumeration enum = m_resumQualityTable.keys(); enum.hasMoreElements(); m_resumpriorityTable.put(value, key))
                 {
                     key = (AttrQuality)enum.nextElement();
                     value = (Integer)m_resumQualityTable.get(key);
                 }

                 Object objList[] = m_resumQualityTable.values().toArray();
                 Arrays.sort(objList);
                 AttrQuality attrQuality_tmp = (AttrQuality)m_resumpriorityTable.get(objList[objList.length - 1]);
                 DevState state_tmp = (DevState)m_stateQualityTable.get(attrQuality_tmp);
                 set_state(state_tmp);
                 set_status("One of the attribute is in " + (String)m_qualityTable.get(attrQuality_tmp) + " quality.");
             }
         }
         catch(Exception exception) { }
     }
 }
 
 
 public class ValueReader extends Thread
 {
     public void run()
     {
         for(Enumeration enumeration = m_groupTable.keys(); enumeration.hasMoreElements();)
         {
             String attributeName = (String)enumeration.nextElement();
             Group aGroup = (Group)m_groupTable.get(attributeName);
             
             int size = aGroup.get_size(true);
             DeviceProxy proxy = null;
             DeviceAttribute attr  = null;
             for (int i = 0; i <size; i++)
             {
                 proxy = aGroup.get_device(i);
                 if(proxy != null)
                 {
                     try
                     {
                         attr = proxy.read_attribute(attributeName);
                         double value = 0.0D;
                         switch(attr.getType())
                         {
                         case 2: // '\002'
                             value = (new Short(attr.extractShort())).doubleValue();
                             break;

                         case 10: // '\n'
                             short array[] = attr.extractShortArray();
                             value = 0.0D;
                             for(int j = 0; j < array.length; j++)
                                 value += array[j];

                             break;

                         case 1: // '\001'
                             if(attr.extractBoolean())
                                 value = 1.0D;
                             else
                                 value = 0.0D;
                             break;

                         case 6: // '\006'
                             value = (new Integer(attr.extractUShort())).doubleValue();
                             break;

                         case 14: // '\016'
                             int array1[] = attr.extractUShortArray();
                             value = 0.0D;
                             for(int j = 0; j < array1.length; j++)
                                 value += array1[j];

                             break;

                         case 7: // '\007'
                             value = (new Integer(attr.extractLong())).doubleValue();
                             break;

                         case 15: // '\017'
                             int array2[] = attr.extractLongArray();
                             value = 0.0D;
                             for(int j = 0; j < array2.length; j++)
                                 value += array2[j];

                             break;

                         case 3: // '\003'
                             value = (new Integer(attr.extractLong())).doubleValue();
                             break;

                         case 11: // '\013'
                             int array3[] = attr.extractLongArray();
                             value = 0.0D;
                             for(int j = 0; j < array3.length; j++)
                                 value += array3[j];

                             break;

                         case 22: // '\026'
                             value = (new Short(attr.extractUChar())).doubleValue();
                             break;

                         case 19: // '\023'
                             value = (new Integer(attr.extractState().value())).doubleValue();
                             break;

                         case 5: // '\005'
                             value = attr.extractDouble();
                             break;

                         case 13: // '\r'
                             double array4[] = attr.extractDoubleArray();
                             value = 0.0D;
                             for(int j = 0; j < array4.length; j++)
                                 value += array4[j];

                             break;

                         case 4: // '\004'
                         case 8: // '\b'
                         case 9: // '\t'
                         case 12: // '\f'
                         case 16: // '\020'
                         case 17: // '\021'
                         case 18: // '\022'
                         case 20: // '\024'
                         case 21: // '\025'
                         default:
                             value = (0.0D / 0.0D);
                             break;
                         }
                         m_attributeValueTable.put(proxy.name() + "/" + attributeName,new Double(value));
                     }
                     catch(Exception e)
                     {
                         e.printStackTrace();
                     }
                 }
             }
         }
             
             /*
             String[] deviList = aGroup.get_device_list(true);
             aGroup.remove_all();
             aGroup.add(deviList);
             GroupAttrReplyList resultGroup = null;
             try
             {
                 resultGroup = aGroup.read_attribute(attributeName, true);
             }
             catch(DevFailed e)
             {
                 e.printStackTrace();
             }
             Enumeration enum = resultGroup.elements();
             DeviceAttribute attr = null;
             while(enum.hasMoreElements()) 
             {
                 GroupAttrReply result = (GroupAttrReply)enum.nextElement();
                 try
                 {
                     attr = result.get_data();
                     double value = 0.0D;
                     switch(attr.getType())
                     {
                     case 2: // '\002'
                         value = (new Short(attr.extractShort())).doubleValue();
                         break;

                     case 10: // '\n'
                         short array[] = attr.extractShortArray();
                         value = 0.0D;
                         for(int j = 0; j < array.length; j++)
                             value += array[j];

                         break;

                     case 1: // '\001'
                         if(attr.extractBoolean())
                             value = 1.0D;
                         else
                             value = 0.0D;
                         break;

                     case 6: // '\006'
                         value = (new Integer(attr.extractUShort())).doubleValue();
                         break;

                     case 14: // '\016'
                         int array1[] = attr.extractUShortArray();
                         value = 0.0D;
                         for(int j = 0; j < array1.length; j++)
                             value += array1[j];

                         break;

                     case 7: // '\007'
                         value = (new Integer(attr.extractLong())).doubleValue();
                         break;

                     case 15: // '\017'
                         int array2[] = attr.extractLongArray();
                         value = 0.0D;
                         for(int j = 0; j < array2.length; j++)
                             value += array2[j];

                         break;

                     case 3: // '\003'
                         value = (new Integer(attr.extractLong())).doubleValue();
                         break;

                     case 11: // '\013'
                         int array3[] = attr.extractLongArray();
                         value = 0.0D;
                         for(int j = 0; j < array3.length; j++)
                             value += array3[j];

                         break;

                     case 22: // '\026'
                         value = (new Short(attr.extractUChar())).doubleValue();
                         break;

                     case 19: // '\023'
                         value = (new Integer(attr.extractState().value())).doubleValue();
                         break;

                     case 5: // '\005'
                         value = attr.extractDouble();
                         break;

                     case 13: // '\r'
                         double array4[] = attr.extractDoubleArray();
                         value = 0.0D;
                         for(int j = 0; j < array4.length; j++)
                             value += array4[j];

                         break;

                     case 4: // '\004'
                     case 8: // '\b'
                     case 9: // '\t'
                     case 12: // '\f'
                     case 16: // '\020'
                     case 17: // '\021'
                     case 18: // '\022'
                     case 20: // '\024'
                     case 21: // '\025'
                     default:
                         value = (0.0D / 0.0D);
                         break;
                     }
                     
                     m_attributeValueTable.put(result.dev_name() + "/" + attributeName, new Double(value));
                 }
                 catch(Exception exception) { }
             }
             resultGroup.clear();
             resultGroup = null;
             System.gc();
         }*/
         
     }
 }
 
 public class ValueUpdater extends Thread
 {
     
     public void run()
     {
         Enumeration enumeration = m_attributeValueTable.keys();
         while (enumeration.hasMoreElements()) {
            String attributeName = (String) enumeration.nextElement();
            double value = ((Double)m_attributeValueTable.get(attributeName)).doubleValue();
            int index = get_index_for_attribute(attributeName);
            attr_spectrumResult_read[index] = value;
            short svalue = 1;
            if(value == (0.0D / 0.0D) || value != 1.0D)
                svalue = 0;
            attr_booleanSpectrum_read[index] = svalue;
        }
         
         m_booleanLogical.clear();
         for(int i = 0; i < attr_booleanSpectrum_read.length; i++)
         {
             Boolean boolVal = new Boolean(false);
             if(attr_booleanSpectrum_read[i] == 1)
                 boolVal = new Boolean(true);
             m_booleanLogical.put(boolVal, boolVal);
             if(m_booleanLogical.size() == 2)
                 return;
         }
     }
 }
 
 public static void main(String argv[])
 {
     System.out.println("ATTRIBUTECOMPOSER VERSION 1.0.7 NO GROUP");
     
     try
	 {
	    //Unexport the server before
	    if(argv != null && argv.length > 0)
	    {
	        new Database().unexport_server("attributecomposer/" + argv[0]);
	    }
	 }
     catch(Exception e){}
     
     try
     {
         
         Util tg = Util.init(argv, "AttributeComposer");
         tg.server_init();
         System.out.println("Ready to accept request");
         tg.server_run();
     }
     catch(OutOfMemoryError ex)
     {
         System.err.println("Can't allocate memory !!!!");
         System.err.println("Exiting");
     }
     catch(UserException ex)
     {
         Except.print_exception(ex);
         System.err.println("Received a CORBA user exception");
         System.err.println("Exiting");
     }
     catch(SystemException ex)
     {
         Except.print_exception(ex);
         System.err.println("Received a CORBA system exception");
         System.err.println("Exiting");
     }
     System.exit(-1);
 }
 


}
