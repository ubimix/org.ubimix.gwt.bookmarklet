/**
 * 
 */
package org.ubimix.gwt.bookmarklet.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A GWT adaptor for easyXDM sockets. See {@linkplain http://easyxdm.net/}.
 * 
 * @author kotelnikov
 */
public class XdmSocket {

    public interface IInitListener {
        void onInit(XdmSocket socket);
    }

    protected static class Request extends JavaScriptObject {

        public static native Request newRequest(
            String requestId,
            String url,
            JavaScriptObject data) /*-{
            return {
            requestId : requestId,
            serviceURL: url,
            data : data
            };
        }-*/;

        protected Request() {
        }

        public final String asString() {
            JSONObject json = new JSONObject(this);
            return json.toString();
        }

    }

    private class RequestWrapper {

        private Request fRequest;

        public RequestWrapper(Request request) {
            fRequest = request;
        }

        public void call() {
            doPostMessage(fSocket, fRequest.asString());
            if (fTimer == null) {
                fTimer = new Timer() {
                    @Override
                    public void run() {
                        for (String requestId : fMap.keySet()) {
                            ResponseCallbackWrapper wrapper = fMap
                                .get(requestId);
                            if (wrapper.isExpired()) {
                                fMap.remove(requestId);
                                wrapper.onTimeout();
                            }
                        }
                        if (fMap.isEmpty()) {
                            fTimer.cancel();
                            fTimer = null;
                        }
                    }
                };
                fTimer.scheduleRepeating(300);
            }
        }

        private native void doPostMessage(
            JavaScriptObject socket,
            String message) /*-{
            socket.postMessage(message);
        }-*/;
    }

    protected static class Response extends JavaScriptObject {

        public static Response getResponse(String json) {
            JSONObject o = (JSONObject) JSONParser.parseStrict(json);
            return toResponse(o.getJavaScriptObject());
        }

        private static native Response toResponse(JavaScriptObject o) /*-{
            return o;
        }-*/;

        protected Response() {
            super();
        }

        public final native JavaScriptObject getData() /*-{
            return this["data"];
        }-*/;

        public final native String getError() /*-{
            return this["error"];
        }-*/;

        public final native String getErrorMessage() /*-{
            return this["errorMessage"];
        }-*/;

        public final native String getRequestId() /*-{
            return this["requestId"];
        }-*/;

    }

    private class ResponseCallbackWrapper {
        private AsyncCallback<JavaScriptObject> fCallback;

        private long fTimeout;

        public ResponseCallbackWrapper(
            AsyncCallback<JavaScriptObject> callback,
            int delay) {
            fCallback = callback;
            fTimeout = now() + delay;
        }

        public boolean isExpired() {
            return now() - fTimeout >= 0;
        }

        private long now() {
            return System.currentTimeMillis();
        }

        public void onFailure(Throwable t) {
            fCallback.onFailure(t);
        }

        public void onSuccess(JavaScriptObject result) {
            fCallback.onSuccess(result);
        }

        public void onTimeout() {
            onFailure(new SocketTimeoutException());
        }

    }

    public static class SocketException extends Exception {
        private static final long serialVersionUID = 5220954010583510599L;

        public SocketException() {
            super();
        }

        public SocketException(String message) {
            super(message);
        }

    }

    public static class SocketTimeoutException extends SocketException {
        private static final long serialVersionUID = -4084390066937130089L;

        public SocketTimeoutException() {
            super();
        }

        public SocketTimeoutException(String message) {
            super(message);
        }
    }

    private int fCounter;

    private boolean fInitialized;

    private Map<String, ResponseCallbackWrapper> fMap = new HashMap<String, ResponseCallbackWrapper>();

    private String fProviderPageUrl;

    private List<RequestWrapper> fQueue = new ArrayList<RequestWrapper>();

    private String fServiceUrlBase;

    private JavaScriptObject fSocket;

    private Timer fTimer;

    public XdmSocket(String serviceUrlBase) {
        this(GWT.getModuleBaseURL() + "xdm-provider.html", GWT
            .getModuleBaseURL() + serviceUrlBase);
    }

    public XdmSocket(String providerUrl, String serviceUrlBase) {
        fProviderPageUrl = providerUrl;
        if (serviceUrlBase == null) {
            serviceUrlBase = "/";
        } else {
            serviceUrlBase = serviceUrlBase.replaceAll("\\\\", "/");
            if (!serviceUrlBase.endsWith("/")) {
                serviceUrlBase += "/";
            }
        }
        fServiceUrlBase = serviceUrlBase;
    }

    private void handleRequestQueue() {
        for (RequestWrapper request : fQueue) {
            request.call();
        }
        fQueue.clear();
    }

    protected final void initialize() {
        fInitialized = true;
        handleRequestQueue();
    }

    public boolean isOpened() {
        return fInitialized;
    }

    protected String newRequestId() {
        return "request-" + fCounter++;
    }

    private native JavaScriptObject newSocket(String providerPageUrl) /*-{
        var self = this;
        return new $wnd.easyXDM.Socket({
        remote: providerPageUrl,
        onMessage: function(message, origin){
        self.@org.ubimix.gwt.bookmarklet.client.XdmSocket::onMessage(Ljava/lang/String;)(message);
        },
        onReady: function() {
        self.@org.ubimix.gwt.bookmarklet.client.XdmSocket::initialize()();
        }
        });
    }-*/;

    protected void onMessage(String message) {
        Response response = Response.getResponse(message);
        String requestId = response.getRequestId();
        GWT.log("A response received: " + requestId);
        ResponseCallbackWrapper wrapper = fMap.remove(requestId);
        if (wrapper != null) {
            String error = response.getError();
            String errorMessage = response.getErrorMessage();
            if (error != null || errorMessage != null) {
                SocketException e = new SocketException(error != null
                    ? error
                    : errorMessage);
                GWT.log("An error was found.", e);
                wrapper.onFailure(e);
            } else {
                JavaScriptObject data = response.getData();
                wrapper.onSuccess(data);
            }
        }
    }

    public void postMessage(
        String method,
        JavaScriptObject data,
        int timeout,
        AsyncCallback<JavaScriptObject> callback) {
        String requestId = newRequestId();
        String serviceUrl = fServiceUrlBase + method;
        Request request = Request.newRequest(requestId, serviceUrl, data);
        ResponseCallbackWrapper responseWrapper = new ResponseCallbackWrapper(
            callback,
            timeout);
        fMap.put(requestId, responseWrapper);

        RequestWrapper requestWrapper = new RequestWrapper(request);
        fQueue.add(requestWrapper);
        if (!fInitialized) {
            fSocket = newSocket(fProviderPageUrl);
        } else {
            handleRequestQueue();
        }
    }

}