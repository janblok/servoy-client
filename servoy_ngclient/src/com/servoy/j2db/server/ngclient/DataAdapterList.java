package com.servoy.j2db.server.ngclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.property.types.TypesRegistry;

import com.servoy.base.util.ITagResolver;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.IDataAdapter;
import com.servoy.j2db.dataprocessing.IModificationListener;
import com.servoy.j2db.dataprocessing.IRecord;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.dataprocessing.ModificationEvent;
import com.servoy.j2db.dataprocessing.TagResolver;
import com.servoy.j2db.persistence.AggregateVariable;
import com.servoy.j2db.persistence.ColumnWrapper;
import com.servoy.j2db.persistence.IDataProvider;
import com.servoy.j2db.persistence.IDataProviderLookup;
import com.servoy.j2db.persistence.ScriptVariable;
import com.servoy.j2db.query.QueryAggregate;
import com.servoy.j2db.scripting.FormScope;
import com.servoy.j2db.scripting.GlobalScope;
import com.servoy.j2db.scripting.ScopesScope;
import com.servoy.j2db.server.ngclient.component.EventExecutor;
import com.servoy.j2db.server.ngclient.property.DataproviderConfig;
import com.servoy.j2db.server.ngclient.property.IServoyAwarePropertyValue;
import com.servoy.j2db.server.ngclient.property.types.DataproviderTypeSabloValue;
import com.servoy.j2db.server.ngclient.property.types.IDataLinkedType.TargetDataLinks;
import com.servoy.j2db.server.ngclient.property.types.NGConversions;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.ScopesUtils;
import com.servoy.j2db.util.Text;


public class DataAdapterList implements IModificationListener, ITagResolver, IDataAdapterList
{
	private final Map<String, Map<WebFormComponent, List<String>>> dataProviderToComponentWithTags = new HashMap<>();

	// properties that are interested in a specific dataproviderID chaning
	protected final Map<String, List<Pair<WebFormComponent, String>>> dataProviderToLinkedComponentProperty = new HashMap<>(); // dataProviderID -> [(comp, propertyName)]

	// all data-linked properties - contains 'dataProviderToLinkedComponentProperty' as well as other ones that are interested in any DP change
	protected final List<Pair<WebFormComponent, String>> allComponentPropertiesLinkedToData = new ArrayList<>(); // [(comp, propertyName), ...]

	private final IWebFormController formController;
	private final EventExecutor executor;
	private final WeakHashMap<IWebFormController, String> relatedForms = new WeakHashMap<>();

	private IRecordInternal record;
//	private boolean findMode;
	private boolean settingRecord;

	private boolean isFormScopeListener;
	private boolean isGlobalScopeListener;

	public DataAdapterList(IWebFormController formController)
	{
		this.formController = formController;
		this.executor = new EventExecutor(formController);
	}

	/**
	 * @return the application
	 */
	public final INGApplication getApplication()
	{
		return formController.getApplication();
	}

	public final IWebFormController getForm()
	{
		return formController;
	}

	@Override
	public Object executeEvent(WebComponent webComponent, String event, int eventId, Object[] args)
	{
		Object jsRetVal = executor.executeEvent(webComponent, event, eventId, args);
		return NGConversions.INSTANCE.convertRhinoToSabloComponentValue(jsRetVal, null, null, webComponent); // TODO why do handlers not have complete definitions in spec - just like apis? - we don't know types here
	}

	@Override
	public Object executeInlineScript(String script, JSONObject args, JSONArray appendingArgs)
	{
		String decryptedScript = HTMLTagsConverter.decryptInlineScript(script, args);
		if (appendingArgs != null && decryptedScript.endsWith("()"))
		{
			decryptedScript = decryptedScript.substring(0, decryptedScript.length() - 1);
			for (int i = 0; i < appendingArgs.length(); i++)
			{
				try
				{
					decryptedScript += appendingArgs.get(i);
				}
				catch (JSONException e)
				{
					Debug.error(e);
				}
				if (i < appendingArgs.length() - 1)
				{
					decryptedScript += ",";
				}
				else
				{
					decryptedScript += ")";
				}
			}
		}
		return decryptedScript != null ? formController.eval(decryptedScript) : null;
	}

	public void addRelatedForm(IWebFormController form, String relation)
	{
		form.setParentFormController(formController);
		relatedForms.put(form, relation);
	}

	public void removeRelatedForm(IWebFormController form)
	{
		form.setParentFormController(null);
		relatedForms.remove(form);
	}

