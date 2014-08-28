package com.servoy.j2db.server.ngclient;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.border.Border;

import org.json.JSONException;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.IComplexPropertyValue;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.ConversionLocation;
import org.sablo.websocket.TypedData;

import com.servoy.j2db.FormController;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IForm;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.component.ComponentFormat;
import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.dataprocessing.IValueList;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.persistence.AbstractPersistFactory;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.ISupportTabSeq;
import com.servoy.j2db.persistence.PositionComparator;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.scripting.ElementScope;
import com.servoy.j2db.scripting.FormScope;
import com.servoy.j2db.server.ngclient.component.RuntimeLegacyComponent;
import com.servoy.j2db.server.ngclient.component.RuntimeWebComponent;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.SortedList;
import com.servoy.j2db.util.UUID;
import com.servoy.j2db.util.Utils;

@SuppressWarnings("nls")
public class WebFormUI extends Container implements IWebFormUI
{
	private static final class FormSpecification extends WebComponentSpecification
	{
		private FormSpecification()
		{
			super("form_spec", "", "", null, null, "", null);
			putProperty("size", new PropertyDescription("size", TypesRegistry.getType("dimension")));
		}
	}

	private static final WebComponentSpecification FORM_SPEC = new FormSpecification();

	private final Map<String, Integer> events = new HashMap<>(); //event name mapping to persist id
	private final IWebFormController formController;

	private boolean enabled = true;
	private boolean readOnly = false;
	private Object parentContainerOrWindowName;

	protected IDataAdapterList dataAdapterList;

	// the next available tab sequence number (after this component and all its subtree)
	protected int nextAvailableTabSequence;

	public WebFormUI(IWebFormController formController)
	{
		super(formController.getName(), FORM_SPEC);
		this.formController = formController;
		init();
	}

	public final INGApplication getApplication()
	{
		return formController.getApplication();
	}

	/**
	 * this is a full recreate ui.
	 *
	 * @param formController
	 * @param dal
	 * @return
	 * @throws RepositoryException
	 */
	public void init()
	{
		components.clear();
		DataAdapterList dal = new DataAdapterList(formController);
		ElementScope elementsScope = initElementScope(formController);
		List<FormElement> formElements = getFormElements();
		int counter = 0;
		for (FormElement fe : formElements)
		{
			// TODO do something similar for child elements (so properties of type 'components' which contain componentSpecs in them)

			WebComponentSpecification componentSpec = fe.getWebComponentSpec(false);
			if (componentSpec == null)
			{
				getApplication().reportError("Didn't find a spec file for component " + fe + " when creating form: " + formController.getName(), null);
				continue;
			}

			WebFormComponent component = ComponentFactory.createComponent(getApplication(), dal, fe, this);
			IPersist persist = fe.getPersistIfAvailable();
			int access = 0;
			if (persist != null)
			{
				// don't add the component to the form ui if component is not visible due to security settings
				access = formController.getApplication().getFlattenedSolution().getSecurityAccess(persist.getUUID());
				if (!((access & IRepository.VIEWABLE) != 0)) continue;
			}
			counter = contributeComponentToElementsScope(elementsScope, counter, fe, componentSpec, component);
			add(component);

			for (String propName : fe.getProperties().keySet())
			{
				if (fe.getPropertyWithDefault(propName) == null || componentSpec.getProperty(propName) == null) continue; //TODO this if should not be necessary. currently in the case of "printable" hidden property
				fillProperties(fe.getForm(), fe, fe.getPropertyWithDefault(propName), componentSpec.getProperty(propName), dal, component, component, "");
			}
			// overwrite accessible
			if (persist != null && !((access & IRepository.ACCESSIBLE) != 0)) component.setProperty("enabled", false, ConversionLocation.SERVER);
			for (String eventName : componentSpec.getHandlers().keySet())
			{
				Object eventValue = fe.getProperty(eventName);
				if (eventValue instanceof String)
				{
					UUID uuid = UUID.fromString((String)eventValue);
					try
					{
						component.add(eventName, ((AbstractPersistFactory)ApplicationServerRegistry.get().getLocalRepository()).getElementIdForUUID(uuid));
					}
					catch (RepositoryException e)
					{
						Debug.error(e);
					}
				}
				else if (eventValue instanceof Number && ((Number)eventValue).intValue() > 0)
				{
					component.add(eventName, ((Number)eventValue).intValue());

				}
			}
		}

		DefaultNavigatorWebComponent nav = (DefaultNavigatorWebComponent)components.get(DefaultNavigator.NAME_PROP_VALUE);
		if (nav != null)
		{
			nav.newFoundset(null);
		}
		// special support for the default navigator
		if (formController.getForm().getNavigatorID() == Form.NAVIGATOR_DEFAULT)
		{
			add(new DefaultNavigatorWebComponent(dal));
		}

		if (dataAdapterList != null)
		{
			IRecordInternal record = ((DataAdapterList)dataAdapterList).getRecord();
			if (record != null)
			{
				dal.setRecord(record, false);
				dataAdapterList.setRecord(null, false);

				nav = (DefaultNavigatorWebComponent)components.get(DefaultNavigator.NAME_PROP_VALUE);
				if (nav != null) nav.newFoundset(record.getParentFoundSet());
			}
		}
		dataAdapterList = dal;
	}

