/*
 * Copyright 2014-2015 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kotcrab.vis.ui.widget.tabbedpane;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.IdentityMap.Entry;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.i18n.BundleText;
import com.kotcrab.vis.ui.layout.DragPane;
import com.kotcrab.vis.ui.util.dialog.DialogUtils;
import com.kotcrab.vis.ui.util.dialog.DialogUtils.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.widget.Draggable;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageButton.VisImageButtonStyle;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextButton.VisTextButtonStyle;

/**
 * A tabbed pane, allows to have multiple tabs open and switch between them. TabbedPane does not handle displaying tab content,
 * you have to do that manually using tabbed pane listener to get tab content table (see {@link Tab#getContentTable()} and
 * {@link TabbedPaneListener}). All tabs must extend {@link Tab} class.
 * <p>
 * Since 0.9.3, tabbed pane uses an internal {@link DragPane} to make the tabs draggable. You can completely turn off this
 * functionality by setting {@link TabbedPaneStyle#draggable} to false. To turn off the drag listener at runtime, use
 * {@link #getTabsPane()} method to get a reference of {@link DragPane}, and invoke {@link DragPane#setDraggable(Draggable)} with
 * null argument - this will clear draggable listener from all tabs' buttons; naturally, setting this value to non-null
 * {@link Draggable} listener will also add it to all buttons.
 * @author Kotcrab
 * @author MJ
 * @since 0.7.0
 */
public class TabbedPane {
	private TabbedPaneStyle style;
	private VisImageButtonStyle sharedCloseActiveButtonStyle;

	private DragPane tabsPane;
	private VisTable mainTable;

	private Array<Tab> tabs;
	private IdentityMap<Tab, TabButtonTable> tabsButtonMap;
	private ButtonGroup<Button> group;

	private Tab activeTab;

	private Array<TabbedPaneListener> listeners;
	private boolean allowTabDeselect;

	public TabbedPane () {
		this(VisUI.getSkin().get(TabbedPaneStyle.class));
	}

	public TabbedPane (String styleName) {
		this(VisUI.getSkin().get(styleName, TabbedPaneStyle.class));
	}

	public TabbedPane (TabbedPaneStyle style) {
		this.style = style;
		listeners = new Array<TabbedPaneListener>();

		sharedCloseActiveButtonStyle = VisUI.getSkin().get("close-active-tab", VisImageButtonStyle.class);

		group = new ButtonGroup<Button>();

		mainTable = new VisTable();
		tabsPane = new DragPane(style.vertical); // TODO Use flow layout.
		configureDragPane(style);

		mainTable.setBackground(style.background);

		tabs = new Array<Tab>();
		tabsButtonMap = new IdentityMap<Tab, TabButtonTable>();

		mainTable.add(tabsPane);

		// if height is not set bottomBar may sometimes disappear
		if (style.bottomBar != null) {
			if (style.vertical) {
				// TODO Separate image. One that actually looks good.
				mainTable.add(new Image(style.bottomBar)).expand().fill().width(style.bottomBar.getMinWidth());
			} else {
				mainTable.getCell(tabsPane).left().expandX();
				mainTable.row();
				mainTable.add(new Image(style.bottomBar)).expand().fill().height(style.bottomBar.getMinHeight());
			}
		} else {
			//make sure that tab will fill available space even when there is not bottomBar image set
			mainTable.add().expand().fill();
		}
	}

	private void configureDragPane (TabbedPaneStyle style) {
		tabsPane.setTouchable(Touchable.childrenOnly);
		tabsPane.setListener(new DragPane.DragPaneListener.AcceptOwnChildren());
		if (style.draggable) {
			Draggable draggable = new Draggable();
			draggable.setInvisibleWhenDragged(true);
			draggable.setKeepWithinParent(true);
			draggable.setBlockInput(true);
			draggable.setFadingTime(0f);
			tabsPane.setDraggable(draggable);
		}
	}

	/** @return a direct reference to internal {@link DragPane}. Allows to manage {@link Draggable} settings. */
	public DragPane getTabsPane () {
		return tabsPane;
	}

	/**
	 * @param allowTabDeselect if true user may deselect tab, meaning that there won't be any active tab. Allows to create similar
	 * behaviour like in Intellij IDEA bottom quick access bar
	 */
	public void setAllowTabDeselect (boolean allowTabDeselect) {
		this.allowTabDeselect = allowTabDeselect;
		if (allowTabDeselect) {
			group.setMinCheckCount(0);
		} else {
			group.setMinCheckCount(1);
		}
	}

	public boolean isAllowTabDeselect () {
		return allowTabDeselect;
	}