	private void setupModificationListener(String dataprovider)
	{
		if (!isFormScopeListener && isFormDataprovider(dataprovider))
		{
			formController.getFormScope().getModificationSubject().addModificationListener(this);
			isFormScopeListener = true;
		}
		else if (!isGlobalScopeListener && isGlobalDataprovider(dataprovider))
		{
			formController.getApplication().getScriptEngine().getScopesScope().getModificationSubject().addModificationListener(this);
			isGlobalScopeListener = true;
		}
	}

	public void addTaggedProperty(final WebFormComponent component, final String beanTaggedProperty, String propertyValue)
	{
		Text.processTags(propertyValue, new ITagResolver()
		{
			@Override
			public String getStringValue(String name)
			{
				String dp = name;
				if (dp.startsWith(ScriptVariable.GLOBALS_DOT_PREFIX))
				{
					dp = ScriptVariable.SCOPES_DOT_PREFIX + dp;
				}
				Map<WebFormComponent, List<String>> map = dataProviderToComponentWithTags.get(dp);
				if (map == null)
				{
					map = new HashMap<WebFormComponent, List<String>>();
					dataProviderToComponentWithTags.put(dp, map);
				}

//				recordAwareComponents.add(component);
				List<String> props = map.get(component);
				if (props == null)
				{
					props = new ArrayList<>();
					map.put(component, props);
				}
				props.add(beanTaggedProperty);
				if (formController != null) setupModificationListener(dp);
				return dp;
			}
		});
	}

	public void addDataLinkedProperty(WebFormComponent component, String propertyName, TargetDataLinks targetDataLinks)
	{
		if (targetDataLinks == TargetDataLinks.NOT_LINKED_TO_DATA) return;

		Pair<WebFormComponent, String> propertyIdentifier = new Pair<>(component, propertyName);
		if (targetDataLinks.dataProviderIDs != null)
		{
			for (String dpID : targetDataLinks.dataProviderIDs)
			{
				List<Pair<WebFormComponent, String>> allLinksOfDP = dataProviderToLinkedComponentProperty.get(dpID);
				if (allLinksOfDP == null)
				{
					allLinksOfDP = new ArrayList<>();
					dataProviderToLinkedComponentProperty.put(dpID, allLinksOfDP);
				}
				if (!allLinksOfDP.contains(propertyIdentifier)) allLinksOfDP.add(propertyIdentifier);

				if (formController != null) setupModificationListener(dpID); // see if we need to listen to global/form scope changes
			}
		}

		allComponentPropertiesLinkedToData.add(propertyIdentifier);
	}

	public void removeRecordAwareComponent(WebFormComponent component)
	{
		// TODO remove modification listeners for form/global scopes if needed...

		Iterator<Pair<WebFormComponent, String>> it = allComponentPropertiesLinkedToData.iterator();
		while (it.hasNext())
		{
			Pair<WebFormComponent, String> item = it.next();
			if (item.getLeft() == component) it.remove();
		}
		Iterator<List<Pair<WebFormComponent, String>>> it1 = dataProviderToLinkedComponentProperty.values().iterator();
		while (it1.hasNext())
		{
			List<Pair<WebFormComponent, String>> lList = it1.next();
			it = lList.iterator();
			while (it.hasNext())
			{
				Pair<WebFormComponent, String> item = it.next();
				if (item.getLeft() == component)
				{
					it.remove();
					if (lList.size() == 0) it1.remove();
				}
			}
		}
	}

	public void setRecord(IRecord record, boolean fireChangeEvent)
	{
		if (settingRecord)
		{
			if (record != this.record)
			{
				throw new IllegalStateException("Record " + record + " is being set on DAL when record: " + this.record + " is being processed");
			}
			return;
		}
		try
		{
			settingRecord = true;
			if (this.record != null)
			{
				this.record.removeModificationListener(this);
			}
			this.record = (IRecordInternal)record;

			if (this.record != null)
			{
				pushChangedValues(null, fireChangeEvent);
				this.record.addModificationListener(this);
			}

			for (IWebFormController form : relatedForms.keySet())
			{
				if (form.isFormVisible())
				{
					form.loadRecords(record != null ? record.getRelatedFoundSet(relatedForms.get(form)) : null);
				}
			}
		}
		finally
		{
			settingRecord = false;
		}
	}

	public IRecordInternal getRecord()
	{
		return record;
	}

	private boolean updateTagValue(Map<WebFormComponent, List<String>> components)
	{
		boolean changed = false;
		for (Map.Entry<WebFormComponent, List<String>> entry : components.entrySet())
		{
			WebFormComponent component = entry.getKey();
			for (String taggedProp : entry.getValue())
			{
				String initialPropValue = (String)component.getInitialProperty(taggedProp); // once this CODE IS REMOVED, also remove component.getInitialProperty please
				String tagValue = Text.processTags(initialPropValue, DataAdapterList.this);
				changed = component.setProperty(taggedProp, tagValue) || changed;
			}
		}

		return changed;
	}