	public void contributeComponentToElementsScope(FormElement fe, WebComponentSpecification componentSpec, WebFormComponent component)
	{
		ElementScope elementsScope = getElementsScope();
		if (elementsScope != null)
		{
			Object tmp = elementsScope.get("length", elementsScope);
			int counter = tmp instanceof Integer ? ((Integer)tmp).intValue() : 0;
			contributeComponentToElementsScope(elementsScope, counter, fe, componentSpec, component);
		}
		else
		{
			Debug.error(new RuntimeException("Trying to contribute to a non-existent elements scope for form: " + getName()));
		}
	}

	private int contributeComponentToElementsScope(ElementScope elementsScope, int counter, FormElement fe, WebComponentSpecification componentSpec,
		WebFormComponent component)
	{
		if (!fe.getName().startsWith("svy_"))
		{
			RuntimeWebComponent runtimeComponent = new RuntimeWebComponent(component, componentSpec);
			elementsScope.put(fe.getName(), formController.getFormScope(), runtimeComponent);
			elementsScope.put(counter++, formController.getFormScope(), runtimeComponent);
			if (fe.isLegacy() ||
				((fe.getForm().getView() == IForm.LIST_VIEW || fe.getForm().getView() == FormController.LOCKED_LIST_VIEW) && fe.getTypeName().startsWith("svy-")))
			{
				// add legacy behavior
				runtimeComponent.setPrototype(new RuntimeLegacyComponent(component));
			}
		}
		return counter;
	}

	public IServoyDataConverterContext getDataConverterContext()
	{
		return new ServoyDataConverterContext(formController);
	}

