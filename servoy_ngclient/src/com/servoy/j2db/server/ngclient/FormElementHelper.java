/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.j2db.server.ngclient;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.property.CustomJSONArrayType;

import com.servoy.j2db.AbstractActiveSolutionHandler;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.BaseComponent;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.GraphicalComponent;
import com.servoy.j2db.persistence.IAnchorConstants;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.ISupportScrollbars;
import com.servoy.j2db.persistence.ISupportTabSeq;
import com.servoy.j2db.persistence.Part;
import com.servoy.j2db.persistence.PositionComparator;
import com.servoy.j2db.server.ngclient.property.ComponentPropertyType;
import com.servoy.j2db.server.ngclient.property.types.NGTabSeqPropertyType;
import com.servoy.j2db.server.ngclient.property.types.PropertyPath;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServer;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;

/**
 * Class used to cache FormElements that can be cached.
 * Also contains more code that is useful when working with FormElements.
 *
 * @author acostescu
 */
public class FormElementHelper
{

	public final static FormElementHelper INSTANCE = new FormElementHelper();

	// todo identity key? SolutionModel persist shouldn't be cached at all?
	private final ConcurrentMap<String, FlattenedSolution> globalFlattendSolutions = new ConcurrentHashMap<>();
	private final ConcurrentMap<IPersist, FormElement> persistWrappers = new ConcurrentHashMap<>();
	private final ConcurrentMap<Form, Map<ISupportTabSeq, Integer>> formTabSequences = new ConcurrentHashMap<>();

	public List<FormElement> getFormElements(Iterator<IPersist> iterator, IServoyDataConverterContext context)
	{
		if (Boolean.valueOf(Settings.getInstance().getProperty("servoy.internal.reloadSpecsAllTheTime", "false")).booleanValue())
		{
			WebComponentSpecProvider.reload();
		}
		List<FormElement> lst = new ArrayList<>();
		while (iterator.hasNext())
		{
			IPersist persist = iterator.next();
			if (persist instanceof IFormElement)
			{
				lst.add(getFormElement((IFormElement)persist, context, null));
			}
		}
		return lst;
	}

	public FormElement getFormElement(IFormElement formElement, IServoyDataConverterContext context, PropertyPath propertyPath)
	{
		return getFormElement(formElement, context.getSolution(), propertyPath, (context.getApplication() != null && context.getApplication().isInDesigner()));
	}

	public FormElement getFormElement(IFormElement formElement, FlattenedSolution fs, PropertyPath propertyPath, final boolean designer)
	{
		// dont cache if solution model is used (media,valuelist,relations can be changed for a none changed element)
		if (designer || (fs.getSolutionCopy(false) != null))
		{
			if (propertyPath == null)
			{
				propertyPath = new PropertyPath();
				propertyPath.setShouldAddElementName();
			}
			if (formElement instanceof BodyPortal) return createBodyPortalFormElement((BodyPortal)formElement, fs, designer);
			else return new FormElement(formElement, fs, propertyPath, designer);
		}
		FormElement persistWrapper = persistWrappers.get(formElement);
		if (persistWrapper == null)
		{
			if (propertyPath == null)
			{
				propertyPath = new PropertyPath();
				propertyPath.setShouldAddElementName();
			}
			if (formElement instanceof BodyPortal) persistWrapper = createBodyPortalFormElement((BodyPortal)formElement, getSharedFlattenedSolution(fs),
				designer);
			else persistWrapper = new FormElement(formElement, getSharedFlattenedSolution(fs), propertyPath, false);
			FormElement existing = persistWrappers.putIfAbsent(formElement, persistWrapper);
			if (existing != null)
			{
				persistWrapper = existing;
			}
		}
		return persistWrapper;
	}

	private FlattenedSolution getSharedFlattenedSolution(FlattenedSolution fs)
	{
		FlattenedSolution flattenedSolution = globalFlattendSolutions.get(fs.getName());
		if (flattenedSolution == null)
		{
			try
			{
				flattenedSolution = new FlattenedSolution(true);
				flattenedSolution.setSolution(fs.getMainSolutionMetaData(), false, true,
					new AbstractActiveSolutionHandler(ApplicationServerRegistry.getService(IApplicationServer.class))
					{
						@Override
						public IRepository getRepository()
						{
							return ApplicationServerRegistry.get().getLocalRepository();
						}
					});
				FlattenedSolution alreadyCreated = globalFlattendSolutions.putIfAbsent(flattenedSolution.getName(), flattenedSolution);
				if (alreadyCreated != null)
				{
					flattenedSolution.close(null);
					flattenedSolution = alreadyCreated;
				}
			}
			catch (Exception e)
			{
				throw new RuntimeException("Can't create FlattenedSolution for: " + fs, e);
			}
		}
		return flattenedSolution;
	}