	public void add (Tab tab) {
		tab.setPane(this);
		tabs.add(tab);

		addTab(tab, tabsPane.getChildren().size);
		switchTab(tab);
	}

	public void insert (int index, Tab tab) {
		tab.setPane(this);
		tabs.insert(index, tab);
		addTab(tab, index);
	}

	/**
	 * @param tab will be added in the selected index.
	 * @param index index of the tab, starting from zero.
	 */
	protected void addTab (Tab tab, int index) {
		TabButtonTable buttonTable = tabsButtonMap.get(tab);
		if (buttonTable == null) {
			buttonTable = new TabButtonTable(tab);
			tabsButtonMap.put(tab, buttonTable);
		}

		buttonTable.setTouchable(Touchable.enabled);
		if (index >= tabsPane.getChildren().size) {
			tabsPane.addActor(buttonTable);
		} else {
			tabsPane.addActorAt(index, buttonTable);
		}
		group.add(buttonTable.button);

		if (tabs.size == 1 && activeTab != null) {
			buttonTable.select();
			notifyListenersSwitched(tab);
		} else if (tab == activeTab) {
			buttonTable.select(); // maintains current previous tab while rebuilding
		}
	}

	/**
	 * Disables or enables given tab.
	 * <p>
	 * When disabling, if tab is currently selected, TabbedPane will switch to first available enabled Tab. If there is no any
	 * other enabled Tab, listener {@link TabbedPaneListener#switchedTab(Tab)} with null Tab will be called.
	 * <p>
	 * When enabling Tab and there isn't any others Tab enabled and {@link #setAllowTabDeselect(boolean)} was set to false, passed
	 * Tab will be selected. If {@link #setAllowTabDeselect(boolean)} is set to true nothing will be selected, all tabs will remain
	 * unselected.
	 * @param tab tab to change its state
	 * @param disable controls whether to disable or enable this tab
	 * @throws IllegalArgumentException if tab does not belong to this TabbedPane
	 */
	public void disableTab (Tab tab, boolean disable) {
		checkIfTabsBelongsToThisPane(tab);

		TabButtonTable buttonTable = tabsButtonMap.get(tab);
		buttonTable.button.setDisabled(disable);

		if (activeTab == tab && disable) {
			if (selectFirstEnabledTab()) {
				return;
			}

			// there isn't any tab we can switch to
			activeTab = null;
			notifyListenersSwitched(null);
		}

		if (activeTab == null && allowTabDeselect == false) {
			selectFirstEnabledTab();
		}
	}

	public boolean isTabDisabled (Tab tab) {
		TabButtonTable table = tabsButtonMap.get(tab);
		if (table == null) {
			throwNotBelongingTabException(tab);
		}
		return table.button.isDisabled();
	}

	private boolean selectFirstEnabledTab () {
		for (Entry<Tab, TabButtonTable> entry : tabsButtonMap) {
			if (entry.value.button.isDisabled() == false) {
				switchTab(entry.key);
				return true;
			}
		}

		return false;
	}

	private void checkIfTabsBelongsToThisPane (Tab tab) {
		if (tabs.contains(tab, true) == false) {
			throwNotBelongingTabException(tab);
		}
	}

	protected void throwNotBelongingTabException (Tab tab) {
		throw new IllegalArgumentException("Tab '" + tab.getTabTitle() + "' does not belong to this TabbedPane");
	}

	/**
	 * Removes tab from pane, if tab is dirty this won't cause to display "Unsaved changes" dialog!
	 * @param tab to be removed
	 * @return true if tab was removed, false if that tab wasn't added to this pane
	 */
	public boolean remove (Tab tab) {
		return remove(tab, true);
	}

	/**
	 * Removes tab from pane, if tab is dirty and 'ignoreTabDirty == false' this will cause to display "Unsaved changes" dialog!
	 * @return true if tab was removed, false if that tab wasn't added to this pane or "Unsaved changes" dialog was started
	 */
	public boolean remove (final Tab tab, boolean ignoreTabDirty) {
		checkIfTabsBelongsToThisPane(tab);

		if (ignoreTabDirty) {
			return removeTab(tab);
		}

		if (tab.isDirty() && mainTable.getStage() != null) {
			DialogUtils.showOptionDialog(mainTable.getStage(), Text.UNSAVED_DIALOG_TITLE.get(), Text.UNSAVED_DIALOG_TEXT.get(),
					OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter() {
						@Override
						public void yes () {
							tab.save();
							removeTab(tab);
						}

						@Override
						public void no () {
							removeTab(tab);
						}
					});
		} else {
			return removeTab(tab);
		}

		return false;
	}

