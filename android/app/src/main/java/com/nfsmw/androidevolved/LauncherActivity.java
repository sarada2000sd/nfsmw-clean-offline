package com.nfsmw.androidevolved;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher in the style of the MW and Carbon ports: game files and JUGAR on the left, options on the
 * right (Vulkan driver, resolution, frame cap, widescreen, compatibility).
 */
public final class LauncherActivity extends Activity {
    // Launcher accent: Underground orange/red (the NFSU2 port is green).
    private static final int ACCENT = 0xFFFF7A1A;
    /** {label, guest language name, LANGUAGES/<file>.bin}. */
    private static final String[][] LANGUAGES = {
        {"Español", "Spanish", "Spanish"}, {"English", "English", "English"},
        {"Français", "French", "French"}, {"Deutsch", "German", "German"},
        {"Italiano", "Italian", "Italian"}, {"Nederlands", "Dutch", "Dutch"},
        {"Svenska", "Swedish", "Swedish"}, {"Dansk", "Danish", "Danish"},
        {"Suomi", "Finnish", "Finnish"}, {"Polski", "Polish", "Polish"},
        {"한국어", "Korean", "Korean"}, {"中文", "Chinese", "Chinese"}
    };

    private LinearLayout checksList, optionsList;
    private TextView status;
    private Button play, access;
    private boolean bundleInstalling;
    private boolean bundleFailed;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(buildScreen());
        // First launch: the three flags come before any option.
        if (!Lang.chosen(this)) showLanguagePicker();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected void onPause() {
        super.onPause();
    }

    // ---- Screen ------------------------------------------------------------------------------------------

