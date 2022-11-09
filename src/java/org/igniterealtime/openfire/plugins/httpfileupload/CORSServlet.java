/*
 * Copyright (c) 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

import nl.goodbytes.xmpp.xep0363.Servlet;
import org.jivesoftware.openfire.http.HttpBindManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by guus on 19-11-17.
 */
public class CORSServlet extends Servlet
{
    @Override
    protected void service( HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        final HttpBindManager boshManager = HttpBindManager.getInstance();

        // add CORS headers for all HTTP responses (errors, etc.)
        if (HttpBindManager.HTTP_BIND_CORS_ENABLED.getValue())
        {
            if (boshManager.isAllOriginsAllowed()) {
                // Set the Access-Control-Allow-Origin header to * to allow all Origin to do the CORS
                response.setHeader( "Access-Control-Allow-Origin", HttpBindManager.HTTP_BIND_CORS_ALLOW_ORIGIN_ALL);
            } else {
                // Get the Origin header from the request and check if it is in the allowed Origin Map.
                // If it is allowed write it back to the Access-Control-Allow-Origin header of the respond.
                final String origin = request.getHeader("Origin");
                if (boshManager.isThisOriginAllowed(origin)) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                }
            }
            response.setHeader("Access-Control-Allow-Methods", String.join(", ", HttpBindManager.HTTP_BIND_CORS_ALLOW_METHODS.getDefaultValue()));
            response.setHeader("Access-Control-Allow-Headers", String.join(", ", HttpBindManager.HTTP_BIND_CORS_ALLOW_HEADERS.getDefaultValue()));
            response.setHeader("Access-Control-Max-Age", String.valueOf(HttpBindManager.HTTP_BIND_CORS_MAX_AGE.getDefaultValue().getSeconds())); // // TODO: replace with 'toSeconds()' after dropping support for Java 8.
        }
        super.service(request, response);
    }

}
