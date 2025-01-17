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
package com.servoy.j2db.debug;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.eclipse.dltk.rhino.dbgp.DBGPDebugger;
import org.mozilla.javascript.RhinoException;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;

import com.servoy.j2db.ClientState;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.component.ComponentFactory;
import com.servoy.j2db.dataprocessing.FoundSetManager;
import com.servoy.j2db.debug.DebugJ2DBClient.DebugSwingFormMananger;
import com.servoy.j2db.persistence.Field;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IPersistVisitor;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.IScriptProvider;
import com.servoy.j2db.persistence.Media;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.persistence.ScriptCalculation;
import com.servoy.j2db.persistence.ScriptMethod;
import com.servoy.j2db.persistence.ScriptVariable;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.Style;
import com.servoy.j2db.persistence.Tab;
import com.servoy.j2db.persistence.TableNode;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.persistence.WebComponent;
import com.servoy.j2db.scripting.FormScope;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.scripting.LazyCompilationScope;
import com.servoy.j2db.server.ngclient.property.types.RelationPropertyType;
import com.servoy.j2db.server.ngclient.property.types.ValueListPropertyType;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.Utils;

public class DebugUtils
{
	public interface DebugUpdateFormSupport
	{
		public void updateForm(Form form);
	}

	public static void errorToDebugger(IExecutingEnviroment engine, String message, Object errorDetail)
	{
		Object detail = errorDetail;
		if (engine instanceof RemoteDebugScriptEngine)
		{
			DBGPDebugger debugger = ((RemoteDebugScriptEngine)engine).getDebugger();
			if (debugger != null)
			{
				RhinoException rhinoException = null;
				if (detail instanceof Exception)
				{
					Throwable exception = (Exception)detail;
					while (exception != null)
					{
						if (exception instanceof RhinoException)
						{
							rhinoException = (RhinoException)exception;
							break;
						}
						exception = exception.getCause();
					}
				}
				String msg = message;
				if (rhinoException != null)
				{
					if (msg == null)
					{
						msg = rhinoException.getLocalizedMessage();
					}
					else msg += '\n' + rhinoException.getLocalizedMessage();
					msg += '\n' + rhinoException.getScriptStackTrace();
				}
				else if (detail instanceof Exception)
				{
					Object e = ((Exception)detail).getCause();
					if (e != null)
					{
						detail = e;
					}
					msg += "\n > " + detail.toString(); // complete stack?
					if (detail instanceof ServoyException && ((ServoyException)detail).getScriptStackTrace() != null)
					{
						msg += '\n' + ((ServoyException)detail).getScriptStackTrace();
					}
				}
				else if (detail != null)
				{
					msg += "\n" + detail;
				}
				debugger.outputStdErr(msg.toString() + '\n');
			}
		}
	}

	public static void stdoutToDebugger(IExecutingEnviroment engine, Object message)
	{
		if (engine instanceof RemoteDebugScriptEngine)
		{
			DBGPDebugger debugger = ((RemoteDebugScriptEngine)engine).getDebugger();
			if (debugger != null)
			{
				debugger.outputStdOut((message == null ? "<null>" : message.toString()) + '\n');
			}
		}
	}


	public static void infoToDebugger(IExecutingEnviroment engine, String message)
	{
		if (engine instanceof RemoteDebugScriptEngine)
		{
			DBGPDebugger debugger = ((RemoteDebugScriptEngine)engine).getDebugger();
			if (debugger != null)
			{
				debugger.outputStdOut(message + '\n');
			}
		}
	}

