// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.versioncontrol.path;

import com.microsoft.alm.plugin.exceptions.ServerPathFormatException;
import org.junit.Test;

public class ServerPathTests {
    @Test(expected = ServerPathFormatException.class)
    public void testCanonicalizeWithDollarValidation() {
        ServerPath.canonicalize("$/test/$path", true);
    }

    @Test
    public void testCanonicalizeWithoutDollarValidation() {
        ServerPath.canonicalize("$/test/$path", false); // should not throw
    }
}