	/**
	 *  -fe is only needed because of format . It accesses another property value based on the 'for' property (). TODO this FormElement parameter should be analyzed because format accepts a flat property value.
	 *
	 * -level is only needed because the current implementation 'flattens' the dataproviderid's and tagstrings for DAL  .(level should be removed after next changes)
	 *
	 *  -component is the whole component for now ,but it should be the current component node in the runtime component tree (instead of flat properties map)
	 *  -component and componentNode should have been just componentNode (but currently WebCoponent is not nested)
	 */
	public void fillProperties(Form formElNodeForm, FormElement fe, Object formElementProperty, PropertyDescription propertySpec, DataAdapterList dal,
		WebFormComponent component, Object componentNode, String level)
	{
		// TODO This whole method content I think can be removed when dataprovider, tagstring, ... are implemented as complex types and tree JSON handling is also completely working...
		// except for initial filling of all properties from FormElement into WebComponent
		IPropertyType< ? > type = propertySpec.getType();
		if (propertySpec.isArray() && formElementProperty instanceof List && !(formElementProperty instanceof IComplexPropertyValue)) // if it's a special property type that handles directly arrays, it could be a different kind of object
		{
			List<Object> processedArray = new ArrayList<>();
			List<Object> fePropertyArray = (List<Object>)formElementProperty;
			for (Object arrayValue : fePropertyArray)
			{
				Object propValue = initFormElementProperty(formElNodeForm, fe, arrayValue, propertySpec.asArrayElement(), dal, component, componentNode, level,
					true);
				switch (type.getName())
				{
					case "dataprovider" : // array of dataprovider is not supported yet (DAL does not support arrays)  , Should be done in initFormElementProperty()
					{
						Debug.error("Array of dataprovider currently not supported dataprovider");
						Object dataproviderID = propValue;
						if (dataproviderID instanceof String)
						{
							dal.add(component, level + (String)dataproviderID, propertySpec.getName());
						}
						break;
					}
					case "tagstring" : // array of taggstring is not supported yet (DAL does not support arrays)
					{
						Debug.error("Array of tagstring currently not supported dataprovider");
						//bind tag expressions
						//for each property with tags ('tagstring' type), add it's dependent tags to the DAL
						if (propValue != null && propValue instanceof String && (((String)propValue).contains("%%")) || ((String)propValue).startsWith("i18n:"))
						{
							dal.addTaggedProperty(component, level + propertySpec.getName(), (String)propValue);
						}
						break;
					}
					default :
					{
						processedArray.add(propValue);
					}

				}
			}
			if (processedArray.size() > 0)
			{
				putInComponentNode(componentNode, propertySpec.getName(), processedArray, propertySpec, component);
			}
		}
		else
		{
			Object propValue = initFormElementProperty(formElNodeForm, fe, formElementProperty, propertySpec, dal, component, componentNode, level, false);
			String propName = propertySpec.getName();
			switch (type.getName())
			{
				case "dataprovider" : // array of dataprovider is not supported yet (DAL does not support arrays)
				{
					Object dataproviderID = formElementProperty;
					if (dataproviderID instanceof String)
					{
						dal.add(component, (String)dataproviderID, level + propName);
						return;
					}
					break;
				}
				case "tagstring" : // array of taggstring is not supported yet (DAL does not support arrays)
				{
					//bind tag expressions
					//for each property with tags ('tagstring' type), add it's dependent tags to the DAL
					if (propValue != null && propValue instanceof String && (((String)propValue).contains("%%") || ((String)propValue).startsWith("i18n:")))
					{
						dal.addTaggedProperty(component, level + propName, (String)propValue);
						return;
					}
					break;
				}
				case "valuelist" : // skip valuelistID , it is handled elsewhere (should be changed to be handled here?)
					return;
				default :
					break;
			}
			if (propValue != null) putInComponentNode(componentNode, propName, propValue, propertySpec, component); //TODO
		}
	}

	/**
	 * TEMPORARY FUNCTION until we move to nested web component tree , with each node having semantics (PropertyType)
	 *  Webcomponent will be a tree
	 */
	private void putInComponentNode(Object componentNode, String propName, Object propValue, PropertyDescription propertySpec, WebFormComponent component)
	{
		// TODO should this just a a property.property.property = value called to WebFormComponent?
		if (componentNode instanceof WebFormComponent)
		{
			// TODO this will convert a second time (the first conversion was done in FormElement; is this really needed? cause
			// converted value reaching conversion again doesn't seem nice
			((WebFormComponent)componentNode).setProperty(propName, propValue, ConversionLocation.DESIGN);
		}
		else
		{
			// now we need to convert it ourselfs. because this map will be internal to the WebFormComponent so has to have wrapper values.
			if (propertySpec != null && propertySpec.getType() instanceof IWrapperType< ? , ? >)
			{
				propValue = ((IWrapperType)propertySpec.getType()).wrap(propValue, null, new DataConverterContext(propertySpec, component));
			}
			((Map)componentNode).put(propName, propValue);
		}
	}

