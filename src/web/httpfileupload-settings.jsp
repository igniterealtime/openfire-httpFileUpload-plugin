<%@page import="java.net.URI"%>
<%@page import="java.net.URISyntaxException"%>
<%@page import="java.io.IOException"%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="/error.jsp" %>
<%@ page import="org.igniterealtime.openfire.plugins.httpfileupload.HttpFileUploadPlugin"%>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.time.Duration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Optional" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>
<%
    // Get handle on the HttpFileUpload plugin
    HttpFileUploadPlugin plugin = (HttpFileUploadPlugin) XMPPServer.getInstance().getPluginManager().getPluginByName("HTTP File Upload").get();

%>

<html>
<head>
  <title><fmt:message key="httpfileupload.settings.title"/></title>
  <meta name="pageID" content="httpfileupload-settings"/>

</head>

<body>

<% // Get parameters
    boolean update = request.getParameter("update") != null;
     
    Map<String, String> errors = new HashMap<>();
    String errorMessage = "";

    if (request.getParameter("cancel") != null) {
        response.sendRedirect("httpfileupload-settings.jsp");
        return;
    }
    
    String fileRepo = Optional.ofNullable( HttpFileUploadPlugin.FILE_REPO.getValue()).orElse("");
        
    String announcedAddress = "unknown";
    try {
      announcedAddress = plugin.getAnnouncedAddress();
	  } catch (URISyntaxException e) {
      errors.put("announcedAddress" , e.getMessage() );
      errorMessage = "Announced Address is not correct: " + e.getMessage();
	  }

    // Update the session kick policy if requested
    if (request.getMethod().equals("POST") && update) {
        // New settings for message archiving.
        String announcedProtocol = request.getParameter("announcedProtocol");
        String announcedWebHost = request.getParameter("announcedWebHost");
        Integer announcedPort = ParamUtils.getIntParameter(request, "announcedPort", HttpFileUploadPlugin.ANNOUNCED_WEB_PORT.getValue());
        String announcedContextRoot = request.getParameter("announcedContextRoot");
        long maxFileSize = ParamUtils.getLongParameter(request, "maxFileSize", HttpFileUploadPlugin.MAX_FILE_SIZE.getValue());
        fileRepo = request.getParameter("fileRepo");

        if ( fileRepo != null && !fileRepo.equals("") ) {
          try {
            plugin.check(fileRepo);
          } catch ( IOException e ) {
            errors.put("fileRepo" , e.getMessage() );
            errorMessage = "File Directory not correct: " + e.getMessage();
          }
        }
        
        try {
          final URI uri = new URI(
              announcedProtocol,
              null, // userinfo
              announcedWebHost,
              announcedPort,
              announcedContextRoot,
              null, // query
              null // fragment
          );
          announcedAddress =  uri.toASCIIString();
          errors.remove("announcedAddress");
        } catch (URISyntaxException e) {
          errors.put("announcedAddress" , e.getMessage() );
          errorMessage = "Announced Address is not correct: " + e.getMessage();
        }
        
        // If no errors, continue:
        if (errors.size() == 0) {
          HttpFileUploadPlugin.ANNOUNCED_WEB_PROTOCOL.setValue(announcedProtocol);
          HttpFileUploadPlugin.ANNOUNCED_WEB_HOST.setValue(announcedWebHost);
          HttpFileUploadPlugin.ANNOUNCED_WEB_PORT.setValue(announcedPort);
          HttpFileUploadPlugin.ANNOUNCED_WEB_CONTEXT_ROOT.setValue(announcedContextRoot);
          HttpFileUploadPlugin.FILE_REPO.setValue(fileRepo);
          HttpFileUploadPlugin.MAX_FILE_SIZE.setValue(maxFileSize);

          webManager.logEvent("Changed HTTP File Upload settings (httpfileupload plugin)",
                                "announced Protocol: " + announcedProtocol
		                                + ", announced Web Host: " + announcedWebHost
		                                + ", announced Port: " + announcedPort
		                                + ", announced Context Root: " + announcedContextRoot
                                    + ", File Directory: " + fileRepo
                                    + ", maxFileSize: " + maxFileSize );

%>
<div class="success">
    <fmt:message key="httpfileupload.settings.success"/>
</div><br>
<%
        }
    }
%>

<% if (errors.size() > 0) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<p>
    <fmt:message key="httpfileupload.settings.description"/>
