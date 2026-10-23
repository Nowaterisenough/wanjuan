package io.wanjuan.app.releaseprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Platform-only probe: no Kotlin, AndroidX or desugared libraries can shadow the R8 target. */
public final class ReleaseProbe extends Instrumentation {
    private Bundle arguments;
    private Activity activity;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            String[] providers = {
                "org.chromium.net.impl.HttpEngineNativeProvider",
                "org.chromium.net.impl.NativeCronetProvider",
                "org.chromium.net.impl.JavaCronetProvider"
            };
            for (String name : providers) {
                Object provider = context.getClassLoader().loadClass(name)
                    .getConstructor(Context.class).newInstance(context);
                if (!name.equals(provider.getClass().getName())) throw new AssertionError(name);
            }
            result.putInt("providers_verified", providers.length);
            if (arguments != null && "true".equals(arguments.getString("checkUpdate"))) {
                verifyUpdateUi(context, result);
            }
            result.putString("stream", "PASS: release providers and requested UI checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyUpdateUi(Context context, Bundle result) throws Exception {
        SharedPreferences preferences = context.getSharedPreferences(
            context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        boolean existed = preferences.contains("Cronet");
        boolean previous = preferences.getBoolean("Cronet", false);
        preferences.edit().putBoolean("Cronet", true).commit();
        try {
            activity = startActivitySync(new Intent().setClassName(context.getPackageName(),
                "io.wanjuan.app.ui.about.AboutActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            AccessibilityNodeInfo button = null;
            for (int attempt = 0; attempt < 30 && button == null; attempt++) {
                AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    for (String title : new String[] {"Check for Updates", "检查更新"}) {
                        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(title);
                        if (!nodes.isEmpty()) { button = nodes.get(0); break; }
                    }
                }
                if (button == null) SystemClock.sleep(100);
            }
            while (button != null && !button.isClickable()) button = button.getParent();
            if (button == null) throw new AssertionError("Update action was not found");
            final AccessibilityNodeInfo action = button;
            AccessibilityEvent event = getUiAutomation().executeAndWaitForEvent(
                () -> {
                    if (!action.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        throw new AssertionError("Update action was not clicked");
                    }
                },
                candidate -> candidate.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                    && (candidate.getText().toString().contains("已是最新版本")
                        || candidate.getText().toString().contains("检查更新")
                        || candidate.getText().toString().contains("Check for Updates")),
                55_000
            );
            String text = event.getText().toString();
            result.putString("update_result", text);
            if (!text.contains("已是最新版本")) throw new AssertionError("Update failed: " + text);
            Bitmap screenshot = getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                try (FileOutputStream out = new FileOutputStream(
                        new File(context.getExternalCacheDir(), "update-release-verified.png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
                } finally { screenshot.recycle(); }
            }
        } finally {
            if (existed) preferences.edit().putBoolean("Cronet", previous).commit();
            else preferences.edit().remove("Cronet").commit();
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
    }
}
