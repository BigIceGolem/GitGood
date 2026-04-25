package com.guild.schedule;

import io.javalin.Javalin;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class Main {
    public static void main(String[] args) {
        Javalin app = Javalin.create().start(8080);
        System.out.println("Server is running! Listening for WhatsApp messages...");

        app.post("/whatsapp/webhook", ctx -> {

            String incomingText = ctx.formParam("Body");
            String incomingImage = ctx.formParam("MediaUrl0");

            // We need to capture the phone numbers so the background thread knows who to text back
            String senderPhone = ctx.formParam("From");
            String twilioSandboxNumber = ctx.formParam("To");

            System.out.println("\n--- New WhatsApp Message Received ---");

            if (incomingImage == null || incomingImage.isEmpty()) {
                ctx.contentType("application/xml");
                ctx.result("<Response><Message>Please attach a photo of the damaged parcel!</Message></Response>");
                return;
            }

            // 1. INSTANT REPLY: Give Twilio an empty TwiML response immediately to beat the 15s timeout
            ctx.contentType("application/xml");
            ctx.result("<Response></Response>");
            System.out.println("Sent immediate 200 OK to Twilio. Handing image to background thread...");

            // 2. ASYNCHRONOUS PROCESSING: Run the heavy AI engine in the background
            CompletableFuture.runAsync(() -> {
                System.out.println("[Background Thread] Downloading image and pinging Gemini...");
                String jsonResult = ExtractionAgent.processWithGLM(incomingText, incomingImage);

                // Clean the output in case Gemini added markdown backticks
                String cleanJson = jsonResult.replace("```json", "").replace("```", "").trim();
                System.out.println("[Background Thread] Agent A Output: " + cleanJson);

                // 3. PUSH NOTIFICATION: Send the final JSON back to the user via Twilio REST API
                sendTwilioReply(senderPhone, twilioSandboxNumber, "Processed by Agent A:\n" + cleanJson);
            });
        });
    }

    // Helper method to push messages to WhatsApp using OkHttp
    private static void sendTwilioReply(String toPhone, String fromPhone, String messageBody) {
        String twilioSid = "AC05962ce67910d50b03e6916467b12cf0";
        String twilioToken = "eb2a7f509bcf70478d9fb059d274a4e3"; // <-- PASTE YOUR TOKEN HERE

        OkHttpClient client = new OkHttpClient();
        String credential = Credentials.basic(twilioSid, twilioToken);

        RequestBody body = new FormBody.Builder()
                .add("To", toPhone)
                .add("From", fromPhone)
                .add("Body", messageBody)
                .build();

        Request request = new Request.Builder()
                .url("https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json")
                .addHeader("Authorization", credential)
                .post(body)
                .build();

        try {
            Response response = client.newCall(request).execute();
            if(response.isSuccessful()) {
                System.out.println("[Background Thread] Successfully pushed final message to WhatsApp!");
            } else {
                System.err.println("[Background Thread] Failed to send WhatsApp reply: " + response.code());
            }
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}