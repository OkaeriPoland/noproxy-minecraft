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
package eu.okaeri.noproxy.minecraft.bukkit;

import eu.okaeri.noproxy.client.NoProxyApiContext;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class NoProxyBukkitPlugin extends JavaPlugin {

    private NoProxyBukkit noproxy;
    private NoProxyApiContext context;

    public NoProxyApiContext getContext() {
        return this.context;
    }

    public NoProxyBukkit getNoproxy() {
        return this.noproxy;
    }

    @Override
    public void onEnable() {

        // save default configuration if config.yml does not exists
        this.saveDefaultConfig();

        // validate configuration and create ApiContext
        FileConfiguration config = this.getConfig();
        String token = config.getString("token");

        if ((token == null) || "".equals(token)) {
            this.getLogger().log(Level.SEVERE, "Nie znaleziono poprawnie ustawionej wartosci 'token' w config.yml," +
                    " nalezy ja ustawic i zrestartowac serwer.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // create context
        this.context = new NoProxyApiContext(token);

        // create noproxy
        this.noproxy = new NoProxyBukkit(this);

        // custom api url
        String apiUrl = this.getConfig().getString("api-url");
        if ((apiUrl != null) && !"".equals(apiUrl)) {
            this.context.setMainUrl(apiUrl);
        }

        // listeners
        this.getServer().getPluginManager().registerEvents(new NoProxyListener(this), this);
    }

    protected String message(String key, String... params) {
        String message = this.getConfig().getString("message-" + key);
        Validate.notNull(message, "message for " + key + " not found");
        message = message.replace("{PREFIX}", this.getConfig().getString("message-prefix"));
        message = ChatColor.translateAlternateColorCodes('&', message);
        for (int i = 0; i < params.length; i++) {
            message = message.replace("{" + i + "}", params[i]);
        }
        return message;
    }
}
