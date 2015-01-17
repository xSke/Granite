package org.granitepowered.granite;

import org.spongepowered.api.GameVersion;

public class GraniteGameVersion implements GameVersion {

    String version;

    public GraniteGameVersion(String version) {
        this.version = version;
    }

    @Override
    public String getName() {
        return version;
    }

    @Override
    public int compareTo(GameVersion o) {
        return 0;
    }
}