    private View buildScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF0E0604, 0xFF2E0B05, 0xFF0E0604}));
        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(dp(24), dp(10), dp(24), dp(10));
        screen.addView(columns, new FrameLayout.LayoutParams(-1, -1));

        // Left: logo, game files and JUGAR.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(true);
        leftScroll.addView(left);
        columns.addView(leftScroll, new LinearLayout.LayoutParams(0, -1, 1.1f));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.icono);
        logo.setAdjustViewBounds(true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(label("Need for Speed Most Wanted", 18, Color.WHITE, true));
        titles.addView(label(tr("Offline single-player edition"), 12, 0x99FFFFFF, false));
        header.addView(titles);
        left.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = card();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(8);
        left.addView(card, cardParams);
        card.addView(label(tr("ARCHIVOS DEL JUEGO"), 12, ACCENT, true));
        status = label("", 13, 0xDDFFFFFF, false);
        status.setPadding(0, dp(4), 0, dp(6));
        card.addView(status);
        checksList = new LinearLayout(this);
        checksList.setOrientation(LinearLayout.VERTICAL);
        card.addView(checksList);

        play = actionButton(tr("JUGAR"), true);
        play.setOnClickListener(view -> play());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(-1, dp(54));
        playParams.topMargin = dp(8);
        left.addView(play, playParams);

        access = actionButton(tr("Permitir acceso a memoria interna/nfsmw"), false);
        access.setOnClickListener(view -> requestAccess());
        LinearLayout.LayoutParams accessParams = new LinearLayout.LayoutParams(-1, dp(46));
        accessParams.topMargin = dp(10);
        left.addView(access, accessParams);


        // Right: options.
        LinearLayout right = card();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1f);
        rightParams.leftMargin = dp(24);
        columns.addView(right, rightParams);
        right.addView(label(tr("OPCIONES"), 12, ACCENT, true));
        TextView note = label(tr("Se aplican al pulsar JUGAR."), 12, 0x88FFFFFF, false);
        note.setPadding(0, dp(2), 0, dp(4));
        right.addView(note);
        ScrollView scroll = new ScrollView(this);
        optionsList = new LinearLayout(this);
        optionsList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(optionsList);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return screen;
    }

    private void refresh() {
        boolean allowed = allowed();
        File root = gameRoot();
        checksList.removeAllViews();
        if (allowed && !bundleFailed && BundledGameAssets.hasBundle(this) &&
                !BundledGameAssets.isInstalled(this, root)) {
            addCheck(tr("Game files included in this APK"), false, true);
            status.setText(tr("Preparing the game files from this APK. Keep the app open."));
            play.setEnabled(false);
            play.setAlpha(.45f);
            access.setVisibility(View.GONE);
            refreshOptions();
            startBundleInstall(root);
            return;
        }
        boolean exe = allowed && (new File(root, "speed.exe").isFile() || new File(root, "Speed.exe").isFile());
        addCheck("Speed.exe", exe, true);
        StringBuilder missing = new StringBuilder();
        for (String folder : new String[] {"GLOBAL", "TRACKS", "CARS", "FRONTEND", "LANGUAGES", "SOUND", "MOVIES", "NIS"})
            if (!(allowed && new File(root, folder).isDirectory())) missing.append(missing.length() == 0 ? "" : ", ").append(folder);
        addCheck(missing.length() == 0 ? tr("Carpetas del juego") : tr("Faltan carpetas: %s", missing), missing.length() == 0, true);
        boolean language = allowed && !languages().isEmpty();
        boolean d3dx = allowed && new File(root, "d3dx9_26.dll").isFile();
        addCheck("d3dx9_26.dll (DirectX runtime)", d3dx, true);
        if (exe && !language) addCheck(tr("Falta LANGUAGES/<idioma>.bin"), false, true);
        boolean ready = exe && language && d3dx && missing.length() == 0;
        if (allowed && !root.isDirectory()) root.mkdirs();  // Show the player where the files go.
        String nested = allowed && !exe ? nestedGameFolder(root) : null;
        status.setText(!allowed ? tr("Permite el acceso a archivos para leer memoria interna/nfsmw.")
                : ready ? tr("Memoria interna/nfsmw")
                : nested != null ? tr("Los archivos están en «%s»: mueve su contenido directamente a memoria interna/nfsmw.", nested)
                : tr("Copia los archivos de tu juego (Speed.exe y sus carpetas) dentro de:\n%s", root.getAbsolutePath()));
        play.setEnabled(ready);
        play.setAlpha(ready ? 1f : .45f);
        access.setVisibility(allowed ? View.GONE : View.VISIBLE);
        refreshOptions();
    }

    private void startBundleInstall(File root) {
        if (bundleInstalling || bundleFailed) return;
        bundleInstalling = true;
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setTitle(tr("Preparing Most Wanted"));
        dialog.setMessage(tr("Installing the game files included in this APK. Please keep the app open."));
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setIndeterminate(false);
        dialog.setMax(1000);
        dialog.setProgress(0);
        dialog.setCancelable(false);
        dialog.show();

        Thread worker = new Thread(() -> {
            try {
                BundledGameAssets.install(getApplicationContext(), root, (copied, total) -> {
                    final int value = total <= 0 ? 0 : (int)Math.min(1000, copied * 1000L / total);
                    runOnUiThread(() -> {
                        if (!isFinishing() && dialog.isShowing()) dialog.setProgress(value);
                    });
                });
                runOnUiThread(() -> {
                    bundleInstalling = false;
                    if (dialog.isShowing()) dialog.dismiss();
                    if (!isFinishing()) {
                        message(tr("Game files ready"), tr("The bundled game data has been installed. Press JUGAR to start."));
                        refresh();
                    }
                });
            } catch (Exception exception) {
                final String detail = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                runOnUiThread(() -> {
                    bundleInstalling = false;
                    bundleFailed = true;
                    if (dialog.isShowing()) dialog.dismiss();
                    if (!isFinishing()) {
                        status.setText(tr("Game data installation failed."));
                        message(tr("Could not prepare the game"), tr("%s\nFree enough storage and reopen the app to retry.", detail));
                    }
                });
            }
        }, "NFSMW-Asset-Installer");
        worker.start();
    }

    private void refreshOptions() {
        optionsList.removeAllViews();
        optionsList.addView(optionRow(tr("Idioma del launcher"), Lang.name(this), this::showLanguagePicker));
        optionsList.addView(optionRow(tr("Idioma del juego"), languageLabel(), this::chooseLanguage));
        for (GameOptions.Option option : GameOptions.ALL)
            optionsList.addView(optionRow(tr(option.title), tr(option.label(GameOptions.get(this, option.key))), () -> chooseOption(option)));
    }

    // ---- Options ---------------------------------------------------------------------------------------

    /** A copy one folder too deep (nfsmw/<game folder>/Speed.exe), a common mistake when copying. */
    private static String nestedGameFolder(File root) {
        File[] children = root.listFiles();
        if (children == null) return null;
        for (File child : children)
            if (child.isDirectory() && (new File(child, "Speed.exe").isFile() || new File(child, "speed.exe").isFile()))
                return "nfsmw/" + child.getName();
        return null;
    }

    private String tr(String spanish, Object... args) { return Lang.t(this, spanish, args); }

    /** Three flags (Spanish, English, Portuguese): the launcher texts follow the choice. */
    private void showLanguagePicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(12));
        box.addView(label("Elige tu idioma · Choose your language · Escolha seu idioma", 15, Color.WHITE, true));
        LinearLayout flags = new LinearLayout(this);
        flags.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flagsParams = new LinearLayout.LayoutParams(-1, -2);
        flagsParams.topMargin = dp(14);
        box.addView(flags, flagsParams);
        TextView hint = label(tr("Puedes cambiarlo después en Opciones."), 12, 0x99FFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        box.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(box).setCancelable(Lang.chosen(this)).create();
        for (String[] choice : Lang.CHOICES) {
            LinearLayout flag = new LinearLayout(this);
            flag.setOrientation(LinearLayout.VERTICAL);
            flag.setGravity(Gravity.CENTER);
            flag.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable bg = new GradientDrawable();
            boolean current = Lang.chosen(this) && Lang.code(this).equals(choice[0]);
            bg.setColor(current ? 0x44FF7A1A : 0x16FFFFFF);
            bg.setStroke(dp(current ? 2 : 1), current ? ACCENT : 0x44FFFFFF);
            bg.setCornerRadius(dp(14));
            flag.setBackground(bg);
            TextView emoji = label(choice[1], 46, Color.WHITE, false);
            emoji.setGravity(Gravity.CENTER);
            flag.addView(emoji);
            TextView name = label(choice[2], 15, Color.WHITE, true);
            name.setGravity(Gravity.CENTER);
            flag.addView(name);
            flag.setClickable(true);
            flag.setContentDescription(choice[2]);
            flag.setOnClickListener(v -> {
                Lang.set(this, choice[0]);
                dialog.dismiss();
                setContentView(buildScreen());
                refresh();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            params.leftMargin = params.rightMargin = dp(6);
            flags.addView(flag, params);
        }
        dialog.show();
    }

    private List<String[]> languages() {
        List<String[]> found = new ArrayList<>();
        File root = gameRoot();
        for (String[] language : LANGUAGES)
            if (new File(root, "LANGUAGES/" + language[2] + ".bin").isFile()) found.add(language);
        return found;
    }

    private String languageLabel() {
        String current = GameOptions.language(this);
        for (String[] language : LANGUAGES) if (language[1].equals(current)) return language[0];
        return current;
    }

    private void chooseLanguage() {
        List<String[]> found = allowed() ? languages() : new ArrayList<>();
        if (found.isEmpty()) { message(tr("Idioma del juego"), tr("No se encontraron idiomas en memoria interna/nfsmw/LANGUAGES.")); return; }
        String[] names = new String[found.size()];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = found.get(i)[0];
            if (found.get(i)[1].equals(GameOptions.language(this))) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(tr("Idioma del juego"))
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    GameOptions.prefs(this).edit().putString(GameOptions.LANGUAGE, found.get(which)[1]).apply();
                    dialog.dismiss(); refreshOptions();
                }).setNegativeButton(tr("Cancelar"), null).show();
    }

    private void chooseOption(GameOptions.Option option) {
        String current = GameOptions.get(this, option.key);
        int checked = 0;
        for (int i = 0; i < option.values.length; i++) if (option.values[i].equals(current)) checked = i;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(tr(option.title))
                .setSingleChoiceItems(Lang.t(this, option.labels), checked, (dialog, which) -> {
                    GameOptions.set(this, option.key, option.values[which]);
                    dialog.dismiss(); refreshOptions();
                }).setNegativeButton(tr("Cancelar"), null).show();
    }

    // ---- Launch ----------------------------------------------------------------------------------------

    private void play() { launch(); }

    private void launch() {
        // Each launch uses a fresh guest machine in the dedicated :game process.
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (android.app.ActivityManager.RunningAppProcessInfo process : processes)
            if (process.uid == android.os.Process.myUid() && process.processName.equals(getPackageName() + ":game"))
                android.os.Process.killProcess(process.pid);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int[] size = GameOptions.renderSize(this, Math.max(metrics.widthPixels, metrics.heightPixels),
                Math.min(metrics.widthPixels, metrics.heightPixels));
        startActivity(new Intent(this, GameActivity.class)
                .putExtra("language", GameOptions.language(this))
                .putExtra("renderWidth", size[0]).putExtra("renderHeight", size[1])
                .putExtra("frameCap", Integer.parseInt(GameOptions.get(this, GameOptions.FPS)))
                .putExtra("widescreen", GameOptions.get(this, GameOptions.WIDESCREEN).equals("on"))
                .putExtra("minimapTop", GameOptions.get(this, GameOptions.MINIMAP).equals("top"))
                .putExtra("backBuffers", Integer.parseInt(GameOptions.get(this, GameOptions.LATENCY)))
                .putExtra("cpuTextures", GameOptions.get(this, GameOptions.TEXTURES).equals("cpu"))
                .putExtra("padJoystick", GameOptions.get(this, GameOptions.GAMEPAD).equals("directinput"))
                );
    }

    // ---- Storage ---------------------------------------------------------------------------------------

    private static File gameRoot() { return new File(Environment.getExternalStorageDirectory(), "nfsmw"); }

    private boolean allowed() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
            : checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30)
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
        else requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        refresh();
    }

    @Override protected void onDestroy() { super.onDestroy(); }

    // ---- Widgets ---------------------------------------------------------------------------------------

    private void addCheck(String name, boolean ok, boolean required) {
        TextView row = label((ok ? "✓  " : required ? "✗  " : "!  ") + name + (ok || required ? "" : tr(" · opcional")), 13,
                ok ? 0xFF7CD992 : required ? 0xFFFF6B6B : 0xFFFFC857, false);
        row.setPadding(0, dp(2), 0, dp(2));
        checksList.addView(row);
    }

    private View optionRow(String title, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x16FF7A1A);
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);
        row.setClickable(true);
        row.setOnClickListener(v -> onClick.run());
        row.addView(label(title, 14, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView current = label(value + "  ›", 13, ACCENT, true);
        current.setGravity(Gravity.END);
        row.addView(current, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(5);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1C0D08);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x44FF7A1A);
        card.setBackground(bg);
        return card;
    }

    private Button actionButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(primary ? 0xFF1F0802 : Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 22 : 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setLetterSpacing(primary ? .12f : 0f);
        GradientDrawable bg = primary
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFFFFB13D, 0xFFE3261B})
                : new GradientDrawable();
        if (!primary) { bg.setColor(0x22FFFFFF); bg.setStroke(dp(1), 0x55FFFFFF); }
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        return b;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        if (text == null) text = "";
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) { t.setTypeface(Typeface.DEFAULT_BOLD); t.setLetterSpacing(.06f); }
        return t;
    }

    private void message(String title, String text) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(text).setPositiveButton(tr("Aceptar"), null).show();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
