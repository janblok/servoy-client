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

package com.servoy.j2db.persistence;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.util.UUID;

/**
 * @author lvostinar
 *
 */
public abstract class AbstractContainer extends AbstractBase implements ISupportFormElements, ISupportUpdateableName
{
	protected AbstractContainer(int type, ISupportChilds parent, int element_id, UUID uuid)
	{
		super(type, parent, element_id, uuid);
	}

	/**
	 * FOR INTERNAL USE ONLY, DO NOT CALL. This method shouldn't be called from outside the persistance package!!
	 *
	 * @param arg the form name
	 * @exclude
	 */
	public void setName(String arg)
	{
		setTypedProperty(StaticContentSpecLoader.PROPERTY_NAME, arg);
	}

	/**
	 * FOR INTERNAL USE ONLY, DO NOT CALL.
	 *
	 * @exclude
	 */
	public void updateName(IValidateName validator, String arg) throws RepositoryException
	{
		validator.checkName(arg, getID(), new ValidatorSearchContext(this, IRepository.FORMS), false);
		setTypedProperty(StaticContentSpecLoader.PROPERTY_NAME, arg);
		getRootObject().getChangeHandler().fireIPersistChanged(this);
	}

	/**
	 * The name of the form.
	 */
	@ServoyClientSupport(mc = true, wc = true, sc = true)
	public String getName()
	{
		return getTypedProperty(StaticContentSpecLoader.PROPERTY_NAME);
	}

	/**
	 * Set the container size.
	 *
	 * @param arg the size
	 */
	public void setSize(Dimension arg)
	{
		setTypedProperty(StaticContentSpecLoader.PROPERTY_SIZE, arg);
	}

	public java.awt.Dimension getSize()
	{
		Dimension size = getTypedProperty(StaticContentSpecLoader.PROPERTY_SIZE);
		if (size == null)
		{
			return new java.awt.Dimension(20, 20);
		}
		return size;
	}

	/**
	 * Get the all the fields on a form.
	 *
	 * @return the fields
	 */
	public Iterator<Field> getFields()
	{
		return getObjects(IRepository.FIELDS);
	}

	/**
	 * Create a new field.
	 *
	 * @param location the location
	 * @return the field
	 */
	public Field createNewField(Point location) throws RepositoryException
	{
		Field obj = (Field)getSolution().getChangeHandler().createNewObject(this, IRepository.FIELDS);

		//set all the required properties
		obj.setLocation(location);

		addChild(obj);
		return obj;
	}

	/**
	 * Get the all the child layout containers.
	 *
	 * @return the layout containers
	 */
	public Iterator<LayoutContainer> getLayoutContainers()
	{
		return getObjects(IRepository.LAYOUTCONTAINERS);
	}

	/**
	 * Create a new layout container.
	 *
	 * @return the field
	 */
	public LayoutContainer createNewLayoutContainer() throws RepositoryException
	{
		LayoutContainer obj = (LayoutContainer)getSolution().getChangeHandler().createNewObject(this, IRepository.LAYOUTCONTAINERS);

		addChild(obj);
		return obj;
	}

	public Solution getSolution()
	{
		return (Solution)getRootObject();
	}

	/*
	 * _____________________________________________________________ Methods for Label handling
	 */
	/**
	 * Get all the graphicalComponents from this form.
	 *
	 * @return graphicalComponents
	 */
	public Iterator<GraphicalComponent> getGraphicalComponents()
	{
		return getObjects(IRepository.GRAPHICALCOMPONENTS);
	}

	/**
	 * Create new graphicalComponents.
	 *
	 * @param location
	 * @return the graphicalComponent
	 */
	public GraphicalComponent createNewGraphicalComponent(Point location) throws RepositoryException
	{
		GraphicalComponent obj = (GraphicalComponent)getRootObject().getChangeHandler().createNewObject(this, IRepository.GRAPHICALCOMPONENTS);
		//set all the required properties

		obj.setLocation(location);

		addChild(obj);
		return obj;
	}

	/*
	 * _____________________________________________________________ Methods for Shape handling
	 */

	/**
	 * Get all the shapes.
	 *
	 * @return the shapes
	 */
	public Iterator<Shape> getShapes()
	{
		return getObjects(IRepository.SHAPES);
	}

