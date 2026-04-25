package com.guild.schedule;

import io.javalin.Javalin;

public class Main {
    public static void main(String[] args) {
        // 1. Start a lightweight web server on port 8080
        Javalin app = Javalin.create().start(8080);
        System.out.println("Server is running! Listening for WhatsApp messages on port 8080...");

        // 2. Create the webhook endpoint for Twilio to send data to
        app.post("/whatsapp/webhook", ctx -> {

            // Extract the text and image that the user sent via WhatsApp
            String incomingText = ctx.formParam("Body");
            String incomingImage = ctx.formParam("MediaUrl0");

            System.out.println("\n--- New WhatsApp Message Received ---");
            System.out.println("Text: " + incomingText);

            // 3. Handling Constraints: If the user forgot to attach a photo, intercept and ask for one.
            if (incomingImage == null || incomingImage.isEmpty()) {
                System.out.println("No image attached. Triggering fallback response.");
                String fallbackReply = "<Response><Message>Please attach a photo of the damaged parcel so I can assess it!</Message></Response>";
                ctx.contentType("application/xml");
                ctx.result(fallbackReply);
                return; // Stop execution here
            }

            // 4. Pass the real WhatsApp data to your Gemini Agent
            System.out.println("Image received. Sending to Agent A...");
            String jsonResult = ExtractionAgent.processWithGLM(incomingText, incomingImage);

            System.out.println("Agent A Output: " + jsonResult);

            // 5. Reply back to the user's WhatsApp
            // Twilio requires replies to be formatted in TwiML (XML)
            String twimlResponse = "<Response><Message>Processed by Agent A:\n" + jsonResult + "</Message></Response>";
            ctx.contentType("application/xml");
            ctx.result(twimlResponse);
        });
    }
}