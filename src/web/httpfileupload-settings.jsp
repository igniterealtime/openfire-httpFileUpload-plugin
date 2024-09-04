<%@page import="org.jivesoftware.openfire.cluster.ClusterManager"%>
<%@page import="java.net.URI"%>
<%@page import="java.net.URISyntaxException"%>
<%@page import="java.io.IOException"%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="/error.jsp" %>
<%@ page import="org.igniterealtime.openfire.plugins.httpfileupload.HttpFileUploadPlugin"%>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Optional" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>
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

    final boolean isCluster = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();
    final boolean isEncrypted = HttpFileUploadPlugin.ANNOUNCED_WEB_PROTOCOL.getValue().equals("https");

    final String calculatedAddress =
        ( isEncrypted ? "https://" : "http://" )
            + ( isEncrypted ? XMPPServer.getInstance().getServerInfo().getXMPPDomain() : XMPPServer.getInstance().getServerInfo().getHostname() )
            + ":"
            + ( isEncrypted ? HttpBindManager.HTTP_BIND_SECURE_PORT.getValue() : HttpBindManager.HTTP_BIND_PORT.getValue() )
            + "/httpfileupload";

    request.setAttribute("errors", errors);
    request.setAttribute("errorMessage", errorMessage);
    request.setAttribute("isHttpBindEnabled", HttpBindManager.getInstance().isHttpBindEnabled());
    request.setAttribute("announcedAddress", announcedAddress);
    request.setAttribute("calculatedAddress", calculatedAddress);
    request.setAttribute("isCluster", isCluster);
    request.setAttribute("isEncrypted", isEncrypted);
    request.setAttribute("fileRepo", fileRepo);
    request.setAttribute("announcedWebProtocol", HttpFileUploadPlugin.ANNOUNCED_WEB_PROTOCOL.getValue());
    request.setAttribute("announcedWebHost", HttpFileUploadPlugin.ANNOUNCED_WEB_HOST.getValue());
    request.setAttribute("announcedWebPort", HttpFileUploadPlugin.ANNOUNCED_WEB_PORT.getValue());
    request.setAttribute("announcedContextRoot", HttpFileUploadPlugin.ANNOUNCED_WEB_CONTEXT_ROOT.getValue());
    request.setAttribute("maxFileSize", HttpFileUploadPlugin.MAX_FILE_SIZE.getValue());
%>

<c:if test="${not empty errors}">
    <div class="error">
        <c:out value="${errorMessage}"/>
    </div>
    <br/>
</c:if>

<p>
    <fmt:message key="httpfileupload.settings.description"/>
</p>

    <c:if test="${not isHttpBindEnabled}">
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
    </c:if>
     <div class="jive-contentBoxHeader"><fmt:message key="httpfileupload.settings.logs.title"/></div>
     <div class="jive-contentBox">
	     <p>
	     <fmt:message key="httpfileupload.settings.logs.link.announced">
             <fmt:param><c:out value="${announcedAddress}"/></fmt:param>
	     </fmt:message>
	     </p>

         <c:if test="${not announcedAddress eq calculatedAddress}">
            <div class="warning">
                 <fmt:message key="${isCluster ? 'httpfileupload.settings.logs.link.cluster' : (isEncrypted ? 'httpfileupload.settings.logs.link.secure' : 'httpfileupload.settings.logs.link.unsecure' )}">
                     <fmt:param><c:out value="${calculatedAddress}"/></fmt:param>
                 </fmt:message>
             </div>
         </c:if>

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
                   <input type="text" name="announcedProtocol" size="10" maxlength="10" value="${admin:escapeHTMLTags(announcedWebProtocol)}" />
               </td>
               <td></td>
             </tr>
            <tr> <!-- plugin.httpfileupload.announcedWebHost’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedWebHost.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebHost"/></td>
               <td>
                   <input type="text" name="announcedWebHost" size="20" maxlength="200" value="${admin:escapeHTMLTags(announcedWebHost)}" />
               </td>
               <td></td>
             </tr>
            <tr> <!-- plugin.httpfileupload.announcedPort’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedPort.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebPort"/></td>
               <td>
                   <input type="number" min="1" max="65535" name="announcedPort" size="10" maxlength="10" value="${admin:escapeHTMLTags(announcedWebPort)}" />
               </td>
               <td></td>
             </tr>
            <tr> <!-- plugin.httpfileupload.announcedContextRoot’  -->
               <td>
                   <label class="jive-label"><fmt:message key="httpfileupload.settings.announcedContextRoot.title"/>:</label>
                   <br>
                   <fmt:message key="system_property.plugin.httpfileupload.announcedWebContextRoot"/></td>
               <td>
                   <input type="text" name="announcedContextRoot" size="20" maxlength="200" value="${admin:escapeHTMLTags(announcedContextRoot)}" />
               </td>
               <td></td>
	           </tr>
	           <tr> <!-- plugin.httpfileupload.fileRepo’  -->
	               <td>
	                   <label class="jive-label"><fmt:message key="httpfileupload.settings.fileRepo.title"/>:</label>
	                   <br>
	                   <fmt:message key="system_property.plugin.httpfileupload.fileRepo"/><br>
                     <fmt:message key="httpfileupload.settings.fileRepo.cluster.desc"/></td>
	               <td>
	                   <input type="text" name="fileRepo" size="30" maxlength="250" value="${admin:escapeHTMLTags(fileRepo)}" />
	               </td>
	               <td></td>
              </tr>
	            <tr> <!-- plugin.httpfileupload.maxFileSize’  -->
	               <td>
	                   <label class="jive-label"><fmt:message key="httpfileupload.settings.maxFileSize.title"/>:</label>
	                   <br>
	                   <fmt:message key="system_property.plugin.httpfileupload.maxFileSize"/></td>
	               <td>
	                   <input type="number" min="0" name="maxFileSize" size="10" maxlength="10" value="${maxFileSize}" />
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
