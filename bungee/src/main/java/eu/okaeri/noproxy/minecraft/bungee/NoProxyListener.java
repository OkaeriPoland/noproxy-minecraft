package eu.okaeri.noproxy.minecraft.bungee;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public class NoProxyListener implements Listener {

    private final NoProxyBungeePlugin plugin;
    private final NoProxyBungee noproxy;

    public NoProxyListener(NoProxyBungeePlugin plugin) {
        this.plugin = plugin;
        this.noproxy = plugin.getNoproxy();
    }

    @EventHandler
    public void handlePreLogin(PreLoginEvent event) {

        String name = event.getConnection().getName();
        SocketAddress socketAddress = event.getConnection().getSocketAddress();
        if (!(socketAddress instanceof InetSocketAddress)) {
            this.noproxy.warning("NoProxy nie wspiera polaczen innych niz te korzystajace z InetSocketAddress: probowano przekazac "
                    + socketAddress.getClass().getSimpleName() + ". Mozliwe, ze probujesz uzywac tej wtyczki z bungee nasluchujacym po unix socket.");
            return;
        }

        String address = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
        List<String> whitelist = this.plugin.getConfig().getStringList("white-list");
        if (whitelist.contains(name) || whitelist.contains(address)) {
            return;
        }

        event.registerIntent(this.plugin);
        if (!this.noproxy.shouldBeBlocked(address, name)) {
            event.completeIntent(this.plugin);
            return;
        }

        event.setCancelled(true);
        event.setCancelReason(TextComponent.fromLegacyText(this.plugin.message("player-info")));
        event.completeIntent(this.plugin);
    }
}
