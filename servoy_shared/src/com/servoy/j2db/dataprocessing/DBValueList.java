/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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
package com.servoy.j2db.dataprocessing;


import java.util.ArrayList;
import java.util.List;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.IServiceProvider;
import com.servoy.j2db.persistence.Column;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.IServer;
import com.servoy.j2db.persistence.ITable;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.query.IQuerySelectValue;
import com.servoy.j2db.query.IQuerySort;
import com.servoy.j2db.query.ISQLCondition;
import com.servoy.j2db.query.QueryColumn;
import com.servoy.j2db.query.QuerySelect;
import com.servoy.j2db.query.QuerySort;
import com.servoy.j2db.query.QueryTable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.SafeArrayList;
import com.servoy.j2db.util.Utils;

/**
 * Table based valuelist
 * 
 * @author jblok
 */
public class DBValueList extends CustomValueList implements ITableChangeListener
{
	public static final int MAX_VALUELIST_ROWS = 500;
	public static final String NAME_COLUMN = "valuelist_name"; //$NON-NLS-1$

	protected List<SortColumn> defaultSort = null;
	private Table table;
	private boolean containsCalculation = false;
	protected boolean registered = false;

/*
 * _____________________________________________________________ Declaration and definition of constructors
 */
	public DBValueList(IServiceProvider app, ValueList vl)
	{
		super(app, vl);

		if (vl.getAddEmptyValue() == ValueList.EMPTY_VALUE_ALWAYS)
		{
			allowEmptySelection = true;
		}

		setContainsCalculationFlag();

		realValues = new SafeArrayList<Object>();
		if (vl.getDatabaseValuesType() == ValueList.TABLE_VALUES)
		{
			try
			{
				IServer s = application.getSolution().getServer(vl.getServerName());
				if (s != null)
				{
					table = (Table)s.getTable(vl.getTableName());

					//if changes are performed on the data refresh this list.
					if (!registered)
					{
						((FoundSetManager)application.getFoundSetManager()).addTableListener(table, this);
						registered = true;
					}

					defaultSort = application.getFoundSetManager().getSortColumns(table, vl.getSortOptions());
				}
			}
			catch (Exception e)
			{
				Debug.error(e);
			}
			fill();
		}
	}

	@Override
	public void deregister()
	{
		//Can't deregister DB Valuelist in this method because this valuelist can be reused!
	}

/*
 * _____________________________________________________________ The methods below belong to interface <interfacename>
 */
	@Override
	public Object getRealElementAt(int row)//real value, getElementAt is display value
	{
		if (row < -1 && fallbackValueList != null)
		{
			return fallbackValueList.getRealElementAt((row * -1) - 2);
		}
		return realValues.get(row);
	}

	/*
	 * @see com.servoy.j2db.dataprocessing.CustomValueList#hasRealValues()
	 */
	@Override
	public boolean hasRealValues()
	{
		return valueList.getReturnDataProviders() != valueList.getShowDataProviders();
	}

	@Override
	public int realValueIndexOf(Object value)
	{
		int i = realValues.indexOf(value);
		if (i == -1 && fallbackValueList != null)
		{
			i = fallbackValueList.realValueIndexOf(value);
			if (i != -1)
			{
				i = (i + 2) * -1; // all values range from -2 > N
			}
		}
		return i;
	}

	//update the list, contents may have changed, can this implemented more effective?
	public void tableChange(TableEvent e)
	{
		try
		{
			int size = getSize();
			stopBundlingEvents(); // to be on the safe side
			realValues = new SafeArrayList<Object>();
			removeAllElements();
			if (size > 0)
			{
				fireIntervalRemoved(this, 0, size - 1);
			}
			isLoaded = false;
			if (valueList.getDatabaseValuesType() == ValueList.TABLE_VALUES)
			{
				fill();
			}
		}
		catch (Exception ex)
		{
			Debug.error(ex);//due to buggy swing ui on macosx 131 this is needed, thows nullp when filled and not selected
		}
	}

/*
 * _____________________________________________________________ The methods below belong to this class
 */

	protected boolean isLoaded = false;

	@Override
	public void fill(IRecordInternal parentState)
	{
		super.fill(parentState);
		if (!isLoaded && !(parentState instanceof PrototypeState) && parentState != null && valueList.getDatabaseValuesType() == ValueList.TABLE_VALUES)
		{
			fill();
			stopBundlingEvents(); // to be on the safe side
			int size = getSize();
			if (size > 0) fireContentsChanged(this, 0, size - 1);
		}
	}