	private FormElement createBodyPortalFormElement(BodyPortal listViewPortal, FlattenedSolution fs, final boolean isInDesigner)
	{
		Form form = listViewPortal.getForm();
		Part bodyPart = null;
		for (Part prt : Utils.iterate(form.getParts()))
		{
			if (prt.getPartType() == Part.BODY)
			{
				bodyPart = prt;
				break;
			}
		}
		if (bodyPart != null)
		{
			try
			{
				String name = "svy_lvp_" + form.getName();
				int startPos = form.getPartStartYPos(bodyPart.getID());
				int endPos = bodyPart.getHeight();
				int bodyheight = endPos - startPos;

				JSONObject portal = new JSONObject();
				portal.put("name", name);
				portal.put("multiLine", !listViewPortal.isTableview());
				portal.put("rowHeight", !listViewPortal.isTableview() ? bodyheight : getRowHeight(form));
				portal.put("scrollbars", form.getScrollbars());
				if (listViewPortal.isTableview())
				{
					int headerHeight = 30;
					if (form.hasPart(Part.HEADER))
					{
						headerHeight = 0;
					}
					portal.put("headerHeight", headerHeight);
					portal.put("sortable", form.getOnSortCmdMethodID() != -1);
				}

				portal.put("anchors", IAnchorConstants.ALL);
				JSONObject location = new JSONObject();
				location.put("x", 0);
				location.put("y", isInDesigner ? startPos : 0);
				portal.put("location", location);
				JSONObject size = new JSONObject();
//				size.put("width", (listViewPortal.isTableview() && !fillsWidth) ? getGridWidth(form) : form.getWidth());
				size.put("width", form.getWidth());
				size.put("height", bodyheight);
				portal.put("size", size);
				portal.put("visible", listViewPortal.getVisible());
				portal.put("enabled", listViewPortal.getEnabled());
				portal.put("childElements", new JSONArray()); // empty contents; will be updated afterwards directly with form element values for components

				JSONObject relatedFoundset = new JSONObject();
				relatedFoundset.put("foundsetSelector", "");
				portal.put("relatedFoundset", relatedFoundset);

				PropertyPath propertyPath = new PropertyPath();
				propertyPath.setShouldAddElementName();
				FormElement portalFormElement = new FormElement("servoydefault-portal", portal, form, name, fs, propertyPath, isInDesigner);
				PropertyDescription pd = portalFormElement.getWebComponentSpec().getProperties().get("childElements");
				if (pd != null) pd = ((CustomJSONArrayType< ? , ? >)pd.getType()).getCustomJSONTypeDefinition();
				if (pd == null)
				{
					Debug.error(new RuntimeException("Cannot find component definition special type to use for portal."));
					return null;
				}
				ComponentPropertyType type = ((ComponentPropertyType)pd.getType());

				Map<String, Object> portalFormElementProperties = new HashMap<>(portalFormElement.getRawPropertyValues());
				portalFormElementProperties.put("offsetY", startPos);
				portalFormElementProperties.put("partHeight", bodyPart.getHeight());
				// now put real child component form element values in "childElements"
				Iterator<IPersist> it = form.getAllObjects(PositionComparator.XY_PERSIST_COMPARATOR);
				List<Object> children = new ArrayList<>(); // contains actually ComponentTypeFormElementValue objects
				List<String> headersText = new ArrayList<>();
				propertyPath.add(portalFormElement.getName());
				propertyPath.add("childElements");

				// it's a generated table-view form portal (BodyPortal); we just
				// have to set the Portal's tabSeq to the first one of it's children for it to work properly
				int minBodyPortalTabSeq = -2;

				while (it.hasNext())
				{
					IPersist persist = it.next();
					if (persist instanceof IFormElement)
					{
						Point loc = ((IFormElement)persist).getLocation();
						if (startPos <= loc.y && endPos >= loc.y)
						{
							if (listViewPortal.isTableview() && persist instanceof GraphicalComponent && ((GraphicalComponent)persist).getLabelFor() != null) continue;
							propertyPath.add(children.size());
							FormElement fe = getFormElement((IFormElement)persist, fs, propertyPath, isInDesigner);
							if (listViewPortal.isTableview())
							{
								String elementName = fe.getName();
								boolean hasLabelFor = false;
								Iterator<GraphicalComponent> graphicalComponents = form.getGraphicalComponents();
								while (graphicalComponents.hasNext())
								{
									GraphicalComponent gc = graphicalComponents.next();
									if (gc.getLabelFor() != null && Utils.equalObjects(elementName, gc.getLabelFor()) && startPos <= gc.getLocation().y &&
										endPos >= gc.getLocation().y)
									{
										headersText.add(gc.getText());
										hasLabelFor = true;
										break;
									}
								}
								if (!hasLabelFor)
								{
									if (fe.getPropertyValue("text") != null)
									{
										// legacy behavior, take text property
										headersText.add(fe.getPropertyValue("text").toString());
									}
									else
									{
										headersText.add(null);
									}
								}
							}
							children.add(type.getFormElementValue(null, pd, propertyPath, fe, fs));
							propertyPath.backOneLevel();

							Collection<PropertyDescription> tabSequenceProperties = fe.getWebComponentSpec().getProperties(NGTabSeqPropertyType.NG_INSTANCE);
							for (PropertyDescription tabSeqProperty : tabSequenceProperties)
							{
								String tabSeqPropertyName = tabSeqProperty.getName();
								Integer tabSeqVal = (Integer)fe.getPropertyValue(tabSeqPropertyName);
								if (tabSeqVal == null) tabSeqVal = Integer.valueOf(0); // default is 0 == DEFAULT tab sequence
								if (minBodyPortalTabSeq < 0 || minBodyPortalTabSeq > tabSeqVal.intValue()) minBodyPortalTabSeq = tabSeqVal.intValue();
							}
						}

					}
				}
				propertyPath.backOneLevel();
				propertyPath.backOneLevel();
				portalFormElementProperties.put("childElements", children.toArray());
				if (listViewPortal.isTableview())
				{
					portalFormElementProperties.put("columnHeaders", headersText.toArray());
				}

				portalFormElementProperties.put("tabSeq", Integer.valueOf(minBodyPortalTabSeq)); // table view tab seq. is the minimum of it's children tabSeq'es

				portalFormElement.updatePropertyValuesDontUse(portalFormElementProperties);

				return portalFormElement;
			}
			catch (JSONException ex)
			{
				Debug.error("Cannot create list view portal component", ex);
			}
		}

		return null;
	}

