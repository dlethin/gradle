/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.launcher.daemon.client;

import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.server.DaemonIdleTimeout;

import java.io.File;

/**
 * Takes care of instantiating and wiring together the services required by the daemon client.
 */
public class DaemonClientServices extends DaemonClientServicesSupport {

    private final File userHomeDir;
    private final int idleTimeout;
    private final ServiceRegistry registryServices;

    public DaemonClientServices(ServiceRegistry loggingServices, File userHomeDir) {
        this(loggingServices, userHomeDir, DaemonIdleTimeout.DEFAULT_IDLE_TIMEOUT);
    }

    public DaemonClientServices(ServiceRegistry loggingServices, File userHomeDir, int idleTimeout) {
        super(loggingServices);
        this.userHomeDir = userHomeDir;
        this.idleTimeout = idleTimeout;
        this.registryServices = new DaemonRegistryServices(userHomeDir);
        add(registryServices);
    }

    // here to satisfy DaemonClientServicesSupport contract
    protected DaemonRegistry createDaemonRegistry() {
        return registryServices.get(DaemonRegistry.class);
    }

    public Runnable makeDaemonStarter() {
        return new DaemonStarter(userHomeDir, idleTimeout);
    }
}