	/**
	 * This method turns a design-time property into a Runtime Object that will be used as that property.
	 *  TODO merge component and component node remove isarrayElement parameter
	 * @return
	 */
	private Object initFormElementProperty(Form formElNodeForm, FormElement fe, Object formElementProperty, PropertyDescription propertySpec,
		DataAdapterList dal, WebFormComponent component, Object componentNode, String level, boolean isArrayElement)
	{
		// TODO This whole method I think should be removed when dataprovider, tagstring, ... are implemented as complex types and tree JSON handling is also completely working...
		Object ret = null;
		switch (propertySpec.getType().getName())
		{
			case "format" :
			{
				Object propValue = formElementProperty;
				if (propValue instanceof String)
				{
					// get dataproviderId
					String dataproviderId = (String)fe.getProperty((String)propertySpec.getConfig());
					ComponentFormat format = ComponentFormat.getComponentFormat((String)propValue, dataproviderId,
						getApplication().getFlattenedSolution().getDataproviderLookup(getApplication().getFoundSetManager(), formElNodeForm), getApplication());
					ret = format;
				}
				break;
			}
			case "bean" :
			{
				Object propValue = formElementProperty;
				if (propValue instanceof String)
				{
					ret = ComponentFactory.getMarkupId(fe.getName(), (String)propValue);
				}
				break;
			}
			case "valuelist" : // skip valuelistID , it is handled elsewhere (should be changed to be handled here?)
				break;
			default :
			{
				if ((propertySpec.getType() instanceof ICustomType) && ((ICustomType)propertySpec.getType()).getCustomJSONTypeDefinition() != null &&
					formElementProperty instanceof Map && !(formElementProperty instanceof IComplexPropertyValue))
				{
					// TODO Remove this when pure tree-like JSON properties which use complex types in leafs are operational (so no need for flattening them any more)
					String innerLevelpropName = level + propertySpec.getName();
					Map<String, PropertyDescription> props = ((Map<String, PropertyDescription>)formElementProperty);
					Map<String, Object> newComponentNode = new HashMap<>();
					PropertyDescription localPropertyType = ((ICustomType)propertySpec.getType()).getCustomJSONTypeDefinition();

					for (String prop : props.keySet())
					{
						PropertyDescription localPropertyDescription = localPropertyType.getProperty(prop);
						fillProperties(formElNodeForm, fe, props.get(prop), localPropertyDescription, dal, component, newComponentNode, isArrayElement ? ""
							: innerLevelpropName + ".");
					}
					ret = newComponentNode;
					break;
				}
				else
				{
					ret = formElementProperty;
				}
			}
		}
		return ret;
	}

	public IDataAdapterList getDataAdapterList()
	{
		return dataAdapterList;
	}

	public void add(String eventType, int functionID)
	{
		events.put(eventType, Integer.valueOf(functionID));
	}

	public boolean hasEvent(String eventType)
	{
		return events.containsKey(eventType);
	}

	@Override
	public Object executeEvent(String eventType, Object[] args)
	{
		Integer eventId = events.get(eventType);
		if (eventId != null)
		{
			return dataAdapterList.executeEvent(this, eventType, eventId.intValue(), args);
		}
		throw new IllegalArgumentException("Unknown event '" + eventType + "' for component " + this);
	}

	@Override
	public WebFormComponent getWebComponent(String name)
	{
		return (WebFormComponent)super.getComponent(name);
	}

	@Override
	public TypedData<Map<String, Map<String, Object>>> getAllComponentsProperties()
	{
		try
		{
			getController().setRendering(true);
			return super.getAllComponentsProperties();
		}
		finally
		{
			getController().setRendering(false);
		}
	}

	@Override
	public TypedData<Map<String, Map<String, Object>>> getAllComponentsChanges()
	{
		try
		{
			getController().setRendering(true);
			return super.getAllComponentsChanges();
		}
		finally
		{
			getController().setRendering(false);
		}
	}

	private ElementScope initElementScope(IFormController controller)
	{
		FormScope formScope = controller.getFormScope();
		ElementScope elementsScope = new ElementScope(formScope);
		formScope.putWithoutFireChange("elements", elementsScope); //$NON-NLS-1$
		return elementsScope;
	}

