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
package com.servoy.j2db.smart;

import java.awt.AWTKeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.print.PrinterJob;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.mozilla.javascript.JavaMembers;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.servoy.j2db.ControllerUndoManager;
import com.servoy.j2db.DesignModeCallbacks;
import com.servoy.j2db.FormController;
import com.servoy.j2db.FormDialog;
import com.servoy.j2db.FormManager;
import com.servoy.j2db.FormWindow;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IForm;
import com.servoy.j2db.IFormUIInternal;
import com.servoy.j2db.IView;
import com.servoy.j2db.Messages;
import com.servoy.j2db.component.ComponentFactory;
import com.servoy.j2db.component.ISupportAsyncLoading;
import com.servoy.j2db.component.InvisibleBean;
import com.servoy.j2db.component.VisibleBean;
import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.FoundSetManager;
import com.servoy.j2db.dataprocessing.IDataSet;
import com.servoy.j2db.dataprocessing.IDisplay;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.ISaveConstants;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.dataprocessing.PrototypeState;
import com.servoy.j2db.dataprocessing.SortColumn;
import com.servoy.j2db.dataui.IServoyAwareBean;
import com.servoy.j2db.persistence.FormElementGroup;
import com.servoy.j2db.persistence.Part;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.printing.FormPreviewPanel;
import com.servoy.j2db.printing.PageList;
import com.servoy.j2db.printing.PrintPreview;
import com.servoy.j2db.scripting.ElementScope;
import com.servoy.j2db.scripting.GroupScriptObject;
import com.servoy.j2db.scripting.JSEvent;
import com.servoy.j2db.scripting.ScriptObjectRegistry;
import com.servoy.j2db.scripting.JSEvent.EventType;
import com.servoy.j2db.smart.dataui.CellAdapter;
import com.servoy.j2db.smart.dataui.DataComboBox;
import com.servoy.j2db.smart.dataui.DataRenderer;
import com.servoy.j2db.smart.dataui.FormBodyEditor;
import com.servoy.j2db.smart.dataui.FormLookupPanel;
import com.servoy.j2db.smart.dataui.PortalComponent;
import com.servoy.j2db.smart.dataui.ScriptButton;
import com.servoy.j2db.smart.dataui.ScriptLabel;
import com.servoy.j2db.smart.dataui.SolutionSkin;
import com.servoy.j2db.smart.dataui.SpecialTabPanel;
import com.servoy.j2db.smart.dataui.SplitPane;
import com.servoy.j2db.smart.scripting.TwoNativeJavaObject;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.ui.IDataRenderer;
import com.servoy.j2db.ui.IScriptBaseMethods;
import com.servoy.j2db.ui.IScriptReadOnlyMethods;
import com.servoy.j2db.ui.ISplitPane;
import com.servoy.j2db.ui.ITabPanel;
import com.servoy.j2db.util.AutoTransferFocusListener;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.IFocusCycleRoot;
import com.servoy.j2db.util.ISupportFocusTransfer;
import com.servoy.j2db.util.ITabPaneAlike;
import com.servoy.j2db.util.Utils;
import com.servoy.j2db.util.gui.OrientationApplier;
import com.servoy.j2db.util.gui.PartsScrollPane;


public class SwingForm extends PartsScrollPane implements IFormUIInternal<Component>, ISupportFocusTransfer
{

	/**
	 * Button for option pane that will trigger click when ENTER is released not pressed, so as to avoid a key released event being triggered on whatever
	 * component is focused after the option dialog closes.
	 */
	private class NoDoClickJButton extends JButton
	{

		private static final long serialVersionUID = 1L;

		public NoDoClickJButton()
		{
			super();
			addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0)
					{
						NoDoClickJButton.this.getModel().setPressed(true);
						NoDoClickJButton.this.getModel().setArmed(true);
					}
				}

