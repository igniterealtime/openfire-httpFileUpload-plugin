/*
 * Copyright (c) 2017-2023 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugins.httpfileupload;

import nl.goodbytes.xmpp.xep0363.*;
import nl.goodbytes.xmpp.xep0363.clamav.ClamavMalwareScanner;
import nl.goodbytes.xmpp.xep0363.repository.DirectoryRepository;
import nl.goodbytes.xmpp.xep0363.repository.TempDirectoryRepository;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by guus on 18-11-17.
 */
public class HttpFileUploadPlugin implements Plugin, PropertyEventListener
{
    private static final Logger Log = LoggerFactory.getLogger( HttpFileUploadPlugin.class );

    /**
     * Controls the scheme that is used in URLs advertised for file uploads/downloads. This property ignored if XML
     * property 'plugin.httpfileupload.serverspecific.announcedWebProtocol' is defined.
     *
     * Unlike this (database-based) property, the XML property can be assigned different values on each server, which
     * can come in handy in an Openfire cluster.
     */
    private static final SystemProperty<String> ANNOUNCED_WEB_PROTOCOL = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.announcedWebProtocol")
        .setDefaultValue("https")
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyWebProtocolConfiguration())
        .build();

    /**
     * Controls the server address that is used in URLs advertised for file uploads/downloads. This property ignored if
     * XML property 'plugin.httpfileupload.serverspecific.announcedWebHost' is defined.
     *
     * Unlike this (database-based) property, the XML property can be assigned different values on each server, which
     * can come in handy in an Openfire cluster.
     */
    private static final SystemProperty<String> ANNOUNCED_WEB_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.announcedWebHost")
        .setDefaultValue(XMPPServer.getInstance().getServerInfo().getHostname())
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyWebHostConfiguration())
        .build();

    /**
     * Controls the TCP port that is used in URLs advertised for file uploads/downloads. This property ignored if XML
     * property 'plugin.httpfileupload.serverspecific.announcedWebPort' is defined.
     *
     * Unlike this (database-based) property, the XML property can be assigned different values on each server, which
     * can come in handy in an Openfire cluster.
     */
    private static final SystemProperty<Integer> ANNOUNCED_WEB_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("plugin.httpfileupload.announcedWebPort")
        .setDefaultValue(HttpBindManager.HTTP_BIND_SECURE_PORT.getValue())
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyWebPortConfiguration())
        .build();

    /**
     * Controls the context root that is used in URLs advertised for file uploads/downloads. This property ignored if
     * XML property 'plugin.httpfileupload.serverspecific.announcedWebContextRoot' is defined.
     *
     * Unlike this (database-based) property, the XML property can be assigned different values on each server, which
     * can come in handy in an Openfire cluster.
     */
    private static final SystemProperty<String> ANNOUNCED_WEB_CONTEXT_ROOT = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.announcedWebContextRoot")
        .setDefaultValue("/httpfileupload")
        .setDynamic(false)
        .setPlugin("HTTP File Upload")
        .build();

    /**
     * Defines the maximum size (in bytes) of files that can be uploaded. This property ignored if XML property
     * 'plugin.httpfileupload.serverspecific.maxFileSize' is defined.
     *
     * Unlike this (database-based) property, the XML property can be assigned different values on each server, which
     * can come in handy in an Openfire cluster.
     */
    private static final SystemProperty<Long> MAX_FILE_SIZE = SystemProperty.Builder.ofType(Long.class)
        .setKey("plugin.httpfileupload.maxFileSize")
        .setDefaultValue(SlotManager.DEFAULT_MAX_FILE_SIZE)
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyMaxFileSizeConfiguration())
        .build();

    /**
     * Defines the file system path (directory) in which data is stored on the server. If the path is absent, or
     * invalid, a temporary directory will be used. This property ignored if XML property
     * 'plugin.httpfileupload.serverspecific.fileRepo' is defined.
     *
     * Unlike this (database-based) property, the XML property can be assigned different values on each server, which
     * can come in handy in an Openfire cluster.
     */
    private static final SystemProperty<String> FILE_REPO = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.fileRepo")
        .setDynamic(false)
        .setPlugin("HTTP File Upload")
        .build();


    /**
     * Controls if integration with an external (third-party) ClamAV malware scanner is enabled.
     */
    private static final SystemProperty<Boolean> CLAMAV_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("plugin.httpfileupload.clamavEnabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyClamAvConfiguration())
        .build();

    /**
     * Controls the host that is used to integrate with an external (third-party) ClamAV malware scanner daemon.
     */
    private static final SystemProperty<String> CLAMAV_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.clamavHost")
        .setDefaultValue("127.0.0.1")
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyClamAvConfiguration())
        .build();

    /**
     * Controls the port that is used to integrate with an external (third-party) ClamAV malware scanner daemon.
     */
    private static final SystemProperty<Integer> CLAMAV_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("plugin.httpfileupload.clamavPort")
        .setDefaultValue(3310)
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyClamAvConfiguration())
        .build();

    /**
     * Controls the connection timeout that is used when integrating with an external (third-party) ClamAV malware
     * scanner daemon.
     */
    private static final SystemProperty<Duration> CLAMAV_CONNECTION_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("plugin.httpfileupload.clamavConnectionTimeout")
        .setDefaultValue(Duration.ofSeconds(3))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> applyClamAvConfiguration())
        .build();

    private Component component;
    private WebAppContext context;

    /**
     * Returns the scheme that is used in URLs advertised for file uploads/downloads.
     *
     * This method will first attempt to read the configuration from the XML property
     * 'plugin.httpfileupload.serverspecific.announcedWebProtocol'. If that is not defined, then the value of the
     * (database) property 'plugin.httpfileupload.announcedWebProtocol' is used. When that's not defined either, then a
     * hard-coded default is used.
     *
     * Note that in a cluster of Openfire, the XML property values can be different on each server, unlike regular
     * properties that are based on database values.
     *
     * @return a scheme
     */
    public static String getWebProtocolFromProperties() {
        return JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.announcedWebProtocol", ANNOUNCED_WEB_PROTOCOL.getValue());
    }

    /**
     * Returns the server address that is used in URLs advertised for file uploads/downloads.
     *
     * This method will first attempt to read the configuration from the XML property
     * 'plugin.httpfileupload.serverspecific.announcedWebHost'. If that is not defined, then the value of the
     * (database) property 'plugin.httpfileupload.announcedWebHost' is used. When that's not defined either, then a
     * hard-coded default is used.
     *
     * Note that in a cluster of Openfire, the XML property values can be different on each server, unlike regular
     * properties that are based on database values.
     *
     * @return A host name
     */
    public static String getWebHostFromProperties() {
        return JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.announcedWebHost", ANNOUNCED_WEB_HOST.getValue());
    }

    /**
     * Returns the TCP port that is used in URLs advertised for file uploads/downloads.
     *
     * This method will first attempt to read the configuration from the XML property
     * 'plugin.httpfileupload.serverspecific.announcedWebPort'. If that is not defined, then the value of the
     * (database) property 'plugin.httpfileupload.announcedWebPort' is used. When that's not defined either, then a
     * hard-coded default is used.
     *
     * Note that in a cluster of Openfire, the XML property values can be different on each server, unlike regular
     * properties that are based on database values.
     *
     * @return a TCP port
     */
    public static int getWebPortFromProperties() {
        final String xmlProperty = JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.announcedWebPort");
        final String dbProperty = JiveGlobals.getProperty(ANNOUNCED_WEB_PORT.getKey());
        if (xmlProperty == null && dbProperty == null) {
            // Issue #37: Use a port that matches the scheme used, if no value is set explicitly.
            return getWebProtocolFromProperties().equalsIgnoreCase("http") ? HttpBindManager.HTTP_BIND_PORT.getValue() : ANNOUNCED_WEB_PORT.getDefaultValue();
        }
        return JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.announcedWebPort", ANNOUNCED_WEB_PORT.getValue());
    }

    /**
     * Returns the context root that is used in URLs advertised for file uploads/downloads.
     *
     * This method will first attempt to read the configuration from the XML property
     * 'plugin.httpfileupload.serverspecific.announcedWebContextRoot'. If that is not defined, then the value of the
     * (database) property 'plugin.httpfileupload.announcedWebContextRoot' is used. When that's not defined either, then
     * a hard-coded default is used.
     *
     * Note that in a cluster of Openfire, the XML property values can be different on each server, unlike regular
     * properties that are based on database values.
     *
     * @return context root
     */
    public static String getWebContextRootFromProperties() {
        return JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.announcedWebContextRoot", ANNOUNCED_WEB_CONTEXT_ROOT.getValue());
    }

    /**
     * Returns the maximum size (in bytes) of files that can be uploaded.
     *
     * This method will first attempt to read the configuration from the XML property
     * 'plugin.httpfileupload.serverspecific.maxFileSize'. If that is not defined, then the value of the
     * (database) property 'plugin.httpfileupload.maxFileSize' is used. When that's not defined either, then a
     * hard-coded default is used.
     *
     * Note that in a cluster of Openfire, the XML property values can be different on each server, unlike regular
     * properties that are based on database values.
     *
     * @return size in bytes
     */
    public static long getMaxFileSizeProperties() {
        final String xmlProperty = JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.maxFileSize");
        if (xmlProperty != null) {
            try {
                return Long.parseLong(xmlProperty);
            } catch (NumberFormatException ex) {
                Log.debug("Ignoring invalid long value for property {}: {}", "plugin.httpfileupload.serverspecific.maxFileSize", xmlProperty, ex);
            }
        }
        return MAX_FILE_SIZE.getValue();
    }

    /**
     * Defines the file system path (directory) in which data is stored on the server. If the path is absent, or
     * invalid, a temporary directory will be used.
     *
     * This method will first attempt to read the configuration from the XML property
     * 'plugin.httpfileupload.serverspecific.fileRepo'. If that is not defined, then the value of the
     * (database) property 'plugin.httpfileupload.fileRepo' is used. When that's not defined either, then a
     * hard-coded default is used.
     *
     * Note that in a cluster of Openfire, the XML property values can be different on each server, unlike regular
     * properties that are based on database values.
     *
     * @return file system path
     */
    public static String getFileRepoFromProperties() {
        return JiveGlobals.getXMLProperty("plugin.httpfileupload.serverspecific.fileRepo", FILE_REPO.getValue());
    }

    /**
     * Re-evaluates property values and applies any change to affects the web protocol to be used when announcing
     * endpoint URLs.
     */
    public static void applyWebProtocolConfiguration() {
        final SlotManager slotManager = SlotManager.getInstance();
        final String existing = slotManager.getWebProtocol();
        final String updated = getWebProtocolFromProperties();
        if (!Objects.equals(existing, updated)) {
            slotManager.setWebProtocol(updated);

            // Issue #37: when switching to a new scheme, default to the corresponding port.
            applyWebPortConfiguration();

            Log.info("Reconfigured announced web protocol from '{}' to '{}'. New web endpoint: {}://{}:{}{} with a max file size of {} bytes.",
                existing, updated, slotManager.getWebProtocol(), slotManager.getWebHost(), slotManager.getWebPort(), slotManager.getWebContextRoot(), slotManager.getMaxFileSize());
        }
    }

    /**
     * Re-evaluates property values and applies any change to affects the web host to be used when announcing
     * endpoint URLs.
     */
    public static void applyWebHostConfiguration() {
        final SlotManager slotManager = SlotManager.getInstance();
        final String existing = slotManager.getWebHost();
        final String updated = getWebHostFromProperties();
        if (!Objects.equals(existing, updated)) {
            slotManager.setWebHost(updated);
            Log.info("Reconfigured announced web host from '{}' to '{}'. New web endpoint: {}://{}:{}{} with a max file size of {} bytes.",
                existing, updated, slotManager.getWebProtocol(), slotManager.getWebHost(), slotManager.getWebPort(), slotManager.getWebContextRoot(), slotManager.getMaxFileSize());
        }
    }

    /**
     * Re-evaluates property values and applies any change to affects the web port to be used when announcing
     * endpoint URLs.
     */
    public static void applyWebPortConfiguration() {
        final SlotManager slotManager = SlotManager.getInstance();
        final Integer existing = slotManager.getWebPort();
        final Integer updated = getWebPortFromProperties();
        if (!Objects.equals(existing, updated)) {
            slotManager.setWebPort(updated);
            Log.info("Reconfigured announced web port from '{}' to '{}'. New web endpoint: {}://{}:{}{} with a max file size of {} bytes.",
                existing, updated, slotManager.getWebProtocol(), slotManager.getWebHost(), slotManager.getWebPort(), slotManager.getWebContextRoot(), slotManager.getMaxFileSize());
        }
    }

    /**
     * Re-evaluates property values and applies any change to affects the web context root to be used when announcing
     * endpoint URLs.
     */
    public static void applyWebContextRootConfiguration() {
        final SlotManager slotManager = SlotManager.getInstance();
        final String existing = slotManager.getWebContextRoot();
        final String updated = getWebContextRootFromProperties();
        if (!Objects.equals(existing, updated)) {
            slotManager.setWebContextRoot(updated);
            Log.info("Reconfigured announced web context root from '{}' to '{}'. New web endpoint: {}://{}:{}{} with a max file size of {} bytes.",
                existing, updated, slotManager.getWebProtocol(), slotManager.getWebHost(), slotManager.getWebPort(), slotManager.getWebContextRoot(), slotManager.getMaxFileSize());
        }
    }

    /**
     * Re-evaluates property values and applies any change to affects maximum allowable file size of content to be
     * uploaded.
     */
    public static void applyMaxFileSizeConfiguration() {
        final SlotManager slotManager = SlotManager.getInstance();
        final Long existing = slotManager.getMaxFileSize();
        final Long updated = getMaxFileSizeProperties();
        if (!Objects.equals(existing, updated)) {
            slotManager.setMaxFileSize(updated);
            Log.info("Reconfigured maximum file size from '{}' to '{}'.", existing, updated);
        }
    }

    public static void applyClamAvConfiguration()
    {
        final MalwareScannerManager manager = MalwareScannerManager.getInstance();
        manager.destroy();

        if (CLAMAV_ENABLED.getValue()) {
            final MalwareScanner clamAv = new ClamavMalwareScanner(CLAMAV_HOST.getValue(), CLAMAV_PORT.getValue(), CLAMAV_CONNECTION_TIMEOUT.getValue());
            try {
                MalwareScannerManager.getInstance().initialize(clamAv);
            } catch (IOException e) {
                Log.warn("Unable to initialize integration with the ClamAV malware scanner daemon (at {}:{}).", CLAMAV_HOST.getValue(), CLAMAV_PORT.getValue(), e);
            }
        }
    }

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        try
        {
            PropertyEventDispatcher.addListener(this);

            SlotManager.getInstance().initialize(new OpenfireSlotProvider());

            applyWebProtocolConfiguration();
            applyWebHostConfiguration();
            applyWebPortConfiguration();
            applyWebContextRootConfiguration();
            applyMaxFileSizeConfiguration();
            applyClamAvConfiguration();

            Repository repository;
            final String fileRepo = getFileRepoFromProperties();

            if ( fileRepo == null)
            {
                repository = new TempDirectoryRepository();
            }
            else {
                try {
                    final Path path = Paths.get( fileRepo );
                    repository = new DirectoryRepository( path );

                } catch ( InvalidPathException e ) {
                    Log.error( "Invalid value for 'fileRepo' option: " + e.getMessage() );
                    repository = new TempDirectoryRepository();
                }
            }
            RepositoryManager.getInstance().initialize( repository );

            component = new Component( XMPPServer.getInstance().getServerInfo().getXMPPDomain());

            // Add the webapp to the same context as the one that's providing the BOSH interface.
            context = new WebAppContext( null, pluginDirectory.getPath() + File.separator + "classes", "/httpfileupload" );
            context.setClassLoader( this.getClass().getClassLoader() );

            // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
            final List<ContainerInitializer> initializers = new ArrayList<>();
            initializers.add( new ContainerInitializer( new JettyJasperInitializer(), null ) );
            context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
            context.setAttribute( InstanceManager.class.getName(), new SimpleInstanceManager());

            HttpBindManager.getInstance().addJettyHandler( context );

            InternalComponentManager.getInstance().addComponent( "httpfileupload", component );
        }
        catch ( Exception e )
        {
            Log.error( "Unable to register component!", e );
        }

    }

    @Override
    public void destroyPlugin()
    {
        if ( context != null )
        {
            HttpBindManager.getInstance().removeJettyHandler( context );
            context.destroy();
            context = null;
        }

        if ( component != null )
        {
            InternalComponentManager.getInstance().removeComponent( "httpfileupload" );
        }

        PropertyEventDispatcher.removeListener(this);
    }

    @Override
    public void propertySet(String property, Map params) {
        // Ignored: 'regular' property configuration is handled through the SystemProperties implementation.
    }

    @Override
    public void propertyDeleted(String property, Map params) {
        // Ignored: 'regular' property configuration is handled through the SystemProperties implementation.
    }

    @Override
    public void xmlPropertySet(String property, Map params)
    {
        switch (property) {
            case "plugin.httpfileupload.serverspecific.announcedWebProtocol":
                applyWebProtocolConfiguration();
                break;

            case "plugin.httpfileupload.serverspecific.announcedWebHost":
                applyWebHostConfiguration();
                break;

            case "plugin.httpfileupload.serverspecific.announcedWebPort":
                applyWebPortConfiguration();
                break;

            case "plugin.httpfileupload.serverspecific.announcedWebContextRoot":
                // Not dynamic // TODO figure out why this isn't dynamic.
                // applyWebContextRootConfiguration();
                break;

            case "plugin.httpfileupload.serverspecific.maxFileSize":
                applyMaxFileSizeConfiguration();
                break;

            case "plugin.httpfileupload.serverspecific.fileRepo":
                // Not dynamic
                break;
        }
    }

    @Override
    public void xmlPropertyDeleted(String property, Map params)
    {
        xmlPropertySet(property, params);
    }
}
