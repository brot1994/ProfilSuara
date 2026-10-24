package id.bram.soundprofile;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.List;

/** Tombol di panel Quick Settings: ketuk untuk pindah ke profil berikutnya. */
public class ProfileTile extends TileService {
    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        Store s = new Store(this);
        List<Profile> ps = s.profiles();
        if (ps.isEmpty()) return;
        int idx = -1;
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).id == s.activeId()) idx = i;
        Profile nx = ps.get((idx + 1) % ps.size());
        Engine.activate(this, nx.id, 0);
        update();
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        Store s = new Store(this);
        Profile a = s.profile(s.activeId());
        String name = a == null ? "—" : a.name;
        t.setState(Tile.STATE_ACTIVE);
        t.setIcon(Icon.createWithResource(this, a == null ? R.drawable.ic_stat : a.iconRes()));
        if (Build.VERSION.SDK_INT >= 29) {
            t.setLabel("Profil Suara");
            t.setSubtitle(name);
        } else {
            t.setLabel(name);
        }
        t.updateTile();
    }
}
