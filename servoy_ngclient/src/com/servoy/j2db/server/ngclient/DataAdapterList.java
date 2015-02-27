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
import org.sablo.Container;
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
import com.servoy.j2db.query.QueryAggregate;
import com.servoy.j2db.scripting.FormScope;
import com.servoy.j2db.scripting.GlobalScope;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.scripting.ScopesScope;
import com.servoy.j2db.server.ngclient.component.EventExecutor;
import com.servoy.j2db.server.ngclient.property.DataproviderConfig;
import com.servoy.j2db.server.ngclient.property.IDataLinkedPropertyValue;
import com.servoy.j2db.server.ngclient.property.IFindModeAwarePropertyValue;
import com.servoy.j2db.server.ngclient.property.types.DataproviderTypeSabloValue;
import com.servoy.j2db.server.ngclient.property.types.IDataLinkedType.TargetDataLinks;
import com.servoy.j2db.server.ngclient.property.types.NGConversions;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.ScopesUtils;


public class DataAdapterList implements IModificationListener, ITagResolver, IDataAdapterList
{

	// properties that are interested in a specific dataproviderID chaning
	protected final Map<String, List<IDataLinkedPropertyValue>> dataProviderToLinkedComponentProperty = new HashMap<>(); // dataProviderID -> [(comp, propertyName)]

	// all data-linked properties - contains 'dataProviderToLinkedComponentProperty' as well as other ones that are interested in any DP change
	protected final List<IDataLinkedPropertyValue> allComponentPropertiesLinkedToData = new ArrayList<>(); // [(comp, propertyName), ...]

	protected final List<IFindModeAwarePropertyValue> findModeAwareProperties = new ArrayList<>();

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

	private static boolean containsForm(IWebFormUI parent, IWebFormUI child)
	{
		Object childParentContainer = child.getParentContainer();
		if (childParentContainer instanceof WebFormComponent)
		{
			Container p = ((WebFormComponent)childParentContainer).getParent();
			if (p instanceof IWebFormUI)
			{
				if (p.equals(parent))
				{
					return true;
				}
				else return containsForm(parent, (IWebFormUI)p);
			}
		}

		return false;
	}

	public void addRelatedForm(IWebFormController form, String relation, boolean shouldUpdateParentFormController)
	{
		if (shouldUpdateParentFormController)
		{
			form.setParentFormController(formController);
		}
		else
		{
			form.getFormUI().getDataAdapterList().addParentRelatedForm(getForm());
		}

		for (Entry<IWebFormController, String> relatedFormEntry : relatedForms.entrySet())
		{
			IWebFormController relatedForm = relatedFormEntry.getKey();
			String relatedFormRelation = relatedFormEntry.getValue();
			if (relatedFormRelation.startsWith(relation) && relatedFormRelation.length() > relation.length())
			{
				if (!containsForm(form.getFormUI(), relatedForm.getFormUI()))
				{
					form.getFormUI().getDataAdapterList().addRelatedForm(relatedForm, relatedFormRelation.substring(relation.length() + 1), false);
				}
			}
			else if (relation.startsWith(relatedFormRelation) && relation.length() > relatedFormRelation.length())
			{
				if (!containsForm(relatedForm.getFormUI(), form.getFormUI()))
				{
					relatedForm.getFormUI().getDataAdapterList().addRelatedForm(form, relation.substring(relatedFormRelation.length() + 1), false);
				}
			}
		}

		relatedForms.put(form, relation);
	}

	public void removeRelatedForm(IWebFormController form, boolean shouldUpdateParentFormController)
	{
		if (shouldUpdateParentFormController)
		{
			form.setParentFormController(null);
		}
		else
		{
			form.getFormUI().getDataAdapterList().removeParentRelatedForm(getForm());
		}
		relatedForms.remove(form);
		for (Object relWFC : form.getFormUI().getDataAdapterList().getParentRelatedForms().toArray())
		{
			((IWebFormController)relWFC).getFormUI().getDataAdapterList().removeRelatedForm(form, false);
		}
	}

	public Map<IWebFormController, String> getRelatedForms()
	{
		return relatedForms;
	}

	private final ArrayList<IWebFormController> parentRelatedForms = new ArrayList<IWebFormController>();

	public void addParentRelatedForm(IWebFormController form)
	{
		if (parentRelatedForms.indexOf(form) == -1) parentRelatedForms.add(form);
	}

	public void removeParentRelatedForm(IWebFormController form)
	{
		parentRelatedForms.remove(form);
	}

	public List<IWebFormController> getParentRelatedForms()
	{
		return parentRelatedForms;
	}

	private void setupModificationListener(String dataprovider)
	{
		if (!isFormScopeListener && (isFormDataprovider(dataprovider) || dataprovider == null))
		{
			formController.getFormScope().getModificationSubject().addModificationListener(this);
			isFormScopeListener = true;
		}
		if (!isGlobalScopeListener && (isGlobalDataprovider(dataprovider) || dataprovider == null))
		{
			formController.getApplication().getScriptEngine().getScopesScope().getModificationSubject().addModificationListener(this);
			isGlobalScopeListener = true;
		}
	}

