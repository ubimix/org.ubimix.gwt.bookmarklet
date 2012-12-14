/**
 * 
 */
package org.ubimix.gwt.bookmarklet.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

/**
 * The base class for bookmarklets.
 * 
 * @author kotelnikov
 */
public abstract class AbstractBookmarklet implements EntryPoint {

    /**
     * The application can be initialized only once but it could be activated
     * multiple times.
     * 
     * @author kotelnikov
     */
    public interface IBookmarkletApplication {

        /**
         * This method is called every time when the application is activated.
         */
        void onActivate();
    }

    private static native Object getApplication(String name)/*-{
        return $wnd["__" + name + "_application"];
    }-*/;

    private static native void setApplication(String name, Object app)/*-{
        $wnd["__" + name + "_application"] = app;
    }-*/;

    protected String getBaseURL() {
        String base = GWT.getModuleBaseURL();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base;
    }

    protected String getXdmProviderPath() {
        return "xdm-provider.html";
    }

    protected String getXdmProviderUrl() {
        String base = getBaseURL();
        return base + getXdmProviderPath();
    }

    protected String getXdmServicePath() {
        return "../service";
    }

    protected String getXdmServiceUrl() {
        String base = getBaseURL();
        return base + getXdmServicePath();
    }

    private boolean isEmpty(String str) {
        return str == null || "".equals(str.trim());
    }

    protected abstract IBookmarkletApplication newApplication(XdmSocket fetcher);

    public final void onModuleLoad() {
        String name = GWT.getModuleName();
        IBookmarkletApplication app = (IBookmarkletApplication) getApplication(name);
        if (app == null) {
            String xdmServiceUrl = getXdmServiceUrl();
            String xdmProviderUrl = getXdmProviderUrl();
            XdmSocket socket = new XdmSocket(xdmProviderUrl, xdmServiceUrl);
            app = newApplication(socket);
            setApplication(name, app);
        }
        app.onActivate();
    }

}
