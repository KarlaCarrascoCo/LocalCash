package com.localcash.app.ui;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import com.localcash.app.R;

/** Autenticación biométrica nativa, sin servicios ni dependencias externas. */
public final class BiometricAuthenticator {
    public interface Callback { void onAuthenticated(); void onError(CharSequence message); }
    private BiometricAuthenticator() { }

    @SuppressWarnings("deprecation")
    public static void authenticate(Activity activity, Callback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BiometricManager manager = activity.getSystemService(BiometricManager.class);
            if (manager == null) {
                callback.onError(activity.getString(R.string.biometric_unavailable));
                return;
            }

            int availability = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    : manager.canAuthenticate();
            if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                callback.onError(messageForAvailability(activity, availability));
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                        .setTitle(activity.getString(R.string.biometric_prompt_title))
                        .setDescription(activity.getString(R.string.biometric_prompt_description))
                        .setNegativeButton(activity.getString(R.string.action_cancel), activity.getMainExecutor(),
                                (dialog, which) -> callback.onError(activity.getString(R.string.biometric_cancelled)))
                        .build();
                prompt.authenticate(new CancellationSignal(), activity.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { callback.onAuthenticated(); }
                    @Override public void onAuthenticationError(int code, CharSequence message) { callback.onError(message); }
                });
            } catch (RuntimeException exception) {
                callback.onError(activity.getString(R.string.biometric_prompt_unavailable));
            }
            return;
        }
        callback.onError(activity.getString(R.string.biometric_unsupported_version));
    }

    private static CharSequence messageForAvailability(Activity activity, int availability) {
        if (availability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return activity.getString(R.string.biometric_not_enrolled);
        }
        if (availability == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            return activity.getString(R.string.biometric_no_hardware);
        }
        if (availability == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            return activity.getString(R.string.biometric_hardware_busy);
        }
        return activity.getString(R.string.biometric_unavailable);
    }
}