	protected boolean isFormDataprovider(String dataprovider)
	{
		if (dataprovider == null) return false;
		FormScope fs = formController.getFormScope();
		return fs.has(dataprovider, fs);
	}

	protected boolean isGlobalDataprovider(String dataprovider)
	{
		if (dataprovider == null) return false;
		ScopesScope ss = formController.getApplication().getScriptEngine().getScopesScope();
		Pair<String, String> scope = ScopesUtils.getVariableScope(dataprovider);
		if (scope.getLeft() != null)
		{
			GlobalScope gs = ss.getGlobalScope(scope.getLeft());
			return gs != null && gs.has(scope.getRight(), gs);
		}

		return false;
	}

	private void pushChangedValues(String dataProvider, boolean fireChangeEvent)
	{
		boolean isFormDP = isFormDataprovider(dataProvider);
		boolean isGlobalDP = isGlobalDataprovider(dataProvider);

		boolean changed = false;
		if (dataProvider == null)
		{
			// announce to all - we don't know exactly what changed; maybe all DPs changed
			for (Pair<WebFormComponent, String> x : allComponentPropertiesLinkedToData)
			{
				Object rawPropValue = x.getLeft().getRawPropertyValue(x.getRight());
				if (rawPropValue instanceof IServoyAwarePropertyValue) ((IServoyAwarePropertyValue)rawPropValue).dataProviderOrRecordChanged(record, null,
					isFormDP, isGlobalDP, fireChangeEvent);
			}

			for (Entry<String, Map<WebFormComponent, List<String>>> entry : dataProviderToComponentWithTags.entrySet())
			{
				changed = updateTagValue(entry.getValue()) || changed;
			}
		}
		else
		{
			// announce to all - we don't know exactly what changed; maybe all DPs changed
			List<Pair<WebFormComponent, String>> interestedComponentProperties = dataProviderToLinkedComponentProperty.get(dataProvider);
			if (interestedComponentProperties != null)
			{
				for (Pair<WebFormComponent, String> x : interestedComponentProperties)
				{
					Object rawPropValue = x.getLeft().getRawPropertyValue(x.getRight());
					if (rawPropValue instanceof IServoyAwarePropertyValue) ((IServoyAwarePropertyValue)rawPropValue).dataProviderOrRecordChanged(record,
						dataProvider, isFormDP, isGlobalDP, fireChangeEvent);
				}
			}

			if ((isFormDP || isGlobalDP) && dataProviderToComponentWithTags.containsKey(dataProvider))
			{
				changed = updateTagValue(dataProviderToComponentWithTags.get(dataProvider)) || changed;
			}
		}

		if (fireChangeEvent && changed)
		{
			getApplication().getChangeListener().valueChanged();
		}
	}

	@Override
	public void valueChanged(ModificationEvent e)
	{
		if (getForm().isFormVisible())
		{
			pushChangedValues(e.getName(), true);
		}
	}

	public void pushChanges(WebFormComponent webComponent, String beanProperty)
	{
		pushChanges(webComponent, beanProperty, webComponent.getProperty(beanProperty));
	}

	/**
	 * Get the dataProviderID from the runtime property.
	 * NOTE: it's not taken directly from FormElement because 'beanProperty' might contain dots (a dataprovider nested somewhere in another property) - and BaseWebObject deals with that correctly.
	 */
	protected String getDataProviderID(WebFormComponent webComponent, String beanProperty)
	{
		Object propertyValue = webComponent.getProperty(beanProperty);
		if (propertyValue instanceof DataproviderTypeSabloValue) return ((DataproviderTypeSabloValue)propertyValue).getDataProviderID();
		return null;
	}