	@Override
	public void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		if ("size".equals(propertyName))
		{
			Dimension prev = (Dimension)properties.get("size");
			super.putBrowserProperty(propertyName, propertyValue);
			Dimension newSize = (Dimension)properties.get("size");
			if (!Utils.equalObjects(prev, newSize))
			{
				formController.notifyResized();
			}
		}
		else
		{
			super.putBrowserProperty(propertyName, propertyValue);
		}
	}

	@Override
	public IWebFormController getController()
	{
		return formController;
	}

	@Override
	public boolean isDisplayingMoreThanOneRecord()
	{
		return false;
	}

	@Override
	public void destroy()
	{
		for (WebComponent c : components.values())
		{
			c.dispose();
		}
		components.clear();
	}

	@Override
	public void setModel(IFoundSetInternal fs)
	{
		DefaultNavigatorWebComponent nav = (DefaultNavigatorWebComponent)components.get(DefaultNavigator.NAME_PROP_VALUE);
		if (nav != null)
		{
			nav.newFoundset(fs);
		}
	}

	@Override
	public void setComponentEnabled(boolean enabled)
	{
		this.enabled = enabled;
		propagatePropertyToAllComponents("enabled", enabled);
	}

	@Override
	public boolean isEnabled()
	{
		return enabled;
	}

	@Override
	public boolean isReadOnly()
	{
		return readOnly;
	}

	@Override
	public void setReadOnly(boolean readOnly)
	{
		this.readOnly = readOnly;
		propagatePropertyToAllComponents("readOnly", readOnly);
	}

	private void propagatePropertyToAllComponents(String property, boolean value)
	{
		if ("readOnly".equals(property))
		{
			// special case, we have to set editable property for legacy components
			List<FormElement> formElements = getFormElements();
			for (FormElement fe : formElements)
			{
				WebComponent component = components.get(fe.getName());
				if (component != null)
				{
					if (fe.isLegacy())
					{
						component.setProperty("editable", !value, ConversionLocation.SERVER);
					}
					else
					{
						component.setProperty(property, value, ConversionLocation.SERVER);
					}
				}
			}
		}
		else
		{
			for (WebComponent component : components.values())
			{
				component.setProperty(property, value, ConversionLocation.SERVER);
			}
		}
	}

	private ElementScope getElementsScope()
	{
		FormScope formScope = formController.getFormScope();
		if (formScope != null)
		{
			return (ElementScope)formScope.get("elements", null);
		}
		return null;
	}

	@Override
	public int recalculateTabIndex(int startIndex, TabSequencePropertyWithComponent startComponent)
	{
		int currentIndex = startIndex;
		List<TabSequencePropertyWithComponent> tabSeqComponents = getTabSeqComponents();
		int startIndexInList = 0;
		if (startComponent != null)
		{
			startIndexInList = tabSeqComponents.indexOf(startComponent);
		}
		for (int i = Math.max(0, startIndexInList); i < tabSeqComponents.size(); i++)
		{
			tabSeqComponents.get(i).setCalculatedTabSequence(currentIndex++);
		}
		if (startComponent != null && parentContainerOrWindowName instanceof WebFormComponent)
		{
			// upwards traversal
			((WebFormComponent)parentContainerOrWindowName).recalculateTabSequence(currentIndex);
		}
		nextAvailableTabSequence = currentIndex;
		return currentIndex;
	}

	protected List<TabSequencePropertyWithComponent> getTabSeqComponents()
	{
		SortedList<TabSequencePropertyWithComponent> tabSeqComponents = new SortedList<TabSequencePropertyWithComponent>(
			new Comparator<TabSequencePropertyWithComponent>()
			{
				@Override
				public int compare(TabSequencePropertyWithComponent o1, TabSequencePropertyWithComponent o2)
				{
					int seq1 = o1.getTabSequence();
					int seq2 = o2.getTabSequence();

					if (seq1 == ISupportTabSeq.DEFAULT && seq2 == ISupportTabSeq.DEFAULT)
					{
						//delegate to Yx
						int yxCompare = PositionComparator.XY_BOUNDS_COMPARATOR.compare(o1, o2);
						// if they are at the same position, and are different persist, just use UUID to decide the sequence
						return yxCompare == 0 ? o1.getComponent().getName().compareTo(o2.getComponent().getName()) : yxCompare;
					}
					else if (seq1 == ISupportTabSeq.DEFAULT)
					{
						return 1;
					}
					else if (seq2 == ISupportTabSeq.DEFAULT)
					{
						return -1;
					}
					return seq1 - seq2;
				}

			});
		for (WebComponent component : components.values())
		{
			WebFormComponent comp = (WebFormComponent)component;
			Map<String, PropertyDescription> tabSeqProps = comp.getFormElement().getWebComponentSpec().getProperties(TypesRegistry.getType("tabseq"));
			for (PropertyDescription pd : tabSeqProps.values())
			{
				if (Utils.getAsInteger(comp.getInitialProperty(pd.getName())) >= 0)
				{
					tabSeqComponents.add(new TabSequencePropertyWithComponent(comp, pd.getName()));
				}
			}
		}
		return tabSeqComponents;
	}

	public void setParentContainer(WebFormComponent parentContainer)
	{
		this.parentContainerOrWindowName = parentContainer;
	}

	public Object getParentContainer()
	{
		return parentContainerOrWindowName;
	}

	@Override
	public String getParentWindowName()
	{
		if (parentContainerOrWindowName instanceof String)
		{
			return (String)parentContainerOrWindowName;
		}
		else if (parentContainerOrWindowName instanceof WebFormComponent)
		{
			return ((WebFormComponent)parentContainerOrWindowName).findParent(IWebFormUI.class).getParentWindowName();
		}
		return null;
	}

	public void setParentWindowName(String parentWindowName)
	{
		this.parentContainerOrWindowName = parentWindowName;
	}

	public int getNextAvailableTabSequence()
	{
		return nextAvailableTabSequence;
	}

	@Override
	public void setComponentVisible(boolean visible)
	{
		setVisible(visible);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setLocation(java.awt.Point)
	 */
	@Override
	public void setLocation(Point location)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getLocation()
	 */
	@Override
	public Point getLocation()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setSize(java.awt.Dimension)
	 */
	@Override
	public void setSize(Dimension size)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getSize()
	 */
	@Override
	public Dimension getSize()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setForeground(java.awt.Color)
	 */
	@Override
	public void setForeground(Color foreground)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getForeground()
	 */
	@Override
	public Color getForeground()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setBackground(java.awt.Color)
	 */
	@Override
	public void setBackground(Color background)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getBackground()
	 */
	@Override
	public Color getBackground()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setFont(java.awt.Font)
	 */
	@Override
	public void setFont(Font font)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getFont()
	 */
	@Override
	public Font getFont()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setBorder(javax.swing.border.Border)
	 */
	@Override
	public void setBorder(Border border)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getBorder()
	 */
	@Override
	public Border getBorder()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setName(java.lang.String)
	 */
	@Override
	public void setName(String name)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setOpaque(boolean)
	 */
	@Override
	public void setOpaque(boolean opaque)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#isOpaque()
	 */
	@Override
	public boolean isOpaque()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setCursor(java.awt.Cursor)
	 */
	@Override
	public void setCursor(Cursor cursor)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#setToolTipText(java.lang.String)
	 */
	@Override
	public void setToolTipText(String tooltip)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getToolTipText()
	 */
	@Override
	public String getToolTipText()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.IComponent#getId()
	 */
	@Override
	public String getId()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#showYesNoQuestionDialog(com.servoy.j2db.IApplication, java.lang.String, java.lang.String)
	 */
	@Override
	public boolean showYesNoQuestionDialog(IApplication application, String dlgMessage, String string)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getContainerName()
	{
		if (parentContainerOrWindowName instanceof String)
		{
			return (String)parentContainerOrWindowName;
		}
		if (parentContainerOrWindowName instanceof WebFormComponent && ((WebFormComponent)parentContainerOrWindowName).getParent() != null)
		{
			return ((WebFormComponent)parentContainerOrWindowName).findParent(IWebFormUI.class).getContainerName();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#printPreview(boolean, boolean, int, java.awt.print.PrinterJob)
	 */
	@Override
	public void printPreview(boolean showDialogs, boolean printCurrentRecordOnly, int zoomFactor, PrinterJob printerJob)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#print(boolean, boolean, boolean, java.awt.print.PrinterJob)
	 */
	@Override
	public void print(boolean showDialogs, boolean printCurrentRecordOnly, boolean showPrinterSelectDialog, PrinterJob printerJob)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#printXML(boolean)
	 */
	@Override
	public String printXML(boolean printCurrentRecordOnly)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#showSortDialog(com.servoy.j2db.IApplication, java.lang.String)
	 */
	@Override
	public void showSortDialog(IApplication application, String options)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#getFormWidth()
	 */
	@Override
	public int getFormWidth()
	{
		Dimension size = (Dimension)properties.get("size");
		if (size != null) return size.width;
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#getPartHeight(int)
	 */
	@Override
	public int getPartHeight(int partType)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public JSDataSet getFormContext()
	{
		IDataSet set = new BufferedDataSet(
			new String[] { "windowname", "formname", "containername", "tabname", "tabindex", "tabindex1based" }, new ArrayList<Object[]>()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		set.addRow(new Object[] { null, formController.getName(), null, null, null, null });
		Object currentContainer = parentContainerOrWindowName;
		WebFormUI currentForm = this;
		while (currentContainer instanceof WebFormComponent)
		{
			WebFormComponent currentComponent = (WebFormComponent)currentContainer;
			int index = currentComponent.getFormIndex(currentForm);
			currentForm = currentComponent.findParent(WebFormUI.class);
			set.addRow(0, new Object[] { null, currentForm.formController.getName(), currentComponent.getName(), null, new Integer(index), new Integer(
				index + 1) });
			currentContainer = currentForm.getParentContainer();
		}
		if (currentContainer instanceof String)
		{
			// fill in window name
			for (int i = 0; i < set.getRowCount(); i++)
			{
				set.getRow(i)[0] = currentContainer;
			}
		}
		return new JSDataSet(formController.getApplication(), set);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#changeFocusIfInvalid(java.util.List)
	 */
	@Override
	public void changeFocusIfInvalid(List<Runnable> invokeLaterRunnables)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IBasicFormUI#prepareForSave(boolean)
	 */
	@Override
	public void prepareForSave(boolean looseFocus)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#start(com.servoy.j2db.IApplication)
	 */
	@Override
	public void start(IApplication app)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#stop()
	 */
	@Override
	public void stop()
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#editCellAt(int)
	 */
	@Override
	public boolean editCellAt(int row)
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#stopUIEditing(boolean)
	 */
	@Override
	public boolean stopUIEditing(boolean looseFocus)
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#isEditing()
	 */
	@Override
	public boolean isEditing()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#requestFocus()
	 */
	@Override
	public void requestFocus()
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#ensureIndexIsVisible(int)
	 */
	@Override
	public void ensureIndexIsVisible(int index)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#setEditable(boolean)
	 */
	@Override
	public void setEditable(boolean findMode)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#getVisibleRect()
	 */
	@Override
	public Rectangle getVisibleRect()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.IView#setVisibleRect(java.awt.Rectangle)
	 */
	@Override
	public void setVisibleRect(Rectangle scrollPosition)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.ISupportRowBGColorScript#setRowBGColorScript(java.lang.String, java.util.List)
	 */
	@Override
	public void setRowBGColorScript(String bgColorScript, List<Object> args)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.ISupportRowBGColorScript#getRowBGColorScript()
	 */
	@Override
	public String getRowBGColorScript()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ui.ISupportRowBGColorScript#getRowBGColorArgs()
	 */
	@Override
	public List<Object> getRowBGColorArgs()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public final void valueChanged()
	{
		getApplication().getChangeListener().valueChanged();
	}

	@Override
	public void refreshValueList(IValueList valuelist)
	{
		for (WebComponent component : components.values())
		{
			WebFormComponent comp = (WebFormComponent)component;
			Map<String, PropertyDescription> valuelistProps = comp.getFormElement().getWebComponentSpec().getProperties(TypesRegistry.getType("valuelist"));
			for (PropertyDescription vlProp : valuelistProps.values())
			{
				Object vl = comp.getProperty(vlProp.getName());
				if (vl instanceof IValueList && ((IValueList)vl).getValueList() == valuelist.getValueList())
				{
					comp.setProperty(vlProp.getName(), valuelist, ConversionLocation.BROWSER);
				}
			}
		}
	}

	protected List<FormElement> getFormElements()
	{
		Form form = formController.getForm();
		List<FormElement> formElements = ComponentFactory.getFormElements(form.getFlattenedObjects().iterator(), getDataConverterContext());
		return formElements;
	}
}
