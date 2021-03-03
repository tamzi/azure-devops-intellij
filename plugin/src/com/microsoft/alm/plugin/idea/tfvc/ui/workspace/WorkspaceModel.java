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

package com.microsoft.alm.plugin.idea.tfvc.ui.workspace;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.microsoft.alm.common.utils.ArgumentHelper;
import com.microsoft.alm.plugin.context.RepositoryContext;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.context.ServerContextManager;
import com.microsoft.alm.plugin.external.models.Workspace;
import com.microsoft.alm.plugin.external.utils.CommandUtils;
import com.microsoft.alm.plugin.external.utils.WorkspaceHelper;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import com.microsoft.alm.plugin.idea.common.services.LocalizationServiceImpl;
import com.microsoft.alm.plugin.idea.common.ui.common.AbstractModel;
import com.microsoft.alm.plugin.idea.common.ui.common.ModelValidationInfo;
import com.microsoft.alm.plugin.idea.common.utils.IdeaHelper;
import com.microsoft.alm.plugin.idea.common.utils.VcsHelper;
import com.microsoft.alm.plugin.operations.OperationExecutor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.event.HyperlinkEvent;
import javax.ws.rs.NotAuthorizedException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkspaceModel extends AbstractModel {
    private final Logger logger = LoggerFactory.getLogger(WorkspaceModel.class);

    public static final String PROP_NAME = "name";
    public static final String PROP_COMPUTER = "computer";
    public static final String PROP_OWNER = "owner";
    public static final String PROP_COMMENT = "comment";
    public static final String PROP_SERVER = "server";
    public static final String PROP_MAPPINGS = "mappings";
    public static final String PROP_LOADING = "loading";
    public static final String PROP_LOCATION = "location";

    private boolean loading;
    private String name;
    private String computer;
    private String owner;
    private String comment;
    private String server;
    private List<Workspace.Mapping> mappings;
    private Workspace.Location location;

    private Workspace oldWorkspace;
    private ServerContext currentServerContext;

    public WorkspaceModel() {
    }

    public boolean isLoading() {
        return loading;
    }

    @VisibleForTesting
    protected void setLoading(final boolean loading) {
        this.loading = loading;
        setChangedAndNotify(PROP_LOADING);
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        if (!StringUtils.equals(this.name, name)) {
            this.name = name;
            super.setChangedAndNotify(PROP_NAME);
        }
    }

    public String getComputer() {
        return computer;
    }

    public void setComputer(final String computer) {
        if (!StringUtils.equals(this.computer, computer)) {
            this.computer = computer;
            super.setChangedAndNotify(PROP_COMPUTER);
        }
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        if (!StringUtils.equals(this.owner, owner)) {
            this.owner = owner;
            super.setChangedAndNotify(PROP_OWNER);
        }
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        if (!StringUtils.equals(this.comment, comment)) {
            this.comment = comment;
            super.setChangedAndNotify(PROP_COMMENT);
        }
    }

    public String getServer() {
        return server;
    }

    public void setServer(final String server) {
        if (!StringUtils.equals(this.server, server)) {
            this.server = server;
            super.setChangedAndNotify(PROP_SERVER);
        }
    }

    public List<Workspace.Mapping> getMappings() {
        if (mappings == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(mappings);
    }

    public void setMappings(@NotNull final List<Workspace.Mapping> mappings) {
        if (WorkspaceHelper.areMappingsDifferent(this.mappings, mappings)) {
            this.mappings = mappings;
            super.setChangedAndNotify(PROP_MAPPINGS);
        }
    }

    public Workspace.Location getLocation() {
        return location;
    }

    public void setLocation(final Workspace.Location location) {
        if (this.location != location) {
            this.location = location;
            super.setChangedAndNotify(PROP_LOCATION);
        }
    }

    public ModelValidationInfo validate() {
        if (StringUtils.isEmpty(getName())) {
            return ModelValidationInfo.createWithResource(PROP_NAME,
                    TfPluginBundle.KEY_WORKSPACE_DIALOG_ERRORS_NAME_EMPTY);
        }
        if (getMappings().size() == 0) {
            return ModelValidationInfo.createWithResource(PROP_MAPPINGS,
                    TfPluginBundle.KEY_WORKSPACE_DIALOG_ERRORS_MAPPINGS_EMPTY);
        }

        return ModelValidationInfo.NO_ERRORS;
    }

    public void loadWorkspace(final Project project) {
        logger.info("loadWorkspace starting");
        setLoading(true);
        // Load
        OperationExecutor.getInstance().submitOperationTask(() -> {
            try {
                logger.info("loadWorkspace: getting repository context");
                final RepositoryContext repositoryContext = VcsHelper.getRepositoryContext(project);
                if (repositoryContext == null || StringUtils.isEmpty(repositoryContext.getUrl()) || StringUtils.isEmpty(repositoryContext.getTeamProjectName())) {
                    logger.warn("loadWorkspace: Could not determine repositoryContext for project");
                    throw new RuntimeException(TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_ERRORS_CONTEXT_FAILED));
                }

                logger.info("loadWorkspace: getting server context");
                currentServerContext = ServerContextManager.getInstance().createContextFromTfvcServerUrl(
                        URI.create(repositoryContext.getUrl()),
                        repositoryContext.getTeamProjectName(),
                        true);
                if (currentServerContext == null) {
                    logger.warn("loadWorkspace: Could not get the context for the repository. User may have canceled.");
                    throw new NotAuthorizedException(TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_ERRORS_AUTH_FAILED, repositoryContext.getUrl()));
                }

                logger.info("loadWorkspace: getting workspace");
                loadWorkspaceInternal(CommandUtils.getDetailedWorkspace(currentServerContext, project));
            } finally {
                loadWorkspaceComplete();

            }
        });
    }

    public void loadWorkspace(final RepositoryContext repositoryContext, final String workspaceName) {
        logger.info("loadWorkspace starting");
        ArgumentHelper.checkNotNull(repositoryContext, "repositoryContext");
        ArgumentHelper.checkNotEmptyString(workspaceName, "workspaceName");

        setLoading(true);
        // Load
        OperationExecutor.getInstance().submitOperationTask(() -> {
            try {
                logger.info("loadWorkspace: getting server context");
                currentServerContext = ServerContextManager.getInstance().createContextFromTfvcServerUrl(
                        URI.create(repositoryContext.getUrl()),
                        repositoryContext.getTeamProjectName(),
                        true);
                if (currentServerContext == null) {
                    logger.warn("loadWorkspace: Could not get the context for the repository. User may have canceled.");
                    throw new NotAuthorizedException(TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_ERRORS_AUTH_FAILED, repositoryContext.getUrl()));
                }

                logger.info("loadWorkspace: getting workspace by name");
                loadWorkspaceInternal(CommandUtils.getWorkspace(currentServerContext, workspaceName));
            } finally {
                // Make sure to fire events only on the UI thread
                loadWorkspaceComplete();
            }
        });
    }

    public void loadWorkspace(final ServerContext serverContext, final Workspace workspace) {
        logger.info("loadWorkspace starting");
        ArgumentHelper.checkNotNull(serverContext, "serverContext");
        ArgumentHelper.checkNotNull(workspace, "workspace");

        setLoading(true);
        // Load
        OperationExecutor.getInstance().submitOperationTask(new Runnable() {
            @Override
            public void run() {
                logger.info("loadWorkspace: already have context so load workspace");
                currentServerContext = serverContext;
                loadWorkspaceInternal(workspace);
                // Make sure to fire events only on the UI thread
                loadWorkspaceComplete();
            }
        });
    }

    private void loadWorkspaceInternal(final Workspace workspace) {
        oldWorkspace = workspace;
        if (oldWorkspace != null) {
            logger.info("loadWorkspace: got workspace, setting fields");
            server = oldWorkspace.getServerDisplayName();
            owner = oldWorkspace.getOwner();
            computer = oldWorkspace.getComputer();
            name = oldWorkspace.getName();
            comment = oldWorkspace.getComment();
            mappings = new ArrayList<Workspace.Mapping>(oldWorkspace.getMappings());
            location = workspace.getLocation() == Workspace.Location.UNKNOWN ? Workspace.Location.LOCAL : workspace.getLocation();
        } else {
            // This shouldn't happen, so we will log this case, but not throw
            logger.warn("loadWorkspace: workspace was returned as null");
        }
    }

    private void loadWorkspaceComplete() {
        // Make sure to fire events only on the UI thread
        IdeaHelper.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                // Update all fields
                setChangedAndNotify(null);
                // Set loading to false
                setLoading(false);
                logger.info("loadWorkspace: done loading");
            }
        });
    }

    public void saveWorkspace(final Project project, final String workspaceRootPath, final boolean syncFiles, final Runnable onSuccess) {
        final ServerContext serverContext = currentServerContext;
        final Workspace oldWorkspace = this.oldWorkspace;
        final Workspace newWorkspace = new Workspace(server, name, computer, owner, comment, mappings);

        // Using IntelliJ's background framework here so the user can choose to wait or continue working
        final Task.Backgroundable backgroundTask = new Task.Backgroundable(project,
                TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_PROGRESS_TITLE),
                true, PerformInBackgroundOption.DEAF) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                saveWorkspaceInternal(serverContext, oldWorkspace, newWorkspace, indicator, project,
                        workspaceRootPath, syncFiles, onSuccess);
            }
        };

        backgroundTask.queue();
    }

    @VisibleForTesting
    protected void saveWorkspaceInternal(final ServerContext serverContext, final Workspace oldWorkspace,
                                         final Workspace newWorkspace, final ProgressIndicator indicator,
                                         final Project project, final String workspaceRootPath,
                                         final boolean syncFiles, final Runnable onSuccess) {
        try {
            IdeaHelper.setProgress(indicator, 0.10,
                    TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_SAVE_PROGRESS_UPDATING));

            // Update the workspace mappings and other properties
            CommandUtils.updateWorkspace(serverContext, oldWorkspace, newWorkspace);

            if (syncFiles) {
                IdeaHelper.setProgress(indicator, 0.30,
                        TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_SAVE_PROGRESS_SYNCING));
                CommandUtils.syncWorkspace(serverContext, workspaceRootPath);
            }

            IdeaHelper.setProgress(indicator, 1.00,
                    TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_SAVE_PROGRESS_DONE), true);

            if (onSuccess != null) {
                // Trigger the onSuccess callback on the UI thread (it is up to the success handler to notify the user)
                IdeaHelper.runOnUIThread(onSuccess, false, ModalityState.defaultModalityState());
            } else {
                // Notify the user of success and provide a link to sync the workspace
                // (It doesn't make sense to tell the user we are done here if there is another thread still doing work)
                VcsNotifier.getInstance(project).notifyImportantInfo(
                        TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_NOTIFY_SUCCESS_TITLE),
                        TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_NOTIFY_SUCCESS_MESSAGE),
                        new NotificationListener() {
                            @Override
                            public void hyperlinkUpdate(@NotNull final Notification n, @NotNull final HyperlinkEvent e) {
                                syncWorkspaceAsync(serverContext, project, workspaceRootPath);
                            }
                        });
            }
        } catch (final Throwable t) {
            //TODO on failure we could provide a link that reopened the dialog with the values they tried to save
            VcsNotifier.getInstance(project).notifyError(
                    TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_NOTIFY_FAILURE_TITLE),
                    LocalizationServiceImpl.getInstance().getExceptionMessage(t));
        }
    }

    public void syncWorkspaceAsync(final ServerContext context, final Project project, final String workspaceRootPath) {
        final Task.Backgroundable backgroundTask = new Task.Backgroundable(project,
                TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_PROGRESS_TITLE),
                true, PerformInBackgroundOption.DEAF) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                try {
                    IdeaHelper.setProgress(indicator, 0.30,
                            TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_SAVE_PROGRESS_SYNCING));

                    // Sync all files recursively
                    CommandUtils.syncWorkspace(context, workspaceRootPath);

                    // Notify the user of a successful sync
                    VcsNotifier.getInstance(project).notifySuccess(
                            TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_NOTIFY_SUCCESS_TITLE),
                            TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_NOTIFY_SUCCESS_SYNC_MESSAGE));
                } catch (final Throwable t) {
                    VcsNotifier.getInstance(project).notifyError(
                            TfPluginBundle.message(TfPluginBundle.KEY_WORKSPACE_DIALOG_NOTIFY_FAILURE_TITLE),
                            LocalizationServiceImpl.getInstance().getExceptionMessage(t));
                }

            }
        };
        backgroundTask.queue();
    }
}