				@Override
				public void keyReleased(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0)
					{
						NoDoClickJButton.this.getModel().setPressed(false);
						NoDoClickJButton.this.getModel().setArmed(false);
					}
				}
			});
		}

		@Override
		public void doClick()
		{
			// ignore this
		}

		@Override
		public void doClick(int pressTime)
		{
			// ignore this
		}
	}

	private static final long serialVersionUID = 1L;

	private static final String ACTION_GO_OUT_TO_NEXT = "goOutToNext"; //$NON-NLS-1$
	private static final String ACTION_GO_OUT_TO_PREV = "goOutToPrev"; //$NON-NLS-1$

	private final ControllerUndoManager undoManager;
	private final FormController formController;

	private List<Component> tabSeqComponentList = new ArrayList<Component>();
	private boolean transferFocusBackwards = false;
	private boolean readonly;

	private IView view;

	private final List<Component> markedComponents;

	private Color bgColor;
	private final Timer containerTimer;

	public SwingForm(FormController formController)
	{
		this.formController = formController;
		setFocusCycleRoot(true);

		ComponentFactory.applyScrollBarsProperty(this, formController.getForm());
		undoManager = new ControllerUndoManager();
		undoManager.setLimit(50);

		setOpaque(!formController.getForm().getTransparent());

		ActionMap am = this.getActionMap();
		am.put(ACTION_GO_OUT_TO_NEXT, new GoOutOfSwingFormAction(false));
		am.put(ACTION_GO_OUT_TO_PREV, new GoOutOfSwingFormAction(true));

		addFocusListener(new AutoTransferFocusListener(this, this));
		readonly = false;

		markedComponents = new ArrayList<Component>();
		registerFindKeystrokes(this);
		containerTimer = new Timer(300, new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					if (SwingForm.this.formController != null)
					{
						SwingForm.this.formController.notifyResized();
					}
				}
				finally
				{
					containerTimer.stop();
				}
			}
		});
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				containerTimer.start();
			}
		});
	}

	private void registerFindKeystrokes(JComponent c)
	{
		Action a = formController.getApplication().getCmdManager().getRegisteredAction("cmdperformfind"); //$NON-NLS-1$
		if (a != null)
		{
			c.registerKeyboardAction(a, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		}

		a = formController.getApplication().getCmdManager().getRegisteredAction("cmdstopsearchfindall"); //$NON-NLS-1$
		if (a != null)
		{
			c.registerKeyboardAction(a, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		}
	}

	// TODO: probably these should also be removed from the component at some point?
	private void addJumpOutActionToComponent(JComponent c, KeyStroke key, String actionName, boolean moveBackward)
	{
		if (!moveBackward)
		{
			Set<AWTKeyStroke> originalForwardKeys = c.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
			Set<AWTKeyStroke> newForwardKeys = new HashSet<AWTKeyStroke>(originalForwardKeys);
			if (newForwardKeys.contains(key)) newForwardKeys.remove(key);
			c.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, newForwardKeys);
		}
		else
		{
			Set<AWTKeyStroke> originalBackwardKeys = c.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
			Set<AWTKeyStroke> newBackwardKeys = new HashSet<AWTKeyStroke>(originalBackwardKeys);
			if (newBackwardKeys.contains(key)) newBackwardKeys.remove(key);
			c.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, newBackwardKeys);
		}

		InputMap im = c.getInputMap(WHEN_FOCUSED);
		im.put(key, actionName);
		ActionMap am = c.getActionMap();
		am.put(actionName, new GoOutOfSwingFormAction(moveBackward));
	}

	public void setTabSeqComponents(List<Component> tabSequence)
	{
		IDataRenderer formEditorRenderer = formController.getDataRenderers()[FormController.FORM_EDITOR];
		if (formEditorRenderer instanceof TableView && (tabSequence == null || !tabSequence.contains(formEditorRenderer)))
		{
			// table view should be added to tab sequence
			((TableView)formEditorRenderer).setTabSeqComponents(tabSequence);

			if (tabSequence != null && tabSequence.size() > 0)
			{
				// this means that we have to identify components that are part of the table view and based on where these are located set the view's tabIndex
				// when called from JS tabSequence will only contain table columns, not table view as opposed to initialization when tabSequence only contains the view
				int i;
				for (i = 0; i < tabSequence.size(); i++)
				{
					if (((TableView)formEditorRenderer).isColumnIdentifierComponent(tabSequence.get(i)))
					{
						tabSequence = new ArrayList<Component>(tabSequence);
						tabSequence.add(i, (Component)formEditorRenderer);
						break;
					}
				}
				i++;
				while (i < tabSequence.size())
				{
					if (((TableView)formEditorRenderer).isColumnIdentifierComponent(tabSequence.get(i)))
					{
						tabSequence.remove(i);
					}
					else
					{
						i++;
					}
				}
			}
		}
		this.tabSeqComponentList = tabSequence;
		if (tabSeqComponentList != null)
		{
			if (tabSeqComponentList.size() > 0)
			{
				JComponent lastComponent = (JComponent)tabSeqComponentList.get(tabSeqComponentList.size() - 1);
				if (!(lastComponent instanceof SpecialTabPanel))
				{
					int modifier = 0;
					if ((lastComponent instanceof TableView)) modifier = InputEvent.CTRL_DOWN_MASK;
					if ((lastComponent instanceof DataComboBox) && ((DataComboBox)lastComponent).isEditable()) lastComponent = (JComponent)((DataComboBox)lastComponent).getEditor().getEditorComponent();
					addJumpOutActionToComponent(lastComponent, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, modifier), ACTION_GO_OUT_TO_NEXT, false);
				}
				JComponent firstComponent = (JComponent)tabSeqComponentList.get(0);
				if (!(firstComponent instanceof SpecialTabPanel))
				{
					int modifier = 0;
					if ((firstComponent instanceof TableView)) modifier = InputEvent.CTRL_DOWN_MASK;
					if ((firstComponent instanceof DataComboBox) && ((DataComboBox)firstComponent).isEditable()) firstComponent = (JComponent)((DataComboBox)firstComponent).getEditor().getEditorComponent();
					addJumpOutActionToComponent(firstComponent, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, modifier | InputEvent.SHIFT_DOWN_MASK),
						ACTION_GO_OUT_TO_PREV, true);
				}
			}
		}
	}

	public List<Component> getTabSeqComponents()
	{
		return tabSeqComponentList;
	}

	public void focusField(Component field)
	{
		Component fieldToFocus = field;
		if (fieldToFocus instanceof SpecialTabPanel)
		{
			Object potential = ((SpecialTabPanel)fieldToFocus).getFirstFocusableField();
			if (potential != null)
			{
				fieldToFocus = (Component)potential;
				if (fieldToFocus instanceof IFocusCycleRoot< ? >)
				{
					potential = ((IFocusCycleRoot< ? >)fieldToFocus).getFirstFocusableField();
					if (potential != null) fieldToFocus = (Component)potential;
				}
			}
		}
		if (fieldToFocus instanceof TableView)
		{
			((TableView)fieldToFocus).editCellAt(((TableView)fieldToFocus).getSelectedRow());
		}
		else if (fieldToFocus != null)
		{
			if (view instanceof ListView)
			{
				view.editCellAt(((ListView)view).getSelectedIndex());
			}
			fieldToFocus.requestFocus();
		}
	}

	public Component getFirstFocusableField()
	{
		if (view instanceof ListView && !((ListView)view).isEditing())
		{
			view.editCellAt(((ListView)view).getSelectedIndex());
		}
		if (tabSeqComponentList != null && tabSeqComponentList.size() > 0)
		{
			return tabSeqComponentList.get(0);
		}
		return null;
	}


	public Component getLastFocusableField()
	{
		if (tabSeqComponentList != null && tabSeqComponentList.size() > 0)
		{
			return tabSeqComponentList.get(tabSeqComponentList.size() - 1);
		}
		return null;
	}

	/**
	 * @see com.servoy.j2db.IFormUI#getFormContext()
	 */
	public JSDataSet getFormContext()
	{
		SwingForm current = this;
		FormLookupPanel currentLookupPanel = null;
		SpecialTabPanel currentTabPanel = null;
		String currentBeanName = null;
		IDataSet set = new BufferedDataSet(new String[] { "containername", "formname", "tabpanel/beanname", "tabname", "tabindex" }, new ArrayList<Object[]>()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		set.addRow(new Object[] { null, current.formController.getName(), null, null, null });
		Container parent = getParent();
		while (parent != null)
		{
			if (parent instanceof SpecialTabPanel)
			{
				currentTabPanel = (SpecialTabPanel)parent;
			}
			else if (parent instanceof FormLookupPanel)
			{
				currentLookupPanel = (FormLookupPanel)parent;
			}
			else if (parent instanceof IServoyAwareBean && parent instanceof IComponent)
			{
				currentBeanName = ((IComponent)parent).getName();
			}
			else if (parent instanceof SwingForm)
			{
				current = (SwingForm)parent;
				if (currentTabPanel != null)
				{
					ITabPaneAlike panel = currentTabPanel.getEnclosingComponent();
					int index = -1;
					String tabName = null;
					if (currentLookupPanel != null)
					{
						index = panel.getTabIndex(currentLookupPanel);
						if (index != -1)
						{
							tabName = panel.getNameAt(index);
						}
					}
					set.addRow(0, new Object[] { null, current.formController.getName(), currentTabPanel.getName(), tabName, new Integer(index) });
				}
				else if (currentBeanName != null)
				{
					set.addRow(0, new Object[] { null, current.formController.getName(), currentBeanName, null, null });
				}
				else
				{
					set.addRow(0, new Object[] { null, current.formController.getName(), null, null, null });
				}
				currentBeanName = null;
				currentTabPanel = null;
				currentLookupPanel = null;
			}
			else if (parent instanceof MainPanel)
			{
				String containerName = ((MainPanel)parent).getContainerName();
				if (containerName != null)
				{
					for (int i = 0; i < set.getRowCount(); i++)
					{
						set.getRow(i)[0] = containerName;
					}
				}
				return new JSDataSet(formController.getApplication(), set);
			}
			parent = parent.getParent();
		}
		return new JSDataSet(formController.getApplication(), set);
	}

	/**
	 * TODO CHECK this one should always only be called Through the FormController!!
	 * 
	 * @param b
	 */
	@Override
	public void setReadOnly(boolean b)
	{
		if (readonly != b)
		{
			readonly = b;
			if (readonly == true)
			{
				for (int i = 0; i < getComponentCount(); i++)
				{
					setReadOnly(getComponent(i), b);
				}
			}
			else
			{
				if (markedComponents.size() != 0)
				{
					for (Component component : markedComponents)
					{
						setReadOnly(component, b);
					}
				}
				markedComponents.clear();
			}
		}
	}

	private boolean componentIsReadOnly(Component comp)
	{
		if (comp instanceof ListView)
		{
			if (((ListView)comp).isEditable() == true) return false;
		}
		else if (comp instanceof TableView)
		{
			if (((TableView)comp).isEditable() == true) return false;
		}
		else if (comp instanceof IScriptReadOnlyMethods)
		{
			return ((IScriptReadOnlyMethods)comp).js_isReadOnly();
		}
		return false;
	}

	private void setReadOnly(Component comp, boolean b)
	{
		if (comp instanceof ListView)
		{
			if (b == true)
			{
				if (componentIsReadOnly(comp) == false)
				{
					((ListView)comp).setEditable(false);
					if (markedComponents.contains(comp) == false) markedComponents.add(comp); // pay attention; what to do if container
				}
			}
			else
			{
				((ListView)comp).setEditable(!b);
			}

		}
		else if (comp instanceof TableView)
		{
			if (b == true)
			{
				if (componentIsReadOnly(comp) == false)
				{
					((TableView)comp).setEditable(false);
					if (markedComponents.contains(comp) == false) markedComponents.add(comp); // pay attention; what to do if container
				}
			}
			else
			{
				((TableView)comp).setEditable(true);
			}
		}
		else if (comp instanceof IScriptReadOnlyMethods)
		{
			if (b == true)
			{
				if (componentIsReadOnly(comp) == false)
				{
					((IScriptReadOnlyMethods)comp).js_setReadOnly(true);
					if (markedComponents.contains(comp) == false) markedComponents.add(comp); // pay attention; what to do if container
				}
			}
			else
			{
				((IScriptReadOnlyMethods)comp).js_setReadOnly(false);
			}
		}
		else if (comp instanceof Container)
		{
			Component[] comps = ((Container)comp).getComponents();
			for (Component element : comps)
			{
				setReadOnly(element, b);
			}
		}
	}

	@Override
	public void setBackground(Color bgColor)
	{
		this.bgColor = bgColor;
		if (bgColor != null)
		{
			JViewport viewport = getViewport();
			if (viewport != null) viewport.setBackground(bgColor);
			if (view instanceof ListView) ((ListView)view).setBackground(bgColor);
		}
	}

	public IView initView(IApplication application, FormController fp, int viewType)
	{
		view = null;

		IDataRenderer[] dataRenderers = fp.getDataRenderers();

		setTitleHeader((JComponent)dataRenderers[Part.TITLE_HEADER]);
		setHeader((JComponent)dataRenderers[Part.HEADER]);
		setLeadingGrandSummary((JComponent)dataRenderers[Part.LEADING_GRAND_SUMMARY]);
		setTrailingGrandSummary((JComponent)dataRenderers[Part.TRAILING_GRAND_SUMMARY]);
		setFooter((JComponent)dataRenderers[Part.FOOTER]);

		setWest(null);//remove any left slider
		switch (viewType)
		{
			case FormController.LOCKED_LIST_VIEW :
			case IForm.LIST_VIEW :
				view = new ListView();

				PrototypeState proto = null;
				if (fp.getFoundSet() != null)
				{
					proto = fp.getFoundSet().getPrototypeState();
				}
				else
				{
					proto = new PrototypeState(null);
				}
				((ListView)view).setPrototypeCellValue(proto); //this call is extreemly inportant, it prevent all rows retrieval 

				FormBodyEditor editor = null;

				if (dataRenderers[FormController.FORM_RENDERER] != null)
				{
					Component[] rendererComponents = ((Container)dataRenderers[FormController.FORM_RENDERER]).getComponents();
					for (Component rendererComponent : rendererComponents)
					{
						if (rendererComponent instanceof ISupportAsyncLoading)
						{
							//in listview it is impossible to get lazy loaded images displaying correctly, due to needed repaintfire, which we cannot initiate
							((ISupportAsyncLoading)rendererComponent).setAsyncLoadingEnabled(false);
						}
						rendererComponent.setFocusable(false);
					}

					((Component)dataRenderers[FormController.FORM_RENDERER]).setFocusable(false);
				}

				editor = new FormBodyEditor((DataRenderer)dataRenderers[FormController.FORM_EDITOR]);
				if (dataRenderers[FormController.FORM_RENDERER] != null)
				{
					DataRenderer dr = (DataRenderer)dataRenderers[FormController.FORM_RENDERER];
					dr.setRenderer(true);
					String bgcolorCalc = fp.getForm().getRowBGColorCalculation();
					if (bgcolorCalc != null)
					{
						//dr.setRowBGColorProvider(bgcolorCalc);
						dr.setShowSelection(false);
						((ListView)view).setRowBGColorScript(bgcolorCalc, fp.getForm().getInstanceMethodArguments("rowBGColorCalculation")); //$NON-NLS-1$
						((DataRenderer)dataRenderers[FormController.FORM_EDITOR]).setShowSelection(false);
					}
					((ListView)view).setCellRenderer(dr);
				}
				else
				{
					// form with no body part - used only for printing probably
					((ListView)view).setCellRenderer(new ListCellRenderer()
					{
						public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
						{
							return new JLabel(""); //$NON-NLS-1$
						}
					});
				}
				((ListView)view).setCellEditor(editor);
				((ListView)view).setRendererSameAsEditor(false);
				((ListView)view).setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				break;

			case FormController.LOCKED_TABLE_VIEW :
			case FormController.TABLE_VIEW :
				view = new TableView(application, fp, fp.getForm(), fp.getForm(), fp.getScriptExecuter(), dataRenderers[Part.HEADER],
					dataRenderers[Part.LEADING_GRAND_SUMMARY], false);
				dataRenderers[FormController.FORM_EDITOR] = (IDataRenderer)view;
				break;

			case IForm.LOCKED_RECORD_VIEW :
			case IForm.RECORD_VIEW :
			default :
				view = new RecordView(application);
				if (dataRenderers[FormController.FORM_EDITOR] != null)
				{
					((DataRenderer)dataRenderers[FormController.FORM_EDITOR]).setShowSelection(false);
					((RecordView)view).setCellRenderer((DataRenderer)dataRenderers[FormController.FORM_EDITOR]);
				}
				int form_id = fp.getForm().getNavigatorID();
				if (form_id == 0)
				{
					JComponent slider = ((RecordView)view).getSliderComponent();
					// set same background color to the default navigator
					JViewport viewport = getViewport();
					if (viewport != null)
					{
						Color bg = viewport.getBackground();
						if (bg != null) slider.setBackground(bg);
						//slider.setOpaque(!(bgColor == null || bgColor instanceof ColorUIResource));
					}
					setWest(slider);
				}
		}

		getVerticalScrollBar().setValue(1);
		getVerticalScrollBar().setValue(0);
		if (application instanceof J2DBClient)
		{
			UIDefaults defs = ((J2DBClient)application).getSkinLookAndFeelDefaults();
			if (defs != null)
			{
				SolutionSkin.updateComponentTreeUI(defs, (Component)view);
			}
		}
		setViewportView((JComponent)view);
		if (view instanceof TableView)
		{
			// the table view needs it's orientation set correctly
			OrientationApplier.setOrientationToAWTComponent(this, application.getLocale(), application.getSolution().getTextOrientation());
		}
		if (view instanceof ListView)
		{
			view.requestFocus();
		}
		OrientationApplier.setOrientationToAWTComponent(this, application.getLocale(), application.getSolution().getTextOrientation());
		if ((bgColor != null) && (view instanceof ListView)) ((ListView)view).setBackground(bgColor);
		// Apply the opacity to the newly added view (and slider if any).
		setOpaque(isOpaque());
		return view;
	}

	@Override
	public void destroy()
	{
		super.destroy();
		Container p = getParent();
		if (p != null)
		{
			p.remove(this);
		}
	}

	public ControllerUndoManager getUndoManager()
	{
		return undoManager;
	}

	public FormController getController()
	{
		return formController;
	}

	public void setComponentVisible(boolean b_visible)
	{
		setVisible(b_visible);
	}

	public void setComponentEnabled(boolean enabled)
	{
		setEnabled(enabled);
	}

	public void showSortDialog(IApplication application, String options)
	{
		try
		{
			Table t = (Table)formController.getTable();
			if (t != null)
			{
				List<SortColumn> sortColumns = null;
				if (options == null || options.length() == 0)
				{
					sortColumns = formController.getFormModel().getLastSortColumns();
				}
				else
				{
					sortColumns = ((FoundSetManager)application.getFoundSetManager()).getSortColumns(t, options);
				}
				Window window = SwingUtilities.getWindowAncestor(this);
				if (window == null) window = application.getMainApplicationFrame();
				SortDialog nfd = (SortDialog)application.getWindow("SortDialog"); //$NON-NLS-1$
				if (nfd == null || nfd.getOwner() != window)
				{
					if (window instanceof Frame)
					{
						nfd = new SortDialog((Frame)window, application);
					}
					else if (window instanceof Dialog)
					{
						nfd = new SortDialog((Dialog)window, application);
					}
					application.registerWindow("SortDialog", nfd); //$NON-NLS-1$
				}
				List<SortColumn> list = nfd.showDialog(t, sortColumns);
				if (list != null) formController.sort(list, false);
			}
		}
		catch (Exception ex)
		{
			application.reportError(Messages.getString("servoy.formPanel.error.sortRecordsDialog"), ex); //$NON-NLS-1$
		}
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#makeElementsScriptObject(org.mozilla.javascript.Scriptable)
	 */
	public ElementScope makeElementsScriptObject(Scriptable fs, Map<String, Object[]> hmChildrenJavaMembers, IDataRenderer[] dataRenderers, IView controller)
	{
		ElementScope es = new ElementScope(fs);

		int counter = 0;
		for (int i = FormController.FORM_RENDERER + 1; i < dataRenderers.length; i++)
		{
			IDataRenderer dr = dataRenderers[i];
			if (dr == null) continue;

			Object[] comps = null;
			Object[] compsRenderer = null;
			if (dr instanceof DataRenderer)
			{
				comps = ((DataRenderer)dr).getComponents();
				if (i == FormController.FORM_EDITOR && dataRenderers[FormController.FORM_RENDERER] != null)
				{
					compsRenderer = ((DataRenderer)dataRenderers[FormController.FORM_RENDERER]).getComponents();
				}
			}
			else if (dr instanceof TableView)
			{
				TableView tv = (TableView)dr;
				comps = new Component[tv.getColumnCount()];
				compsRenderer = new Component[tv.getColumnCount()];

				for (int j = 0; j < comps.length; j++)
				{
					comps[j] = ((CellAdapter)tv.getCellEditor(0, j)).getEditor();
					compsRenderer[j] = ((CellAdapter)tv.getCellEditor(0, j)).getRenderer();
				}
			}
			else if (dr instanceof WebMarkupContainer)
			{
				comps = new Object[((WebMarkupContainer)dr).size()];
				Iterator< ? > it = ((WebMarkupContainer)dr).iterator();
				int j = 0;
				while (it.hasNext())
				{
					comps[j++] = it.next();
				}
			}

			counter = registerComponentsToScope(fs, es, counter, comps, compsRenderer, (Component)controller, hmChildrenJavaMembers);
		}

		es.setLocked(true);
		return es;
	}

	private int registerComponentsToScope(Scriptable fs, ElementScope es, int counter, Object[] comps, Object[] compsRenderer, Component controller,
		Map<String, Object[]> hmChildrenJavaMembers)
	{
		if (comps != null)
		{
			for (int j = 0; j < comps.length; j++)
			{
				Object comp = comps[j];
				if (comp instanceof PortalComponent)
				{
					PortalComponent portal = (PortalComponent)comp;
					counter = registerComponentsToScope(fs, es, counter, portal.getEditorComponents(), portal.getRendererComponents(), portal,
						hmChildrenJavaMembers);
				}

				String name = null;

				if (comp instanceof IComponent)
				{
					name = ((IComponent)comp).getName();
				}
				else if (comp instanceof Component)
				{
					name = ((Component)comp).getName();
				}

				Object obj = comp;
				if (comp instanceof InvisibleBean)
				{
					obj = ((InvisibleBean)comp).getDelegate();
				}
				else if (comp instanceof VisibleBean)
				{
					obj = ((VisibleBean)comp).getDelegate();
				}
				JavaMembers jm = ScriptObjectRegistry.getJavaMembers(obj.getClass(), ScriptableObject.getTopLevelScope(fs));

				String groupName = FormElementGroup.getName((String)formController.getComponentProperty(comp, ComponentFactory.GROUPID_COMPONENT_PROPERTY));
				boolean named = name != null && !name.equals("") && !name.startsWith(ComponentFactory.WEB_ID_PREFIX); //$NON-NLS-1$
				if (groupName != null || named)
				{
					Object obj2 = null;

					if (compsRenderer != null)
					{
						obj2 = compsRenderer[j];
						if (obj2 instanceof InvisibleBean)
						{
							obj2 = ((InvisibleBean)obj2).getDelegate();
						}
					}

					try
					{
						Scriptable s = null;
						if (obj2 != null)
						{
							NativeJavaObject s2 = new NativeJavaObject(fs, obj2, jm);

							s = new TwoNativeJavaObject(fs, obj, s2, jm, controller);
						}
						else
						{
							s = new NativeJavaObject(fs, obj, jm);
						}
						if (named)
						{
							es.put(name, fs, s);
							es.put(counter++, fs, s);
							hmChildrenJavaMembers.put(name, new Object[] { jm, obj });
						}
						if (groupName != null)
						{
							Object group = es.get(groupName, fs);
							if (group == Scriptable.NOT_FOUND)
							{
								group = new NativeJavaObject(fs, new GroupScriptObject(fs), ScriptObjectRegistry.getJavaMembers(GroupScriptObject.class,
									ScriptableObject.getTopLevelScope(fs)));
								es.put(groupName, fs, group);
								es.put(counter++, fs, group);
							}
							if (group instanceof NativeJavaObject && ((NativeJavaObject)group).unwrap() instanceof GroupScriptObject)
							{
								((GroupScriptObject)(((NativeJavaObject)group).unwrap())).addScriptable(s);
							}
						}
					}
					catch (Throwable ex)
					{
						Debug.error(ex);//incase classdefnot founds are thrown for beans,applets/plugins
					}
				}
			}
		}
		return counter;
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#updateFormUI()
	 */
	public void updateFormUI()
	{
		invalidateTables(this);
	}

	private void invalidateTables(JComponent component)
	{
		int count = component.getComponentCount();
		for (int i = 0; i < count; i++)
		{
			Component child = component.getComponent(i);
			if (child instanceof JTable)
			{
				((JTable)child).repaint();
			}
			else if (child instanceof JComponent)
			{
				invalidateTables((JComponent)child);
			}
		}
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#print(boolean, boolean, boolean, java.awt.print.PrinterJob)
	 */
	public void print(boolean showDialogs, boolean printCurrentRecordOnly, boolean showPrinterSelectDialog, PrinterJob printerJob)
	{
		IFoundSetInternal fs = formController.getFoundSet();
		if (!printCurrentRecordOnly)
		{
			if (showDialogs)
			{
				int option = willingToPrint(fs);
				if (option == 2)
				{
					printCurrentRecordOnly = true;
				}
				else if (option == 1)
				{
					return;//cancel
				}
			}
		}
		IApplication application = formController.getApplication();
		try
		{
			application.blockGUI(Messages.getString("servoy.formPanel.status.printProgress")); //$NON-NLS-1$

			if (printCurrentRecordOnly)
			{
				fs = fs.copyCurrentRecordFoundSet();
			}

			FormPreviewPanel fpp = new FormPreviewPanel(application, formController, fs);
			fpp.process();
			PrintPreview.startPrinting(application, fpp.getPageable(), printerJob, formController.getPreferredPrinterName(), showPrinterSelectDialog, false);
			fpp.destroy();
		}
		catch (Exception ex)
		{
			application.reportError(Messages.getString("servoy.formPanel.error.printDocument"), ex); //$NON-NLS-1$
		}
		finally
		{
			application.releaseGUI();
		}
	}

	/**
	 * 
	 * @return 0 when ok,1 when cancel,2 when current rec.
	 */
	private int willingToPrint(IFoundSetInternal formModel)
	{
		//test if foundset is big. if so ask if really want to print/preview
		if (formModel.getSize() >= ((FoundSetManager)formModel.getFoundSetManager()).pkChunkSize)
		{
			Object[] options = new String[] { Messages.getString("servoy.button.ok"), //$NON-NLS-1$
			Messages.getString("servoy.button.cancel"), //$NON-NLS-1$
			Messages.getString("servoy.formPanel.printCurrentRecord") //$NON-NLS-1$
			};
			return JOptionPane.showOptionDialog(formController.getApplication().getMainApplicationFrame(), Messages.getString(
				"servoy.formPanel.message.largeResultset", new Object[] { new Integer(formModel.getSize()) }), //$NON-NLS-1$
				Messages.getString("servoy.general.warning"), //$NON-NLS-1$
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
		}
		return 0;
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#printPreview(boolean, boolean, java.awt.print.PrinterJob)
	 */
	public void printPreview(boolean showDialogs, boolean printCurrentRecordOnly, int zoomFactor, PrinterJob printerJob)
	{
		IApplication application = formController.getApplication();
		try
		{
			// TODO do a print preview even if records are not saved?
			if (application.getFoundSetManager().getEditRecordList().stopEditing(false) != ISaveConstants.STOPPED) return;
			IFoundSetInternal set = formController.getFormModel();
			if (set == null)
			{
				if (showDialogs)
				{
					JOptionPane.showMessageDialog(
						application.getMainApplicationFrame(),
						Messages.getString("servoy.formPanel.error.noRecordsToPrint"), Messages.getString("servoy.general.warning"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
				}
				return;
			}
			if (showDialogs)
			{
				if (set.getSize() == 0)//test if foundset is zero. if so ask if really want to print/preview
				{
					int val = JOptionPane.showConfirmDialog(
						application.getMainApplicationFrame(),
						Messages.getString("servoy.formPanel.error.noRecordsToPrint"), Messages.getString("servoy.general.warning"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
					if (val != JOptionPane.OK_OPTION)
					{
						return;
					}
				}
				else if (!printCurrentRecordOnly)
				{
					int option = willingToPrint(set);
					if (option == 2)
					{
						printCurrentRecordOnly = true;
					}
					else if (option == 1)
					{
						return;//cancel
					}
				}
			}
			if (printCurrentRecordOnly)
			{
				set = set.copyCurrentRecordFoundSet();
			}
			((FormManager)application.getFormManager()).showPreview(formController, set, zoomFactor, printerJob);
		}
		catch (Exception ex)
		{
			application.reportError(Messages.getString("servoy.formPanel.error.printPreview"), ex); //$NON-NLS-1$
		}
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#printXML(boolean)
	 */
	public String printXML(boolean printCurrentRecordOnly)
	{
		IApplication application = formController.getApplication();
		IFoundSetInternal fs = formController.getFoundSet();
		try
		{
			application.blockGUI(Messages.getString("servoy.formPanel.status.xmlPrinting")); //$NON-NLS-1$

			if (printCurrentRecordOnly)
			{
				fs = fs.copyCurrentRecordFoundSet();
			}

			FormPreviewPanel fpp = new FormPreviewPanel(application, formController, fs);
			fpp.process();
			StringWriter w = new StringWriter();
			((PageList)fpp.getPageable()).toXML(w);
			fpp.destroy();
			return w.toString();
		}
		catch (Throwable ex)
		{
			application.reportError(Messages.getString("servoy.formPanel.error.printDocument"), ex); //$NON-NLS-1$
		}
		finally
		{
			application.releaseGUI();
		}
		return null;
	}

	public String getId()
	{
		return (String)getClientProperty("Id"); //$NON-NLS-1$
	}

	public String getContainerName()
	{
		Container parent = getParent();
		while (parent != null)
		{
			if (parent instanceof FormWindow && parent instanceof Window)
			{
				Window w = ((Window)parent);
				return w.isVisible() ? w.getName() : null;
			}
			parent = parent.getParent();
		}
		return null;
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#isFormInDialog()
	 */
	public boolean isFormInDialog()
	{
		Container parent = getParent();
		while (parent != null)
		{
			if (parent instanceof FormDialog)
			{
				return parent.isVisible();
			}
			parent = parent.getParent();
		}
		return false;
	}

	/**
	 * @see java.awt.Component#toString()
	 */
	@Override
	public String toString()
	{
		return "Form:" + getController().getName() + "," + super.toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public boolean isTraversalPolicyEnabled()
	{
		if (view instanceof ListView)
		{
			Component editor = ((ListView)view).getEditorComponent();
			if (editor instanceof DataRenderer) return ((DataRenderer)editor).isTraversalPolicyEnabled();
		}
		return true;
	}

	private boolean isPartOfSlider(Component c)
	{
		if (c == null) return false;
		if (c instanceof Slider) return true;
		if (c.getParent() == null) return false;
		if (c.getParent() instanceof Slider) return true;
		return false;
	}

	private boolean isPartOfTabPanel(Component c)
	{
		if (c == null) return false;
		if (c instanceof SpecialTabPanel) return true;
		if (c.getParent() == null) return false;
		if (c.getParent() instanceof SpecialTabPanel) return true;
		return false;
	}

	public void prepareForSave(boolean looseFocus)
	{
		final Component currentFocusHolder = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		Component component = currentFocusHolder;
		if (component instanceof JRootPane || component instanceof JFrame)
		{
			component = this;
		}
		else while (component != null && !(component instanceof Window) && !(component instanceof IView) && !isPartOfSlider(component) &&
			!isPartOfTabPanel(component))
		{
			component = component.getParent();
		}
		if (component != null && currentFocusHolder != component && !(component instanceof TableView) &&
			!(component instanceof ListView && !((ListView)component).isEditing()))
		{
			// if component == null then it was a editing component of a Table.
			// focus will be transferred automatic then.
			// Execute requestFocus asynchronously, calling requestFocus directly can keep
			// focus away from a dialog that is in the process of being popped up.
			final Component comp = component;
			formController.getApplication().invokeLater(new Runnable()
			{
				public void run()
				{
					if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == currentFocusHolder)
					{
						comp.requestFocus();
					}
				}
			});
		}
	}

	public boolean isTransferFocusBackwards()
	{
		return transferFocusBackwards;
	}

	public void setTransferFocusBackwards(boolean transferBackwards)
	{
		this.transferFocusBackwards = transferBackwards;
	}

	private class GoOutOfSwingFormAction extends AbstractAction
	{
		private final boolean moveBackward;

		public GoOutOfSwingFormAction(boolean moveBackward)
		{
			this.moveBackward = moveBackward;
		}

		public void actionPerformed(ActionEvent e)
		{
			Container formDirectParent = SwingForm.this.getParent();
			boolean solved = false;
			if (formDirectParent instanceof FormLookupPanel)
			{
				Component lastSpecialTabPanel = null;
				SwingForm sf = null;
				Component lastTabPanelAlike = null;
				do
				{
					lastTabPanelAlike = formDirectParent.getParent();
					lastSpecialTabPanel = lastTabPanelAlike.getParent();

					Component parent = lastSpecialTabPanel.getParent();
					while ((parent != null) && !(parent instanceof SwingForm))
						parent = parent.getParent();

					sf = (SwingForm)parent;
					formDirectParent = sf.getParent();
				}
				while ((formDirectParent instanceof FormLookupPanel) &&
					((lastSpecialTabPanel.equals(sf.getLastFocusableField()) && !moveBackward) || (lastSpecialTabPanel.equals(sf.getFirstFocusableField()) && moveBackward)));
				((ISupportFocusTransfer)lastSpecialTabPanel).setTransferFocusBackwards(moveBackward);
				if (lastTabPanelAlike instanceof SplitPane && SwingForm.this.getParent().equals(((SplitPane)lastTabPanelAlike).getLeftComponent()))
				{
					KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(((SplitPane)lastTabPanelAlike).getRightComponent());
				}
				else KeyboardFocusManager.getCurrentKeyboardFocusManager().upFocusCycle(lastTabPanelAlike);
				solved = true;
			}
			if (!solved)
			{
				SwingForm.this.setTransferFocusBackwards(moveBackward);
				KeyboardFocusManager.getCurrentKeyboardFocusManager().upFocusCycle();
			}
		}

	}

	public boolean showYesNoQuestionDialog(IApplication application, String message, String title)
	{
		// create custom Yes/No buttons to avoid bug 129045 (ENTER from this dialog closing and showing the dialog again
		// because the dialog closes on ENTER press and it is reopened on ENTER release) - reproducible with Servoy Developer 3.5.7_05-build 520 / Java version 1.5.0_11-b03 (Windows XP)
		Component parentComponent = application.getMainApplicationFrame();
		Locale l = application.getMainApplicationFrame().getLocale();
		JButton buttonYes = getNoDoClickJButton(UIManager.getString("OptionPane.yesButtonText", l), getMnemonic("OptionPane.yesButtonMnemonic", l)); //$NON-NLS-1$ //$NON-NLS-2$
		JButton buttonNo = getNoDoClickJButton(UIManager.getString("OptionPane.noButtonText", l), getMnemonic("OptionPane.noButtonMnemonic", l)); //$NON-NLS-1$ //$NON-NLS-2$

		final JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, new Object[] { buttonYes, buttonNo },
			buttonYes);

		Font buttonFont = (Font)UIManager.get("OptionPane.buttonFont", parentComponent.getLocale()); //$NON-NLS-1$
		if (buttonFont != null)
		{
			buttonYes.setFont(buttonFont);
			buttonNo.setFont(buttonFont);
		}

		int threshhold = UIManager.getInt("OptionPane.buttonClickThreshhold", parentComponent.getLocale()); //$NON-NLS-1$
		buttonYes.setMultiClickThreshhold(threshhold);
		buttonNo.setMultiClickThreshhold(threshhold);

		ActionListener selectButtonActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				pane.setValue(e.getSource());
			}
		};
		buttonYes.addActionListener(selectButtonActionListener);
		buttonNo.addActionListener(selectButtonActionListener);

		pane.setInitialValue(buttonYes);
		pane.setComponentOrientation(((parentComponent == null) ? JOptionPane.getRootFrame() : parentComponent).getComponentOrientation());

		JDialog dialog = pane.createDialog(parentComponent, title);

		pane.selectInitialValue();
		dialog.setVisible(true);
		dialog.dispose();

		return pane.getValue() == buttonYes;
	}

	private int getMnemonic(String key, Locale l)
	{
		String value = (String)UIManager.get(key, l);

		if (value == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException nfe)
		{
		}
		return 0;
	}

	private JButton getNoDoClickJButton(String text, int mnemonic)
	{
		NoDoClickJButton b = new NoDoClickJButton();
		b.setText(text);
		b.setMnemonic(mnemonic);
		b.setName("OptionPane.button"); //$NON-NLS-1$
		return b;
	}

	private JComponent realViewPort = null;
	private JComponent realHeader = null;
	private JComponent realTitleHeader = null;
	private JComponent realFooter = null;
	private JComponent realLeadingGrandSummary = null;
	private JComponent realTrailingGrandSummary = null;

	/**
	 * @see com.servoy.j2db.IFormUIInternal#setDesignMode(boolean)
	 */
	public void setDesignMode(DesignModeCallbacks callback)
	{
		if (callback != null && realViewPort == null)
		{
			JLayeredPane pane = new JLayeredPane();
			pane.setLayout(new LayeredPaneLayout());
			realViewPort = (JComponent)getViewport().getView();
			pane.setPreferredSize(realViewPort.getPreferredSize());
			pane.setSize(realViewPort.getSize());
			pane.add(realViewPort, new Integer(1));

			SelectionHandler sh = new SelectionHandler();
			final DesignPanel panel = new DesignPanel(realViewPort, callback, formController, sh);
			pane.add(panel, new Integer(2));

			setViewportView(pane);

			if (header != null)
			{
				realHeader = header;
				DesignPanel designPanel = new DesignPanel(realHeader, callback, formController, sh);
				JLayeredPane layer = new JLayeredPane();
				layer.setLayout(new LayeredPaneLayout());
				layer.setPreferredSize(realHeader.getPreferredSize());
				layer.setSize(realHeader.getSize());
				layer.add(realHeader, new Integer(1));
				layer.add(designPanel, new Integer(2));
				setHeader(layer);
			}
			if (titleHeader != null)
			{
				realTitleHeader = titleHeader;
				DesignPanel designPanel = new DesignPanel(realTitleHeader, callback, formController, sh);
				JLayeredPane layer = new JLayeredPane();
				layer.setLayout(new LayeredPaneLayout());
				layer.setPreferredSize(realTitleHeader.getPreferredSize());
				layer.setSize(realTitleHeader.getSize());
				layer.add(realTitleHeader, new Integer(1));
				layer.add(designPanel, new Integer(2));
				setTitleHeader(layer);
			}
			if (footer != null)
			{
				realFooter = footer;
				DesignPanel designPanel = new DesignPanel(realFooter, callback, formController, sh);
				JLayeredPane layer = new JLayeredPane();
				layer.setLayout(new LayeredPaneLayout());
				layer.setPreferredSize(realFooter.getPreferredSize());
				layer.setSize(realFooter.getSize());
				layer.add(realFooter, new Integer(1));
				layer.add(designPanel, new Integer(2));

				setFooter(layer);
			}

			if (leadingGrandSummary != null)
			{
				realLeadingGrandSummary = leadingGrandSummary;
				DesignPanel designPanel = new DesignPanel(realLeadingGrandSummary, callback, formController, sh);
				JLayeredPane layer = new JLayeredPane();
				layer.setLayout(new LayeredPaneLayout());
				layer.setPreferredSize(realLeadingGrandSummary.getPreferredSize());
				layer.setSize(realLeadingGrandSummary.getSize());
				layer.add(realLeadingGrandSummary, new Integer(1));
				layer.add(designPanel, new Integer(2));
				setLeadingGrandSummary(layer);
			}
			if (trailingGrandSummary != null)
			{
				realTrailingGrandSummary = trailingGrandSummary;
				DesignPanel designPanel = new DesignPanel(realTrailingGrandSummary, callback, formController, sh);
				JLayeredPane layer = new JLayeredPane();
				layer.setLayout(new LayeredPaneLayout());
				layer.setPreferredSize(realTrailingGrandSummary.getPreferredSize());
				layer.setSize(realTrailingGrandSummary.getSize());
				layer.add(realTrailingGrandSummary, new Integer(1));
				layer.add(designPanel, new Integer(2));

				setTrailingGrandSummary(layer);
			}

			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					panel.requestFocus();
				}
			});

		}
		else if (realViewPort != null)
		{
			setViewportView(realViewPort);
			realViewPort = null;
			if (realHeader != null)
			{
				setHeader(realHeader);
				realHeader = null;
			}
			if (realTitleHeader != null)
			{
				setTitleHeader(realTitleHeader);
				realTitleHeader = null;
			}
			if (realFooter != null)
			{
				setFooter(realFooter);
				realFooter = null;
			}

			if (realLeadingGrandSummary != null)
			{
				setLeadingGrandSummary(realLeadingGrandSummary);
				realLeadingGrandSummary = null;
			}
			if (realTrailingGrandSummary != null)
			{
				setTrailingGrandSummary(realTrailingGrandSummary);
				realTrailingGrandSummary = null;
			}
		}
		validate();
		repaint();

//		if (west != null) west.setEnabled(realViewPort == null);

	}

	public boolean isDesignMode()
	{
		return realViewPort != null;
	}

	/**
	 * @see com.servoy.j2db.IFormUIInternal#uiRecreated()
	 */
	public void uiRecreated()
	{
	}

	public Dimension getFormSize()
	{
		Dimension size = null;
		if (formController.getViewComponent() instanceof Component)
		{
			size = ((Component)formController.getViewComponent()).getSize();
		}
		else size = getSize();
		return size;
	}

	public int getFormWidth()
	{
		Dimension size = null;
		if (formController.getViewComponent() instanceof Component)
		{
			size = ((Component)formController.getViewComponent()).getSize();
		}
		else size = getSize();
		return size.width;
	}

	public int getPartHeight(int partType)
	{
		if (formController.getDataRenderers() != null && formController.getDataRenderers().length > partType)
		{
			Component renderer = (Component)formController.getDataRenderers()[partType];
			if (renderer != null)
			{
				return renderer.getHeight();
			}
		}
		return 0;
	}

	private static class LayeredPaneLayout implements LayoutManager2
	{

		/**
		 * @see java.awt.LayoutManager2#addLayoutComponent(java.awt.Component, java.lang.Object)
		 */
		public void addLayoutComponent(Component comp, Object constraints)
		{
		}

		/**
		 * @see java.awt.LayoutManager2#getLayoutAlignmentX(java.awt.Container)
		 */
		public float getLayoutAlignmentX(Container target)
		{
			return 0;
		}

		/**
		 * @see java.awt.LayoutManager2#getLayoutAlignmentY(java.awt.Container)
		 */
		public float getLayoutAlignmentY(Container target)
		{
			return 0;
		}

		/**
		 * @see java.awt.LayoutManager2#invalidateLayout(java.awt.Container)
		 */
		public void invalidateLayout(Container target)
		{
		}

		/**
		 * @see java.awt.LayoutManager2#maximumLayoutSize(java.awt.Container)
		 */
		public Dimension maximumLayoutSize(Container target)
		{
			return target.getSize();
		}

		/**
		 * @see java.awt.LayoutManager#addLayoutComponent(java.lang.String, java.awt.Component)
		 */
		public void addLayoutComponent(String name, Component comp)
		{
		}

		/**
		 * @see java.awt.LayoutManager#layoutContainer(java.awt.Container)
		 */
		public void layoutContainer(Container parent)
		{
			Dimension size = parent.getSize();
			Component[] components = parent.getComponents();
			for (Component component : components)
			{
				component.setBounds(0, 0, size.width, size.height);
			}

		}

		/**
		 * @see java.awt.LayoutManager#minimumLayoutSize(java.awt.Container)
		 */
		public Dimension minimumLayoutSize(Container parent)
		{
			return new Dimension(0, 0);
		}

		/**
		 * @see java.awt.LayoutManager#preferredLayoutSize(java.awt.Container)
		 */
		public Dimension preferredLayoutSize(Container parent)
		{
			return parent.getPreferredSize();
		}

		/**
		 * @see java.awt.LayoutManager#removeLayoutComponent(java.awt.Component)
		 */
		public void removeLayoutComponent(Component comp)
		{
		}

	}

	private static class DesignPanel extends JPanel implements MouseListener, MouseMotionListener, AncestorListener
	{
		private final JComponent realViewPort;

		private int dragMode;
		private Point lastMousePosition;

		private Point lassoPosition;

		private final DesignModeCallbacks callback;

		private final FormController controller;

		private boolean resized;
		private Object canResize;
		private boolean moved;
		private Object canMove;

		private final SelectionHandler selectionHandler;

		private final ComponentListener componentListener = new ComponentListener()
		{

			public void componentShown(ComponentEvent e)
			{
			}

			public void componentResized(ComponentEvent e)
			{
				componentMoved(e);
			}

			public void componentMoved(ComponentEvent e)
			{
				Map<JComponent, int[][]> selection = selectionHandler.getSelection(DesignPanel.this);
				if (selection.containsKey(e.getComponent()))
				{
					addSelectedComponent(e.getComponent(), e.getComponent().getBounds());
					DesignPanel.this.repaint();
				}
			}

			public void componentHidden(ComponentEvent e)
			{
			}
		};

		public DesignPanel(JComponent realViewPort, DesignModeCallbacks callback, FormController controller, SelectionHandler selectionHandler)
		{
			this.realViewPort = realViewPort;
			this.callback = callback;
			this.controller = controller;
			this.selectionHandler = selectionHandler;
			addMouseListener(this);
			addMouseMotionListener(this);
			addAncestorListener(this);
			setFocusable(true);
			setOpaque(false);
			setPreferredSize(realViewPort.getPreferredSize());
			setSize(realViewPort.getSize());

			attachComponentListener(realViewPort);
		}


		private void attachComponentListener(Container comp)
		{
			Component[] components = comp.getComponents();
			for (Component component : components)
			{
				if (component instanceof IDisplay || component instanceof ScriptLabel || component instanceof ScriptButton)
				{
					component.addComponentListener(componentListener);
				}
				else if (component instanceof Container)
				{
					attachComponentListener((Container)component);
				}
			}
		}

		public void mousePressed(MouseEvent e)
		{
			requestFocus();

			dragMode = -1;

			Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelectionForChange(this);
			if (selectedComponents.size() == 1)
			{
				int[][] positions = selectedComponents.values().iterator().next();
				if (positions != null)
				{
					int x = e.getX();
					int y = e.getY();
					for (int i = 0; i < positions.length; i++)
					{
						int[] position = positions[i];
						if (position[0] < x && x < (position[0] + 6))
						{
							if (position[1] < y && y < (position[1] + 6))
							{
								dragMode = i;
								return;
							}
						}
					}
				}

			}
			boolean changed = false;
			lastMousePosition = e.getPoint();
			JComponent selectedComponent = (JComponent)SwingUtilities.getDeepestComponentAt(realViewPort, e.getX(), e.getY());
			Container panel = SwingUtilities.getAncestorOfClass(ITabPanel.class, selectedComponent);
			if (panel == null) panel = SwingUtilities.getAncestorOfClass(ISplitPane.class, selectedComponent);
			while (panel != null)
			{
				if (SwingUtilities.isDescendingFrom(panel, realViewPort))
				{
					selectedComponent = (JComponent)panel;
					panel = SwingUtilities.getAncestorOfClass(ITabPanel.class, selectedComponent);
					if (panel == null) panel = SwingUtilities.getAncestorOfClass(ISplitPane.class, selectedComponent);
				}
				else
				{
					panel = null;
				}
			}
			if (selectedComponent == realViewPort || selectedComponent instanceof DataRenderer)
			{
				changed = selectedComponents.size() > 0;
				selectedComponents.clear();
			}
			else
			{
				while (selectedComponent.getParent() != realViewPort && !(selectedComponent.getParent() instanceof DataRenderer))
				{
					selectedComponent = (JComponent)selectedComponent.getParent();
				}
				if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK && selectedComponents.containsKey(selectedComponent))
				{
					changed = selectedComponents.remove(selectedComponent) != null;
					selectedComponent = null;
				}
				else
				{
					if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != InputEvent.CTRL_DOWN_MASK && !selectedComponents.containsKey(selectedComponent))
					{
						changed = selectedComponents.size() > 0;
						selectedComponents.clear();
					}
					changed = addSelectedComponent(selectedComponent, selectedComponent.getBounds()) || changed;
				}
			}
			if (changed)
			{
				Object ret = callback.executeOnSelect(getJSEvent(e, EventType.rightClick));
				if (selectedComponent != null && ret instanceof Boolean && !((Boolean)ret).booleanValue())
				{
					selectedComponents.remove(selectedComponent);
				}
			}
			repaint();
		}

		/**
		 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
		 */
		public void mouseReleased(MouseEvent e)
		{
			if (lastMousePosition != null && lassoPosition != null)
			{
				int x = Math.min(lassoPosition.x, lastMousePosition.x);
				int y = Math.min(lassoPosition.y, lastMousePosition.y);
				int height = Math.abs(lassoPosition.y - lastMousePosition.y);
				int width = Math.abs(lassoPosition.x - lastMousePosition.x);

				Rectangle rect = new Rectangle(x, y, width, height);


				List<JComponent> changed = calculateSelection(realViewPort, rect);
				if (changed.size() > 0)
				{
					Object ret = callback.executeOnSelect(getJSEvent(e, EventType.rightClick));
					if (ret instanceof Boolean && !((Boolean)ret).booleanValue())
					{
						Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelectionForChange(this);
						selectedComponents.keySet().removeAll(changed);
					}
				}
			}
			if (moved)
			{
				callback.executeOnDrop(getJSEvent(e, EventType.onDrop));
			}
			if (resized)
			{
				callback.executeOnResize(getJSEvent(e, EventType.onDrop));
			}
			dragMode = -1;
			lastMousePosition = null;
			lassoPosition = null;
			canMove = null;
			moved = false;
			canResize = null;
			resized = false;
			setCursor(Cursor.getDefaultCursor());
			repaint();
		}

		/**
		 * @param e
		 */
		private JSEvent getJSEvent(MouseEvent e, EventType type)
		{
			JSEvent event = new JSEvent();
			event.setFormName(controller.getName());
			event.setType(type);
			event.setModifiers(e.getModifiers());
			event.setLocation(e.getPoint());
			Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelectionForChange(this);
			event.setData(selectedComponents.keySet().toArray());
			//event.setSource(e)
			return event;
		}

		/**
		 * @param rect
		 */
		private List<JComponent> calculateSelection(JComponent parent, Rectangle rect)
		{
			List<JComponent> changed = new ArrayList<JComponent>();
			Component[] comps = parent.getComponents();
			for (Component component : comps)
			{
				if (component instanceof DataRenderer)
				{
					changed.addAll(calculateSelection((JComponent)component, rect));
				}
				else if (rect.contains(component.getBounds()))
				{
					if (addSelectedComponent(component, component.getBounds()))
					{
						changed.add((JComponent)component);
					}
				}
			}
			return changed;
		}

		/**
		 * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
		 */
		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelection(this);
			if (selectedComponents.size() > 0)
			{
				Iterator<Entry<JComponent, int[][]>> iterator = selectedComponents.entrySet().iterator();
				while (iterator.hasNext())
				{
					Entry<JComponent, int[][]> entry = iterator.next();
					JComponent selectedComponent = entry.getKey();
					int[][] positions = entry.getValue();
					g.setColor(Color.black);
					Rectangle bounds = selectedComponent.getBounds();
					g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

					for (int[] position : positions)
					{
						g.setColor(Color.black);
						g.fillRect(position[0] + 1, position[1] + 1, 4, 4);
						g.setColor(Color.white);
						g.drawRect(position[0], position[1], 5, 5);
					}
				}
			}
			if (lassoPosition != null && lastMousePosition != null)
			{
				g.setColor(Color.black);
				Stroke s = new BasicStroke(1.0f, // Width
					BasicStroke.CAP_SQUARE, // End cap
					BasicStroke.JOIN_ROUND, // Join style
					0.0f, // Miter limit
					new float[] { 3.0f, 3.0f }, // Dash pattern
					0.0f); // Dash phase
				((Graphics2D)g).setStroke(s);
				int x = Math.min(lassoPosition.x, lastMousePosition.x);
				int y = Math.min(lassoPosition.y, lastMousePosition.y);
				int height = Math.abs(lassoPosition.y - lastMousePosition.y);
				int width = Math.abs(lassoPosition.x - lastMousePosition.x);
				g.drawRect(x, y, width, height);
			}
		}

		/**
		 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
		 */
		public void mouseDragged(MouseEvent e)
		{
			Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelectionForChange(this);
			if (dragMode != -1 && selectedComponents.size() == 1)
			{
				if (!resized)
				{
					if (canResize == null)
					{
						canResize = callback.executeOnDrag(getJSEvent(e, EventType.onDrag));
					}
					if (canResize instanceof Boolean && !((Boolean)canResize).booleanValue()) return; //drag stopped
				}

				JComponent selectedComponent = selectedComponents.keySet().iterator().next();
				Rectangle current = selectedComponent.getBounds();
				Rectangle bounds = new Rectangle(current);
				switch (dragMode)
				{
					case 0 :
						bounds.x = e.getX();
						bounds.y = e.getY();
						bounds.width = current.width - (e.getX() - current.x);
						bounds.height = current.height - (e.getY() - current.y);
						break;
					case 1 :
						bounds.x = e.getX();
						bounds.width = current.width - (e.getX() - current.x);
						break;
					case 2 :
						bounds.x = e.getX();
						bounds.width = current.width - (e.getX() - current.x);
						bounds.height = e.getY() - current.y;
						break;
					case 3 :
						bounds.y = e.getY();
						bounds.height = current.height - (e.getY() - current.y);
						break;
					case 4 :
						bounds.height = e.getY() - current.y;
						break;
					case 5 :
						bounds.y = e.getY();
						bounds.width = e.getX() - current.x;
						bounds.height = current.height - (e.getY() - current.y);
						break;
					case 6 :
						bounds.width = e.getX() - current.x;
						break;
					case 7 :
						bounds.width = e.getX() - current.x;
						bounds.height = e.getY() - current.y;
				}
				if (bounds.x < 1) bounds.x = 1;
				if (bounds.y < 1) bounds.y = 1;
				if (bounds.height < 1) bounds.height = 4;
				if (bounds.width < 1) bounds.width = 4;

				resized = true;
				addSelectedComponent(selectedComponent, bounds);
			}
			else if (lastMousePosition != null)
			{
				if (selectedComponents.size() > 0)
				{
					if (e.getY() >= 0 && e.getX() >= 0)
					{
						if (!moved && canMove == null)
						{
							canMove = callback.executeOnDrag(getJSEvent(e, EventType.onDrag));
						}
						if (!(canMove instanceof Boolean && !((Boolean)canMove).booleanValue()))
						{
							Map<JComponent, Rectangle> toUpdate = new HashMap<JComponent, Rectangle>(selectedComponents.size());
							Iterator<JComponent> iterator = selectedComponents.keySet().iterator();
							int panelWidth = getWidth();
							int panelHeight = getHeight();
							while (iterator.hasNext())
							{
								JComponent selectedComponent = iterator.next();
								Rectangle current = selectedComponent.getBounds();
								int x = current.x + (e.getX() - lastMousePosition.x);
								int y = current.y + (e.getY() - lastMousePosition.y);
								if (x < 0 || y < 0 || (x + current.width) > panelWidth || (y + current.height) > panelHeight)
								{
									toUpdate.clear();
									break;
								}
								current.x = x;
								current.y = y;
								toUpdate.put(selectedComponent, current);
							}
							if (toUpdate.size() > 0)
							{
								Iterator<Entry<JComponent, Rectangle>> it = toUpdate.entrySet().iterator();
								while (it.hasNext())
								{
									Entry<JComponent, Rectangle> entry = it.next();
									addSelectedComponent(entry.getKey(), entry.getValue());
								}
								moved = true;
								setCursor(new Cursor(Cursor.MOVE_CURSOR));
							}
							else
							{
								setCursor(Cursor.getDefaultCursor());
							}
						}
					}
				}
				else
				{
					if (lassoPosition == null)
					{
						lassoPosition = lastMousePosition;
					}
				}

				lastMousePosition = e.getPoint();
			}
			repaint();
		}

		private boolean addSelectedComponent(Component component, Rectangle bounds)
		{
			Object clientdesign_handles = null;
			if (component instanceof IScriptBaseMethods)
			{
				IScriptBaseMethods sbmc = (IScriptBaseMethods)component;
				if (sbmc.js_getName() == null) return false; //skip, elements with no name are not usable in CD

				clientdesign_handles = sbmc.js_getClientProperty("clientdesign.handles");
				Object clientdesign_selectable = sbmc.js_getClientProperty("clientdesign.selectable");
				if (clientdesign_selectable != null && !Utils.getAsBoolean(clientdesign_selectable)) return false; //skip
			}
			Set<String> handles = null;
			if (clientdesign_handles instanceof Object[])
			{
				handles = new HashSet<String>();
				for (int i = 0; i < ((Object[])clientdesign_handles).length; i++)
				{
					String val = ((Object[])clientdesign_handles)[i].toString();
					handles.add(Utils.stringReplace(val, "'", ""));
				}
			}

			component.setBounds(bounds);

			int[][] positions = new int[8][2];
			if (handles == null || handles.contains("tl"))
			{
				positions[0][0] = bounds.x - 2;
				positions[0][1] = bounds.y - 2;
			}
			if (handles == null || handles.contains("l"))
			{
				positions[1][0] = bounds.x - 2;
				positions[1][1] = bounds.y + bounds.height / 2 - 3;
			}
			if (handles == null || handles.contains("bl"))
			{
				positions[2][0] = bounds.x - 2;
				positions[2][1] = bounds.y + bounds.height - 3;
			}
			if (handles == null || handles.contains("t"))
			{
				positions[3][0] = bounds.x + bounds.width / 2 - 3;
				positions[3][1] = bounds.y - 3;
			}
			if (handles == null || handles.contains("b"))
			{
				positions[4][0] = bounds.x + bounds.width / 2 - 3;
				positions[4][1] = bounds.y + bounds.height - 3;
			}
			if (handles == null || handles.contains("tr"))
			{
				positions[5][0] = bounds.x + bounds.width - 3;
				positions[5][1] = bounds.y - 2;
			}
			if (handles == null || handles.contains("r"))
			{
				positions[6][0] = bounds.x + bounds.width - 3;
				positions[6][1] = bounds.y + bounds.height / 2 - 3;
			}
			if (handles == null || handles.contains("br"))
			{
				positions[7][0] = bounds.x + bounds.width - 3;
				positions[7][1] = bounds.y + bounds.height - 3;
			}

			Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelectionForChange(this);
			return selectedComponents.put((JComponent)component, positions) == null;
		}

		public void mouseMoved(MouseEvent e)
		{
			Map<JComponent, int[][]> selectedComponents = selectionHandler.getSelection(this);
			if (selectedComponents.size() == 1)
			{
				int[][] positions = selectedComponents.values().iterator().next();
				int x = e.getX();
				int y = e.getY();
				for (int i = 0; i < positions.length; i++)
				{
					int[] position = positions[i];
					if (position[0] < x && x < (position[0] + 6))
					{
						if (position[1] < y && y < (position[1] + 6))
						{
							switch (i)
							{
								case 0 :
									setCursor(new Cursor(Cursor.SE_RESIZE_CURSOR));
									break;
								case 1 :
									setCursor(new Cursor(Cursor.E_RESIZE_CURSOR));
									break;
								case 2 :
									setCursor(new Cursor(Cursor.NE_RESIZE_CURSOR));
									break;
								case 3 :
									setCursor(new Cursor(Cursor.N_RESIZE_CURSOR));
									break;
								case 4 :
									setCursor(new Cursor(Cursor.S_RESIZE_CURSOR));
									break;
								case 5 :
									setCursor(new Cursor(Cursor.SW_RESIZE_CURSOR));
									break;
								case 6 :
									setCursor(new Cursor(Cursor.W_RESIZE_CURSOR));
									break;
								case 7 :
									setCursor(new Cursor(Cursor.NW_RESIZE_CURSOR));
									break;
							}
							return;
						}
					}
				}
			}
			setCursor(Cursor.getDefaultCursor());
		}

		public void mouseClicked(MouseEvent e)
		{
		}

		public void mouseEntered(MouseEvent e)
		{
		}

		public void mouseExited(MouseEvent e)
		{
		}


		/**
		 * @see javax.swing.event.AncestorListener#ancestorAdded(javax.swing.event.AncestorEvent)
		 */
		public void ancestorAdded(AncestorEvent event)
		{
		}


		/**
		 * @see javax.swing.event.AncestorListener#ancestorMoved(javax.swing.event.AncestorEvent)
		 */
		public void ancestorMoved(AncestorEvent event)
		{
		}


		/**
		 * @see javax.swing.event.AncestorListener#ancestorRemoved(javax.swing.event.AncestorEvent)
		 */
		public void ancestorRemoved(AncestorEvent event)
		{
			removeComponentListener(realViewPort);
		}

		private void removeComponentListener(Container comp)
		{
			Component[] components = comp.getComponents();
			for (Component component : components)
			{
				if (component instanceof IDisplay || component instanceof ScriptLabel || component instanceof ScriptButton)
				{
					component.removeComponentListener(componentListener);
				}
				else if (component instanceof Container)
				{
					removeComponentListener((Container)component);
				}
			}
		}
	}

	private static class SelectionHandler
	{
		private final Map<JComponent, int[][]> selectedComponents = new HashMap<JComponent, int[][]>();
		private DesignPanel selectedPanel = null;

		public Map<JComponent, int[][]> getSelection(DesignPanel panel)
		{
			if (selectedPanel == panel)
			{
				return selectedComponents;
			}
			return Collections.emptyMap();
		}

		public Map<JComponent, int[][]> getSelectionForChange(DesignPanel panel)
		{
			if (selectedPanel != panel && selectedComponents.size() > 0)
			{
				selectedComponents.clear();
				selectedPanel.repaint();
			}
			selectedPanel = panel;
			return selectedComponents;
		}
	}

}
