package com.example.smartcalculator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.smartcalculator.ai.MathSolver;

import net.objecthunter.exp4j.ExpressionBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static {
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
        System.setProperty("log4j2.isWebapp", "false");
        System.setProperty("log4j.ignoreTCL", "true");
        System.setProperty("log4j.skipJansi", "true");
    }

    // UI Elements
    private LinearLayout tabCalculator, tabAiCalculator, calculatorContainer, aiContainer, chatContainer;
    private GridLayout calculatorGrid;
    private ScrollView scrollAiChat;
    private EditText etAiMessage;
    private ImageButton btnSendAi;
    private ImageView btnCamera;
    private TextView tvResult;

    // Typing bubble
    private LinearLayout typingBubble = null;
    private TextView typingText = null;
    private final Handler typingHandler = new Handler();
    private int dotCount = 0;

    // AI Engine

    // Constants
    private static final int PICK_IMAGE = 101;
    private static final int PERMISSION_IMAGE = 102;
    private Uri pendingImageUri = null;
    private final List<String> chatHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 5;

    // Calculator
    private boolean isResultDisplayed = false;
    private String currentExpression = "";

    // Under UI Elements variables, add these:
    private FrameLayout imagePreviewContainer;
    private ImageView ivSelectedImagePreview, btnCancelImage;
    ImageView btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Pre-warm Math Engine
        new Thread(() -> {
            try { new org.matheclipse.core.eval.ExprEvaluator(); } catch (Throwable ignored) {}
        }).start();



        // Bind Views
        tabCalculator = findViewById(R.id.tabCalculator);
        tabAiCalculator = findViewById(R.id.tabAiCalculator);
        calculatorContainer = findViewById(R.id.calculatorContainer);
        aiContainer = findViewById(R.id.aiContainer);
        calculatorGrid = findViewById(R.id.calculatorGrid);
        chatContainer = findViewById(R.id.chatContainer);
        etAiMessage = findViewById(R.id.etAiMessage);
        btnSendAi = findViewById(R.id.btnSendAi);
        scrollAiChat = findViewById(R.id.scrollAiChat);
        btnCamera = findViewById(R.id.btnCamera);
        tvResult = findViewById(R.id.tvResult);
        // Bind Staging Preview Elements
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        ivSelectedImagePreview = findViewById(R.id.ivSelectedImagePreview);
        btnCancelImage = findViewById(R.id.btnCancelImage);
        btnDelete = findViewById(R.id.btnDelete);


        btnDelete.setOnClickListener(v -> {
            handleBackspace(); // reuse your existing logic
        });


        btnDelete.setOnLongClickListener(v -> {
            fullReset(); // clear everything
            return true;
        });

        etAiMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToBottom(); // moves chat up when keyboard opens
            }
        });



        tvResult.setText("0");
        setupCalculatorButtons();




        updateTabUI(true);

        // Listeners
        btnCamera.setOnClickListener(v -> checkPermissionAndOpenGallery());
        btnSendAi.setOnClickListener(v -> sendMessage());
        btnCancelImage.setOnClickListener(v -> {
            pendingImageUri = null;
            imagePreviewContainer.setVisibility(View.GONE);
        });
        tabCalculator.setOnClickListener(v -> switchTab(true));
        tabAiCalculator.setOnClickListener(v -> switchTab(false));


    }



    private void sendMessage() {
        String msg = etAiMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg) && pendingImageUri == null) return;

        hideKeyboard();
        etAiMessage.setText("");

        // If there is a pending image layout stage active, append it to the message feed now!
        if (pendingImageUri != null) {
            addImageBubble(pendingImageUri, Gravity.END, R.drawable.bubble_user);
            imagePreviewContainer.setVisibility(View.GONE); // Hide staging view container
        }

        if (!TextUtils.isEmpty(msg)) {
            addUserMessage(msg);
            updateHistory("User: " + msg);
        }

        showTypingDots();


        String finalInput = msg;

        if (TextUtils.isEmpty(finalInput) && pendingImageUri != null) {
            finalInput = "Solve this image";
        }

        processCombinedInput(finalInput);
    }







    private void processCombinedInput(String input) {


        String lowerInput = input.toLowerCase();


        // 🌍 Country time detection
        if (lowerInput.contains("time in")) {
            handleCountryTime(input);
            return;
        }


        // ✅ FIRST check time/date
        if (lowerInput.contains("time") || lowerInput.contains("date")) {
            handleTimeQuery(input);
            return; // ❗ STOP (don’t go to API)
        }


        runOnUiThread(() -> btnSendAi.setEnabled(false));

        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

                // 🔑 PUT YOUR API KEY HERE
                String apiKey = "AIzaSyBo_QYRmniYa4L53vO1YfGVM97FFe1UWB0";

                // 🛠 Fix empty input
                String finalInput = input;
                if (finalInput == null || finalInput.trim().isEmpty()) {
                    finalInput = "Solve this";
                }

                String url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=" + apiKey;

                // ✅ Proper JSON (escaped safely)
                org.json.JSONObject textPart = new org.json.JSONObject();
                // 🔥 ADD THIS
                String prompt = "You are a friendly AI 🤖. "
                        + "Use 1 or 2 friendly emojis only 😊. "
                        + "Keep answers short, clean and helpful.\nUser: " + finalInput;

                textPart.put("text", prompt);

                org.json.JSONArray partsArray = new org.json.JSONArray();
                partsArray.put(textPart);

                org.json.JSONObject content = new org.json.JSONObject();
                content.put("parts", partsArray);

                org.json.JSONArray contentsArray = new org.json.JSONArray();
                contentsArray.put(content);

                org.json.JSONObject requestBodyJson = new org.json.JSONObject();
                requestBodyJson.put("contents", contentsArray);

                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                        requestBodyJson.toString(),
                        okhttp3.MediaType.get("application/json")
                );

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                okhttp3.Response response = client.newCall(request).execute();

