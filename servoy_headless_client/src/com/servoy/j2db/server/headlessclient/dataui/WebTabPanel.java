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
package com.servoy.j2db.server.headlessclient.dataui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.IRequestTarget;
import org.apache.wicket.IResourceListener;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Page;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.Loop;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.ClientProperties;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.version.undo.Change;

import com.servoy.base.util.ITagResolver;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.FormController;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IForm;
import com.servoy.j2db.IScriptExecuter;
import com.servoy.j2db.component.ComponentFactory;
import com.servoy.j2db.dataprocessing.IDisplayRelatedData;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.dataprocessing.ISaveConstants;
import com.servoy.j2db.dataprocessing.ISwingFoundSet;
import com.servoy.j2db.dataprocessing.RelatedFoundSet;
import com.servoy.j2db.dataprocessing.SortColumn;
import com.servoy.j2db.dataprocessing.TagResolver;
import com.servoy.j2db.persistence.IAnchorConstants;
import com.servoy.j2db.persistence.Media;
import com.servoy.j2db.persistence.StaticContentSpecLoader;
import com.servoy.j2db.persistence.TabPanel;
import com.servoy.j2db.server.headlessclient.MainPage;
import com.servoy.j2db.server.headlessclient.TabIndexHelper;
import com.servoy.j2db.server.headlessclient.WebClientSession;
import com.servoy.j2db.server.headlessclient.WebForm;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.ui.IFormLookupPanel;
import com.servoy.j2db.ui.IFormUI;
import com.servoy.j2db.ui.IProviderStylePropertyChanges;
import com.servoy.j2db.ui.IStylePropertyChanges;
import com.servoy.j2db.ui.ISupportSecuritySettings;
import com.servoy.j2db.ui.ISupportSimulateBounds;
import com.servoy.j2db.ui.ISupportSimulateBoundsProvider;
import com.servoy.j2db.ui.ISupportWebBounds;
import com.servoy.j2db.ui.ITabPanel;
import com.servoy.j2db.ui.runtime.IRuntimeComponent;
import com.servoy.j2db.ui.scripting.RuntimeTabPanel;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.HtmlUtils;
import com.servoy.j2db.util.PersistHelper;
import com.servoy.j2db.util.Utils;

/**
 * Represents a tabpanel in the webbrowser.
 *
 * @author jcompagner
 */