	public void addDataLinkedProperty(IDataLinkedPropertyValue propertyValue, TargetDataLinks targetDataLinks)
	{
		if (targetDataLinks == TargetDataLinks.NOT_LINKED_TO_DATA || targetDataLinks == null) return;

		String[] dataproviders = targetDataLinks.dataProviderIDs;
		if (dataproviders == null)
		{
			dataproviders = new String[] { null };
		}
		for (String dpID : dataproviders)
		{
			List<IDataLinkedPropertyValue> allLinksOfDP = dataProviderToLinkedComponentProperty.get(dpID);
			if (allLinksOfDP == null)
			{
				allLinksOfDP = new ArrayList<>();
				dataProviderToLinkedComponentProperty.put(dpID, allLinksOfDP);
			}
			if (!allLinksOfDP.contains(propertyValue)) allLinksOfDP.add(propertyValue);

			if (formController != null) setupModificationListener(dpID); // see if we need to listen to global/form scope changes
		}

		allComponentPropertiesLinkedToData.add(propertyValue);
	}

	public void removeDataLinkedProperty(IDataLinkedPropertyValue propertyValue)
	{
		Iterator<List<IDataLinkedPropertyValue>> it = dataProviderToLinkedComponentProperty.values().iterator();
		while (it.hasNext())
		{
			List<IDataLinkedPropertyValue> x = it.next();
			if (x.remove(propertyValue))
			{
				if (x.size() == 0) it.remove();
			}
		}
		// TODO keep track & unregister when needed global/form scope listeners: so undo setupModificationListener(dpID);
		allComponentPropertiesLinkedToData.remove(propertyValue);
	}

	public void addFindModeAwareProperty(IFindModeAwarePropertyValue propertyValue)
	{
		findModeAwareProperties.add(propertyValue);
	}

	public void removeFindModeAwareProperty(IFindModeAwarePropertyValue propertyValue)
	{
		findModeAwareProperties.remove(propertyValue);
	}

	public void componentDisposed(WebFormComponent component)
	{
		// TODO remove modification listeners for form/global scopes if needed...
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
			for (IDataLinkedPropertyValue x : allComponentPropertiesLinkedToData)
			{
				x.dataProviderOrRecordChanged(record, null, isFormDP, isGlobalDP, fireChangeEvent);
			}
		}
		else
		{
			List<IDataLinkedPropertyValue> interestedComponentProperties = dataProviderToLinkedComponentProperty.get(dataProvider);
			if (interestedComponentProperties == null)
			{
				interestedComponentProperties = dataProviderToLinkedComponentProperty.get(null);
			}
			else
			{
				List<IDataLinkedPropertyValue> listenToAllComponentProperties = dataProviderToLinkedComponentProperty.get(null);
				if (listenToAllComponentProperties != null && listenToAllComponentProperties.size() > 0)
				{
					interestedComponentProperties = new ArrayList<IDataLinkedPropertyValue>(interestedComponentProperties);
					for (IDataLinkedPropertyValue dataLink : listenToAllComponentProperties)
					{
						if (!interestedComponentProperties.contains(dataLink)) interestedComponentProperties.add(dataLink);
					}
				}
			}

			if (interestedComponentProperties != null)
			{
				for (IDataLinkedPropertyValue x : interestedComponentProperties)
				{
					x.dataProviderOrRecordChanged(record, dataProvider, isFormDP, isGlobalDP, fireChangeEvent);
				}
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
				Object returnValue = null;
				Exception exception = null;
				try
				{
					returnValue = webComponent.executeEvent(onDataChange, new Object[] { oldValue, v, event });
				}
				catch (Exception e)
				{
					Debug.error("Error during onDataChange webComponent=" + webComponent, e);
					exception = e;
				}
				String onDataChangeCallback = ((DataproviderConfig)webComponent.getFormElement().getWebComponentSpec().getProperty(beanProperty).getConfig()).getOnDataChangeCallback();
				if (onDataChangeCallback != null)
				{
					WebComponentApiDefinition call = new WebComponentApiDefinition(onDataChangeCallback);
					call.addParameter(new PropertyDescription("event", TypesRegistry.getType("object")));
					call.addParameter(new PropertyDescription("returnValue", TypesRegistry.getType("object")));
					call.addParameter(new PropertyDescription("exception", TypesRegistry.getType("object")));
					webComponent.invokeApi(call, new Object[] { event, returnValue, exception == null ? null : exception.getMessage() });
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
	public Object getValueObject(IRecord recordToUse, String dataProviderId)
	{
//		return record.getValue(dataProviderId);
		return com.servoy.j2db.dataprocessing.DataAdapterList.getValueObject(recordToUse, formController.getFormScope(), dataProviderId); // needed for tagString processing (so not just record values but also variables)
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
		for (IFindModeAwarePropertyValue x : findModeAwareProperties)
		{
			x.findModeChanged(findMode);
		}

		getApplication().getWebsocketSession().getClientService("$servoyInternal").executeAsyncServiceCall(
			"setFindMode",
			new Object[] { formController.getName(), Boolean.valueOf(findMode), Boolean.valueOf(!Boolean.TRUE.equals(getApplication().getClientProperty(
				IApplication.LEAVE_FIELDS_READONLY_IN_FIND_MODE))) });
	}

	public void destroy()
	{
		if (record != null)
		{
			setRecord(null, false);
		}
		if (formController != null && formController.getFormScope() != null)
		{
			formController.getFormScope().getModificationSubject().removeModificationListener(this);
		}
		if (formController != null && formController.getApplication() != null && formController.getApplication().getScriptEngine() != null)
		{
			IExecutingEnviroment er = formController.getApplication().getScriptEngine();
			if (er.getScopesScope() != null)
			{
				er.getScopesScope().getModificationSubject().removeModificationListener(this);
			}
		}
		dataProviderToLinkedComponentProperty.clear();
		allComponentPropertiesLinkedToData.clear();
		findModeAwareProperties.clear();
	}
}