	private boolean removeTab (Tab tab) {
		int index = tabs.indexOf(tab, true);
		boolean success = tabs.removeValue(tab, true);

		if (success) {
			TabButtonTable buttonTable = tabsButtonMap.get(tab);
			tabsPane.removeActor(buttonTable, true);
			tabsPane.invalidateHierarchy();
			tabsButtonMap.remove(tab);

			tab.setPane(null);
			tab.onHide();
			tab.dispose();
			notifyListenersRemoved(tab);

			if (tabs.size == 0) {
				// all tabs were removed so notify listener
				notifyListenersRemovedAll();
			} else if (activeTab == tab) {
				if (index > 0) {
					// switch to previous tab
					switchTab(--index);
				} else {
					// Switching to the next tab, currently having our removed tab index.
					switchTab(index);
				}
			}
		}

		return success;
	}

	/** Removes all tabs, ignores if tab is dirty */
	public void removeAll () {
		for (Tab tab : tabs) {
			tab.setPane(null);
			tab.onHide();
			tab.dispose();
		}

		tabs.clear();
		tabsButtonMap.clear();
		tabsPane.clear();

		notifyListenersRemovedAll();
	}

	public void switchTab (int index) {
		tabsButtonMap.get(tabs.get(index)).select();
	}

	public void switchTab (Tab tab) {
		TabButtonTable table = tabsButtonMap.get(tab);
		if (table == null) {
			throwNotBelongingTabException(tab);
		}
		table.select();
	}

	/**
	 * Must be called when you want to update tab title. If tab is dirty an '*' is added before title. This is called automatically
	 * if using {@link Tab#setDirty(boolean)}
	 * @param tab that title will be updated
	 */
	public void updateTabTitle (Tab tab) {
		TabButtonTable table = tabsButtonMap.get(tab);
		if (table == null) {
			throwNotBelongingTabException(tab);
		}
		table.button.setText(getTabTitle(tab));
	}

	protected String getTabTitle (Tab tab) {
		return tab.isDirty() ? "*" + tab.getTabTitle() : tab.getTabTitle();
	}

	public Table getTable () {
		return mainTable;
	}

	/** @return active tab or null if no tab is selected. */
	public Tab getActiveTab () {
		return activeTab;
	}

	public void addListener (TabbedPaneListener listener) {
		listeners.add(listener);
	}

	public boolean removeListener (TabbedPaneListener listener) {
		return listeners.removeValue(listener, true);
	}

	private void notifyListenersSwitched (Tab tab) {
		for (TabbedPaneListener listener : listeners) {
			listener.switchedTab(tab);
		}
	}

	private void notifyListenersRemoved (Tab tab) {
		for (TabbedPaneListener listener : listeners) {
			listener.removedTab(tab);
		}
	}

	private void notifyListenersRemovedAll () {
		for (TabbedPaneListener listener : listeners) {
			listener.removedAllTabs();
		}
	}

	public Array<Tab> getTabs () {
		return tabs;
	}

	public static class TabbedPaneStyle {
		public Drawable bottomBar;
		public Drawable background;
		public VisTextButtonStyle buttonStyle;
		/** Optional, defaults to false. */
		public boolean vertical;
		/** Optional, defaults to true. */
		public boolean draggable = true;

		public TabbedPaneStyle () {
		}

		public TabbedPaneStyle (TabbedPaneStyle other) {
			this.bottomBar = other.bottomBar;
			this.background = other.background;
			this.buttonStyle = other.buttonStyle;
			this.vertical = other.vertical;
			this.draggable = other.draggable;
		}

		public TabbedPaneStyle (Drawable background, Drawable bottomBar, VisTextButtonStyle buttonStyle) {
			this.background = background;
			this.bottomBar = bottomBar;
			this.buttonStyle = buttonStyle;
		}

		public TabbedPaneStyle (Drawable bottomBar, Drawable background, VisTextButtonStyle buttonStyle, boolean vertical, boolean draggable) {
			this.bottomBar = bottomBar;
			this.background = background;
			this.buttonStyle = buttonStyle;
			this.vertical = vertical;
			this.draggable = draggable;
		}
	}

	private class TabButtonTable extends VisTable {
		public VisTextButton button;
		public VisImageButton closeButton;
		private Tab tab;

		private VisTextButtonStyle buttonStyle;
		private VisImageButtonStyle closeButtonStyle;
		private Drawable up;

