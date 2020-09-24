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

public abstract class NoProxyDetector {

    private final NoProxyApiContext context;

    public NoProxyDetector(NoProxyApiContext context) {
        this.context = context;
    }

    public NoProxyApiContext getContext() {
        return this.context;
    }

    public boolean shouldBeBlocked(String ip) {

        AddressInfo addressInfo;
        try {
            addressInfo = AddressInfo.get(this.context, ip);
        } catch (ApiException exception) {
            ApiError apiError = exception.getApiError();
            this.warning("Blad komunikacji z API No.Proxy: " + apiError.getType() + ", " + apiError.getMessage());
            return false;
        }

        boolean block = addressInfo.getSuggestions().isBlock();
        if (block) {
            this.info("Zablokowano adres IP '" + ip + "' [" + addressInfo.getGeneral().getCountry() + ", AS" + addressInfo.getGeneral().getAsn() + "]");
        }

        return block;
    }

    public abstract void warning(String message);

    public abstract void info(String message);
}
