package org.igniterealtime.openfire.plugins.httpfileupload;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by guus on 18-11-17.
 */
public class HttpFileUploadPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger( HttpFileUploadPlugin.class );

    /**
     * Controls the scheme that is used in URLs advertised for file uploads/downloads.
     */
    public static final SystemProperty<String> ANNOUNCED_WEB_PROTOCOL = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.announcedWebProtocol")
        .setDefaultValue("https")
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> SlotManager.getInstance().setWebProtocol(newValue))
        .build();

    /**
     * Controls the server address that is used in URLs advertised for file uploads/downloads.
     */
    public static final SystemProperty<String> ANNOUNCED_WEB_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.announcedWebHost")
        .setDefaultValue(XMPPServer.getInstance().getServerInfo().getHostname())
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> SlotManager.getInstance().setWebHost(newValue))
        .build();

    /**
     * Controls the TCP port that is used in URLs advertised for file uploads/downloads.
     */
    public static final SystemProperty<Integer> ANNOUNCED_WEB_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("plugin.httpfileupload.announcedWebPort")
        .setDefaultValue(ANNOUNCED_WEB_PROTOCOL.getValue().equalsIgnoreCase("http") ? HttpBindManager.HTTP_BIND_PORT.getValue() : HttpBindManager.HTTP_BIND_SECURE_PORT.getValue())
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> SlotManager.getInstance().setWebPort(newValue))
        .build();

    /**
     * Controls the context root that is used in URLs advertised for file uploads/downloads.
     */
    public static final SystemProperty<String> ANNOUNCED_WEB_CONTEXT_ROOT = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.announcedWebContextRoot")
        .setDefaultValue("/httpfileupload")
        .setDynamic(false)
        .setPlugin("HTTP File Upload")
        .build();

    /**
     * Defines the maximum size (in bytes) of files that can be uploaded.
     */
    public static final SystemProperty<Long> MAX_FILE_SIZE = SystemProperty.Builder.ofType(Long.class)
        .setKey("plugin.httpfileupload.maxFileSize")
        .setDefaultValue(SlotManager.DEFAULT_MAX_FILE_SIZE)
        .setDynamic(true)
        .setPlugin("HTTP File Upload")
        .addListener(newValue -> SlotManager.getInstance().setMaxFileSize(newValue))
        .build();

    /**
     * Defines the file system path (directory) in which data is stored on the server. If the path is absent, or
     * invalid, a temporary directory will be used.
     */
    public static final SystemProperty<String> FILE_REPO = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.httpfileupload.fileRepo")
        .setDynamic(false)
        .setPlugin("HTTP File Upload")
        .build();

    static {
        // Issue #37: when switching to a new scheme, default to the corresponding port.
        ANNOUNCED_WEB_PROTOCOL.addListener( newValue -> SlotManager.getInstance().setWebPort( ANNOUNCED_WEB_PORT.getValue() ));
    }

    private Component component;
    private WebAppContext context;

    private final String[] publicResources = new String[]
        {
            "httpfileupload/*",
            "httpFileUpload/*"
        };

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        try
        {
            SlotManager.getInstance().initialize(new OpenfireSlotProvider());

            SlotManager.getInstance().setWebProtocol(ANNOUNCED_WEB_PROTOCOL.getValue());
            SlotManager.getInstance().setWebHost(ANNOUNCED_WEB_HOST.getValue());
            SlotManager.getInstance().setWebPort(ANNOUNCED_WEB_PORT.getValue());
            SlotManager.getInstance().setWebContextRoot(ANNOUNCED_WEB_CONTEXT_ROOT.getValue());
            SlotManager.getInstance().setMaxFileSize(MAX_FILE_SIZE.getValue());

            Repository repository;
            final String fileRepo = FILE_REPO.getValue();

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

            for ( final String publicResource : publicResources )
            {
                AuthCheckFilter.addExclude( publicResource );
            }
        }
        catch ( Exception e )
        {
            Log.error( "Unable to register component!", e );
        }

    }

    @Override
    public void destroyPlugin()
    {
        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.removeExclude( publicResource );
        }

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
    }
}
