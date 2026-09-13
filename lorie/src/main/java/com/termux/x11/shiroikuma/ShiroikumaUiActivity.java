package com.termux.x11.shiroikuma;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.termux.x11.R;

/**
 * The host of the 白い熊 Termux X11 UI page: a black toolbar titled 「白い熊 Termux X11 UI」 over
 * {@link ShiroikumaUiFragment}. Reached from the launcher shortcut, a long-press on the extra-keys
 * bar's PREFERENCES key or on the PREFERENCES button of the not-connected screen, and the first row
 * of the app's own Preferences screen.
 *
 * <p>In the sharedUid flavour it runs — like every other component of this app — inside Termux's
 * process ({@code android:process="com.termux"}, see {@code lorie-app/src/sharedUid/AndroidManifest.xml}),
 * which is what lets an import's committed preferences be seen at once by the X window.
 */
public class ShiroikumaUiActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shiroikuma_ui);

        Toolbar toolbar = findViewById(R.id.shiroikuma_toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.shiroikuma_ui_title);
        }
        Drawable nav = toolbar.getNavigationIcon();
        if (nav != null)
            nav.setTint(ShiroikumaDialogs.YELLOW);

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.shiroikuma_fragment, new ShiroikumaUiFragment())
                    .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
