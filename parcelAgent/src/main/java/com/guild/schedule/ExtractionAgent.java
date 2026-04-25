package com.guild.schedule;

import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class ExtractionAgent {

    // IMPORTANT: Paste your actual Google Gemini API key here
    private static final String API_KEY = "AIzaSyBb7bI09xmyt_yuKgyF8u6aQ6kxAqeC2bE";

    // Google Gemini endpoint (Note: the API key goes directly in the URL)
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + API_KEY;

    public static String processWithGLM(String userMessage, String imageUrl) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // 1. Download the image from the URL and convert it to a Base64 string
        String base64Image = "";
        try {
            Request imgRequest = new Request.Builder()
                    .url(imageUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();
            try (Response imgResponse = client.newCall(imgRequest).execute()) {
                if (imgResponse.isSuccessful() && imgResponse.body() != null) {
                    byte[] imageBytes = imgResponse.body().bytes();
                    base64Image = Base64.getEncoder().encodeToString(imageBytes);
                } else {
                    System.err.println("Failed to download image from URL.");
                    return "{\"trackingNumber\": null, \"damageLevel\": null}";
                }
            }
        } catch (Exception e) {
            System.err.println("Network error while downloading image: " + e.getMessage());
            return "{\"trackingNumber\": null, \"damageLevel\": null}";
        }

        // 2. Build the Gemini JSON Payload using Gson
        String systemPrompt = "You are a data extraction agent for a logistics system. " +
                "Extract the tracking number. Assess the damage from the image (HIGH, MEDIUM, LOW, or NONE). " +
                "Output ONLY raw, valid JSON in this exact format: {\"trackingNumber\": \"...\", \"damageLevel\": \"...\"}. " +
                "Do not include markdown tags like ```json. If tracking number is missing, output null.";

        String combinedText = systemPrompt + "\n\nUser Message: " + userMessage;

        // Text Part
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", combinedText);

        // Image Part (Base64)
        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mime_type", "image/jpeg");
        inlineData.addProperty("data", base64Image);

        JsonObject imagePart = new JsonObject();
        imagePart.add("inline_data", inlineData);

        // Combine Parts
        JsonArray partsArray = new JsonArray();
        partsArray.add(textPart);
        partsArray.add(imagePart);

        JsonObject contentObject = new JsonObject();
        contentObject.add("parts", partsArray);

        JsonArray contentsArray = new JsonArray();
        contentsArray.add(contentObject);

        JsonObject payloadObject = new JsonObject();
        payloadObject.add("contents", contentsArray);

        String jsonPayload = payloadObject.toString();

        // 3. Send the POST request to Google
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("API Call Failed: " + response.code());
                if (response.body() != null) {
                    System.err.println("Error Details: " + response.body().string());
                }
                return "{\"trackingNumber\": null, \"damageLevel\": null}";
            }

            // Parse Gemini's specific response structure
            String responseData = response.body().string();
            JsonObject jsonObject = JsonParser.parseString(responseData).getAsJsonObject();

            String extractedContent = jsonObject
                    .getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            // Clean up the output in case Gemini adds spaces or newlines
            return extractedContent.trim();

        } catch (IOException e) {
            e.printStackTrace();
            return "{\"trackingNumber\": null, \"damageLevel\": null}";
        }
    }
}