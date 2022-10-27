package com.adobe.marketing.mobile.simplification.internal;

import android.app.Application;

import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.LoggingMode;

import java.util.List;
import java.util.Map;

public class InitOptions {
    private Application application;
    private String appId;
    private LoggingMode logLevel;
    private Map<String, Object> configurationMap;
    private boolean disableLifecycleStart;
    private List<Class<? extends Extension>> thirdPartyExtensions;

    private InitOptions(Application application, String appId, LoggingMode logLevel, Map<String, Object> configurationMap,
                        boolean disableLifecycleStart, List<Class<? extends Extension>> thirdPartyExtensions) {
        this.application = application;
        this.appId = appId;
        this.logLevel = logLevel;
        this.configurationMap = configurationMap;
        this.disableLifecycleStart = disableLifecycleStart;
        this.thirdPartyExtensions = thirdPartyExtensions;
    }

    public Application getApplication() {
        return application;
    }

    public String getAppId() {
        return appId;
    }

    public LoggingMode getLogLevel() {
        return logLevel;
    }

    public boolean disableLifecycleStart() {
        return disableLifecycleStart;
    }

    public List<Class<? extends Extension>> getThirdPartyExtensions() {
        return this.thirdPartyExtensions;
    }

    public static class Builder {
        private Application application;
        private String appId;
        private LoggingMode logLevel;
        private Map<String, Object> configurationMap;
        private boolean disableLifecycleStart;
        private List<Class<? extends Extension>> thirdPartyExtensions;

        public Builder() {
        }

        public Builder application(Application application) {
            this.application = application;
            return this;
        }

        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public Builder logLevel(LoggingMode logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder updateConfiguration(Map<String, Object> configurationMap) {
            this.configurationMap = configurationMap;
            return this;
        }

        public Builder disableLifecycleStart(boolean disableLifecycleStart) {
            this.disableLifecycleStart = disableLifecycleStart;
            return this;
        }

        public Builder thirdPartyExtensions(List<Class<? extends Extension>> thirdPartyExtensions) {
            this.thirdPartyExtensions = thirdPartyExtensions;
            return this;
        }

        public InitOptions build() {
            return new InitOptions(application, appId, logLevel, configurationMap, disableLifecycleStart, thirdPartyExtensions);
        }


    }
}
