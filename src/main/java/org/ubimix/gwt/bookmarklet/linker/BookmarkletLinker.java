package org.ubimix.gwt.bookmarklet.linker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.CompilationResult;
import com.google.gwt.core.ext.linker.EmittedArtifact;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.ScriptReference;
import com.google.gwt.core.ext.linker.StylesheetReference;
import com.google.gwt.core.ext.linker.SyntheticArtifact;
import com.google.gwt.core.ext.linker.impl.SelectionScriptLinker;
import com.google.gwt.core.ext.linker.impl.StandardLinkerContext;
import com.google.gwt.dev.About;
import com.google.gwt.dev.jjs.SourceInfo;
import com.google.gwt.dev.js.JsObfuscateNamer;
import com.google.gwt.dev.js.JsParser;
import com.google.gwt.dev.js.JsParserException;
import com.google.gwt.dev.js.JsSourceGenerationVisitor;
import com.google.gwt.dev.js.JsStringInterner;
import com.google.gwt.dev.js.JsSymbolResolver;
import com.google.gwt.dev.js.JsUnusedFunctionRemover;
import com.google.gwt.dev.js.ast.JsContext;
import com.google.gwt.dev.js.ast.JsFunction;
import com.google.gwt.dev.js.ast.JsModVisitor;
import com.google.gwt.dev.js.ast.JsName;
import com.google.gwt.dev.js.ast.JsProgram;
import com.google.gwt.dev.js.ast.JsScope;
import com.google.gwt.dev.util.DefaultTextOutput;
import com.google.gwt.dev.util.Util;
import com.google.gwt.util.tools.Utility;

/**
 * @author kotelnikov
 */
@LinkerOrder(LinkerOrder.Order.PRIMARY)
public class BookmarkletLinker extends SelectionScriptLinker {

    /**
     * Determines whether or not the URL is relative.
     * 
     * @param src the test url
     * @return <code>true</code> if the URL is relative, <code>false</code> if
     *         not
     */
    protected static boolean isRelativeURL(String src) {
        // A straight absolute url for the same domain, server, and protocol.
        if (src.startsWith("/")) {
            return false;
        }
        // If it can be parsed as a URL, then it's probably absolute.
        try {
            // Just check to see if it can be parsed, no need to store the
            // result.
            new URL(src);

            // Let's guess that it is absolute (thus, not relative).
            return false;
        } catch (MalformedURLException e) {
            // Do nothing, since it was a speculative parse.
        }
        // Since none of the above matched, let's guess that it's relative.
        return true;
    }

    protected SyntheticArtifact buildBookmarklet(
        TreeLogger logger,
        LinkerContext context,
        String bookmarkletCode) throws UnableToCompleteException {
        byte[] code = Util.getBytes(bookmarkletCode);
        String bookmarkletName = getBookmarkletName();
        SyntheticArtifact artifact = emitBytes(logger, code, bookmarkletName);
        return artifact;
    }

    protected String buildBookmarkletCode(
        TreeLogger logger,
        LinkerContext context) throws UnableToCompleteException {
        try {
            String bookmarkletTemplateName = getBookmarkletTemplateName(
                logger,
                context);
            String bookmarkletTemplate = Utility
                .getFileFromClassPath(bookmarkletTemplateName);
            StringBuffer buf = new StringBuffer(bookmarkletTemplate);
            replaceAll(buf, "__MODULE_FUNC__", context.getModuleFunctionName());
            replaceAll(buf, "__MODULE_NAME__", context.getModuleName());

            String bookmarklet = buf.toString();
            bookmarklet = optimizeJavaScript(logger, context, bookmarklet);
            return bookmarklet;
        } catch (IOException e) {
            logger.log(
                TreeLogger.ERROR,
                "Unable to read bookmarklet template",
                e);
            throw new UnableToCompleteException();
        }
    }

