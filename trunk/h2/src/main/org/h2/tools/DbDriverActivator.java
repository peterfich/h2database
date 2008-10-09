/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.tools;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The driver activator loads the H2 driver when starting the bundle.
 * The driver is unloaded when stopping the bundle.
 */
public class DbDriverActivator implements BundleActivator {

    public void start(BundleContext bundleContext) {
        org.h2.Driver.load();
    }

    public void stop(BundleContext bundleContext) {
        org.h2.Driver.unload();
    }

}
