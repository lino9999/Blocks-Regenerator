package dev.lino.blocksregenerator.bukkit;

import dev.lino.blocksregenerator.integration.ClaimsService;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class RegionPermissions {
    private final ClaimsService claims;

    public RegionPermissions(ClaimsService claims) {
        this.claims = claims;
    }

    public boolean claimsAllow(Region region, Player player, Location location, ClaimsService.Action action) {
        return !region.setting(RegionSetting.RESPECT_CLAIMS) || claims.allowed(player, location, action);
    }

    public boolean actionAllowed(Region region, RegionSetting setting, Player player, Location location, ClaimsService.Action action) {
        return claimsAllow(region, player, location, action)
                && (region.setting(setting) || region.global() && region.setting(RegionSetting.RESPECT_CLAIMS)
                && claims.grants(player, location));
    }
}
