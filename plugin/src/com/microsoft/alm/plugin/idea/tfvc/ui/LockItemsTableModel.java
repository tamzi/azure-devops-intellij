// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.microsoft.alm.plugin.idea.tfvc.ui;

import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import com.microsoft.alm.plugin.external.commands.LockCommand;
import com.microsoft.alm.plugin.external.models.ExtendedItemInfo;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

public class LockItemsTableModel extends AbstractTableModel {

    public interface Listener extends EventListener {
        void selectionChanged();
    }

    enum Column {
        Selection("", 25) {
            public Boolean getValue(final ExtendedItemInfoWithSelection item) {
                return item.selected;
            }
        },
        Item(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_ITEM_COLUMN), 550) {
            public String getValue(final ExtendedItemInfoWithSelection item) {
                return item.info.getServerItem();
            }
        },
        Lock(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_LOCK_COLUMN), 110) {
            public String getValue(final ExtendedItemInfoWithSelection item) {
                final LockCommand.LockLevel level = LockCommand.LockLevel.fromString(item.info.getLock());
                switch (level) {
                    case CHECKIN:
                        return TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_LOCK_LEVEL_CHECKIN);
                    case CHECKOUT:
                        return TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_LOCK_LEVEL_CHECKOUT);
                }
                // "None" is just the empty string
                return StringUtils.EMPTY;
            }
        },
        LockOwner(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_LOCKED_BY_COLUMN), 130) {
            public String getValue(final ExtendedItemInfoWithSelection item) {
                return item.info.getLockOwner();
            }
        };

        private final String name;
        private final int width;

        Column(final String name, final int width) {
            this.name = name;
            this.width = JBUI.scale(width);
        }

        public String getName() {
            return name;
        }

        public int getWidth() {
            return width;
        }

        @Nullable
        public abstract Object getValue(final ExtendedItemInfoWithSelection item);
    }

    private final List<ExtendedItemInfoWithSelection> items;
    private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

    public LockItemsTableModel(@NotNull List<ExtendedItemInfo> items) {
        this.items = new ArrayList<>(items.size());
        for (ExtendedItemInfo item : items) {
            this.items.add(new ExtendedItemInfoWithSelection(item));
        }
        setInitialSelection();
    }

    /**
     * This method looks at the first item in the list and selects all items that are like it.
     * I.e. if the first item is not locked then select all items that are not locked
     * OR if the first item IS locked, select all items that are also locked.
     * This assures that the one of the Lock or Unlock buttons will be enabled.
     */
    private void setInitialSelection() {
        if (items != null && items.size() > 0) {
            final LockCommand.LockLevel firstItemLevel = LockCommand.LockLevel.fromString(items.get(0).info.getLock());
            final boolean selectIfNone = firstItemLevel == LockCommand.LockLevel.NONE;

            for (final ExtendedItemInfoWithSelection item : items) {
                final LockCommand.LockLevel currentLevel = LockCommand.LockLevel.fromString(item.info.getLock());
                if (currentLevel == LockCommand.LockLevel.NONE && selectIfNone) {
                    item.selected = true;
                } else if (currentLevel != LockCommand.LockLevel.NONE && !selectIfNone) {
                    item.selected = true;
                } else {
                    item.selected = false;
                }
            }
        }
    }

    public int getRowCount() {
        return items.size();
    }

    public int getColumnCount() {
        return Column.values().length;
    }

    public String getColumnName(final int column) {
        return Column.values()[column].getName();
    }

    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        return Column.values()[columnIndex] == Column.Selection;
    }

    @Nullable
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        return Column.values()[columnIndex].getValue(items.get(rowIndex));
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        if (Column.values()[columnIndex] == Column.Selection) {
            items.get(rowIndex).selected = (Boolean) aValue;
            myEventDispatcher.getMulticaster().selectionChanged();
        }
    }

    public List<ExtendedItemInfo> getSelectedItems() {
        final List<ExtendedItemInfo> result = new ArrayList<>();
        for (ExtendedItemInfoWithSelection item : items) {
            if (item.selected) {
                result.add(item.info);
            }
        }
        return result;
    }

    public void addListener(final Listener listener) {
        myEventDispatcher.addListener(listener);
    }

    public void removeListener(final Listener listener) {
        myEventDispatcher.removeListener(listener);
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
        if (columnIndex == Column.Selection.ordinal()) {
            return Boolean.class;
        } else {
            return super.getColumnClass(columnIndex);
        }
    }

    private static class ExtendedItemInfoWithSelection {
        public final ExtendedItemInfo info;
        public boolean selected;

        public ExtendedItemInfoWithSelection(ExtendedItemInfo info) {
            this.info = info;
        }
    }
}
