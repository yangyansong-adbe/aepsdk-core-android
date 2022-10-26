package com.adobe.marketing.mobile.simplification.internal;

import android.app.Application;

import com.adobe.marketing.mobile.LoggingMode;

import java.util.Map;

public class InitOptions {
    private Application application;
    private String appId;
    private LoggingMode logLevel;
    private Map<String, Object> configurationMap;
    private boolean disableLifecycleStart;

    private InitOptions(Application application, String appId, LoggingMode logLevel, Map<String, Object> configurationMap,
                        boolean disableLifecycleStart) {
        this.application = application;
        this.appId = appId;
        this.logLevel = logLevel;
        this.configurationMap = configurationMap;
        this.disableLifecycleStart = disableLifecycleStart;
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

    public static class Builder {
        private Application application;
        private String appId;
        private LoggingMode logLevel;
        private Map<String, Object> configurationMap;
        private boolean disableLifecycleStart;

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

        public InitOptions build() {
            return new InitOptions(application, appId, logLevel, configurationMap, disableLifecycleStart);
        }


    }
}
