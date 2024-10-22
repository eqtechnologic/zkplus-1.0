/* ZkAuthenticationEntryPoint.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Thu Dec 21 16:17:28     2006, Created by Henri
}}IS_NOTE

Copyright (C) 2006 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
}}IS_RIGHT
*/
package org.zkoss.zkplus.acegi;

import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.UiException;

import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilter;
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint;
import org.acegisecurity.context.SecurityContextHolder;
import org.springframework.beans.factory.InitializingBean;

import org.springframework.util.Assert;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <p>This implementation would forward to onAcegiLogin event and popup a login page.</p>
 * <p>Applicable to Acegi Security version 1.0.3</p>
 * @see ZkAuthenticationProcessingFilter
 * @see ShowWindowEventListener
 * @author Henri
 */
public class ZkAuthenticationEntryPoint extends AuthenticationProcessingFilterEntryPoint {
	/*package*/ static final String ON_ACEGILOGIN = "onAcegiLogin";
    /** <p>This implmentation forward request to onAcegiLogin command.</p>
     */
	public void commence(ServletRequest request, ServletResponse response, AuthenticationException authException)
    throws IOException, ServletException {
    	final Component comp = (Component) request.getAttribute(ZkEventExceptionFilter.COMPONENT);
    	
    	//remember the original event that cause the security login in session
    	final Event evt = (Event) request.getAttribute(ZkEventExceptionFilter.EVENT);
		((HttpServletRequest)request).getSession().setAttribute(ZkEventExceptionFilter.EVENT, evt);					

		if (!comp.isListenerAvailable(ON_ACEGILOGIN, true)) {
			final EventListener listener = new ShowWindowEventListener();
			comp.setAttribute(ON_ACEGILOGIN, listener);
			comp.addEventListener(ON_ACEGILOGIN, listener);
		}
		final String url = getLoginFormUrl();
    	Events.postEvent(new Event(ON_ACEGILOGIN, comp, url != null ? url : "~./acegilogin.zul"));
    }
}
