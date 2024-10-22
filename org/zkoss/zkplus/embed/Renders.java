/* Renders.java

	Purpose:
		
	Description:
		
	History:
		Tue Aug 10 08:48:59 TST 2010, Created by tomyeh

Copyright (C) 2009 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkplus.embed;

import java.io.Writer;
import java.io.IOException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.zkoss.lang.Library;
import org.zkoss.web.servlet.http.Https;

import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Richlet;
import org.zkoss.zk.ui.GenericRichlet;
import org.zkoss.zk.ui.metainfo.PageDefinitions;
import org.zkoss.zk.ui.sys.HtmlPageRenders;
import org.zkoss.zk.ui.sys.WebAppCtrl;
import org.zkoss.zk.ui.sys.UiFactory;
import org.zkoss.zk.ui.sys.SessionCtrl;
import org.zkoss.zk.ui.sys.RequestInfo;
import org.zkoss.zk.ui.impl.Attributes;
import org.zkoss.zk.ui.impl.RequestInfoImpl;
import org.zkoss.zk.ui.http.WebManager;
import org.zkoss.zk.ui.http.ExecutionImpl;
import org.zkoss.zk.ui.http.I18Ns;
import org.zkoss.zk.ui.http.Utils;

/**
 * Utilities to embed ZK component(s) as a native JSF component, a JSP tag, Zimlet or others.
 * It allows application developers to use the native element without knowing the existence of ZK.
 * For example, ZK Spreadsheet for JSF is a native JSF component made in this way.
 *
 * <p>To use an embedded component, ZK Update Engine
 * ({@link org.zkoss.zk.au.http.DHtmlUpdateServlet}) is still required,
 * while ZK Loader ({@link org.zkoss.zk.ui.http.DHtmlLayoutServlet}) is not needed (though not hurt).
 * If ZK Loader not installed, it assumes the update URI is "/zkau", which
 * can be overriden by setting a library property called
 * "org.zkoss.zkplus.embed.updateURI".
 *
 * <p>Example:
<pre><code>
	Calendar cal = new Calendar();
	Renders.render(config.getServletContext(), request, response, cal, null, out);
</code></pre>
 *
 * <p>See also <a href="http://books.zkoss.org/wiki/ZK_Developer%27s_Reference/Integration/Embed_ZK_Component_in_Foreign_Framework">Embed ZK Component in Foreign Framework</a>.
 *
 * @author tomyeh
 * @since 5.0.5
 */