	public static Set<IFormController>[] getScopesAndFormsToReload(final ClientState clientState, Collection<IPersist> changes)
	{
		Set<IFormController> scopesToReload = new HashSet<IFormController>();
		final Set<IFormController> formsToReload = new HashSet<IFormController>();

		Set<Form> formsUpdated = new HashSet<Form>();
		for (IPersist persist : changes)
		{

			clientState.getFlattenedSolution().updatePersistInSolutionCopy(persist);
			if (persist instanceof ScriptMethod)
			{
				if (persist.getParent() instanceof Form)
				{
					Form form = (Form)persist.getParent();
					List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers(form);

					for (IFormController formController : cachedFormControllers)
					{
						scopesToReload.add(formController);
					}
				}
				else if (persist.getParent() instanceof Solution)
				{
					LazyCompilationScope scope = clientState.getScriptEngine().getScopesScope().getGlobalScope(((ScriptMethod)persist).getScopeName());
					scope.remove((IScriptProvider)persist);
					scope.put((IScriptProvider)persist, (IScriptProvider)persist);
				}
				else if (persist.getParent() instanceof TableNode)
				{
					clientState.getFoundSetManager().reloadFoundsetMethod(((TableNode)persist.getParent()).getDataSource(), (IScriptProvider)persist);
				}

				if (clientState instanceof DebugJ2DBClient)
				{
//					((DebugJ2DBClient)clientState).clearUserWindows();  no need for this as window API was refactored and it allows users to clean up dialogs
					((DebugSwingFormMananger)((DebugJ2DBClient)clientState).getFormManager()).fillScriptMenu();
				}
			}
			else if (persist instanceof ScriptVariable)
			{
				ScriptVariable sv = (ScriptVariable)persist;
				if (persist.getParent() instanceof Solution)
				{
					clientState.getScriptEngine().getScopesScope().getGlobalScope(sv.getScopeName()).put(sv);
				}
				if (persist.getParent() instanceof Form)
				{
					Form form = (Form)persist.getParent();
					List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers(form);

					for (IFormController formController : cachedFormControllers)
					{
						FormScope scope = formController.getFormScope();
						scope.put(sv);
					}
				}
			}
			else if (persist.getAncestor(IRepository.FORMS) != null)
			{
				Form form = (Form)persist.getAncestor(IRepository.FORMS);
				if (!formsUpdated.contains(form))
				{
					formsUpdated.add(form);
					List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers(form);

					for (IFormController formController : cachedFormControllers)
					{
						formsToReload.add(formController);
					}
				}
				if (persist instanceof Form && clientState.getFormManager() instanceof DebugUtils.DebugUpdateFormSupport)
				{
					((DebugUtils.DebugUpdateFormSupport)clientState.getFormManager()).updateForm((Form)persist);
				}
			}
			else if (persist instanceof ScriptCalculation)
			{
				ScriptCalculation sc = (ScriptCalculation)persist;
				if (((RemoteDebugScriptEngine)clientState.getScriptEngine()).recompileScriptCalculation(sc))
				{
					List<String> al = new ArrayList<String>();
					al.add(sc.getDataProviderID());
					try
					{
						String dataSource = clientState.getFoundSetManager().getDataSource(sc.getTable());
						((FoundSetManager)clientState.getFoundSetManager()).getRowManager(dataSource).clearCalcs(null, al);
						((FoundSetManager)clientState.getFoundSetManager()).flushSQLSheet(dataSource);
					}
					catch (Exception e)
					{
						Debug.error(e);
					}
				}
//				if (clientState instanceof DebugJ2DBClient)
//				{
//					((DebugJ2DBClient)clientState).clearUserWindows(); no need for this as window API was refactored and it allows users to clean up dialogs
//				}
			}
			else if (persist instanceof Relation)
			{
				((FoundSetManager)clientState.getFoundSetManager()).flushSQLSheet((Relation)persist);

				List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers();

				try
				{
					String primary = ((Relation)persist).getPrimaryDataSource();
					for (IFormController formController : cachedFormControllers)
					{
						if (primary.equals(formController.getDataSource()))
						{
							final IFormController finalController = formController;
							final Relation finalRelation = (Relation)persist;
							formController.getForm().acceptVisitor(new IPersistVisitor()
							{
								@Override
								public Object visit(IPersist o)
								{
									if (o instanceof Tab && Utils.equalObjects(finalRelation.getName(), ((Tab)o).getRelationName()))
									{
										formsToReload.add(finalController);
										return o;
									}
									if (o instanceof Field && ((Field)o).getValuelistID() > 0)
									{
										ValueList vl = clientState.getFlattenedSolution().getValueList(((Field)o).getValuelistID());
										if (vl != null && Utils.equalObjects(finalRelation.getName(), vl.getRelationName()))
										{
											formsToReload.add(finalController);
											return o;
										}
									}
									if (o instanceof WebComponent)
									{
										WebComponent webComponent = (WebComponent)o;
										WebComponentSpecification spec = WebComponentSpecProvider.getInstance() != null
											? WebComponentSpecProvider.getInstance().getWebComponentSpecification(webComponent.getTypeName()) : null;
										if (spec != null)
										{
											Collection<PropertyDescription> properties = spec.getProperties(RelationPropertyType.INSTANCE);
											for (PropertyDescription pd : properties)
											{
												if (Utils.equalObjects(webComponent.getFlattenedJson().opt(pd.getName()), finalRelation.getName()))
												{
													formsToReload.add(finalController);
													return o;
												}
											}
										}
									}
									return CONTINUE_TRAVERSAL;
								}
							});
						}
					}
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
			}
			else if (persist instanceof ValueList)
			{
				ComponentFactory.flushValueList(clientState, (ValueList)persist);
				List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers();
				for (IFormController formController : cachedFormControllers)
				{
					final IFormController finalController = formController;
					final ValueList finalValuelist = (ValueList)persist;
					formController.getForm().acceptVisitor(new IPersistVisitor()
					{
						@Override
						public Object visit(IPersist o)
						{
							if (o instanceof Field && ((Field)o).getValuelistID() > 0 &&
								Utils.equalObjects(((Field)o).getValuelistID(), finalValuelist.getID()))
							{
								formsToReload.add(finalController);
								return o;
							}
							if (o instanceof WebComponent)
							{
								WebComponent webComponent = (WebComponent)o;
								WebComponentSpecification spec = WebComponentSpecProvider.getInstance() != null
									? WebComponentSpecProvider.getInstance().getWebComponentSpecification(webComponent.getTypeName()) : null;
								if (spec != null)
								{
									Collection<PropertyDescription> properties = spec.getProperties(ValueListPropertyType.INSTANCE);
									for (PropertyDescription pd : properties)
									{
										if (Utils.equalObjects(webComponent.getFlattenedJson().opt(pd.getName()), finalValuelist.getUUID().toString()))
										{
											formsToReload.add(finalController);
											return o;
										}
									}
								}
							}
							return CONTINUE_TRAVERSAL;
						}
					});
				}
			}
			else if (persist instanceof Style)
			{
				ComponentFactory.flushStyle(null, ((Style)persist));
				List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers();

				String styleName = ((Style)persist).getName();
				for (IFormController formController : cachedFormControllers)
				{
					if (styleName.equals(formController.getForm().getStyleName()))
					{
						formsToReload.add(formController);
					}
				}
			}
			else if (persist instanceof Media && clientState.getFlattenedSolution().getSolution().getStyleSheetID() == ((Media)persist).getID())
			{
				List<IFormController> cachedFormControllers = clientState.getFormManager().getCachedFormControllers();
				for (IFormController formController : cachedFormControllers)
				{
					formsToReload.add(formController);
				}
			}
		}

		return new Set[] { scopesToReload, formsToReload };
	}

	/**
	 * This method must be invoked from the swt thread to deal with mac os 10.8 deadlock problems when the awt thread freezes with the stack :
	 * <p>
	 * 	<i>at apple.awt.CInputMethod.getNativeLocale(Native Method)
	 *	at apple.awt.CToolkit.getDefaultKeyboardLocale(CToolkit.java:1044)</i>
	 * <p>
	 * //https://bugs.eclipse.org/bugs/show_bug.cgi?id=372951#c7
	 * apply workaround from https://bugs.eclipse.org/bugs/show_bug.cgi?id=291326   plus read and dispatch
	 * @param run : run must be <b>final</b>
	 * @throws InvocationTargetException
	 */
	public static void invokeAndWaitWhileDispatchingOnSWT(final Runnable run) throws InterruptedException, InvocationTargetException
	{
		// apply workaround from https://bugs.eclipse.org/bugs/show_bug.cgi?id=291326   plus read and dispatch
		if (EventQueue.isDispatchThread())
		{// called from AWT dispatch thread
			run.run();
		}
		else if (org.eclipse.swt.widgets.Display.getCurrent() == null)
		{// called from non SWT thread
			SwingUtilities.invokeAndWait(run);
		}
		else
		{
			final AtomicBoolean awtFinished = new AtomicBoolean(false);
			final org.eclipse.swt.widgets.Display display = org.eclipse.swt.widgets.Display.getCurrent();
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					// do some AWT stuff here
					try
					{
						run.run();
					}
					finally
					{
						awtFinished.set(true);
						display.asyncExec(new Runnable()
						{
							public void run()
							{
								// deliberately empty, this is only to wake up a
								// potentially waiting SWT-thread below
							}
						});
					}
				}
			});
			while (!awtFinished.get())
			{
				if (!display.readAndDispatch()) display.sleep();
			}
		}
	}
}