	/**
	 * Create a new shape.
	 *
	 * @param location
	 * @return the shape
	 */
	public Shape createNewShape(Point location) throws RepositoryException
	{
		Shape obj = (Shape)getRootObject().getChangeHandler().createNewObject(this, IRepository.SHAPES);
		//set all the required properties

		obj.setLocation(location);
		obj.setLineSize(1);
		addChild(obj);
		return obj;
	}

	/*
	 * _____________________________________________________________ Methods for Portal handling
	 */
	/**
	 * Get all the portals from this form.
	 *
	 * @return the portals
	 */
	public Iterator<Portal> getPortals()
	{
		return getObjects(IRepository.PORTALS);
	}

	/**
	 * Create a new portal.
	 *
	 * @param name the name of the new portal
	 * @param location the location of the new portal
	 * @return the new portal
	 */
	public Portal createNewPortal(String name, Point location) throws RepositoryException
	{
		Portal obj = (Portal)getRootObject().getChangeHandler().createNewObject(this, IRepository.PORTALS);
		//set all the required properties

		obj.setLocation(location);
		obj.setName(name == null ? "untitled" : name); //$NON-NLS-1$

		addChild(obj);
		return obj;
	}

	/*
	 * _____________________________________________________________ Methods for Bean handling
	 */
	/**
	 * Get all the beans for this form.
	 *
	 * @return all the beans
	 */
	public Iterator<Bean> getBeans()
	{
		return getObjects(IRepository.BEANS);
	}

	/**
	 * Create a new bean.
	 *
	 * @param name the name of the bean
	 * @param className the class name
	 * @return the new bean
	 */
	public Bean createNewBean(String name, String className) throws RepositoryException
	{
		Bean obj = (Bean)getRootObject().getChangeHandler().createNewObject(this, IRepository.BEANS);
		//set all the required properties

		obj.setName(name == null ? "untitled" : name); //$NON-NLS-1$
		obj.setBeanClassName(className);

		addChild(obj);
		return obj;
	}

	/*
	 * _____________________________________________________________ Methods for TabPanel handling
	 */
	/**
	 * Get all the form tab panels.
	 *
	 * @return all the tab panels
	 */
	public Iterator<TabPanel> getTabPanels()
	{
		return getObjects(IRepository.TABPANELS);
	}

	/**
	 * Create a new tab panel.
	 *
	 * @param name
	 * @return the new tab panel
	 */
	public TabPanel createNewTabPanel(String name) throws RepositoryException
	{
		TabPanel obj = (TabPanel)getRootObject().getChangeHandler().createNewObject(this, IRepository.TABPANELS);
		//set all the required properties

		obj.setName(name);

		addChild(obj);
		return obj;
	}

	/*
	 * _____________________________________________________________ Methods for Rectangle handling
	 */
	/**
	 * @deprecated
	 */
	@Deprecated
	public RectShape createNewRectangle(Point location) throws RepositoryException
	{
		RectShape obj = (RectShape)getRootObject().getChangeHandler().createNewObject(this, IRepository.RECTSHAPES);
		//set all the required properties

		obj.setLocation(location);
		obj.setLineSize(1);
		addChild(obj);
		return obj;
	}

	public List<IPersist> getHierarchyChildren()
	{
		return getAllObjectsAsList();
	}

	/**
	 * @return
	 */
	public List<IFormElement> getFlattenedObjects(Comparator< ? super IFormElement> comparator)
	{
		List<IFormElement> flattenedPersists = new ArrayList<IFormElement>();
		List<IPersist> children = getHierarchyChildren();
		for (IPersist persist : children)
		{
			if (persist instanceof LayoutContainer)
			{
				flattenedPersists.addAll(((LayoutContainer)persist).getFlattenedObjects(comparator));
			}
			else if (persist instanceof IFormElement)
			{
				flattenedPersists.add((IFormElement)persist);
			}
		}
		IFormElement[] array = flattenedPersists.toArray(new IFormElement[flattenedPersists.size()]);
		Arrays.sort(array, comparator);
		return new ArrayList<IFormElement>(Arrays.<IFormElement> asList(array));
	}
}
