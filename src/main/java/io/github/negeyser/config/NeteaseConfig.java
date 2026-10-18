/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.config;

/**
 * Small, dependency-free configuration holder for the extension.
 */
public final class NeteaseConfig {

    private boolean onlyNeteaseClients;
    private String disconnectMessage = "This server only accepts NetEase clients.";
    private boolean debugMode;
    private boolean requireObtEnvironment;
    private int resourcePackChunkDelayMs = 200;
    private boolean allowPcClients;
    private boolean allowSingleChain;
    private java.util.Set<String> trustedRelayAddresses = java.util.Set.of();
    private int singleChainPackStackDelayMs = 1000;
    private boolean filterUnknownItems = true;
    private boolean stripItemNbt = false;
    private java.util.Set<String> extraItemNbt = java.util.Set.of();
    private boolean translateNeteaseNames = false;

    public boolean onlyNeteaseClients() {
        return onlyNeteaseClients;
    }

    public String disconnectMessage() {
        return disconnectMessage;
    }

    public boolean debugMode() {
        return debugMode;
    }

    public boolean requireObtEnvironment() {
        return requireObtEnvironment;
    }

    public int resourcePackChunkDelayMs() {
        return resourcePackChunkDelayMs;
    }

    public boolean allowPcClients() {
        return allowPcClients;
    }

    public boolean allowSingleChain() {
        return allowSingleChain;
    }

    public java.util.Set<String> trustedRelayAddresses() {
        return trustedRelayAddresses;
    }

    public int singleChainPackStackDelayMs() {
        return singleChainPackStackDelayMs;
    }

    public boolean filterUnknownItems() {
        return filterUnknownItems;
    }

    public boolean stripItemNbt() {
        return stripItemNbt;
    }

    public java.util.Set<String> extraItemNbt() {
        return extraItemNbt;
    }

    public boolean translateNeteaseNames() {
        return translateNeteaseNames;
    }

    void setOnlyNeteaseClients(boolean value) {
        this.onlyNeteaseClients = value;
    }

    void setDisconnectMessage(String value) {
        this.disconnectMessage = value;
    }

    void setDebugMode(boolean value) {
        this.debugMode = value;
    }

    void setRequireObtEnvironment(boolean value) {
        this.requireObtEnvironment = value;
    }

    void setResourcePackChunkDelayMs(int value) {
        this.resourcePackChunkDelayMs = value;
    }

    void setAllowPcClients(boolean value) {
        this.allowPcClients = value;
    }

    void setAllowSingleChain(boolean value) {
        this.allowSingleChain = value;
    }

    void setTrustedRelayAddresses(java.util.Set<String> value) {
        this.trustedRelayAddresses = value;
    }

    void setSingleChainPackStackDelayMs(int value) {
        this.singleChainPackStackDelayMs = value;
    }

    void setFilterUnknownItems(boolean value) {
        this.filterUnknownItems = value;
    }

    void setStripItemNbt(boolean value) {
        this.stripItemNbt = value;
    }

    void setExtraItemNbt(java.util.Set<String> value) {
        this.extraItemNbt = value;
    }

    void setTranslateNeteaseNames(boolean value) {
        this.translateNeteaseNames = value;
    }
}
