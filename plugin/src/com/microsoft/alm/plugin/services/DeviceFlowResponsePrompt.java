// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.services;

import com.microsoft.alm.auth.oauth.DeviceFlowResponse;
import com.microsoft.alm.helpers.Action;

public interface DeviceFlowResponsePrompt {

    /**
     * Get a callback for the device flow to display the relevant information to end users
     *
     * @param cancellationCallback
     *      This is the action listener on the cancel button -- if user cancels the dialog,
     *      we should give control back to user
     *
     * @return a device flow response callback action
     */
    Action<DeviceFlowResponse> getCallback(final Action<String> cancellationCallback);

}
