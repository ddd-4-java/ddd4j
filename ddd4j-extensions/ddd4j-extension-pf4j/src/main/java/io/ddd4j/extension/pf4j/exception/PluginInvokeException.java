package io.ddd4j.extension.pf4j.exception;

import org.pf4j.PluginRuntimeException;

@SuppressWarnings("serial")
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PluginInvokeException extends PluginRuntimeException {

    private String pluginId;

    private String extensionId;

    public PluginInvokeException(String pluginId, Throwable cause) {
        super("Plugin '" + pluginId + "' invoke error.", cause);
        this.pluginId = pluginId;
    }

    public PluginInvokeException(String pluginId, String extensionId, Throwable cause) {
        super("Plugin '" + pluginId + "' extensionId '" + extensionId + "' invoke error.", cause);
        this.pluginId = pluginId;
        this.extensionId = extensionId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getExtensionId() {
        return extensionId;
    }

}
