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
package eu.okaeri.noproxy.minecraft.bungee;

import com.google.common.io.ByteStreams;
import eu.okaeri.noproxy.client.NoProxyApiContext;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;
import java.util.logging.Level;

public class NoProxyBungeePlugin extends Plugin {

    private NoProxyBungee noproxy;
    private NoProxyApiContext context;
    private Configuration config;

    public NoProxyApiContext getContext() {
        return this.context;
    }

    public NoProxyBungee getNoproxy() {
        return this.noproxy;
    }

    public Configuration getConfig() {
        return this.config;
    }

    @Override
    public void onEnable() {

        // save default configuration if config.yml does not exists
        this.saveDefaultConfig();
        try {
            this.loadConfig();
        } catch (IOException exception) {
            this.getLogger().warning("Blad ladowania konfiguracji");
            exception.printStackTrace();
            return;
        }

        // validate configuration and create ApiContext
        Configuration config = this.getConfig();
        String token = config.getString("token");

        if ((token == null) || "".equals(token)) {
            this.getLogger().log(Level.SEVERE, "Nie znaleziono poprawnie ustawionej wartosci 'token' w config.yml," +
                    " nalezy ja ustawic i zrestartowac serwer.");
            return;
        }

        // create context
        this.context = new NoProxyApiContext(token);

        // create noproxy
        this.noproxy = new NoProxyBungee(this);

        // custom api url
        String apiUrl = this.getConfig().getString("api-url");
        if ((apiUrl != null) && !"".equals(apiUrl)) {
            this.context.setMainUrl(apiUrl);
        }

        // listeners
        this.getProxy().getPluginManager().registerListener(this, new NoProxyListener(this));
    }

    protected String message(String key, String... params) {
        String message = this.getConfig().getString("message-" + key);
        if (message == null) throw new IllegalArgumentException("message for " + key + " not found");
        message = message.replace("{PREFIX}", this.getConfig().getString("message-prefix"));
        message = ChatColor.translateAlternateColorCodes('&', message);
        for (int i = 0; i < params.length; i++) {
            message = message.replace("{" + i + "}", params[i]);
        }
        return message;
    }

    public void loadConfig() throws IOException {
        File configFile = this.getConfigFile();
        this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
    }

    private File getConfigFile() {
        return new File(this.getDataFolder(), "config.yml");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void saveDefaultConfig() {

        if (!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
        }

        File configFile = new File(this.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            return;
        }

        try {
            configFile.createNewFile();
            try (InputStream is = this.getResourceAsStream("config.yml");
                 OutputStream os = new FileOutputStream(configFile)) {
                ByteStreams.copy(is, os);
            }
        } catch (IOException exception) {
            throw new RuntimeException("Unable to create configuration file", exception);
        }
    }
}