    protected SyntheticArtifact buildBookmarkletPage(
        TreeLogger logger,
        LinkerContext context,
        String bookmarkletCode) throws UnableToCompleteException {
        try {
            String bookmarkletTemplateName = getBookmarkletPageTemplateName(
                logger,
                context);
            String pageTemplate = Utility
                .getFileFromClassPath(bookmarkletTemplateName);
            StringBuffer buf = new StringBuffer(pageTemplate);
            replaceAll(buf, "__MODULE_FUNC__", context.getModuleFunctionName());
            replaceAll(buf, "__MODULE_NAME__", context.getModuleName());
            replaceAll(buf, "__BOOKMARKLET_CODE__", bookmarkletCode);

            byte[] code = Util.getBytes(buf.toString());
            String bookmarkletPageName = getBookmarkletPageName();
            SyntheticArtifact artifact = emitBytes(
                logger,
                code,
                bookmarkletPageName);
            return artifact;
        } catch (IOException e) {
            logger.log(
                TreeLogger.ERROR,
                "Unable to read bookmarklet template",
                e);
            throw new UnableToCompleteException();
        }
    }

    @Override
    protected EmittedArtifact emitSelectionScript(
        TreeLogger logger,
        LinkerContext context,
        ArtifactSet artifacts) throws UnableToCompleteException {
        String newName = context.getModuleName() + ".js";
        EmittedArtifact artifact = super.emitSelectionScript(
            logger,
            context,
            artifacts);
        try {
            InputStream code = artifact.getContents(logger);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Util.copy(logger, code, out);
                artifact = new SyntheticArtifact(
                    getClass(),
                    newName,
                    out.toByteArray());
                return artifact;
            } finally {
                code.close();
            }
        } catch (IOException e) {
            logger.log(Type.ERROR, "Can not replace the selector script from '"
                + artifact.getPartialPath()
                + "' to '"
                + newName
                + "'.");
            throw new UnableToCompleteException();
        }
    }

    @Override
    protected String fillSelectionScriptTemplate(
        StringBuffer selectionScript,
        TreeLogger logger,
        LinkerContext context,
        ArtifactSet artifacts,
        CompilationResult result) throws UnableToCompleteException {
        String computeScriptBase;
        String processMetas;
        try {
            computeScriptBase = Utility
                .getFileFromClassPath(COMPUTE_SCRIPT_BASE_JS);
            processMetas = Utility.getFileFromClassPath(PROCESS_METAS_JS);
        } catch (IOException e) {
            logger.log(
                TreeLogger.ERROR,
                "Unable to read selection script template",
                e);
            throw new UnableToCompleteException();
        }

        replaceAll(
            selectionScript,
            "__COMPUTE_SCRIPT_BASE__",
            computeScriptBase);
        replaceAll(selectionScript, "__PROCESS_METAS__", processMetas);

        selectionScript = injectResources(selectionScript, artifacts);
        permutationsUtil.addPermutationsJs(selectionScript, logger, context);

        replaceAll(
            selectionScript,
            "__MODULE_FUNC__",
            context.getModuleFunctionName());
        replaceAll(selectionScript, "__MODULE_NAME__", context.getModuleName());
        replaceAll(selectionScript, "__HOSTED_FILENAME__", getHostedFilename());

        return selectionScript.toString();
    }

    protected String generateScriptInjector(String scriptUrl) {
        try {
            if (isRelativeURL(scriptUrl)) {

                String base = getRelativeScriptsBase();
                scriptUrl = base + scriptUrl;
                scriptUrl = scriptUrl.replace('\\', '/');
                String script = Utility.getFileFromClassPath(scriptUrl);
                int idx = scriptUrl.lastIndexOf('/');
                String name = idx > 0
                    ? scriptUrl.substring(idx + 1)
                    : scriptUrl;
                script = optimizeJavaScript(name, script);

                script = "\n /* " + scriptUrl + " */" + "\n" + script + "\n\n";
                return script;
            } else {
                throw new IllegalArgumentException(
                    "A local script is expected.");
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(
                    "Unable to read the script content. Script URL: "
                        + scriptUrl,
                    e);
            }
        }
        // return "\n{\n"
        // + "var s = document.createElement('script');\n"
        // + "s.setAttribute('src', '"
        // + scriptUrl
        // + "');\n"
        // + "document.getElementsByTagName('head')[0].appendChild(s);\n"
        // + "}\n";
    }

    /**
     * Generate a Snippet of JavaScript to inject an external stylesheet.
     * 
     * <pre>
     * if (!__gwt_stylesLoaded['URL']) {
     *   var l = $doc.createElement('link');
     *   __gwt_styleLoaded['URL'] = l;
     *   l.setAttribute('rel', 'stylesheet');
     *   l.setAttribute('href', HREF_EXPR);
     *   $doc.getElementsByTagName('head')[0].appendChild(l);
     * }
     * </pre>
     */
    protected String generateStylesheetInjector(String stylesheetUrl) {
        String hrefExpr = "'" + stylesheetUrl + "'";
        if (isRelativeURL(stylesheetUrl)) {
            hrefExpr = "base + " + hrefExpr;
        }
        return "if (!__gwt_stylesLoaded['"
            + stylesheetUrl
            + "']) {\n           "
            + "  var l = $doc.createElement('link');\n                          "
            + "  __gwt_stylesLoaded['"
            + stylesheetUrl
            + "'] = l;\n             "
            + "  l.setAttribute('rel', 'stylesheet');\n                         "
            + "  l.setAttribute('href', "
            + hrefExpr
            + ");\n                    "
            + "  $doc.getElementsByTagName('head')[0].appendChild(l);\n         "
            + "}\n";
    }

    protected String getBookmarkletName() {
        return "BookmarkletHref.js";
    }

    protected String getBookmarkletPageName() {
        return "Bookmarklet.html";
    }

    protected String getBookmarkletPageTemplateName(
        TreeLogger logger,
        LinkerContext context) {
        return getPackageResource(getBookmarkletPageName());
    }

    protected String getBookmarkletTemplateName(
        TreeLogger logger,
        LinkerContext context) {
        return getPackageResource(getBookmarkletName());
    }

    @Override
    protected String getCompilationExtension(
        TreeLogger logger,
        LinkerContext context) throws UnableToCompleteException {
        return ".cache.js";
    }

    @Override
    public String getDescription() {
        return "Bookmarklet Linker";
    }

    @Override
    protected String getModulePrefix(
        TreeLogger logger,
        LinkerContext context,
        String strongName) throws UnableToCompleteException {
        DefaultTextOutput out = new DefaultTextOutput(context.isOutputCompact());

        out.print("(function(){");
        out.newlineOpt();

        // Setup the well-known variables.
        //
        out.print("var $gwt_version = \"" + About.getGwtVersionNum() + "\";");
        out.newlineOpt();
        out.print("var $wnd = window;");
        out.newlineOpt();
        out.print("var $doc = $wnd.document;");
        out.newlineOpt();
        out.print("var $moduleName, $moduleBase;");
        out.newlineOpt();
        out.print("var $strongName = '" + strongName + "';");
        out.newlineOpt();
        out.print("var $stats = null;");
        out.newlineOpt();
        out
            .print("var $sessionId = $wnd.__gwtStatsSessionId ? $wnd.__gwtStatsSessionId : null;");
        out.newlineOpt();
        return out.toString();
    }

    @Override
    protected String getModuleSuffix(TreeLogger logger, LinkerContext context)
        throws UnableToCompleteException {
        DefaultTextOutput out = new DefaultTextOutput(context.isOutputCompact());
        // Generate the call to tell the bootstrap code that we're ready to go.
        out.newlineOpt();
        out.print("if ("
            + context.getModuleFunctionName()
            + ") "
            + context.getModuleFunctionName()
            + ".onScriptLoad(gwtOnLoad);");
        out.newlineOpt();
        out.print("})();");
        out.newlineOpt();

        return out.toString();
    }

    protected String getPackageResource(String resourceName) {
        String name = getClass().getPackage().getName();
        name = name.replace('.', '/');
        name += "/";
        name += resourceName;
        return name;
    }

    /**
     * @return the base path to local scripts. These scripts should be included
     *         in the generated bookmarklet.
     */
    protected String getRelativeScriptsBase() {
        String packageName = getClass().getPackage().getName();
        int idx = packageName.lastIndexOf('.');
        if (idx > 0) {
            packageName = packageName.substring(0, idx);
        }
        packageName = packageName.replaceAll("\\.", "/");
        packageName += "/public/";
        return packageName;
    }

    @Override
    protected String getSelectionScriptTemplate(
        TreeLogger logger,
        LinkerContext context) throws UnableToCompleteException {
        String name = getClass().getName();
        name = name.replace('.', '/');
        name += ".js";
        return name;
    }

    /**
     * Installs stylesheets and scripts
     */
    public StringBuffer injectResources(
        StringBuffer selectionScript,
        ArtifactSet artifacts) {
        // Add external dependencies
        int startPos = selectionScript.indexOf("// __MODULE_STYLES_END__");
        if (startPos != -1) {
            for (StylesheetReference resource : artifacts
                .find(StylesheetReference.class)) {
                String text = generateStylesheetInjector(resource.getSrc());
                selectionScript.insert(startPos, text);
                startPos += text.length();
            }
        }

        startPos = selectionScript.indexOf("// __MODULE_SCRIPTS_END__");
        if (startPos != -1) {
            for (ScriptReference resource : artifacts
                .find(ScriptReference.class)) {
                String text = generateScriptInjector(resource.getSrc());
                selectionScript.insert(startPos, text);
                startPos += text.length();
            }
        }
        return selectionScript;
    }

    @Override
    public ArtifactSet link(
        TreeLogger logger,
        LinkerContext context,
        ArtifactSet artifacts) throws UnableToCompleteException {
        ArtifactSet set = super.link(logger, context, artifacts);
        String bookmarkletCode = buildBookmarkletCode(logger, context);
        SyntheticArtifact bookmarklet = buildBookmarklet(
            logger,
            context,
            bookmarkletCode);
        set.add(bookmarklet);
        SyntheticArtifact bookmarkletPage = buildBookmarkletPage(
            logger,
            context,
            bookmarkletCode);
        set.add(bookmarkletPage);
        return set;
    }

    private String optimizeJavaScript(String name, String program)
        throws IOException,
        JsParserException {
        Reader r = new StringReader(program);
        final JsProgram jsProgram = new JsProgram();
        JsScope topScope = jsProgram.getScope();
        JsName funcName = topScope.declareName(name);
        funcName.setObfuscatable(false);
        SourceInfo sourceInfo = jsProgram
            .createSourceInfoSynthetic(StandardLinkerContext.class);
        JsParser.parseInto(sourceInfo, topScope, jsProgram.getGlobalBlock(), r);

        JsSymbolResolver.exec(jsProgram);
        JsUnusedFunctionRemover.exec(jsProgram);

        JsModVisitor visitor = new JsModVisitor() {
            @Override
            public boolean visit(JsFunction x, JsContext ctx) {
                didChange |= JsStringInterner.exec(
                    jsProgram,
                    x.getBody(),
                    x.getScope(),
                    true);
                return false;
            }
        };
        visitor.accept(jsProgram);
        visitor.didChange();
        JsObfuscateNamer.exec(jsProgram);

        DefaultTextOutput out = new DefaultTextOutput(true);
        JsSourceGenerationVisitor v = new JsSourceGenerationVisitor(out);
        v.accept(jsProgram);
        return out.toString();
    }

    /*
     * This code was copied from the {@link
     * StandardLinkerContext#optimizeJavaScript(TreeLogger, String)} method (GWT
     * v2.0.4).
     */
    protected String optimizeJavaScript(
        TreeLogger logger,
        LinkerContext context,
        String program) throws UnableToCompleteException {
        logger = logger.branch(
            TreeLogger.DEBUG,
            "Attempting to optimize JS",
            null);
        try {
            String name = context.getModuleFunctionName();
            return optimizeJavaScript(name, program);
        } catch (IOException e) {
            logger.log(TreeLogger.ERROR, "Unable to parse JavaScript", e);
            throw new UnableToCompleteException();
        } catch (JsParserException e) {
            logger.log(TreeLogger.ERROR, "Unable to parse JavaScript", e);
            throw new UnableToCompleteException();
        }
    }

}
