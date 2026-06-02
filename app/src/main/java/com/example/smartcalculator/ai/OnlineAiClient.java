package com.example.smartcalculator.ai;

import android.os.Handler;
import android.os.Looper;

public class OnlineAiClient {

    public interface Callback {
        void onResult(String answer);
        void onError();
    }

    public static void ask(String prompt, Callback callback) {

        // 🔹 DEMO AI (replace later with real API)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            if (prompt.contains("2+3=10") || prompt.contains("9+5")) {
                callback.onResult(
                        "Pattern detected: A × (A + B)\n" +
                                "So, 9 × (9 + 5) = 126"
                );
            } else {
                callback.onResult("This looks like a logic-based problem.");
            }

        }, 1200);
    }
}


