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

import com.intellij.openapi.project.Project;
import com.microsoft.alm.plugin.external.commands.LockCommand;
import com.microsoft.alm.plugin.external.models.ExtendedItemInfo;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import com.microsoft.alm.plugin.idea.common.ui.common.BaseDialogImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;

public class LockItemsDialog extends BaseDialogImpl {
    public static final String PROP_ITEMS = "items";

    public static final int LOCK_EXIT_CODE = NEXT_USER_EXIT_CODE;
    public static final int UNLOCK_EXIT_CODE = NEXT_USER_EXIT_CODE + 1;

    private LockItemsForm form;

    // Button actions
    private Action lockAction;
    private Action unlockAction;

    public LockItemsDialog(Project project, List<ExtendedItemInfo> items) {
        super(project, TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_TITLE), null,
                TfPluginBundle.KEY_TFVC_LOCK_DIALOG_TITLE, true,
                Collections.<String, Object>singletonMap(PROP_ITEMS, items));


        updateControls();
    }

    @Nullable
    protected JComponent createCenterPanel() {
        form = new LockItemsForm((List<ExtendedItemInfo>) getProperty(PROP_ITEMS));
        form.addListener(new LockItemsTableModel.Listener() {
            public void selectionChanged() {
                updateControls();
            }
        });

        return form.getContentPane();
    }

    @NotNull
    protected Action[] createActions() {
        lockAction = new LockAction();
        unlockAction = new UnlockAction();
        return new Action[]{lockAction, unlockAction, getCancelAction()};
    }

    private void setLockActionEnabled(final boolean isEnabled) {
        if (lockAction != null) {
            lockAction.setEnabled(isEnabled);
            form.setRadioButtonsEnabled(isEnabled);
        }
    }

    private void setUnlockActionEnabled(final boolean isEnabled) {
        if (unlockAction != null) {
            unlockAction.setEnabled(isEnabled);
        }
    }

    private void updateControls() {
        List<ExtendedItemInfo> items = getSelectedItems();
        setLockActionEnabled(!items.isEmpty() && canAllBeLocked(items));
        setUnlockActionEnabled(!items.isEmpty() && canAllBeUnlocked(items));
    }

    private static boolean canAllBeLocked(List<ExtendedItemInfo> items) {
        for (ExtendedItemInfo item : items) {
            final LockCommand.LockLevel level = LockCommand.LockLevel.fromString(item.getLock());
            if (level != LockCommand.LockLevel.NONE) {
                return false;
            }
        }
        return true;
    }

    private static boolean canAllBeUnlocked(List<ExtendedItemInfo> items) {
        for (ExtendedItemInfo item : items) {
            final LockCommand.LockLevel level = LockCommand.LockLevel.fromString(item.getLock());
            if (level == LockCommand.LockLevel.NONE) {
                return false;
            }
        }
        return true;
    }

    public List<ExtendedItemInfo> getSelectedItems() {
        return form.getSelectedItems();
    }

    public LockCommand.LockLevel getLockLevel() {
        return form.getLockLevel();
    }

    public boolean getRecursive() {
        return form.getRecursive();
    }

    private class LockAction extends AbstractAction {
        public LockAction() {
            super(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_LOCK_BUTTON));
            putValue(DEFAULT_ACTION, Boolean.TRUE);
        }

        public void actionPerformed(ActionEvent e) {
            if (myPerformAction) return;
            try {
                myPerformAction = true;
                if (this.isEnabled()) {
                    close(LOCK_EXIT_CODE);
                }
            } finally {
                myPerformAction = false;
            }
        }
    }

    private class UnlockAction extends AbstractAction {
        public UnlockAction() {
            super(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_LOCK_DIALOG_UNLOCK_BUTTON));
        }

        public void actionPerformed(ActionEvent e) {
            if (myPerformAction) return;
            try {
                myPerformAction = true;
                if (this.isEnabled()) {
                    close(UNLOCK_EXIT_CODE);
                }
            } finally {
                myPerformAction = false;
            }
        }
    }
}
