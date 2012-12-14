if (!window.__MODULE_FUNC__) {
    window.__MODULE_FUNC__ = function(){
        var $wnd = window;
        
        // If non-empty, an alternate base url for this module
        if (!$wnd.____MODULE_NAME___base) {
            $wnd.____MODULE_NAME___base = "./";
        }
        // These variables could be used to re-define the easyXDM provider 
        // and service URL.
        $wnd.____MODULE_NAME___xdmServiceUrl = "";
        $wnd.____MODULE_NAME___xdmProviderUrl = "";
        
        // ---------------- INTERNAL GLOBALS ----------------
        //
        // If non-empty, an alternate base url for this module
        var base = $wnd.____MODULE_NAME___base; // 
        // Cache symbols locally for good obfuscation
        var $doc = document, $stats = null //    
        // A map of properties that were declared in meta tags
        , metaProps = {} // 
        // Maps property names onto sets of legal values for that property.
        , values = [] // 
        // Maps property names onto a function to compute that property.
        , providers = [] //
        // A multi-tier lookup map that uses actual property values to quickly find
        // the strong name of the cache.js file to load.
        , answers = []//
        // Error functions.  Default unset in compiled mode, may be set by meta props.
        , onLoadErrorFunc, propertyErrorFunc; // 
        // end of global vars
        //
        
        // --------------- PROPERTY PROVIDERS --------------- 
        
        // __PROPERTIES_BEGIN__
        // __PROPERTIES_END__
        
        // --------------- CHECK IF THE SCRIPT IS ALREADY LOADED ----------------
        
        // If the module is already loaded then call its "onModuleLoad" method.
        if ($wnd.__MODULE_FUNC__.loading) {
            // Script activation is not finished yet. We just need to wait.
            return;
        }
        
        if ($wnd.__MODULE_FUNC__.gwtOnLoadFunc) {
            // Script is already loaded. We can call it directly.
            $wnd.__MODULE_FUNC__.gwtOnLoadFunc(onLoadErrorFunc, '__MODULE_NAME__', base);
            return;
        }
        
        // ------------------ TRUE GLOBALS ------------------
        
        // Maps to synchronize the loading of styles and scripts; resources are loaded
        // only once, even when multiple modules depend on them.  This API must not
        // change across GWT versions.
        if (!$wnd.__gwt_stylesLoaded) {
            $wnd.__gwt_stylesLoaded = {};
        }
        if (!$wnd.__gwt_scriptsLoaded) {
            $wnd.__gwt_scriptsLoaded = {};
        }
        
        // --------------- EXPOSED FUNCTIONS ----------------
        
        // Called when the compiled script identified by moduleName is done loading.
        // Start to load the required script.
        $wnd.__MODULE_FUNC__.loading = true;
        $wnd.__MODULE_FUNC__.onScriptLoad = function(gwtOnLoadFunc){
            $wnd.__MODULE_FUNC__.loading = false;
            $wnd.__MODULE_FUNC__.gwtOnLoadFunc = gwtOnLoadFunc;
            $wnd.__MODULE_FUNC__.gwtOnLoadFunc(onLoadErrorFunc, '__MODULE_NAME__', base);
        }
        
        // --------------- INTERNAL FUNCTIONS ---------------
        
        // Deferred-binding mapper function.  Sets a value into the several-level-deep
        // answers map. The keys are specified by a non-zero-length propValArray,
        // which should be a flat array target property values. Used by the generated
        // PERMUTATIONS code.
        //
        function unflattenKeylistIntoAnswers(propValArray, value){
            var answer = answers;
            for (var i = 0, n = propValArray.length - 1; i < n; ++i) {
                // lazy initialize an empty object for the current key if needed
                answer = answer[propValArray[i]] || (answer[propValArray[i]] = []);
            }
            // set the final one to the value
            answer[propValArray[n]] = value;
        }
        
        // Computes the value of a given property.  propName must be a valid property
        // name. Used by the generated PERMUTATIONS code.
        //
        function computePropValue(propName){
            var value = providers[propName](), allowedValuesMap = values[propName];
            if (value in allowedValuesMap) {
                return value;
            }
            var allowedValuesList = [];
            for (var k in allowedValuesMap) {
                allowedValuesList[allowedValuesMap[k]] = k;
            }
            if (propertyErrorFunc) {
                propertyErrorFunc(propName, allowedValuesList, value);
            }
            throw null;
        }
        
        // --------------- WINDOW ONLOAD HOOK ---------------
        
        var strongName;
        try {
            // __PERMUTATIONS_BEGIN__
            // Permutation logic
            // __PERMUTATIONS_END__
        } 
        catch (e) {
            // intentionally silent on property failure
            return;
        }
        
        // __MODULE_STYLES_BEGIN__
        // Style resources are injected here to prevent operation aborted errors on ie
        // __MODULE_STYLES_END__
        
        // __MODULE_SCRIPTS_BEGIN__
        // Script resources are injected here
        // __MODULE_SCRIPTS_END__
        
        // Removes the script if it is already exists in this document.
        var script = $doc.getElementById(strongName);
        if (script != null) {
            script.parentNode.removeChild(script);
        }
        var compiledScriptUrl = base + strongName + '.cache.js';
        script = $doc.createElement("script");
        script.setAttribute('src', compiledScriptUrl);
        script.id = strongName;
        $doc.getElementsByTagName('head')[0].appendChild(script);
    }
}
window.__MODULE_FUNC__();