	//also called by universal field valueChanged
	@SuppressWarnings("nls")
	private void fill()
	{
		try
		{
			if (table == null) return;

			FoundSetManager foundSetManager = ((FoundSetManager)application.getFoundSetManager());
			List<SortColumn> sortColumns = foundSetManager.getSortColumns(table, valueList.getSortOptions());
			FoundSet fs = (FoundSet)foundSetManager.getNewFoundSet(table, null, sortColumns);
			if (fs == null)
			{
				return;
			}

			if (valueList.getUseTableFilter())//apply name as filter on column valuelist_name
			{
				fs.addFilterParam("valueList.nameColumn", NAME_COLUMN, "=", valueList.getName()); //$NON-NLS-1$
			}

			fs.browseAll(false);//we do nothing with related foundsets so don't touch these 

			// browse all could trigger also a fill
			if (isLoaded) return;
			isLoaded = true;

			int showValues = valueList.getShowDataProviders();
			int returnValues = valueList.getReturnDataProviders();
			int total = (showValues | returnValues);

			//more than one value -> concat
			boolean concatShowValues = false;
			if ((showValues != 1) && (showValues != 2) && (showValues != 4))
			{
				concatShowValues = true;
			}
			boolean concatReturnValues = false;
			if ((returnValues != 1) && (returnValues != 2) && (returnValues != 4))
			{
				concatReturnValues = true;
			}

			boolean singleColumn = (total & 7) == 1 || (total & 7) == 2 || (total & 7) == 4;

			try
			{
				startBundlingEvents();
				//add empty row
				if (valueList.getAddEmptyValue() == ValueList.EMPTY_VALUE_ALWAYS)
				{
					addElement(""); //$NON-NLS-1$
					realValues.add(null);
				}

				QuerySelect creationSQLParts = null;
				if (singleColumn && fs.getSize() >= ((FoundSetManager)application.getFoundSetManager()).pkChunkSize && !containsCalculation)
				{
					creationSQLParts = createValuelistQuery(application, valueList, table);
				}
				if (creationSQLParts != null && creationSQLParts.isDistinct() &&
					fs.getSize() >= ((FoundSetManager)application.getFoundSetManager()).pkChunkSize && !containsCalculation)
				{
					ArrayList<TableFilter> tableFilterParams = foundSetManager.getTableFilterParams(table.getServerName(), creationSQLParts);
					if (valueList.getUseTableFilter()) //apply name as filter on column valuelist_name in creationSQLParts
					{
						if (tableFilterParams == null)
						{
							tableFilterParams = new ArrayList<TableFilter>();
						}
						tableFilterParams.add(new TableFilter(
							"dbValueList.nameFilter", table.getServerName(), table.getName(), table.getSQLName(), NAME_COLUMN, //$NON-NLS-1$
							ISQLCondition.EQUALS_OPERATOR, valueList.getName()));
					}
					String transaction_id = foundSetManager.getTransactionID(table.getServerName());
					SQLStatement trackingInfo = null;
					if (foundSetManager.getEditRecordList().hasAccess(table, IRepository.TRACKING_VIEWS))
					{
						trackingInfo = new SQLStatement(ISQLActionTypes.SELECT_ACTION, table.getServerName(), table.getName(), null, null);
						trackingInfo.setTrackingData(creationSQLParts.getColumnNames(), new Object[][] { }, new Object[][] { }, application.getUserUID(),
							foundSetManager.getTrackingInfo(), application.getClientID());
					}
					IDataSet set = application.getDataServer().performQuery(application.getClientID(), table.getServerName(), transaction_id, creationSQLParts,
						tableFilterParams, !creationSQLParts.isUnique(), 0, MAX_VALUELIST_ROWS, IDataServer.VALUELIST_QUERY, trackingInfo);
					if (set.getRowCount() >= MAX_VALUELIST_ROWS)
					{
						application.reportJSError("Valuelist " + getName() + " fully loaded with 500 rows, more rows are discarded!!", null);
					}

					for (int i = 0; i < set.getRowCount(); i++)
					{
						Object[] row = CustomValueList.processRow(set.getRow(i), showValues, returnValues);
						addElement(handleRowData(valueList, concatShowValues, showValues, row, application));
						realValues.add(handleRowData(valueList, concatReturnValues, returnValues, row, application));
					}
				}
				else
				{
					IRecordInternal[] array = fs.getRecords(0, MAX_VALUELIST_ROWS);
					for (IRecordInternal r : array)
					{
						if (r != null)
						{
							Object val = handleRowData(valueList, concatShowValues, showValues, r, application);
							Object rval = handleRowData(valueList, concatReturnValues, returnValues, r, application);
							int index = indexOf(val);
							if (index == -1 || !Utils.equalObjects(getRealElementAt(index), rval))
							{
								addElement(val);
								realValues.add(rval);
							}
						}
					}
					if (fs.getSize() >= MAX_VALUELIST_ROWS)
					{
						application.reportJSError("Valuelist " + getName() + " fully loaded with 500 rows, more rows are discarded!!", null);
					}

				}
			}
			finally
			{
				stopBundlingEvents();
			}
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
	}

	protected void setContainsCalculationFlag()
	{
		if (valueList != null && application != null && application.getFlattenedSolution() != null)
		{
			containsCalculation = (checkIfCalc(valueList.getDataProviderID1()) || checkIfCalc(valueList.getDataProviderID2()) || checkIfCalc(valueList.getDataProviderID3()));
		}
	}

	private boolean checkIfCalc(String dp)
	{
		return dp != null && application.getFlattenedSolution().getScriptCalculation(dp, table) != null;
	}

	public static IQuerySelectValue getQuerySelectValue(Table table, QueryTable queryTable, String dataprovider)
	{
		if (dataprovider != null && table != null)
		{
			Column c = table.getColumn(dataprovider);
			if (c != null)
			{
				return new QueryColumn(queryTable, c.getID(), c.getSQLName(), c.getType(), c.getLength());
			}
		}
		// should never happen
		throw new IllegalStateException("Cannot find column " + dataprovider + " in table " + table);
	}

	public ITable getTable()
	{
		return table;
	}

	public static QuerySelect createValuelistQuery(IServiceProvider application, ValueList valueList, Table table)
	{
		if (table == null) return null;

		FoundSetManager foundSetManager = ((FoundSetManager)application.getFoundSetManager());
		List<SortColumn> sortColumns = foundSetManager.getSortColumns(table, valueList.getSortOptions());

		int showValues = valueList.getShowDataProviders();
		int returnValues = valueList.getReturnDataProviders();
		int total = (showValues | returnValues);

		QuerySelect select = new QuerySelect(new QueryTable(table.getSQLName(), table.getDataSource(), table.getCatalog(), table.getSchema()));

		ArrayList<IQuerySort> orderColumns = new ArrayList<IQuerySort>();
		ArrayList<IQuerySelectValue> columns = new ArrayList<IQuerySelectValue>();

		boolean useDefinedSort = sortColumns != null && sortColumns.size() > 0;
		if (useDefinedSort)
		{
			for (SortColumn sc : sortColumns)
			{
				orderColumns.add(new QuerySort(getQuerySelectValue(table, select.getTable(), sc.getDataProviderID()), sc.getSortOrder() == SortColumn.ASCENDING));
			}
		}

		if ((total & 1) != 0)
		{
			IQuerySelectValue cSQLName = getQuerySelectValue(table, select.getTable(), valueList.getDataProviderID1());
			columns.add(cSQLName);
			if ((showValues & 1) != 0 && !useDefinedSort)
			{
				orderColumns.add(new QuerySort(cSQLName, true));
			}
		}
		if ((total & 2) != 0)
		{
			IQuerySelectValue cSQLName = getQuerySelectValue(table, select.getTable(), valueList.getDataProviderID2());
			columns.add(cSQLName);
			if ((showValues & 2) != 0 && !useDefinedSort)
			{
				orderColumns.add(new QuerySort(cSQLName, true));
			}
		}
		if ((total & 4) != 0)
		{
			IQuerySelectValue cSQLName = getQuerySelectValue(table, select.getTable(), valueList.getDataProviderID3());
			columns.add(cSQLName);
			if ((showValues & 4) != 0 && !useDefinedSort)
			{
				orderColumns.add(new QuerySort(cSQLName, true));
			}
		}

		// check if we can still use distinct
		select.setDistinct(SQLGenerator.isDistinctAllowed(columns, orderColumns));
		select.setColumns(columns);
		select.setSorts(orderColumns);

		return select;
	}

	public static List<String> getShowDataproviders(ValueList valueList, Table table, String dataProviderID, IFoundSetManagerInternal foundSetManager)
		throws RepositoryException
	{
		if (valueList == null)
		{
			return null;
		}

		// first try fallback value list,
		FlattenedSolution flattenedSolution = foundSetManager.getApplication().getFlattenedSolution();
		ValueList usedValueList = flattenedSolution.getValueList(valueList.getFallbackValueListID());
		Relation valuelistSortRelation = flattenedSolution.getValuelistSortRelation(usedValueList, table, dataProviderID, foundSetManager);
		if (valuelistSortRelation == null)
		{
			// then try regular value list
			usedValueList = valueList;
			valuelistSortRelation = flattenedSolution.getValuelistSortRelation(usedValueList, table, dataProviderID, foundSetManager);
		}

		if (valuelistSortRelation == null)
		{
			return null;
		}

		List<String> showDataproviders = new ArrayList<String>(3);
		int showValues = usedValueList.getShowDataProviders();
		if ((showValues & 1) != 0)
		{
			showDataproviders.add(valuelistSortRelation.getName() + '.' + usedValueList.getDataProviderID1());
		}
		if ((showValues & 2) != 0)
		{
			showDataproviders.add(valuelistSortRelation.getName() + '.' + usedValueList.getDataProviderID2());
		}
		if ((showValues & 4) != 0)
		{
			showDataproviders.add(valuelistSortRelation.getName() + '.' + usedValueList.getDataProviderID3());
		}
		return showDataproviders;
	}
}
