package com.plugin.xray;
import com.plugin.xray.IXrayCallback;

interface IXrayStatus {
    boolean isRunning();
    void registerCallback(IXrayCallback cb);
    void unregisterCallback(IXrayCallback cb);
}