// ✅ DEFINE statusCode FIRST
                int statusCode = response.code();

// ✅ GET RESPONSE BODY
                String res = response.body() != null ? response.body().string() : "";

// ✅ LOG BOTH
                Log.d("API_STATUS", String.valueOf(statusCode));
                Log.d("API_RESPONSE", res);

                runOnUiThread(() -> {
                    stopTypingDots();

                    if (res.isEmpty()) {
                        addAiMessage("❌ Empty response from AI");
                        btnSendAi.setEnabled(true);
                        return;
                    }

                    try {
                        org.json.JSONObject jsonObject = new org.json.JSONObject(res);

                        org.json.JSONArray candidates = jsonObject.getJSONArray("candidates");
                        org.json.JSONObject contentObj = candidates.getJSONObject(0).getJSONObject("content");
                        org.json.JSONArray parts = contentObj.getJSONArray("parts");

                        String reply = parts.getJSONObject(0).getString("text");

// 👉 Add random emojis here
                        reply = reply + " " + getRandomEmoji(); // only ONE emoji

                        addAiMessage(reply);

                    } catch (Exception e) {
                        addAiMessage("❌ Parse Error");
                        android.util.Log.e("PARSE_ERROR", e.toString());
                    }

                    btnSendAi.setEnabled(true);
                });

            } catch (Exception e) {
                android.util.Log.e("API_CRASH", Log.getStackTraceString(e));
                runOnUiThread(() -> {
                    stopTypingDots();
                    addAiMessage("❌ API Error: " + e.getMessage());
                    btnSendAi.setEnabled(true);
                });

                android.util.Log.e("API_ERROR", e.toString());
            }
        }).start();





    }

    // 👇 ADD YOUR TIME METHOD HERE (outside, not inside above method)
    private void handleTimeQuery(String input) {

        stopTypingDots(); // 🔥 ADD THIS LINE

        java.text.SimpleDateFormat timeFormat =
                new java.text.SimpleDateFormat("hh:mm a"); // ✅ 12-hour format

        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat("dd MMM yyyy");

        String time = timeFormat.format(new java.util.Date());
        String date = dateFormat.format(new java.util.Date());

        String reply = "⏰ Time: " + time + "\n📅 Date: " + date + " " + getRandomEmoji();

        addAiMessage(reply);
    }
    private void handleCountryTime(String input) {

        stopTypingDots(); // 🔥 ADD THIS LINE

        String country = input.toLowerCase();

        String timezone = "";

        // 🌍 Country → Timezone mapping
        if (country.contains("bangladesh")) {
            timezone = "Asia/Dhaka";
        } else if (country.contains("india")) {
            timezone = "Asia/Kolkata";
        } else if (country.contains("usa") || country.contains("america")) {
            timezone = "America/New_York"; // default US
        } else if (country.contains("uk") || country.contains("london")) {
            timezone = "Europe/London";
        } else if (country.contains("dubai")) {
            timezone = "Asia/Dubai";
        } else if (country.contains("japan")) {
            timezone = "Asia/Tokyo";
        } else {
            addAiMessage("🌍 Sorry bro, country not supported yet 😅");
            return;
        }

        java.text.SimpleDateFormat timeFormat =
                new java.text.SimpleDateFormat("hh:mm a");

        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat("dd MMM yyyy");

        java.util.TimeZone tz = java.util.TimeZone.getTimeZone(timezone);

        timeFormat.setTimeZone(tz);
        dateFormat.setTimeZone(tz);

        String time = timeFormat.format(new java.util.Date());
        String date = dateFormat.format(new java.util.Date());

        String reply = "🌍 " + input.toUpperCase() + "\n⏰ Time: " + time +
                "\n📅 Date: " + date + " " + getRandomEmoji();

        addAiMessage(reply);
    }



    private TextView addStreamingAiBubble(String initialText) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bubble_ai);
        bubble.setPadding(40, 25, 40, 25);
        TextView tv = new TextView(this); tv.setText(initialText); tv.setTextColor(Color.WHITE); tv.setTextSize(16);
        bubble.addView(tv);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.START; p.setMargins(0, 15, 0, 15);
        bubble.setLayoutParams(p);
        chatContainer.addView(bubble);
        return tv;
    }

    // -------------------- CALCULATOR --------------------
    private void setupCalculatorButtons() {
        for (int i = 0; i < calculatorGrid.getChildCount(); i++) {

            View view = calculatorGrid.getChildAt(i);

            // ✅ Skip non-buttons (VERY IMPORTANT)
            if (!(view instanceof Button)) continue;

            Button btn = (Button) view;
            String text = btn.getText().toString();

            if (text.equals("=")) {
                btn.setOnClickListener(v -> calculateResult());

            } else if (text.equalsIgnoreCase("C")) {
                btn.setOnClickListener(v -> handleBackspace());
                btn.setOnLongClickListener(v -> {
                    fullReset();
                    return true;
                });

            } else {
                btn.setOnClickListener(v -> onNumberOrOperatorClick(text));
            }
        }
    }

    private void calculateResult() {
        if (currentExpression.isEmpty()) return;
        try {
            String expr = currentExpression.replace("×", "*").replace("÷", "/");
            double result = new ExpressionBuilder(expr).build().evaluate();
            String resStr = (result == (long) result) ? String.format("%d", (long) result) : String.valueOf(result);
            tvResult.setText(resStr);
            currentExpression = resStr;
            isResultDisplayed = true;
        } catch (Exception e) { tvResult.setText("Error"); isResultDisplayed = true; }
    }

    private void onNumberOrOperatorClick(String value) {
        if (isResultDisplayed) { currentExpression = ""; isResultDisplayed = false; }
        currentExpression += value; tvResult.setText(currentExpression);
    }

    private void handleBackspace() {
        if (!currentExpression.isEmpty()) currentExpression = currentExpression.substring(0, currentExpression.length() - 1);
        tvResult.setText(currentExpression.isEmpty() ? "0" : currentExpression);
    }

    private void fullReset() { currentExpression = ""; tvResult.setText("0"); isResultDisplayed = false; }

    // -------------------- CHAT UI --------------------
    private void addUserMessage(String msg) { chatContainer.addView(createBubble(msg, Gravity.END, R.drawable.bubble_user)); scrollToBottom(); }
    private void addAiMessage(String msg) { chatContainer.addView(createBubble(msg, Gravity.START, R.drawable.bubble_ai)); scrollToBottom(); }

    private LinearLayout createBubble(String text, int gravity, int bg) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(bg);
        bubble.setPadding(40, 25, 40, 25);
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.WHITE); tv.setTextSize(16);
        bubble.addView(tv);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = gravity; p.setMargins(0, 15, 0, 15);
        bubble.setLayoutParams(p);
        return bubble;
    }

    private void scrollToBottom() {
        scrollAiChat.postDelayed(() -> {
            scrollAiChat.smoothScrollTo(0, chatContainer.getBottom());
        }, 0); // 50ms delay makes it much more reliable
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etAiMessage.getWindowToken(), 0);
    }

    // -------------------- TYPING BUBBLE --------------------
    private final Runnable typingRunnable = new Runnable() {
        @Override public void run() {
            if (typingText != null) {
                dotCount = (dotCount + 1) % 4;
                typingText.setText("AI is thinking" + ".".repeat(dotCount));
                typingHandler.postDelayed(this, 400);
            }
        }
    };

    private void showTypingDots() {
        if (typingBubble != null) return; // already showing
        typingBubble = createBubble("AI is thinking", Gravity.START, R.drawable.bubble_ai);
        typingText = (TextView) typingBubble.getChildAt(0);
        chatContainer.addView(typingBubble);
        scrollToBottom();
        dotCount = 0;
        typingHandler.post(typingRunnable);
    }

    private void stopTypingDots() {
        typingHandler.removeCallbacks(typingRunnable);
        if (typingBubble != null) {
            chatContainer.removeView(typingBubble);
            typingBubble = null;
            typingText = null;
        }
    }

    // -------------------- TAB & HISTORY --------------------
    private void switchTab(boolean isCalc) {

        if (isCalc) {
            calculatorContainer.setVisibility(View.VISIBLE);
            calculatorGrid.setVisibility(View.VISIBLE);

            aiContainer.setVisibility(View.GONE);

        } else {
            calculatorContainer.setVisibility(View.GONE);
            calculatorGrid.setVisibility(View.GONE); // ⭐ THIS FIXES YOUR BUG

            aiContainer.setVisibility(View.VISIBLE);
        }

        updateTabUI(isCalc);
    }

    private void updateTabUI(boolean isCalc) {
        tabCalculator.setBackgroundResource(isCalc ? R.drawable.tab_inverted_shape : 0);
        tabAiCalculator.setBackgroundResource(!isCalc ? R.drawable.tab_inverted_shape_right : 0);
    }

    private String cleanMathText(String text) { return text != null ? text.replaceAll("(?i)o", "0").trim() : ""; }

    private void updateHistory(String entry) { chatHistory.add(entry); if (chatHistory.size() > MAX_HISTORY) chatHistory.remove(0); }

    // -------------------- IMAGE --------------------
    private void checkPermissionAndOpenGallery() {
        String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{perm}, PERMISSION_IMAGE);
        else openGallery();
    }


    // 🔥 Emoji list
    String[] emojis = {
            "😊", "😄", "🔥", "✨", "🚀", "💡", "😎",
            "🤖", "📘", "🎯", "💬", "🌍", "⚡", "👍"
    };

    // 🎲 Random emoji function
    private String getRandomEmoji() {
        java.util.Random random = new java.util.Random();
        return emojis[random.nextInt(emojis.length)];
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            pendingImageUri = data.getData();

            // Show the image preview container instead of putting it on the chat right away
            if (pendingImageUri != null && imagePreviewContainer != null && ivSelectedImagePreview != null) {
                imagePreviewContainer.setVisibility(View.VISIBLE);
                Glide.with(this).load(pendingImageUri).into(ivSelectedImagePreview);
                scrollToBottom();
            }
        }
    }

    private void addImageBubble(Uri uri, int gravity, int bg) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(bg); // Restored back to your original user/ai theme bubble
        bubble.setPadding(15, 15, 15, 15);

        ImageView img = new ImageView(this);
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.6);
        img.setLayoutParams(new LinearLayout.LayoutParams(w, w));
        bubble.addView(img);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = gravity;
        p.setMargins(0, 20, 0, 20);
        bubble.setLayoutParams(p);

        chatContainer.addView(bubble);
        Glide.with(this).load(uri).into(img);
        scrollToBottom();
    }


}