	private boolean fillsWidth(Form form)
	{
		if ((form.getScrollbars() & ISupportScrollbars.HORIZONTAL_SCROLLBAR_NEVER) == ISupportScrollbars.HORIZONTAL_SCROLLBAR_NEVER)
		{
			Part part = getBodyPart(form);
			int startPos = form.getPartStartYPos(part.getID());
			int endPos = part.getHeight();
			Iterator<IPersist> it = form.getAllObjects(PositionComparator.XY_PERSIST_COMPARATOR);
			while (it.hasNext())
			{
				IPersist persist = it.next();
				if (persist instanceof GraphicalComponent && ((GraphicalComponent)persist).getLabelFor() != null) continue;
				if (persist instanceof BaseComponent)
				{
					BaseComponent bc = (BaseComponent)persist;
					if ((bc.getAnchors() & (IAnchorConstants.WEST + IAnchorConstants.EAST)) == (IAnchorConstants.WEST + IAnchorConstants.EAST))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	public Part getBodyPart(Form form)
	{
		for (Part prt : Utils.iterate(form.getParts()))
		{
			if (prt.getPartType() == Part.BODY)
			{
				return prt;
			}
		}
		return null;
	}

	public boolean hasExtraParts(Form form)
	{
		int count = 0;
		for (Part prt : Utils.iterate(form.getParts()))
		{
			count++;
			if (count >= 2)
			{
				return true;
			}
		}
		return false;
	}

	private int getGridWidth(Form form)
	{
		int rowWidth = 0;
		Part part = getBodyPart(form);
		int startPos = form.getPartStartYPos(part.getID());
		int endPos = part.getHeight();
		Iterator<IPersist> it = form.getAllObjects(PositionComparator.XY_PERSIST_COMPARATOR);
		while (it.hasNext())
		{
			IPersist persist = it.next();
			if (persist instanceof GraphicalComponent && ((GraphicalComponent)persist).getLabelFor() != null) continue;
			if (persist instanceof BaseComponent)
			{
				BaseComponent bc = (BaseComponent)persist;
				Point location = bc.getLocation();
				if (startPos <= location.y && endPos >= location.y)
				{
					rowWidth += bc.getSize().width + 0.5;//+borders
				}
			}
		}
		return rowWidth;
	}

	private int getRowHeight(Form form)
	{
		int rowHeight = 0;
		Part part = getBodyPart(form);
		int startPos = form.getPartStartYPos(part.getID());
		int endPos = part.getHeight();
		Iterator<IPersist> it = form.getAllObjects(PositionComparator.XY_PERSIST_COMPARATOR);
		while (it.hasNext())
		{
			IPersist persist = it.next();
			if (persist instanceof GraphicalComponent && ((GraphicalComponent)persist).getLabelFor() != null) continue;
			if (persist instanceof BaseComponent)
			{
				BaseComponent bc = (BaseComponent)persist;
				Point location = bc.getLocation();
				if (startPos <= location.y && endPos >= location.y)
				{
					if (rowHeight == 0)
					{
						rowHeight = bc.getSize().height;
						break;
					}
				}
			}
		}

		return rowHeight == 0 ? 20 : rowHeight;
	}

	public void reload()
	{
		persistWrappers.clear();
		formTabSequences.clear();
		for (FlattenedSolution fs : globalFlattendSolutions.values())
		{
			fs.close(null);
		}
		globalFlattendSolutions.clear();
		WebComponentSpecProvider.reload();
	}

	public void flush(Collection<IPersist> changes)
	{
		if (changes != null)
		{
			for (IPersist persist : changes)
			{
				persistWrappers.remove(persist);
			}
		}
	}

	/**
	 * Generates a Servoy controlled tab-sequence-index. We try to avoid sending default (0 or null) tabSeq even
	 * for forms that do use default tab sequence in order to avoid problems with nesting default and non-default tabSeq forms.
	 *
	 * @param designValue the value the persist holds for the tabSeq.
	 * @param form the form containing the persist
	 * @param persistIfAvailable the persist. For now, 'component' type properties might work with non-persist-linked FormElements so it could be null.
	 * When those become persist based as well this will never be null.
	 * @return the requested controlled tabSeq (should make tabSeq be identical to the one shown in developer).
	 */
	public Integer getControlledTabSeqReplacementFor(Integer designValue, Form flattenedForm, IPersist persistIfAvailable, FlattenedSolution flattenedSolution) // TODO more args will be needed here such as the tabSeq property name or description
	{
		if (persistIfAvailable == null) return designValue; // TODO this can be removed when we know we'll always have a persist here; currently don't handle this in any way as it's not supported

		boolean formWasModifiedViaSolutionModel = flattenedSolution.hasCopy(flattenedForm);
		Map<ISupportTabSeq, Integer> cachedTabSeq;
		if (formWasModifiedViaSolutionModel) cachedTabSeq = null;
		else cachedTabSeq = formTabSequences.get(flattenedForm);

		if (cachedTabSeq == null)
		{
			cachedTabSeq = new HashMap<ISupportTabSeq, Integer>();

			Iterator<ISupportTabSeq> sequence = flattenedForm.getTabSeqElementsByTabOrder();
			int i = 1;
			while (sequence.hasNext())
			{
				ISupportTabSeq el = sequence.next();
				if (el.getTabSeq() < 0) cachedTabSeq.put(el, Integer.valueOf(-2)); // skip from tab seq.
				else cachedTabSeq.put(el, Integer.valueOf(i++));
			}

			if (!formWasModifiedViaSolutionModel) formTabSequences.putIfAbsent(flattenedForm, cachedTabSeq);
		}

		Integer controlledTabSeq = cachedTabSeq.get(flattenedForm.getChild(persistIfAvailable.getUUID()));
		if (controlledTabSeq == null) controlledTabSeq = Integer.valueOf(-2); // if not in tabSeq, use "skip" value

		return controlledTabSeq;
	}

}