</p>

    <% if ( ! HttpBindManager.getInstance().isHttpBindEnabled() ) { %>

    <div class="jive-warning">
        <table>
            <tbody>
            <tr><td class="jive-icon"><img src="images/warning-16x16.gif" width="16" height="16" border="0" alt=""></td>
                <td class="jive-icon-label">
                    <fmt:message key="warning.httpbinding.disabled">
                        <fmt:param value="<a href=\"../../http-bind.jsp\">"/>
                        <fmt:param value="</a>"/>
                    </fmt:message>
                </td></tr>
            </tbody>
        </table>
    </div><br>
    <%  } %>
     <div class="jive-contentBoxHeader"><fmt:message key="httpfileupload.settings.logs.title"/></div>
     <div class="jive-contentBox">
	     <p>
	     <fmt:message key="httpfileupload.settings.logs.link.announced">
	         <fmt:param value="<%= announcedAddress %>"/>
	     </fmt:message>
	     </p>
	     
	     <% 
	     if ( HttpFileUploadPlugin.ANNOUNCED_WEB_PROTOCOL.getValue().equals("https") ) {
	       
         final String securedAddress = "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HttpBindManager.HTTP_BIND_SECURE_PORT.getValue() + "/httpfileupload";
      
         if ( !announcedAddress.equals(securedAddress)) { %>
            <div class="warning">
				      <fmt:message key="httpfileupload.settings.logs.link.secure">
				          <fmt:param value="<%= securedAddress %>"/>
				      </fmt:message>
            </div>
      <%  }
        } else {
	       
        final String unsecuredAddress = "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HttpBindManager.HTTP_BIND_PORT.getValue() + "/httpfileupload";
     
        if ( !announcedAddress.equals(unsecuredAddress) ) { %>
            <div class="warning">
      	     <fmt:message key="httpfileupload.settings.logs.link.unsecure">
	            <fmt:param value="<%= unsecuredAddress %>"/>
	           </fmt:message>
	          </div>
       <%  }	     
        }
	     %>
	     
	     <p><fmt:message key="httpfileupload.settings.logs.redirect" >
	           <fmt:param value="<a href=\"../../http-bind.jsp\">"/>
	           <fmt:param value="</a>"/>
	         </fmt:message></p>
    </div>

  <form action="httpfileupload-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
            
    <div class="jive-contentBoxHeader"><fmt:message key="httpfileupload.settings.message.metadata.title"/></div>
    <div class="jive-contentBox">
      <table>
        <tbody>
            <tr>
                <td colspan="3"><p><fmt:message key="httpfileupload.settings.message.metadata.description" /></p></td>
            </tr>
            <tr> <!-- plugin.httpfileupload.announcedProtocol’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedProtocol.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebProtocol"/></td>
               <td>
                   <input type="text" name="announcedProtocol" size="10" maxlength="10" value="<%= HttpFileUploadPlugin.ANNOUNCED_WEB_PROTOCOL.getValue() %>" />
               </td>
               <td></td>
             </tr>
            <tr> <!-- plugin.httpfileupload.announcedWebHost’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedWebHost.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebHost"/></td>
               <td>
                   <input type="text" name="announcedWebHost" size="20" maxlength="200" value="<%= HttpFileUploadPlugin.ANNOUNCED_WEB_HOST.getValue() %>" />
               </td>
               <td></td>
             </tr>
            <tr> <!-- plugin.httpfileupload.announcedPort’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedPort.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebPort"/></td>
               <td>
                   <input type="text" name="announcedPort" size="10" maxlength="10" value="<%= HttpFileUploadPlugin.ANNOUNCED_WEB_PORT.getValue() %>" />
               </td>
               <td></td>
             </tr>
            <tr> <!-- plugin.httpfileupload.announcedContextRoot’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedContextRoot.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebContextRoot"/></td>
               <td>
                   <input type="text" name="announcedContextRoot" size="20" maxlength="200" value="<%= HttpFileUploadPlugin.ANNOUNCED_WEB_CONTEXT_ROOT.getValue() %>" />
               </td>
               <td></td>
	           </tr>
	           <tr> <!-- plugin.httpfileupload.fileRepo’  -->
	               <td>
	                   <label class="jive-label"><fmt:message key="httpfileupload.settings.fileRepo.title"/>:</label>
	                   <br>
	                   <fmt:message key="system_property.plugin.httpfileupload.fileRepo"/></td>
	               <td>
	                   <input type="text" name="fileRepo" size="20" maxlength="250" value="<%= fileRepo %>" />
	               </td>
	               <td></td>
              </tr>
	            <tr> <!-- plugin.httpfileupload.maxFileSize’  -->
	               <td>
	                   <label class="jive-label"><fmt:message key="httpfileupload.settings.maxFileSize.title"/>:</label>
	                   <br>
	                   <fmt:message key="system_property.plugin.httpfileupload.maxFileSize"/></td>
	               <td>
	                   <input type="text" name="maxFileSize" size="10" maxlength="10" value="<%= HttpFileUploadPlugin.MAX_FILE_SIZE.getValue() %>" />
	               </td>
	               <td></td>
	           </tr>
            
        </tbody>
      </table>
    </div>

    <input type="submit" name="update" value="<fmt:message key="httpfileupload.settings.update.settings" />">
    <input type="submit" name="cancel" value="<fmt:message key="httpfileupload.settings.cancel" />">

</form>

</body>
</html>