public class Renders {
	/** Outputs the HTML tags of the given component to the given writer.
	 * @param comp the component to output (never null). It might have child components.
	 * @param path the request path. If null, the servlet path is assumed.
	 * @param out the output (never null).
	 * @since 5.0.5
	 */
	public static final void render(ServletContext ctx,
	HttpServletRequest request, HttpServletResponse response,
	Component comp, String path, Writer out)
	throws ServletException, IOException {
		if (comp == null)
			throw new IllegalArgumentException();
		render(ctx, request, response, new EmbedRichlet(comp), path, out);
	}
	/** Outputs the HTML tags of the given component to the given writer.
	 * @param path the request path. If null, the servlet path is assumed.
	 * @param out the output (never null).
	 * @param richlet the richlet to run. If you have only one component to show and no need
	 * process it under an execution, you could use
	 * {@link #render(ServletContext, HttpServletRequest, HttpServletResponse, Component, String, Writer)}
	 * instead.
	 * @since 5.0.5
	 */
	public static final void render(ServletContext ctx,
	HttpServletRequest request, HttpServletResponse response,
	Richlet richlet, String path, Writer out)
	throws ServletException, IOException {
		if (path == null)
			path = Https.getThisServletPath(request);

		WebManager webman = WebManager.getWebManagerIfAny(ctx);
		if (webman == null) {
			final String ATTR = "org.zkoss.zkplus.embed.updateURI";
			String updateURI = Library.getProperty(ATTR);
			if (updateURI == null)
				updateURI = "/zkau";
			else
				updateURI = Utils.checkUpdateURI(updateURI, ATTR);
			webman = new WebManager(ctx, updateURI);
		}

		final Session sess = WebManager.getSession(ctx, request);
		final WebApp wapp = sess.getWebApp();
		final WebAppCtrl wappc = (WebAppCtrl)wapp;
		final Object old = I18Ns.setup(sess, request, response,
			wapp.getConfiguration().getResponseCharset());
		Execution exec = null;
		try {
			final Desktop desktop = webman.getDesktop(sess, request, response, path, true);
			if (desktop == null) //forward or redirect
				return;

			final RequestInfo ri = new RequestInfoImpl(
				wapp, sess, desktop, request,
				PageDefinitions.getLocator(wapp, path));
			sess.setAttribute(Attributes.GAE_FIX, new Integer(0));
			((SessionCtrl)sess).notifyClientRequest(true);

			final UiFactory uf = wappc.getUiFactory();
			final Page page = WebManager.newPage(uf, ri, richlet, response, path);
			exec = new ExecutionImpl(ctx, request, response, desktop, page);
			exec.setAttribute(Attributes.PAGE_REDRAW_CONTROL, "page");
			exec.setAttribute(Attributes.PAGE_RENDERER, new PageRenderer(exec));

			wappc.getUiEngine().execNewPage(exec, richlet, page, out);
					//no need to set device type here, since UiEngine will do it later
		} finally {
			I18Ns.cleanup(request, old);
			if (exec != null) {
				exec.removeAttribute(Attributes.PAGE_REDRAW_CONTROL);
				exec.removeAttribute(Attributes.PAGE_RENDERER);
			}
		}
	}
	//Supporting classes//
	private static class EmbedRichlet extends GenericRichlet {
		private final Component _comp;
		private EmbedRichlet(Component comp) {
			_comp = comp;
		}
		//@Override
		public void service(Page page) {
			_comp.setPage(page);
		}
	}

	/**
	 * A special page renderer that renders a page without generating
	 * the HTML tag of the page.
	 * In other words, it generates all components directly.
	 * @author tomyeh
	 * @since 5.0.4
	 */
	public static class PageRenderer implements org.zkoss.zk.ui.sys.PageRenderer {
		private final Execution _exec;
		/** Default constructor.
		 * It is the same as <code>PageRenderer(Executions.getCurrent())</code>.
		 */
		public PageRenderer() {
			this(Executions.getCurrent());
		}
		public PageRenderer(Execution exec) {
			_exec = exec;
		}

		//@Override
		public void render(Page page, Writer out) throws IOException {
			final Desktop desktop = _exec.getDesktop();

			out.write(HtmlPageRenders.outLangStyleSheets(_exec, null, null));
			out.write(HtmlPageRenders.outLangJavaScripts(_exec, null, null));

			out.write("<script type=\"text/javascript\">zkpb('");
			out.write(page.getUuid());
			out.write("','");
			out.write(desktop.getId());
			out.write("','");
			out.write(getContextURI());
			out.write("','");
			out.write(desktop.getUpdateURI(null));
			out.write("','");
			out.write(desktop.getRequestPath());
			out.write('\'');

			String style = page.getStyle();
			if (style != null && style.length() > 0) {
				out.write(",{style:'");
				out.write(style);
				out.write("'}");
			}

			out.write(");zkpe();</script>\n");

			for (Component root = page.getFirstRoot(); root != null;
			root = root.getNextSibling()) {
				HtmlPageRenders.outStandalone(_exec, root, out);
			}
		}
		private String getContextURI() {
			if (_exec != null) {
				String s = _exec.encodeURL("/");
				int j = s.lastIndexOf('/'); //might have jsessionid=...
				return j >= 0 ? s.substring(0, j) + s.substring(j + 1): s;
			}
			return "";
		}
	}
}
