package eu.okaeri.noproxy.minecraft.shared;

import java.util.StringJoiner;

public class NoProxyWebhook {

    private String url;
    private String method = "GET";
    private String content = "";
    private boolean blockedOnly = true;

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isBlockedOnly() {
        return this.blockedOnly;
    }

    public void setBlockedOnly(boolean blockedOnly) {
        this.blockedOnly = blockedOnly;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", NoProxyWebhook.class.getSimpleName() + "[", "]")
                .add("url='" + this.url + "'")
                .add("method='" + this.method + "'")
                .add("content='" + this.content + "'")
                .add("blockedOnly=" + this.blockedOnly)
                .toString();
    }
}