	public void pushChanges(WebFormComponent webComponent, String beanProperty, Object newValue)
	{
		// TODO should this all (svy-apply/push) move to DataProviderType client/server side implementation instead of specialized calls?

		String dataProviderID = getDataProviderID(webComponent, beanProperty);
		if (dataProviderID == null)
		{
			Debug.log("apply called on a property that is not bound to a dataprovider: " + beanProperty + ", value: " + newValue + " of component: " +
				webComponent);
			return;
		}

		if (newValue instanceof DataproviderTypeSabloValue) newValue = ((DataproviderTypeSabloValue)newValue).getValue();

		// TODO should this always be tried? (Calendar field has no push for edit, because it doesn't use svyAutoApply)
		// but what if it was a global or form variable?
		if (record == null || record.startEditing())
		{
			Object v;
			// if the value is a map, then it means, that a set of related properties needs to be updated,
			// ex. newValue = {"" : "image_data", "_filename": "pic.jpg", "_mimetype": "image/jpeg"}
			// will update property with "image_data", property_filename with "pic.jpg" and property_mimetype with "image/jpeg"
			if (newValue instanceof HashMap)
			{
				v = ((HashMap< ? , ? >)newValue).get(""); // defining value
				Iterator<Entry< ? , ? >> newValueIte = ((HashMap)newValue).entrySet().iterator();
				while (newValueIte.hasNext())
				{
					Entry< ? , ? > e = newValueIte.next();
					if (!"".equals(e.getKey()))
					{
						com.servoy.j2db.dataprocessing.DataAdapterList.setValueObject(record, formController.getFormScope(), dataProviderID + e.getKey(),
							e.getValue());
					}
				}
			}
			else
			{
				v = newValue;
			}
			Object oldValue = com.servoy.j2db.dataprocessing.DataAdapterList.setValueObject(record, formController.getFormScope(), dataProviderID, v);
			String onDataChange = ((DataproviderConfig)webComponent.getFormElement().getWebComponentSpec().getProperty(beanProperty).getConfig()).getOnDataChange();
			if (onDataChange != null && webComponent.hasEvent(onDataChange))
			{
				JSONObject event = EventExecutor.createEvent(onDataChange);
				Object returnValue = webComponent.executeEvent(onDataChange, new Object[] { oldValue, v, event });
				String onDataChangeCallback = ((DataproviderConfig)webComponent.getFormElement().getWebComponentSpec().getProperty(beanProperty).getConfig()).getOnDataChangeCallback();
				if (onDataChangeCallback != null)
				{
					WebComponentApiDefinition call = new WebComponentApiDefinition(onDataChangeCallback);
					call.addParameter(new PropertyDescription("event", TypesRegistry.getType("object")));
					call.addParameter(new PropertyDescription("returnValue", TypesRegistry.getType("object")));
					webComponent.invokeApi(call, new Object[] { event, returnValue });
				}
			}
		}
	}

	public void startEdit(WebFormComponent webComponent, String property)
	{
		String dataProviderID = getDataProviderID(webComponent, property);
		if (dataProviderID == null)
		{
			Debug.log("startEdit called on a property that is not bound to a dataprovider: " + property + " of component: " + webComponent);
			return;
		}
		if (record != null && !ScopesUtils.isVariableScope(dataProviderID))
		{
			record.startEditing();
		}
	}

	public String getStringValue(String name)
	{
		String stringValue = TagResolver.formatObject(getValueObject(record, name), getApplication().getLocale(), getApplication().getSettings());
		return processValue(stringValue, name, null); // TODO last param ,IDataProviderLookup, should be implemented
	}

	public static String processValue(String stringValue, String dataProviderID, IDataProviderLookup dataProviderLookup)
	{
		if (stringValue == null)
		{
			if ("selectedIndex".equals(dataProviderID) || isCountOrAvgOrSumAggregateDataProvider(dataProviderID, dataProviderLookup)) //$NON-NLS-1$
			{
				return "0"; //$NON-NLS-1$
			}
		}
		return stringValue;
	}

	// helper method; not static because needs form scope
	public Object getValueObject(IRecord record, String dataProviderId)
	{
		return record.getValue(dataProviderId); //TODO scopes support
		//	return getValueObject(record, getFormScope(), dataProviderId);
	}

	public boolean isCountOrAvgOrSumAggregateDataProvider(IDataAdapter dataAdapter)
	{
		return isCountOrAvgOrSumAggregateDataProvider(dataAdapter.getDataProviderID(), null);
	}

	private static boolean isCountOrAvgOrSumAggregateDataProvider(String dataProvider, IDataProviderLookup dataProviderLookup)
	{
		try
		{
			if (dataProviderLookup == null)
			{
				return false;
			}
			IDataProvider dp = dataProviderLookup.getDataProvider(dataProvider);
			if (dp instanceof ColumnWrapper)
			{
				dp = ((ColumnWrapper)dp).getColumn();
			}
			if (dp instanceof AggregateVariable)
			{
				int aggType = ((AggregateVariable)dp).getType();
				return aggType == QueryAggregate.COUNT || aggType == QueryAggregate.AVG || aggType == QueryAggregate.SUM;
			}
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}

		return false;
	}


	@Override
	public void setFindMode(boolean findMode)
	{
//		this.findMode = findMode;

		getApplication().getWebsocketSession().getService("$servoyInternal").executeAsyncServiceCall(
			"setFindMode",
			new Object[] { formController.getName(), Boolean.valueOf(findMode), Boolean.valueOf(!Boolean.TRUE.equals(getApplication().getClientProperty(
				IApplication.LEAVE_FIELDS_READONLY_IN_FIND_MODE))) });

	}
}
