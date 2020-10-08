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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class NoProxyDetector {

    private static final long DATA_DISCARD_TIME = TimeUnit.SECONDS.toMillis(60);

    private final NoProxyApiContext context;
    private final boolean debug = Boolean.getBoolean("noproxyDebug");

    private final Map<String, AddressInfo> infoMap = new ConcurrentHashMap<>();
    private final Map<String, Long> timeMap = new ConcurrentHashMap<>();
    private long nextDiscard = System.currentTimeMillis() + DATA_DISCARD_TIME;

    public NoProxyDetector(NoProxyApiContext context) {
        this.context = context;
    }

    public NoProxyApiContext getContext() {
        return this.context;
    }

    public boolean shouldBeBlocked(String ip) {

        AddressInfo mAddressInfo = this.infoMap.get(ip);
        Long mTime = this.timeMap.get(ip);
        long now = System.currentTimeMillis();
        this.checkForDiscard();

        if ((mTime != null) && ((now - mTime) < DATA_DISCARD_TIME)) {
            if (this.debug) this.info(ip + " " + mTime + " " + now + " (" + (now - mTime) + ")");
            return (mAddressInfo != null) && mAddressInfo.getSuggestions().isBlock();
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

        if (block) {
            this.info("Zablokowano adres IP '" + ip + "' [" + addressInfo.getGeneral().getCountry() + ", AS" + addressInfo.getGeneral().getAsn() + "]");
        }

        return block;
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

    public abstract void warning(String message);

    public abstract void info(String message);
}