		public TabButtonTable (Tab tab) {
			this.tab = tab;
			button = new VisTextButton(getTabTitle(tab), style.buttonStyle) {
				@Override
				public void setDisabled (boolean isDisabled) {
					super.setDisabled(isDisabled);
					closeButton.setDisabled(isDisabled);
					deselect();
				}
			};
			button.setFocusBorderEnabled(false);
			button.setProgrammaticChangeEvents(false);

			closeButtonStyle = new VisImageButtonStyle(VisUI.getSkin().get("close", VisImageButtonStyle.class));

			closeButton = new VisImageButton(closeButtonStyle);
			closeButton.setGenerateDisabledImage(true);
			closeButton.getImage().setScaling(Scaling.fill);
			closeButton.getImage().setColor(Color.RED);

			addListeners();

			buttonStyle = new VisTextButtonStyle((VisTextButtonStyle) button.getStyle());
			button.setStyle(buttonStyle);
			closeButtonStyle = closeButton.getStyle();
			up = buttonStyle.up;

			add(button);
			if (tab.isCloseableByUser()) {
				add(closeButton).size(14, button.getHeight());
			}
		}

		private void addListeners () {
			closeButton.addListener(new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					closeTabAsUser();
				}
			});

			button.addListener(new InputListener() {
				private boolean isDown;

				@Override
				public boolean touchDown (InputEvent event, float x, float y, int pointer, int mouseButton) {
					if (button.isDisabled()) {
						return false;
					}

					isDown = true;
					if (UIUtils.left()) {
						setDraggedUpImage();
					}

					if (mouseButton == Buttons.MIDDLE) {
						closeTabAsUser();
					}

					return true;
				}

				@Override
				public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
					setDefaultUpImage();
					isDown = false;
				}

				@Override
				public boolean mouseMoved (InputEvent event, float x, float y) {
					if (!button.isDisabled() && activeTab != tab) {
						setCloseButtonOnMouseMove();
					}
					return false;
				}

				@Override
				public void exit (InputEvent event, float x, float y, int pointer, Actor toActor) {
					if (!button.isDisabled() && !isDown && activeTab != tab) {
						setDefaultUpImage();
					}
				}

				@Override
				public void enter (InputEvent event, float x, float y, int pointer, Actor fromActor) {
					if (!button.isDisabled() && activeTab != tab && Gdx.input.justTouched() == false) {
						setCloseButtonOnMouseMove();
					}
				}

				private void setCloseButtonOnMouseMove () {
					if (UIUtils.left()) {
						closeButtonStyle.up = buttonStyle.down;
					} else {
						closeButtonStyle.up = buttonStyle.over;
					}
				}

				private void setDraggedUpImage () {
					closeButtonStyle.up = buttonStyle.down;
					buttonStyle.up = buttonStyle.down;
				}

				private void setDefaultUpImage () {
					closeButtonStyle.up = up;
					buttonStyle.up = up;
				}
			});

			button.addListener(new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					switchToNewTab();
				}
			});
		}

		private void switchToNewTab () {
			// there was some previous tab, deselect it
			if (activeTab != null && activeTab != tab) {
				TabButtonTable table = tabsButtonMap.get(activeTab);
				// table may no longer exists if tab was removed, no big deal since this only changes
				// button style, tab.onHide() will be already called by remove() method
				if (table != null) {
					table.deselect();
					activeTab.onHide();
				}
			}

			if (button.isChecked() && tab != activeTab) { // switch to new tab
				activeTab = tab;
				notifyListenersSwitched(tab);
				tab.onShow();
				closeButton.setStyle(sharedCloseActiveButtonStyle);
			} else if (group.getCheckedIndex() == -1) { // no tab selected (allowTabDeselect == true)
				activeTab = null;
				notifyListenersSwitched(null);
			}

		}

		/** Closes tab, does nothing if Tab is not closeable by user */
		private void closeTabAsUser () {
			if (tab.isCloseableByUser()) {
				TabbedPane.this.remove(tab, false);
			}
		}

		public void select () {
			button.setChecked(true);
			switchToNewTab();
		}

		public void deselect () {
			closeButton.setStyle(closeButtonStyle);
		}
	}

	private enum Text implements BundleText {
		UNSAVED_DIALOG_TITLE("unsavedDialogTitle"), UNSAVED_DIALOG_TEXT("unsavedDialogText");

		private final String name;

		Text (final String name) {
			this.name = name;
		}

		private static I18NBundle getBundle () {
			return VisUI.getTabbedPaneBundle();
		}

		@Override
		public final String getName () {
			return name;
		}

		@Override
		public final String get () {
			return getBundle().get(name);
		}

		@Override
		public final String format () {
			return getBundle().format(name);
		}

		@Override
		public final String format (final Object... arguments) {
			return getBundle().format(name, arguments);
		}

		@Override
		public final String toString () {
			return get();
		}
	}
}