public class WebTabPanel extends WebMarkupContainer implements ITabPanel, IDisplayRelatedData, IProviderStylePropertyChanges, ISupportSecuritySettings,
	ISupportWebBounds, ISupportWebTabSeq, ListSelectionListener, IWebFormContainer, ISupportSimulateBoundsProvider
{
	private static final long serialVersionUID = 1L;

	private final IApplication application;
	private WebTabFormLookup currentForm;
	protected IRecordInternal parentData;
	private final List<String> allRelationNames = new ArrayList<String>(5);
	protected final List<WebTabHolder> allTabs = new ArrayList<WebTabHolder>(5);
	private final List<ISwingFoundSet> related = new ArrayList<ISwingFoundSet>();

	private IScriptExecuter scriptExecutor;

	private String onTabChangeMethodCmd;
	private Object[] onTabChangeArgs;

	protected final int orient;
	private int tabSequenceIndex = ISupportWebTabSeq.DEFAULT;
	private Dimension tabSize;
	private final RuntimeTabPanel scriptable;

	public WebTabPanel(IApplication application, final RuntimeTabPanel scriptable, String name, int orient, boolean oneTab)
	{
		super(name);
		this.application = application;
		this.orient = orient;

		final boolean useAJAX = Utils.getAsBoolean(application.getRuntimeProperties().get("useAJAX")); //$NON-NLS-1$
		setOutputMarkupPlaceholderTag(true);

		if (orient != TabPanel.SPLIT_HORIZONTAL && orient != TabPanel.SPLIT_VERTICAL) add(new Label("webform", new Model<String>("")));//temporary add, in case the tab panel does not contain any tabs //$NON-NLS-1$ //$NON-NLS-2$

		// TODO check ignore orient and oneTab??
		IModel<Integer> tabsModel = new AbstractReadOnlyModel<Integer>()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public Integer getObject()
			{
				return Integer.valueOf(allTabs.size());
			}
		};

		if (orient != TabPanel.HIDE && orient != TabPanel.SPLIT_HORIZONTAL && orient != TabPanel.SPLIT_VERTICAL &&
			!(orient == TabPanel.DEFAULT_ORIENTATION && oneTab))
		{
			add(new Loop("tablinks", tabsModel) //$NON-NLS-1$
			{
				private static final long serialVersionUID = 1L;

				private String focusedItem;

				@Override
				protected void populateItem(final LoopItem item)
				{
					final WebTabHolder holder = allTabs.get(item.getIteration());
					MarkupContainer link = null;
					link = new ServoySubmitLink("tablink", useAJAX) //$NON-NLS-1$
					{
						private static final long serialVersionUID = 1L;

						/**
						 * @see wicket.ajax.markup.html.AjaxFallbackLink#onClick(wicket.ajax.AjaxRequestTarget)
						 */
						@Override
						public void onClick(AjaxRequestTarget target)
						{
							Page page = findPage();
							if (page != null)
							{
								setActiveTabPanel(holder.getPanel());
								if (target != null)
								{
									relinkAtTabPanel(WebTabPanel.this);
									focusedItem = item.getId();
									WebEventExecutor.generateResponse(target, page);
								}
							}
						}

						private void relinkAtForm(WebForm form)
						{
							form.visitChildren(WebTabPanel.class, new IVisitor<WebTabPanel>()
							{
								public Object component(WebTabPanel wtp)
								{
									relinkAtTabPanel(wtp);
									return IVisitor.CONTINUE_TRAVERSAL;
								}
							});
						}

						private void relinkAtTabPanel(WebTabPanel wtp)
						{
							wtp.relinkFormIfNeeded();
							wtp.visitChildren(WebForm.class, new IVisitor<WebForm>()
							{
								public Object component(WebForm form)
								{
									relinkAtForm(form);
									return IVisitor.CONTINUE_TRAVERSAL;
								}
							});
						}

						@Override
						protected void disableLink(final ComponentTag tag)
						{
							// if the tag is an anchor proper
							if (tag.getName().equalsIgnoreCase("a") || tag.getName().equalsIgnoreCase("link") || tag.getName().equalsIgnoreCase("area")) //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
							{
								// Remove any href from the old link
								tag.remove("href"); //$NON-NLS-1$
								tag.remove("onclick"); //$NON-NLS-1$
							}
						}

					};

					if (item.getId().equals(focusedItem))
					{
						IRequestTarget currentRequestTarget = RequestCycle.get().getRequestTarget();
						if (currentRequestTarget instanceof AjaxRequestTarget)
						{
							((AjaxRequestTarget)currentRequestTarget).focusComponent(link);
						}
						focusedItem = null;
					}

					if (holder.getTooltip() != null)
					{
						link.setMetaData(TooltipAttributeModifier.TOOLTIP_METADATA, holder.getTooltip());
					}

					TabIndexHelper.setUpTabIndexAttributeModifier(link, tabSequenceIndex);
					link.add(TooltipAttributeModifier.INSTANCE);

					if (item.getIteration() == 0) link.add(new AttributeModifier("firsttab", true, new Model<Boolean>(Boolean.TRUE))); //$NON-NLS-1$
					link.setEnabled(holder.isEnabled() && WebTabPanel.this.isEnabled());

					String text = holder.getText();
					if (holder.getDisplayedMnemonic() > 0)
					{
						final String mnemonic = Character.toString((char)holder.getDisplayedMnemonic());
						link.add(new SimpleAttributeModifier("accesskey", mnemonic)); //$NON-NLS-1$
						if (text != null && text.contains(mnemonic) && !HtmlUtils.hasUsefulHtmlContent(text))
						{
							StringBuffer sbBodyText = new StringBuffer(text);
							int mnemonicIdx = sbBodyText.indexOf(mnemonic);
							if (mnemonicIdx != -1)
							{
								sbBodyText.insert(mnemonicIdx + 1, "</u>"); //$NON-NLS-1$
								sbBodyText.insert(mnemonicIdx, "<u>"); //$NON-NLS-1$
								text = sbBodyText.toString();
							}
						}
					}
					ServoyTabIcon tabIcon = new ServoyTabIcon("icon", holder, scriptable); //$NON-NLS-1$
					link.add(tabIcon);

					Label label = new Label("linktext", new Model<String>(text)); //$NON-NLS-1$
					label.setEscapeModelStrings(false);
					link.add(label);
					item.add(link);
					IModel<String> selectedOrDisabledClass = new AbstractReadOnlyModel<String>()
					{
						private static final long serialVersionUID = 1L;

						@Override
						public String getObject()
						{
							if (!holder.isEnabled() || !WebTabPanel.this.isEnabled())
							{
								if (currentForm == holder.getPanel())
								{
									return "disabled_selected_tab"; //$NON-NLS-1$
								}
								return "disabled_tab"; //$NON-NLS-1$
							}
							else
							{
								if (currentForm == holder.getPanel())
								{
									return "selected_tab"; //$NON-NLS-1$
								}
								return "deselected_tab"; //$NON-NLS-1$
							}
						}
					};
					item.add(new AttributeModifier("class", true, selectedOrDisabledClass)); //$NON-NLS-1$
					label.add(new StyleAppendingModifier(new Model<String>()
					{
						private static final long serialVersionUID = 1L;

						@Override
						public String getObject()
						{
							String style = "white-space: nowrap;"; //$NON-NLS-1$
							if (foreground != null)
							{
								style += " color:" + PersistHelper.createColorString(foreground); //$NON-NLS-1$
							}
							if (holder.getIcon() != null)
							{
								style += "; padding-left: 3px"; //$NON-NLS-1$
							}
							return style;
						}
					}));
				}
			});

			// All tab panels get their tabs rearranged after they make it to the browser.
			// On Chrome & Safari the tab rearrangement produces an ugly flicker effect, because
			// initially the tabs are not visible and then they are made visible. By
			// sending the tab as invisible and turning it to visible only after the tabs
			// are arranged, this jumping/flickering effect is gone. However a small delay can now be
			// noticed in Chrome & Safari, which should also be eliminated somehow.
			// The tab panel is set to visible in function "rearrageTabsInTabPanel" from "servoy.js".
			add(new StyleAppendingModifier(new Model<String>()
			{
				private static final long serialVersionUID = 1L;

				@Override
				public String getObject()
				{
					return "visibility: hidden;overflow:hidden"; //$NON-NLS-1$
				}
			}));

			add(new AbstractServoyDefaultAjaxBehavior()
			{

				@Override
				protected void respond(AjaxRequestTarget target)
				{
				}

				@Override
				public void renderHead(IHeaderResponse response)
				{
					super.renderHead(response);
					boolean dontRearrangeHere = false;

					if (!(getRequestCycle().getRequestTarget() instanceof AjaxRequestTarget) &&
						Utils.getAsBoolean(((MainPage)getPage()).getController().getApplication().getRuntimeProperties().get("enableAnchors"))) //$NON-NLS-1$
					{
						Component parentForm = getParent();
						while ((parentForm != null) && !(parentForm instanceof WebForm))
							parentForm = parentForm.getParent();
						if (parentForm != null)
						{
							int anch = ((WebForm)parentForm).getAnchors(WebTabPanel.this.getMarkupId());
							if (anch != 0 && anch != IAnchorConstants.DEFAULT) dontRearrangeHere = true;
						}
					}
					if (!dontRearrangeHere)
					{
						String jsCall = "rearrageTabsInTabPanel('" + WebTabPanel.this.getMarkupId() + "');"; //$NON-NLS-1$ //$NON-NLS-2$
						// Safari and Konqueror have some problems with the "domready" event, so for those
						// browsers we'll use the "load" event. Otherwise use "domready", it reduces the flicker
						// effect when rearranging the tabs.
						ClientProperties clp = ((WebClientInfo)Session.get().getClientInfo()).getProperties();
						if (clp.isBrowserKonqueror() || clp.isBrowserSafari()) response.renderOnLoadJavascript(jsCall);
						else response.renderOnDomReadyJavascript(jsCall);
					}
				}

				@Override
				public boolean isEnabled(Component component)
				{
					return WebClientSession.get().useAjax();
				}

			});
		}
		add(StyleAttributeModifierModel.INSTANCE);
		add(TooltipAttributeModifier.INSTANCE);
		this.scriptable = scriptable;
		((ChangesRecorder)scriptable.getChangesRecorder()).setDefaultBorderAndPadding(null, TemplateGenerator.DEFAULT_LABEL_PADDING);
	}

	public final RuntimeTabPanel getScriptObject()
	{
		return scriptable;
	}

	/**
	 * @return the orient
	 */
	public int getOrient()
	{
		return orient;
	}

	public IStylePropertyChanges getStylePropertyChanges()
	{
		return scriptable.getChangesRecorder();
	}

	private void setActiveTabPanel(WebTabFormLookup fl)
	{
		if (fl != currentForm)
		{
			WebTabFormLookup previous = currentForm;

			if (previous != null)
			{
				int stopped = application.getFoundSetManager().getEditRecordList().stopEditing(false);
				boolean cantStop = stopped != ISaveConstants.STOPPED && stopped != ISaveConstants.AUTO_SAVE_BLOCKED;
				List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
				boolean ok = previous.notifyVisible(false, invokeLaterRunnables);
				Utils.invokeLater(application, invokeLaterRunnables);
				if (cantStop || !ok)
				{
					return;
				}
			}

			int previousIndex = -1;
			for (int i = 0; i < allTabs.size(); i++)
			{
				WebTabHolder holder = allTabs.get(i);
				if (holder.getPanel() == previous)
				{
					previousIndex = i;
					break;
				}
			}

			if (previousIndex != -1)
			{
				final int changedIndex = previousIndex;
				addStateChange(new Change()
				{
					@Override
					public void undo()
					{
						if (allTabs.size() > changedIndex)
						{
							WebTabHolder holder = allTabs.get(changedIndex);
							setActiveTabPanel(holder.getPanel());
						}
					}
				});
			}

			List<Runnable> invokeLaterRunnables2 = new ArrayList<Runnable>();
			setCurrentForm(fl, previousIndex, invokeLaterRunnables2);
			Utils.invokeLater(application, invokeLaterRunnables2);
		}
	}

	/**
	 * @param fl
	 * @param previousIndex
	 */
	private void setCurrentForm(WebTabFormLookup fl, int previousIndex, List<Runnable> invokeLaterRunnables)
	{
		if (fl != null && !fl.isFormReady()) return;

		getStylePropertyChanges().setChanged();
		currentForm = fl;
		if (parentData != null)
		{
			showFoundSet(currentForm, parentData, getDefaultSort());
		}

		// Test if current one is there
		if (currentForm.isReady())
		{
			WebForm webForm = currentForm.getWebForm();
			if (WebTabPanel.this.get(webForm.getId()) != null)
			{
				// replace it
				WebTabPanel.this.replace(webForm);
			}
			else
			{
				// else add it
				WebTabPanel.this.add(webForm);
			}
			recomputeTabSequence();
			boolean visible = true;
			WebForm webform = findParent(WebForm.class);
			if (webform != null)
			{
				visible = webform.getController().isFormVisible();
			}
			currentForm.notifyVisible(visible, invokeLaterRunnables);

			if (onTabChangeMethodCmd != null && previousIndex != -1)
			{
				scriptExecutor.executeFunction(onTabChangeMethodCmd, Utils.arrayMerge((new Object[] { Integer.valueOf(previousIndex + 1) }), onTabChangeArgs),
					true, this, false, StaticContentSpecLoader.PROPERTY_ONCHANGEMETHODID.getPropertyName(), false);
			}
		}
	}

	public WebForm getCurrentForm()
	{
		return currentForm != null ? currentForm.getWebForm() : null;
	}

	public IFormUI[] getChildForms()
	{
		WebForm form = getCurrentForm();
		if (form != null && form.getParent() == null)
		{
			form = null;
		}
		return form != null ? new IFormUI[] { form } : null;
	}

	/**
	 * @see org.apache.wicket.MarkupContainer#remove(org.apache.wicket.Component)
	 */
	@Override
	public void remove(Component component)
	{
		if (currentForm != null && currentForm.isReady() && component == currentForm.getWebForm())
		{
			currentForm.setWebForm(null);
			//replace(new Label("webform", new Model<String>("")));
		}
		super.remove(component);
	}

	public void recomputeTabSequence()
	{
		FormController fc = currentForm.getWebForm().getController();
		fc.recomputeTabSequence(tabSequenceIndex);
	}

	public boolean isCurrentForm(IFormUI formUI)
	{
		return getCurrentForm() == formUI;
	}

	/**
	 * @see wicket.MarkupContainer#onRender(wicket.markup.MarkupStream)
	 */
	@Override
	protected void onRender(MarkupStream markupStream)
	{
		super.onRender(markupStream);
		getStylePropertyChanges().setRendered();
	}


	/**
	 * @return
	 */
	public void initalizeFirstTab()
	{
		if (currentForm == null && allTabs.size() > 0)
		{
			WebTabHolder holder = allTabs.get(0);
			List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
			setCurrentForm(holder.getPanel(), -1, invokeLaterRunnables);
			Utils.invokeLater(application, invokeLaterRunnables);
		}
		else if (currentForm != null && currentForm.getWebForm() == null)
		{
			// webForm was removed from this tabpanel of the current Form (reuse or destroyed)
			List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
			setCurrentForm(currentForm, -1, invokeLaterRunnables);
			Utils.invokeLater(application, invokeLaterRunnables);

		}
		return;
	}


	private void relinkFormIfNeeded()
	{
		if (currentForm != null && isVisibleInHierarchy() && (currentForm.getWebForm() == null || currentForm.getWebForm().getParent() != this))
		{
			if (currentForm.getWebForm() == null)
			{
				if (size() == 0)
				{
					// probably current form was destroyed from js code
					WebTabPanel.this.add(new Label("webform", new Model<String>("")));
				}
			}
			else if (get(currentForm.getWebForm().getId()) != null)
			{
				// replace it
				replace(currentForm.getWebForm());
			}
			else
			{
				// else add it
				add(currentForm.getWebForm());
			}
		}
	}

	/**
	 * @see wicket.Component#onAttach()
	 */
	@Override
	protected void onBeforeRender()
	{
		if (orient != TabPanel.SPLIT_HORIZONTAL && orient != TabPanel.SPLIT_VERTICAL)
		{
			//tab has to be initialized now.. see also MainPage.listview.onBeforRender..
			initalizeFirstTab();
			super.onBeforeRender();
			relinkFormIfNeeded();
		}
		else super.onBeforeRender();

	}

	public void setTabLayoutPolicy(int scroll_tab_layout)
	{
		//TODO ignore???
	}

	public IFormLookupPanel createFormLookupPanel(String tabname, String relationName, String formName)
	{
		return new WebTabFormLookup(tabname, relationName, formName, this, application);
	}

	public void setCursor(Cursor cursor)
	{
	}

	public void setValidationEnabled(boolean b)
	{
	}

	public void notifyVisible(boolean visible, List<Runnable> invokeLaterRunnables)
	{
		if (currentForm == null && allTabs.size() > 0)
		{
			WebTabHolder holder = allTabs.get(0);
			setCurrentForm(holder.getPanel(), -1, invokeLaterRunnables);
		}
		if (currentForm != null && currentForm.getWebForm() != null)
		{
			FormController controller = currentForm.getWebForm().getController();

			//this is not needed when closing
			if (visible && parentData != null)
			{
				showFoundSet(currentForm, parentData, controller.getDefaultSortColumns());

				// Test if current one is there
				if (currentForm.isReady())
				{
					if (WebTabPanel.this.get(currentForm.getWebForm().getId()) != null)
					{
						// replace it
						WebTabPanel.this.replace(currentForm.getWebForm());
					}
					else
					{
						// else add it
						WebTabPanel.this.add(currentForm.getWebForm());
					}
					recomputeTabSequence();
				}
			}
			controller.notifyVisible(visible, invokeLaterRunnables);
		}
	}

	public void notifyResized()
	{
		if (currentForm != null && currentForm.isReady())
		{
			WebForm webForm = currentForm.getWebForm();
			FormController controller = webForm.getController();
			if (controller != null && webForm.isFormWidthHeightChanged())
			{
				controller.notifyResized();
				webForm.clearFormWidthHeightChangedFlag();
			}
		}
	}

	public void setRecord(IRecordInternal parentState, boolean stopEditing)
	{
		parentData = parentState;
		if (currentForm != null)
		{

			showFoundSet(currentForm, parentState, getDefaultSort());
		}
		else if (allTabs.size() > 0)
		{
			showFoundSet(allTabs.get(0).getPanel(), parentState, getDefaultSort());
		}
		ITagResolver resolver = getTagResolver(parentState);
		for (int i = 0; i < allTabs.size(); i++)
		{
			WebTabHolder element = allTabs.get(i);
			if (element.refreshTagStrings(resolver))
			{
				getStylePropertyChanges().setChanged();
			}
		}
	}

	/**
	 * @param parentState
	 * @return
	 */
	private ITagResolver getTagResolver(IRecordInternal parentState)
	{
		ITagResolver resolver;
		WebForm webForm = findParent(WebForm.class);
		if (webForm != null)
		{
			resolver = webForm.getController().getTagResolver();
		}
		else
		{
			resolver = TagResolver.createResolver(parentState);
		}
		return resolver;
	}

	protected void showFoundSet(WebTabFormLookup flp, IRecordInternal parentState, List<SortColumn> sort)
	{
		deregisterSelectionListeners();

		if (!flp.isReady()) return;

		FormController fp = flp.getWebForm().getController();
		if (fp != null && flp.getRelationName() != null)
		{
			IFoundSetInternal relatedFoundset = parentState == null ? null : parentState.getRelatedFoundSet(flp.getRelationName(), sort);
			registerSelectionListeners(parentState, flp.getRelationName());
			fp.loadData(relatedFoundset, null);
		}

		ITagResolver resolver = getTagResolver(parentState);
		//refresh tab text
		for (int i = 0; i < allTabs.size(); i++)
		{
			WebTabHolder element = allTabs.get(i);
			if (element.getPanel() == flp)
			{
				element.refreshTagStrings(resolver);
				break;
			}
		}
	}

	private void registerSelectionListeners(IRecordInternal parentState, String relationName)
	{
		String[] parts = relationName.split("\\."); //$NON-NLS-1$
		IRecordInternal currentRecord = parentState;
		for (int i = 0; currentRecord != null && i < parts.length - 1; i++)
		{
			IFoundSetInternal fs = currentRecord.getRelatedFoundSet(parts[i]);
			if (fs instanceof ISwingFoundSet)
			{
				related.add((ISwingFoundSet)fs);
				((ISwingFoundSet)fs).getSelectionModel().addListSelectionListener(this);
			}
			currentRecord = (fs == null) ? null : fs.getRecord(fs.getSelectedIndex());
		}
	}

	private void deregisterSelectionListeners()
	{
		for (ISwingFoundSet fs : related)
		{
			fs.getSelectionModel().removeListSelectionListener(this);
		}
		related.clear();
	}

	public String getSelectedRelationName()
	{
		if (currentForm != null)
		{
			return currentForm.getRelationName();
		}
		return null;
	}

	public String[] getAllRelationNames()
	{
		String[] retval = new String[allRelationNames.size()];
		for (int i = 0; i < retval.length; i++)
		{
			Object relationName = allRelationNames.get(i);
			if (relationName != null)
			{
				retval[i] = relationName.toString();
			}
		}
		return retval;
	}

	public List<SortColumn> getDefaultSort()
	{
		if (currentForm != null)
		{
			// extra test, if the current record is null and the form is not ready just return an empty list.
			// record can be null in the destroy, then creating the form doesn't make any sense.
			return currentForm.getDefaultSort(parentData != null || currentForm.isReady());
		}
		else if (allTabs.size() > 0)
		{
			WebTabHolder holder = allTabs.get(0);
			return holder.getPanel().getDefaultSort(parentData != null || holder.getPanel().isReady());
		}
		return null;
	}

	public boolean stopUIEditing(boolean looseFocus)
	{
		if (currentForm != null && currentForm.isReady())
		{
			return currentForm.getWebForm().getController().stopUIEditing(true);
		}
		return true;
	}

	public void destroy()
	{
		deregisterSelectionListeners();

		//TODO should deregister related foundsets??
	}

	/*
	 * tab support----------------------------------------------------------------------------
	 */
	public void addTab(String text, int iconMediaId, IFormLookupPanel flp, String tip)
	{
		byte[] iconData = ComponentFactory.loadIcon(application.getFlattenedSolution(), new Integer(iconMediaId));
		insertTab(text, iconData, flp, tip, allTabs.size(), false);
	}

	public void insertTab(String text, byte[] iconData, IFormLookupPanel flp, String tip, int index, boolean loaded)
	{
		allTabs.add(index, new WebTabHolder(text, flp, iconData, tip));
		allRelationNames.add(index, flp.getRelationName());
		getStylePropertyChanges().setChanged();

		if (allTabs.size() == 1 && loaded)
		{
			// it's the new active one! If the tabPanel is not loaded, don't do this because it will break execution order (it will be done when tabPanel gets shown)
			// (renderers are now being created - forms initialisation not complete, and we shouldn't generate any JS callbacks like notifyVisible() and such which can access these forms)
			setActiveTabPanel((WebTabFormLookup)flp);
		}
	}

	public void setTabForegroundAt(int index, Color fg)
	{
	}

	public void setTabBackgroundAt(int index, Color bg)
	{
	}

	public boolean addTab(IForm formController, String formName, String tabname, String tabText, String tabtooltip, String iconURL, String fg, String bg,
		String relationName, RelatedFoundSet relatedFs, int idx)
	{
		if (formController != null)
		{
			//to make sure we don't have recursion on adding a tab, to a tabpanel, that is based
			//on the form that the tabpanel is placed on
			WebForm webForm = findParent(WebForm.class);
			if (webForm != null)
			{
				FormController parentFormController = webForm.getController();
				if (parentFormController != null && parentFormController.equals(formController))
				{
					return false;
				}
			}
		}

		WebTabFormLookup flp = (WebTabFormLookup)createFormLookupPanel(tabname, relationName, formName);
		if (formController != null) flp.setReadOnly(formController.isReadOnly());
		FlattenedSolution fl = application.getFlattenedSolution();
		int mediaId = -1;
		if (iconURL != null && !"".equals(iconURL))
		{
			Media media = fl.getMedia(iconURL.replaceAll("media:///", ""));
			if (media != null) mediaId = media.getID();
			if (mediaId == -1)
			{
				Debug.warn("Form '" + formController.getName() + "' with tabpanel  '" + this.name + "' has tabicon  for tab '" + tabname +
					"'in with icon media url : " + iconURL + " not found");
			}
		}

		byte[] iconData = (mediaId == -1 ? null : ComponentFactory.loadIcon(fl, new Integer(mediaId)));

		int count = allTabs.size();
		int tabIndex = idx;
		if (tabIndex == -1 || tabIndex >= count)
		{
			tabIndex = count;
		}

		insertTab(application.getI18NMessageIfPrefixed(tabText), iconData, flp, application.getI18NMessageIfPrefixed(tabtooltip), tabIndex, true);

		if (fg != null) setTabForegroundAt(tabIndex, PersistHelper.createColor(fg));
		if (bg != null) setTabBackgroundAt(tabIndex, PersistHelper.createColor(bg));

		// TODO is this if really needed? (insertTab might activate the new tab, but loadData based on relationName only; if it
		// doesn't activate... will ever currentForm == flp?)
		// if the relatedFs is based on a different record then parentState, it would be wrong to use it... maybe we should only use the relationName
		// from the relatedFs - which is already in the relationName param
		if (relatedFs != null && currentForm == flp)
		{
			FormController fp = flp.getWebForm().getController();
			if (fp != null && flp.getRelationName() != null && flp.getRelationName().equals(relationName))
			{
				fp.loadData(relatedFs, null);
			}
		}

		return true;
	}

	public int getMaxTabIndex()
	{
		return allTabs.size() - 1;
	}

	public String getTabFormNameAt(int i)
	{
		WebTabHolder holder = allTabs.get(i);
		return holder.getPanel().getFormName();
	}

	public int getTabIndex()
	{
		for (int i = 0; i < allTabs.size(); i++)
		{
			if (currentForm == null)
			{
				// no current form set yet, default to first tab
				return 0;
			}
			if (allTabs.get(i).getPanel() == currentForm)
			{
				return i;
			}
		}
		return -1;
	}

	public String getTabNameAt(int i)
	{
		WebTabHolder holder = allTabs.get(i);
		return holder.getPanel().getName();
	}

	public String getTabTextAt(int i)
	{
		WebTabHolder holder = allTabs.get(i);
		return holder.getText();
	}

	public int getMnemonicAt(int i)
	{
		WebTabHolder holder = allTabs.get(i);
		return holder.getDisplayedMnemonic();
	}

	public void setMnemonicAt(int i, int m)
	{
		WebTabHolder holder = allTabs.get(i);
		holder.setDisplayedMnemonic(m);
	}

	public boolean isTabEnabledAt(int index)
	{
		WebTabHolder holder = allTabs.get(index);
		return holder.isEnabled();
	}

	public boolean removeTabAt(int index)
	{
		WebTabHolder holder = allTabs.get(index);
		List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
		boolean ok = holder.getPanel().notifyVisible(false, invokeLaterRunnables);
		Utils.invokeLater(application, invokeLaterRunnables);
		if (!ok)
		{
			return false;
		}
		allTabs.remove(index);
		if (holder.getPanel() == currentForm)
		{
			if (allTabs.size() > 0)
			{
				setActiveTabPanel(allTabs.get(0).getPanel());
			}
			else
			{
				//safety
				currentForm = null;
				replace(new Label("webform", new Model<String>("")));
			}
		}
		return true;
	}

	public boolean removeAllTabs()
	{
		for (int i = 0; i < allTabs.size(); i++)
		{
			WebTabHolder comp = allTabs.get(i);
			List<Runnable> invokeLaterRunnables = new ArrayList<Runnable>();
			boolean ok = comp.getPanel().notifyVisible(false, invokeLaterRunnables);
			Utils.invokeLater(application, invokeLaterRunnables);
			if (!ok)
			{
				return false;
			}
		}
		allTabs.clear();
		allRelationNames.clear();

		//safety
		currentForm = null;

		if (WebTabPanel.this.get("webform") != null) //$NON-NLS-1$
		{
			// replace it
			WebTabPanel.this.replace(new Label("webform", new Model<String>("")));//temporary add; //$NON-NLS-1$ //$NON-NLS-2$
		}
		else
		{
			// else add it
			WebTabPanel.this.add(new Label("webform", new Model<String>("")));//temporary add; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return true;
	}

	public void setTabEnabledAt(int i, boolean b)
	{
		WebTabHolder holder = allTabs.get(i);
		holder.setEnabled(b);
	}

	public void setTabIndex(int index)
	{
		setActiveTabPanel(allTabs.get(index).getPanel());
	}

	public void setTabIndex(String name)
	{
		for (int i = 0; i < allTabs.size(); i++)
		{
			WebTabHolder holder = allTabs.get(i);
			if (Utils.stringSafeEquals(holder.getPanel().getName(), name))
			{
				setActiveTabPanel(holder.getPanel());
				break;
			}
		}
	}

	public void setTabTextAt(int i, String s)
	{
		WebTabHolder holder = allTabs.get(i);
		holder.setText(TemplateGenerator.getSafeText(s));
	}


	/*
	 * readonly---------------------------------------------------
	 */
	public boolean isReadOnly()
	{
		if (currentForm != null)
		{
			return currentForm.isReadOnly();
		}
		return false;
	}

	public void setReadOnly(boolean b)
	{
		for (int i = 0; i < allTabs.size(); i++)
		{
			WebTabHolder holder = allTabs.get(i);
			holder.getPanel().setReadOnly(b);
		}
	}

	public void setName(String n)
	{
		name = n;
	}

	private String name;

	public String getName()
	{
		return name;
	}


	/*
	 * border---------------------------------------------------
	 */
	private Border border;

	public void setBorder(Border border)
	{
		this.border = border;
	}

	public Border getBorder()
	{
		return border;
	}


	/*
	 * opaque---------------------------------------------------
	 */
	public void setOpaque(boolean opaque)
	{
		this.opaque = opaque;
	}

	private boolean opaque;

	public boolean isOpaque()
	{
		return opaque;
	}


	private String tooltip;

	public void setToolTipText(String tooltip)
	{
		this.tooltip = Utils.stringIsEmpty(tooltip) ? null : tooltip;
	}

	/**
	 * @see com.servoy.j2db.ui.IComponent#getToolTipText()
	 */
	public String getToolTipText()
	{
		return tooltip;
	}


	/*
	 * font---------------------------------------------------
	 */
	public void setFont(Font font)
	{
		this.font = font;
	}

	private Font font;

	public Font getFont()
	{
		return font;
	}


	private Color background;

	public void setBackground(Color cbg)
	{
		this.background = cbg;
	}

	public Color getBackground()
	{
		return background;
	}


	private Color foreground;

	public void setForeground(Color cfg)
	{
		this.foreground = cfg;
	}

	public Color getForeground()
	{
		return foreground;
	}


	/*
	 * visible---------------------------------------------------
	 */
	public void setComponentVisible(boolean visible)
	{
		if (viewable)
		{
			setVisible(visible);
		}
	}

	public void setComponentEnabled(final boolean b)
	{
		if (accessible)
		{
			super.setEnabled(b);
			visitChildren(IComponent.class, new IVisitor<Component>()
			{
				public Object component(Component component)
				{
					if (component instanceof WebForm)
					{
						((WebForm)component).getController().setComponentEnabled(b);
					}
					else if (component instanceof IComponent && !(component instanceof MarkupContainer))
					{
						((IComponent)component).setComponentEnabled(b);
					}
					else if (!(component instanceof MarkupContainer))
					{
						component.setEnabled(b);
					}
					return CONTINUE_TRAVERSAL;
				}
			});
			getStylePropertyChanges().setChanged();
		}
	}

	private boolean accessible = true;

	public void setAccessible(boolean b)
	{
		if (!b) setComponentEnabled(b);
		accessible = b;
	}

	private boolean viewable = true;

	public void setViewable(boolean b)
	{
		if (!b) setComponentVisible(b);
		this.viewable = b;
	}

	public boolean isViewable()
	{
		return viewable;
	}

	/*
	 * location---------------------------------------------------
	 */
	private Point location = new Point(0, 0);

	public int getAbsoluteFormLocationY()
	{
		WebDataRenderer parent = findParent(WebDataRenderer.class);
		if (parent != null)
		{
			return parent.getYOffset() + getLocation().y;
		}
		return getLocation().y;
	}

	public void setLocation(Point location)
	{
		this.location = location;
	}

	public Point getLocation()
	{
		return location;
	}

	/*
	 * size---------------------------------------------------
	 */
	private Dimension size = new Dimension(0, 0);

	public Dimension getSize()
	{
		return size;
	}

	public Rectangle getWebBounds()
	{
		Dimension d = ((ChangesRecorder)getStylePropertyChanges()).calculateWebSize(size.width, size.height, border, new Insets(0, 0, 0, 0), 0, null);
		return new Rectangle(location, d);
	}

	/**
	 * @see com.servoy.j2db.ui.ISupportWebBounds#getPaddingAndBorder()
	 */
	public Insets getPaddingAndBorder()
	{
		return ((ChangesRecorder)getStylePropertyChanges()).getPaddingAndBorder(size.height, border, new Insets(0, 0, 0, 0), 0, null);
	}


	public void setSize(Dimension size)
	{
		if (this.size != null && currentForm != null && currentForm.isReady())
		{
			currentForm.getWebForm().setFormWidth(0);
		}
		this.size = size;
	}

	/**
	 * @see com.servoy.j2db.ui.ITabPanel#addScriptExecuter(com.servoy.j2db.IScriptExecuter)
	 */
	public void addScriptExecuter(IScriptExecuter executor)
	{
		this.scriptExecutor = executor;
	}

	/**
	 * @see com.servoy.j2db.ui.ITabPanel#setOnTabChangeMethodCmd(java.lang.String, TabPanel)
	 */
	public void setOnTabChangeMethodCmd(String onTabChangeMethodCmd, Object[] onTabChangeArgs)
	{
		this.onTabChangeMethodCmd = onTabChangeMethodCmd;
		this.onTabChangeArgs = onTabChangeArgs;
	}

	public void setTabSequenceIndex(int tabIndex)
	{
		this.tabSequenceIndex = tabIndex;
	}

	public int getTabSequenceIndex()
	{
		return tabSequenceIndex;
	}

	/**
	 * @param current
	 * @return
	 */
	public int getTabIndex(WebForm current)
	{
		if (currentForm != null && currentForm.getWebForm() == current)
		{
			Object o = scriptable.js_getTabIndex();
			if (o instanceof Integer)
			{
				if (((Integer)o).intValue() == -1) return -1;
				return ((Integer)o).intValue() - 1;
			}
		}
		for (int i = 0; i < allTabs.size(); i++)
		{
			WebTabHolder holder = allTabs.get(i);
			if (holder.getPanel().getFormName() == current.getController().getName())
			{
				return i;
			}
		}
		return -1;
	}

	public void valueChanged(ListSelectionEvent e)
	{
		if (parentData != null)
		{
			showFoundSet(currentForm, parentData, getDefaultSort());
		}
	}

	public void setTabSize(Dimension tabSize)
	{
		this.tabSize = tabSize;
	}

	public Dimension getTabSize()
	{
		return tabSize;
	}

	public void setHorizontalAlignment(int alignment)
	{

	}

	public static class ServoyTabIcon extends Label implements IResourceListener
	{
		private final WebTabHolder holder;

		public ServoyTabIcon(String id, final WebTabHolder holder, final IRuntimeComponent scriptable)
		{
			super(id);
			this.holder = holder;
			add(new StyleAppendingModifier(new Model<String>()
			{
				@SuppressWarnings("nls")
				@Override
				public String getObject()
				{
					StringBuilder result = new StringBuilder();
					if (holder.getIcon() != null)
					{
						result.append("width: ").append(holder.getIcon().getWidth()).append("px; height: ").append(holder.getIcon().getHeight()).append("px");
						result.append("; background-image: url(");
						result.append(getResponse().encodeURL(urlFor(IResourceListener.INTERFACE) + "&r=" + Math.random()));
						result.append(')');
						if (!scriptable.isEnabled())
						{
							result.append("; filter:alpha(opacity=50);-moz-opacity:.50;opacity:.50");
						}
					}
					else
					{
						result.append("width: 0px; height: 0px");
					}
					return result.toString();
				}
			}));
		}

		public void onResourceRequested()
		{
			if (holder.getIcon() != null)
			{
				holder.getIcon().onResourceRequested();
			}
		}
	}

	@Override
	protected void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag)
	{
		if (getBorder() instanceof TitledBorder)
		{
			getResponse().write(WebBaseButton.getTitledBorderOpenMarkup((TitledBorder)getBorder()));
		}
		super.onComponentTagBody(markupStream, openTag);
		if (getBorder() instanceof TitledBorder)
		{
			getResponse().write(WebBaseButton.getTitledBorderCloseMarkup());
		}
	}

	public ISupportSimulateBounds getBoundsProvider()
	{
		return findParent(ISupportSimulateBounds.class);
	}


	@Override
	public void uiRecreated()
	{
		recomputeTabSequence();
	}
}
