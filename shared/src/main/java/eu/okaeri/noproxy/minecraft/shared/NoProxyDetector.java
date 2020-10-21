/*
 * OK! No.Proxy Plugin
 * Copyright (C) 2020 Okaeri
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package eu.okaeri.noproxy.minecraft.shared;

import eu.okaeri.noproxy.client.ApiError;
import eu.okaeri.noproxy.client.ApiException;
import eu.okaeri.noproxy.client.NoProxyApiContext;
import eu.okaeri.noproxy.client.info.AddressInfo;
import eu.okaeri.noproxy.client.okhttp3.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class NoProxyDetector {

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String WEBHOOK_USER_AGENT = "NoProxy-Minecraft-Webhook/1.1";
    private static final long DATA_DISCARD_TIME = TimeUnit.SECONDS.toMillis(60);
    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();

    private final NoProxyApiContext context;
    private final boolean debug = Boolean.getBoolean("noproxyDebug");
    private final boolean webhookAlways = Boolean.getBoolean("noproxyWebhookAlways");

    private final Map<String, AddressInfo> infoMap = new ConcurrentHashMap<>();
    private final Map<String, Long> timeMap = new ConcurrentHashMap<>();
    private final List<NoProxyWebhook> webhookList = new ArrayList<>();
    private long nextDiscard = System.currentTimeMillis() + DATA_DISCARD_TIME;

    public NoProxyDetector(NoProxyApiContext context) {
        this.context = context;
    }

    public NoProxyApiContext getContext() {
        return this.context;
    }

    public void addWebhook(NoProxyWebhook webhook) {
        if (webhook == null) throw new IllegalArgumentException("webhook cannot be null");
        if (webhook.getUrl() == null) throw new IllegalArgumentException("webhook.url cannot be null");
        if (webhook.getMethod() == null) throw new IllegalArgumentException("webhook.method cannot be null");
        if (!"POST".equals(webhook.getMethod()) && !"GET".equals(webhook.getMethod())) throw new IllegalArgumentException("webhook.method is not POST or GET");
        this.webhookList.add(webhook);
    }

    public List<NoProxyWebhook> getWebhookList() {
        return this.webhookList;
    }

    public boolean shouldBeBlocked(String ip, String name) {

        AddressInfo mAddressInfo = this.infoMap.get(ip);
        Long mTime = this.timeMap.get(ip);
        long now = System.currentTimeMillis();
        this.checkForDiscard();

        if ((mTime != null) && ((now - mTime) < DATA_DISCARD_TIME)) {
            if (this.debug) this.info(ip + " " + mTime + " " + now + " (" + (now - mTime) + ")");
            boolean block = (mAddressInfo != null) && mAddressInfo.getSuggestions().isBlock();
            if (this.webhookAlways) {
                this.dispatchAsync(() -> this.dispatchWebhooks(mAddressInfo, block, Collections.singletonMap("nick", name)));
            }
            return block;
        }

        AddressInfo addressInfo;
        try {
            addressInfo = AddressInfo.get(this.context, ip);
        } catch (ApiException exception) {
            ApiError apiError = exception.getApiError();
            this.timeMap.put(ip, now);
            this.warning("Blad komunikacji z API No.Proxy: " + exception.getMessage());
            if (this.debug) exception.printStackTrace();
            return false;
        }

        boolean block = addressInfo.getSuggestions().isBlock();
        this.timeMap.put(ip, now);
        this.infoMap.put(ip, addressInfo);
        this.dispatchAsync(() -> this.dispatchWebhooks(addressInfo, block, Collections.singletonMap("nick", name)));

        if (block) {
            this.info("Zablokowano adres IP '" + ip + "' [" + addressInfo.getGeneral().getCountry() + ", AS" + addressInfo.getGeneral().getAsn() + "]");
        }

        return block;
    }

    public boolean shouldBeBlocked(String ip) {
        return this.shouldBeBlocked(ip, null);
    }

    private void checkForDiscard() {

        long now = System.currentTimeMillis();

        if (now < this.nextDiscard) {
            return;
        }

        for (Map.Entry<String, Long> entry : this.timeMap.entrySet()) {
            String key = entry.getKey();
            Long value = entry.getValue();
            if ((now - value) < DATA_DISCARD_TIME) {
                continue;
            }
            if (this.debug) this.info("discard " + key + " " + value + " " + now);
            this.timeMap.remove(key);
            this.infoMap.remove(key);
        }

        this.nextDiscard = now + DATA_DISCARD_TIME;
    }

    public void dispatchWebhooks(AddressInfo info, boolean block, Map<String, String> additionalVariables) {
        if (this.debug) this.info("Checking for webhooks (" + this.webhookList.size() + ")");
        for (NoProxyWebhook webhook : this.webhookList) {
            if (this.debug) this.info("Analzying (block=" + block + "): " + webhook);
            if (webhook.isBlockedOnly()) {
                if (block) {
                    this.dispatchWebhook(info, webhook, additionalVariables);
                }
            } else {
                this.dispatchWebhook(info, webhook, additionalVariables);
            }
        }
    }

    public void dispatchWebhook(AddressInfo info, NoProxyWebhook webhook, Map<String, String> additionalVariables) {
        if (this.debug) this.info("Preparing webhook for " + webhook);
        Map<String, String> addressInfoMap = this.addressInfoToMap(info);
        String method = webhook.getMethod();
        String url = this.replaceVariables(webhook.getUrl(), info, true, false, addressInfoMap, additionalVariables);
        String body = this.replaceVariables(webhook.getContent(), info, false, true, addressInfoMap, additionalVariables);
        Request.Builder builder = new Request.Builder()
                .header("User-Agent", WEBHOOK_USER_AGENT)
                .url(url);
        if ("GET".equals(method)) {
            builder = builder.get();
        }
        else if ("POST".equals(method)) {
            builder = builder.post(RequestBody.create(body, MEDIA_TYPE_JSON));
        } else {
            throw new IllegalArgumentException("Tried to dispatch webhook with unknown method: " + method);
        }
        try {
            if (this.debug) this.info("Sending webhook to '" + url + "'");
            Request request = builder.build();
            Response response = OK_HTTP_CLIENT.newCall(request).execute();
            ResponseBody responseBody = response.body();
            if (this.debug) this.info("Response body '" + url + "': " + (responseBody == null ? "null" : responseBody.string()));
            response.close();
        }
        catch (Exception exception) {
            this.warning("Webhook (" + url + ") failed: " + exception.getMessage());
            if (this.debug) exception.printStackTrace();
        }
    }

    private Map<String, String> addressInfoToMap(AddressInfo info) {
        Map<String, String> map = new HashMap<>();
        map.put("general.ip", info.getGeneral().getIp());
        map.put("general.asn", String.valueOf(info.getGeneral().getAsn()));
        map.put("general.provider", info.getGeneral().getProvider());
        map.put("general.country", info.getGeneral().getCountry());
        map.put("risks.total", String.valueOf(info.getRisks().getTotal()));
        map.put("risks.proxy", String.valueOf(info.getRisks().isProxy()));
        map.put("risks.country", String.valueOf(info.getRisks().isCountry()));
        map.put("risks.asn", String.valueOf(info.getRisks().isAsn()));
        map.put("risks.provider", String.valueOf(info.getRisks().isProvider()));
        map.put("score.noproxy", String.valueOf(info.getScore().getNoproxy()));
        map.put("score.abuseipdb", String.valueOf(info.getScore().getAbuseipdb()));
        map.put("suggestions.verify", String.valueOf(info.getSuggestions().isVerify()));
        map.put("suggestions.block", String.valueOf(info.getSuggestions().isBlock()));
        return map;
    }

    @SafeVarargs
    private final String replaceVariables(String text, AddressInfo info, boolean urlEncode, boolean quotesEscape, Map<String, String>... variableSets) {
        for (Map<String, String> variables : variableSets) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (quotesEscape) {
                    value = value.replaceAll("\"","\\\\\"");
                }
                if (urlEncode) {
                    try {
                        value = URLEncoder.encode(value, "UTF-8");
                    } catch (UnsupportedEncodingException exception) {
                        this.warning("Failed to encode '" + value + "': " + exception.getMessage());
                    }
                }
                text = text.replace("{" + key + "}", value);
            }
        }
        return text;
    }

    public abstract void warning(String message);

    public abstract void info(String message);

    public abstract void dispatchAsync(Runnable runnable);
}
